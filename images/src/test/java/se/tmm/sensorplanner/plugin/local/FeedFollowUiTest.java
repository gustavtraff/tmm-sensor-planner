package se.tmm.sensorplanner.plugin.local;

import android.app.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.plugin.images.*;
import se.tmm.sensorplanner.plugin.ingress.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class FeedFollowUiTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String STREAM="lab.example.invalid:8089:ssl";
    private final MissionSnapshotInput.Feed feed=new MissionSnapshotInput.Feed("Synthetic follow","00000000-0000-4000-8000-000000000242","https://lab.example.invalid:8443");
    private final Deque<Runnable> worker=new ConcurrentLinkedDeque<>();
    private final AtomicInteger lists=new AtomicInteger(),fetches=new AtomicInteger(),imageLists=new AtomicInteger(),downloads=new AtomicInteger(),notices=new AtomicInteger();
    private final List<List<UUID>> completedReceipts=new CopyOnWriteArrayList<>();private final List<List<ImageAssociationRegistry.Record>> completedRecords=new CopyOnWriteArrayList<>();
    private volatile boolean connected=true,configured=true,failing;private volatile CountDownLatch entered,release;
    private Activity activity;private FeedFollowUi ui;private LocalV2Store local;private Path privateRoot,syncDir;private byte[] container,localBefore;private TmmDraft incoming;

    @Before public void setup()throws Exception {
        privateRoot=temp.newFolder().toPath().resolve("private");syncDir=privateRoot.resolve("feed-sync-v1");
        try(InputStream in=getClass().getResourceAsStream("/h1.tmmi")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);container=out.toByteArray();}
        incoming=new TmmDraft(new UUID(0,242),"PIR SYNTHETIC",DeviceType.PIR,DeviceStatus.PLANNED,"ROLE",Instant.EPOCH,Instant.ofEpochSecond(10),null,"remote",new Position(0,1),45.0,null,1);
        local=new LocalV2Store(privateRoot.resolve("local-drafts-v1"));local.initializeNew();localBefore=Files.readAllBytes(privateRoot.resolve("local-drafts-v1/drafts.bin"));
        activity=Robolectric.buildActivity(Activity.class).setup().get();Handler handler=new Handler(Looper.getMainLooper());
        ui=new FeedFollowUi(activity,local,new se.tmm.sensorplanner.images.LocalImages(privateRoot.resolve("local-images-v1")),privateRoot,worker::add,new FeedFollowUi.Host(){
            public void post(Runnable r){handler.post(r);}public boolean available(){return true;}
            public List<String> profiles(){return connected?Collections.singletonList(STREAM):Collections.emptyList();}
            public boolean configured(String stream){return configured&&STREAM.equals(stream);}
            public boolean defaultProvider(){return true;}
            public void notice(String text){notices.incrementAndGet();}
            public void onFetchCompleted(List<UUID> receipts,List<ImageAssociationRegistry.Record> records){completedReceipts.add(receipts);completedRecords.add(records);}
            public FeedFollowUi.Transport connect(MissionEndpoint endpoint){assertEquals(STREAM,endpoint.stream);assertEquals(feed.service,endpoint.base);return new FeedFollowUi.Transport(){
                public String description(){return feed.service;}
                public List<MissionSnapshotInput.Feed> list(MissionReadAttempt a)throws Exception{
                    lists.incrementAndGet();CountDownLatch e=entered;if(e!=null){e.countDown();release.await(5,TimeUnit.SECONDS);}
                    if(failing)throw new IOException("synthetic failure");return Collections.singletonList(feed);
                }
                public MissionSnapshotInput.Snapshot fetch(MissionSnapshotInput.Feed f,MissionReadAttempt a){fetches.incrementAndGet();return new MissionSnapshotInput.Snapshot(f,Collections.singleton(incoming.uid.toString()),Collections.singletonList(incoming));}
                public List<MissionImageInput.Resource> images(MissionSnapshotInput.Feed f,MissionReadAttempt a)throws Exception{
                    imageLists.incrementAndGet();StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(container))hash.append(String.format("%02x",b&255));
                    JSONObject resource=new JSONObject().put("data",new JSONObject().put("name","synthetic.tmmi").put("hash",hash.toString()).put("mimeType","application/octet-stream").put("size",container.length));
                    JSONObject mission=new JSONObject().put("name",f.name).put("guid",f.guid).put("contents",new JSONArray().put(resource));
                    return MissionImageInput.read(f,new ByteArrayInputStream(new JSONObject().put("type","Mission").put("data",new JSONArray().put(mission)).toString().getBytes(StandardCharsets.UTF_8)),()->a.active(System.nanoTime()));
                }
                public ReceivedImagePayload image(MissionSnapshotInput.Feed f,MissionImageInput.Resource r,MissionReadAttempt a)throws Exception{downloads.incrementAndGet();return ImageContainerCodec.read(new ByteArrayInputStream(container));}
            };}
        });
    }
    @After public void close(){if(release!=null)release.countDown();if(ui!=null)ui.close();idle();worker.clear();activity.finish();}

    @Test public void followCreatesStoredSourceWithoutCredentialAndFetchesAtOnce()throws Exception {
        ui.start();settle();assertNull(new FeedSyncState(syncDir).read());assertEquals(0,lists.get());
        ui.show();await("Välj ansluten ATAK-server");pick(0);await("Bekräfta server för hämtning");click(AlertDialog.BUTTON_POSITIVE);
        await("Välj feed att följa");pick(0);await("Följ feed?");click(AlertDialog.BUTTON_POSITIVE);
        await("Starta mottagen bildhistorik?");assertFalse("No silent history initialization",Files.exists(privateRoot.resolve("metadata-inbox-v1/inbox.bin")));
        click(AlertDialog.BUTTON_POSITIVE);await("Följer feed");settle();
        FeedSyncState.Snapshot saved=new FeedSyncState(syncDir).read();
        assertTrue(saved.following);assertEquals(feed.context(),saved.contextId);assertEquals(feed.name,saved.feedName);assertEquals(STREAM,saved.streamConnect);assertEquals(1,saved.records.size());
        assertEquals(new InboxStore(privateRoot.resolve("metadata-inbox-v1")).load().inboxId,saved.inboxId);
        // Exactly the format's fields: nothing else, such as a credential, is in the file.
        Path copy=temp.newFolder().toPath();new FeedSyncState(copy).create(saved);assertArrayEquals(Files.readAllBytes(copy.resolve("sync.bin")),Files.readAllBytes(syncDir.resolve("sync.bin")));
        assertEquals(1,downloads.get());assertEquals(1,completedReceipts.size());assertEquals(1,completedReceipts.get(0).size());assertEquals(1,completedRecords.get(0).size());
        // U3: the new device is added automatically, without an image, and its base is the fetched receipt.
        LocalV2.State added=local.load().state(incoming.uid);assertEquals(LocalV2.Presence.ACTIVE,added.presence);assertNull(added.draft.imageRef);
        FeedSyncState.Record record=new FeedSyncState(syncDir).read().records.get(0);assertEquals(incoming.uid,record.deviceUid);assertEquals(completedReceipts.get(0).get(0),record.baseReceiptId);
        assertTrue(ui.conflicts().isEmpty());assertTrue(ui.unpublished().isEmpty());
    }
    @Test public void cancelAtHistoryConfirmationCreatesNothing()throws Exception {
        ui.start();settle();ui.show();await("Välj ansluten ATAK-server");pick(0);await("Bekräfta server för hämtning");click(AlertDialog.BUTTON_POSITIVE);
        await("Välj feed att följa");pick(0);await("Följ feed?");click(AlertDialog.BUTTON_POSITIVE);await("Starta mottagen bildhistorik?");click(AlertDialog.BUTTON_NEGATIVE);drain();
        assertNull(new FeedSyncState(syncDir).read());assertFalse(Files.exists(privateRoot.resolve("metadata-inbox-v1")));idleFor(600);assertEquals(1,lists.get());
    }
    @Test public void stopFollowingKeepsRecordsAndStopsFetching()throws Exception {
        FeedSyncState.Record record=new FeedSyncState.Record(incoming.uid,new UUID(1,1),new UUID(1,2),1,null,null,null);
        following(Collections.singletonList(record));ui.start();settle();assertEquals(1,lists.get());
        ui.show();await("Följer feed");click(AlertDialog.BUTTON_NEUTRAL);drain();await("Följning avstängd");
        FeedSyncState.Snapshot saved=new FeedSyncState(syncDir).read();assertFalse(saved.following);assertEquals(feed.context(),saved.contextId);assertEquals(STREAM,saved.streamConnect);
        assertEquals(1,saved.records.size());assertEquals(record.remoteReceiptId,saved.records.get(0).remoteReceiptId);assertEquals(record.baseReceiptId,saved.records.get(0).baseReceiptId);
        idleFor(900);ui.refresh();ui.connectionChanged();settle();assertEquals(1,lists.get());
        click(AlertDialog.BUTTON_POSITIVE);drain();await("Följer feed");settle();assertTrue(new FeedSyncState(syncDir).read().following);assertEquals(2,lists.get());
    }
    @Test public void firesOnLoadIntervalManualAndReconnect()throws Exception {
        following(Collections.emptyList());ui.start();settle();assertEquals(1,lists.get());
        idleFor(59);assertEquals(1,lists.get());idleFor(1);settle();assertEquals(2,lists.get());
        ui.refresh();settle();assertEquals(3,lists.get());
        connected=false;ui.connectionChanged();idle();idleFor(300);settle();assertEquals(3,lists.get());assertTrue(ui.label().contains("pausad"));
        connected=true;ui.connectionChanged();idle();settle();assertEquals(4,lists.get());assertFalse(ui.label().contains("pausad"));
    }
    @Test public void failureDoublesIntervalUpToFiveMinutesAndSuccessResets()throws Exception {
        following(Collections.emptyList());failing=true;ui.start();settle();assertEquals(1,lists.get());
        long[] waits={120,240,300,300};int calls=1;
        for(long wait:waits){idleFor(wait-1);assertEquals(calls,lists.get());idleFor(1);settle();assertEquals(++calls,lists.get());}
        failing=false;ui.refresh();settle();assertEquals(++calls,lists.get());
        idleFor(59);assertEquals(calls,lists.get());idleFor(1);settle();assertEquals(++calls,lists.get());
    }
    @Test public void onlyOneAttemptAtATime()throws Exception {
        following(Collections.emptyList());entered=new CountDownLatch(1);release=new CountDownLatch(1);ui.start();drain();idle();
        assertTrue(entered.await(3,TimeUnit.SECONDS));assertTrue(ui.isFetching());
        ui.refresh();ui.connectionChanged();idle();idleFor(120);assertEquals(1,lists.get());
        entered=null;release.countDown();settle();assertEquals(1,lists.get());assertFalse(ui.isFetching());
    }
    @Test public void profileLossOrChangePausesWithReasonAndNeverSwitches()throws Exception {
        following(Collections.emptyList());connected=false;configured=false;ui.start();settle();
        assertEquals(0,lists.get());assertTrue(ui.label().contains("pausad"));assertEquals(1,notices.get());
        ui.refresh();idleFor(600);settle();assertEquals(0,lists.get());assertEquals("Same reason is not repeated",1,notices.get());
        ui.show();await("Följer feed");assertTrue(message().contains("saknas eller har ändrat"));click(AlertDialog.BUTTON_NEGATIVE);
        configured=true;ui.connectionChanged();idle();ui.show();await("Följer feed");assertTrue(message().contains("inte ansluten"));click(AlertDialog.BUTTON_NEGATIVE);
        assertEquals(STREAM,new FeedSyncState(syncDir).read().streamConnect);assertEquals(0,lists.get());
    }
    @Test public void unloadCancelsAttemptAndIgnoresLaterCallbacks()throws Exception {
        following(Collections.emptyList());entered=new CountDownLatch(1);release=new CountDownLatch(1);ui.start();drain();idle();
        assertTrue(entered.await(3,TimeUnit.SECONDS));byte[] inboxBefore=Files.readAllBytes(privateRoot.resolve("metadata-inbox-v1/inbox.bin"));
        ui.close();entered=null;release.countDown();Thread.sleep(100);drain();idle();idleFor(900);drain();
        assertEquals(1,lists.get());assertEquals(0,fetches.get());assertTrue(completedReceipts.isEmpty());
        assertArrayEquals(inboxBefore,Files.readAllBytes(privateRoot.resolve("metadata-inbox-v1/inbox.bin")));
        ui.refresh();ui.connectionChanged();ui.start();drain();idle();assertEquals(1,lists.get());
    }
    @Test public void repeatedFetchDoesNotRedownloadAndLeavesFilesByteIdentical()throws Exception {
        following(Collections.emptyList());ui.start();settle();assertEquals(1,downloads.get());
        Map<Path,byte[]> before=snapshotFiles();
        idleFor(60);settle();ui.refresh();settle();
        assertEquals(3,lists.get());assertEquals(3,imageLists.get());assertEquals("Staged hash is never downloaded again",1,downloads.get());
        assertEquals(before.keySet(),snapshotFiles().keySet());for(Map.Entry<Path,byte[]> e:snapshotFiles().entrySet())assertArrayEquals(e.getKey().toString(),before.get(e.getKey()),e.getValue());
        assertEquals(3,completedRecords.size());assertEquals(completedReceipts.get(0),completedReceipts.get(2));
        assertEquals(completedRecords.get(0).get(0).associationId,completedRecords.get(2).get(0).associationId);
    }

    private void following(List<FeedSyncState.Record> records)throws Exception {
        InboxStore inbox=new InboxStore(privateRoot.resolve("metadata-inbox-v1"));UUID inboxId=inbox.initializeNew().inboxId;
        new ImageAssociationRegistry(privateRoot.resolve("received-image-association-v1")).initializeNew(inboxId);new ImageStageStore(privateRoot.resolve("received-image-stage-v1")).initializeNew();
        new FeedSyncState(syncDir).create(new FeedSyncState.Snapshot(UUID.randomUUID(),inboxId,true,feed.context(),feed.name,STREAM,records));
    }
    private Map<Path,byte[]> snapshotFiles()throws IOException {
        Map<Path,byte[]> files=new TreeMap<>();
        try(java.util.stream.Stream<Path> all=Files.walk(privateRoot)){for(Path p:(Iterable<Path>)all::iterator)if(Files.isRegularFile(p))files.put(privateRoot.relativize(p),Files.readAllBytes(p));}
        return files;
    }
    private void settle()throws Exception {
        for(int n=0;n<1000;n++){drain();idle();if(!ui.isFetching()&&worker.isEmpty())return;Thread.sleep(5);}
        fail("Fetch did not settle");
    }
    private void drain(){for(Runnable r;(r=worker.poll())!=null;){r.run();idle();}idle();}
    private void await(String expected)throws Exception {
        for(int n=0;n<1000;n++){drain();AlertDialog d=dialog();if(d!=null&&d.isShowing()&&expected.equals(shadowOf(d).getTitle().toString()))return;Thread.sleep(5);}
        fail("Expected "+expected+" but got "+(dialog()==null?"none":shadowOf(dialog()).getTitle()));
    }
    private String message(){return shadowOf(dialog()).getMessage().toString();}
    private AlertDialog dialog(){return ShadowAlertDialog.getLatestAlertDialog();}
    private void click(int button){dialog().getButton(button).performClick();idle();}
    private void pick(int n){dialog().getListView().performItemClick(null,n,n);idle();}
    private void idle(){shadowOf(Looper.getMainLooper()).idle();}
    private void idleFor(long seconds){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds));}
}
