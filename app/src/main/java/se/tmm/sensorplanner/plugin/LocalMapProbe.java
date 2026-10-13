package se.tmm.sensorplanner.plugin;

import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.content.Context;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.BasicPointStyle;

import java.util.Collections;
import java.util.UUID;

import gov.tak.api.ui.IHostUIService;

/** Offline-only SDK experiment with one synthetic point, not a saved TMM draft. */
final class LocalMapProbe {
    private final MapView map;
    private final IHostUIService ui;
    private final FeatureSetDatabase2 store;
    private final FeatureLayer3 layer;
    private final long featureSetId;
    private final String uid = "tmm-probe-" + UUID.randomUUID();
    private long featureId = FeatureDataStore2.FEATURE_ID_NONE;
    private AlertDialog dialog;

    LocalMapProbe(MapView map, IHostUIService ui) throws DataStoreException {
        this.map = map;
        this.ui = ui;
        store = new FeatureSetDatabase2(null);
        try {
            featureSetId = store.insertFeatureSet(new FeatureSet(
                    "tmm-probe", "synthetic", "TMM offline test", Double.MAX_VALUE, 0));
            layer = new FeatureLayer3("TMM offline test", store);
            map.addLayer(MapView.RenderStack.POINT_OVERLAYS, layer);
        } catch (RuntimeException | DataStoreException error) {
            store.dispose();
            throw error;
        }
    }

    void show() {
        if (!isOffline()) {
            ui.showToast("Kartprovet kräver flygplansläge och avstängt nätverk");
            return;
        }
        if (dialog != null) dialog.dismiss();
        dialog = new AlertDialog.Builder(map.getContext())
                .setTitle("TMM kartprov – syntetiska data")
                .setItems(new String[]{"Skapa testpunkt (0°, 0°)",
                        "Flytta testpunkt till 0°, 0,001°", "Ta bort testpunkt"},
                        (ignored, action) -> run(action))
                .setNegativeButton("Stäng", null)
                .create();
        dialog.show();
    }

    private void run(int action) {
        try {
            if (!isOffline()) {
                ui.showToast("Kartprovet kräver flygplansläge och avstängt nätverk");
                return;
            }
            switch (action) {
                case 0:
                    if (featureId != FeatureDataStore2.FEATURE_ID_NONE) {
                        ui.showToast("Testpunkten finns redan");
                        return;
                    }
                    Feature point = new Feature(featureSetId, uid, new Point(0, 0),
                            new BasicPointStyle(Color.CYAN, 24), null);
                    try {
                        featureId = store.insertFeature(point);
                    } finally {
                        point.dispose();
                    }
                    verify(1, 0);
                    map.getMapController().panZoomTo(new GeoPoint(0, 0), 0.0001, false);
                    ui.showToast("Testpunkt skapad – endast i minnet");
                    break;
                case 1:
                    if (featureId == FeatureDataStore2.FEATURE_ID_NONE) {
                        ui.showToast("Skapa en testpunkt först");
                        return;
                    }
                    store.updateFeature(featureId, FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY,
                            null, new Point(0.001, 0), null, null,
                            FeatureDataStore2.UPDATE_ATTRIBUTES_SET);
                    verify(1, 0.001);
                    ui.showToast("Testpunkt flyttad");
                    break;
                case 2:
                    if (featureId != FeatureDataStore2.FEATURE_ID_NONE) {
                        store.deleteFeature(featureId);
                        featureId = FeatureDataStore2.FEATURE_ID_NONE;
                    }
                    verify(0, 0);
                    ui.showToast("Testpunkt borttagen");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown probe action");
            }
        } catch (DataStoreException | RuntimeException error) {
            ui.showNotification(IHostUIService.NotificationStatus.Error,
                    "Kartprovet misslyckades", error);
        }
    }

    // Read back mutations so the probe cannot report success from a write alone.
    private void verify(int count, double longitude) throws DataStoreException {
        if (store.queryFeaturesCount(null) != count || map.getMapItem(uid) != null) {
            throw new IllegalStateException("Unexpected feature count or MapItem registration");
        }
        if (count == 0) return;
        FeatureDataStore2.FeatureQueryParameters query = new FeatureDataStore2.FeatureQueryParameters();
        query.ids = Collections.singleton(featureId);
        try (FeatureCursor cursor = store.queryFeatures(query)) {
            if (!cursor.moveToNext()) throw new IllegalStateException("Probe point is missing");
            Point geometry = (Point) cursor.get().getGeometry();
            if (geometry.getX() != longitude || geometry.getY() != 0 || cursor.moveToNext()) {
                throw new IllegalStateException("Unexpected probe geometry or duplicate");
            }
        }
    }

    // Runs in ATAK; its network-state permission is checked before querying.
    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private boolean isOffline() {
        ConnectivityManager connectivity = (ConnectivityManager) map.getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null || map.getContext().checkCallingOrSelfPermission(
                Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NetworkInfo active = connectivity.getActiveNetworkInfo();
        return Settings.Global.getInt(map.getContext().getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                && Settings.Global.getInt(map.getContext().getContentResolver(), "wifi_on", 1) == 0
                && (active == null || !active.isConnectedOrConnecting());
    }

    void close() {
        if (dialog != null) dialog.dismiss();
        map.removeLayer(MapView.RenderStack.POINT_OVERLAYS, layer);
        store.dispose();
    }
}
