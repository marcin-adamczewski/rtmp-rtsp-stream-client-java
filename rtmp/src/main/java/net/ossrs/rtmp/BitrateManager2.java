package net.ossrs.rtmp;

import androidx.annotation.Nullable;

public class BitrateManager2 {
    private final SrsFlvMuxer.BitrateUpdater bitrateUpdater;
    @Nullable private NetworkType currentNetworkType;
    @Nullable private BitrateEstimator currentEstimator;

    public BitrateManager2(SrsFlvMuxer.BitrateUpdater bitrateUpdater) {
        this.bitrateUpdater = bitrateUpdater;
    }

    void onNetworkChanged(NetworkType networkType) {
        if (networkType != NetworkType.NO_CONNECTION) {
            if (networkType != currentNetworkType) {
                currentNetworkType = networkType;
                int defaultBitrateForNetwork = getDefaultBitrateForNetwork(networkType);
                bitrateUpdater.onNewBitrate(defaultBitrateForNetwork);
                setNewEstimator(new BitrateEstimator(false));
            }
        }
    }

    void onQueueFillsUps(float fillPercentage) {
        if (fillPercentage > 0.3f &&
                currentNetworkType != null &&
                currentNetworkType != NetworkType.NO_CONNECTION) {
            setNewEstimator(new BitrateEstimator(false));
        }
    }

    void beforeFrameSent() {
        if (currentEstimator != null) {
            currentEstimator.beforeFrameSent();
        }
    }

    void afterFrameSent(int sentFrameSize) {
        if (currentEstimator != null) {
            currentEstimator.afterFrameSent(sentFrameSize);
        }
    }

    private void setNewEstimator(BitrateEstimator estimator) {
        if (currentEstimator != null) {
            currentEstimator.finish();
        }
        currentEstimator = estimator;
        estimator.start();
    }

    private int fromMbToBits(int megaBits) {
        return megaBits * 1024 * 1024;
    }

    private int getDefaultBitrateForNetwork(NetworkType networkType) {
        return fromMbToBits(getDefaultMegaBitrateForNetwork(networkType));
    }

    private int getDefaultMegaBitrateForNetwork(NetworkType networkType) {
        switch (networkType) {
            case WIFI:
                return 3;
            case FOUR_G:
                return 2;
            case THREE_G:
                return 1;
            default:
                return 1;
        }
    }

    enum NetworkType {
        WIFI, FOUR_G, THREE_G, OTHER, NO_CONNECTION
    }
}
