package net.ossrs.rtmp;

import androidx.annotation.NonNull;

public class ConnectCheckerRtmpAdapter implements ConnectCheckerRtmp {
    @Override
    public void onConnectionSuccessRtmp() {
    }

    @Override
    public void onConnectionFailedRtmp(@NonNull String reason) {
    }

    @Override
    public void onNewBitrateRtmp(long bitrate) {
    }

    @Override
    public void onDisconnectRtmp() {
    }

    @Override
    public void onAuthErrorRtmp() {
    }

    @Override
    public void onAuthSuccessRtmp() {
    }
}
