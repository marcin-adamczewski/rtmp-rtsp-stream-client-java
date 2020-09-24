package net.ossrs.rtmp

import android.net.TrafficStats
import android.os.Process
import android.util.Log
import com.github.faucamp.simplertmp.BenchmarkRtmpPublisher
import com.github.faucamp.simplertmp.DefaultRtmpPublisher
import kotlin.random.Random

class SrsFlvBenchmarkMuxer(
        dataSizeBytes: Int,
        private val rounds: Int,
        private val connectCheckerRtmp: ConnectCheckerRtmp
) {

    private var url: String? = null
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
            Log.d("lolx", "connected")
            repeat(rounds) { index ->
                checkSpeed(fakeData.copyOfRange(0, fakeData.size / (index + 1)))
            }
            stop(connectCheckerRtmp)
        })
        worker?.start()
    }

    private fun checkSpeed(fakeData: ByteArray) {
        val dataSize: Int = fakeData.size
        val transferBefore = TrafficStats.getTotalTxBytes()
        val startTimeNs = System.nanoTime()
        sendFakeData(fakeData)
        val sendTime = (System.nanoTime() - startTimeNs) / 1000.0 / 1000.0 / 1000.0
        val totalTransfer = TrafficStats.getTotalTxBytes() - transferBefore
        val speed: Double = dataSize / sendTime
        val speed2: Double = totalTransfer / sendTime
        Log.d("lolx", "Checking speed of sending: ${dataSize / 1024.0 / 1024.0 * 8} Mb")
        Log.d("lolx", "Total transfer ${totalTransfer / 1024.0 / 1024.0 * 8} Mb")
        Log.d("lolx", "speed: ${speed / 1024.0 / 1024.0 * 8.0}")
        Log.d("lolx", "speed2: ${speed2 / 1024.0 / 1024.0 * 8.0}")
        Log.d("lolx", "time: $sendTime")
    }

    private fun sendFakeData(fakeData: ByteArray) {
        publisher.publishFakeVideoData(fakeData)
    }

    private fun connect(serverUrl: String): Boolean {
        url = serverUrl
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
}