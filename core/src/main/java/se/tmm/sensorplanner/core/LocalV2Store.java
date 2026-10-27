package se.tmm.sensorplanner.core;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static se.tmm.sensorplanner.core.LocalV2.*;

/** Isolated private v2 store/migration. No Android activation, images or transport operations. */
public final class LocalV2Store {
    public enum Reason { BUSY, STALE, INVALID, CAPACITY, UNKNOWN }
    public enum Effect { COMMITTED, ALREADY_COMMITTED, ALREADY_EQUAL }
    public enum CommitStatus { COMMITTED, NOT_COMMITTED, UNKNOWN }
    public static final class Failure extends IOException {
        public final Reason reason;
        private Failure(Reason reason) {
            super("Local v2 operation: " + reason.name() + "; read committed state before retrying");
            this.reason = reason;
        }
    }
    public static final class Result {
        public final Effect effect;
        /** Historical metadata-only evidence, or null for ALREADY_EQUAL. */
        public final Decision decision;
        /** Current full local state, which can differ from the historical result on replay. */
        public final State current;
        private Result(Effect effect, Decision decision, State current) {
            this.effect = effect; this.decision = decision; this.current = current;
        }
    }
    public static final class Readback {
        public final CommitStatus status;
        public final Decision decision;
        /** Null for NOT_COMMITTED/UNKNOWN; neither means an absent target. */
        public final State current;
        private Readback(CommitStatus status, Decision decision, State current) {
            this.status = status; this.decision = decision; this.current = current;
        }
    }
    interface Committer {
        default void writeAndSync(Path temporary, byte[] bytes) throws IOException {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(bytes); output.getFD().sync();
            }
        }
        void replace(Path temporary, Path destination) throws IOException;
    }
    private interface Operation<T> { T run() throws IOException; }
    private final Path directory, data;
    private final Committer committer;

    public LocalV2Store(Path directory) {
        this(directory, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
    LocalV2Store(Path directory, Committer committer) {
        this.directory = Objects.requireNonNull(directory, "directory");
        data = directory.resolve("drafts.bin"); this.committer = Objects.requireNonNull(committer, "committer");
    }

    /** Explicit new dataset only; never overwrites an existing valid, corrupt or unsupported file. */
    public Snapshot initializeNew() throws IOException {
        return locked(() -> {
            if (!Files.notExists(data, LinkOption.NOFOLLOW_LINKS)) throw failure(Reason.INVALID);
            Snapshot empty = new Snapshot(UUID.randomUUID(), Collections.emptyList(), Collections.emptyList());
            write(empty); return empty;
        });
    }
    public Snapshot load() throws IOException { return locked(this::read); }

    /** Explicit in-place upgrade only. Existing valid v2 is returned unchanged for uncertain-outcome recovery. */
    public Snapshot migrateV1() throws IOException {
        return locked(() -> {
            byte[] bytes = readBytes();
            if (bytes.length >= 8 && java.nio.ByteBuffer.wrap(bytes).getInt(4) == 2) {
                return LocalV2Codec.decode(bytes);
            }
            List<State> slots = new ArrayList<>();
            for (TmmDraft draft : DraftStore.decodeV1(bytes).values()) slots.add(State.active(1, draft));
            Snapshot migrated = new Snapshot(UUID.randomUUID(), slots, Collections.emptyList());
            write(migrated); return migrated;
        });
    }

    /** Full expected-state guard, including image association; deleted re-add requires its exact slot. */
    public State save(UUID storeId, State expected, TmmDraft draft) throws IOException {
        Objects.requireNonNull(storeId, "storeId"); Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(draft, "draft");
        return locked(() -> {
            Snapshot saved = read(); requireState(saved, storeId, expected);
            if (!draft.uid.equals(expected.uid) || expected.version == Long.MAX_VALUE) throw failure(Reason.INVALID);
            TmmDraft before = expected.draft;
            if (before == null ? draft.revision != 1 : before.revision == Long.MAX_VALUE
                    || draft.revision != before.revision + 1 || !before.createdAt.equals(draft.createdAt)
                    || draft.updatedAt.isBefore(before.updatedAt)) throw failure(Reason.INVALID);
            State next = State.active(expected.version + 1, draft);
            write(replace(saved, next, saved.decisions)); return next;
        });
    }
    /** Retains a versioned deleted slot, never deletes image bytes or frees slot capacity. */
    public State delete(UUID storeId, State expected) throws IOException {
        Objects.requireNonNull(storeId, "storeId"); Objects.requireNonNull(expected, "expected");
        return locked(() -> {
            Snapshot saved = read(); requireState(saved, storeId, expected);
            if (expected.presence != Presence.ACTIVE || expected.version == Long.MAX_VALUE) throw failure(Reason.INVALID);
            State next = State.deleted(expected.uid, expected.version + 1);
            write(replace(saved, next, saved.decisions)); return next;
        });
    }

    /** Explicit prepared choice only. Holds inbox, then draft lock through validation and replacement. */
    public Result commit(InboxStore inbox, ImportDecisionPreparation.Prepared prepared) throws IOException {
        Objects.requireNonNull(inbox, "inbox"); Objects.requireNonNull(prepared, "prepared");
        try {
            return inbox.withLockedSnapshot(currentInbox -> locked(() -> {
                Snapshot saved = read(); ImportDecisionPreparation.Plan plan = prepared.plan;
                if (!saved.storeId.equals(plan.storeId)) throw failure(Reason.STALE);
                Decision binding = decision(plan);
                for (Decision previous : saved.decisions) {
                    if (!previous.id.equals(plan.operationId)) continue;
                    if (binding == null || !sameDecision(previous, binding)) throw failure(Reason.INVALID);
                    return new Result(Effect.ALREADY_COMMITTED, previous, state(saved, previous.target));
                }
                try { prepared.requireCurrent(currentInbox, saved); }
                catch (IllegalStateException stale) { throw failure(Reason.STALE); }
                if (binding == null) return new Result(Effect.ALREADY_EQUAL, null, state(saved, plan.reviewed.uid));
                List<Decision> history = new ArrayList<>(saved.decisions); history.add(binding);
                write(replace(saved, plan.result, history));
                return new Result(Effect.COMMITTED, binding, plan.result);
            }));
        } catch (Failure safe) { throw safe; }
        catch (InboxStore.BusyFailure busy) { throw failure(Reason.BUSY); }
        catch (IOException unavailable) { throw failure(Reason.UNKNOWN); }
    }

    /** Local-ledger-only evidence; inbox loss never erases a committed decision. No repair or reapply. */
    public Readback readback(UUID storeId, UUID operationId) {
        Objects.requireNonNull(storeId, "storeId"); Objects.requireNonNull(operationId, "operationId");
        try {
            return locked(() -> {
                Snapshot saved = read();
                if (!saved.storeId.equals(storeId)) return new Readback(CommitStatus.UNKNOWN, null, null);
                for (Decision decision : saved.decisions) if (decision.id.equals(operationId)) {
                    return new Readback(CommitStatus.COMMITTED, decision, state(saved, decision.target));
                }
                return new Readback(CommitStatus.NOT_COMMITTED, null, null);
            });
        } catch (IOException unavailable) { return new Readback(CommitStatus.UNKNOWN, null, null); }
    }

    private static State state(Snapshot snapshot, UUID uid) {
        for (State state : snapshot.slots) if (state.uid.equals(uid)) return state;
        return State.neverSeen(uid);
    }
    private static void requireState(Snapshot snapshot, UUID storeId, State expected) throws IOException {
        if (!snapshot.storeId.equals(storeId) || !sameState(expected, state(snapshot, expected.uid))) throw failure(Reason.STALE);
    }
    private static boolean sameState(State a, State b) {
        return a.uid.equals(b.uid) && a.presence == b.presence && a.version == b.version
                && (a.draft == null ? b.draft == null : b.draft != null && a.draft.revision == b.draft.revision
                && Objects.equals(a.draft.imageRef, b.draft.imageRef) && sameMetadata(a.draft, b.draft));
    }
    private static boolean sameMetadata(TmmDraft a, TmmDraft b) {
        return a.uid.equals(b.uid) && ImportReview.sameMetadata(a, b);
    }
    private static Snapshot replace(Snapshot saved, State next, List<Decision> history) {
        List<State> slots = new ArrayList<>(saved.slots); boolean found = false;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).uid.equals(next.uid)) {
            slots.set(i, next); found = true; break;
        }
        if (!found) slots.add(next);
        return new Snapshot(saved.storeId, slots, history);
    }
    private static Decision decision(ImportDecisionPreparation.Plan p) {
        if (!p.needsDecisionRecord()) return null;
        State before = p.reviewed.presence == Presence.ACTIVE
                ? State.active(p.reviewed.version, LocalV2Binary.local(p.reviewed.draft, null, p.reviewed.draft.revision)) : p.reviewed;
        Outcome outcome = p.effect == ImportDecisionPreparation.Effect.ADD_LOCAL ? Outcome.ADDED
                : p.effect == ImportDecisionPreparation.Effect.UPDATE_LOCAL ? Outcome.UPDATED : Outcome.KEPT;
        return new Decision(p.operationId, p.action, p.reviewed.uid, p.inboxId, p.receipt.id, p.receipt.contextId,
                p.receipt.firstObservedAt, p.receipt.incoming, before, p.reviewedFingerprint(), p.fieldMask,
                p.editTime, outcome, p.result.version, p.result.draft.revision,
                LocalV2Binary.local(p.result.draft, null, 1));
    }
    private static boolean sameDecision(Decision a, Decision b) {
        return a.id.equals(b.id) && a.action == b.action && a.target.equals(b.target) && a.inboxId.equals(b.inboxId)
                && a.receiptId.equals(b.receiptId) && a.contextId.equals(b.contextId) && a.firstObservedAt.equals(b.firstObservedAt)
                && sameMetadata(a.incoming, b.incoming) && sameState(a.before, b.before)
                && MessageDigest.isEqual(a.reviewedFingerprint(), b.reviewedFingerprint())
                && a.fieldMask == b.fieldMask && Objects.equals(a.editTime, b.editTime) && a.outcome == b.outcome
                && a.afterVersion == b.afterVersion && a.afterRevision == b.afterRevision && sameMetadata(a.result, b.result);
    }
    private <T> T locked(Operation<T> operation) throws IOException {
        try {
            Files.createDirectories(directory);
            Path lockPath = directory.resolve("drafts.lock");
            if (Files.isSymbolicLink(directory) || !regularOrMissing(data) || !regularOrMissing(lockPath)) throw failure(Reason.UNKNOWN);
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); FileLock lock = channel.tryLock()) {
                if (lock == null) throw failure(Reason.BUSY);
                return operation.run();
            }
        } catch (OverlappingFileLockException busy) { throw failure(Reason.BUSY); }
        catch (Failure safe) {
            if (safe.getSuppressed().length != 0) throw failure(Reason.UNKNOWN);
            throw safe;
        }
        catch (IOException unavailable) { throw failure(Reason.UNKNOWN); }
    }
    private static boolean regularOrMissing(Path path) {
        return Files.notExists(path, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }
    private Snapshot read() throws IOException {
        return LocalV2Codec.decode(readBytes());
    }
    private byte[] readBytes() throws IOException {
        try (InputStream input = Files.newInputStream(data, LinkOption.NOFOLLOW_LINKS)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] chunk = new byte[8192]; int count;
            while ((count = input.read(chunk)) != -1) {
                if (count > LocalV2Codec.MAX_BYTES - buffer.size()) throw failure(Reason.UNKNOWN);
                buffer.write(chunk, 0, count);
            }
            return buffer.toByteArray();
        }
    }
    private void write(Snapshot snapshot) throws IOException {
        if (snapshot.slots.size() > LocalV2Codec.MAX_SLOTS || snapshot.decisions.size() > LocalV2Codec.MAX_DECISIONS) {
            throw failure(Reason.CAPACITY);
        }
        byte[] bytes;
        try { bytes = LocalV2Codec.encode(snapshot); }
        catch (IOException invalid) { throw failure(Reason.INVALID); }
        Path temporary = Files.createTempFile(directory, "drafts-v2-", ".tmp");
        try {
            committer.writeAndSync(temporary, bytes);
            committer.replace(temporary, data);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static Failure failure(Reason reason) { return new Failure(reason); }
}
