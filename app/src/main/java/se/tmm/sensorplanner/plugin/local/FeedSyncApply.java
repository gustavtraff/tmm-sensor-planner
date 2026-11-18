package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.core.FeedSyncDecision.ImageState;
import se.tmm.sensorplanner.core.LocalV2.Presence;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.*;

/**
 * #242 U3–U5: applies one fetch's {@link FeedSyncPlan} locally. Call only on the planner's serialized worker.
 *
 * <p>Every local write is bound to the expected local state; a stale state or any uncertainty skips the
 * device for this round and the next fetch retries. It never deletes a device, never clears an image
 * because something is missing and never sends anything (U8). Image adoption follows
 * docs/FEED_SYNC_STATE_FORMAT.md "Image adoption recovery": an unconfirmed adoption is resolved by
 * {@link #recover()} on load and before every plan.
 */
@TargetApi(26)
final class FeedSyncApply {
    /** Test hook for process-kill simulation; a thrown exception stops the device at that step. */
    interface Steps { void reached(String step) throws IOException; }
    static final String PLANNED = "planned", PREPARED = "prepared", ADOPT_WRITTEN = "adopt-written",
            ATTACHED = "attached", BASE_SET = "base-set";
    // Stands in for an owned image that cannot be read: equals no real digest, so the image is never replaced.
    private static final byte[] UNREADABLE = sha256("TMM unreadable local image".getBytes(StandardCharsets.UTF_8));

    private final FeedSyncState sync;
    private final InboxStore inbox;
    private final ImageStageStore stage;
    private final LocalV2Store local;
    private final LocalImages images;
    private final DraftAttachments attachments;
    // Owned images are never rewritten under the same reference, so a digest stays valid for the process.
    private final Map<String, byte[]> digests = new HashMap<>();
    Steps steps = step -> { };
    private volatile Set<UUID> conflicts = Collections.emptySet(), unpublished = Collections.emptySet();
    private volatile int applied;
    // The registry records of the latest completed fetch; conflict views and resolutions use them (U4 candidates).
    private volatile List<ImageAssociationRegistry.Record> lastImages = Collections.emptyList();

    FeedSyncApply(FeedSyncState sync, InboxStore inbox, ImageStageStore stage, LocalV2Store local, LocalImages images) {
        this.sync = sync; this.inbox = inbox; this.stage = stage; this.local = local; this.images = images;
        attachments = new DraftAttachments(local, images);
    }

    /** Devices with a metadata or image conflict, or an update that cannot be applied, in the latest round. */
    Set<UUID> conflicts() { return conflicts; }
    /** Followed devices whose local version differs from their base (L ≠ B): Ej publicerad. */
    Set<UUID> unpublished() { return unpublished; }
    /** Devices changed locally in the latest round. */
    int applied() { return applied; }

    /** Resolves every unconfirmed image adoption. Returns the current file, or null when there is none. */
    FeedSyncState.Snapshot recover() throws IOException {
        FeedSyncState.Snapshot s = sync.read();
        if (s == null) return null;
        for (FeedSyncState.Record r : new ArrayList<>(s.records)) {
            if (r.adoptNew == null) continue;
            LocalV2.State state = local.load().state(r.deviceUid);
            if (state.presence == Presence.ACTIVE && reference(r.adoptNew).equals(state.draft.imageRef)) {
                s = finish(s, r);   // the commit happened: step 4
            } else {
                // Not committed (still adoptOld or no image), or the user changed it since: drop the new copy only.
                if (!referenced(reference(r.adoptNew))) images.deleteUnreferenced(reference(r.adoptNew));
                s = put(s, record(r, r.baseReceiptId, r.imageKind, r.imageDigest(), null, null));
            }
        }
        return s;
    }

