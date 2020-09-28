package com.github.faucamp.simplertmp

import net.ossrs.rtmp.ConnectCheckerRtmp

class BenchmarkRtmpPublisher(connectCheckerRtmp: ConnectCheckerRtmp) : DefaultRtmpPublisher(connectCheckerRtmp) {

    fun publishFakeVideoData(fakeData: ByteArray) {
        rtmpConnection.publishVideoData(fakeData, fakeData.size, 0)
    }
}