package net.ossrs.rtmp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ConnectionParams {
    private final String url;
    @Nullable
    private final String user;
    @Nullable
    private final String password;

    public ConnectionParams(String url, @Nullable String user, @Nullable String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public String getUrl() {
        return url;
    }

    @Nullable
    public String getUser() {
        return user;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    public static ConnectionParams simple(@NonNull String url) {
        return new ConnectionParams(url, null, null);
    }
}


