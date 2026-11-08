package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.core.ImageAssociationRegistry;
import se.tmm.sensorplanner.core.ImageOffer;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26,35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageStageDiscardTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final UUID H1=UUID.fromString("00000000-0000-4000-8026-000000000301");
    private static final UUID H2=UUID.fromString("00000000-0000-4000-8026-000000000302");

    @Test public void oneReadyEntryIsDiscardedWithoutChangingOtherOrActiveImageCopies() throws Exception {
        Path root=fixture("ready-two"),active=root.getParent().resolve("active-control.png");
        byte[] original=resource("/image-transfer/h2.png");Files.write(active,original);
        ImageStageStore store=new ImageStageStore(root);
        ImageStageStore.DiscardPlan plan=store.prepareDiscard(H1);
        assertFalse(plan.pending);assertEquals(2,plan.fileCount);assertEquals(879,plan.logicalBytes);
        assertArrayEquals(payload(1).image.bytes(),plan.readyPayload().image.bytes());
        assertTrue(Files.exists(root.resolve("ready-"+H1)));
        ImageStageStore.Inspection after=store.discard(plan);
        assertEquals(Collections.singletonList(H2),after.ready);assertTrue(after.pending.isEmpty());assertEquals(897,after.logicalBytes);
        assertArrayEquals(original,Files.readAllBytes(active));assertArrayEquals(original,store.read(H2).image.bytes());
        assertCode(ImageStageStore.Code.ALREADY_ATTEMPTED,()->store.discard(plan));
        assertCode(ImageStageStore.Code.NOT_FOUND,()->store.prepareDiscard(H1));
        assertEquals(ImageStageStore.Outcome.ABSENT,store.reconcile(H1,payload(1)));
    }

    @Test public void publicDiscardRefusesReferencedEntriesAndAbsentRegistry() throws Exception {
        Path root=fixture("ready-two"),registryDir=root.resolveSibling("registry");
        ImageStageStore store=new ImageStageStore(root);
        ImageAssociationRegistry registry=new ImageAssociationRegistry(registryDir);
        ImageStageStore.DiscardPlan absent=store.prepareDiscard(H1);
        try{store.discard(absent,registry);fail();}catch(IOException expected){}
        Files.createDirectories(registryDir);Files.write(registryDir.resolve("associations.bin"),resource("/one.bin"));
        try{store.discard(store.prepareDiscard(H1),registry);fail();}catch(IOException expected){}
        assertArrayEquals(payload(1).image.bytes(),store.read(H1).image.bytes());
        assertEquals(Collections.singletonList(H1),store.discard(store.prepareDiscard(H2),registry).ready);
        store.requireReady(H1,payload(1).offer);
        assertCode(ImageStageStore.Code.CONFLICT,()->store.requireReady(H1,payload(2).offer));
        assertCode(ImageStageStore.Code.NOT_FOUND,()->store.requireReady(H2,payload(2).offer));
    }

    @Test public void pendingPartialCompleteAndEmptyRequireTheirOwnExactReview() throws Exception {
        for(String name:new String[]{"pending-partial","pending-complete","empty"}) {
            Path root=fixture(name);
            if(name.equals("empty"))Files.createDirectory(root.resolve("pending-"+H2));
            ImageStageStore store=new ImageStageStore(root);
            ImageStageStore.DiscardPlan plan=store.prepareDiscard(H2);
            assertTrue(plan.pending);assertEquals(name.equals("empty")?0:2,plan.fileCount);
            assertNull(plan.readyPayload());
            assertEquals(name.equals("empty")?0:name.equals("pending-partial")?110:889,plan.logicalBytes);
            assertTrue(store.inspect().recoveryRequired());
            ImageStageStore.Inspection after=store.discard(plan);assertFalse(after.recoveryRequired());
            assertFalse(Files.exists(root.resolve("pending-"+H2)));
            if(name.equals("pending-partial"))assertArrayEquals(payload(1).image.bytes(),store.read(H1).image.bytes());
            store.stage(ImageStageStore.prepare(payload(2)));
        }
    }

    @Test public void changesInBytesFilePresenceOrReadyStateRejectOldConsent() throws Exception {
        for(String mutation:new String[]{"bytes","presence","state","valid-ready","descriptor-only"}) {
            Path root=fixture(mutation.equals("bytes")||mutation.equals("presence")?"pending-partial":"ready-one");
            UUID id=mutation.equals("bytes")||mutation.equals("presence")?H2:H1;
            ImageStageStore store=new ImageStageStore(root);ImageStageStore.DiscardPlan plan=store.prepareDiscard(id);
            Path directory=root.resolve((plan.pending?"pending-":"ready-")+id);
            switch(mutation) {
                case "bytes": Files.write(directory.resolve("image.png"),new byte[]{1});break;
                case "presence": Files.delete(directory.resolve("offer.bin"));break;
                case "state": Files.move(directory,root.resolve("pending-"+id),StandardCopyOption.ATOMIC_MOVE);break;
                case "valid-ready": Files.write(directory.resolve("offer.bin"),resource("/offer-h2.bin"));Files.write(directory.resolve("image.png"),resource("/image-transfer/h2.png"));break;
                case "descriptor-only":
                    ImageOffer old=payload(1).offer;
                    ImageOffer changed=new ImageOffer(UUID.randomUUID(),old.deliveryId,old.metadata,old.sha256(),old.pngLength,old.width,old.height);
                    Files.write(directory.resolve("offer.bin"),ImageOfferCodec.encode(changed));break;
                default:throw new AssertionError();
            }
            assertCode(ImageStageStore.Code.CONFLICT,()->store.discard(plan));
            if(!plan.pending) assertArrayEquals(payload(1).image.bytes(),plan.readyPayload().image.bytes());
            assertTrue(Files.exists(root.resolve((mutation.equals("state")?"pending-":plan.pending?"pending-":"ready-")+id)));
            assertCode(ImageStageStore.Code.ALREADY_ATTEMPTED,()->store.discard(plan));
        }
    }

    @Test public void aPlanCannotDeleteAnIdenticalEntryInAnotherRoot() throws Exception {
        Path a=fixture("ready-one"),b=fixture("ready-one");ImageStageStore first=new ImageStageStore(a),second=new ImageStageStore(b);
        ImageStageStore.DiscardPlan plan=first.prepareDiscard(H1);
        assertCode(ImageStageStore.Code.CONFLICT,()->second.discard(plan));
        assertEquals(Collections.singletonList(H1),first.inspect().ready);assertEquals(Collections.singletonList(H1),second.inspect().ready);
        assertCode(ImageStageStore.Code.ALREADY_ATTEMPTED,()->first.discard(plan));
    }

    @Test public void failuresAfterEveryUnlinkMilestoneRetainHonestReopenState() throws Exception {
        for(ImageStageStore.Point point:EnumSet.range(ImageStageStore.Point.DISCARD_MARKED_PENDING,ImageStageStore.Point.DISCARD_DIRECTORY_REMOVED)) {
            Path root=fixture("ready-two");ImageStageStore store=new ImageStageStore(root,p->{if(p==point)throw new IOException("SYNTHETIC-PRIVATE-DISCARD");});
            ImageStageStore.DiscardPlan plan=store.prepareDiscard(H1);
            IOException failure=assertThrows(IOException.class,()->store.discard(plan));assertFalse(failure.toString().contains("PRIVATE-DISCARD"));assertNull(failure.getCause());
            ImageStageStore reopened=new ImageStageStore(root);ImageStageStore.Inspection state=reopened.inspect();
            assertEquals(Collections.singletonList(H2),state.ready);assertArrayEquals(payload(2).image.bytes(),reopened.read(H2).image.bytes());
            boolean absent=point==ImageStageStore.Point.DISCARD_DIRECTORY_REMOVED;
            assertEquals(absent?Collections.emptyList():Collections.singletonList(H1),state.pending);
            assertCode(ImageStageStore.Code.ALREADY_ATTEMPTED,()->reopened.discard(plan));
            if(!absent) {
                assertTrue(state.recoveryRequired());
                int expectedFiles=point==ImageStageStore.Point.DISCARD_MARKED_PENDING?2:point==ImageStageStore.Point.DISCARD_OFFER_REMOVED?1:0;
                ImageStageStore.DiscardPlan fresh=reopened.prepareDiscard(H1);assertEquals(expectedFiles,fresh.fileCount);
                assertFalse(reopened.discard(fresh).recoveryRequired());
            } else assertCode(ImageStageStore.Code.NOT_FOUND,()->reopened.prepareDiscard(H1));
        }
    }

    @Test public void invalidOrForeignStateNeverEnablesDestructiveFallback() throws Exception {
        for(String mutation:new String[]{"marker","foreign","corrupt-ready","missing-lock"}) {
            Path root=fixture("ready-one");ImageStageStore store=new ImageStageStore(root);ImageStageStore.DiscardPlan plan=store.prepareDiscard(H1);
            switch(mutation) {
                case "marker":Files.write(root.resolve("format"),"TMMISTG2".getBytes("US-ASCII"));break;
                case "foreign":Files.createFile(root.resolve("ready-"+H1).resolve("unknown"));break;
                case "corrupt-ready":Files.write(root.resolve("ready-"+H1).resolve("image.png"),new byte[]{0});break;
                case "missing-lock":Files.delete(root.resolve("stage.lock"));break;
                default:throw new AssertionError();
            }
            assertThrows(IOException.class,()->store.prepareDiscard(H1));assertThrows(IOException.class,()->store.discard(plan));
            assertTrue(Files.exists(root.resolve("ready-"+H1).resolve("offer.bin")));
            assertTrue(Files.exists(root.resolve("ready-"+H1).resolve("image.png")));
        }
    }

    @Test public void concurrentConfirmationNeverDeletesAnotherEntryOrSilentlyRetries() throws Exception {
        Path root=fixture("ready-two");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ImageStageStore first=new ImageStageStore(root,point->{
            if(point==ImageStageStore.Point.DISCARD_MARKED_PENDING) {
                entered.countDown();try {if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Timed out");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Interrupted");}
            }
        });
        ImageStageStore second=new ImageStageStore(root);
        ImageStageStore.DiscardPlan a=first.prepareDiscard(H1),b=second.prepareDiscard(H1);
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<ImageStageStore.Inspection> result=executor.submit(()->first.discard(a));assertTrue(entered.await(10,TimeUnit.SECONDS));
            assertCode(ImageStageStore.Code.BUSY,()->second.discard(b));release.countDown();assertEquals(Collections.singletonList(H2),result.get(10,TimeUnit.SECONDS).ready);
            assertCode(ImageStageStore.Code.ALREADY_ATTEMPTED,()->second.discard(b));
            assertArrayEquals(payload(2).image.bytes(),second.read(H2).image.bytes());
        } finally {release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }

    private Path fixture(String name) throws Exception {
        Path source=Paths.get(getClass().getResource("/"+name+"/format").toURI()).getParent(),target=temp.newFolder().toPath().resolve("stage");
        try(Stream<Path> paths=Files.walk(source)) {
            for(Path path:(Iterable<Path>)paths::iterator) {
                Path copy=target.resolve(source.relativize(path).toString());
                if(Files.isDirectory(path))Files.createDirectory(copy);else Files.copy(path,copy);
            }
        }
        return target;
    }
    private interface Action {void run() throws Exception;}
    private static void assertCode(ImageStageStore.Code code,Action action) {
        ImageStageStore.Failure failure=assertThrows(ImageStageStore.Failure.class,action::run);assertEquals(code,failure.code);
    }
    private static byte[] resource(String name) throws IOException {
        try(InputStream input=ImageStageDiscardTest.class.getResourceAsStream(name)) {
            assertNotNull(input);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
            for(int n;(n=input.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();
        }
    }
    private static ReceivedImagePayload payload(int n)throws IOException {
        return ReceivedImagePayload.read(new ByteArrayInputStream(resource("/offer-h"+n+".bin")),new ByteArrayInputStream(resource("/image-transfer/h"+n+".png")));
    }
}
