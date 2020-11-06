package net.ossrs.rtmp.bitrateadjuster;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BitrateEstimator {
    public static boolean SHOW_LOGS = false;
    private final static String TAG = "BitrateEstimator";
    private final int INITIAL_DELAY_MS = 1_000; // To not take the TCP slow-start into account

    private final long intervalDurationMs;
    private final boolean isEndlessEstimation;
    private final boolean lowerEstimation;
    private final boolean useSystemUploadCalculation;
    private final float loweringFactor;
    private final long maxIntervals;

    private long uploadedBytesSoFar = 0L;
    private long systemUploadedBytesOnIntervalStart = 0L;
    private long intervalStartNano = -1L;
    private int intervalNo = 1;
    private final List<Double> bitrates = new ArrayList<>();

    private volatile boolean finished = true;
    private volatile boolean waitForInitialDelay = false;

    public BitrateEstimator(long testDurationMs, long intervalDurationMs,
                            boolean isEndlessEstimation, boolean lowerEstimation,
                            float loweringFactor, boolean useSystemUploadCalculation) {
        this.intervalDurationMs = intervalDurationMs;
        this.isEndlessEstimation = isEndlessEstimation;
        this.lowerEstimation = lowerEstimation;
        this.loweringFactor = loweringFactor;
        this.useSystemUploadCalculation = useSystemUploadCalculation;
        maxIntervals = testDurationMs / intervalDurationMs;
    }

    public interface BitrateEstimatorListener {
        void onResult(double medianBitrate);
    }

    public BitrateEstimatorListener listener;

    public void start() {
        finished = false;
        waitForInitialDelay = true;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                reset();
                waitForInitialDelay = false;
            }
        }, INITIAL_DELAY_MS);
    }

    public void beforeFrameSent() {
        if (!canExecute()) {
            return;
        }

        if (intervalStartNano == -1) {
            intervalStartNano = System.nanoTime();
            systemUploadedBytesOnIntervalStart = TrafficStats.getTotalTxBytes();
        }
    }

    public void afterFrameSent(int sentFrameSize) {
        if (!canExecute()) {
            return;
        }

        uploadedBytesSoFar += sentFrameSize;
        long currentIntervalDurationMs = (System.nanoTime() - intervalStartNano) / 1000 / 1000;
        if (currentIntervalDurationMs > intervalDurationMs) {
            estimateBitrateForInterval();
            intervalNo++;
            resetInterval();
        }

        if (intervalNo > maxIntervals) {
            if (listener != null) {
                double medianBitrate = getMedianBitrate(new ArrayList<>(bitrates));
                double estimatedBitrate = lowerEstimation ? medianBitrate * loweringFactor : medianBitrate;
                listener.onResult(estimatedBitrate);
            }
            if (isEndlessEstimation) {
                reset();
            } else {
                finish();
            }
        }
    }

    public void finish() {
        finished = true;
    }

    public boolean isEndlessEstimation() {
        return isEndlessEstimation;
    }

    public boolean isFinished() {
        return finished;
    }

    private boolean canExecute() {
        return !finished && !waitForInitialDelay;
    }

    private void estimateBitrateForInterval() {
        double currentIntervalDuration = (System.nanoTime() - intervalStartNano) / 1000.0 / 1000.0 / 1000.0;
        double byteRateStream = uploadedBytesSoFar / currentIntervalDuration;
        double byteRateSystem = (TrafficStats.getTotalTxBytes() - systemUploadedBytesOnIntervalStart) / currentIntervalDuration;
        if (byteRateStream > 0) {
            if (useSystemUploadCalculation) {
                bitrates.add(byteRateSystem * 8);
            } else {
                bitrates.add(byteRateStream * 8);
            }
            if (SHOW_LOGS) {
                Log.d(TAG, "Bitrate for interval.\nSystem: " + byteRateSystem * 8 / 1024.0 / 1024.0 + "\nStream: " + byteRateStream * 8 / 1024.0 / 1024.0);
            }
        }
    }

    private double getMedianBitrate(List<Double> bitrates) {
        Collections.sort(bitrates);
        if (bitrates.isEmpty()) {
            return 0;
        }
        double medianBitrate;
        if (bitrates.size() % 2 == 0) {
            medianBitrate = ((bitrates.get(bitrates.size() / 2) + bitrates.get(bitrates.size() / 2 - 1)) / 2.0);
        } else {
            medianBitrate = bitrates.get(bitrates.size() / 2);
        }
        return medianBitrate;
    }

    private void reset() {
        intervalNo = 1;
        bitrates.clear();
        resetInterval();
    }

    private void resetInterval() {
        uploadedBytesSoFar = 0L;
        intervalStartNano = -1;
    }
}