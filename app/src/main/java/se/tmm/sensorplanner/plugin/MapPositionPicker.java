package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.AtakMapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import se.tmm.sensorplanner.core.Position;
import java.util.function.Consumer;

/** A temporary screen sight and small dialog, never an ATAK Marker/MapItem or persisted feature. */
@TargetApi(26)
final class MapPositionPicker {
    private final MapView map;
    private final Dialog controls;
    private final PopupWindow sight;
    private final TextView coordinate;
    private final Button accept;
    private final AtakMapView.OnMapMovedListener moved = (view, animate) -> mapUpdate();
    private boolean closed;

    MapPositionPicker(MapView map, String deviceId, Consumer<Position> apply, Runnable finished) {
        this.map = map;
        LinearLayout content = new LinearLayout(map.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));
        content.setBackgroundColor(Color.BLACK);
        coordinate = new TextView(map.getContext());
        coordinate.setTextColor(Color.WHITE);
        coordinate.setTextSize(16);
        TextView instruction = new TextView(map.getContext());
        instruction.setText("Välj plats – " + deviceId + "\nFlytta och zooma kartan under siktet. Inget sparas här.");
        instruction.setTextColor(Color.WHITE);
        content.addView(instruction);
        content.addView(coordinate);
        LinearLayout actions = new LinearLayout(map.getContext());
        accept = new Button(map.getContext());
        accept.setText("Använd plats");
        Button cancel = new Button(map.getContext());
        cancel.setText("Avbryt platsval");
        for (Button button : new Button[]{accept, cancel}) actions.addView(button,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(actions);
        TextView cross = new TextView(map.getContext());
        cross.setText("⊕");
        cross.setTextSize(36);
        cross.setTextColor(Color.CYAN);
        cross.setGravity(Gravity.CENTER);
        cross.setContentDescription("Sikte för vald plats");
        sight = new PopupWindow(cross, dp(48), dp(48), false);
        sight.setTouchable(false);
        controls = new Dialog(map.getContext());
        controls.requestWindowFeature(Window.FEATURE_NO_TITLE);
        controls.setContentView(content);
        controls.setCanceledOnTouchOutside(false);
        controls.setOnDismissListener(ignored -> {
            if (closed) return;
            closed = true;
            map.removeOnMapMovedListener(moved);
            sight.dismiss();
            finished.run();
        });
        accept.setOnClickListener(ignored -> {
            try {
                Position point = position();
                MgrsCoordinates.format(point);
                apply.accept(point);
                close();
            } catch (IllegalArgumentException invalid) {
                coordinate.setText("Platsen kan inte anges i MGRS. Flytta kartan inom 80°S–84°N.");
            }
        });
        cancel.setOnClickListener(ignored -> close());
    }

    void show() {
        controls.show();
        Window window = controls.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.setLayout(Math.min(map.getWidth() - dp(24), dp(560)), ViewGroup.LayoutParams.WRAP_CONTENT);
        sight.showAtLocation(map, Gravity.TOP | Gravity.LEFT, 0, 0);
        map.addOnMapMovedListener(moved);
        mapUpdate();
    }

    private Position position() {
        GeoPoint point = map.getPoint().get();
        return new Position(point.getLatitude(), point.getLongitude());
    }

    private void mapUpdate() {
        map.post(() -> {
            if (closed) return;
            try {
                Position point = position();
                coordinate.setText("MGRS: " + MgrsCoordinates.format(point));
                accept.setEnabled(true);
                PointF screen = map.forward(new GeoPoint(point.latitude, point.longitude));
                int[] origin = new int[2];
                map.getLocationInWindow(origin);
                sight.update(origin[0] + Math.round(screen.x) - dp(24),
                        origin[1] + Math.round(screen.y) - dp(24), -1, -1);
            } catch (RuntimeException unavailable) {
                coordinate.setText("MGRS saknas här. Flytta kartan inom 80°S–84°N.");
                accept.setEnabled(false);
            }
        });
    }

    void close() { controls.dismiss(); }
    private int dp(int value) { return Math.round(value * map.getResources().getDisplayMetrics().density); }
}
