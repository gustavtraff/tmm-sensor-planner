package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import java.util.*;
import se.tmm.sensorplanner.plugin.publication.PublicationCot;

/**
 * #299 B2 (owner choice 2026-11-20): TMM's own icon represents a device that also has a native map copy.
 * The native icon is made invisible on this phone only: ICON_INVISIBLE keeps the marker's hit area
 * (GLMarker2 sizes it from the icon unless ICON_GONE), so Data Sync Map Select and the TMM menu still work.
 * Icon visibility is not part of ATAK's CoT export, so nothing published changes. Unload restores the icons.
 *
 * <p>Owner choice 2026-11-20: no off-screen halo ring for TMM devices, other items keep theirs.
 * GLOffscreenIndicators.shouldDraw skips markers whose local "disable_offscreen_indicator" metadata is true;
 * no CoT handler reads that key, so it stays on this phone too.
 */
@TargetApi(26)
final class NativeCopyIcons implements AutoCloseable {
    private final MapView map;
    private final MapEventDispatcher.MapEventDispatchListener added = event -> apply(event.getItem());
    private static final String NO_HALO = "disable_offscreen_indicator";
    private final Set<Marker> hidden = new HashSet<>();
    private Set<String> uids = Collections.emptySet();
    private boolean closed;

    NativeCopyIcons(MapView map) {
        this.map = Objects.requireNonNull(map);
        map.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_ADDED, added);
    }

    /** UI thread: the current TMM devices; returns those that have a native copy now. */
    Set<UUID> show(Collection<UUID> devices) {
        Set<String> next = new HashSet<>();
        for (UUID uid : devices) next.add(uid.toString());
        uids = next;
        for (Iterator<Marker> it = hidden.iterator(); it.hasNext(); ) {
            Marker marker = it.next();
            if (!next.contains(marker.getUID()) || map.getMapItem(marker.getUID()) != marker) { restore(marker); it.remove(); }
        }
        Set<UUID> copies = new HashSet<>();
        for (UUID uid : devices) if (apply(map.getMapItem(uid.toString()))) copies.add(uid);
        return copies;
    }

    private boolean apply(MapItem item) {
        if (closed || !(item instanceof Marker) || !PublicationCot.TYPE.equals(item.getType()) || !uids.contains(item.getUID())) return false;
        Marker marker = (Marker) item;
        if (marker.getIconVisibility() == Marker.ICON_VISIBLE) { marker.setIconVisibility(Marker.ICON_INVISIBLE); hidden.add(marker); }
        if (!marker.hasMetaValue(NO_HALO)) { marker.setMetaBoolean(NO_HALO, true); hidden.add(marker); }
        return marker.getIconVisibility() == Marker.ICON_INVISIBLE;
    }

    private static void restore(Marker marker) {
        if (marker.getIconVisibility() == Marker.ICON_INVISIBLE) marker.setIconVisibility(Marker.ICON_VISIBLE);
        if (marker.getMetaBoolean(NO_HALO, false)) marker.removeMetaData(NO_HALO);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        map.getMapEventDispatcher().removeMapEventListener(MapEvent.ITEM_ADDED, added);
        for (Marker marker : hidden) restore(marker);
        hidden.clear();
    }
}
