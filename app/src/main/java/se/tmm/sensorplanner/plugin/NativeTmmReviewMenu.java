package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.graphics.Color;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.UriMapDataRef;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.menu.MapMenuHandler;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.widgets.AbstractButtonWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetBackground;
import com.atakmap.android.widgets.WidgetIcon;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import se.tmm.sensorplanner.plugin.local.TmmMapReview;
import se.tmm.sensorplanner.plugin.publication.PublicationCot;

/** A transient native menu for accepted TMM devices; never changes a MapItem or its export. */
@TargetApi(26)
final class NativeTmmReviewMenu implements AutoCloseable {
    private final MapView map;
    private final MapMenuReceiver receiver;
    private final TmmMapReview review;
    private final MapMenuHandler handler = this::updateMenu;
    private final WidgetIcon icon;
    private MapItem displayed;
    private boolean closed;

    NativeTmmReviewMenu(MapView map, Predicate<UUID> accepted, Consumer<UUID> open) {
        this.map = Objects.requireNonNull(map);
        receiver = Objects.requireNonNull(MapMenuReceiver.getInstance(), "Map menu unavailable");
        review = new TmmMapReview(accepted, open);
        // This SDK does not render radial button text; use an original labelled bitmap.
        icon = new WidgetIcon.Builder().setAnchor(48, 24).setSize(96, 48)
                .setImageRef(0, new UriMapDataRef("android.resource://se.tmm.sensorplanner.plugin/" + R.drawable.tmm_review))
                .build();
        // Run after native handlers, leaving every unrecognized item unchanged.
        if (!receiver.registerMapMenuHandler(handler, Integer.MAX_VALUE))
            throw new IllegalStateException("TMM map menu unavailable");
    }

    private void updateMenu(MapItem item, MapMenuWidget menu) {
        if (closed || item == null || menu == null || !PublicationCot.TYPE.equals(item.getType())) return;
        String uid = item.getUID();
        if (review.match(uid) == null) return;
        MapMenuButtonWidget button = new MapMenuButtonWidget(map.getContext());
        button.setName("Öppna i TMM");
        button.setText("Öppna i TMM");
        button.setIcon(icon);
        button.setBackground(new WidgetBackground.Builder().setColor(0, Color.argb(230, 23, 49, 59))
                .setColor(AbstractButtonWidget.STATE_PRESSED, Color.rgb(8, 126, 139)).build());
        button.setOrientation(-90, 56);
        button.setButtonSize(120, 88);
        button.setOnClickAction((view, selected) -> {
            if (closed || selected != item || map.getMapItem(uid) != item || review.match(uid) == null) return;
            receiver.hideMenu();
            map.post(() -> { if (!closed && map.getMapItem(uid) == item) review.open(uid); });
        });
        for (MapWidget child : new ArrayList<>(menu.getChildWidgets())) menu.removeWidget(child);
        menu.setCoveredAngle(120);
        menu.setStartAngle(-90);
        menu.setInnerRadius(56);
        menu.setButtonWidth(88);
        menu.addWidget(button);
        displayed = item;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        review.close();
        receiver.unregisterMapMenuHandler(handler);
        if (displayed != null && MapMenuReceiver.getCurrentItem() == displayed) receiver.hideMenu();
        displayed = null;
    }
}
