package se.tmm.sensorplanner.plugin;

import android.content.Context;
import android.os.Build;
import android.graphics.drawable.Drawable;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapView;

import java.util.Objects;

import gov.tak.api.commons.graphics.Bitmap;
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/** Offline local planner prototype and explicit synthetic lab transport. */
public final class SensorPlannerPlugin implements IPlugin {
    private final IHostUIService hostUi;
    private final ToolbarItem entry;
    private final ToolbarItem cotEntry;
    private volatile boolean started;
    private LocalPlanner planner;
    private CotRoundTripProbe cotProbe;
    private ShareLabReceiver shareReceiver;

    public SensorPlannerPlugin(IServiceController services) {
        Objects.requireNonNull(services, "services");
        PluginContextProvider provider = Objects.requireNonNull(
                services.getService(PluginContextProvider.class), "PluginContextProvider unavailable");
        Context context = Objects.requireNonNull(provider.getPluginContext(), "Plugin context unavailable");
        hostUi = Objects.requireNonNull(services.getService(IHostUIService.class), "Host UI unavailable");
        Bitmap icon = Objects.requireNonNull(MarshalManager.marshal(
                context.getDrawable(R.drawable.planner_icon), Drawable.class, Bitmap.class),
                "Toolbar icon conversion unavailable");
        entry = new ToolbarItem.Builder(context.getString(R.string.app_name), icon)
                .setIdentifier("se.tmm.sensorplanner.plugin")
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        hostUi.queueEvent(() -> {
                            try {
                                openPlanner(true);
                            } catch (Exception error) {
                                hostUi.showToast("Den lokala prototypen kunde inte startas");
                            }
                        });
                    }
                })
                .build();
        cotEntry = new ToolbarItem.Builder("TMM CoT-labb", icon)
                .setIdentifier("se.tmm.sensorplanner.plugin.cotlab")
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        hostUi.queueEvent(() -> {
                            if (!started) return;
                            try {
                                if (Build.VERSION.SDK_INT >= 26 && shareReceiver == null) {
                                    shareReceiver = new ShareLabReceiver(Objects.requireNonNull(MapView.getMapView()), hostUi);
                                }
                                if (cotProbe == null) cotProbe = new CotRoundTripProbe(
                                        Objects.requireNonNull(MapView.getMapView()), hostUi, () -> {
                                            if (shareReceiver != null) shareReceiver.show();
                                            else hostUi.showToast("Metadatalabbet kräver Android 8 eller senare");
                                        });
                                cotProbe.show();
                            } catch (RuntimeException unavailable) {
                                hostUi.showToast("CoT-labbet kunde inte startas");
                            }
                        });
                    }
                }).build();
    }

    @Override
    public void onStart() {
        if (!started) {
            hostUi.addToolbarItem(entry);
            hostUi.addToolbarItem(cotEntry);
            started = true;
            hostUi.queueEvent(() -> {
                try { openPlanner(false); }
                catch (Exception unavailable) {
                    if (started) hostUi.showToast("Lokala markörer kunde inte återställas. Öppna TMM för att försöka igen.");
                }
            });
        }
    }

    private synchronized void openPlanner(boolean showList) throws Exception {
        if (!started) return;
        if (Build.VERSION.SDK_INT < 26) {
            if (showList) hostUi.showToast("Den lokala prototypen kräver Android 8 eller senare");
            return;
        }
        MapView map = Objects.requireNonNull(MapView.getMapView());
        if (!OfflineMode.isOffline(map.getContext())) {
            if (showList) hostUi.showToast("Prototypen kräver flygplansläge och avstängt nätverk");
            return;
        }
        if (planner == null) planner = new LocalPlanner(map, hostUi);
        if (showList) planner.show();
        else planner.restore();
    }

    @Override
    public void onStop() {
        if (started) {
            started = false;
            hostUi.removeToolbarItem(entry);
            hostUi.removeToolbarItem(cotEntry);
            if (cotProbe != null) {
                cotProbe.close();
                cotProbe = null;
            }
            if (shareReceiver != null) { shareReceiver.close(); shareReceiver = null; }
            synchronized (this) {
                if (planner != null) {
                    LocalPlanner stopping = planner;
                    planner = null;
                    stopping.cancelSharing();
                    hostUi.queueEvent(stopping::close);
                }
            }
        }
    }
}
