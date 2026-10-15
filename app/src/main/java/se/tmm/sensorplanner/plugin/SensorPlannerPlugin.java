package se.tmm.sensorplanner.plugin;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.feature.DataStoreException;

import java.util.Objects;

import gov.tak.api.commons.graphics.Bitmap;
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/** SDK experiments for local rendering and explicit synthetic lab transport. */
public final class SensorPlannerPlugin implements IPlugin {
    private final IHostUIService hostUi;
    private final ToolbarItem entry;
    private final ToolbarItem cotEntry;
    private boolean started;
    private LocalMapProbe probe;
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
                                if (probe == null) {
                                    probe = new LocalMapProbe(Objects.requireNonNull(
                                            MapView.getMapView(), "Map view unavailable"), hostUi);
                                }
                                probe.show();
                            } catch (DataStoreException | RuntimeException error) {
                                hostUi.showNotification(IHostUIService.NotificationStatus.Error,
                                        "Kartprovet kunde inte startas", error);
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
            if (probe != null) {
                probe.close();
                probe = null;
            }
        }
    }
}