    /** Recovery, plan and execution for one completed fetch. Returns the sync state as written. */
    FeedSyncState.Snapshot apply(FeedSyncState.Snapshot source, List<UUID> receiptIds,
                                 List<ImageAssociationRegistry.Record> feedImages, BooleanSupplier ok) throws IOException {
        applied = 0;
        lastImages = new ArrayList<>(feedImages);
        FeedSyncState.Snapshot s = recover();
        if (s == null || !s.following || !s.contextId.equals(source.contextId) || !s.inboxId.equals(source.inboxId)) return s;
        InboxStore.Snapshot box = inbox.load();
        LocalV2.Snapshot before = local.load();
        Map<UUID, ImageState> localImages = new HashMap<>();
        for (TmmDraft draft : before.activeDrafts()) localImages.put(draft.uid, imageState(draft.imageRef));
        FeedSyncPlan plan = FeedSyncPlan.plan(s, box, receiptIds, feedImages, before, localImages);
        sync.replace(s, plan.nextSync);
        s = plan.nextSync;
        steps.reached(PLANNED);
        Set<UUID> conflicted = new HashSet<>();
        int changed = 0;
        for (FeedSyncPlan.Entry e : plan.entries) {
            if (!ok.getAsBoolean()) break;
            FeedSyncDecision d = e.decision;
            if (d.metadata == FeedSyncDecision.Metadata.CONFLICT || d.image == FeedSyncDecision.Image.CONFLICT
                    || d.image == FeedSyncDecision.Image.AMBIGUOUS) conflicted.add(e.deviceUid);
            try {
                FeedSyncState.Snapshot next = device(s, box, before, e, conflicted);
                if (next != s) changed++;
                s = next;
            } catch (Exception | OutOfMemoryError uncertain) {
                // Stale or uncertain: keep what is on disk; recovery resolves an adoption, the next fetch retries.
                s = sync.read();
                if (s == null) throw new IOException("Sync state disappeared");
            }
        }
        applied = changed;
        conflicts = Collections.unmodifiableSet(conflicted);
        unpublished = Collections.unmodifiableSet(unpublished(s, box));
        return s;
    }

    // ---- U3 resolution: the user decides one conflicted device (call only on the same serialized worker) ----

    enum Resolution { KEEP_LOCAL, USE_INCOMING }
    /** STALE: the state changed since the view was read, nothing was changed. NOT_APPLIED: refused or uncertain, see resolve. */
    enum Outcome { DONE, STALE, NOT_APPLIED }

    /** What a conflict view shows, with the exact state it was read from (expected-state binding). */
    static final class Conflict {
        final UUID deviceUid;
        final TmmDraft local, remote;
        final String localImageRef;
        /** Distinct incoming images: none, one, or (ambiguous) several the user must choose from. */
        final List<ImageAssociationRegistry.Record> candidates;
        final boolean ambiguous;
        private final UUID remoteReceipt, baseReceipt;
        private final int imageKind;
        private final byte[] imageDigest;
        private final long version;

        private Conflict(UUID uid, TmmDraft local, TmmDraft remote, String ref, List<ImageAssociationRegistry.Record> candidates,
                         boolean ambiguous, FeedSyncState.Record rec, long version) {
            deviceUid = uid; this.local = local; this.remote = remote; localImageRef = ref;
            this.candidates = Collections.unmodifiableList(candidates); this.ambiguous = ambiguous;
            remoteReceipt = rec.remoteReceiptId; baseReceipt = rec.baseReceiptId; imageKind = rec.imageKind;
            imageDigest = rec.imageDigest(); this.version = version;
        }
        private boolean sameState(Conflict o) {
            if (!remoteReceipt.equals(o.remoteReceipt) || !Objects.equals(baseReceipt, o.baseReceipt) || imageKind != o.imageKind
                    || !Arrays.equals(imageDigest, o.imageDigest) || version != o.version || ambiguous != o.ambiguous
                    || candidates.size() != o.candidates.size()) return false;
            for (int n = 0; n < candidates.size(); n++) if (!candidates.get(n).stageLocalId.equals(o.candidates.get(n).stageLocalId)) return false;
            return true;
        }
        private boolean offers(ImageAssociationRegistry.Record r) {
            for (ImageAssociationRegistry.Record c : candidates) if (c.stageLocalId.equals(r.stageLocalId)) return true;
            return false;
        }
        /** The one incoming image when it is unambiguous, else null. */
        ImageAssociationRegistry.Record incoming() { return !ambiguous && candidates.size() == 1 ? candidates.get(0) : null; }
    }

