package net.ossrs.rtmp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BitrateEstimator {
    private final static String TAG = "BitrateEstimator";
    private static final long TEST_DURATION_MS = 25_000;
    private static final long INTERVAL_DURATION_MS = 1_000;
    private final int INITIAL_DELAY_MS = 1_000; // To not take the TCP slow-start into account

    private final long intervalDurationMs;
    private final boolean isEndlessEstimation;
    private final long maxIntervals;

    private long uploadedBytesSoFar = 0L;
    private long intervalStartNano = -1L;
    private int intervalNo = 1;
    private final List<Double> bitrates = new ArrayList<>();

    private boolean finished = true;

    public BitrateEstimator(long testDurationMs, long intervalDurationMs, boolean isEndlessEstimation) {
        this.intervalDurationMs = intervalDurationMs;
        this.isEndlessEstimation = isEndlessEstimation;
        maxIntervals = testDurationMs / intervalDurationMs;
    }

    public BitrateEstimator(boolean isEndlessEstimation) {
        this(TEST_DURATION_MS, INTERVAL_DURATION_MS, isEndlessEstimation);
    }

    interface UploadTestListener {
        void onResult(double medianBitrate);
    }

    public UploadTestListener listener;

    void start() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                restart();
            }
        }, INITIAL_DELAY_MS);
    }

    void beforeFrameSent() {
        if (finished) {
            return;
        }

        if (intervalStartNano == -1) {
            intervalStartNano = System.nanoTime();
        }
    }

    void afterFrameSent(int sentFrameSize) {
        if (finished) {
            return;
        }

        uploadedBytesSoFar += sentFrameSize;
        long currentIntervalDurationMs = (System.nanoTime() - intervalStartNano) / 1000 / 1000;
        if (currentIntervalDurationMs > intervalDurationMs) {
            estimateBitrateForInterval(uploadedBytesSoFar);
            intervalNo++;
            resetInterval();
            Log.d(TAG, "starting new interval: " + intervalNo);
        }

        if (intervalNo > maxIntervals) {
            if (listener != null) {
                listener.onResult(getMedianBitrate());
            }
            if (isEndlessEstimation) {
                restart();
            } else {
                finish();
            }
        }
    }

    private void estimateBitrateForInterval(long uploadedBytes) {
        long currentIntervalDurationMs = (System.nanoTime() - intervalStartNano) / 1000 / 1000;
        double byteRate = uploadedBytes / (currentIntervalDurationMs / 1000.0);
        if (byteRate > 0) {
            bitrates.add(byteRate * 8);
            Log.d(TAG, "Added bitrate: " + byteRate * 8 / 1024.0 / 1024.0);
        }
    }

    private double getMedianBitrate() {
        Collections.sort(bitrates);
        if (bitrates.isEmpty()) {
            return 0;
        }
        double medianBitrate;
        if (bitrates.size() % 2 == 0) {
            medianBitrate = (bitrates.get(bitrates.size() / 2) + bitrates.get(bitrates.size() / 2 - 1) / 2.0);
        } else {
            medianBitrate = bitrates.get(bitrates.size() / 2);
        }
        Log.d(TAG, "Median bitrate is: " + medianBitrate);
        return medianBitrate;
    }

    private void restart() {
        intervalNo = 1;
        finished = false;
        bitrates.clear();
        resetInterval();
    }

    void finish() {
        finished = true;
    }

    private void resetInterval() {
        uploadedBytesSoFar = 0L;
        intervalStartNano = -1;
    }
}