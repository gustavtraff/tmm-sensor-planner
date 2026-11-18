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

    private FeedSyncState.Snapshot device(FeedSyncState.Snapshot s, InboxStore.Snapshot box, LocalV2.Snapshot before,
                                          FeedSyncPlan.Entry e, Set<UUID> conflicted) throws Exception {
        FeedSyncState.Record rec = find(s, e.deviceUid);
        if (rec == null || e.remote == null) return s;
        LocalV2.State expected = e.local;
        if (e.decision.metadata == FeedSyncDecision.Metadata.ADD) {
            // D: Add starts without an image; a known local deletion is never re-added automatically.
            if (expected.presence != Presence.NEVER_SEEN) return s;
            ImportDecisionPreparation add = ImportDecisionPreparation.addLocally(box, rec.remoteReceiptId, before, false);
            LocalV2Store.Result done = local.commit(inbox, add.prepare(box, before));
            expected = readBack(e.deviceUid, done.current.version, e.remote, null);
            // The added device has no image, so its image base is "none" until an image is adopted.
            s = put(s, record(rec, rec.remoteReceiptId, FeedSyncState.IMAGE_NONE, null, null, null));
        } else if (e.decision.metadata == FeedSyncDecision.Metadata.APPLY) {
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
        if (e.decision.image == FeedSyncDecision.Image.APPLY) s = image(s, before.storeId, expected, e);
        return s;
    }

    /** U5 steps 1–4. Any failure after step 2 leaves adoptNew/adoptOld for {@link #recover()}. */
    private FeedSyncState.Snapshot image(FeedSyncState.Snapshot s, UUID storeId, LocalV2.State expected,
                                         FeedSyncPlan.Entry e) throws Exception {
        LocalV2.Snapshot fresh = local.load();
        LocalV2.State state = fresh.state(e.deviceUid);
        if (!fresh.storeId.equals(storeId) || state.presence != Presence.ACTIVE || state.version != expected.version) return s;
        ReceivedImagePayload payload = stage.read(e.remoteImage.stageLocalId);
        if (!MessageDigest.isEqual(payload.image.identity.sha256(), e.remoteImage.sha256())) {
            throw new IOException("Staged image does not match its association");
        }
        LocalV2.Selection selected = fresh.select(e.deviceUid);
        String old = selected.draft.imageRef;
        try (LocalImages.Pending pending = images.prepareReceived(payload.image)) {   // reads the copy back
            steps.reached(PREPARED);
            FeedSyncState.Record rec = find(s, e.deviceUid);
            s = put(s, record(rec, rec.baseReceiptId, rec.imageKind, rec.imageDigest(), id(pending.reference()), old == null ? null : id(old)));
            steps.reached(ADOPT_WRITTEN);
            // The local updatedAt is kept: after the metadata step it is R's whenever L's metadata equals R,
            // and otherwise an image adoption must not change unpublished local metadata.
            attachments.attach(selected, pending, selected.draft.updatedAt);
            steps.reached(ATTACHED);
            return finish(s, find(s, e.deviceUid));
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
