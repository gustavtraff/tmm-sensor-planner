package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import se.tmm.sensorplanner.core.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceivedImageIntakeTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final Instant NOW=Instant.ofEpochSecond(1794520800);
    private static final String A="synthetic-feed-A",B="synthetic-feed-B";
    @Test public void actualIntakeRepeatReopenAndOtherContextKeepSeparateRecordsWithoutDraftWrites()throws Exception {
        Fixture f=new Fixture();byte[] local=Files.readAllBytes(f.root.resolve("local/drafts.bin"));
        ImageAssociationRegistry.Record r=f.intake.receive(A,f.payload,NOW,()->true);
        assertEquals(1,f.inbox.load().receipts.size());assertEquals(1,f.registry.load().records.size());assertEquals(1,f.stage.inspect().ready.size());
        ImageAssociationRegistry.Record repeat=f.intake.receive(A,f.payload,NOW.plusSeconds(1),()->true);assertEquals(r.stageLocalId,repeat.stageLocalId);assertEquals(r.verifiedAt,repeat.verifiedAt);
        ReceivedImageIntake reopened=new ReceivedImageIntake(new InboxStore(f.root.resolve("inbox")),new ImageAssociationRegistry(f.root.resolve("registry")),new ImageStageStore(f.root.resolve("stage")),new LocalV2Store(f.root.resolve("local")));
        assertEquals(r.stageLocalId,reopened.receive(A,f.payload,NOW.plusSeconds(2),()->true).stageLocalId);
        ImageAssociationRegistry.Record other=reopened.receive(B,f.payload,NOW,()->true);assertNotEquals(r.stageLocalId,other.stageLocalId);assertEquals(2,f.registry.load().records.size());assertEquals(2,f.inbox.load().receipts.size());assertEquals(2,f.stage.inspect().ready.size());
        assertArrayEquals(local,Files.readAllBytes(f.root.resolve("local/drafts.bin")));assertTrue(f.local.load().activeDrafts().isEmpty());
    }
    @Test public void canceledAfterStageWritePreservesOrphanAndFreshIntakeReusesIt()throws Exception {
        Fixture f=new Fixture();
        assertThrows(IOException.class,()->f.intake.receive(A,f.payload,NOW,()->{
            try{return f.stage.inspect().ready.isEmpty();}catch(IOException unavailable){return false;}
        }));
        assertEquals(1,f.stage.inspect().ready.size());assertTrue(f.registry.load().records.isEmpty());assertEquals(1,f.inbox.load().receipts.size());
        UUID original=f.stage.inspect().ready.get(0);
        assertEquals(original,f.intake.receive(A,f.payload,NOW.plusSeconds(1),()->true).stageLocalId);
        assertEquals(1,f.stage.inspect().ready.size());assertEquals(1,f.registry.load().records.size());assertEquals(1,f.inbox.load().receipts.size());
    }
    @Test public void knownMissingPictureIsNotRecreatedByDownloadingItAgain()throws Exception {
        Fixture f=new Fixture();ImageAssociationRegistry.Record record=f.intake.receive(A,f.payload,NOW,()->true);
        byte[] registry=Files.readAllBytes(f.root.resolve("registry/associations.bin"));Path png=f.root.resolve("stage/ready-"+record.stageLocalId+"/image.png");Files.delete(png);
        assertThrows(IOException.class,()->f.intake.receive(A,f.payload,NOW.plusSeconds(1),()->true));assertFalse(Files.exists(png));assertArrayEquals(registry,Files.readAllBytes(f.root.resolve("registry/associations.bin")));
        try(java.util.stream.Stream<Path> dirs=Files.list(f.root.resolve("stage"))){assertEquals(1,dirs.filter(p->p.getFileName().toString().startsWith("ready-")).count());}
    }
    @Test public void sameDeliveryWithChangedMetadataRefusesWithoutChangingHistoryOrBytes()throws Exception {
        Fixture f=new Fixture();f.intake.receive(A,f.payload,NOW,()->true);ShareReview.Metadata m=f.payload.offer.metadata;
        TmmDraft d=new TmmDraft(m.uid,m.displayId,m.type,DeviceStatus.ACTIVE,m.owner,m.createdAt,m.updatedAt.plusSeconds(1),m.deployedAt,m.comment,m.position,m.bearingDeg,null,1);
        ImageOffer offer=new ImageOffer(f.payload.offer.associationId,f.payload.offer.deliveryId,new PublicationPreparationReview(d).prepare(d).metadata,f.payload.offer.sha256(),f.payload.offer.pngLength,f.payload.offer.width,f.payload.offer.height);
        ReceivedImagePayload changed=ReceivedImagePayload.read(new ByteArrayInputStream(ImageOfferCodec.encode(offer)),new ByteArrayInputStream(f.payload.image.bytes()));
        byte[] inbox=Files.readAllBytes(f.root.resolve("inbox/inbox.bin")),registry=Files.readAllBytes(f.root.resolve("registry/associations.bin"));
        assertThrows(IOException.class,()->f.intake.receive(A,changed,NOW,()->true));assertArrayEquals(inbox,Files.readAllBytes(f.root.resolve("inbox/inbox.bin")));assertArrayEquals(registry,Files.readAllBytes(f.root.resolve("registry/associations.bin")));assertEquals(1,f.stage.inspect().ready.size());
    }
    @Test public void missingStoresWrongNamespaceAndEarlyCancelNeverInitializeOrWrite()throws Exception {
        Fixture f=new Fixture();byte[] inbox=Files.readAllBytes(f.root.resolve("inbox/inbox.bin"));
        assertThrows(IOException.class,()->f.intake.receive(A,f.payload,NOW,()->false));assertTrue(f.stage.inspect().ready.isEmpty());assertArrayEquals(inbox,Files.readAllBytes(f.root.resolve("inbox/inbox.bin")));
        ImageAssociationRegistry absent=new ImageAssociationRegistry(f.root.resolve("absent"));
        assertThrows(IOException.class,()->new ReceivedImageIntake(f.inbox,absent,f.stage,f.local).receive(A,f.payload,NOW,()->true));assertFalse(Files.exists(f.root.resolve("absent/associations.bin")));
        ImageAssociationRegistry wrong=new ImageAssociationRegistry(f.root.resolve("wrong"));wrong.initializeNew(UUID.randomUUID());
        assertThrows(IOException.class,()->new ReceivedImageIntake(f.inbox,wrong,f.stage,f.local).receive(A,f.payload,NOW,()->true));assertTrue(f.stage.inspect().ready.isEmpty());assertArrayEquals(inbox,Files.readAllBytes(f.root.resolve("inbox/inbox.bin")));
    }
    private final class Fixture {
        final Path root;final InboxStore inbox;final ImageAssociationRegistry registry;final ImageStageStore stage;final LocalV2Store local;final ReceivedImagePayload payload;final ReceivedImageIntake intake;
        Fixture()throws Exception {
            root=temporary.newFolder().toPath();inbox=new InboxStore(root.resolve("inbox"));inbox.initializeNew();registry=new ImageAssociationRegistry(root.resolve("registry"));registry.initializeNew(inbox.load().inboxId);stage=new ImageStageStore(root.resolve("stage"));stage.initializeNew();local=new LocalV2Store(root.resolve("local"));local.initializeNew();
            try(InputStream in=ReceivedImageIntakeTest.class.getResourceAsStream("/h1.tmmi")){payload=ImageContainerCodec.read(in);}
            intake=new ReceivedImageIntake(inbox,registry,stage,local);
        }
    }
}
