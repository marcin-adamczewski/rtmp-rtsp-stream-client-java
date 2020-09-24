package net.ossrs.rtmp

import android.os.Process
import android.util.Log
import com.github.faucamp.simplertmp.BenchmarkRtmpPublisher
import kotlin.random.Random

class NetworkBenchmark(
        dataSizeBytes: Int,
        private val rounds: Int,
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
    private val speeds = mutableListOf<Double>()

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
        speeds.clear()
        repeat(rounds) { index ->
            val speed = checkSpeed(fakeData.copyOfRange(0, fakeData.size / (index + 1)))
            speeds.add(speed)
        }
        val medianSpeed = getMedianSpeed()
        listener.onSpeedEstimated(medianSpeed)
    }

    private fun checkSpeed(fakeData: ByteArray): Double {
        val dataSize: Int = fakeData.size
        val startTimeNs = System.nanoTime()
        sendFakeData(fakeData)
        val sendTime = (System.nanoTime() - startTimeNs) / 1000.0 / 1000.0 / 1000.0
        val speed: Double = dataSize / sendTime / 1024.0 / 1024.0 * 8.0
        Log.d(TAG, "Checking speed of sending: ${dataSize / 1024.0 / 1024.0 * 8} Mb")
        Log.d(TAG, "speed: $speed")
        return speed
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
        }
        worker = null
        Thread { disconnect(connectCheckerRtmp) }.start()
    }

    private fun disconnect(connectChecker: ConnectCheckerRtmp?) {
        try {
            publisher.close()
        } catch (e: IllegalStateException) {
            // Ignore illegal state.
        }
        connected = false
        connectChecker?.onDisconnectRtmp()
    }

    private fun getMedianSpeed(): Double {
        speeds.sort()
        if (speeds.isEmpty()) {
            return 0.0
        }
        return if (speeds.size % 2 == 0) {
                (speeds[speeds.size / 2] + speeds[speeds.size / 2 - 1]) / 2.0
            } else {
                speeds[speeds.size / 2]
            }
    }
}