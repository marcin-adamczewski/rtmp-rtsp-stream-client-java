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
    private static final float MINIMUM_LOWERING_FACTOR = 0.5f;
    private static final long MINIMUM_TIME_BETWEEN_CONGESTION_ESTIMATORS_MS = 10_000;
    private final BitrateUpdater bitrateUpdater;
    private final Context context;
    @NonNull private BitrateAdjusterConfig config;
    private final BitrateEstimator endlessEstimator;
    @Nullable private BitrateEstimator currentEstimator;
    @Nullable private NetworkStateManager networkStateManager;
    @Nullable private volatile NetworkType currentNetworkType;
    private double currentEstimatedBitrate;
    private volatile boolean skipNewEstimators;
    private float adjustableLoweringFactor;
    private long previousStartTimeMsOfEstimationForCongestion;

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
        adjustableLoweringFactor = this.config.loweringFraction;
    }

    public void onStreamConnected() {
        if (networkStateManager != null) {
            networkStateManager.setListener(null);
        }
        networkStateManager = new NetworkStateManager(context);
        networkStateManager.setListener(new NetworkStateManager.NetworkTypeListener() {
            @Override
            public void onNetworkChanged(@NonNull NetworkType networkType) {
                Log.d(TAG, "network changed to: " + networkType);
                startEstimatorForNewNetwork(networkType);
                if (currentNetworkType == null || currentNetworkType != networkType) {
                    // As the current network may be much more stable then previous one,
                    // we reset lowering factor to default value.
                    adjustableLoweringFactor = config.loweringFraction;
                }
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

    // Note that the onCongestion() method gonna be called many times during a congestion.
    // That's why we want to avoid starting new estimator for each call.
    private void startEstimatorForCongestion(float bufferFill) {
        if (bufferFill > 0.2f &&
                (currentNetworkType == null || currentNetworkType != NetworkType.NO_CONNECTION) &&
                isMinimumTimeBetweenCongestionBitrates()
        ) {
            // The initialBitrate helps us recover from congestion quicker.
            // Then, after the estimator is done the bitrate will be adjusted.
            Long initialBitrate = currentEstimatedBitrate > 0 ? (long) (adjustableLoweringFactor * currentEstimatedBitrate) : null;
            boolean hasStarted = startNewEstimator(createEstimator(false, true), false, initialBitrate);
            if (hasStarted) {
                adjustLoweringFractionToCongestion();
                previousStartTimeMsOfEstimationForCongestion = System.currentTimeMillis();
            }
        }
    }

    /**
     * Returns true if the estimator started
     **/
    private synchronized boolean startNewEstimator(
            BitrateEstimator estimator,
            final boolean blockingPriorityEstimator,
            @Nullable Long initialBitrate) {
        // Priority estimator goes first and cancel previous estimators. That way we
        // can for example cancel current estimator for WiFI when network has been changed to LTE.
        if (skipNewEstimators && !blockingPriorityEstimator) {
            return false;
        }

        if (blockingPriorityEstimator) {
            skipNewEstimators = true;
        }

        // We don't let other no-priority estimator to run if other estimator is running.
        // That way we can for example avoid calling congestion estimators multiple times.
        if (!blockingPriorityEstimator && isCurrentEstimatorRunning()) {
            return false;
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
        return true;
    }

    // If congestion happens too often, the estimated value is probably too high. It could happen
    // when the network speed fluctuates in time. If that's the case we want to slowly reduce
    // the estimated bitrate lowering factor up to MINIMUM_TIME_BETWEEN_CONGESTION_MS
    private void adjustLoweringFractionToCongestion() {
        if (System.currentTimeMillis() < minimumTimeWhenNextEstimatorForCongestionCanBeStarted() + 20_000) {
            adjustableLoweringFactor = Math.max(MINIMUM_LOWERING_FACTOR, adjustableLoweringFactor - 0.1f);
            Log.d(TAG, "Lowering fraction reduced to: " + adjustableLoweringFactor);
        }
    }

    // Even though the previous estimator for congestion estimated bitrate correctly we have to
    // give some time to send everything out from the buffer before congestion is gone.
    // That's why we want to wait MINIMUM_TIME_BETWEEN_CONGESTION_ESTIMATORS_MS before starting
    // a new estimator.
    private boolean isMinimumTimeBetweenCongestionBitrates() {
        return System.currentTimeMillis() > minimumTimeWhenNextEstimatorForCongestionCanBeStarted();
    }

    private long minimumTimeWhenNextEstimatorForCongestionCanBeStarted() {
        return previousStartTimeMsOfEstimationForCongestion + config.testDurationMs + MINIMUM_TIME_BETWEEN_CONGESTION_ESTIMATORS_MS;
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
                isEndlessEstimation, lowerEstimation, adjustableLoweringFactor);
    }

    private int getDefaultBitrateForNetwork(NetworkType networkType) {
        return fromMbToBits(config.bitrateForNetwork.getDefaultMegaBitrateForNetwork(networkType));
    }
}