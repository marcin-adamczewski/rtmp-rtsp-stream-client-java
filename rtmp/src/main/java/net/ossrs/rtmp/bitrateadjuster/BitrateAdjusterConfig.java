package net.ossrs.rtmp.bitrateadjuster;

import androidx.annotation.NonNull;

public class BitrateAdjusterConfig {

    public static BitrateAdjusterConfig defaultConfig() {
        return new BitrateAdjusterConfig(new DefaultBitrateForNetwork(), DEFAULT_TEST_DURATION_MS, DEFAULT_INTERVAL_DURATION_MS, DEFAULT_LOWERING_FRACTION);
    }

    public BitrateAdjusterConfig(@NonNull BitrateForNetwork bitrateForNetwork, long testDurationMs, long testIntervalDurationMs, float loweringFraction) {
        this.bitrateForNetwork = bitrateForNetwork;
        this.testDurationMs = testDurationMs;
        this.testIntervalDurationMs = testIntervalDurationMs;
        this.loweringFraction = loweringFraction;
    }

    interface BitrateForNetwork {
        int getDefaultMegaBitrateForNetwork(NetworkType networkType);
    }

    public static class DefaultBitrateForNetwork implements BitrateForNetwork {
        @Override
        public int getDefaultMegaBitrateForNetwork(NetworkType networkType) {
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
    }

    private static final long DEFAULT_TEST_DURATION_MS = 8_000;
    private static final long DEFAULT_INTERVAL_DURATION_MS = 1_000;
    private static final float DEFAULT_LOWERING_FRACTION = 0.9f;

    public final BitrateForNetwork bitrateForNetwork;
    public final long testDurationMs;
    public final long testIntervalDurationMs;
    public final float loweringFraction;

}
