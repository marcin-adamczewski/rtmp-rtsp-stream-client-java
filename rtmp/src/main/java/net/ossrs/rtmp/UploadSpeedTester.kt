package net.ossrs.rtmp

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.github.faucamp.simplertmp.SpeedTestRtmpPublisher

/** Estimates network upload speed by sending random bytes to given RTMP server.
 * @param dataSizeBytes is the amount of data to be send
 * It is not recommended to send data less than 2Mb as the estimation
 * may not be accurate.
 */
class UploadSpeedTester(
        private val dataSizeBytes: Int,
        private val timeout: Int,
        private val connectCheckerRtmp: ConnectCheckerRtmp,
        val listener: SpeedTesterListener
) {

    interface SpeedTesterListener {
        fun onSpeedEstimated(speedMbs: Double)
        fun onTimeout()
        fun onError(e: Exception)
    }

    private val TAG = "UploadSpeedTester"
    private var connected = false
    private val publisher = SpeedTestRtmpPublisher(connectCheckerRtmp).apply {
        // We have to set lower buffer size, otherwise upload speed estimation may be inaccurate.
        setSendBufferSize(40 * 1024)
    }
    private var worker: Thread? = null
    @Volatile private var stopped = false

    fun start(rtmpUrl: String) {
        try {
            worker = Thread(Runnable {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                if (!connect(rtmpUrl)) {
                    return@Runnable
                }
                handleTimeout()
                runTest()
                stop()
            })
            worker?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error when running test: $e")
            listener.onError(e)
        }
    }

    private fun handleTimeout() {
        if (timeout > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!stopped) {
                    listener.onTimeout()
                    stop()
                }
            }, timeout * 1000L)
        }
    }

    private fun runTest() {
        val startTimeNs = System.nanoTime()
        sendFakeDataInChunks()
        val sendTime = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
        val speed: Double = dataSizeBytes / sendTime / 1024.0 / 1024.0 * 8.0
        if (!stopped) {
            listener.onSpeedEstimated(speed)
        }
    }

    // The server may not accept big chunks so we need to split it to smaller ones.
    private fun sendFakeDataInChunks() {
        val partSizeBytes = 200 * 1024
        val parts = dataSizeBytes / partSizeBytes
        val lastPartBytes = dataSizeBytes - (parts * partSizeBytes)

        repeat(parts) {
            publisher.publishFakeVideoData(ByteArray(partSizeBytes))
        }
        if (lastPartBytes > 0) {
            publisher.publishFakeVideoData(ByteArray(lastPartBytes))
        }
    }

    private fun connect(serverUrl: String): Boolean {
        if (!connected) {
            if (publisher.connect(serverUrl)) {
                connected = publisher.publish("live")
            }
        }
        return connected
    }

    fun stop() {
        if (stopped) return else stopped = true
        worker?.interrupt()
        try {
            worker?.join(100)
        } catch (e: InterruptedException) {
            worker?.interrupt()
        } finally {
            worker = null
            Thread { disconnect(connectCheckerRtmp) }.start()
        }
    }

    private fun disconnect(connectChecker: ConnectCheckerRtmp?) {
        try {
            publisher.close()
        } catch (e: IllegalStateException) {
            // Ignore illegal state.
        } finally {
            connected = false
            connectChecker?.onDisconnectRtmp()
        }
    }
}