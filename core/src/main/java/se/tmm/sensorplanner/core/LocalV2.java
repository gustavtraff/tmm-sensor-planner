package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/** Immutable values for the approved private TMMD v2 frame; no store or application operations. */
public final class LocalV2 {
    private LocalV2() { }

    public enum Presence {
        NEVER_SEEN(0), ACTIVE(1), DELETED(2);
        final int code;
        Presence(int code) { this.code = code; }
    }
    public enum Action {
        ADD_LOCAL(1), USE_SELECTED(2), KEEP_LOCAL(3);
        final int code;
        Action(int code) { this.code = code; }
    }
    public enum Outcome {
        ADDED(1), UPDATED(2), KEPT(3);
        final int code;
        Outcome(int code) { this.code = code; }
    }

    public static final class State {
        public final UUID uid;
        public final Presence presence;
        public final long version;
        /** Full local draft for ACTIVE, otherwise null. History uses a metadata-only image association. */
        public final TmmDraft draft;

        private State(UUID uid, Presence presence, long version, TmmDraft draft) {
            this.uid = Objects.requireNonNull(uid, "uid");
            this.presence = presence;
            if (presence == Presence.NEVER_SEEN ? version != 0 : version < 1) {
                throw new IllegalArgumentException("Invalid local state version");
            }
            this.version = version;
            this.draft = draft;
        }
        public static State neverSeen(UUID uid) { return new State(uid, Presence.NEVER_SEEN, 0, null); }
        public static State deleted(UUID uid, long version) { return new State(uid, Presence.DELETED, version, null); }
        public static State active(long version, TmmDraft draft) {
            Objects.requireNonNull(draft, "draft");
            return new State(draft.uid, Presence.ACTIVE, version, draft);
        }
    }

    public static final class Snapshot {
        public final UUID storeId;
        public final List<State> slots;
        public final List<Decision> decisions;

        public Snapshot(UUID storeId, List<State> slots, List<Decision> decisions) {
            this.storeId = Objects.requireNonNull(storeId, "storeId");
            this.slots = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(slots, "slots")));
            this.decisions = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(decisions, "decisions")));
        }
        public State state(UUID uid) {
            Objects.requireNonNull(uid, "uid");
            for (State state : slots) if (state.uid.equals(uid)) return state;
            return State.neverSeen(uid);
        }
        public List<TmmDraft> activeDrafts() {
            List<TmmDraft> drafts = new ArrayList<>();
            for (State state : slots) if (state.presence == Presence.ACTIVE) drafts.add(state.draft);
            return Collections.unmodifiableList(drafts);
        }
        public Selection select(UUID uid) { return new Selection(storeId, state(uid)); }
    }

    /** Captured when a UI action opens, never reconstructed from a fresh revision at save time. */
    public static final class Selection {
        public final UUID storeId;
        public final State state;
        public final TmmDraft draft;
        private Selection(UUID storeId, State state) {
            this.storeId = storeId; this.state = state; this.draft = state.draft;
        }
        public boolean isCurrent(Snapshot current) throws IOException {
            State now = current.state(state.uid);
            return storeId.equals(current.storeId) && state.presence == now.presence && state.version == now.version
                    && MessageDigest.isEqual(LocalStateFingerprint.digest(storeId, state), LocalStateFingerprint.digest(storeId, now));
        }
    }

    public static final class Decision {
        public final UUID id, target, inboxId, receiptId;
        public final Action action;
        public final String contextId;
        public final Instant firstObservedAt, editTime;
        /** Metadata carriers: imageRef null and revision 1; counters live in their explicit fields. */
        public final TmmDraft incoming, result;
        /** Active history carries the observed revision in draft.revision, but no raw image handle. */
        public final State before;
        public final int fieldMask;
        public final Outcome outcome;
        public final long afterVersion, afterRevision;
        private final byte[] reviewedFingerprint;

        public Decision(UUID id, Action action, UUID target, UUID inboxId, UUID receiptId, String contextId,
                        Instant firstObservedAt, TmmDraft incoming, State before, byte[] reviewedFingerprint,
                        int fieldMask, Instant editTime, Outcome outcome, long afterVersion, long afterRevision,
                        TmmDraft result) {
            this.id = Objects.requireNonNull(id, "id");
            this.action = Objects.requireNonNull(action, "action");
            this.target = Objects.requireNonNull(target, "target");
            this.inboxId = Objects.requireNonNull(inboxId, "inboxId");
            this.receiptId = Objects.requireNonNull(receiptId, "receiptId");
            this.contextId = Objects.requireNonNull(contextId, "contextId");
            this.firstObservedAt = Objects.requireNonNull(firstObservedAt, "firstObservedAt");
            this.incoming = Objects.requireNonNull(incoming, "incoming");
            this.before = Objects.requireNonNull(before, "before");
            Objects.requireNonNull(reviewedFingerprint, "reviewedFingerprint");
            if (reviewedFingerprint.length != 32) throw new IllegalArgumentException("Invalid local fingerprint length");
            this.reviewedFingerprint = reviewedFingerprint.clone();
            this.fieldMask = fieldMask;
            this.editTime = editTime;
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.afterVersion = afterVersion;
            this.afterRevision = afterRevision;
            this.result = Objects.requireNonNull(result, "result");
        }
        public byte[] reviewedFingerprint() { return reviewedFingerprint.clone(); }
    }
}
