package se.tmm.sensorplanner.plugin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;

/** Fail-closed offline restriction for local prototype controls. */
final class OfflineMode {
    private OfflineMode() {}
    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    static boolean isOffline(Context context) {
        try {
            ConnectivityManager connectivity = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity == null || context.checkCallingOrSelfPermission(
                    Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            NetworkInfo active = connectivity.getActiveNetworkInfo();
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                    && Settings.Global.getInt(context.getContentResolver(), "wifi_on", 1) == 0
                    && (active == null || !active.isConnectedOrConnecting());
        } catch (SecurityException unavailable) {
            return false;
        }
    }

}
