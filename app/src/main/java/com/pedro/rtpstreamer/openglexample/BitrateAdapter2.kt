package com.pedro.rtpstreamer.openglexample

import android.util.Log
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import net.ossrs.rtmp.SrsFlvMuxer

class BitrateAdapter2(
        private val rtmpCamera: RtmpCamera2,
        private val maxBitrate: Int,
        private val minBitrate: Int
) : SrsFlvMuxer.TransferDiffListener {

    private var previouslyIncreased = false
    private var startIncreaseBlockThresholdMs = 0L
    private var canIncrease = true

    init {
        rtmpCamera.setTransferDiffListener(this)
    }

    override fun onDiffCalculated(diffBytes: Double, diffBytesSystem: Double, framesInBuffer: Double) {
        if (!canIncrease && System.currentTimeMillis() - startIncreaseBlockThresholdMs > 15_000) {
            canIncrease = true
        }

        Log.d("lol10", "frames in buffer: $framesInBuffer")
        Log.d("lol10", "diffMegaBits $diffBytes")
        Log.d("lol10", "diffMegaBitsSystsem $diffBytesSystem")


        if (diffBytes <= 2000 && diffBytesSystem <= 2000) {
            if (canIncrease) {
                val newBitrate = rtmpCamera.bitrate * 1.05
                Log.d("lol9", "increasing")
                setBitrate(newBitrate.toInt())
                previouslyIncreased = true
            }
        } else {
            val newBitrate = rtmpCamera.bitrate - (1.3 * Math.abs(diffBytesSystem) * 8.0)
            Log.d("lol9", "reducing")
            setBitrate(newBitrate.toInt())
            if (previouslyIncreased) {
                canIncrease = false
                startIncreaseBlockThresholdMs = System.currentTimeMillis()
            }
            previouslyIncreased = false
        }
    }

    private fun setBitrate(bitrate: Int) {
        Log.d("lol9", "bitrate before: " + rtmpCamera.bitrate / 1024.0 / 1024.0)
        rtmpCamera.setVideoBitrateOnFly(Math.min(Math.max(minBitrate, bitrate), maxBitrate))
        Log.d("lol9", "bitrate after: " + rtmpCamera.bitrate / 1024.0 / 1024.0)
    }
}