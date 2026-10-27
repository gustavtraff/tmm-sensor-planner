package se.tmm.sensorplanner.core;

import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static se.tmm.sensorplanner.core.LocalV2.*;
import static se.tmm.sensorplanner.core.LocalV2Binary.*;

/** Bounded in-memory TMMD v2 codec; never reads/writes files, migrates or applies data. */
public final class LocalV2Codec {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_SLOTS = 1000, MAX_DECISIONS = 1000;
    private LocalV2Codec() { }

    public static byte[] encode(Snapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            validate(snapshot);
            Buffer buffer = new Buffer(MAX_BYTES - 32);
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(0x544d4d44); out.writeInt(2); uuid(out, snapshot.storeId); out.writeInt(snapshot.slots.size());
                for (State state : snapshot.slots) {
                    uuid(out, state.uid); out.writeLong(state.version); out.writeByte(state.presence.code);
                    if (state.presence == Presence.ACTIVE) local(out, state.draft, false);
                }
                out.writeInt(snapshot.decisions.size());
                for (Decision decision : snapshot.decisions) writeDecision(out, decision);
            }
            byte[] payload = buffer.bytes();
            byte[] result = Arrays.copyOf(payload, payload.length + 32);
            System.arraycopy(sha(payload), 0, result, payload.length, 32); return result;
        } catch (IOException | IllegalArgumentException | java.time.DateTimeException | NullPointerException e) {
            throw invalid();
        }
    }

    public static Snapshot decode(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < 64 || bytes.length > MAX_BYTES) throw invalid();
        byte[] input = bytes.clone();
        int payloadLength = input.length - 32;
        if (!MessageDigest.isEqual(sha(input, payloadLength), Arrays.copyOfRange(input, payloadLength, input.length))) throw invalid();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(input, 0, payloadLength))) {
            if (in.readInt() != 0x544d4d44 || in.readInt() != 2) throw invalid();
            UUID storeId = uuid(in); int slotCount = count(in);
            List<State> slots = new ArrayList<>();
            for (int i = 0; i < slotCount; i++) {
                UUID uid = uuid(in); long version = positive(in.readLong()); int tag = code(in, 1, 2);
                if (tag == 1) {
                    TmmDraft metadata = body(in, uid); String image = flag(in) ? required(in) : null;
                    slots.add(State.active(version, local(metadata, image, positive(in.readLong()))));
                } else slots.add(State.deleted(uid, version));
            }
            int decisionCount = count(in); List<Decision> decisions = new ArrayList<>();
            for (int i = 0; i < decisionCount; i++) decisions.add(readDecision(in));
            if (in.available() != 0) throw invalid();
            Snapshot result = new Snapshot(storeId, slots, decisions); validate(result); return result;
        } catch (IOException | IllegalArgumentException | java.time.DateTimeException | NullPointerException e) {
            throw invalid();
        }
    }

    private static Decision readDecision(DataInputStream in) throws IOException {
        UUID id = uuid(in); Action action;
        switch (code(in, 1, 3)) {
            case 1: action = Action.ADD_LOCAL; break;
            case 2: action = Action.USE_SELECTED; break;
            default: action = Action.KEEP_LOCAL;
        }
        UUID target = uuid(in), inbox = uuid(in), receipt = uuid(in);
        String context = required(in); Instant observed = instant(in); TmmDraft incoming = metadata(in);
        int tag = code(in, 0, 2); long version = in.readLong(); State before;
        if (tag == 1) {
            long revision = positive(in.readLong()); TmmDraft metadata = metadata(in);
            before = State.active(version, local(metadata, null, revision));
        } else if (tag == 0) {
            if (version != 0) throw invalid(); before = State.neverSeen(target);
        } else before = State.deleted(target, version);
        byte[] fingerprint = new byte[32]; in.readFully(fingerprint);
        int mask = in.readInt(); Instant editTime = flag(in) ? instant(in) : null; Outcome outcome;
        switch (code(in, 1, 3)) {
            case 1: outcome = Outcome.ADDED; break;
            case 2: outcome = Outcome.UPDATED; break;
            default: outcome = Outcome.KEPT;
        }
        long afterVersion = positive(in.readLong()), afterRevision = positive(in.readLong());
        return new Decision(id, action, target, inbox, receipt, context, observed, incoming, before, fingerprint,
                mask, editTime, outcome, afterVersion, afterRevision, metadata(in));
    }

    private static void writeDecision(DataOutputStream out, Decision d) throws IOException {
        uuid(out, d.id); out.writeByte(d.action.code); uuid(out, d.target); uuid(out, d.inboxId); uuid(out, d.receiptId);
        text(out, d.contextId); instant(out, d.firstObservedAt); metadata(out, d.incoming);
        out.writeByte(d.before.presence.code); out.writeLong(d.before.version);
        if (d.before.presence == Presence.ACTIVE) { out.writeLong(d.before.draft.revision); metadata(out, d.before.draft); }
        out.write(d.reviewedFingerprint()); out.writeInt(d.fieldMask); out.writeBoolean(d.editTime != null);
        if (d.editTime != null) instant(out, d.editTime);
        out.writeByte(d.outcome.code); out.writeLong(d.afterVersion); out.writeLong(d.afterRevision); metadata(out, d.result);
    }

    private static void validate(Snapshot snapshot) throws IOException {
        if (snapshot.slots.size() > MAX_SLOTS || snapshot.decisions.size() > MAX_DECISIONS) throw invalid();
        Map<UUID, State> slots = new HashMap<>();
        for (State state : snapshot.slots) {
            if (state.presence == Presence.NEVER_SEEN || slots.put(state.uid, state) != null) throw invalid();
            if (state.draft != null) validateDraft(state.draft);
        }
        Set<UUID> ids = new HashSet<>(); Map<UUID, Decision> last = new HashMap<>(); Map<String, Decision> receipts = new HashMap<>();
        for (Decision d : snapshot.decisions) {
            if (!ids.add(d.id)) throw invalid();
            validateText(d.contextId, true); metadataOnly(d.incoming, true); metadataOnly(d.result, true);
            if (d.before.draft != null) metadataOnly(d.before.draft, false);
            if (!d.target.equals(d.before.uid) || !d.target.equals(d.incoming.uid) || !d.target.equals(d.result.uid)
                    || d.fieldMask < 0 || d.fieldMask > 255 || d.afterVersion < 1 || d.afterRevision < 1) throw invalid();
            State current = slots.get(d.target);
            if (current == null || current.version < d.afterVersion) throw invalid();
            validateTransition(d);
            if (d.before.presence != Presence.ACTIVE && !MessageDigest.isEqual(d.reviewedFingerprint(),
                    LocalStateFingerprint.digest(snapshot.storeId, d.before))) throw invalid();
            if (current.version == d.afterVersion) {
                if (current.presence != Presence.ACTIVE || current.draft.revision != d.afterRevision || !same(current.draft, d.result)) throw invalid();
                if (d.action == Action.ADD_LOCAL && current.draft.imageRef != null) throw invalid();
                if (d.before.presence == Presence.ACTIVE) {
                    State reviewed = State.active(d.before.version,
                            local(d.before.draft, current.draft.imageRef, d.before.draft.revision));
                    if (!MessageDigest.isEqual(d.reviewedFingerprint(), LocalStateFingerprint.digest(snapshot.storeId, reviewed))) throw invalid();
                }
            }
            Decision previous = last.put(d.target, d);
            if (previous != null && (d.before.version < previous.afterVersion
                    || d.before.version == previous.afterVersion && (d.before.presence != Presence.ACTIVE
                    || d.before.draft.revision != previous.afterRevision || !same(d.before.draft, previous.result)))) throw invalid();
            Decision provenance = receipts.put(d.inboxId.toString() + d.receiptId, d);
            if (provenance != null && (!provenance.contextId.equals(d.contextId)
                    || !provenance.firstObservedAt.equals(d.firstObservedAt) || !same(provenance.incoming, d.incoming))) throw invalid();
        }
    }

    private static void metadataOnly(TmmDraft draft, boolean dummyRevision) throws IOException {
        validateDraft(draft);
        if (draft.imageRef != null || dummyRevision && draft.revision != 1) throw invalid();
    }
    private static boolean same(TmmDraft a, TmmDraft b) { return a.uid.equals(b.uid) && ImportReview.sameMetadata(a, b); }

    private static void validateTransition(Decision d) throws IOException {
        if (d.action == Action.ADD_LOCAL) {
            if (d.before.presence == Presence.ACTIVE || d.fieldMask != 0 || d.editTime != null || d.outcome != Outcome.ADDED
                    || d.before.version == Long.MAX_VALUE || d.afterVersion != d.before.version + 1
                    || d.afterRevision != 1 || !same(d.result, d.incoming)) throw invalid();
            return;
        }
        if (d.before.presence != Presence.ACTIVE || same(d.before.draft, d.incoming)) throw invalid();
        TmmDraft before = d.before.draft;
        boolean changed = false;
        if (d.action == Action.USE_SELECTED) {
            if (d.fieldMask == 0 || !d.incoming.createdAt.equals(before.createdAt)) throw invalid();
            changed = !same(selected(before, d.incoming, d.fieldMask, before.updatedAt), before);
        } else if (d.fieldMask != 0) throw invalid();
        if (changed) {
            if (d.editTime == null || d.editTime.isBefore(before.updatedAt) || d.outcome != Outcome.UPDATED
                    || d.before.version == Long.MAX_VALUE || before.revision == Long.MAX_VALUE
                    || d.afterVersion != d.before.version + 1 || d.afterRevision != before.revision + 1
                    || !same(d.result, selected(before, d.incoming, d.fieldMask, d.editTime))) throw invalid();
        } else if (d.editTime != null || d.outcome != Outcome.KEPT || d.afterVersion != d.before.version
                || d.afterRevision != before.revision || !same(d.result, before)) throw invalid();
    }

    // Shared metadata projection for history validation and previews; never applies to a live draft.
    static TmmDraft selected(TmmDraft b, TmmDraft r, int mask, Instant updated) {
        return new TmmDraft(b.uid, (mask & 1) != 0 ? r.displayId : b.displayId,
                (mask & 2) != 0 ? r.type : b.type, (mask & 4) != 0 ? r.status : b.status,
                (mask & 8) != 0 ? r.owner : b.owner, b.createdAt, updated,
                (mask & 32) != 0 ? r.deployedAt : b.deployedAt, (mask & 16) != 0 ? r.comment : b.comment,
                (mask & 64) != 0 ? r.position : b.position, (mask & 128) != 0 ? r.bearingDeg : b.bearingDeg, null, 1);
    }
}
