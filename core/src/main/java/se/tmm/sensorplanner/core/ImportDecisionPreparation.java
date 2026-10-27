package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static se.tmm.sensorplanner.core.LocalV2.*;

/** One-use in-memory import review; no file operations, history insertion or application. */
public final class ImportDecisionPreparation implements AutoCloseable {
    public enum Field {
        DISPLAY_ID(1), TYPE(2), STATUS(4), OWNER(8), COMMENT(16), DEPLOYED_AT(32), POSITION(64), BEARING(128);
        public final int mask;
        Field(int mask) { this.mask = mask; }
    }
    public enum Effect { ADD_LOCAL, UPDATE_LOCAL, KEEP_LOCAL, ALREADY_EQUAL }

    public final Plan preview;
    private boolean pending = true;

    private ImportDecisionPreparation(Plan preview) { this.preview = preview; }

    public static ImportDecisionPreparation addLocally(InboxStore.Snapshot inbox, UUID receiptId,
                                                       Snapshot local, boolean confirmReAdd) throws IOException {
        return review(inbox, receiptId, local, LocalV2.Action.ADD_LOCAL, 0, null, confirmReAdd);
    }
    public static ImportDecisionPreparation keepLocal(InboxStore.Snapshot inbox, UUID receiptId,
                                                      Snapshot local) throws IOException {
        return review(inbox, receiptId, local, LocalV2.Action.KEEP_LOCAL, 0, null, false);
    }
    public static ImportDecisionPreparation useSelected(InboxStore.Snapshot inbox, UUID receiptId, Snapshot local,
                                                        Set<Field> fields, Instant localEditTime) throws IOException {
        Objects.requireNonNull(fields, "fields"); int mask = 0;
        for (Field field : fields) mask |= Objects.requireNonNull(field, "field").mask;
        if (mask == 0) throw new IllegalArgumentException("Select received fields explicitly");
        return review(inbox, receiptId, local, LocalV2.Action.USE_SELECTED, mask, localEditTime, false);
    }

    private static ImportDecisionPreparation review(InboxStore.Snapshot inbox, UUID receiptId, Snapshot local,
                                                     LocalV2.Action action, int mask, Instant time, boolean confirmReAdd) throws IOException {
        Objects.requireNonNull(receiptId, "receiptId"); validate(inbox, local);
        InboxStore.Receipt receipt = receipt(inbox, receiptId);
        if (receipt == null) throw new IllegalArgumentException("Selected receipt is unavailable");
        State before = state(local, receipt.incoming.uid), result = before;
        TmmDraft incoming = receipt.incoming;
        Effect effect;
        Instant editTime = null;
        if (action == LocalV2.Action.ADD_LOCAL) {
            if (before.presence == Presence.ACTIVE) throw new IllegalArgumentException("Adding locally requires an absent UUID");
            if (before.presence == Presence.DELETED && !confirmReAdd) {
                throw new IllegalArgumentException("Known local deletion requires explicit re-add confirmation");
            }
            result = State.active(nextVersion(before.version), LocalV2Binary.local(incoming, null, 1));
            effect = Effect.ADD_LOCAL;
        } else {
            if (before.presence != Presence.ACTIVE) throw new IllegalArgumentException("Existing local draft required");
            if (ImportReview.sameMetadata(before.draft, incoming)) {
                effect = Effect.ALREADY_EQUAL;
            } else if (action == LocalV2.Action.KEEP_LOCAL) {
                effect = Effect.KEEP_LOCAL;
            } else {
                if (!before.draft.createdAt.equals(incoming.createdAt)) {
                    throw new IllegalArgumentException("Received creation identity differs; review identity");
                }
                TmmDraft chosen = LocalV2Codec.selected(before.draft, incoming, mask, before.draft.updatedAt);
                if (ImportReview.sameMetadata(before.draft, chosen)) {
                    effect = Effect.KEEP_LOCAL;
                } else {
                    if (time == null) throw new IllegalArgumentException("Local edit time required");
                    TmmDraft edited = before.draft.edit(chosen.displayId, chosen.type, chosen.status, chosen.owner,
                            chosen.deployedAt, chosen.comment, chosen.position, chosen.bearingDeg, before.draft.imageRef, time);
                    result = State.active(nextVersion(before.version), edited);
                    editTime = time; effect = Effect.UPDATE_LOCAL;
                }
            }
        }
        return new ImportDecisionPreparation(new Plan(local.storeId, inbox.inboxId, receipt, action, before,
                result, LocalStateFingerprint.digest(local.storeId, before), mask, editTime, effect));
    }