    private static final class Built {
        final FeedSyncPlan.Entry entry; final Conflict conflict; final InboxStore.Snapshot box; final LocalV2.Snapshot before;
        Built(FeedSyncPlan.Entry entry, Conflict conflict, InboxStore.Snapshot box, LocalV2.Snapshot before) {
            this.entry = entry; this.conflict = conflict; this.box = box; this.before = before;
        }
    }

    /** Reads the conflict of one device from the current files and the latest fetch, or null when it has none. */
    Conflict conflict(UUID uid) throws IOException {
        FeedSyncState.Snapshot s = sync.read();
        Built built = s == null ? null : build(s, uid);
        return built == null ? null : built.conflict;
    }

    /** The staged bytes of an incoming image, for a preview. */
    byte[] incomingImage(ImageAssociationRegistry.Record record) throws IOException { return stage.read(record.stageLocalId).image.bytes(); }

    private Built build(FeedSyncState.Snapshot s, UUID uid) throws IOException {
        FeedSyncState.Record rec = find(s, uid);
        if (rec == null || rec.remoteReceiptId == null) return null;
        InboxStore.Snapshot box = inbox.load();
        LocalV2.Snapshot before = local.load();
        LocalV2.State state = before.state(uid);
        // A device deleted locally has nothing to show or apply: it is never re-added from here.
        if (state.presence != Presence.ACTIVE) return null;
        FeedSyncPlan plan = FeedSyncPlan.plan(s, box, Collections.singletonList(rec.remoteReceiptId), lastImages, before,
                Collections.singletonMap(uid, imageState(state.draft.imageRef)));
        FeedSyncPlan.Entry entry = null;
        for (FeedSyncPlan.Entry e : plan.entries) if (e.deviceUid.equals(uid)) entry = e;
        if (entry == null || entry.remote == null) return null;
        FeedSyncDecision d = entry.decision;
        boolean ambiguous = d.image == FeedSyncDecision.Image.AMBIGUOUS;
        if (d.metadata != FeedSyncDecision.Metadata.CONFLICT && d.image != FeedSyncDecision.Image.CONFLICT && !ambiguous) return null;
        List<ImageAssociationRegistry.Record> current = new ArrayList<>(), all = new ArrayList<>();
        List<byte[]> currentDigests = new ArrayList<>(), allDigests = new ArrayList<>();
        for (ImageAssociationRegistry.Record r : lastImages) {
            if (!r.deviceUid.equals(uid)) continue;
            all.add(r); allDigests.add(r.sha256());
            if (rec.remoteReceiptId.equals(r.receiptId)) { current.add(r); currentDigests.add(r.sha256()); }
        }
        List<ImageAssociationRegistry.Record> pool = distinct(current.isEmpty() ? all : current);
        ImageState base = rec.imageKind == FeedSyncState.IMAGE_UNKNOWN ? ImageState.UNKNOWN
                : rec.imageKind == FeedSyncState.IMAGE_NONE ? ImageState.NONE : ImageState.of(rec.imageDigest());
        ImageState h = FeedSyncDecision.remoteImage(currentDigests, allDigests, base);
        List<ImageAssociationRegistry.Record> candidates = new ArrayList<>();
        if (ambiguous) candidates = pool;
        else for (ImageAssociationRegistry.Record r : pool) if (h != null && ImageState.of(r.sha256()).sameAs(h)) { candidates.add(r); break; }
        return new Built(entry, new Conflict(uid, state.draft, entry.remote, state.draft.imageRef, candidates, ambiguous, rec, state.version), box, before);
    }

    private static List<ImageAssociationRegistry.Record> distinct(List<ImageAssociationRegistry.Record> records) {
        List<ImageAssociationRegistry.Record> result = new ArrayList<>();
        outer:
        for (ImageAssociationRegistry.Record r : records) {
            for (ImageAssociationRegistry.Record seen : result) if (MessageDigest.isEqual(seen.sha256(), r.sha256())) continue outer;
            result.add(r);
        }
        return result;
    }

