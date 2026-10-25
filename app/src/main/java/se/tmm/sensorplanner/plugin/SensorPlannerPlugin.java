package se.tmm.sensorplanner.plugin;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.graphics.drawable.Drawable;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapView;

import java.util.Objects;

import se.tmm.sensorplanner.plugin.local.LocalTrialSession;
import se.tmm.sensorplanner.images.LocalImages;

import gov.tak.api.commons.graphics.Bitmap;
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/** Local planner with explicit connected-trial consent and separate guarded lab transport. */
public final class SensorPlannerPlugin implements IPlugin {
    private final IHostUIService hostUi;
    private final ToolbarItem entry;
    private final ToolbarItem cotEntry;
    private final String imageCopyNotice;
    private volatile boolean started;
    private final LocalTrialSession localSession = new LocalTrialSession();
    private volatile AlertDialog trialConsent;
    private LocalPlanner planner;
    private CotRoundTripProbe cotProbe;
    private ShareLabReceiver shareReceiver;
    private PublicationImportProbe publicationProbe;
    private AlertDialog labMenu;

    public SensorPlannerPlugin(IServiceController services) {
        Objects.requireNonNull(services, "services");
        PluginContextProvider provider = Objects.requireNonNull(
                services.getService(PluginContextProvider.class), "PluginContextProvider unavailable");
        Context context = Objects.requireNonNull(provider.getPluginContext(), "Plugin context unavailable");
        imageCopyNotice = context.getString(R.string.local_image_copy_notice, LocalImages.MAX_COPY_EDGE);
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
                            if (!labAllowed()) {
                                hostUi.showToast("CoT-labbet är avstängt under uppkopplat lokalt test och kräver annars offline-läge");
                                return;
                            }
                            if (labMenu != null) labMenu.dismiss();
                            labMenu = new AlertDialog.Builder(MapView.getMapView().getContext())
                                    .setTitle("Välj isolerat labbprov")
                                    .setItems(new String[]{"Tidigare CoT-labb", "Syntetiskt SDK-importprov"}, (d, which) -> {
                                        if (!started || !labAllowed()) return;
                                        try {
                                            if (which == 0) openCotLab();
                                            else openPublicationLab();
                                        } catch (RuntimeException unavailable) {
                                            hostUi.showToast("Labbprovet kunde inte startas");
                                        }
                                    }).setNegativeButton("Avbryt", null).create();
                            labMenu.show();
                        });
                    }
                }).build();
    }

    private void openCotLab() {
        if (publicationProbe != null) { publicationProbe.close(); publicationProbe = null; }
        if (Build.VERSION.SDK_INT >= 26 && shareReceiver == null) {
            shareReceiver = new ShareLabReceiver(Objects.requireNonNull(MapView.getMapView()), hostUi);
        }
        if (cotProbe == null) cotProbe = new CotRoundTripProbe(
                Objects.requireNonNull(MapView.getMapView()), hostUi, () -> {
                    if (shareReceiver != null) shareReceiver.show();
                    else hostUi.showToast("Metadatalabbet kräver Android 8 eller senare");
                });
        cotProbe.show();
    }

    private void openPublicationLab() {
        if (Build.VERSION.SDK_INT < 26) {
            hostUi.showToast("SDK-importprovet kräver Android 8 eller senare"); return;
        }
        // The old waypoint-only metadata handler must not consume the sensor experiment's detail.
        if (cotProbe != null) { cotProbe.close(); cotProbe = null; }
        if (shareReceiver != null) { shareReceiver.close(); shareReceiver = null; }
        if (publicationProbe == null) publicationProbe = new PublicationImportProbe(
                Objects.requireNonNull(MapView.getMapView()), hostUi, this::labAllowed);
        publicationProbe.show();
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
        if (!localAllowed()) {
            if (showList && trialConsent == null) {
                trialConsent = new AlertDialog.Builder(map.getContext())
                        .setTitle("Uppkopplat lokalt test")
                        .setMessage("Endast syntetiska testdata i en separat testmiljö. "
                                + "TMM:s delnings- och CoT-labbfunktioner är avstängda i detta läge. "
                                + "Testläget gäller tills pluginet avlastas (Unload) eller ATAK startas om.")
                        .setNegativeButton("Avbryt", null)
                        .setPositiveButton("Starta lokalt test", (dialog, which) -> {
                            if (!started) return;
                            localSession.startConnectedTrial();
                            if (planner != null) planner.disableSharing();
                            if (labMenu != null) { labMenu.dismiss(); labMenu = null; }
                            if (publicationProbe != null) { publicationProbe.close(); publicationProbe = null; }
                            if (cotProbe != null) { cotProbe.close(); cotProbe = null; }
                            if (shareReceiver != null) { shareReceiver.close(); shareReceiver = null; }
                            try { openPlanner(true); }
                            catch (Exception unavailable) { hostUi.showToast("Det lokala testet kunde inte startas"); }
                        }).create();
                trialConsent.setOnDismissListener(dialog -> { if (trialConsent == dialog) trialConsent = null; });
                trialConsent.show();
            }
            return;
        }
        if (planner == null) planner = new LocalPlanner(map, hostUi, this::localAllowed, this::labAllowed, imageCopyNotice);
        if (showList) planner.show();
        else planner.restore();
    }

    private boolean localAllowed() {
        return started && localSession.canUseLocal(OfflineMode.isOffline(MapView.getMapView().getContext()));
    }

    private boolean labAllowed() {
        return started && localSession.canUseLab(OfflineMode.isOffline(MapView.getMapView().getContext()));
    }

    @Override
    public void onStop() {
        if (started) {
            started = false;
            localSession.reset();
            AlertDialog stoppingConsent = trialConsent;
            trialConsent = null;
            if (stoppingConsent != null) hostUi.queueEvent(stoppingConsent::dismiss);
            if (publicationProbe != null) { publicationProbe.close(); publicationProbe = null; }
            AlertDialog stoppingMenu = labMenu;
            labMenu = null;
            if (stoppingMenu != null) hostUi.queueEvent(stoppingMenu::dismiss);
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
