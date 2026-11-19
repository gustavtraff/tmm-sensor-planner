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
    private final String imageCopyNotice;
    private volatile boolean started;
    private final LocalTrialSession localSession = new LocalTrialSession();
    private volatile AlertDialog trialConsent;
    private LocalPlanner planner;
    private CotRoundTripProbe cotProbe;
    private ShareLabReceiver shareReceiver;
    private PublicationImportProbe publicationProbe;
    private PublicationEnvelopeProbe envelopeProbe;
    private AlertDialog labMenu;
    private IngressObservationProbe ingressProbe;
    private MissionReadProbe missionReadProbe;

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
    }

    private void openLabMenu() {
        if (!started) return;
        if (!labAllowed()) {
            hostUi.showToast("Labbverktygen är avstängda vid uppkopplat arbete och kräver annars offline-läge");
            return;
        }
        if (labMenu != null) labMenu.dismiss();
        labMenu = new AlertDialog.Builder(MapView.getMapView().getContext())
                .setTitle("Välj isolerat labbprov")
                .setItems(new String[]{"Tidigare CoT-labb", "Syntetiskt SDK-importprov", "Produktionsformat · syntetiskt prov", "Källkontext · syntetisk observation", "Mission API · läs labböversikt"}, (d, which) -> {
                    if (!started || !labAllowed()) return;
                    try {
                        if (which == 0) openCotLab();
                        else if (which == 1) openPublicationLab();
                        else if (which == 2) openEnvelopeLab();
                        else if (which == 3) openIngressProbe();
                        else openMissionReadProbe();
                    } catch (RuntimeException unavailable) {
                        hostUi.showToast("Labbprovet kunde inte startas");
                    }
                }).setNegativeButton("Avbryt", null).create();
        labMenu.show();
    }

    private void openIngressProbe() {
        if (Build.VERSION.SDK_INT < 26 || !started || !labAllowed()) return;
        if (ingressProbe == null) ingressProbe = new IngressObservationProbe(MapView.getMapView(), hostUi, this::labAllowed);
        ingressProbe.show();
    }

    private void openMissionReadProbe(){
        if(Build.VERSION.SDK_INT<26||!started||!labAllowed())return;
        if(missionReadProbe==null)missionReadProbe=new MissionReadProbe(MapView.getMapView(),hostUi,this::labAllowed);
        missionReadProbe.show();
    }

    private void openCotLab() {
        if (envelopeProbe != null) { envelopeProbe.close(); envelopeProbe = null; }
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
        if (envelopeProbe != null) { envelopeProbe.close(); envelopeProbe = null; }
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

    private void openEnvelopeLab() {
        if (Build.VERSION.SDK_INT < 26) { hostUi.showToast("Provet kräver Android 8 eller senare"); return; }
        if (cotProbe != null) { cotProbe.close(); cotProbe = null; }
        if (shareReceiver != null) { shareReceiver.close(); shareReceiver = null; }
        if (publicationProbe != null) { publicationProbe.close(); publicationProbe = null; }
        if (envelopeProbe == null) envelopeProbe = new PublicationEnvelopeProbe(
                Objects.requireNonNull(MapView.getMapView()), hostUi, this::labAllowed);
        envelopeProbe.show();
    }

    @Override
    public void onStart() {
        if (!started) {
            hostUi.addToolbarItem(entry);
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
        // ADR 0013: an explicit Följ feed choice resumes connected use after a restart; the lab stays off as under trial consent.
        if (!localAllowed() && localSession.resumeFollowedFeed(new java.io.File(map.getContext().getNoBackupFilesDir(),
                "se.tmm.sensorplanner/feed-sync-v1").toPath())) closeLabTools();
        if (!localAllowed()) {
            if (showList && trialConsent == null) {
                trialConsent = new AlertDialog.Builder(map.getContext())
                        .setTitle("Arbeta uppkopplad")
                        .setMessage("Dina enheter och ändringar sparas lokalt. "
                                + "Välj Följ feed för att ta emot gruppens uppdateringar. Dina ändringar delas först när du väljer Publicera och bekräftar. "
                                + "Den här versionen får bara användas med syntetiska data i en separat testmiljö. "
                                + "Detta val gäller tills pluginet avlastas (Unload) eller ATAK startas om; en sparad följning återupptas efter omstart.")
                        .setNegativeButton("Avbryt", null)
                        .setPositiveButton("Fortsätt", (dialog, which) -> {
                            if (!started) return;
                            localSession.startConnectedTrial();
                            closeLabTools();
                            try { openPlanner(true); }
                            catch (Exception unavailable) { hostUi.showToast("TMM kunde inte öppnas"); }
                        }).create();
                trialConsent.setOnDismissListener(dialog -> { if (trialConsent == dialog) trialConsent = null; });
                trialConsent.show();
            }
            return;
        }
        if (planner == null) planner = new LocalPlanner(map, hostUi, this::localAllowed, this::labAllowed, imageCopyNotice, this::openLabMenu);
        if (showList) planner.show();
        else planner.restore();
    }

    /** Connected local use (trial consent or a followed feed) ends every lab tool for the session. */
    private void closeLabTools() {
        if (planner != null) planner.disableSharing();
        if (labMenu != null) { labMenu.dismiss(); labMenu = null; }
        if (publicationProbe != null) { publicationProbe.close(); publicationProbe = null; }
        if (envelopeProbe != null) { envelopeProbe.close(); envelopeProbe = null; }
        if (cotProbe != null) { cotProbe.close(); cotProbe = null; }
        if (shareReceiver != null) { shareReceiver.close(); shareReceiver = null; }
        if (ingressProbe != null) { ingressProbe.close(); ingressProbe = null; }
        if (missionReadProbe != null) { missionReadProbe.close(); missionReadProbe = null; }
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
            IngressObservationProbe stoppingIngress = ingressProbe;
            ingressProbe = null;
            if (stoppingIngress != null) { stoppingIngress.stop(); hostUi.queueEvent(stoppingIngress::close); }
            MissionReadProbe stoppingRead=missionReadProbe;missionReadProbe=null;
            if(stoppingRead!=null){stoppingRead.stop();hostUi.queueEvent(stoppingRead::close);}
            localSession.reset();
            AlertDialog stoppingConsent = trialConsent;
            trialConsent = null;
            if (stoppingConsent != null) hostUi.queueEvent(stoppingConsent::dismiss);
            if (publicationProbe != null) { publicationProbe.close(); publicationProbe = null; }
            if (envelopeProbe != null) { envelopeProbe.close(); envelopeProbe = null; }
            AlertDialog stoppingMenu = labMenu;
            labMenu = null;
            if (stoppingMenu != null) hostUi.queueEvent(stoppingMenu::dismiss);
            hostUi.removeToolbarItem(entry);
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
