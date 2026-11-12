package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class ReceivedImageReviewTest {
    private static final String A = "synthetic-source-A", B = "synthetic-source-B";
    private static final Instant NOW = Instant.ofEpochSecond(1794444000);
    private interface Action { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("tmm-image-review-");
        try {
            ImageOffer one = offer(1), two = offer(2);
            InboxStore inbox = new InboxStore(root.resolve("inbox")); inbox.initializeNew();
            ImageAssociationRegistry registry = new ImageAssociationRegistry(root.resolve("registry"));
            registry.initializeNew(inbox.load().inboxId);
            receive(inbox, registry, A, one); receive(inbox, registry, A, two); receive(inbox, registry, B, one);
            TmmDraft subject = ImageOffer.carrier(one.metadata);
            TmmDraft other = new TmmDraft(UUID.randomUUID(),subject.displayId,subject.type,subject.status,subject.owner,
                    subject.createdAt,subject.updatedAt,subject.deployedAt,subject.comment,subject.position,subject.bearingDeg,null,1);
            ImageOffer foreign = new ImageOffer(UUID.randomUUID(),UUID.randomUUID(),new ShareReview.Metadata(other),
                    one.sha256(),one.pngLength,one.width,one.height);
            receive(inbox,registry,A,foreign);
            byte[] beforeInbox = Files.readAllBytes(root.resolve("inbox/inbox.bin"));
            byte[] beforeRegistry = Files.readAllBytes(root.resolve("registry/associations.bin"));
            LocalV2.Snapshot local = snapshot(LocalV2.State.active(7,withImage(subject)));
            ReceivedImageReview review = ReceivedImageReview.assess(registry.load(),inbox.load(),local,subject.uid);
            assert review.images.size()==3 : "filter by UUID, never display label";
            assert review.images.get(0).record.contextId.equals(A) && review.images.get(2).record.contextId.equals(B);
            for (ReceivedImageReview.Item item : review.images) {
                assert item.metadata!=null && item.sameCurrentMetadata && item.localPresence==LocalV2.Presence.ACTIVE;
                assert ImageOffer.carrier(item.metadata).imageRef==null;
            }
            assert review.images.get(0).matchesOffer(one) && review.images.get(1).matchesOffer(two);
            assert review.images.get(2).matchesOffer(one) : "same claims in another context retain separate provenance";
            assert !review.images.get(0).matchesOffer(two) && !review.images.get(0).matchesOffer(foreign);
            assert !review.images.get(0).matchesOffer(null);
            assert review.images.get(0).matchesReceipt(inbox.find(A,subject));
            assert !review.images.get(0).matchesReceipt(inbox.find(B,subject));
            assert !review.images.get(0).matchesReceipt(null);
            review.images.get(0).record.sha256()[0]^=1;
            assert review.images.get(0).matchesOffer(one);
            try { review.images.clear(); throw new AssertionError("mutable review"); }
            catch (UnsupportedOperationException expected) { }

            for (LocalV2.State state : new LocalV2.State[]{LocalV2.State.neverSeen(subject.uid),
                    LocalV2.State.deleted(subject.uid,8), LocalV2.State.active(9,changed(subject))}) {
                ReceivedImageReview different = ReceivedImageReview.assess(registry.load(),inbox.load(),snapshot(state),subject.uid);
                assert different.images.size()==3;
                for (ReceivedImageReview.Item item : different.images) {
                    assert item.localPresence==state.presence && !item.sameCurrentMetadata;
                    assert item.metadata!=null;
                }
                assert different.images.get(0).matchesOffer(one) : "historical valid offer is not rebound to local changes";
            }
            ReceivedImageReview empty = ReceivedImageReview.assess(registry.load(),inbox.load(),local,UUID.randomUUID());
            assert empty.images.isEmpty();

            boolean[] locked = {false};
            registry.withRecord(review.registryId,review.inboxId,review.images.get(0).record,()->{
                try { new ImageAssociationRegistry(root.resolve("registry")).load(); }
                catch(IOException busy) { locked[0]=true; }
                return null;
            });
            assert locked[0] : "registry lock must cover stage opening";
            rejects(() -> registry.withRecord(UUID.randomUUID(),review.inboxId,review.images.get(0).record,()->null));
            rejects(() -> registry.withRecord(review.registryId,UUID.randomUUID(),review.images.get(0).record,()->null));

            InboxStore.Snapshot saved = inbox.load();
            rejects(() -> ReceivedImageReview.assess(registry.load(),new InboxStore.Snapshot(UUID.randomUUID(),saved.receipts),local,subject.uid));
            rejects(() -> ReceivedImageReview.assess(null,saved,local,subject.uid));
            rejects(() -> ReceivedImageReview.assess(registry.load(),null,local,subject.uid));
            rejects(() -> ReceivedImageReview.assess(registry.load(),saved,null,subject.uid));
            List<InboxStore.Receipt> duplicate = new ArrayList<>(saved.receipts); duplicate.add(saved.receipts.get(0));
            rejects(() -> ReceivedImageReview.assess(registry.load(),new InboxStore.Snapshot(saved.inboxId,duplicate),local,subject.uid));

            ReceivedImageReview lost = ReceivedImageReview.assess(registry.load(),
                    new InboxStore.Snapshot(saved.inboxId,Collections.emptyList()),local,subject.uid);
            assert lost.images.size()==3 : "broken links must not vanish";
            for (ReceivedImageReview.Item item : lost.images) assert item.metadata==null && !item.matchesOffer(one) && !item.sameCurrentMetadata;
            for (boolean wrongContext : new boolean[]{true,false}) {
                List<InboxStore.Receipt> damaged = new ArrayList<>(saved.receipts);
                InboxStore.Receipt first = damaged.get(0);
                damaged.set(0,new InboxStore.Receipt(first.id,wrongContext?"foreign-context":first.contextId,
                        first.firstObservedAt,wrongContext?first.incoming:other,first.localObservations));
                ReceivedImageReview links = ReceivedImageReview.assess(registry.load(),new InboxStore.Snapshot(saved.inboxId,damaged),local,subject.uid);
                assert links.images.get(0).metadata==null && links.images.get(1).metadata==null;
                assert links.images.get(2).metadata!=null;
            }

            ReceivedImageReview.Item first = review.images.get(0);
            for (ImageOffer substituted : new ImageOffer[]{
                    copy(one,UUID.randomUUID(),one.deliveryId,one.metadata,one.sha256(),one.width),
                    copy(one,one.associationId,UUID.randomUUID(),one.metadata,one.sha256(),one.width),
                    copy(one,one.associationId,one.deliveryId,new ShareReview.Metadata(changed(subject)),one.sha256(),one.width),
                    copy(one,one.associationId,one.deliveryId,one.metadata,two.sha256(),one.width),
                    copy(one,one.associationId,one.deliveryId,one.metadata,one.sha256(),63)}) assert !first.matchesOffer(substituted);
            assert Arrays.equals(beforeInbox,Files.readAllBytes(root.resolve("inbox/inbox.bin")));
            assert Arrays.equals(beforeRegistry,Files.readAllBytes(root.resolve("registry/associations.bin")));
            System.out.println("Received image review passed (UUID/source/receipt linkage, current/deleted/history, strict offer coherence, immutable output and no store writes).");
        } finally {
            try (java.util.stream.Stream<Path> paths=Files.walk(root)) {
                for (Path p : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
    }

    private static ImageOffer offer(int n) throws IOException {
        try(InputStream in=Files.newInputStream(Paths.get("test-fixtures/synthetic/image-offer-proposal/offer-h"+n+".bin"))) {
            return ImageOfferCodec.decode(in);
        }
    }
    private static void receive(InboxStore inbox,ImageAssociationRegistry registry,String context,ImageOffer offer) throws IOException {
        inbox.receive(context,ImageOffer.carrier(offer.metadata),null,NOW);
        registry.associate(inbox,context,offer,UUID.randomUUID(),NOW,(id,expected)->{ });
    }
    private static LocalV2.Snapshot snapshot(LocalV2.State state) {
        return new LocalV2.Snapshot(UUID.randomUUID(),Collections.singletonList(state),Collections.emptyList());
    }
    private static TmmDraft withImage(TmmDraft d) {
        return new TmmDraft(d.uid,d.displayId,d.type,d.status,d.owner,d.createdAt,d.updatedAt,d.deployedAt,d.comment,
                d.position,d.bearingDeg,"00000000-0000-4000-8000-000000000202.png",99);
    }
    private static TmmDraft changed(TmmDraft d) {
        return new TmmDraft(d.uid,d.displayId,d.type,d.status,d.owner,d.createdAt,d.updatedAt.plusNanos(1),d.deployedAt,
                d.comment,d.position,d.bearingDeg,null,d.revision+1);
    }
    private static ImageOffer copy(ImageOffer d,UUID a,UUID b,ShareReview.Metadata metadata,byte[] hash,int width) {
        return new ImageOffer(a,b,metadata,hash,d.pngLength,width,d.height);
    }
    private static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("expected unavailable review"); }
        catch(IOException expected) { }
    }
}