    /**
     * Behåll min: base := the remote receipt and the image base := the incoming image (or none); nothing local changes.
     * With an AMBIGUOUS image only the metadata base moves, the image stays in conflict until U4 clears it.
     * Använd inkommande: applies the remote metadata and the incoming image (the picked candidate when ambiguous)
     * through the same code as an automatic APPLY; refused, with nothing changed, when the remote edit time is
     * earlier than the local one (the store cannot move a time backwards, so L = R is unreachable).
     * Both recheck the state the view was read from and return STALE if it differs. Nothing is ever sent (U8).
     */
    Outcome resolve(Conflict seen, Resolution how, ImageAssociationRegistry.Record pick) {
        UUID uid = seen.deviceUid;
        try {
            FeedSyncState.Snapshot s = recover();
            Built built = s == null ? null : build(s, uid);
            if (built == null || !built.conflict.sameState(seen)) return Outcome.STALE;
            Conflict c = built.conflict;
            FeedSyncState.Record rec = find(s, uid);
            if (how == Resolution.KEEP_LOCAL) {
                ImageAssociationRegistry.Record image = c.incoming();
                int kind = c.ambiguous ? rec.imageKind : image == null ? FeedSyncState.IMAGE_NONE : FeedSyncState.IMAGE_DIGEST;
                byte[] digest = c.ambiguous ? rec.imageDigest() : image == null ? null : image.sha256();
                s = put(s, record(rec, rec.remoteReceiptId, kind, digest, null, null));
                refresh(s, built.box, c.ambiguous ? null : uid);
                return Outcome.DONE;
            }
            ImageAssociationRegistry.Record image = c.ambiguous ? pick : c.incoming();
            if (c.ambiguous && (pick == null || !c.offers(pick))) return Outcome.NOT_APPLIED;
            if (built.entry.decision.metadata == FeedSyncDecision.Metadata.ADOPT_BASE) {
                s = put(s, record(rec, rec.remoteReceiptId, rec.imageKind, rec.imageDigest(), null, null));
            }
            boolean imageDiffers = image != null && !MessageDigest.isEqual(digestOf(c.localImageRef), image.sha256());
            Set<UUID> failed = new HashSet<>();
            s = device(s, built.box, built.before, built.entry, failed, true, imageDiffers ? image : null);
            LocalV2.State now = local.load().state(uid);
            if (!failed.isEmpty() || now.presence != Presence.ACTIVE || !same(now.draft, c.remote)
                    || image != null && !MessageDigest.isEqual(digestOf(now.draft.imageRef), image.sha256())) {
                refresh(s, built.box, null);
                return Outcome.NOT_APPLIED;
            }
            if (image != null && !imageDiffers) {
                FeedSyncState.Record r = find(s, uid);
                s = put(s, record(r, r.baseReceiptId, FeedSyncState.IMAGE_DIGEST, image.sha256(), null, null));
            }
            refresh(s, built.box, uid);
            return Outcome.DONE;
        } catch (Exception | OutOfMemoryError uncertain) {
            return Outcome.NOT_APPLIED;   // recovery resolves an unconfirmed adoption on the next load
        }
    }

    private void refresh(FeedSyncState.Snapshot s, InboxStore.Snapshot box, UUID resolved) throws IOException {
        unpublished = Collections.unmodifiableSet(unpublished(s, box));
        if (resolved != null) {
            Set<UUID> rest = new HashSet<>(conflicts);
            rest.remove(resolved);
            conflicts = Collections.unmodifiableSet(rest);
        }
    }

    private byte[] digestOf(String reference) { return reference == null ? sha256(new byte[0]) : digest(reference); }

    private FeedSyncState.Snapshot device(FeedSyncState.Snapshot s, InboxStore.Snapshot box, LocalV2.Snapshot before,
                                          FeedSyncPlan.Entry e, Set<UUID> conflicted) throws Exception {
        return device(s, box, before, e, conflicted, false, null);
    }

