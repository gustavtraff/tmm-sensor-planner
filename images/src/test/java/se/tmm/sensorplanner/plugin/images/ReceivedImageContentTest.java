package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import se.tmm.sensorplanner.core.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35}, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceivedImageContentTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String CONTEXT="synthetic-received-image-source";
    private static final Instant NOW=Instant.ofEpochSecond(1794444000);

    @Test public void actualStoredAssociationReceiptAndStageOpenWithoutChangingAnyBytes() throws Exception {
        Fixture f=fixture(); Map<Path,byte[]> before=bytes(f.root);
        ReceivedImagePayload result=ReceivedImageContent.open(f.review,f.item,f.registry,f.inbox,f.stage);
        assertArrayEquals(resource("/image-transfer/h1.png"),result.image.bytes());
        assertTrue(f.item.matchesOffer(result.offer));assertSame(f.item,f.review.images.get(0));
        assertFiles(before,bytes(f.root));
        result.image.bytes()[0]=0;
        assertArrayEquals(resource("/image-transfer/h1.png"),
                ReceivedImageContent.open(f.review,f.item,f.registry,f.inbox,f.stage).image.bytes());
    }

    @Test public void differentRegistryOrInboxNamespaceAndForeignSelectionRefuse() throws Exception {
        Fixture f=fixture(); ImageAssociationRegistry other=new ImageAssociationRegistry(f.root.resolve("other-registry"));
        other.initializeNew(f.inbox.load().inboxId);
        other.associate(f.inbox,CONTEXT,f.payload.offer,f.id,NOW,f.stage::requireReady);
        assertThrows(IOException.class,()->ReceivedImageContent.open(f.review,f.item,other,f.inbox,f.stage));
        InboxStore otherInbox=new InboxStore(f.root.resolve("other-inbox"));otherInbox.initializeNew();
        assertThrows(IOException.class,()->ReceivedImageContent.open(f.review,f.item,f.registry,otherInbox,f.stage));
        Fixture different=fixture();
        assertThrows(IOException.class,()->ReceivedImageContent.open(f.review,different.item,f.registry,f.inbox,f.stage));
        assertThrows(IOException.class,()->ReceivedImageContent.open(f.review,null,f.registry,f.inbox,f.stage));
    }

    @Test public void missingReceiptKeepsHistoryAndRefusesContentOpening() throws Exception {
        Fixture f=fixture();byte[] registry=Files.readAllBytes(f.root.resolve("registry/associations.bin"));
        Files.write(f.root.resolve("inbox/inbox.bin"),f.emptyInbox);
        assertThrows(IOException.class,()->ReceivedImageContent.open(f.review,f.item,f.registry,f.inbox,f.stage));
        assertArrayEquals(registry,Files.readAllBytes(f.root.resolve("registry/associations.bin")));
        assertArrayEquals(resource("/image-transfer/h1.png"),f.stage.read(f.id).image.bytes());
    }

    @Test public void missingOrCorruptContentIsNotRecreatedOrDetached() throws Exception {
        for(boolean missing:new boolean[]{true,false}) {
            Fixture f=fixture();Path png=f.root.resolve("stage/ready-"+f.id+"/image.png");
            if(missing)Files.delete(png);else Files.write(png,new byte[]{1,2,3});
            byte[] registry=Files.readAllBytes(f.root.resolve("registry/associations.bin"));
            assertThrows(IOException.class,()->ReceivedImageContent.open(f.review,f.item,f.registry,f.inbox,f.stage));
            assertArrayEquals(registry,Files.readAllBytes(f.root.resolve("registry/associations.bin")));
            if(missing)assertFalse(Files.exists(png));else assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(png));
        }
    }

    @Test public void coherentButSubstitutedOfferCannotReuseAnOldAssociation() throws Exception {
        Fixture f=fixture();ShareReview.Metadata m=f.payload.offer.metadata;
        TmmDraft changed=new TmmDraft(m.uid,m.displayId,m.type,m.status,m.owner,m.createdAt,m.updatedAt.plusNanos(1),
                m.deployedAt,m.comment,m.position,m.bearingDeg,null,1);
        ShareReview.Metadata metadata=new PublicationPreparationReview(changed).prepare(changed).metadata;
        ImageOffer offer=new ImageOffer(f.payload.offer.associationId,f.payload.offer.deliveryId,metadata,
                f.payload.offer.sha256(),f.payload.offer.pngLength,f.payload.offer.width,f.payload.offer.height);
        Files.write(f.root.resolve("stage/ready-"+f.id+"/offer.bin"),ImageOfferCodec.encode(offer));
        assertTrue(f.stage.read(f.id).offer.sameBinding(offer));
        assertThrows(IOException.class,()->ReceivedImageContent.open(f.review,f.item,f.registry,f.inbox,f.stage));
    }

    @Test public void currentMetadataChangesOrDeletionDoNotRebindTheHistoricalPicture() throws Exception {
        Fixture f=fixture();TmmDraft d=carrier(f.payload.offer.metadata);
        for(LocalV2.State state:new LocalV2.State[]{LocalV2.State.deleted(d.uid,7),LocalV2.State.active(8,
                d.edit(d.displayId,d.type,DeviceStatus.ACTIVE,d.owner,d.deployedAt,d.comment,d.position,d.bearingDeg,null,d.updatedAt.plusSeconds(1)))}) {
            LocalV2.Snapshot local=new LocalV2.Snapshot(UUID.randomUUID(),Collections.singletonList(state),Collections.emptyList());
            ReceivedImageReview review=ReceivedImageReview.assess(f.registry.load(),f.inbox.load(),local,d.uid);
            assertFalse(review.images.get(0).sameCurrentMetadata);
            ReceivedImagePayload actual=ReceivedImageContent.open(review,review.images.get(0),f.registry,f.inbox,f.stage);
            assertTrue(actual.offer.sameBinding(f.payload.offer));
            assertArrayEquals(resource("/image-transfer/h1.png"),actual.image.bytes());
        }
    }

    private Fixture fixture() throws Exception {
        Path root=temp.newFolder().toPath();InboxStore inbox=new InboxStore(root.resolve("inbox"));inbox.initializeNew();
        byte[] empty=Files.readAllBytes(root.resolve("inbox/inbox.bin"));
        ReceivedImagePayload payload=ImageContainerCodec.read(new ByteArrayInputStream(resource("/h1.tmmi")));
        inbox.receive(CONTEXT,carrier(payload.offer.metadata),null,NOW);
        ImageStageStore stage=new ImageStageStore(root.resolve("stage"));stage.initializeNew();
        UUID id=stage.stage(ImageStageStore.prepare(payload));
        ImageAssociationRegistry registry=new ImageAssociationRegistry(root.resolve("registry"));registry.initializeNew(inbox.load().inboxId);
        registry.associate(inbox,CONTEXT,payload.offer,id,NOW,stage::requireReady);
        LocalV2.Snapshot local=new LocalV2.Snapshot(UUID.randomUUID(),Collections.emptyList(),Collections.emptyList());
        ReceivedImageReview review=ReceivedImageReview.assess(registry.load(),inbox.load(),local,payload.offer.metadata.uid);
        return new Fixture(root,inbox,registry,stage,id,payload,review,empty);
    }
    private static TmmDraft carrier(ShareReview.Metadata m) {
        return new TmmDraft(m.uid,m.displayId,m.type,m.status,m.owner,m.createdAt,m.updatedAt,m.deployedAt,m.comment,m.position,m.bearingDeg,null,1);
    }
    private static byte[] resource(String name) throws IOException {
        try(InputStream input=ReceivedImageContentTest.class.getResourceAsStream(name)) {
            assertNotNull(name,input);ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] b=new byte[4096];
            for(int n;(n=input.read(b))!=-1;)output.write(b,0,n);return output.toByteArray();
        }
    }
    private static Map<Path,byte[]> bytes(Path root) throws IOException {
        Map<Path,byte[]> result=new HashMap<>();try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
            for(Path p:(Iterable<Path>)paths.filter(Files::isRegularFile)::iterator)result.put(root.relativize(p),Files.readAllBytes(p));
        }return result;
    }
    private static void assertFiles(Map<Path,byte[]> a,Map<Path,byte[]> b) {
        assertEquals(a.keySet(),b.keySet());for(Path path:a.keySet())assertArrayEquals(path.toString(),a.get(path),b.get(path));
    }
    private static final class Fixture {
        final Path root;final InboxStore inbox;final ImageAssociationRegistry registry;final ImageStageStore stage;
        final UUID id;final ReceivedImagePayload payload;final ReceivedImageReview review;final ReceivedImageReview.Item item;final byte[] emptyInbox;
        Fixture(Path root,InboxStore inbox,ImageAssociationRegistry registry,ImageStageStore stage,UUID id,
                ReceivedImagePayload payload,ReceivedImageReview review,byte[] emptyInbox) {
            this.root=root;this.inbox=inbox;this.registry=registry;this.stage=stage;this.id=id;this.payload=payload;
            this.review=review;this.item=review.images.get(0);this.emptyInbox=emptyInbox;
        }
    }
}
