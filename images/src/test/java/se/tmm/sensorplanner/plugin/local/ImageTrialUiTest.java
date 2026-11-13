package se.tmm.sensorplanner.plugin.local;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.*;
import se.tmm.sensorplanner.plugin.ingress.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageTrialUiTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String STREAM="lab.example.invalid:8089:ssl";
    private final Deque<Runnable> worker=new ArrayDeque<>();
    private final Queue<Runnable> heldPosts=new ConcurrentLinkedQueue<>();
    private volatile boolean holdPosts;
    private Activity activity;private ImageTrialUi ui;private LocalV2Store local;private TmmDraft draft;
    private Path root,privateRoot,downloads,owned;private byte[] source,container,localBefore;
    private volatile boolean connected=true,available=true,secondEnabled;private volatile int listCalls,imageCalls;private int changed;
    private final MissionSnapshotInput.Feed feed=new MissionSnapshotInput.Feed("Synthetic images","00000000-0000-4000-8000-000000000208","https://lab.example.invalid:8443");
    private ReceivedImagePayload incoming;
    @Before public void setup()throws Exception {
        root=temp.newFolder().toPath();privateRoot=root.resolve("private");downloads=root.resolve("Downloads/TMM-Sensor-Planner");owned=privateRoot.resolve("local-images-v1");Files.createDirectories(owned);
        try(InputStream in=getClass().getResourceAsStream("/h1.tmmi")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);container=out.toByteArray();}
        incoming=ImageContainerCodec.read(new ByteArrayInputStream(container));source=incoming.image.bytes();String handle="00000000-0000-4000-8000-000000000208.png";Files.write(owned.resolve(handle),source);
        ShareReview.Metadata m=incoming.offer.metadata;draft=new TmmDraft(UUID.randomUUID(),"Synthetic private unit",m.type,m.status,m.owner,m.createdAt,m.updatedAt,m.deployedAt,m.comment,m.position,m.bearingDeg,handle,1);
        local=new LocalV2Store(privateRoot.resolve("local-drafts-v1"));LocalV2.Snapshot initial=local.initializeNew();local.save(initial.storeId,initial.state(draft.uid),draft);localBefore=Files.readAllBytes(privateRoot.resolve("local-drafts-v1/drafts.bin"));
        activity=Robolectric.buildActivity(Activity.class).setup().get();Handler handler=new Handler(Looper.getMainLooper());
        ui=new ImageTrialUi(activity,local,new LocalImages(owned),privateRoot,downloads,worker::add,new ImageTrialUi.Host(){
            public void post(Runnable r){if(holdPosts)heldPosts.add(r);else handler.post(r);}public boolean available(){return available;}
            public List<String> profiles(){return connected?Collections.singletonList(STREAM):Collections.emptyList();}
            public List<ConfiguredPublicationTrial.Stream> streams(){List<ConfiguredPublicationTrial.Stream> streams=new ArrayList<>();streams.add(new ConfiguredPublicationTrial.Stream(true,connected,STREAM));if(secondEnabled)streams.add(new ConfiguredPublicationTrial.Stream(true,false,"other.example.invalid:8089:ssl"));return streams;}
            public boolean defaultProvider(){return true;}
            public ImageTrialUi.Transport connect(MissionEndpoint endpoint){assertEquals(feed.service,endpoint.base);return new ImageTrialUi.Transport(){
                public String description(){return feed.service;}
                public List<MissionSnapshotInput.Feed> list(MissionReadAttempt a){assertTrue(a.active(System.nanoTime()));listCalls++;return Collections.singletonList(feed);}
                public List<MissionImageInput.Resource> images(MissionSnapshotInput.Feed f,MissionReadAttempt a)throws Exception{
                    StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(container))hash.append(String.format("%02x",b&255));
                    JSONObject resource=new JSONObject().put("data",new JSONObject().put("name","synthetic.tmmi").put("hash",hash.toString()).put("mimeType","application/octet-stream").put("size",container.length));
                    JSONObject mission=new JSONObject().put("name",feed.name).put("guid",feed.guid).put("contents",new JSONArray().put(resource));
                    return MissionImageInput.read(feed,new ByteArrayInputStream(new JSONObject().put("type","Mission").put("data",new JSONArray().put(mission)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),()->a.active(System.nanoTime()));
                }
                public ReceivedImagePayload image(MissionSnapshotInput.Feed f,MissionImageInput.Resource r,MissionReadAttempt a)throws Exception{imageCalls++;return ImageContainerCodec.read(new ByteArrayInputStream(container));}
            };}
            public String summary(ShareReview.Metadata metadata){return metadata.displayId+"\nUUID: "+metadata.uid;}
            public String coordinates(Position position){return "Synthetic MGRS";}public void changed(UUID uid){changed++;}
        });
    }
    @After public void close(){if(ui!=null)ui.close();idle();worker.clear();activity.finish();}
    @Test public void cancelPreviewCreatesNoExportAndPreservesPrivateData()throws Exception {
        exportPreview();click(AlertDialog.BUTTON_NEGATIVE);assertFalse(Files.exists(downloads));preserved();assertEquals(1,listCalls);assertEquals(0,imageCalls);
    }
    @Test public void confirmedExportRechecksFeedAndWritesActualFrozenContainerOnce()throws Exception {
        exportPreview();AlertDialog preview=dialog();Object p=field("preview");Field reviewField=p.getClass().getDeclaredField("review");reviewField.setAccessible(true);byte[] frozen=((ImagePublicationReview)reviewField.get(p)).preview();click(AlertDialog.BUTTON_POSITIVE);await("Export förberedd – inte publicerad",true);
        assertEquals(2,listCalls);assertEquals(1,exports());Path file=exported();try(InputStream in=Files.newInputStream(file)){ReceivedImagePayload actual=ImageContainerCodec.read(in);assertEquals(draft.uid,actual.offer.metadata.uid);assertArrayEquals(frozen,actual.image.bytes());}
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();drain();assertEquals(1,exports());preserved();assertFalse(Files.exists(privateRoot.resolve("received-image-stage-v1")));
    }
    @Test public void localChangeOrSourceLossBeforeConfirmationCannotExport()throws Exception {
        exportPreview();LocalV2.Snapshot s=local.load();TmmDraft changed=draft.edit(draft.displayId,draft.type,draft.status,draft.owner,draft.deployedAt,"local changed",draft.position,draft.bearingDeg,draft.imageRef,draft.updatedAt.plusSeconds(1));local.save(s.storeId,s.state(draft.uid),changed);
        click(AlertDialog.BUTTON_POSITIVE);await("Bildåtgärden kunde inte bekräftas",true);assertFalse(Files.exists(downloads));assertEquals(changed.comment,local.load().state(draft.uid).draft.comment);
    }
    @Test public void oldPreviewAfterSourceLossDoesNotReviveOrWrite()throws Exception {
        exportPreview();AlertDialog old=dialog();connected=false;ui.sourceLost();idle();await("Källåtkomsten avbröts",true);
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick();drain();assertFalse(Files.exists(downloads));preserved();
    }
    @Test public void receiveUnknownDeviceIsSeparateRepeatReusesAndViewerOpensOffline()throws Exception {
        receive();assertEquals(1,imageCalls);assertEquals(1,local.load().activeDrafts().size());assertNull(local.load().state(incoming.offer.metadata.uid).draft);preserved();
        ImageAssociationRegistry registry=new ImageAssociationRegistry(privateRoot.resolve("received-image-association-v1"));ImageStageStore stage=new ImageStageStore(privateRoot.resolve("received-image-stage-v1"));assertEquals(1,registry.load().records.size());assertEquals(1,stage.inspect().ready.size());
        click(AlertDialog.BUTTON_POSITIVE);awaitViewer();Dialog first=(Dialog)field("viewer");assertTrue(first.isShowing());assertNull(field("dialog"));assertTrue(allText(first.getWindow().getDecorView()).contains(incoming.offer.metadata.uid.toString()));assertTrue(allText(first.getWindow().getDecorView()).contains("Historisk/annan metadata"));
        ui.cancel();connected=false;ui.received(incoming.offer.metadata.uid);await("Mottagna bilder – separata varianter",true);pick(0);awaitViewer();assertTrue(((Dialog)field("viewer")).isShowing());
        ui.cancel();connected=true;receive();assertEquals(2,imageCalls);assertEquals(1,registry.load().records.size());assertEquals(1,stage.inspect().ready.size());preserved();
    }
    @Test public void metadataFromImageStillNeedsExplicitReviewAndCancelWritesNoDraft()throws Exception {
        receive();click(AlertDialog.BUTTON_NEUTRAL);await("Tillägg · inget sparas",true);assertEquals(1,local.load().activeDrafts().size());click(AlertDialog.BUTTON_NEGATIVE);preserved();assertEquals(0,changed);
    }
    @Test public void imageMetadataCanBeAddedOnlyAfterSeparateFreshLocalConfirmation()throws Exception {
        receive();assertNull(local.load().state(incoming.offer.metadata.uid).draft);click(AlertDialog.BUTTON_NEUTRAL);await("Tillägg · inget sparas",true);
        click(AlertDialog.BUTTON_POSITIVE);await("Exakt importförslag · inget sparas",true);preserved();assertNull(local.load().state(incoming.offer.metadata.uid).draft);
        click(AlertDialog.BUTTON_POSITIVE);await("Enheten tillagd lokalt",true);TmmDraft added=local.load().state(incoming.offer.metadata.uid).draft;
        assertNotNull(added);assertEquals(incoming.offer.metadata.displayId,added.displayId);assertNull(added.imageRef);assertEquals(2,local.load().activeDrafts().size());assertEquals(1,changed);assertArrayEquals(source,Files.readAllBytes(owned.resolve(draft.imageRef)));
    }
    @Test public void secondEnabledStreamRefusesBeforeAnyFeedReadOrExport()throws Exception {
        secondEnabled=true;ui.show(draft.uid);pick(0);await("Välj ansluten ATAK-server",true);pick(0);await("Bekräfta server för hämtning",true);click(AlertDialog.BUTTON_POSITIVE);await("Publiceringsprov · syntetiska data",true);
        List<CheckBox> checks=new ArrayList<>();collect(shadowOf(dialog()).getView(),checks);for(CheckBox c:checks)c.setChecked(true);click(AlertDialog.BUTTON_POSITIVE);await("Bildprovet kunde inte starta",true);
        assertEquals(0,listCalls);assertEquals(0,imageCalls);assertFalse(Files.exists(downloads));preserved();
    }
    @Test public void closeBeforeQueuedIntakePreventsLateWritesAndViewer()throws Exception {
        start(false);pick(0);await("Välj bildpaket i Synthetic images",true);pick(0);click(AlertDialog.BUTTON_POSITIVE);await("Starta mottagen bildhistorik?",true);click(AlertDialog.BUTTON_POSITIVE);worker.remove().run();idle();
        await("Sparar mottagen bild för granskning",false);assertFalse(worker.isEmpty());ui.close();drain();idle();
        assertTrue(new ImageAssociationRegistry(privateRoot.resolve("received-image-association-v1")).load().records.isEmpty());assertTrue(new ImageStageStore(privateRoot.resolve("received-image-stage-v1")).inspect().ready.isEmpty());assertNull(field("viewer"));preserved();
    }
    @Test public void failedOfflineReadWhileUnavailableReleasesBusyAndAllowsFreshOpen()throws Exception {
        ui.received(draft.uid);assertFalse(worker.isEmpty());available=false;worker.remove().run();idle();
        available=true;assertFalse("Failed unavailable work must not strand the controller",ui.isBusy());
        ui.show(draft.uid);assertEquals("Bilddelning · syntetiskt prov",shadowOf(dialog()).getTitle().toString());preserved();
    }
    @Test public void cancelledAttemptCannotDeliverQueuedImageResult()throws Exception {
        queuedImageResult();MissionReadAttempt attempt=(MissionReadAttempt)field("pending");attempt.cancel();
        deliverHeldPosts();assertTrue("Canceled result must not queue private intake",worker.isEmpty());
        assertFalse((Boolean)field("busy"));assertNull(field("pending"));
        assertEquals("Bildåtgärden kunde inte bekräftas",shadowOf(dialog()).getTitle().toString());assertEmptyReceivedHistory();preserved();
    }
    @Test public void unavailableHostAtQueuedImageDeliveryReleasesAttempt()throws Exception {
        queuedImageResult();available=false;deliverHeldPosts();available=true;
        assertFalse("Unavailable delivery must release pending work",ui.isBusy());
        assertNull(field("pending"));assertTrue(worker.isEmpty());assertEmptyReceivedHistory();preserved();
    }
    @Test public void deadlineAfterNetworkCompletionRejectsQueuedImageResult()throws Exception {
        queuedImageResult();MissionReadAttempt attempt=(MissionReadAttempt)field("pending");
        Field deadline=MissionReadAttempt.class.getDeclaredField("deadline");deadline.setAccessible(true);
        ((Runnable)deadline.get(attempt)).run();deliverHeldPosts();
        assertTrue(worker.isEmpty());assertNull(field("pending"));assertFalse((Boolean)field("busy"));
        assertEquals("Bildåtgärden kunde inte bekräftas",shadowOf(dialog()).getTitle().toString());assertEmptyReceivedHistory();preserved();
    }
    @Test public void oldNetworkResultAndFailureCannotCancelNewExplicitFlow()throws Exception {
        queuedImageResult();MissionReadAttempt attempt=(MissionReadAttempt)field("pending");
        Field deadline=MissionReadAttempt.class.getDeclaredField("deadline");deadline.setAccessible(true);
        ((Runnable)deadline.get(attempt)).run();ui.cancel();ui.show(draft.uid);AlertDialog fresh=dialog();
        deliverHeldPosts();assertSame(fresh,dialog());assertTrue(fresh.isShowing());assertTrue(worker.isEmpty());
        assertNull(field("viewer"));assertEmptyReceivedHistory();preserved();
    }
    @Test public void dismissedReceivedListCannotOpenViewerInFreshFlow()throws Exception {
        AlertDialog old=dismissedReceivedList();ui.show(draft.uid);AlertDialog fresh=dialog();
        old.getListView().performItemClick(null,0,0);idle();
        assertTrue("Dismissed selection must not queue another image read",worker.isEmpty());
        assertSame(fresh,dialog());assertTrue(fresh.isShowing());assertNull(field("viewer"));preserved();
    }
    @Test public void dismissedReceivedListCloseCannotCancelFreshFlow()throws Exception {
        AlertDialog old=dismissedReceivedList();ui.show(draft.uid);AlertDialog fresh=dialog();
        old.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();
        assertSame(fresh,dialog());assertTrue("Dismissed close must not cancel fresh UI",fresh.isShowing());
        assertNull(field("viewer"));preserved();
    }
    @Test public void dismissedReceiveResultCannotOpenViewerInFreshFlow()throws Exception {
        receive();AlertDialog old=dialog();ui.cancel();ui.show(draft.uid);AlertDialog fresh=dialog();
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();
        assertTrue("Dismissed result must not queue another image read",worker.isEmpty());
        assertSame(fresh,dialog());assertTrue(fresh.isShowing());assertNull(field("viewer"));preserved();
    }
    @Test public void dismissedReceiveResultCloseCannotCancelFreshFlow()throws Exception {
        receive();AlertDialog old=dialog();ui.cancel();ui.show(draft.uid);AlertDialog fresh=dialog();
        old.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();
        assertSame(fresh,dialog());assertTrue("Dismissed result close must not cancel fresh UI",fresh.isShowing());preserved();
    }
    private AlertDialog dismissedReceivedList()throws Exception {
        receive();ui.cancel();ui.received(incoming.offer.metadata.uid);
        await("Mottagna bilder – separata varianter",true);AlertDialog old=dialog();ui.cancel();return old;
    }
    private void queuedImageResult()throws Exception {
        // Isolate terminal delivery from the independent periodic source-loss observer.
        ScheduledThreadPoolExecutor timer=(ScheduledThreadPoolExecutor)field("timer");
        for(Runnable task:timer.getQueue())if(((RunnableScheduledFuture<?>)task).isPeriodic())((Future<?>)task).cancel(false);
        InboxStore inbox=new InboxStore(privateRoot.resolve("metadata-inbox-v1"));
        new ImageAssociationRegistry(privateRoot.resolve("received-image-association-v1")).initializeNew(inbox.initializeNew().inboxId);
        new ImageStageStore(privateRoot.resolve("received-image-stage-v1")).initializeNew();
        start(false);pick(0);await("Välj bildpaket i Synthetic images",true);pick(0);click(AlertDialog.BUTTON_POSITIVE);
        ThreadPoolExecutor network=(ThreadPoolExecutor)field("network");
        for(int n=0;n<400&&network.getCompletedTaskCount()<2;n++)Thread.sleep(5);
        assertEquals(2,network.getCompletedTaskCount());
        holdPosts=true;worker.remove().run();
        while(field("pending")==null){Runnable callback=heldPosts.poll();assertNotNull(callback);callback.run();}
        for(int n=0;n<400&&network.getCompletedTaskCount()<3;n++)Thread.sleep(5);
        assertEquals("Network result must be queued before the control",3,network.getCompletedTaskCount());
        assertNotNull(field("pending"));assertEquals(1,imageCalls);assertFalse(heldPosts.isEmpty());
    }
    private void deliverHeldPosts(){holdPosts=false;for(Runnable r;(r=heldPosts.poll())!=null;)r.run();idle();}
    private void assertEmptyReceivedHistory()throws Exception {
        assertTrue(new InboxStore(privateRoot.resolve("metadata-inbox-v1")).load().receipts.isEmpty());
        assertTrue(new ImageAssociationRegistry(privateRoot.resolve("received-image-association-v1")).load().records.isEmpty());
        assertTrue(new ImageStageStore(privateRoot.resolve("received-image-stage-v1")).inspect().ready.isEmpty());
    }
    private void exportPreview()throws Exception{start(true);pick(0);await("Granska bildexport – inte publicerad",true);}
    private void receive()throws Exception {
        start(false);pick(0);await("Välj bildpaket i Synthetic images",true);pick(0);click(AlertDialog.BUTTON_POSITIVE);
        if(!Files.exists(privateRoot.resolve("received-image-association-v1/associations.bin"))){await("Starta mottagen bildhistorik?",true);click(AlertDialog.BUTTON_POSITIVE);}
        await("Bild verifierad för granskning",true);
    }
    private void start(boolean export)throws Exception {
        ui.show(export?draft.uid:null);pick(0);await("Välj ansluten ATAK-server",true);pick(0);await("Bekräfta server för hämtning",true);click(AlertDialog.BUTTON_POSITIVE);await("Publiceringsprov · syntetiska data",true);
        List<CheckBox> checks=new ArrayList<>();collect(shadowOf(dialog()).getView(),checks);assertEquals(4,checks.size());for(CheckBox c:checks)c.setChecked(true);click(AlertDialog.BUTTON_POSITIVE);await("Välj testfeed",true);
    }
    private void preserved()throws Exception{assertArrayEquals(localBefore,Files.readAllBytes(privateRoot.resolve("local-drafts-v1/drafts.bin")));assertArrayEquals(source,Files.readAllBytes(owned.resolve(draft.imageRef)));}
    private long exports()throws Exception{try(java.util.stream.Stream<Path> files=Files.list(downloads)){return files.filter(p->p.toString().endsWith(".tmmi")).count();}}
    private Path exported()throws Exception{try(java.util.stream.Stream<Path> files=Files.list(downloads)){return files.filter(p->p.toString().endsWith(".tmmi")).findFirst().get();}}
    private void drain(){while(!worker.isEmpty()){worker.remove().run();idle();}idle();}
    private void await(String expected,boolean runWorker)throws Exception{
        for(int n=0;n<400;n++){if(runWorker)drain();else idle();AlertDialog d=dialog();if(d!=null&&d.isShowing()&&expected.equals(shadowOf(d).getTitle().toString()))return;Thread.sleep(5);}
        fail("Expected "+expected+" but got "+(dialog()==null?"none":shadowOf(dialog()).getTitle()));
    }
    private void awaitViewer()throws Exception{for(int n=0;n<400;n++){drain();if(field("viewer")!=null)return;Thread.sleep(5);}fail("Viewer unavailable");}
    private Object field(String name)throws Exception{Field f=ImageTrialUi.class.getDeclaredField(name);f.setAccessible(true);return f.get(ui);}
    private AlertDialog dialog(){return ShadowAlertDialog.getLatestAlertDialog();}
    private void click(int button){dialog().getButton(button).performClick();idle();}
    private void pick(int n){dialog().getListView().performItemClick(null,n,n);idle();}
    private void idle(){shadowOf(Looper.getMainLooper()).idle();}
    private void collect(View view,List<CheckBox> out){if(view instanceof CheckBox)out.add((CheckBox)view);if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)collect(((ViewGroup)view).getChildAt(n),out);}
    private String allText(View v){String s=v instanceof TextView?((TextView)v).getText().toString():"";if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)s+="\n"+allText(((ViewGroup)v).getChildAt(n));return s;}
}
