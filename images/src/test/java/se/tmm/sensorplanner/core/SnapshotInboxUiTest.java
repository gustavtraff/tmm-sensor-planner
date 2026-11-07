package se.tmm.sensorplanner.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import se.tmm.sensorplanner.plugin.local.SnapshotInboxUi;
import se.tmm.sensorplanner.plugin.ingress.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,35},manifest=Config.NONE)
public class SnapshotInboxUiTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private Activity activity;private SnapshotInboxUi flow;private LocalV2Store local;private InboxStore inbox;private Path inboxFile,localFile;
    private final Queue<Runnable> work=new ArrayDeque<>(),posts=new ConcurrentLinkedQueue<>();
    private final AtomicBoolean available=new AtomicBoolean(true),lab=new AtomicBoolean(true);
    private final AtomicInteger lists=new AtomicInteger(),fetches=new AtomicInteger();
    private MissionSnapshotInput.Feed feed=new MissionSnapshotInput.Feed("Synthetic","00000000-0000-4000-8000-000000000171");
    private TmmDraft incoming;private volatile CountDownLatch entered,release;private volatile boolean wrongFeed,wrongSource,networkFailure;
    @Before public void setup()throws Exception{
        activity=Robolectric.buildActivity(Activity.class).setup().get();AlertDialog warm=new AlertDialog.Builder(activity).setTitle("Warmup").create();warm.show();warm.dismiss();Path root=temporary.newFolder().toPath();
        local=new LocalV2Store(root.resolve("local"));local.initializeNew();localFile=root.resolve("local/drafts.bin");inbox=new InboxStore(root.resolve("inbox"));inboxFile=root.resolve("inbox/inbox.bin");
        incoming=new TmmDraft(new UUID(0,171),"PIR SYNTHETIC",DeviceType.PIR,DeviceStatus.PLANNED,"ROLE",Instant.EPOCH,Instant.ofEpochSecond(10),null,"remote",new Position(0,1),45.0,null,1);
        flow=new SnapshotInboxUi(activity,local,inboxFile.getParent(),work::add,new SnapshotInboxUi.Host(){
            public void post(Runnable r){posts.add(r);}public boolean available(){return available.get();}public boolean sourceAvailable(){return lab.get();}public void changed(){}
        },new SnapshotInboxUi.Transport(){
            public List<MissionSnapshotInput.Feed> list(MissionReadAttempt attempt)throws Exception{lists.incrementAndGet();if(networkFailure)throw new java.io.IOException("PRIVATE-SYNTHETIC-ENDPOINT");return Collections.singletonList(feed);}
            public MissionSnapshotInput.Snapshot fetch(MissionSnapshotInput.Feed selected,MissionReadAttempt attempt)throws Exception{
                fetches.incrementAndGet();if(entered!=null){entered.countDown();release.await(5,TimeUnit.SECONDS);}
                return new MissionSnapshotInput.Snapshot(wrongFeed?new MissionSnapshotInput.Feed("Other","00000000-0000-4000-8000-000000000172"):wrongSource?new MissionSnapshotInput.Feed(selected.name,selected.guid,"https://other.example.invalid:8443"):selected,Collections.singleton(incoming.uid.toString()),Collections.singletonList(incoming));
            }
        },p->"SYNTHETIC MGRS");
    }
    @After public void cleanup(){flow.close();if(release!=null)release.countDown();activity.finish();}
    private AlertDialog dialog(){return ShadowAlertDialog.getLatestAlertDialog();}
    private void click(int button){try{Thread.sleep(50);}catch(InterruptedException e){throw new AssertionError(e);}dialog().getButton(button).performClick();pump();}
    private void pump(){for(Runnable r;(r=posts.poll())!=null;)r.run();org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private void work(){assertFalse(work.isEmpty());work.remove().run();pump();}
    private void awaitPost()throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<end){
            pump();String title=dialog()==null?"":org.robolectric.Shadows.shadowOf(dialog()).getTitle().toString();
            if(!title.equals("Läser feeds")&&!title.equals("Hämtar metadatasnapshot")){Thread.sleep(50);return;}
            Thread.sleep(10);
        }
        fail("Missing async result dialog");
    }
    private void listExisting()throws Exception{flow.fetch();work();awaitPost();assertEquals("Välj feed",org.robolectric.Shadows.shadowOf(dialog()).getTitle().toString());}
    private void choose(){dialog().getListView().performItemClick(null,0,0);pump();}
    private void fetchToWorker()throws Exception{listExisting();choose();click(AlertDialog.BUTTON_POSITIVE);awaitPost();}
    @Test public void missingInboxNeedsExplicitConsentAndCancelCreatesNothing()throws Exception{
        flow.fetch();work();assertFalse(Files.exists(inboxFile));assertEquals(0,lists.get());click(AlertDialog.BUTTON_NEGATIVE);assertFalse(Files.exists(inboxFile));
        flow.fetch();work();click(AlertDialog.BUTTON_POSITIVE);work();awaitPost();assertTrue(Files.exists(inboxFile));assertTrue(inbox.load().receipts.isEmpty());assertEquals(1,lists.get());
    }
    @Test public void successfulFetchStagesMetadataOnlyAndRepeatedFetchDeduplicates()throws Exception{
        inbox.initializeNew();byte[] original=Files.readAllBytes(localFile);fetchToWorker();assertTrue(inbox.load().receipts.isEmpty());work();
        assertEquals(1,inbox.load().receipts.size());assertTrue(local.load().activeDrafts().isEmpty());assertArrayEquals(original,Files.readAllBytes(localFile));
        InboxStore.Receipt saved=inbox.load().receipts.get(0);assertEquals(feed.context(),saved.contextId);assertNull(saved.incoming.imageRef);
        byte[] bytes=Files.readAllBytes(inboxFile);click(AlertDialog.BUTTON_NEGATIVE);Thread.sleep(30);fetchToWorker();work();assertArrayEquals(bytes,Files.readAllBytes(inboxFile));assertEquals(saved.id,inbox.load().receipts.get(0).id);
    }
    @Test public void reviewCancelAndQueuedStorageCancelDoNotReceiveAnything()throws Exception{
        inbox.initializeNew();listExisting();choose();click(AlertDialog.BUTTON_NEGATIVE);assertEquals(0,fetches.get());assertTrue(inbox.load().receipts.isEmpty());
        Thread.sleep(30);fetchToWorker();click(AlertDialog.BUTTON_NEGATIVE);work();assertTrue(inbox.load().receipts.isEmpty());assertTrue(local.load().activeDrafts().isEmpty());
    }
    @Test public void sourceLossBeforeStoreAndLateNetworkCompletionCannotWrite()throws Exception{
        inbox.initializeNew();fetchToWorker();lab.set(false);work();assertTrue(inbox.load().receipts.isEmpty());
        lab.set(true);flow.cancel();Thread.sleep(30);entered=new CountDownLatch(1);release=new CountDownLatch(1);listExisting();choose();click(AlertDialog.BUTTON_POSITIVE);assertTrue(entered.await(3,TimeUnit.SECONDS));flow.cancel();release.countDown();Thread.sleep(50);pump();assertTrue(inbox.load().receipts.isEmpty());assertTrue(work.isEmpty());
    }
    @Test public void corruptHistoryIsNotInitializedAndErrorsDoNotExposeProviderText()throws Exception{
        Files.createDirectories(inboxFile.getParent());Files.write(inboxFile,new byte[]{1,2,3});flow.fetch();work();assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(inboxFile));assertEquals(0,lists.get());
        Files.delete(inboxFile);inbox.initializeNew();networkFailure=true;flow.fetch();work();awaitPost();assertTrue(inbox.load().receipts.isEmpty());assertEquals("Hämtningen kunde inte verifieras",org.robolectric.Shadows.shadowOf(dialog()).getTitle().toString());
    }
    private boolean trusted(UUID id,InboxStore.Receipt receipt){
        return org.robolectric.util.ReflectionHelpers.callInstanceMethod(flow,"trusted",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(UUID.class,id),
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(InboxStore.Receipt.class,receipt));
    }
    @Test public void applicationGrantIsBoundToInboxContextMembershipAndCurrentAccess()throws Exception{
        inbox.initializeNew();fetchToWorker();work();InboxStore.Snapshot saved=inbox.load();InboxStore.Receipt received=saved.receipts.get(0);
        assertTrue(trusted(saved.inboxId,received));assertFalse(trusted(UUID.randomUUID(),received));
        InboxStore.Receipt other=inbox.receive("OTHER SOURCE",incoming,null,Instant.EPOCH);assertFalse(trusted(saved.inboxId,other));
        TmmDraft absent=new TmmDraft(new UUID(0,172),incoming.displayId,incoming.type,incoming.status,incoming.owner,incoming.createdAt,incoming.updatedAt,null,incoming.comment,incoming.position,incoming.bearingDeg,null,1);
        InboxStore.Receipt absentReceipt=inbox.receive(feed.context(),absent,null,Instant.EPOCH);assertFalse(trusted(saved.inboxId,absentReceipt));
        lab.set(false);assertFalse(trusted(saved.inboxId,received));lab.set(true);flow.cancel();assertFalse(trusted(saved.inboxId,received));
    }
    @Test public void missingLocalDatasetCannotBecomeNewInboxConsent()throws Exception{
        Files.delete(localFile);flow.fetch();work();assertFalse(Files.exists(inboxFile));assertEquals(0,lists.get());
        assertEquals("Lokala lagringen kunde inte läsas",org.robolectric.Shadows.shadowOf(dialog()).getTitle().toString());
    }
    @Test public void sameNamedFeedFromDifferentServiceCannotReachInbox()throws Exception{
        inbox.initializeNew();wrongSource=true;byte[] original=Files.readAllBytes(localFile);
        listExisting();choose();click(AlertDialog.BUTTON_POSITIVE);awaitPost();
        assertTrue(work.isEmpty());assertTrue(inbox.load().receipts.isEmpty());assertArrayEquals(original,Files.readAllBytes(localFile));
    }
    @Test public void wrongReturnedFeedCannotReachTheInbox()throws Exception{
        inbox.initializeNew();wrongFeed=true;listExisting();choose();click(AlertDialog.BUTTON_POSITIVE);awaitPost();assertTrue(work.isEmpty());assertTrue(inbox.load().receipts.isEmpty());
    }
}
