package net.ossrs.rtmp

import android.os.Process
import android.util.Log
import com.github.faucamp.simplertmp.BenchmarkRtmpPublisher
import kotlin.random.Random

/** Estimate network speed by sending random bytes to given RTMP server
 * @param dataSizeBytes is the amount of data to be send
 * It is not recommended to send data less than 1Mb as the estimation
 * may not be accurate. Don't pass the @param dataSizeBytes too high to avoid OOM.
 */
class NetworkBenchmark(
        private val dataSizeBytes: Int,
        private val connectCheckerRtmp: ConnectCheckerRtmp,
        val listener: SpeedBenchmarkListener
) {

    interface SpeedBenchmarkListener {
        fun onSpeedEstimated(speedMbs: Double)
    }

    private val TAG = "NetoworkBenchmark"
    private var connected = false
    private val publisher = BenchmarkRtmpPublisher(connectCheckerRtmp)
    private var worker: Thread? = null
    private val fakeData = ByteArray(dataSizeBytes).apply {
        Random.nextBytes(this)
    }

    fun start(rtmpUrl: String) {
        worker = Thread(Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
            if (!connect(rtmpUrl)) {
                return@Runnable
            }
            runBenchmark()
            stop(connectCheckerRtmp)
        })
        worker?.start()
    }

    private fun runBenchmark() {
        val sampleDataSize = ByteArray(10 * 1024).apply {
            Random.nextBytes(this)
        }
        val iterations = fakeData.size / sampleDataSize.size
        Log.d(TAG, "iterations: $iterations")

        val startTimeNs = System.nanoTime()
        sendFakeData(ByteArray(dataSizeBytes).apply { Random.nextBytes(this) })
        val sendTime = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
        val speed: Double = fakeData.size / sendTime / 1024.0 / 1024.0 * 8.0

        Log.d(TAG, "speed: $speed")
        listener.onSpeedEstimated(speed)
    }

    private fun sendFakeData(fakeData: ByteArray) {
        publisher.publishFakeVideoData(fakeData)
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
        stop(connectCheckerRtmp)
    }

    private fun stop(connectCheckerRtmp: ConnectCheckerRtmp) {
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