package net.ossrs.rtmp

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class ObsBitrateAdapter(
        private val streamReader: StreamReader,
        private val maxBitrate: Long,
        private val minBitrate: Long,
        private val initialBitrate: Long
) {
    companion object {
        private val DBR_INC_THRESHOLD_NANO: Long = TimeUnit.SECONDS.toNanos(30)
        private val TAG = "obs"
    }

    private val incBitrate: Long = initialBitrate / 10
    private var incTimeout: Long = 0
    private var previousBitrate: Long = 0
    private var currentBitrate: Long = 0
    private var lastDtsMs: Int = 0

    fun beforeFrameAdded(frame: SrsFlvFrame) {
        if (frame.is_video) {
            addedVideoPacket(frame, pframes = true)
            addedVideoPacket(frame, pframes = false)
            lastDtsMs = frame.dts
        }
    }

    private fun addedVideoPacket(frame: SrsFlvFrame, pframes: Boolean) {
        if (!pframes) {
            increaseBitrateIfNeeded()
        }

        if (streamReader.videoFramesCount() < 5) {
            return
        }

        val buffer_duration_usec = lastDtsMs - first.dts_usec
    }

    private fun increaseBitrateIfNeeded() {
        if (incTimeout == 0L) {
            incTimeout = System.nanoTime() + DBR_INC_THRESHOLD_NANO
        } else if (System.nanoTime() > incTimeout) {
            previousBitrate = currentBitrate
            val increasedBitrate = currentBitrate + incBitrate
            if (updateBitrate(increasedBitrate)) {
                incTimeout = System.nanoTime() + DBR_INC_THRESHOLD_NANO
            }
        }
    }

    private fun updateBitrate(updatedBitrate: Long): Boolean {
        Log.d(TAG, "Trying updated bitrate to $updatedBitrate")
        val newBitrate = min(maxBitrate, max(minBitrate, updatedBitrate))
        return if (newBitrate != currentBitrate) {
            currentBitrate = newBitrate
            streamReader.setBitrate(newBitrate)
            Log.d(TAG, "Bitrate updated from: $previousBitrate to $currentBitrate")
            true
        } else {
            Log.d(TAG, "Bitrate not updated")
            false
        }
    }

}

interface BitrateNotifier {
    fun onBitrateUpdated(bitrate: Long)
}

interface StreamReader {
    fun getBitrate(): Long
    fun setBitrate(bitrate: Long)
    fun videoFramesCount(): Int
    fun videoBufferDurationMs(): Int
}