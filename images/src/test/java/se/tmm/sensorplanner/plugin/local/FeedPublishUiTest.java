package se.tmm.sensorplanner.plugin.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.*;
import se.tmm.sensorplanner.plugin.ingress.*;
import se.tmm.sensorplanner.plugin.local.FeedPhotoPublication.WriteFailure;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** #243 B1: ordinary Publicera with real private stores, the synthetic h1/h2 containers, a fake host write and fake transports. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeedPublishUiTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String CONTEXT="mission-api-v1|https://lab.example.invalid:8443|00000000-0000-4000-8000-000000000242",
            STREAM="lab.example.invalid:8089:ssl";
    private final Queue<Runnable> work=new ArrayDeque<>(),ui=new ConcurrentLinkedQueue<>();
    private final List<String> calls=Collections.synchronizedList(new ArrayList<>());
    private final List<String> profiles=new ArrayList<>(Collections.singletonList(STREAM));
    private Path root;private LocalV2Store local;private LocalImages images;private InboxStore inbox;private ImageAssociationRegistry registry;
    private ImageStageStore stage;private FeedSyncState sync;private ImageAssociationRegistry.Record h1;private UUID device;
    private FeedPhotoPublication photos;private FeedPublishUi publish;private final Photo photo=new Photo();
    /** What the followed feed's CoT shows for the device; the host write publishes the saved M when {@code nativePublishes}. */
    private volatile TmmDraft remote;private boolean nativePublishes=true,hostCopy=true,single=true;private ShareReview.Metadata previous;
    private int published;

    @Before public void setup()throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        root=temp.newFolder().toPath();
        local=new LocalV2Store(root.resolve("local-drafts-v1"));local.initializeNew();images=new LocalImages(root.resolve("local-images-v1"));
        inbox=new InboxStore(root.resolve("metadata-inbox-v1"));UUID inboxId=inbox.initializeNew().inboxId;
        registry=new ImageAssociationRegistry(root.resolve("received-image-association-v1"));registry.initializeNew(inboxId);
        stage=new ImageStageStore(root.resolve("received-image-stage-v1"));stage.initializeNew();
        sync=new FeedSyncState(root.resolve("feed-sync-v1"));
        sync.create(new FeedSyncState.Snapshot(UUID.randomUUID(),inboxId,true,CONTEXT,"Synthetic follow",STREAM,Collections.emptyList()));
        // The device and photo 1 arrive from the feed: photo 1 is the image base, and the feed shows that M.
        h1=intake(container("/h1.tmmi"));device=h1.deviceUid;
        new FeedSyncApply(sync,inbox,stage,local,images).apply(sync.read(),Collections.singletonList(h1.receiptId),Collections.singletonList(h1),()->true);
        remote=feedCopy(saved());previous=metadata(saved());
        photos=new FeedPhotoPublication(local,images,root,new FeedPhotoPublication.Host(){
            public boolean available(){return true;}public boolean defaultProvider(){return true;}
            public List<String> profiles(){return profiles;}
            public FeedPhotoPublication.Transport connect(MissionEndpoint endpoint){return photo;}
            public String creatorUid(){return "ANDROID-synthetic";}
        });
        photos.fetchCompleted(Collections.singletonList(h1));
        publish=new FeedPublishUi(activity,local,images,root,work::add,photos,new FeedPublishUi.Host(){
            public void post(Runnable action){ui.add(action);}public boolean available(){return true;}
            public boolean singleStream(String stream){assertEquals(STREAM,stream);return single;}
            public String summary(ShareReview.Metadata m){return m.displayId+" / "+m.status;}
            public String coordinates(Position p){return "MGRS "+p.latitude;}
            public boolean hostCopyExists(UUID uid){assertEquals(device,uid);return hostCopy;}
            public SavedDraftCopyUi.PendingUpdate inspectUpdate(PublicationPreparationReview.Prepared prepared,String label){
                ShareReview.Metadata before=previous;
                return new SavedDraftCopyUi.PendingUpdate(){
                    public ShareReview.Metadata previous(){return before;}public String previousLabel(){return "Synthetic · r1";}
                    public String commit(){calls.add("host-update");written(prepared);return "SDK-resultat: METADATA_PERSIST_REQUESTED\nFeedpublicering och leverans är obekräftade.";}
                    public void close(){}
                };
            }
            public String create(PublicationPreparationReview.Prepared prepared,String label){calls.add("host-create");written(prepared);return "SDK-resultat: SUCCESS\nKontrollera kartan och feeds vid oklart resultat.";}
            public FeedFollowUi.Transport connect(MissionEndpoint endpoint){assertEquals(STREAM,endpoint.stream);return feed;}
            public void published(){published++;}
        });
        publish.waitMillis=200;publish.pollMillis=20;
    }
    @After public void close(){publish.close();photos.close();}

    @Test public void publishesMetadataThenReadsItBackThenUploadsThePhoto()throws Exception {
        attachSecondPhoto();
        String preview=open();
        assertTrue(preview,preview.contains("Feed: Synthetic follow")&&preview.contains("NY BILD")&&preview.contains("Välj endast Synthetic follow")
                &&preview.contains("Publish Content to Feeds")&&preview.contains("syntetiska")&&!preview.contains("Auto-publish"));
        assertNotNull("The photo is previewed",image(shadowOf(dialog()).getView()));
        assertEquals(0,boxes(shadowOf(dialog()).getView()).size());
        assertTrue(calls.isEmpty());
        confirm();
        assertEquals(Arrays.asList("host-update","fetch","upload","associate"),calls);
        assertEquals("Publicerad",title());assertTrue(message().contains("Metadata finns i Synthetic follow"));
        assertTrue("No delivery claim from the native step",message().contains("obekräftade"));assertEquals(1,published);
        // The container is bound to exactly the M read back (U4 rule 1).
        ReceivedImagePayload sent=ImageContainerCodec.read(new ByteArrayInputStream(photo.body));
        assertEquals(saved().updatedAt,sent.offer.metadata.updatedAt);assertEquals(device,sent.offer.metadata.uid);
        // The normal next fetch: R = L for metadata and photo, so the device leaves Ej publicerad without a conflict.
        FeedSyncApply apply=nextFetch(intake(sent));
        assertArrayEquals("The local photo is now exactly the published copy",sent.image.bytes(),images.read(saved().imageRef));
        assertFalse(apply.unpublished().contains(device));assertFalse(apply.conflicts().contains(device));
    }
    @Test public void metadataThatDoesNotAppearInTimeUploadsNothing()throws Exception {
        attachSecondPhoto();nativePublishes=false;
        open();confirm();
        assertEquals("host-update",calls.get(0));assertTrue("Repeated GETs",Collections.frequency(calls,"fetch")>=2);
        assertFalse(calls.contains("upload"));assertEquals("Ej publicerad",title());
        assertTrue(message().contains(FeedPublishUi.NOT_IN_FEED));assertEquals(0,published);
    }
    @Test public void statusOnlyUpdatePublishesMetadataWithoutUpload()throws Exception {
        TmmDraft d=saved();save(d.edit(d.displayId,d.type,DeviceStatus.values()[(d.status.ordinal()+1)%DeviceStatus.values().length],d.owner,d.deployedAt,d.comment,d.position,d.bearingDeg,d.imageRef,Instant.now()));
        String preview=open();assertTrue(preview,preview.contains("Bilden är oförändrad")&&!preview.contains("ÄNDRAD GEOMETRI"));
        confirm();
        assertEquals(Arrays.asList("host-update","fetch"),calls);assertEquals("Publicerad",title());
    }
    @Test public void newDeviceUsesTheHostCopyAndListsTheNativeSteps()throws Exception {
        hostCopy=false;
        String preview=open();assertTrue(preview,preview.contains("NY ENHET")&&preview.contains("Add Items")&&preview.contains("Done"));
        confirm();assertEquals(Arrays.asList("host-create","fetch"),calls);assertEquals("Publicerad",title());
    }
    @Test public void positionChangeNeedsTheSecondAcknowledgement()throws Exception {
        TmmDraft d=saved();save(d.edit(d.displayId,d.type,d.status,d.owner,d.deployedAt,d.comment,new Position(d.position.latitude+0.5,d.position.longitude),d.bearingDeg,d.imageRef,Instant.now()));
        String preview=open();
        assertTrue(preview,preview.contains("ÄNDRAD GEOMETRI")&&preview.contains("MGRS "+d.position.latitude+" → MGRS "+(d.position.latitude+0.5)));
        List<CheckBox> boxes=boxes(shadowOf(dialog()).getView());assertEquals(1,boxes.size());
        assertFalse(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        click(AlertDialog.BUTTON_POSITIVE);assertTrue(work.isEmpty());
        boxes.get(0).setChecked(true);assertTrue(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        confirm();assertEquals(Arrays.asList("host-update","fetch"),calls);assertEquals("Publicerad",title());
    }
    @Test public void anyDestinationChangeAfterThePreviewRefusesBeforeTheHostWrite()throws Exception {
        attachSecondPhoto();
        open();FeedSyncState.Snapshot s=sync.read();
        sync.replace(s,new FeedSyncState.Snapshot(s.syncId,s.inboxId,true,"mission-api-v1|https://lab.example.invalid:8443|00000000-0000-4000-8000-000000000243",s.feedName,s.streamConnect,s.records));
        confirm();
        assertTrue(calls.isEmpty());assertEquals("Inte publicerat",title());assertTrue(message().contains("Granska på nytt"));
        // A second enabled profile after the preview, then already at the preview.
        click(AlertDialog.BUTTON_POSITIVE);sync.replace(sync.read(),s);
        open();single=false;confirm();
        assertTrue(calls.isEmpty());assertTrue(message().contains("enda aktiverade serverprofilen"));
        click(AlertDialog.BUTTON_POSITIVE);publish.show(device);runWorker();
        assertEquals("Inte publicerat",title());assertTrue(calls.isEmpty());
    }
    @Test public void forbiddenUploadShowsThePermissionMessage()throws Exception {
        attachSecondPhoto();photo.uploadFailure=WriteFailure.Kind.REFUSED;
        open();confirm();
        assertEquals(Arrays.asList("host-update","fetch","upload"),calls);
        assertTrue(message().contains("403")&&message().contains("skrivbehörighet"));assertNotEquals("Publicerad",title());
    }
    @Test public void unknownUploadOutcomeIsShownAndNeverResent()throws Exception {
        attachSecondPhoto();photo.uploadFailure=WriteFailure.Kind.UNKNOWN;
        open();confirm();
        assertTrue(message().contains("Okänt om bilden kom fram"));assertNotEquals("Publicerad",title());
        click(AlertDialog.BUTTON_POSITIVE);photos.fetchCompleted(Collections.singletonList(h1));drainUi();
        assertEquals(Arrays.asList("host-update","fetch","upload"),calls);assertTrue(work.isEmpty());
    }
    @Test public void replacedPhotoIsRemovedOnlyByAnExplicitPublicera()throws Exception {
        attachSecondPhoto();open();confirm();assertEquals("Publicerad",title());click(AlertDialog.BUTTON_POSITIVE);
        ImageAssociationRegistry.Record fresh=intake(ImageContainerCodec.read(new ByteArrayInputStream(photo.body)));
        // Automatic fetching shows both photos and applies; it never removes anything.
        nextFetch(fresh);photos.fetchCompleted(Arrays.asList(h1,fresh));drainUi();
        assertFalse(calls.contains("remove"));assertTrue(photos.awaitingRemoval(device));
        calls.clear();open();confirm();
        assertEquals("W4 first, then the unchanged photo is not uploaded again",Arrays.asList("remove","host-update","fetch"),calls);
        assertTrue(message().contains("ersatta bilden togs bort"));assertFalse(photos.awaitingRemoval(device));
    }
    @Test public void notFollowingRefusesWithoutAPreview()throws Exception {
        FeedSyncState.Snapshot s=sync.read();
        sync.replace(s,new FeedSyncState.Snapshot(s.syncId,s.inboxId,false,s.contextId,s.feedName,s.streamConnect,s.records));
        publish.show(device);runWorker();
        assertEquals("Inte publicerat",title());assertTrue(message().contains("Följ feed"));assertTrue(calls.isEmpty());
        click(AlertDialog.BUTTON_POSITIVE);sync.replace(sync.read(),s);profiles.clear();
        publish.show(device);runWorker();assertTrue(message().contains("inte ansluten"));assertTrue(calls.isEmpty());
    }

    private final FeedFollowUi.Transport feed=new FeedFollowUi.Transport(){
        public String description(){return "fake";}
        public List<MissionSnapshotInput.Feed> list(MissionReadAttempt a){throw new AssertionError("Readback needs no list");}
        public MissionSnapshotInput.Snapshot fetch(MissionSnapshotInput.Feed f,MissionReadAttempt a){
            calls.add("fetch");assertEquals(CONTEXT,f.context());assertTrue(a.active(System.nanoTime()));
            TmmDraft r=remote;
            return new MissionSnapshotInput.Snapshot(f,Collections.singleton(device.toString()),r==null?Collections.emptyList():Collections.singletonList(r));
        }
        public List<MissionImageInput.Resource> images(MissionSnapshotInput.Feed f,MissionReadAttempt a){throw new AssertionError("Readback reads no images");}
        public ReceivedImagePayload image(MissionSnapshotInput.Feed f,MissionImageInput.Resource r,MissionReadAttempt a){throw new AssertionError();}
    };
    private void written(PublicationPreparationReview.Prepared prepared){
        try{TmmDraft d=saved();assertEquals(d.updatedAt,prepared.metadata.updatedAt);if(nativePublishes)remote=feedCopy(d);}catch(IOException e){throw new AssertionError(e);}
    }
    private final class Photo implements FeedPhotoPublication.Transport {
        byte[] body;WriteFailure.Kind uploadFailure;
        public String upload(String name,String creatorUid,byte[] container,MissionReadAttempt attempt)throws WriteFailure {
            calls.add("upload");body=container.clone();if(uploadFailure!=null)throw new WriteFailure(uploadFailure);
            try{StringBuilder out=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(container))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
            catch(Exception e){throw new AssertionError(e);}
        }
        public void associate(String guid,String hash,String creatorUid,MissionReadAttempt attempt){calls.add("associate");}
        public void remove(String guid,String hash,String creatorUid,MissionReadAttempt attempt){calls.add("remove");}
    }
    /** The next automatic fetch: the feed's current M plus the given photos, applied as FeedFollowUi does. */
    private FeedSyncApply nextFetch(ImageAssociationRegistry.Record... fresh)throws Exception {
        Map<UUID,TmmDraft> observed=new HashMap<>();for(TmmDraft d:local.load().activeDrafts())observed.put(d.uid,d);
        List<UUID> receipts=new ArrayList<>();
        for(InboxStore.Receipt r:inbox.receiveBatch(CONTEXT,Collections.singletonList(remote),observed,Instant.now()))receipts.add(r.id);
        List<ImageAssociationRegistry.Record> records=new ArrayList<>(Collections.singletonList(h1));records.addAll(Arrays.asList(fresh));
        FeedSyncApply apply=new FeedSyncApply(sync,inbox,stage,local,images);apply.apply(sync.read(),receipts,records,()->true);
        return apply;
    }
    private void attachSecondPhoto()throws Exception {
        try(LocalImages.Pending pending=images.prepareReceived(container("/h2.tmmi").image)){
            new DraftAttachments(local,images).attach(local.load().select(device),pending,Instant.now());
        }
    }
    private String open()throws Exception {
        publish.show(device);runWorker();
        assertEquals("Publicera till Synthetic follow",title());return text(shadowOf(dialog()).getView());
    }
    private void confirm()throws Exception {click(AlertDialog.BUTTON_POSITIVE);runWorker();}
    /** Runs the queued worker task on its own thread while this thread plays the UI thread. */
    private void runWorker()throws Exception {
        Thread t=new Thread(work.remove(),"publish-test-worker");t.start();
        long until=System.nanoTime()+10_000_000_000L;
        while(t.isAlive()&&System.nanoTime()<until){drainUi();Thread.sleep(1);}
        t.join(100);assertFalse("Worker must finish",t.isAlive());drainUi();
    }
    private void drainUi(){Runnable r;while((r=ui.poll())!=null)r.run();shadowOf(Looper.getMainLooper()).idle();}
    private void click(int which)throws Exception {dialog().getButton(which).performClick();shadowOf(Looper.getMainLooper()).idle();}
    private AlertDialog dialog()throws Exception {Field f=FeedPublishUi.class.getDeclaredField("dialog");f.setAccessible(true);return (AlertDialog)f.get(publish);}
    private String title()throws Exception {return shadowOf(dialog()).getTitle().toString();}
    private String message()throws Exception {CharSequence m=shadowOf(dialog()).getMessage();return m==null?"":m.toString();}
    private static String text(View view){
        if(view instanceof android.widget.TextView)return ((android.widget.TextView)view).getText().toString();
        StringBuilder out=new StringBuilder();
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(text(((ViewGroup)view).getChildAt(i)));
        return out.toString();
    }
    private static List<CheckBox> boxes(View view){
        List<CheckBox> out=new ArrayList<>();
        if(view instanceof CheckBox)out.add((CheckBox)view);
        else if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.addAll(boxes(((ViewGroup)view).getChildAt(i)));
        return out;
    }
    private static android.widget.ImageView image(View view){
        if(view instanceof android.widget.ImageView)return (android.widget.ImageView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){android.widget.ImageView found=image(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private TmmDraft saved()throws IOException {return local.load().select(device).draft;}
    private void save(TmmDraft value)throws IOException {LocalV2.Snapshot s=local.load();local.save(s.storeId,s.state(value.uid),value);}
    private static TmmDraft feedCopy(TmmDraft d){return d.edit(d.displayId,d.type,d.status,d.owner,d.deployedAt,d.comment,d.position,d.bearingDeg,null,d.updatedAt);}
    private static ShareReview.Metadata metadata(TmmDraft d){return new PublicationPreparationReview(d).metadata;}
    private ImageAssociationRegistry.Record intake(ReceivedImagePayload payload)throws Exception {
        return new ReceivedImageIntake(inbox,registry,stage,local).receive(CONTEXT,payload,Instant.EPOCH,()->true);
    }
    private ReceivedImagePayload container(String name)throws Exception {try(InputStream in=getClass().getResourceAsStream(name)){return ImageContainerCodec.read(in);}}
}