    /** {@code force}: the user chose Använd inkommande, so a metadata CONFLICT is applied and {@code forcedImage} replaces the image. */
    private FeedSyncState.Snapshot device(FeedSyncState.Snapshot s, InboxStore.Snapshot box, LocalV2.Snapshot before,
                                          FeedSyncPlan.Entry e, Set<UUID> conflicted, boolean force,
                                          ImageAssociationRegistry.Record forcedImage) throws Exception {
        FeedSyncState.Record rec = find(s, e.deviceUid);
        if (rec == null || e.remote == null) return s;
        LocalV2.State expected = e.local;
        // Forced: a metadata CONFLICT, or a NOTHING (R = B) that still differs from the local edits, takes the remote values.
        boolean takeRemote = force && expected.presence == Presence.ACTIVE && (e.decision.metadata == FeedSyncDecision.Metadata.CONFLICT
                || e.decision.metadata == FeedSyncDecision.Metadata.NOTHING && !same(expected.draft, e.remote));
        FeedSyncDecision.Metadata metadata = takeRemote ? FeedSyncDecision.Metadata.APPLY : e.decision.metadata;
        if (metadata == FeedSyncDecision.Metadata.ADD) {
            // D: Add starts without an image; a known local deletion is never re-added automatically.
            if (expected.presence != Presence.NEVER_SEEN) return s;
            ImportDecisionPreparation add = ImportDecisionPreparation.addLocally(box, rec.remoteReceiptId, before, false);
            LocalV2Store.Result done = local.commit(inbox, add.prepare(box, before));
            expected = readBack(e.deviceUid, done.current.version, e.remote, null);
            // The added device has no image, so its image base is "none" until an image is adopted.
            s = put(s, record(rec, rec.remoteReceiptId, FeedSyncState.IMAGE_NONE, null, null, null));
        } else if (metadata == FeedSyncDecision.Metadata.APPLY) {
            TmmDraft l = expected.draft, r = e.remote;
            // L must equal R afterwards; a different creation identity or an earlier remote edit time cannot.
            if (!l.createdAt.equals(r.createdAt) || r.updatedAt.isBefore(l.updatedAt) || l.revision == Long.MAX_VALUE) {
                conflicted.add(e.deviceUid);
                return s;
            }
            TmmDraft next = new TmmDraft(l.uid, r.displayId, r.type, r.status, r.owner, l.createdAt, r.updatedAt,
                    r.deployedAt, r.comment, r.position, r.bearingDeg, l.imageRef, l.revision + 1);
            LocalV2.State saved = local.save(before.storeId, expected, next);
            expected = readBack(e.deviceUid, saved.version, r, l.imageRef);
            s = put(s, record(rec, rec.remoteReceiptId, rec.imageKind, rec.imageDigest(), null, null));
        }
        if (forcedImage != null) s = image(s, before.storeId, expected, e.deviceUid, forcedImage);
        else if (!force && e.decision.image == FeedSyncDecision.Image.APPLY) s = image(s, before.storeId, expected, e.deviceUid, e.remoteImage);
        return s;
    }

    /** U5 steps 1–4. Any failure after step 2 leaves adoptNew/adoptOld for {@link #recover()}. */
    private FeedSyncState.Snapshot image(FeedSyncState.Snapshot s, UUID storeId, LocalV2.State expected,
                                         UUID deviceUid, ImageAssociationRegistry.Record remoteImage) throws Exception {
        LocalV2.Snapshot fresh = local.load();
        LocalV2.State state = fresh.state(deviceUid);
        if (!fresh.storeId.equals(storeId) || state.presence != Presence.ACTIVE || state.version != expected.version) return s;
        ReceivedImagePayload payload = stage.read(remoteImage.stageLocalId);
        if (!MessageDigest.isEqual(payload.image.identity.sha256(), remoteImage.sha256())) {
            throw new IOException("Staged image does not match its association");
        }
        LocalV2.Selection selected = fresh.select(deviceUid);
        String old = selected.draft.imageRef;
        try (LocalImages.Pending pending = images.prepareReceived(payload.image)) {   // reads the copy back
            steps.reached(PREPARED);
            FeedSyncState.Record rec = find(s, deviceUid);
            s = put(s, record(rec, rec.baseReceiptId, rec.imageKind, rec.imageDigest(), id(pending.reference()), old == null ? null : id(old)));
            steps.reached(ADOPT_WRITTEN);
            // The local updatedAt is kept: after the metadata step it is R's whenever L's metadata equals R,
            // and otherwise an image adoption must not change unpublished local metadata.
            attachments.attach(selected, pending, selected.draft.updatedAt);
            steps.reached(ATTACHED);
            return finish(s, find(s, deviceUid));
        }
    }

