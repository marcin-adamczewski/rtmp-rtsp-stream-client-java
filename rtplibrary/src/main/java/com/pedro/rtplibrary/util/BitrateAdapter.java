package com.pedro.rtplibrary.util;

import android.util.Log;

/**
 * Created by pedro on 11/07/19.
 */
public class BitrateAdapter {

  public interface Listener {
    void onBitrateAdapted(int bitrate);
  }

  private int maxBitrate;
  private int oldBitrate;
  private int averageBitrate;
  private int cont;
  private Listener listener;

  public BitrateAdapter(Listener listener) {
    this.listener = listener;
    reset();
  }

  public void setMaxBitrate(int bitrate) {
    this.maxBitrate = bitrate;
    this.oldBitrate = bitrate;
    reset();
  }

  public void adaptBitrate(long actualBitrate) {
    averageBitrate += actualBitrate;
    averageBitrate /= 2;
    cont++;
    if (cont >= 5) {
      if (listener != null && maxBitrate != 0) {
        listener.onBitrateAdapted(getBitrateAdapted(averageBitrate));
        reset();
      }
    }
  }

  private int getBitrateAdapted(int averageBitrate) {
    if (averageBitrate >= maxBitrate) { //You have high speed and max bitrate. Keep max speed
      oldBitrate = maxBitrate;
      Log.d("lol6", "bitrate max speed: " + oldBitrate / 1024.0 / 1024.0);
    } else if (averageBitrate <= oldBitrate * 0.9f) { //You have low speed and bitrate too high. Reduce bitrate by 10%.
      oldBitrate = (int) (averageBitrate * 0.9);
      Log.d("lol6", "bitrate reduced: " + oldBitrate / 1024.0 / 1024.0);
    } else { //You have high speed and bitrate too low. Increase bitrate by 10%.
      oldBitrate = (int) (averageBitrate * 1.1);
      if (oldBitrate > maxBitrate) oldBitrate = maxBitrate;
      Log.d("lol6", "bitrate increased: " + oldBitrate / 1024.0 / 1024.0);
    }
    return oldBitrate;
  }

  public void reset() {
    averageBitrate = 0;
    cont = 0;
  }
}
