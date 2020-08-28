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

    public interface BitrateForNetwork {
        int getDefaultBitrateForNetwork(NetworkType networkType);
    }

    static int fromMbToBits(int megaBits) {
        return megaBits * 1024 * 1024;
    }

    public static class DefaultBitrateForNetwork implements BitrateForNetwork {
        @Override
        public int getDefaultBitrateForNetwork(NetworkType networkType) {
            switch (networkType) {
                case WIFI:
                    return fromMbToBits(3);
                case FOUR_G:
                    return fromMbToBits(2);
                case THREE_G:
                    return fromMbToBits(1);
                default:
                    return fromMbToBits(1);
            }
        }
    }

    public static final long DEFAULT_TEST_DURATION_MS = 5_000;
    public static final long DEFAULT_INTERVAL_DURATION_MS = 1_000;
    public static final float DEFAULT_LOWERING_FRACTION = 0.9f;

    public final BitrateForNetwork bitrateForNetwork;
    public final long testDurationMs;
    public final long testIntervalDurationMs;
    public final float loweringFraction;

}
