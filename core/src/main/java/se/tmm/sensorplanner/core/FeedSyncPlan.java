package se.tmm.sensorplanner.core;

import java.util.*;
import se.tmm.sensorplanner.core.FeedSyncDecision.ImageState;

/**
 * Pure per-device plan for one completed fetch (docs/AUTOMATIC_FEED_SYNC_PROPOSAL.md, #242).
 * No I/O: the caller reads the inputs, executes the plan, and writes {@link #nextSync}.
 *
 * <p>{@link #nextSync} holds only bookkeeping that needs no local write: remote receipt ids,
 * metadata ADOPT_BASE and image ADOPT_BASE. APPLY and ADD never change a base here (U5: the
 * executor sets it after reading its local commit back); adoptNew/adoptOld are never touched.
 *
 * <p>Conservative choices: a device that would be new in a full state file (256 records) gets
 * NOTHING/NOTHING, is left out of nextSync and is listed in {@link #overCapacity}. Devices only
 * in the sync file get NOTHING (U3: missing never deletes). A NEVER_SEEN local device counts as
 * absent locally, exactly like DELETED. If several feed images share the chosen digest, one bound
 * to the current M is the remote image record, else the first in {@code feedImages} order.
 */
public final class FeedSyncPlan {
    public static final class Entry {
        public final UUID deviceUid;
        public final FeedSyncDecision decision;
        public final TmmDraft remote;                              // null when not in this fetch
        public final ImageAssociationRegistry.Record remoteImage;  // image APPLY or ADOPT_BASE; null for ADOPT_BASE with H = no image
        public final LocalV2.State local;

        Entry(UUID deviceUid, FeedSyncDecision decision, TmmDraft remote,
              ImageAssociationRegistry.Record remoteImage, LocalV2.State local) {
            this.deviceUid = deviceUid;
            this.decision = decision;
            this.remote = remote;
            this.remoteImage = remoteImage;
            this.local = local;
        }
    }

    public final List<Entry> entries;
    public final FeedSyncState.Snapshot nextSync;
    public final List<UUID> overCapacity;

    private FeedSyncPlan(List<Entry> entries, FeedSyncState.Snapshot nextSync, List<UUID> overCapacity) {
        this.entries = Collections.unmodifiableList(entries);
        this.nextSync = nextSync;
        this.overCapacity = Collections.unmodifiableList(overCapacity);
    }

