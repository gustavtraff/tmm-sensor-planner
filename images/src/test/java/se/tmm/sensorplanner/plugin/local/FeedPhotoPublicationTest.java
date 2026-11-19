package se.tmm.sensorplanner.plugin.local;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.*;
import se.tmm.sensorplanner.plugin.ingress.*;
import se.tmm.sensorplanner.plugin.local.FeedPhotoPublication.*;
import static org.junit.Assert.*;

/** #243 W2–W4 with real private stores, the synthetic h1/h2 containers and a fake transport. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeedPhotoPublicationTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String CONTEXT="mission-api-v1|https://lab.example.invalid:8443|00000000-0000-4000-8000-000000000242",
            GUID="00000000-0000-4000-8000-000000000242",STREAM="lab.example.invalid:8089:ssl";
    private Path root;private LocalV2Store local;private LocalImages images;private InboxStore inbox;private ImageAssociationRegistry registry;
    private ImageStageStore stage;private FeedSyncState sync;private ImageAssociationRegistry.Record h1;private UUID device;
    private final Fake fake=new Fake();private final List<String> profiles=new ArrayList<>(Collections.singletonList(STREAM));
    private boolean provider=true;private FeedPhotoPublication service;private Runnable onConnect=()->{};

    @Before public void setup()throws Exception {
        root=temp.newFolder().toPath();
        local=new LocalV2Store(root.resolve("local-drafts-v1"));local.initializeNew();images=new LocalImages(root.resolve("local-images-v1"));
        inbox=new InboxStore(root.resolve("metadata-inbox-v1"));UUID inboxId=inbox.initializeNew().inboxId;
        registry=new ImageAssociationRegistry(root.resolve("received-image-association-v1"));registry.initializeNew(inboxId);
        stage=new ImageStageStore(root.resolve("received-image-stage-v1"));stage.initializeNew();
        sync=new FeedSyncState(root.resolve("feed-sync-v1"));
        sync.create(new FeedSyncState.Snapshot(UUID.randomUUID(),inboxId,true,CONTEXT,"Synthetic follow",STREAM,Collections.emptyList()));
        // The device and photo 1 arrive from the feed: photo 1 is the device's image base.
        h1=intake(container("/h1.tmmi"));device=h1.deviceUid;
        new FeedSyncApply(sync,inbox,stage,local,images).apply(sync.read(),Collections.singletonList(h1.receiptId),Collections.singletonList(h1),()->true);
        assertArrayEquals(h1.sha256(),base().imageDigest());
        // The deployer then attaches photo 2 locally.
        ReceivedImagePayload second=container("/h2.tmmi");
        try(LocalImages.Pending pending=images.prepareReceived(second.image)){
            new DraftAttachments(local,images).attach(local.load().select(device),pending,Instant.now());
        }
        service=new FeedPhotoPublication(local,images,root,new FeedPhotoPublication.Host(){
            public boolean available(){return true;}public boolean defaultProvider(){return provider;}
            public List<String> profiles(){return profiles;}
            public Transport connect(MissionEndpoint endpoint){assertEquals("https://lab.example.invalid:8443",endpoint.base);assertEquals(STREAM,endpoint.stream);onConnect.run();return fake;}
            public String creatorUid(){return "ANDROID-synthetic";}
        });
        service.fetchCompleted(Collections.singletonList(h1));
    }
    @After public void close(){service.close();}

    @Test public void uploadsTheFrozenContainerThenAssociatesItsVerifiedHash()throws Exception {
        Request request=service.review(device);byte[] previewed=request.review.preview();
        Result result=service.publish(request,()->true);
        assertEquals(result.message,Outcome.SUCCEEDED,result.outcome);
        assertEquals(Arrays.asList("upload","associate"),fake.calls);
        assertTrue(fake.name.matches("[0-9a-f-]{36}\\.tmmi"));assertEquals("ANDROID-synthetic",fake.creator);
        ReceivedImagePayload sent=ImageContainerCodec.read(new ByteArrayInputStream(fake.body));
        assertArrayEquals("Exactly the previewed export of photo 2",previewed,sent.image.bytes());assertNotEquals(h1.pngLength,previewed.length);
        assertEquals(device,sent.offer.metadata.uid);
        assertEquals(sha256(fake.body),fake.associated);assertEquals(GUID,fake.guid);
        assertNoOutboxFile();
    }
    @Test public void hashMismatchFailsWithoutAssociation()throws Exception {
        fake.reply="0".repeat(64);
        Result result=publish();
        assertEquals(Outcome.FAILED,result.outcome);assertTrue(result.message.contains("inte samma"));
        assertEquals(Collections.singletonList("upload"),fake.calls);assertFalse(service.awaitingRemoval(device));
    }
    @Test public void cancellationDuringUploadDoesNotStartAssociationOrAdoptThePhoto()throws Exception {
        AtomicBoolean active=new AtomicBoolean(true);fake.afterUpload=()->active.set(false);
        TmmDraft before=local.load().select(device).draft;
        Result result=service.publish(service.review(device),active::get);
        assertEquals(Outcome.FAILED,result.outcome);assertTrue(result.message.contains("avbröts")&&result.message.contains("inte kopplad"));
        assertEquals(Collections.singletonList("upload"),fake.calls);
        assertEquals(before.imageRef,local.load().select(device).draft.imageRef);
        assertFalse(service.awaitingRemoval(device));assertNoOutboxFile();
    }
    @Test public void interruptionDuringUploadDoesNotStartAssociation()throws Exception {
        fake.afterUpload=()->Thread.currentThread().interrupt();
        try{
            assertEquals(Outcome.FAILED,publish().outcome);
            assertEquals(Collections.singletonList("upload"),fake.calls);
        }finally{Thread.interrupted();}
        assertNoOutboxFile();
    }
    @Test public void cancellationDuringConnectionDoesNotStartUpload()throws Exception {
        AtomicBoolean active=new AtomicBoolean(true);onConnect=()->active.set(false);
        assertEquals(Outcome.FAILED,service.publish(service.review(device),active::get).outcome);
        assertTrue(fake.calls.isEmpty());assertNoOutboxFile();
    }
    @Test public void cancellationDuringRemovalConnectionDoesNotStartRemoval()throws Exception {
        assertEquals(Outcome.SUCCEEDED,publish().outcome);
        ImageAssociationRegistry.Record fresh=intake(ImageContainerCodec.read(new ByteArrayInputStream(fake.body)));
        service.fetchCompleted(Arrays.asList(h1,fresh));fake.calls.clear();
        AtomicBoolean active=new AtomicBoolean(true);onConnect=()->active.set(false);
        List<Result> results=service.removeReplaced(active::get);
        assertEquals(1,results.size());assertEquals(Outcome.FAILED,results.get(0).outcome);assertTrue(fake.calls.isEmpty());
    }
    @Test public void forbiddenUploadOrAssociationGivesThePermissionMessage()throws Exception {
        fake.uploadFailure=WriteFailure.Kind.REFUSED;
        Result upload=publish();
        assertEquals(Outcome.REFUSED,upload.outcome);
        for(String part:new String[]{"403","skrivbehörighet","skrivskyddad","lösenordsskyddad","inbjudan"})assertTrue(part,upload.message.contains(part));
        assertEquals(Collections.singletonList("upload"),fake.calls);
        fake.calls.clear();fake.uploadFailure=null;fake.associateFailure=WriteFailure.Kind.REFUSED;
        Result associate=publish();
        assertEquals(Outcome.REFUSED,associate.outcome);assertTrue(associate.message.contains("403"));assertTrue(associate.message.contains("innehållslager"));
        assertEquals(Arrays.asList("upload","associate"),fake.calls);assertFalse(service.awaitingRemoval(device));
    }
    @Test public void anyDestinationChangeRefusesBeforeSending()throws Exception {
        FeedSyncState.Snapshot s=sync.read();
        sync.replace(s,new FeedSyncState.Snapshot(s.syncId,s.inboxId,true,s.contextId,s.feedName,"other.example.invalid:8089:ssl",s.records));
        assertEquals(Outcome.FAILED,service.publish(requestFor(s),()->true).outcome);
        sync.replace(sync.read(),s);
        sync.replace(s,new FeedSyncState.Snapshot(s.syncId,s.inboxId,true,"mission-api-v1|https://lab.example.invalid:8443|00000000-0000-4000-8000-000000000243",s.feedName,s.streamConnect,s.records));
        assertEquals(Outcome.FAILED,service.publish(requestFor(s),()->true).outcome);
        sync.replace(sync.read(),s);
        profiles.clear();
        assertTrue(service.publish(service.review(device),()->true).message.contains("inte ansluten"));
        profiles.add(STREAM);provider=false;
        assertTrue(service.publish(service.review(device),()->true).message.contains("leverantör"));
        assertTrue(fake.calls.isEmpty());
    }
    @Test public void unknownOutcomeIsNeverResent()throws Exception {
        fake.uploadFailure=WriteFailure.Kind.UNKNOWN;
        Request request=service.review(device);
        Result result=service.publish(request,()->true);
        assertEquals(Outcome.UNKNOWN,result.outcome);assertTrue(result.message.contains("Okänt om bilden kom fram"));
        try{service.publish(request,()->true);fail("A request is one attempt");}catch(IllegalStateException expected){}
        service.fetchCompleted(Collections.singletonList(h1));assertTrue(service.removeReplaced(()->true).isEmpty());
        assertEquals(Collections.singletonList("upload"),fake.calls);
        // An unknown association waits for the fetch like a success, and is not resent either.
        fake.calls.clear();fake.uploadFailure=null;fake.associateFailure=WriteFailure.Kind.UNKNOWN;
        assertEquals(Outcome.UNKNOWN,publish().outcome);assertEquals(Arrays.asList("upload","associate"),fake.calls);
        assertTrue(service.awaitingRemoval(device));assertNoOutboxFile();
    }
    @Test public void removesOnlyTheReplacedBaseAndOnlyAfterTheNewPhotoIsSeen()throws Exception {
        assertEquals(Outcome.SUCCEEDED,publish().outcome);fake.calls.clear();
        assertTrue("Not seen yet",service.removeReplaced(()->true).isEmpty());
        service.fetchCompleted(Collections.singletonList(h1));assertTrue(service.removeReplaced(()->true).isEmpty());
        assertTrue(fake.calls.isEmpty());assertTrue(service.awaitingRemoval(device));
        // The next fetch shows the new photo next to the old one and another, never-base photo of the same device.
        ImageAssociationRegistry.Record fresh=intake(ImageContainerCodec.read(new ByteArrayInputStream(fake.body)));
        ImageAssociationRegistry.Record other=intake(container("/h2.tmmi"));assertEquals(device,other.deviceUid);
        service.fetchCompleted(Arrays.asList(h1,fresh,other));
        List<Result> results=service.removeReplaced(()->true);
        assertEquals(1,results.size());assertEquals(Outcome.SUCCEEDED,results.get(0).outcome);
        assertEquals(Collections.singletonList("remove"),fake.calls);assertEquals(GUID,fake.guid);
        try(InputStream in=getClass().getResourceAsStream("/h1.tmmi")){assertEquals(sha256(readAll(in)),fake.removed);}
        assertTrue("One attempt only",service.removeReplaced(()->true).isEmpty());assertEquals(1,fake.calls.size());
    }
    @Test public void removalFailureIsReportedOnceAndAGoneBaseIsNotRemoved()throws Exception {
        assertEquals(Outcome.SUCCEEDED,publish().outcome);fake.calls.clear();
        ImageAssociationRegistry.Record fresh=intake(ImageContainerCodec.read(new ByteArrayInputStream(fake.body)));
        fake.removeFailure=WriteFailure.Kind.NOT_SENT;service.fetchCompleted(Arrays.asList(h1,fresh));
        List<Result> results=service.removeReplaced(()->true);
        assertEquals(Outcome.FAILED,results.get(0).outcome);assertTrue(service.removeReplaced(()->true).isEmpty());assertEquals(1,fake.calls.size());
        // Publish again: the old base is no longer in the feed, so nothing is removed.
        fake.calls.clear();fake.removeFailure=null;assertEquals(Outcome.SUCCEEDED,publish().outcome);
        ImageAssociationRegistry.Record newer=intake(ImageContainerCodec.read(new ByteArrayInputStream(fake.body)));
        service.fetchCompleted(Collections.singletonList(newer));
        assertTrue(service.removeReplaced(()->true).isEmpty());assertEquals(Arrays.asList("upload","associate"),fake.calls);
    }
    @Test public void nothingIsSentWhenFollowingIsOff()throws Exception {
        Request request=service.review(device);
        FeedSyncState.Snapshot s=sync.read();
        sync.replace(s,new FeedSyncState.Snapshot(s.syncId,s.inboxId,false,s.contextId,s.feedName,s.streamConnect,s.records));
        Result result=service.publish(request,()->true);
        assertEquals(Outcome.FAILED,result.outcome);assertTrue(result.message.contains("avstängd"));
        try{service.review(device);fail();}catch(IOException expected){}
        assertTrue(fake.calls.isEmpty());assertNoOutboxFile();
    }

    private Result publish()throws IOException {return service.publish(service.review(device),()->true);}
    /** A request built while the stored state was {@code shown}, as a preview held across a change would be. */
    private Request requestFor(FeedSyncState.Snapshot shown)throws IOException {
        FeedSyncState.Snapshot now=sync.read();sync.replace(now,shown);
        try{return service.review(device);}finally{sync.replace(shown,now);}
    }
    private FeedSyncState.Record base()throws IOException {for(FeedSyncState.Record r:sync.read().records)if(r.deviceUid.equals(device))return r;throw new AssertionError();}
    private ImageAssociationRegistry.Record intake(ReceivedImagePayload payload)throws Exception {
        return new ReceivedImageIntake(inbox,registry,stage,local).receive(CONTEXT,payload,Instant.EPOCH,()->true);
    }
    private ReceivedImagePayload container(String name)throws Exception {try(InputStream in=getClass().getResourceAsStream(name)){return ImageContainerCodec.read(in);}}
    private void assertNoOutboxFile()throws IOException {
        Path outbox=root.resolve("feed-photo-outbox");if(!Files.exists(outbox))return;
        try(DirectoryStream<Path> all=Files.newDirectoryStream(outbox)){for(Path p:all)assertEquals("exports.lock",p.getFileName().toString());}
    }
    private static byte[] readAll(InputStream in)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}
    private static String sha256(byte[] bytes)throws Exception {
        StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }

    private static final class Fake implements Transport {
        final List<String> calls=new ArrayList<>();String name,creator,guid,associated,removed,reply;byte[] body;
        WriteFailure.Kind uploadFailure,associateFailure,removeFailure;
        Runnable afterUpload=()->{};
        public String upload(String name,String creatorUid,byte[] container,MissionReadAttempt attempt)throws WriteFailure {
            calls.add("upload");assertTrue(attempt.active(System.nanoTime()));this.name=name;creator=creatorUid;body=container.clone();
            if(uploadFailure!=null)throw new WriteFailure(uploadFailure);
            afterUpload.run();try{return reply!=null?reply:sha256(container);}catch(Exception e){throw new AssertionError(e);}
        }
        public void associate(String guid,String hash,String creatorUid,MissionReadAttempt attempt)throws WriteFailure {
            calls.add("associate");this.guid=guid;associated=hash;if(associateFailure!=null)throw new WriteFailure(associateFailure);
        }
        public void remove(String guid,String hash,String creatorUid,MissionReadAttempt attempt)throws WriteFailure {
            calls.add("remove");this.guid=guid;removed=hash;if(removeFailure!=null)throw new WriteFailure(removeFailure);
        }
    }
}