    /** Step 4: the draft refers to adoptNew. Set the image base, clear the adoption, delete the old copy if unused. */
    private FeedSyncState.Snapshot finish(FeedSyncState.Snapshot s, FeedSyncState.Record r) throws IOException {
        String adopted = reference(r.adoptNew);
        LocalV2.State state = local.load().state(r.deviceUid);
        if (state.presence != Presence.ACTIVE || !adopted.equals(state.draft.imageRef)) throw new IOException("Adoption not confirmed");
        s = put(s, record(r, r.baseReceiptId, FeedSyncState.IMAGE_DIGEST, sha256(images.read(adopted)), null, null));
        steps.reached(BASE_SET);
        if (r.adoptOld != null && !referenced(reference(r.adoptOld))) images.deleteUnreferenced(reference(r.adoptOld));
        return s;
    }

    private LocalV2.State readBack(UUID uid, long version, TmmDraft remote, String imageRef) throws IOException {
        LocalV2.State state = local.load().state(uid);
        if (state.presence != Presence.ACTIVE || state.version != version || !same(state.draft, remote)
                || !Objects.equals(state.draft.imageRef, imageRef)) throw new IOException("Local commit not confirmed");
        return state;
    }

    private Set<UUID> unpublished(FeedSyncState.Snapshot s, InboxStore.Snapshot box) throws IOException {
        Map<UUID, TmmDraft> receipts = new HashMap<>();
        for (InboxStore.Receipt r : box.receipts) receipts.put(r.id, r.incoming);
        LocalV2.Snapshot now = local.load();
        Set<UUID> result = new HashSet<>();
        for (FeedSyncState.Record r : s.records) {
            TmmDraft base = r.baseReceiptId == null ? null : receipts.get(r.baseReceiptId);
            LocalV2.State state = now.state(r.deviceUid);
            if (base == null || state.presence != Presence.ACTIVE) continue;
            boolean imageDiffers = r.imageKind == FeedSyncState.IMAGE_NONE ? state.draft.imageRef != null
                    : r.imageKind == FeedSyncState.IMAGE_DIGEST
                    && (state.draft.imageRef == null || !MessageDigest.isEqual(digest(state.draft.imageRef), r.imageDigest()));
            if (!same(state.draft, base) || imageDiffers) result.add(r.deviceUid);
        }
        return result;
    }

    private ImageState imageState(String reference) { return reference == null ? ImageState.NONE : ImageState.of(digest(reference)); }
    private byte[] digest(String reference) {
        byte[] known = digests.get(reference);
        if (known != null) return known;
        try {
            byte[] value = sha256(images.read(reference));
            digests.put(reference, value);
            return value;
        } catch (IOException | RuntimeException unreadable) {
            return UNREADABLE;
        }
    }
    private boolean referenced(String reference) throws IOException {
        for (TmmDraft draft : local.load().activeDrafts()) if (reference.equals(draft.imageRef)) return true;
        return false;
    }
    private FeedSyncState.Snapshot put(FeedSyncState.Snapshot s, FeedSyncState.Record next) throws IOException {
        List<FeedSyncState.Record> records = new ArrayList<>();
        for (FeedSyncState.Record r : s.records) records.add(r.deviceUid.equals(next.deviceUid) ? next : r);
        FeedSyncState.Snapshot updated = new FeedSyncState.Snapshot(s.syncId, s.inboxId, s.following, s.contextId,
                s.feedName, s.streamConnect, records);
        sync.replace(s, updated);
        return updated;
    }
    private static FeedSyncState.Record find(FeedSyncState.Snapshot s, UUID uid) {
        for (FeedSyncState.Record r : s.records) if (r.deviceUid.equals(uid)) return r;
        return null;
    }
    private static FeedSyncState.Record record(FeedSyncState.Record r, UUID base, int kind, byte[] digest, UUID adoptNew, UUID adoptOld) {
        return new FeedSyncState.Record(r.deviceUid, r.remoteReceiptId, base, kind, digest, adoptNew, adoptOld);
    }
    private static boolean same(TmmDraft local, TmmDraft remote) {
        return ImportReview.assess(Collections.singletonList(local), remote).kind == ImportReview.Kind.SAME_METADATA;
    }
    private static String reference(UUID id) { return id + ".png"; }
    private static UUID id(String reference) { return UUID.fromString(reference.substring(0, reference.length() - 4)); }
    static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
}
