package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.util.*;
import se.tmm.sensorplanner.core.FeedSyncDecision.ImageState;
import static se.tmm.sensorplanner.core.FeedSyncDecision.Image;
import static se.tmm.sensorplanner.core.FeedSyncDecision.Metadata;

public final class FeedSyncPlanTest {
    private static final String CTX = "mission-api-v1|test";
    private static final UUID SYNC = id(900), INBOX = id(901), STORE = id(902);
    private static final UUID A = id(1), B = id(2);
    private static final byte[] H1 = digest(1), H2 = digest(2);
    private static final List<ImageAssociationRegistry.Record> NO_FEED = Collections.<ImageAssociationRegistry.Record>emptyList();
    private static final Map<UUID, ImageState> NO_IMG = Collections.<UUID, ImageState>emptyMap();

    public static void main(String[] args) {
        firstFetchAdds();
        duplicateFetchIsByteIdentical();
        adoptBaseBookkeeping();
        applyLeavesBase();
        conflictAndNeverSeen();
        absentDeviceKeepsRecord();
        refusals();
        overCapacity();
        imageSelection();
        System.out.println("Feed sync plan tests passed.");
    }

    private static UUID id(int n) { return UUID.fromString(String.format("00000000-0000-4000-8026-%012d", n)); }

    private static byte[] digest(int seed) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) seed);
        return bytes;
    }

    private static TmmDraft draft(UUID uid, String comment) {
        return new TmmDraft(uid, "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE, "TEST ROLE",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(10), null, comment, new Position(0, 1), null, null, 1);
    }

    private static InboxStore.Receipt receipt(int n, TmmDraft d) {
        return new InboxStore.Receipt(id(n), CTX, Instant.EPOCH, d, Collections.<TmmDraft>emptyList());
    }

    private static InboxStore.Snapshot inbox(InboxStore.Receipt... r) { return new InboxStore.Snapshot(INBOX, Arrays.asList(r)); }

    private static FeedSyncState.Snapshot sync(FeedSyncState.Record... r) {
        return new FeedSyncState.Snapshot(SYNC, INBOX, true, CTX, "Feed", "stream", Arrays.asList(r));
    }

    private static FeedSyncState.Record rec(UUID dev, int remote, int base, int kind, byte[] image) {
        return new FeedSyncState.Record(dev, id(remote), base == 0 ? null : id(base), kind, image, null, null);
    }

    private static LocalV2.Snapshot local(LocalV2.State... s) {
        return new LocalV2.Snapshot(STORE, Arrays.asList(s), Collections.<LocalV2.Decision>emptyList());
    }

    private static ImageAssociationRegistry.Record image(UUID dev, int receipt, byte[] d) {
        return new ImageAssociationRegistry.Record(UUID.randomUUID(), UUID.randomUUID(), dev, id(receipt), UUID.randomUUID(),
                CTX, 10, 1, 1, d, Instant.EPOCH);
    }

    private static List<UUID> ids(int... n) {
        List<UUID> l = new ArrayList<UUID>();
        for (int i : n) l.add(id(i));
        return l;
    }

    private static FeedSyncPlan.Entry entry(FeedSyncPlan p, UUID dev) {
        for (FeedSyncPlan.Entry e : p.entries) if (e.deviceUid.equals(dev)) return e;
        throw new AssertionError("no entry");
    }

    private static FeedSyncState.Record next(FeedSyncPlan p, UUID dev) {
        for (FeedSyncState.Record r : p.nextSync.records) if (r.deviceUid.equals(dev)) return r;
        return null;
    }

    private static void rejects(Class<? extends RuntimeException> type, Runnable r) {
        try { r.run(); } catch (RuntimeException e) { assert type.isInstance(e) : e; return; }
        throw new AssertionError("expected " + type);
    }

    private static void firstFetchAdds() {
        InboxStore.Snapshot in = inbox(receipt(11, draft(B, "b")), receipt(10, draft(A, "a")));
        FeedSyncPlan p = FeedSyncPlan.plan(sync(), in, ids(11, 10), NO_FEED, local(), NO_IMG);
        assert p.entries.size() == 2 && p.entries.get(0).deviceUid.equals(A) && p.entries.get(1).deviceUid.equals(B);
        assert entry(p, A).decision.metadata == Metadata.ADD && entry(p, A).decision.image == Image.NOTHING;
        FeedSyncState.Record r = next(p, A);
        assert r.remoteReceiptId.equals(id(10)) && r.baseReceiptId == null && r.imageKind == 0;
        assert p.overCapacity.isEmpty();
    }

    private static void duplicateFetchIsByteIdentical() {
        TmmDraft m = draft(A, "a");
        FeedSyncState.Snapshot s = sync(rec(A, 10, 10, 1, null));
        LocalV2.Snapshot l = local(LocalV2.State.active(1, m));
        Map<UUID, ImageState> li = Collections.singletonMap(A, ImageState.NONE);
        FeedSyncPlan p = FeedSyncPlan.plan(s, inbox(receipt(10, m)), ids(10), NO_FEED, l, li);
        assert entry(p, A).decision.metadata == Metadata.NOTHING && entry(p, A).decision.image == Image.NOTHING;
        assert Arrays.equals(FeedSyncState.encode(s), FeedSyncState.encode(p.nextSync));
    }

    private static void adoptBaseBookkeeping() {
        TmmDraft m1 = draft(A, "one"), m2 = draft(A, "two");
        FeedSyncState.Snapshot s = sync(rec(A, 10, 10, 1, null));
        LocalV2.Snapshot l = local(LocalV2.State.active(1, m2));
        Map<UUID, ImageState> li = Collections.singletonMap(A, ImageState.of(H1));
        List<ImageAssociationRegistry.Record> feed = Collections.singletonList(image(A, 11, H1));
        FeedSyncPlan p = FeedSyncPlan.plan(s, inbox(receipt(10, m1), receipt(11, m2)), ids(11), feed, l, li);
        FeedSyncPlan.Entry e = entry(p, A);
        assert e.decision.metadata == Metadata.ADOPT_BASE && e.decision.image == Image.ADOPT_BASE;
        assert e.remoteImage == feed.get(0);
        FeedSyncState.Record r = next(p, A);
        assert r.remoteReceiptId.equals(id(11)) && r.baseReceiptId.equals(id(11));
        assert r.imageKind == 2 && Arrays.equals(r.imageDigest(), H1);
    }

    private static void applyLeavesBase() {
        TmmDraft m1 = draft(A, "one"), m2 = draft(A, "two");
        FeedSyncState.Snapshot s = sync(rec(A, 10, 10, 1, null));
        Map<UUID, ImageState> li = Collections.singletonMap(A, ImageState.NONE);
        List<ImageAssociationRegistry.Record> feed = Collections.singletonList(image(A, 11, H1));
        FeedSyncPlan p = FeedSyncPlan.plan(s, inbox(receipt(10, m1), receipt(11, m2)), ids(11), feed,
                local(LocalV2.State.active(1, m1)), li);
        FeedSyncPlan.Entry e = entry(p, A);
        assert e.decision.metadata == Metadata.APPLY && e.decision.image == Image.APPLY && e.remoteImage == feed.get(0);
        FeedSyncState.Record r = next(p, A);
        assert r.remoteReceiptId.equals(id(11)) && r.baseReceiptId.equals(id(10)) && r.imageKind == 1;
        // ADD also leaves the base empty
        FeedSyncPlan add = FeedSyncPlan.plan(sync(), inbox(receipt(11, m2)), ids(11), NO_FEED, local(), NO_IMG);
        assert next(add, A).baseReceiptId == null;
    }

    private static void conflictAndNeverSeen() {
        TmmDraft m1 = draft(A, "one"), m2 = draft(A, "two"), m3 = draft(A, "three");
        Map<UUID, ImageState> li = Collections.singletonMap(A, ImageState.NONE);
        FeedSyncPlan p = FeedSyncPlan.plan(sync(rec(A, 10, 10, 1, null)), inbox(receipt(10, m1), receipt(11, m2)), ids(11),
                NO_FEED, local(LocalV2.State.active(1, m3)), li);
        assert entry(p, A).decision.metadata == Metadata.CONFLICT && next(p, A).baseReceiptId.equals(id(10));
        // NEVER_SEEN with a base is treated as absent locally: R != B conflicts, R = B does nothing.
        FeedSyncPlan never = FeedSyncPlan.plan(sync(rec(A, 10, 10, 1, null)), inbox(receipt(10, m1), receipt(11, m2)), ids(11),
                NO_FEED, local(), NO_IMG);
        assert entry(never, A).decision.metadata == Metadata.CONFLICT && entry(never, A).local.presence == LocalV2.Presence.NEVER_SEEN;
        FeedSyncPlan same = FeedSyncPlan.plan(sync(rec(A, 10, 10, 1, null)), inbox(receipt(10, m1)), ids(10),
                NO_FEED, local(LocalV2.State.deleted(A, 2)), NO_IMG);
        assert entry(same, A).decision.metadata == Metadata.NOTHING;
    }

    private static void absentDeviceKeepsRecord() {
        TmmDraft m = draft(A, "a");
        FeedSyncState.Snapshot s = sync(rec(A, 10, 10, 1, null));
        FeedSyncPlan p = FeedSyncPlan.plan(s, inbox(receipt(10, m), receipt(11, draft(B, "b"))), ids(11), NO_FEED, local(), NO_IMG);
        assert entry(p, A).decision.metadata == Metadata.NOTHING && entry(p, A).decision.image == Image.NOTHING && entry(p, A).remote == null;
        FeedSyncState.Record kept = next(p, A);
        assert kept.remoteReceiptId.equals(id(10)) && kept.baseReceiptId.equals(id(10));
        assert next(p, B) != null && entry(p, B).decision.metadata == Metadata.ADD;
    }

    private static void refusals() {
        final TmmDraft m = draft(A, "a");
        final InboxStore.Snapshot in = inbox(receipt(10, m), receipt(11, draft(A, "other")));
        rejects(IllegalStateException.class, new Runnable() { public void run() {
            FeedSyncPlan.plan(sync(rec(A, 10, 99, 1, null)), in, ids(10), NO_FEED, local(), NO_IMG); } });
        rejects(IllegalArgumentException.class, new Runnable() { public void run() {
            FeedSyncPlan.plan(sync(), new InboxStore.Snapshot(id(777), in.receipts), ids(10), NO_FEED, local(), NO_IMG); } });
        rejects(IllegalArgumentException.class, new Runnable() { public void run() {
            FeedSyncPlan.plan(new FeedSyncState.Snapshot(SYNC, INBOX, true, "mission-api-v1|other", "F", "s",
                    Collections.<FeedSyncState.Record>emptyList()), in, ids(10), NO_FEED, local(), NO_IMG); } });
        rejects(IllegalArgumentException.class, new Runnable() { public void run() {
            FeedSyncPlan.plan(sync(), in, ids(10, 11), NO_FEED, local(), NO_IMG); } });          // duplicate device
        rejects(IllegalArgumentException.class, new Runnable() { public void run() {
            FeedSyncPlan.plan(sync(), in, ids(55), NO_FEED, local(), NO_IMG); } });              // unknown receipt
        rejects(IllegalArgumentException.class, new Runnable() { public void run() {
            FeedSyncPlan.plan(sync(), in, ids(10), NO_FEED, local(LocalV2.State.active(1, m)), NO_IMG); } }); // no image state
    }

    private static void overCapacity() {
        List<FeedSyncState.Record> records = new ArrayList<FeedSyncState.Record>();
        for (int i = 0; i < FeedSyncState.MAX_RECORDS; i++) records.add(rec(id(1000 + i), 5000 + i, 0, 0, null));
        FeedSyncState.Snapshot full = new FeedSyncState.Snapshot(SYNC, INBOX, true, CTX, "F", "s", records);
        FeedSyncPlan p = FeedSyncPlan.plan(full, inbox(receipt(10, draft(A, "a"))), ids(10), NO_FEED, local(), NO_IMG);
        assert p.overCapacity.equals(Collections.singletonList(A));
        assert entry(p, A).decision.metadata == Metadata.NOTHING && next(p, A) == null;
        assert p.nextSync.records.size() == FeedSyncState.MAX_RECORDS;
        // with one free slot, the first new device in order fits and the second does not
        records.remove(0);
        FeedSyncState.Snapshot one = new FeedSyncState.Snapshot(SYNC, INBOX, true, CTX, "F", "s", records);
        FeedSyncPlan q = FeedSyncPlan.plan(one, inbox(receipt(10, draft(A, "a")), receipt(11, draft(B, "b"))), ids(11, 10), NO_FEED, local(), NO_IMG);
        assert q.overCapacity.equals(Collections.singletonList(B)) && next(q, A) != null;
    }

    private static void imageSelection() {
        TmmDraft m = draft(A, "a");
        ImageAssociationRegistry.Record r1 = image(A, 10, H1), r1b = image(A, 11, H1), r2 = image(A, 12, H2);
        // base image H1, feed binds H1 (two records) and H2: H = H2, the one differing from the base
        FeedSyncPlan p = FeedSyncPlan.plan(sync(rec(A, 10, 10, 2, H1)), inbox(receipt(10, m)), ids(10),
                Arrays.asList(r1, r1b, r2), local(LocalV2.State.active(1, m)), Collections.singletonMap(A, ImageState.of(H1)));
        assert entry(p, A).decision.image == Image.APPLY && entry(p, A).remoteImage == r2;
        // ambiguous without a base: nothing chosen, nothing changed
        FeedSyncPlan amb = FeedSyncPlan.plan(sync(), inbox(receipt(10, m)), ids(10), Arrays.asList(r1, r2), local(), NO_IMG);
        assert entry(amb, A).decision.image == Image.AMBIGUOUS && entry(amb, A).remoteImage == null;
        // duplicates of the same digest: first record in list order wins
        FeedSyncPlan dup = FeedSyncPlan.plan(sync(), inbox(receipt(10, m)), ids(10), Arrays.asList(r1b, r1), local(), NO_IMG);
        assert entry(dup, A).remoteImage == r1b;
    }
}
