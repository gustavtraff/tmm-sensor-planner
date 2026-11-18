package se.tmm.sensorplanner.plugin.local;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.*;
import static org.junit.Assert.*;

/** #242 U3–U5 executor against real private stores and the synthetic h1/h2 containers. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
public class FeedSyncApplyTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String CONTEXT="mission-api-v1|https://lab.example.invalid:8443|00000000-0000-4000-8000-000000000242";
    private Path root;private LocalV2Store local;private LocalImages images;private InboxStore inbox;private ImageAssociationRegistry registry;private ImageStageStore stage;private FeedSyncState sync;
    private ImageAssociationRegistry.Record h1,h2;private TmmDraft m1,deployed;private UUID r1,rDeployed;private byte[] png1,png2;

    @Before public void setup()throws Exception {
        root=temp.newFolder().toPath();
        local=new LocalV2Store(root.resolve("local-drafts-v1"));local.initializeNew();images=new LocalImages(root.resolve("local-images-v1"));
        inbox=new InboxStore(root.resolve("metadata-inbox-v1"));UUID inboxId=inbox.initializeNew().inboxId;
        registry=new ImageAssociationRegistry(root.resolve("received-image-association-v1"));registry.initializeNew(inboxId);
        stage=new ImageStageStore(root.resolve("received-image-stage-v1"));stage.initializeNew();
        sync=new FeedSyncState(root.resolve("feed-sync-v1"));
        sync.create(new FeedSyncState.Snapshot(UUID.randomUUID(),inboxId,true,CONTEXT,"Synthetic follow","lab.example.invalid:8089:ssl",Collections.emptyList()));
        h1=intake(container("/h1.tmmi",null));r1=h1.receiptId;m1=receipt(r1);assertEquals(h1.deviceUid,m1.uid);
        // The deployer's photo 2 is bound to their new metadata M2, as a real publication would be.
        deployed=changed(m1,"deployed",m1.updatedAt.plusSeconds(30));h2=intake(container("/h2.tmmi",deployed));rDeployed=h2.receiptId;
        assertTrue(same(receipt(rDeployed),deployed));
        png1=stage.read(h1.stageLocalId).image.bytes();png2=stage.read(h2.stageLocalId).image.bytes();
    }

    @Test public void addCreatesTheDeviceWithoutImageAndSetsTheBase()throws Exception {
        run(executor(),list(r1),list());
        LocalV2.State state=local.load().state(m1.uid);
        assertEquals(LocalV2.Presence.ACTIVE,state.presence);assertTrue(same(state.draft,m1));assertNull(state.draft.imageRef);assertEquals(1,state.draft.revision);
        FeedSyncState.Record rec=record();assertEquals(r1,rec.baseReceiptId);assertEquals(FeedSyncState.IMAGE_NONE,rec.imageKind);
    }
    @Test public void deployerPhotoBoundToNewMetadataWinsWithoutFlipAndSurvivesStatusOnlyUpdate()throws Exception {
        run(executor(),list(r1),list(h1));
        // Photo 2 is published with M2 while photo 1 is still in the feed.
        run(executor(),list(rDeployed),list(h1,h2));
        TmmDraft draft=local.load().state(m1.uid).draft;assertTrue(same(draft,deployed));assertArrayEquals(png2,images.read(draft.imageRef));
        Map<Path,byte[]> before=files(root);
        FeedSyncApply apply=executor();run(apply,list(rDeployed),list(h1,h2));
        assertSameFiles(before,files(root));assertEquals(0,apply.applied());assertTrue(apply.conflicts().isEmpty());
        // A later status-only M3, with both photos still in the feed and neither bound to M3, keeps photo 2.
        TmmDraft m3=new TmmDraft(deployed.uid,deployed.displayId,deployed.type,deployed.status==DeviceStatus.PLANNED?DeviceStatus.ACTIVE:DeviceStatus.PLANNED,
                deployed.owner,deployed.createdAt,deployed.updatedAt.plusSeconds(30),deployed.deployedAt,deployed.comment,deployed.position,deployed.bearingDeg,null,1);
        UUID r3=receive(m3);run(apply,list(r3),list(h1,h2));
        TmmDraft after=local.load().state(m1.uid).draft;assertTrue(same(after,m3));assertEquals(draft.imageRef,after.imageRef);
        assertEquals(r3,record().baseReceiptId);assertArrayEquals(h2.sha256(),record().imageDigest());
        assertTrue(apply.conflicts().isEmpty());assertTrue(apply.unpublished().isEmpty());assertEquals(1,pngs());
    }
    @Test public void plannedDeviceWithoutPhotoThenDeployerPhotoIsAppliedNotConflict()throws Exception {
        // The planner created and published the device without a photo; this client already has it locally.
        TmmDraft planned=new TmmDraft(m1.uid,m1.displayId,m1.type,m1.status,m1.owner,m1.createdAt,m1.createdAt,m1.deployedAt,"planned",m1.position,m1.bearingDeg,null,1);
        local.save(local.load().storeId,LocalV2.State.neverSeen(m1.uid),planned);UUID r0=receive(planned);
        byte[] draftsBefore=Files.readAllBytes(root.resolve("local-drafts-v1/drafts.bin"));
        FeedSyncApply apply=executor();run(apply,list(r0),list());
        assertArrayEquals("R = L changes nothing locally",draftsBefore,Files.readAllBytes(root.resolve("local-drafts-v1/drafts.bin")));
        assertEquals(r0,record().baseReceiptId);assertEquals("No image on either side: base NONE",FeedSyncState.IMAGE_NONE,record().imageKind);
        // The deployer publishes new metadata and a placement photo.
        run(apply,list(r1),list(h1));
        TmmDraft draft=local.load().state(m1.uid).draft;assertTrue(same(draft,m1));assertArrayEquals(png1,images.read(draft.imageRef));
        assertEquals(r1,record().baseReceiptId);assertArrayEquals(h1.sha256(),record().imageDigest());
        assertTrue(apply.conflicts().isEmpty());assertTrue(apply.unpublished().isEmpty());
    }
    @Test public void addThenApplyImageEndToEndWithReadback()throws Exception {
        FeedSyncApply apply=executor();run(apply,list(r1),list(h1));
        TmmDraft draft=local.load().state(m1.uid).draft;assertTrue(same(draft,m1));
        assertArrayEquals(png1,images.read(draft.imageRef));
        FeedSyncState.Record rec=record();assertEquals(FeedSyncState.IMAGE_DIGEST,rec.imageKind);assertArrayEquals(h1.sha256(),rec.imageDigest());
        assertNull(rec.adoptNew);assertNull(rec.adoptOld);assertTrue(apply.conflicts().isEmpty());assertTrue(apply.unpublished().isEmpty());
        assertEquals(1,pngs());
    }
    @Test public void replacingTheImageDeletesTheOldCopyOnlyAfterCommit()throws Exception {
        run(executor(),list(r1),list(h1));String old=local.load().state(m1.uid).draft.imageRef;
        run(executor(),list(rDeployed),list(h1,h2));
        TmmDraft draft=local.load().state(m1.uid).draft;assertNotEquals(old,draft.imageRef);
        assertArrayEquals(png2,images.read(draft.imageRef));assertArrayEquals(h2.sha256(),record().imageDigest());
        assertTrue(same(draft,deployed));assertEquals(1,pngs());
    }
    @Test public void applyMetadataKeepsTheLocalImageAndCreationIdentity()throws Exception {
        run(executor(),list(r1),list(h1));TmmDraft before=local.load().state(m1.uid).draft;
        TmmDraft m2=changed(m1,"remote update",m1.updatedAt.plusSeconds(60));UUID r2=receive(m2);
        FeedSyncApply apply=executor();run(apply,list(r2),list(h1));
        TmmDraft after=local.load().state(m1.uid).draft;
        assertTrue(same(after,m2));assertEquals(before.imageRef,after.imageRef);assertEquals(before.createdAt,after.createdAt);assertEquals(before.revision+1,after.revision);
        assertEquals(r2,record().baseReceiptId);assertArrayEquals(h1.sha256(),record().imageDigest());
        assertTrue(apply.conflicts().isEmpty());assertTrue(apply.unpublished().isEmpty());
    }
    @Test public void conflictChangesNothingLocallyAndIsExposed()throws Exception {
        run(executor(),list(r1),list(h1));
        LocalV2.State state=local.load().state(m1.uid);
        local.save(local.load().storeId,state,state.draft.edit(state.draft.displayId,state.draft.type,state.draft.status,state.draft.owner,
                state.draft.deployedAt,"local edit",state.draft.position,state.draft.bearingDeg,state.draft.imageRef,state.draft.updatedAt.plusSeconds(5)));
        TmmDraft m2=changed(m1,"remote update",m1.updatedAt.plusSeconds(60));UUID r2=receive(m2);
        Map<Path,byte[]> before=files(root.resolve("local-drafts-v1"));before.putAll(files(root.resolve("local-images-v1")));
        FeedSyncApply apply=executor();run(apply,list(r2),list(h1));
        Map<Path,byte[]> after=files(root.resolve("local-drafts-v1"));after.putAll(files(root.resolve("local-images-v1")));
        assertSameFiles(before,after);
        assertEquals(Collections.singleton(m1.uid),apply.conflicts());assertEquals(Collections.singleton(m1.uid),apply.unpublished());
        assertEquals("Base is not moved by a conflict",r1,record().baseReceiptId);assertEquals(r2,record().remoteReceiptId);
    }
    @Test public void duplicateFetchLeavesEveryStateFileByteIdentical()throws Exception {
        run(executor(),list(r1),list(h1));Map<Path,byte[]> before=files(root);
        FeedSyncApply apply=executor();run(apply,list(r1),list(h1));run(apply,list(r1),list(h1));
        assertSameFiles(before,files(root));assertEquals(0,apply.applied());
    }
    @Test public void staleLocalStateSkipsTheDeviceThisRound()throws Exception {
        run(executor(),list(r1),list());
        TmmDraft m2=changed(m1,"remote update",m1.updatedAt.plusSeconds(60));UUID r2=receive(m2);
        FeedSyncApply apply=executor();
        apply.steps=step->{if(FeedSyncApply.PLANNED.equals(step)){
            LocalV2.State s=local.load().state(m1.uid);
            local.save(local.load().storeId,s,s.draft.edit(s.draft.displayId,s.draft.type,s.draft.status,s.draft.owner,s.draft.deployedAt,"concurrent",s.draft.position,s.draft.bearingDeg,null,s.draft.updatedAt.plusSeconds(1)));
        }};
        run(apply,list(r2),list(h2));
        TmmDraft draft=local.load().state(m1.uid).draft;assertEquals("concurrent",draft.comment);assertNull(draft.imageRef);
        assertEquals(r1,record().baseReceiptId);assertEquals(FeedSyncState.IMAGE_NONE,record().imageKind);assertEquals(0,pngs());
    }
    @Test public void processKillAtEachAdoptionStepRecoversOnNextLoad()throws Exception {
        for(String step:new String[]{FeedSyncApply.PREPARED,FeedSyncApply.ADOPT_WRITTEN,FeedSyncApply.ATTACHED,FeedSyncApply.BASE_SET}){
            setup();
            run(executor(),list(r1),list(h1));String old=local.load().state(m1.uid).draft.imageRef;
            boolean[] hit={false};
            FeedSyncApply killed=executor();killed.steps=s->{if(step.equals(s)){hit[0]=true;throw new IOException("killed at "+s);}};
            run(killed,list(rDeployed),list(h1,h2));
            assertTrue(step,hit[0]);
            boolean pendingAdoption=step.equals(FeedSyncApply.ADOPT_WRITTEN)||step.equals(FeedSyncApply.ATTACHED);
            assertEquals(step,pendingAdoption,record().adoptNew!=null);
            // Next load: a fresh executor resolves the adoption before anything else.
            FeedSyncState.Snapshot recovered=executor().recover();FeedSyncState.Record rec=find(recovered);
            assertNull(step,rec.adoptNew);assertNull(step,rec.adoptOld);
            String current=local.load().state(m1.uid).draft.imageRef;assertNotNull(step,current);
            byte[] shown=images.read(current);
            if(step.equals(FeedSyncApply.PREPARED)||step.equals(FeedSyncApply.ADOPT_WRITTEN)){
                assertEquals(step,old,current);assertArrayEquals(step,png1,shown);assertArrayEquals(step,h1.sha256(),rec.imageDigest());
            }else{
                assertArrayEquals(step,png2,shown);assertArrayEquals(step,h2.sha256(),rec.imageDigest());
            }
            assertEquals(step+": no orphaned or lost copy",1,pngs());
            run(executor(),list(rDeployed),list(h1,h2));
            current=local.load().state(m1.uid).draft.imageRef;assertArrayEquals(step,png2,images.read(current));
            assertArrayEquals(step,h2.sha256(),record().imageDigest());assertNull(step,record().adoptNew);assertEquals(step,1,pngs());
        }
    }
    @Test public void recoveryLeavesAUserImageChangeAlone()throws Exception {
        run(executor(),list(r1),list(h1));
        FeedSyncState.Snapshot s=sync.read();FeedSyncState.Record rec=find(s);String old=local.load().state(m1.uid).draft.imageRef;
        // A crash left adoptNew/adoptOld, then the user attached another image.
        LocalImages.Pending orphan=images.prepareReceived(stage.read(h2.stageLocalId).image);String orphanRef=orphan.reference();
        sync.replace(s,new FeedSyncState.Snapshot(s.syncId,s.inboxId,true,s.contextId,s.feedName,s.streamConnect,Collections.singletonList(
                new FeedSyncState.Record(rec.deviceUid,rec.remoteReceiptId,rec.baseReceiptId,rec.imageKind,rec.imageDigest(),uuid(orphanRef),uuid(old)))));
        LocalImages.Pending user=images.prepareReceived(stage.read(h2.stageLocalId).image);String userRef=user.reference();
        new DraftAttachments(local,images).attach(local.load().select(m1.uid),user,local.load().state(m1.uid).draft.updatedAt);
        FeedSyncState.Record after=find(executor().recover());
        assertNull(after.adoptNew);assertArrayEquals("Image base unchanged",h1.sha256(),after.imageDigest());
        assertEquals(userRef,local.load().state(m1.uid).draft.imageRef);assertFalse(Files.exists(root.resolve("local-images-v1").resolve(orphanRef)));
        assertEquals(1,pngs());
    }


    // ---- #242 conflict resolution (Behåll min / Använd inkommande) ----

    /** Local comment edit and a different remote edit on top of the base r1: a metadata conflict. */
    private UUID metadataConflict()throws Exception {
        run(executor(),list(r1),list(h1));edit("local edit");
        return receive(changed(m1,"remote update",m1.updatedAt.plusSeconds(60)));
    }
    private void edit(String comment)throws Exception {
        LocalV2.State s=local.load().state(m1.uid);
        local.save(local.load().storeId,s,s.draft.edit(s.draft.displayId,s.draft.type,s.draft.status,s.draft.owner,s.draft.deployedAt,comment,
                s.draft.position,s.draft.bearingDeg,s.draft.imageRef,s.draft.updatedAt.plusSeconds(5)));
    }
    private Map<Path,byte[]> localFiles()throws IOException {Map<Path,byte[]> m=files(root.resolve("local-drafts-v1"));m.putAll(files(root.resolve("local-images-v1")));return m;}

    @Test public void keepMineChangesNothingLocallyThenShowsUnpublishedAndNoConflict()throws Exception {
        UUID r2=metadataConflict();FeedSyncApply apply=executor();run(apply,list(r2),list(h1));
        assertEquals(Collections.singleton(m1.uid),apply.conflicts());
        FeedSyncApply.Conflict c=apply.conflict(m1.uid);assertNotNull(c);assertEquals("local edit",c.local.comment);assertEquals("remote update",c.remote.comment);
        assertNotNull("Local image is shown",c.localImageRef);assertEquals(1,c.candidates.size());
        Map<Path,byte[]> before=localFiles();
        assertEquals(FeedSyncApply.Outcome.DONE,apply.resolve(c,FeedSyncApply.Resolution.KEEP_LOCAL,null));
        assertSameFiles(before,localFiles());
        FeedSyncState.Record rec=record();assertEquals(r2,rec.baseReceiptId);assertEquals(FeedSyncState.IMAGE_DIGEST,rec.imageKind);assertArrayEquals(h1.sha256(),rec.imageDigest());
        assertTrue(apply.conflicts().isEmpty());assertEquals(Collections.singleton(m1.uid),apply.unpublished());
        FeedSyncApply next=executor();run(next,list(r2),list(h1));
        assertTrue(next.conflicts().isEmpty());assertEquals(Collections.singleton(m1.uid),next.unpublished());assertEquals(0,next.applied());assertSameFiles(before,localFiles());
    }
    @Test public void useIncomingAppliesMetadataAndPhotoThenTheNextFetchIsQuiet()throws Exception {
        run(executor(),list(r1),list());   // device added without an image; base r1, image base none
        edit("local edit");LocalImages.Pending mine=images.prepareReceived(stage.read(h2.stageLocalId).image);
        new DraftAttachments(local,images).attach(local.load().select(m1.uid),mine,local.load().state(m1.uid).draft.updatedAt);
        UUID r2=receive(changed(m1,"remote update",m1.updatedAt.plusSeconds(60)));
        FeedSyncApply apply=executor();run(apply,list(r2),list(h1));
        assertEquals(Collections.singleton(m1.uid),apply.conflicts());
        FeedSyncApply.Conflict c=apply.conflict(m1.uid);assertEquals(1,c.candidates.size());assertArrayEquals(png1,apply.incomingImage(c.incoming()));
        assertEquals(FeedSyncApply.Outcome.DONE,apply.resolve(c,FeedSyncApply.Resolution.USE_INCOMING,null));
        TmmDraft draft=local.load().state(m1.uid).draft;assertEquals("remote update",draft.comment);assertArrayEquals(png1,images.read(draft.imageRef));
        assertEquals(r2,record().baseReceiptId);assertArrayEquals(h1.sha256(),record().imageDigest());assertNull(record().adoptNew);
        assertTrue(apply.conflicts().isEmpty());assertTrue(apply.unpublished().isEmpty());assertEquals("The replaced own photo is deleted",1,pngs());
        Map<Path,byte[]> before=files(root);FeedSyncApply next=executor();run(next,list(r2),list(h1));
        assertTrue(next.conflicts().isEmpty());assertTrue(next.unpublished().isEmpty());assertEquals(0,next.applied());assertSameFiles(before,files(root));
    }
    @Test public void aStaleViewIsRefusedAndChangesNothing()throws Exception {
        UUID r2=metadataConflict();FeedSyncApply apply=executor();run(apply,list(r2),list(h1));
        FeedSyncApply.Conflict c=apply.conflict(m1.uid);
        edit("edited again after the view was opened");
        Map<Path,byte[]> before=files(root);
        assertEquals(FeedSyncApply.Outcome.STALE,apply.resolve(c,FeedSyncApply.Resolution.USE_INCOMING,null));
        assertEquals(FeedSyncApply.Outcome.STALE,apply.resolve(c,FeedSyncApply.Resolution.KEEP_LOCAL,null));
        assertSameFiles(before,files(root));
        // A newer remote version arriving also makes the old view stale.
        FeedSyncApply.Conflict fresh=apply.conflict(m1.uid);UUID r3=receive(changed(m1,"newer remote",m1.updatedAt.plusSeconds(90)));
        run(apply,list(r3),list(h1));Map<Path,byte[]> mid=files(root);
        assertEquals(FeedSyncApply.Outcome.STALE,apply.resolve(fresh,FeedSyncApply.Resolution.KEEP_LOCAL,null));assertSameFiles(mid,files(root));
    }
    @Test public void aDuplicatePressIsIdempotent()throws Exception {
        UUID r2=metadataConflict();FeedSyncApply apply=executor();run(apply,list(r2),list(h1));
        FeedSyncApply.Conflict c=apply.conflict(m1.uid);
        assertEquals(FeedSyncApply.Outcome.DONE,apply.resolve(c,FeedSyncApply.Resolution.USE_INCOMING,null));Map<Path,byte[]> after=files(root);
        assertEquals(FeedSyncApply.Outcome.STALE,apply.resolve(c,FeedSyncApply.Resolution.USE_INCOMING,null));
        assertEquals(FeedSyncApply.Outcome.STALE,apply.resolve(c,FeedSyncApply.Resolution.KEEP_LOCAL,null));assertSameFiles(after,files(root));
    }
    @Test public void ambiguousPhotosAreListedAndOnlyAnExplicitPickIsApplied()throws Exception {
        run(executor(),list(r1),list());   // device without an image
        // Metadata changed remotely with both photos in the feed and neither bound to it: U4 AMBIGUOUS.
        UUID r2=receive(changed(m1,"status only",m1.updatedAt.plusSeconds(60)));
        FeedSyncApply apply=executor();run(apply,list(r2),list(h1,h2));
        assertEquals(Collections.singleton(m1.uid),apply.conflicts());
        FeedSyncApply.Conflict c=apply.conflict(m1.uid);assertTrue(c.ambiguous);assertEquals(2,c.candidates.size());
        assertFalse(Arrays.equals(c.candidates.get(0).sha256(),c.candidates.get(1).sha256()));assertNull(c.incoming());
        for(ImageAssociationRegistry.Record r:c.candidates)assertNotNull(apply.incomingImage(r));
        Map<Path,byte[]> before=files(root);
        assertEquals("No automatic pick",FeedSyncApply.Outcome.NOT_APPLIED,apply.resolve(c,FeedSyncApply.Resolution.USE_INCOMING,null));assertSameFiles(before,files(root));
        ImageAssociationRegistry.Record pick=Arrays.equals(h2.sha256(),c.candidates.get(0).sha256())?c.candidates.get(0):c.candidates.get(1);
        assertEquals(FeedSyncApply.Outcome.DONE,apply.resolve(c,FeedSyncApply.Resolution.USE_INCOMING,pick));
        assertArrayEquals(png2,images.read(local.load().state(m1.uid).draft.imageRef));assertArrayEquals(h2.sha256(),record().imageDigest());
        FeedSyncApply next=executor();run(next,list(r2),list(h1,h2));assertTrue(next.conflicts().isEmpty());assertTrue(next.unpublished().isEmpty());assertEquals(0,next.applied());
    }
    @Test public void keepMineWithAmbiguousPhotosMovesOnlyTheMetadataBase()throws Exception {
        run(executor(),list(r1),list());UUID r2=receive(changed(m1,"status only",m1.updatedAt.plusSeconds(60)));
        FeedSyncApply apply=executor();run(apply,list(r2),list(h1,h2));FeedSyncApply.Conflict c=apply.conflict(m1.uid);Map<Path,byte[]> before=localFiles();
        assertEquals(FeedSyncApply.Outcome.DONE,apply.resolve(c,FeedSyncApply.Resolution.KEEP_LOCAL,null));
        assertSameFiles(before,localFiles());assertEquals(FeedSyncState.IMAGE_NONE,record().imageKind);
        assertEquals("The ambiguity is not resolved by keeping",Collections.singleton(m1.uid),apply.conflicts());
    }
    @Test public void anIncomingEditOlderThanTheLocalOneIsRefusedWithoutChanges()throws Exception {
        run(executor(),list(r1),list(h1));edit("local edit");edit("local edit 2");
        UUID r2=receive(changed(m1,"remote update",m1.updatedAt.plusSeconds(1)));   // earlier than the local edit time
        FeedSyncApply apply=executor();run(apply,list(r2),list(h1));FeedSyncApply.Conflict c=apply.conflict(m1.uid);Map<Path,byte[]> before=files(root);
        assertEquals(FeedSyncApply.Outcome.NOT_APPLIED,apply.resolve(c,FeedSyncApply.Resolution.USE_INCOMING,null));assertSameFiles(before,files(root));
    }

    private FeedSyncApply executor(){return new FeedSyncApply(sync,inbox,stage,local,images);}
    private void run(FeedSyncApply apply,List<UUID> receipts,List<ImageAssociationRegistry.Record> records)throws Exception {apply.apply(sync.read(),receipts,records,()->true);}
    private ImageAssociationRegistry.Record intake(ReceivedImagePayload payload)throws Exception {
        return new ReceivedImageIntake(inbox,registry,stage,local).receive(CONTEXT,payload,Instant.EPOCH,()->true);
    }
    /** A synthetic container, optionally re-bound to other metadata with fresh association and delivery IDs. */
    private ReceivedImagePayload container(String name,TmmDraft metadata)throws Exception {
        ReceivedImagePayload p;try(InputStream in=getClass().getResourceAsStream(name)){p=ImageContainerCodec.read(in);}
        if(metadata==null)return p;
        ImageOffer offer=new ImageOffer(UUID.randomUUID(),UUID.randomUUID(),new ShareReview(metadata,"synthetic","synthetic").metadata,
                p.offer.sha256(),p.offer.pngLength,p.offer.width,p.offer.height);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageContainerCodec.write(out,offer,p.image);
        return ImageContainerCodec.read(new ByteArrayInputStream(out.toByteArray()));
    }
    private TmmDraft receipt(UUID id)throws IOException {for(InboxStore.Receipt r:inbox.load().receipts)if(r.id.equals(id))return r.incoming;throw new AssertionError();}
    private UUID receive(TmmDraft draft)throws IOException {return inbox.receiveBatch(CONTEXT,Collections.singletonList(draft),Collections.emptyMap(),Instant.EPOCH).get(0).id;}
    private static TmmDraft changed(TmmDraft d,String comment,Instant updated){
        return new TmmDraft(d.uid,d.displayId,d.type,d.status,d.owner,d.createdAt,updated,d.deployedAt,comment,d.position,d.bearingDeg,null,d.revision);
    }
    private static boolean same(TmmDraft a,TmmDraft b){return ImportReview.assess(Collections.singletonList(a),b).kind==ImportReview.Kind.SAME_METADATA;}
    private FeedSyncState.Record record()throws IOException {return find(sync.read());}
    private FeedSyncState.Record find(FeedSyncState.Snapshot s){for(FeedSyncState.Record r:s.records)if(r.deviceUid.equals(m1.uid))return r;throw new AssertionError("No record");}
    private static UUID uuid(String reference){return UUID.fromString(reference.substring(0,36));}
    @SafeVarargs private static <T>List<T> list(T... items){return Arrays.asList(items);}
    private long pngs()throws IOException {
        Path dir=root.resolve("local-images-v1");if(!Files.exists(dir))return 0;
        try(java.util.stream.Stream<Path> all=Files.list(dir)){return all.filter(p->p.toString().endsWith(".png")).count();}
    }
    private static Map<Path,byte[]> files(Path dir)throws IOException {
        Map<Path,byte[]> result=new TreeMap<>();if(!Files.exists(dir))return result;
        try(java.util.stream.Stream<Path> all=Files.walk(dir)){for(Path p:(Iterable<Path>)all::iterator)if(Files.isRegularFile(p))result.put(p,Files.readAllBytes(p));}
        return result;
    }
    private static void assertSameFiles(Map<Path,byte[]> before,Map<Path,byte[]> after){
        assertEquals(before.keySet(),after.keySet());for(Map.Entry<Path,byte[]> e:before.entrySet())assertArrayEquals(e.getKey().toString(),e.getValue(),after.get(e.getKey()));
    }
}
