package net.ossrs.rtmp

import android.net.TrafficStats
import android.os.Process
import android.util.Log
import com.github.faucamp.simplertmp.DefaultRtmpPublisher

class SrsFlvFakeMuxer(private val connectCheckerRtmp: ConnectCheckerRtmp) {

    private var url: String? = null
    private var connected = false
    private val publisher = DefaultRtmpPublisher(connectCheckerRtmp)
    private var worker: Thread? = null

    private val fakeData = ByteArray(1024 * 1024)

    fun start(rtmpUrl: String) {
        worker = Thread(Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
            if (!connect(rtmpUrl)) {
                return@Runnable
            }
            Log.d("lolx", "connected")
            checkSpeed(fakeData)
            checkSpeed(fakeData.copyOfRange(0, fakeData.size / 2))
            checkSpeed(fakeData.copyOfRange(0, fakeData.size / 4))
            checkSpeed(fakeData.copyOfRange(0, fakeData.size / 8))

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
        publisher.publishFakeData(fakeData)
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
        Thread(Runnable { disconnect(connectCheckerRtmp) }).start()
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