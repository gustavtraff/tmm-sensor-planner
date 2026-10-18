package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.graphics.Color;
import android.graphics.PointF;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.feature.style.LabelPointStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import se.tmm.sensorplanner.core.DeviceStatus;
import se.tmm.sensorplanner.core.TmmDraft;

/** An owned, in-memory rendering layer; drafts never become ordinary ATAK MapItems. */
@TargetApi(26)
final class LocalDraftLayer {
    private static final double EARTH_RADIUS_METERS = 6371008.8;
    private final MapView map;
    private final GLMapView renderer;
    private final Consumer<UUID> select;
    private final MapEventDispatcher.MapEventDispatchListener clickListener = this::onMapClick;
    private final FeatureSetDatabase2 store;
    private final FeatureLayer3 layer;
    private long featureSetId = FeatureDataStore2.FEATURESET_ID_NONE;
    private List<TmmDraft> displayed = Collections.emptyList();
    private boolean closed;

    LocalDraftLayer(MapView map, Consumer<UUID> select) throws DataStoreException {
        this.map = map;
        this.select = select;
        // Keep the owning queue; ATAK clears its current surface before deferred cleanup.
        GLMapSurface surface = map.getGLSurface();
        renderer = surface == null ? null : surface.getGLMapView();
        if (renderer == null) throw new IllegalStateException("Map renderer unavailable");
        store = new FeatureSetDatabase2(null);
        layer = new FeatureLayer3("TMM – lokala utkast", store);
        try {
            render(Collections.emptyList());
        } catch (RuntimeException | DataStoreException error) {
            store.dispose();
            throw error;
        }
        try {
            map.addLayer(MapView.RenderStack.POINT_OVERLAYS, layer);
            map.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_CLICK, clickListener);
        } catch (RuntimeException error) {
            close();
            throw error;
        }
    }

    void render(List<TmmDraft> drafts) throws DataStoreException {
        if (closed) throw new IllegalStateException("Draft layer is closed");
        List<TmmDraft> nextDrafts = new ArrayList<>(drafts);
        try {
            store.acquireModifyLock(true);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DataStoreException("Draft rendering was interrupted", error);
        }
        long nextSetId = FeatureDataStore2.FEATURESET_ID_NONE;
        try {
            nextSetId = store.insertFeatureSet(new FeatureSet(
                    "tmm-local", "draft", "TMM – lokala utkast", Double.MAX_VALUE, 0));
            int count = 0;
            for (TmmDraft draft : nextDrafts) {
                int color = color(draft.status);
                String label = letter(draft) + " · " + draft.displayId + " · "
                        + statusLabel(draft.status) + " · Lokal";
                insert(store, nextSetId, label,
                        new Point(draft.position.longitude, draft.position.latitude),
                        new CompositeStyle(new Style[]{new IconPointStyle(color,
                                iconUri(draft), 32, 32, 0, 0, 0, false),
                                new LabelPointStyle(label, Color.WHITE, Color.BLACK,
                                        LabelPointStyle.ScrollMode.OFF, 14, 0, 48, 0, 1, 0, false, 14, 1)}));
                count++;
                if (draft.bearingDeg != null) {
                    // A fixed 100 m direction cue, never a sensor coverage/range claim.
                    GeoPoint origin = point(draft);
                    GeoPoint tip = destination(origin, draft.bearingDeg, 100);
                    GeoPoint left = destination(tip, draft.bearingDeg + 150, 25);
                    GeoPoint right = destination(tip, draft.bearingDeg - 150, 25);
                    LineString arrow = new LineString(2);
                    arrow.addPoint(origin.getLongitude(), origin.getLatitude());
                    arrow.addPoint(tip.getLongitude(), tip.getLatitude());
                    arrow.addPoint(left.getLongitude(), left.getLatitude());
                    arrow.addPoint(tip.getLongitude(), tip.getLatitude());
                    arrow.addPoint(right.getLongitude(), right.getLatitude());
                    insert(store, nextSetId, null, arrow, new BasicStrokeStyle(color, 3));
                    count++;
                }
            }
            FeatureDataStore2.FeatureQueryParameters query = new FeatureDataStore2.FeatureQueryParameters();
            query.featureSetFilter = new FeatureDataStore2.FeatureSetQueryParameters();
            query.featureSetFilter.ids = Collections.singleton(nextSetId);
            if (store.queryFeaturesCount(query) != count) {
                throw new IllegalStateException("Draft rendering count mismatch");
            }
            if (featureSetId != FeatureDataStore2.FEATURESET_ID_NONE) {
                store.deleteFeatureSet(featureSetId);
            }
            featureSetId = nextSetId;
            displayed = nextDrafts;
        } catch (RuntimeException | DataStoreException error) {
            if (nextSetId != FeatureDataStore2.FEATURESET_ID_NONE) {
                try {
                    store.deleteFeatureSet(nextSetId);
                } catch (RuntimeException | DataStoreException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw error;
        } finally {
            store.releaseModifyLock();
        }
    }

    void center(TmmDraft draft) {
        if (!closed) map.getMapController().panZoomTo(point(draft), 0.0001, false);
    }

    private void onMapClick(MapEvent event) {
        PointF click = event.getPointF();
        if (closed || click == null) return;
        double radius = 32 * map.getContext().getResources().getDisplayMetrics().density;
        double bestDistance = radius * radius;
        UUID selected = null;
        for (TmmDraft draft : displayed) {
            PointF screen = map.forward(point(draft));
            if (screen == null) continue;
            double dx = screen.x - click.x;
            double dy = screen.y - click.y;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                selected = draft.uid;
            }
        }
        if (selected != null) {
            UUID uid = selected;
            map.post(() -> { if (!closed) select.accept(uid); });
        }
    }

    private static void insert(FeatureSetDatabase2 target, long setId, String label,
                               Geometry geometry, Style style) throws DataStoreException {
        Feature feature = new Feature(setId, label, geometry, style, null);
        try {
            target.insertFeature(feature);
        } finally {
            feature.dispose();
            geometry.dispose();
            style.dispose();
        }
    }

    private static GeoPoint point(TmmDraft draft) {
        return new GeoPoint(draft.position.latitude, draft.position.longitude);
    }

    private static GeoPoint destination(GeoPoint start, double bearing, double meters) {
        double latitude = Math.toRadians(start.getLatitude());
        double longitude = Math.toRadians(start.getLongitude());
        double heading = Math.toRadians(bearing);
        double arc = meters / EARTH_RADIUS_METERS;
        double sine = Math.sin(latitude) * Math.cos(arc)
                + Math.cos(latitude) * Math.sin(arc) * Math.cos(heading);
        double endLatitude = Math.asin(Math.max(-1, Math.min(1, sine)));
        double endLongitude = longitude + Math.atan2(Math.sin(heading) * Math.sin(arc)
                * Math.cos(latitude), Math.cos(arc) - Math.sin(latitude) * Math.sin(endLatitude));
        return new GeoPoint(Math.toDegrees(endLatitude),
                ((Math.toDegrees(endLongitude) + 540) % 360) - 180);
    }

    private static String letter(TmmDraft draft) {
        switch (draft.type) {
            case MINI: return "M";
            case SCOUT: return "S";
            case PIR: return "P";
            case GATEWAY: return "G";
            default: throw new IllegalArgumentException("Unknown device type");
        }
    }

    private static String iconUri(TmmDraft draft) {
        int resource;
        switch (draft.type) {
            case MINI: resource = R.drawable.tmm_mini; break;
            case SCOUT: resource = R.drawable.tmm_scout; break;
            case PIR: resource = R.drawable.tmm_pir; break;
            case GATEWAY: resource = R.drawable.tmm_gateway; break;
            default: throw new IllegalArgumentException("Unknown device type");
        }
        return "android.resource://se.tmm.sensorplanner.plugin/" + resource;
    }

    static String statusLabel(DeviceStatus status) {
        switch (status) {
            case PLANNED: return "Planerad";
            case DEPLOYED: return "Utplacerad";
            case ACTIVE: return "Aktiv";
            case RETRIEVED: return "Intagen";
            case CANCELLED: return "Avbruten";
            default: throw new IllegalArgumentException("Unknown device status");
        }
    }

    private static int color(DeviceStatus status) {
        switch (status) {
            case PLANNED: return Color.YELLOW;
            case DEPLOYED: return Color.GREEN;
            case ACTIVE: return Color.GREEN;
            case RETRIEVED: return Color.rgb(204, 204, 204);
            case CANCELLED: return Color.rgb(204, 204, 204);
            default: throw new IllegalArgumentException("Unknown device status");
        }
    }

    void close() {
        if (closed) return;
        closed = true;
        map.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_CLICK, clickListener);
        displayed = Collections.emptyList();
        map.removeLayer(MapView.RenderStack.POINT_OVERLAYS, layer);
        // GLMultiLayer queues renderer release; the same GL queue then owns disposal.
        renderer.queueEvent(store::dispose);
    }
}
