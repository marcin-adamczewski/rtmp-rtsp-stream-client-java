package net.ossrs.rtmp.bitrateadjuster;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import net.ossrs.rtmp.SrsFlvMuxer;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BitrateAdjuster implements SrsFlvMuxer.MuxerEventsListener {

    public interface BitrateUpdater {
        void onNewBitrate(long bitrate);
    }

    private static final String TAG = "BitrateAdjuster";
    private final BitrateUpdater bitrateUpdater;
    private final Context context;
    @NonNull private BitrateAdjusterConfig config;
    private final BitrateEstimator endlessEstimator;
    @Nullable private BitrateEstimator currentEstimator;
    @Nullable private NetworkStateManager networkStateManager;
    @Nullable private volatile NetworkType currentNetworkType;
    private double currentEstimatedBitrate;
    private volatile boolean skipNewEstimators;

    public BitrateAdjuster(@NonNull Context context, @Nullable BitrateAdjusterConfig config, @NonNull BitrateUpdater bitrateUpdater) {
        this.bitrateUpdater = bitrateUpdater;
        this.context = context;
        this.config = config == null ? BitrateAdjusterConfig.defaultConfig() : config;
        endlessEstimator = createEstimator(true, false);
        endlessEstimator.listener = new BitrateEstimator.BitrateEstimatorListener() {
            @Override
            public void onResult(double medianBitrate) {
                Log.d(TAG, "current estimated bitrate: " + medianBitrate / 1024.0 / 1024.0);
                currentEstimatedBitrate = medianBitrate;
            }
        };
        endlessEstimator.start();
    }

    public void onStreamConnected() {
        if (networkStateManager != null) {
            networkStateManager.setListener(null);
        }
        networkStateManager = new NetworkStateManager(context);
        networkStateManager.setListener(new NetworkStateManager.NetworkTypeListener() {
            @Override
            public void onNetworkChanged(@NonNull NetworkType networkType) {
                Log.d("lol", "network changed to: " + networkType);
                startEstimatorForNewNetwork(networkType);
            }
        });
    }

    @Override
    public void onCongestion(float bufferFill) {
        startEstimatorForCongestion(bufferFill);
    }

    @Override
    public void beforeVideoFrameSent() {
        if (currentEstimator != null) {
            currentEstimator.beforeFrameSent();
        }
        endlessEstimator.beforeFrameSent();
    }

    @Override
    public void afterVideoFrameSent(int frameSize) {
        if (currentEstimator != null) {
            currentEstimator.afterFrameSent(frameSize);
        }
        endlessEstimator.afterFrameSent(frameSize);
    }

    private void startEstimatorForNewNetwork(NetworkType networkType) {
        currentNetworkType = networkType;
        if (networkType == NetworkType.NO_CONNECTION) return;

        int defaultBitrateForNetwork = getDefaultBitrateForNetwork(networkType);
        startNewEstimator(createEstimator(false, false), true, (long) defaultBitrateForNetwork);
    }

    private void startEstimatorForCongestion(float bufferFill) {
        if (bufferFill > 0.2f &&
                (currentNetworkType == null || currentNetworkType != NetworkType.NO_CONNECTION)) {
            Long initialBitrate = currentEstimatedBitrate > 0 ? (long) currentEstimatedBitrate : null;
            startNewEstimator(createEstimator(false, true), false, initialBitrate);
        }
    }

    private synchronized void startNewEstimator(
            BitrateEstimator estimator,
            final boolean blockingPriorityEstimator,
            @Nullable Long initialBitrate) {
        if (skipNewEstimators) {
            return;
        }

        if (blockingPriorityEstimator) {
            skipNewEstimators = true;
        }

        if (!blockingPriorityEstimator && isCurrentEstimatorRunning()) {
            return;
        }

        if (currentEstimator != null) {
            currentEstimator.finish();
        }

        Log.d(TAG, "starting new estimator");
        if (initialBitrate != null) {
            Log.d(TAG, "setting estimator initial bitrate: " + initialBitrate);
            bitrateUpdater.onNewBitrate(initialBitrate);
        }

        currentEstimator = estimator;
        currentEstimator.listener = new BitrateEstimator.BitrateEstimatorListener() {
            @Override
            public void onResult(double medianBitrate) {
                Log.d(TAG, "estimator finished with result:: " + medianBitrate / 1024.0 / 1024.0);
                bitrateUpdater.onNewBitrate((long) medianBitrate);
                if (blockingPriorityEstimator) {
                    skipNewEstimators = false;
                }
            }
        };
        estimator.start();
    }

    private boolean isCurrentEstimatorRunning() {
        return currentEstimator != null &&
                !currentEstimator.isEndlessEstimation() &&
                !currentEstimator.isFinished();
    }

    private int fromMbToBits(int megaBits) {
        return megaBits * 1024 * 1024;
    }

    private BitrateEstimator createEstimator(boolean isEndlessEstimation, boolean lowerEstimation) {
        return new BitrateEstimator(config.testDurationMs, config.testIntervalDurationMs,
                isEndlessEstimation, lowerEstimation, config.loweringFraction);
    }

    private int getDefaultBitrateForNetwork(NetworkType networkType) {
        return fromMbToBits(config.bitrateForNetwork.getDefaultMegaBitrateForNetwork(networkType));
    }
}