    /** Consumes even a failed attempt; later application still needs atomic current-state validation. */
    public synchronized Prepared prepare(InboxStore.Snapshot currentInbox, Snapshot currentLocal) throws IOException {
        if (!pending) throw new IllegalStateException("Import preparation is no longer pending");
        pending = false;
        preview.requireCurrent(currentInbox, currentLocal);
        return new Prepared(preview);
    }
    @Override public synchronized void close() { pending = false; }

    private static long nextVersion(long version) {
        if (version == Long.MAX_VALUE) throw new IllegalArgumentException("Local state version is exhausted");
        return version + 1;
    }
    private static void validate(InboxStore.Snapshot inbox, Snapshot local) throws IOException {
        // Reuse complete bounded validation without opening or changing either store.
        InboxCodec.encode(Objects.requireNonNull(inbox, "inbox"));
        LocalV2Codec.encode(Objects.requireNonNull(local, "local"));
    }
    private static State state(Snapshot snapshot, UUID uid) {
        for (State state : snapshot.slots) if (state.uid.equals(uid)) return state;
        return State.neverSeen(uid);
    }
    private static InboxStore.Receipt receipt(InboxStore.Snapshot inbox, UUID id) {
        for (InboxStore.Receipt receipt : inbox.receipts) if (receipt.id.equals(id)) return receipt;
        return null;
    }
    private static boolean sameReceipt(InboxStore.Receipt before, InboxStore.Receipt current) {
        return current != null && before.id.equals(current.id) && before.contextId.equals(current.contextId)
                && before.firstObservedAt.equals(current.firstObservedAt) && before.incoming.uid.equals(current.incoming.uid)
                && ImportReview.sameMetadata(before.incoming, current.incoming);
    }
    private static boolean sameState(State before, State current) {
        if (before.presence != current.presence || before.version != current.version || !before.uid.equals(current.uid)) return false;
        if (before.draft == null) return current.draft == null;
        return current.draft != null && before.draft.revision == current.draft.revision
                && Objects.equals(before.draft.imageRef, current.draft.imageRef)
                && ImportReview.sameMetadata(before.draft, current.draft);
    }

    /** Returned only after successful one-use preparation; still no durable consent or commit. */
    public static final class Prepared {
        public final Plan plan;
        private Prepared(Plan plan) { this.plan = plan; }
        public void requireCurrent(InboxStore.Snapshot currentInbox, Snapshot currentLocal) throws IOException {
            plan.requireCurrent(currentInbox, currentLocal);
        }
    }

    /** Immutable exact preview/binding only; not committed history, durable consent or an apply token. */
    public static final class Plan {
        public final UUID operationId, storeId, inboxId;
        public final InboxStore.Receipt receipt;
        public final LocalV2.Action action;
        public final State reviewed, result;
        public final int fieldMask;
        /** Null for Add, Keep and unchanged selections; real edits bind the exact previewed Instant. */
        public final Instant editTime;
        public final Effect effect;
        private final byte[] fingerprint;

        private Plan(UUID storeId, UUID inboxId, InboxStore.Receipt receipt, LocalV2.Action action,
                     State reviewed, State result, byte[] fingerprint, int fieldMask, Instant editTime, Effect effect) {
            this.operationId = UUID.randomUUID(); this.storeId = storeId; this.inboxId = inboxId;
            this.receipt = receipt; this.action = action; this.reviewed = reviewed; this.result = result;
            this.fingerprint = fingerprint.clone(); this.fieldMask = fieldMask; this.editTime = editTime; this.effect = effect;
        }
        public byte[] reviewedFingerprint() { return fingerprint.clone(); }
        public boolean isReAdd() { return action == LocalV2.Action.ADD_LOCAL && reviewed.presence == Presence.DELETED; }
        public boolean needsDecisionRecord() { return effect != Effect.ALREADY_EQUAL; }

        /** Pure supplied-snapshot check; a future transaction must repeat it under the owning locks. */
        private void requireCurrent(InboxStore.Snapshot currentInbox, Snapshot currentLocal) throws IOException {
            validate(currentInbox, currentLocal);
            State current = state(currentLocal, reviewed.uid);
            if (!storeId.equals(currentLocal.storeId) || !inboxId.equals(currentInbox.inboxId)
                    || !sameReceipt(receipt, receipt(currentInbox, receipt.id)) || !sameState(reviewed, current)
                    || !MessageDigest.isEqual(fingerprint, LocalStateFingerprint.digest(currentLocal.storeId, current))) {
                throw new IllegalStateException("Import review changed; review again");
            }
        }
    }
}
