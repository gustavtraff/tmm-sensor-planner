package se.tmm.sensorplanner.plugin;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atak.plugins.impl.PluginContextProvider;

import java.util.Objects;

import gov.tak.api.commons.graphics.Bitmap;
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/** SDK loading probe. It creates no sensor objects or publication adapters. */
public final class SensorPlannerPlugin implements IPlugin {
    private final IHostUIService hostUi;
    private final ToolbarItem entry;
    private boolean started;

    public SensorPlannerPlugin(IServiceController services) {
        Objects.requireNonNull(services, "services");
        PluginContextProvider provider = Objects.requireNonNull(
                services.getService(PluginContextProvider.class), "PluginContextProvider unavailable");
        Context context = Objects.requireNonNull(provider.getPluginContext(), "Plugin context unavailable");
        hostUi = Objects.requireNonNull(services.getService(IHostUIService.class), "Host UI unavailable");
        Bitmap icon = Objects.requireNonNull(MarshalManager.marshal(
                context.getDrawable(R.drawable.planner_icon), Drawable.class, Bitmap.class),
                "Toolbar icon conversion unavailable");
        String message = context.getString(R.string.prototype_message);
        entry = new ToolbarItem.Builder(context.getString(R.string.app_name), icon)
                .setIdentifier("se.tmm.sensorplanner.plugin")
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        hostUi.showToast(message);
                    }
                })
                .build();
    }

    @Override
    public void onStart() {
        if (!started) {
            hostUi.addToolbarItem(entry);
            started = true;
        }
    }

    @Override
    public void onStop() {
        if (started) {
            hostUi.removeToolbarItem(entry);
            started = false;
        }
    }
}
