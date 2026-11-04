package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
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
import se.tmm.sensorplanner.core.ImageOffer;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import se.tmm.sensorplanner.images.ImagePreparationException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageStageStoreTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final UUID H1 = UUID.fromString("00000000-0000-4000-8026-000000000301");
    private static final UUID H2 = UUID.fromString("00000000-0000-4000-8026-000000000302");

    @Test public void independentLayoutsReadWithoutMutationOrImplicitPromotion() throws Exception {
        String[] cases = {"empty", "ready-one", "ready-two", "pending-partial", "pending-complete"};
        int[][] counts = {{0,0,8}, {1,0,887}, {2,0,1776}, {1,1,997}, {0,1,897}};
        for (int i=0; i<cases.length; i++) {
            Path root = fixture(cases[i]); Map<String,String> before = fingerprint(root);
            ImageStageStore store = new ImageStageStore(root);
            ImageStageStore.Inspection state = store.inspect();
            assertEquals(counts[i][0], state.ready.size()); assertEquals(counts[i][1], state.pending.size());
            assertEquals(counts[i][2], state.logicalBytes); assertEquals(counts[i][1]>0, state.recoveryRequired());
            assertThrows(UnsupportedOperationException.class, () -> state.ready.clear());
            if (state.ready.contains(H1)) assertArrayEquals(resource("/image-transfer/h1.png"), store.read(H1).image.bytes());
            if (state.ready.contains(H2)) assertArrayEquals(resource("/image-transfer/h2.png"), store.read(H2).image.bytes());
            assertEquals(before, fingerprint(root));
        }
    }

    @Test public void explicitInitializationAndRestartPreserveExactPairsAndDistinctOffers() throws Exception {
        Path root = absent(); ImageStageStore store = new ImageStageStore(root);
        assertThrows(IOException.class, store::inspect); assertFalse(Files.exists(root));
        assertEquals(8, store.initializeNew().logicalBytes);
        assertArrayEquals("TMMISTG1".getBytes("US-ASCII"), Files.readAllBytes(root.resolve("format")));
        ImageStageStore.Prepared first = ImageStageStore.prepare(payload(1)), second = ImageStageStore.prepare(payload(2));
        assertNotEquals(first.id, second.id); assertNotEquals(first.id, payload(1).offer.associationId);
        assertEquals(first.id, store.stage(first)); assertEquals(second.id, store.stage(second));
        store = new ImageStageStore(root);
        assertEquals(2, store.inspect().ready.size()); assertFalse(store.inspect().recoveryRequired());
        assertEquals(ImageStageStore.Outcome.READY, store.reconcile(first.id, payload(1)));
        assertEquals(ImageStageStore.Outcome.READY, store.reconcile(second));
        assertArrayEquals(resource("/offer-h1.bin"), Files.readAllBytes(root.resolve("ready-"+first.id).resolve("offer.bin")));
        assertArrayEquals(resource("/image-transfer/h1.png"), store.read(first.id).image.bytes());
        ImageStageStore restarted = store;
        assertCode(ImageStageStore.Code.ALREADY_ATTEMPTED, () -> restarted.stage(first));
        assertCode(ImageStageStore.Code.CONFLICT, () -> restarted.reconcile(first.id, payload(2)));
        Map<String,String> before = fingerprint(root);
        assertThrows(IOException.class, restarted::initializeNew); assertEquals(before, fingerprint(root));
        UUID unknown = UUID.randomUUID();
        assertCode(ImageStageStore.Code.NOT_FOUND, () -> restarted.read(unknown));
        assertEquals(ImageStageStore.Outcome.ABSENT, restarted.reconcile(unknown, payload(1)));
    }

    @Test public void identicalIncomingPairsRemainIndependentLocalUnassociatedCandidates() throws Exception {
        Path root = absent(); ImageStageStore store = new ImageStageStore(root); store.initializeNew();
        ImageStageStore.Prepared a = ImageStageStore.prepare(payload(1)), b = ImageStageStore.prepare(payload(1));
        store.stage(a); store.stage(b);
        assertEquals(2, store.inspect().ready.size()); assertNotEquals(a.id, b.id);
        assertArrayEquals(store.read(a.id).image.bytes(), store.read(b.id).image.bytes());
    }

    @Test public void faultsRetainPendingOrReadyAndNeverPermitBlindRetry() throws Exception {
        for (ImageStageStore.Point point : ImageStageStore.Point.values()) {
            if (point == ImageStageStore.Point.ROOT_CREATED || point == ImageStageStore.Point.MARKER_SYNCED) continue;
            Path root = absent(); new ImageStageStore(root).initializeNew();
            ImageStageStore.Prepared operation = ImageStageStore.prepare(payload(1));
            ImageStageStore failing = new ImageStageStore(root, p -> {
                if (p == point) throw new IOException("SYNTHETIC-PRIVATE-PATH");
            });
            IOException error = assertThrows(IOException.class, () -> failing.stage(operation));
            assertFalse(error.toString().contains("PRIVATE-PATH")); assertNull(error.getCause());
            ImageStageStore restarted = new ImageStageStore(root);
            boolean renamed = point == ImageStageStore.Point.RENAMED || point == ImageStageStore.Point.READY_VERIFIED;
            assertEquals(renamed ? ImageStageStore.Outcome.READY : ImageStageStore.Outcome.PENDING,
                    restarted.reconcile(operation.id, payload(1)));
            Map<String,String> before = fingerprint(root);
            assertCode(ImageStageStore.Code.ALREADY_ATTEMPTED, () -> failing.stage(operation));
            assertEquals(before, fingerprint(root));
            if (!renamed) {
                assertCode(ImageStageStore.Code.RECOVERY_REQUIRED, () -> restarted.stage(ImageStageStore.prepare(payload(2))));
                assertEquals(before, fingerprint(root));
                assertCode(ImageStageStore.Code.NOT_FOUND, () -> restarted.read(operation.id));
            } else assertArrayEquals(payload(1).image.bytes(), restarted.read(operation.id).image.bytes());
        }
    }

    @Test public void failedInitializationIsNeverReinitializedOrSilentlyEmptied() throws Exception {
        for (ImageStageStore.Point point : new ImageStageStore.Point[]{ImageStageStore.Point.ROOT_CREATED, ImageStageStore.Point.MARKER_SYNCED}) {
            Path root = absent(); ImageStageStore store = new ImageStageStore(root, p -> {
                if (p == point) throw new IOException("SYNTHETIC-PRIVATE-INIT");
            });
            assertThrows(IOException.class, store::initializeNew); assertTrue(Files.isDirectory(root));
            Map<String,String> before = fingerprint(root);
            assertThrows(IOException.class, () -> new ImageStageStore(root).initializeNew());
            assertEquals(before, fingerprint(root));
            if (point == ImageStageStore.Point.ROOT_CREATED) assertThrows(IOException.class, () -> new ImageStageStore(root).inspect());
            else assertEquals(8, new ImageStageStore(root).inspect().logicalBytes);
        }
    }

    @Test public void partialBytesAndCompletePendingAreRetainedAndBlockAdmission() throws Exception {
        for (String name : new String[]{"pending-partial", "pending-complete"}) {
            Path root = fixture(name); ImageStageStore store = new ImageStageStore(root);
            Map<String,String> before = fingerprint(root);
            assertCode(ImageStageStore.Code.RECOVERY_REQUIRED, () -> store.stage(ImageStageStore.prepare(payload(1))));
            assertEquals(ImageStageStore.Outcome.PENDING, store.reconcile(H2, payload(2)));
            assertCode(ImageStageStore.Code.NOT_FOUND, () -> store.read(H2));
            assertEquals(before, fingerprint(root));
        }
    }

    @Test public void corruptForeignMissingUnsupportedAndLinkedStorageFailClosed() throws Exception {
        for (String mutation : new String[]{"marker", "version", "lock", "foreign", "nested", "offer", "png", "missing", "duplicate", "uppercase", "nil", "file-link", "dir-link", "root-link", "lock-link", "oversize"}) {
            Path root = fixture("ready-one"), record = root.resolve("ready-"+H1);
            switch (mutation) {
                case "marker": Files.delete(root.resolve("format")); break;
                case "version": Files.write(root.resolve("format"), "TMMISTG2".getBytes("US-ASCII")); break;
                case "lock": Files.write(root.resolve("stage.lock"), new byte[]{1}); break;
                case "foreign": Files.createFile(root.resolve("unexpected")); break;
                case "nested": Files.createDirectory(record.resolve("nested")); break;
                case "offer": Files.write(record.resolve("offer.bin"), new byte[]{1}); break;
                case "png": Files.write(record.resolve("image.png"), resource("/image-transfer/h2.png")); break;
                case "missing": Files.delete(record.resolve("image.png")); break;
                case "duplicate": Files.createDirectory(root.resolve("pending-"+H1)); break;
                case "uppercase": Files.move(record, root.resolve("ready-00000000-0000-4000-8026-00000000030A")); break;
                case "nil": Files.move(record, root.resolve("ready-00000000-0000-0000-0000-000000000000")); break;
                case "file-link": Files.delete(record.resolve("image.png")); Files.createSymbolicLink(record.resolve("image.png"), root.resolve("format")); break;
                case "dir-link": Files.delete(record.resolve("offer.bin")); Files.delete(record.resolve("image.png")); Files.delete(record); Files.createSymbolicLink(record, root); break;
                case "root-link": Path link=absent(); Files.createSymbolicLink(link,root); root=link; break;
                case "lock-link": Files.delete(root.resolve("stage.lock")); Files.createSymbolicLink(root.resolve("stage.lock"),root.resolve("format")); break;
                case "oversize": try (RandomAccessFile file=new RandomAccessFile(record.resolve("image.png").toFile(),"rw")) { file.setLength(16777217); } break;
                default: throw new AssertionError();
            }
            ImageStageStore store = new ImageStageStore(root);
            assertThrows(mutation, IOException.class, store::inspect);
            assertThrows(mutation, IOException.class, () -> store.stage(ImageStageStore.prepare(payload(1))));
            assertTrue(Files.exists(root));
        }
    }

    @Test public void canonicalOfferIsRequiredEvenWhenAlternateXmlDecodes() throws Exception {
        Path root = fixture("ready-one"), file = root.resolve("ready-"+H1).resolve("offer.bin");
        byte[] bytes = Files.readAllBytes(file);
        byte[] variant = alternateXml(bytes);
        assertNotNull(ImageOfferCodec.decode(new ByteArrayInputStream(variant)));
        Files.write(file, variant);
        assertThrows(IOException.class, () -> new ImageStageStore(root).inspect());
    }

    @Test public void exactQuotaCountsPartialLogicalBytesAndRejectsExtraWithoutDeleting() throws Exception {
        Path root = fixture("empty");
        Path last = null;
        for (int i=0; i<16; i++) {
            Path record = Files.createDirectory(root.resolve("pending-"+UUID.randomUUID()));
            last = record.resolve("image.png");
            try (RandomAccessFile file=new RandomAccessFile(last.toFile(),"rw")) { file.setLength(16777216-(i==15?8:0)); }
        }
        ImageStageStore store = new ImageStageStore(root);
        assertEquals(ImageStageStore.MAX_BYTES, store.inspect().logicalBytes);
        try (RandomAccessFile file=new RandomAccessFile(last.toFile(),"rw")) { file.setLength(16777216-7); }
        assertCode(ImageStageStore.Code.CAPACITY, store::inspect);
        assertTrue(Files.exists(last));
    }

    @Test public void entryLimitRejectsAdmissionAndNeverEvictsExistingPairs() throws Exception {
        Path root = absent(); ImageStageStore store = new ImageStageStore(root); store.initializeNew();
        for (int i=0; i<16; i++) store.stage(ImageStageStore.prepare(payload(i%2+1)));
        Map<String,String> before = fingerprint(root);
        assertCode(ImageStageStore.Code.CAPACITY, () -> store.stage(ImageStageStore.prepare(payload(1))));
        assertEquals(before, fingerprint(root)); assertEquals(16, store.inspect().ready.size());
        Files.createDirectory(root.resolve("pending-"+UUID.randomUUID()));
        assertCode(ImageStageStore.Code.CAPACITY, store::inspect);
    }

    @Test public void separateInstancesAndReadersRespectOneExclusiveLock() throws Exception {
        Path root = absent(); new ImageStageStore(root).initializeNew();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ImageStageStore first = new ImageStageStore(root, point -> {
            if (point == ImageStageStore.Point.PENDING_CREATED) {
                entered.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new IOException("Test timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted"); }
            }
        });
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            ImageStageStore.Prepared operation=ImageStageStore.prepare(payload(1));
            Future<UUID> result=executor.submit(()->first.stage(operation));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            ImageStageStore second=new ImageStageStore(root);
            assertCode(ImageStageStore.Code.BUSY, second::inspect);
            assertCode(ImageStageStore.Code.BUSY, () -> second.stage(ImageStageStore.prepare(payload(2))));
            release.countDown(); assertEquals(operation.id,result.get(10,TimeUnit.SECONDS));
            second.stage(ImageStageStore.prepare(payload(2))); assertEquals(2,second.inspect().ready.size());
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS)); }
    }

    @Test public void operatingSystemLockAlsoExcludesIndependentProcess() throws Exception {
        Path root=absent(); ImageStageStore store=new ImageStageStore(root); store.initializeNew();
        String classResource=StageLockPeer.class.getName().replace('.','/')+".class";
        Path classFile=Paths.get(StageLockPeer.class.getClassLoader().getResource(classResource).toURI());
        for(String ignored:classResource.split("/")) classFile=classFile.getParent();
        String classes=classFile.toString();
        Process process=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java").toString(),
                "-cp",classes,StageLockPeer.class.getName(),root.resolve("stage.lock").toString()).redirectErrorStream(true).start();
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            BufferedReader output=new BufferedReader(new InputStreamReader(process.getInputStream()));
            assertEquals("LOCKED",executor.submit(output::readLine).get(10,TimeUnit.SECONDS));
            assertCode(ImageStageStore.Code.BUSY,store::inspect);
            process.getOutputStream().write(1); process.getOutputStream().flush();
            assertTrue(process.waitFor(10,TimeUnit.SECONDS)); assertEquals(0,process.exitValue());
            assertEquals(8,store.inspect().logicalBytes);
        } finally { process.destroyForcibly(); process.waitFor(10,TimeUnit.SECONDS); executor.shutdownNow(); }
    }

    @Test public void writeReadbackAndSyntheticMemoryFailuresPreserveCopies() throws Exception {
        for (boolean corrupt : new boolean[]{true,false}) {
            Path root=absent(); new ImageStageStore(root).initializeNew();
            ImageStageStore.Prepared operation=ImageStageStore.prepare(payload(1));
            ImageStageStore store=new ImageStageStore(root,point->{
                if(point==ImageStageStore.Point.PNG_SYNCED) {
                    if(corrupt) Files.write(root.resolve("pending-"+operation.id).resolve("image.png"),new byte[]{0});
                    else throw new OutOfMemoryError("SYNTHETIC-PRIVATE-HEAP");
                }
            });
            IOException failure=assertThrows(IOException.class,()->store.stage(operation));
            if(!corrupt) assertEquals(ImagePreparationException.Reason.LOW_MEMORY,((ImagePreparationException)failure).reason);
            assertTrue(new ImageStageStore(root).inspect().recoveryRequired());
            assertEquals(ImageStageStore.Outcome.PENDING,new ImageStageStore(root).reconcile(operation));
        }
    }

    @Test public void corruptedPublishedReadbackIsNeverReportedAsKnownReady() throws Exception {
        Path root=absent(); new ImageStageStore(root).initializeNew();
        ImageStageStore.Prepared operation=ImageStageStore.prepare(payload(1));
        ImageStageStore store=new ImageStageStore(root,point->{
            if(point==ImageStageStore.Point.RENAMED) Files.write(
                    root.resolve("ready-"+operation.id).resolve("image.png"),new byte[]{0});
        });
        assertThrows(IOException.class,()->store.stage(operation));
        assertThrows(IOException.class,()->new ImageStageStore(root).reconcile(operation));
        assertTrue(Files.exists(root.resolve("ready-"+operation.id).resolve("offer.bin")));
        assertTrue(Files.exists(root.resolve("ready-"+operation.id).resolve("image.png")));
    }

    @Test public void readbackMatchesEntireDescriptorRatherThanOnlyImageOrDevice() throws Exception {
        Path root=absent(); ImageStageStore store=new ImageStageStore(root);store.initializeNew();
        ReceivedImagePayload original=payload(1);
        ImageStageStore.Prepared operation=ImageStageStore.prepare(original);store.stage(operation);
        ImageOffer changed=new ImageOffer(UUID.randomUUID(),original.offer.deliveryId,original.offer.metadata,
                original.offer.sha256(),original.offer.pngLength,original.offer.width,original.offer.height);
        ReceivedImagePayload sameContent=ReceivedImagePayload.read(new ByteArrayInputStream(ImageOfferCodec.encode(changed)),
                new ByteArrayInputStream(original.image.bytes()));
        assertCode(ImageStageStore.Code.CONFLICT,()->store.reconcile(operation.id,sameContent));
        assertEquals(ImageStageStore.Outcome.READY,store.reconcile(operation));
    }

    private Path absent() throws IOException { Path root=temp.newFolder().toPath().resolve("stage"); return root; }
    private Path fixture(String name) throws Exception {
        Path source=Paths.get(getClass().getResource("/"+name+"/format").toURI()).getParent(), target=absent();
        try(Stream<Path> files=Files.walk(source)) {
            for(Path p:(Iterable<Path>)files::iterator) {
                Path copy=target.resolve(source.relativize(p).toString());
                if(Files.isDirectory(p)) Files.createDirectory(copy); else Files.copy(p,copy);
            }
        }
        return target;
    }
    private static byte[] resource(String name) throws IOException {
        try(InputStream input=ImageStageStoreTest.class.getResourceAsStream(name)) {
            assertNotNull(name,input); ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[4096];
            for(int n;(n=input.read(buffer))!=-1;) bytes.write(buffer,0,n); return bytes.toByteArray();
        }
    }
    private static ReceivedImagePayload payload(int number) throws IOException {
        return ReceivedImagePayload.read(new ByteArrayInputStream(resource("/offer-h"+number+".bin")),
                new ByteArrayInputStream(resource("/image-transfer/h"+number+".png")));
    }
    private interface Action { void run() throws Exception; }
    private static void assertCode(ImageStageStore.Code code,Action action) {
        ImageStageStore.Failure failure=assertThrows(ImageStageStore.Failure.class,action::run); assertEquals(code,failure.code);
        assertNull(failure.getCause());
    }
    private static Map<String,String> fingerprint(Path root) throws Exception {
        Map<String,String> result=new TreeMap<>();
        try(Stream<Path> paths=Files.walk(root)) {
            for(Path path:(Iterable<Path>)paths::iterator) if(Files.isRegularFile(path)) {
                byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
                result.put(root.relativize(path).toString(),Base64.getEncoder().encodeToString(digest));
            }
        }
        return result;
    }
    private static byte[] alternateXml(byte[] source) throws Exception {
        int n=java.nio.ByteBuffer.wrap(source).getInt(104);
        byte[] xml=Arrays.copyOfRange(source,108,108+n);
        byte[] variant=new String(xml,"UTF-8").replace(" />\n", "  />\n").getBytes("UTF-8");
        byte[] out=new byte[112+variant.length]; System.arraycopy(source,0,out,0,108);
        java.nio.ByteBuffer.wrap(out).putInt(104,variant.length); System.arraycopy(variant,0,out,108,variant.length);
        java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(out,0,out.length-4);
        java.nio.ByteBuffer.wrap(out).putInt(out.length-4,(int)crc.getValue());return out;
    }
}
