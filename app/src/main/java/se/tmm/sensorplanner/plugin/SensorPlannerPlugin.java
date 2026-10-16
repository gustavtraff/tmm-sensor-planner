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
    private boolean started;
    private LocalPlanner planner;
    private CotRoundTripProbe cotProbe;

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
                            if (!started) return;
                            try {
                                if (Build.VERSION.SDK_INT < 26) {
                                    hostUi.showToast("Den lokala prototypen kräver Android 8 eller senare");
                                    return;
                                }
                                if (!OfflineMode.isOffline(MapView.getMapView().getContext())) {
                                    hostUi.showToast("Prototypen kräver flygplansläge och avstängt nätverk");
                                    return;
                                }
                                if (planner == null) planner = new LocalPlanner(
                                        Objects.requireNonNull(MapView.getMapView()), hostUi);
                                planner.show();
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
                                if (cotProbe == null) cotProbe = new CotRoundTripProbe(
                                        Objects.requireNonNull(MapView.getMapView()), hostUi);
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
        }
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
            if (planner != null) {
                LocalPlanner stopping = planner;
                planner = null;
                hostUi.queueEvent(stopping::close);
            }
        }
    }
}