    public static FeedSyncPlan plan(FeedSyncState.Snapshot sync, InboxStore.Snapshot inbox,
                                    List<UUID> fetchedReceiptIds,
                                    List<ImageAssociationRegistry.Record> feedImages,
                                    LocalV2.Snapshot local, Map<UUID, ImageState> localImages) {
        Objects.requireNonNull(sync, "sync");
        Objects.requireNonNull(inbox, "inbox");
        Objects.requireNonNull(local, "local");
        if (!sync.inboxId.equals(inbox.inboxId)) throw new IllegalArgumentException("Inbox does not match sync state");
        Map<UUID, InboxStore.Receipt> receipts = new HashMap<UUID, InboxStore.Receipt>();
        for (InboxStore.Receipt r : inbox.receipts) receipts.put(r.id, r);

        Map<UUID, InboxStore.Receipt> fetched = new TreeMap<UUID, InboxStore.Receipt>(UNSIGNED);
        for (UUID id : fetchedReceiptIds) {
            InboxStore.Receipt r = receipts.get(id);
            if (r == null || !r.contextId.equals(sync.contextId)) throw new IllegalArgumentException("Fetched receipt not in inbox or context");
            if (fetched.put(r.incoming.uid, r) != null) throw new IllegalArgumentException("Duplicate device in fetch");
        }
        Map<UUID, FeedSyncState.Record> records = new HashMap<UUID, FeedSyncState.Record>();
        for (FeedSyncState.Record r : sync.records) records.put(r.deviceUid, r);
        Map<UUID, List<ImageAssociationRegistry.Record>> images = new HashMap<UUID, List<ImageAssociationRegistry.Record>>();
        for (ImageAssociationRegistry.Record r : feedImages) {
            if (!r.contextId.equals(sync.contextId)) throw new IllegalArgumentException("Feed image from another context");
            if (!images.containsKey(r.deviceUid)) images.put(r.deviceUid, new ArrayList<ImageAssociationRegistry.Record>());
            images.get(r.deviceUid).add(r);
        }

        TreeSet<UUID> devices = new TreeSet<UUID>(UNSIGNED);
        devices.addAll(fetched.keySet());
        devices.addAll(records.keySet());
        int free = FeedSyncState.MAX_RECORDS - records.size();
        List<Entry> entries = new ArrayList<Entry>();
        List<UUID> over = new ArrayList<UUID>();
        Map<UUID, FeedSyncState.Record> next = new HashMap<UUID, FeedSyncState.Record>(records);

        for (UUID uid : devices) {
            LocalV2.State state = local.state(uid);
            InboxStore.Receipt receipt = fetched.get(uid);
            FeedSyncState.Record rec = records.get(uid);
            if (receipt == null || (rec == null && free-- <= 0)) {
                if (receipt != null) over.add(uid);
                entries.add(new Entry(uid, FeedSyncDecision.decide(null, null, null,
                        Collections.<byte[]>emptyList(), ImageState.UNKNOWN, ImageState.NONE),
                        receipt == null ? null : receipt.incoming, null, state));
                continue;
            }
            TmmDraft base = null;
            if (rec != null && rec.baseReceiptId != null) {
                InboxStore.Receipt b = receipts.get(rec.baseReceiptId);
                if (b == null || !b.incoming.uid.equals(uid)) throw new IllegalStateException("Base receipt missing from inbox");
                base = b.incoming;
            }
            ImageState baseImage = rec == null || rec.imageKind == FeedSyncState.IMAGE_UNKNOWN ? ImageState.UNKNOWN
                    : rec.imageKind == FeedSyncState.IMAGE_NONE ? ImageState.NONE : ImageState.of(rec.imageDigest());
            TmmDraft localDraft = state.presence == LocalV2.Presence.ACTIVE ? state.draft : null;
            ImageState localImage = ImageState.NONE;
            if (localDraft != null) {
                localImage = localImages.get(uid);
                if (localImage == null) throw new IllegalArgumentException("Missing local image state for an active device");
            }
            List<ImageAssociationRegistry.Record> bound = images.containsKey(uid)
                    ? images.get(uid) : Collections.<ImageAssociationRegistry.Record>emptyList();
            List<byte[]> digests = new ArrayList<byte[]>(), current = new ArrayList<byte[]>();
            for (ImageAssociationRegistry.Record r : bound) {
                digests.add(r.sha256());
                if (receipt.id.equals(r.receiptId)) current.add(r.sha256());
            }
            FeedSyncDecision d = FeedSyncDecision.decide(receipt.incoming, base, localDraft, current, digests, baseImage, localImage);

            ImageAssociationRegistry.Record chosen = null;
            if (d.image == FeedSyncDecision.Image.APPLY || d.image == FeedSyncDecision.Image.ADOPT_BASE && !bound.isEmpty()) {
                chosen = chooseH(bound, receipt.id, FeedSyncDecision.remoteImage(current, digests, baseImage));
            }
            entries.add(new Entry(uid, d, receipt.incoming, chosen, state));

            UUID newBase = rec == null ? null : rec.baseReceiptId;
            int kind = rec == null ? FeedSyncState.IMAGE_UNKNOWN : rec.imageKind;
            byte[] digest = rec == null ? null : rec.imageDigest();
            if (d.metadata == FeedSyncDecision.Metadata.ADOPT_BASE) newBase = receipt.id;
            if (d.image == FeedSyncDecision.Image.ADOPT_BASE) {
                kind = chosen == null ? FeedSyncState.IMAGE_NONE : FeedSyncState.IMAGE_DIGEST;
                digest = chosen == null ? null : chosen.sha256();
            }
            next.put(uid, new FeedSyncState.Record(uid, receipt.id, newBase, kind, digest,
                    rec == null ? null : rec.adoptNew, rec == null ? null : rec.adoptOld));
        }
        FeedSyncState.Snapshot nextSync = new FeedSyncState.Snapshot(sync.syncId, sync.inboxId, sync.following,
                sync.contextId, sync.feedName, sync.streamConnect, new ArrayList<FeedSyncState.Record>(next.values()));
        return new FeedSyncPlan(entries, nextSync, over);
    }

    /** The record whose digest is H, preferring one bound to the current M, then registry order. */
    private static ImageAssociationRegistry.Record chooseH(List<ImageAssociationRegistry.Record> bound, UUID current,
                                                           ImageState h) {
        ImageAssociationRegistry.Record first = null;
        for (ImageAssociationRegistry.Record r : bound) {
            if (!ImageState.of(r.sha256()).sameAs(h)) continue;
            if (current.equals(r.receiptId)) return r;
            if (first == null) first = r;
        }
        if (first == null) throw new IllegalStateException("No record for the remote image");
        return first;
    }

    private static final Comparator<UUID> UNSIGNED = new Comparator<UUID>() {
        @Override public int compare(UUID a, UUID b) {
            int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
            return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
        }
    };
}
