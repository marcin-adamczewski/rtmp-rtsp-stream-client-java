package net.ossrs.rtmp.bitrateadjuster;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkStateManager {

    interface NetworkTypeListener {
        void onNetworkChanged(NetworkType networkType);
    }

    private static final String TAG = "NetworkStateManager";
    private final ConnectivityManager connectivityManager;
    @Nullable private NetworkTypeListener listener;

    public NetworkStateManager(Context context) {
        connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.d(TAG, "onAvailable");
                notifyNetworkChanged();
            }

            // Keep in mind that onLost callback will be called also when switching from WiFi to Cellular.
            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                Log.d(TAG, "onLost");
                notifyNetworkChanged();
            }
        };
        NetworkRequest networkRequest = new NetworkRequest.Builder().build();
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    public void setListener(@Nullable NetworkTypeListener listener) {
        this.listener = listener;
    }

    private void notifyNetworkChanged() {
        if (listener != null) {
            listener.onNetworkChanged(getNetworkType());
        }
    }

    @NonNull
    private NetworkType getNetworkType() {
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo == null || !activeNetworkInfo.isConnected()) {
            return NetworkType.NO_CONNECTION;
        }

        NetworkType networkType;
        switch (activeNetworkInfo.getType()) {
            case ConnectivityManager.TYPE_WIFI:
                networkType = NetworkType.WIFI;
                break;
            case ConnectivityManager.TYPE_MOBILE:
                if (activeNetworkInfo.getSubtype() == TelephonyManager.NETWORK_TYPE_LTE) {
                    networkType = NetworkType.FOUR_G;
                } else {
                    networkType = NetworkType.THREE_G;
                }
                break;
            default:
                networkType = NetworkType.OTHER;
        }
        return networkType;
    }
}