package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static se.tmm.sensorplanner.core.LocalV2.*;
import static se.tmm.sensorplanner.core.ImportDecisionPreparation.*;

public final class ImportDecisionPreparationTest {
    private static final UUID UID = new UUID(0, 15), STORE = new UUID(0, 16), INBOX = new UUID(0, 17), RECEIPT = new UUID(0, 18);
    private static final Instant EDIT = Instant.ofEpochSecond(30, 123);
    private interface Check { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        addAndReAdd(); choicesAndUnchanged(); invalidChoices(); staleStateAndReceipt();
        cancellationAndConcurrency(); savedDataUnchanged();
        System.out.println("Import preparation passed (add/re-add, selected/keep/equal previews, exact bindings, stale state, images, invalid choices, cancel and no file changes).");
    }
    private static TmmDraft local() {
        return new TmmDraft(UID, "PIR SYNTHETIC", DeviceType.PIR, DeviceStatus.ACTIVE, "SYNTHETIC LOCAL",
                Instant.EPOCH, Instant.ofEpochSecond(10), Instant.ofEpochSecond(5), "Local comment",
                new Position(-0.0, 1), 45.0, "owned-local", 7);
    }
    private static TmmDraft incoming() {
        return new TmmDraft(UID, "SCOUT RECEIVED", DeviceType.SCOUT, DeviceStatus.DEPLOYED, "SYNTHETIC RECEIVED",
                Instant.EPOCH, Instant.ofEpochSecond(20), null, "Received åäö 🌲",
                new Position(2, 3), 90.0, null, 1);
    }
    private static TmmDraft image(TmmDraft m, String image, long revision) { return LocalV2Binary.local(m, image, revision); }
    private static InboxStore.Receipt row(TmmDraft incoming) {
        return new InboxStore.Receipt(RECEIPT, "SYNTHETIC-SOURCE", Instant.ofEpochSecond(100), incoming, Collections.singletonList(null));
    }
    private static InboxStore.Snapshot inbox(TmmDraft incoming) { return new InboxStore.Snapshot(INBOX, Collections.singletonList(row(incoming))); }
    private static Snapshot snapshot(State... slots) { return new Snapshot(STORE, Arrays.asList(slots), Collections.emptyList()); }
    private static Snapshot active() { return snapshot(State.active(4, local())); }
    private static ImportDecisionPreparation use(InboxStore.Snapshot inbox, Snapshot local, Field... fields) throws IOException {
        return ImportDecisionPreparation.useSelected(inbox, RECEIPT, local, new HashSet<>(Arrays.asList(fields)), EDIT);
    }

    private static void addAndReAdd() throws Exception {
        InboxStore.Snapshot inbox = inbox(incoming()); Snapshot empty = snapshot();
        ImportDecisionPreparation review = ImportDecisionPreparation.addLocally(inbox, RECEIPT, empty, false);
        Plan p = review.preview;
        assert p.effect == Effect.ADD_LOCAL && p.needsDecisionRecord() && !p.isReAdd();
        assert p.reviewed.presence == Presence.NEVER_SEEN && p.reviewed.version == 0;
        assert p.storeId.equals(STORE) && p.inboxId.equals(INBOX) && p.receipt == inbox.receipts.get(0);
        assert p.result.version == 1 && p.result.draft.revision == 1 && p.result.draft.imageRef == null;
        assert ImportReview.sameMetadata(p.result.draft, incoming()) && p.editTime == null && p.fieldMask == 0;
        assert review.prepare(inbox, empty).plan == p;
        closed(() -> review.prepare(inbox, empty));
        candidateCompatible(p, empty);
        Snapshot deleted = snapshot(State.deleted(UID, 8));
        invalid(() -> ImportDecisionPreparation.addLocally(inbox, RECEIPT, deleted, false));
        Plan restored = ImportDecisionPreparation.addLocally(inbox, RECEIPT, deleted, true).prepare(inbox, deleted).plan;
        assert restored.isReAdd() && restored.result.version == 9 && restored.result.draft.revision == 1;
        assert restored.result.draft.imageRef == null && restored.result.draft.createdAt.equals(incoming().createdAt);
        candidateCompatible(restored, deleted);
        invalid(() -> ImportDecisionPreparation.addLocally(inbox, RECEIPT, active(), true));
        invalid(() -> ImportDecisionPreparation.addLocally(inbox, RECEIPT, snapshot(State.deleted(UID, Long.MAX_VALUE)), true));
    }

    private static void choicesAndUnchanged() throws Exception {
        InboxStore.Snapshot inbox = inbox(incoming()); Snapshot saved = active();
        for (Field field : Field.values()) {
            Plan p = use(inbox, saved, field).prepare(inbox, saved).plan;
            TmmDraft b = local(), r = incoming(), v = p.result.draft;
            assert p.effect == Effect.UPDATE_LOCAL && p.fieldMask == field.mask && p.editTime.equals(EDIT);
            assert v.uid.equals(UID) && v.createdAt.equals(b.createdAt) && v.updatedAt.equals(EDIT);
            assert v.revision == 8 && p.result.version == 5 && v.imageRef.equals(b.imageRef);
            assert v.displayId.equals(field == Field.DISPLAY_ID ? r.displayId : b.displayId);
            assert v.type == (field == Field.TYPE ? r.type : b.type);
            assert v.status == (field == Field.STATUS ? r.status : b.status);
            assert v.owner.equals(field == Field.OWNER ? r.owner : b.owner);
            assert v.comment.equals(field == Field.COMMENT ? r.comment : b.comment);
            assert Objects.equals(v.deployedAt, field == Field.DEPLOYED_AT ? r.deployedAt : b.deployedAt);
            assert v.position.latitude == (field == Field.POSITION ? r.position.latitude : b.position.latitude);
            assert v.position.longitude == (field == Field.POSITION ? r.position.longitude : b.position.longitude);
            assert Objects.equals(v.bearingDeg, field == Field.BEARING ? r.bearingDeg : b.bearingDeg);
            assert Arrays.equals(p.reviewedFingerprint(), LocalStateFingerprint.digest(STORE, saved.slots.get(0)));
            candidateCompatible(p, saved);
        }
        Set<Field> selected = EnumSet.allOf(Field.class);
        ImportDecisionPreparation all = ImportDecisionPreparation.useSelected(inbox, RECEIPT, saved, selected, EDIT);
        selected.clear(); assert all.preview.fieldMask == 255;
        Plan p = all.prepare(inbox, saved).plan; assert ImportReview.sameMetadata(p.result.draft,
                new TmmDraft(UID, incoming().displayId, incoming().type, incoming().status, incoming().owner,
                        local().createdAt, EDIT, incoming().deployedAt, incoming().comment, incoming().position,
                        incoming().bearingDeg, null, 1));
        Plan keep = ImportDecisionPreparation.keepLocal(inbox, RECEIPT, saved).prepare(inbox, saved).plan;
        assert keep.effect == Effect.KEEP_LOCAL && keep.result == saved.slots.get(0) && keep.needsDecisionRecord();
        candidateCompatible(keep, saved);
        TmmDraft same = image(local(), null, 1);
        InboxStore.Snapshot identical = inbox(same);
        Plan equal = ImportDecisionPreparation.keepLocal(identical, RECEIPT, saved).prepare(identical, saved).plan;
        assert equal.effect == Effect.ALREADY_EQUAL && !equal.needsDecisionRecord() && equal.result == saved.slots.get(0);
        assert ImportDecisionPreparation.useSelected(identical, RECEIPT, saved, EnumSet.of(Field.COMMENT), null)
                .preview.effect == Effect.ALREADY_EQUAL;
        TmmDraft unselected = local().edit(local().displayId, local().type, local().status, local().owner,
                local().deployedAt, "Different unselected comment", new Position(0.0, 1), local().bearingDeg, null, Instant.ofEpochSecond(11));
        InboxStore.Snapshot noChange = inbox(image(unselected, null, 1));
        Plan unchanged = ImportDecisionPreparation.useSelected(noChange, RECEIPT, saved, EnumSet.of(Field.POSITION), Instant.EPOCH)
                .prepare(noChange, saved).plan;
        assert unchanged.effect == Effect.KEEP_LOCAL && unchanged.needsDecisionRecord();
        assert unchanged.editTime == null && unchanged.result == saved.slots.get(0) && unchanged.fieldMask == Field.POSITION.mask;
        candidateCompatible(unchanged, saved);
        byte[] digest = unchanged.reviewedFingerprint(); digest[0] ^= 1;
        assert !Arrays.equals(digest, unchanged.reviewedFingerprint());
    }

    private static void invalidChoices() throws Exception {
        Snapshot saved = active(); InboxStore.Snapshot inbox = inbox(incoming());
        invalid(() -> ImportDecisionPreparation.keepLocal(inbox, RECEIPT, snapshot()));
        invalid(() -> use(inbox, snapshot(State.deleted(UID, 5)), Field.COMMENT));
        invalid(() -> ImportDecisionPreparation.useSelected(inbox, RECEIPT, saved, Collections.emptySet(), EDIT));
        invalid(() -> ImportDecisionPreparation.useSelected(inbox, RECEIPT, saved, EnumSet.of(Field.COMMENT), null));
        invalid(() -> ImportDecisionPreparation.useSelected(inbox, RECEIPT, saved, EnumSet.of(Field.COMMENT), Instant.EPOCH));
        invalid(() -> ImportDecisionPreparation.keepLocal(inbox, UUID.randomUUID(), saved));
        ioFailure(() -> ImportDecisionPreparation.keepLocal(inbox, RECEIPT, snapshot(saved.slots.get(0), saved.slots.get(0))));
        InboxStore.Snapshot duplicateReceipts = new InboxStore.Snapshot(INBOX, Arrays.asList(row(incoming()), row(incoming())));
        ioFailure(() -> ImportDecisionPreparation.keepLocal(duplicateReceipts, RECEIPT, saved));
        TmmDraft mismatch = new TmmDraft(UID, incoming().displayId, incoming().type, incoming().status, incoming().owner,
                Instant.ofEpochSecond(1), incoming().updatedAt, null, incoming().comment, incoming().position, incoming().bearingDeg, null, 1);
        invalid(() -> use(inbox(mismatch), saved, Field.COMMENT));
        assert ImportDecisionPreparation.keepLocal(inbox(mismatch), RECEIPT, saved).preview.effect == Effect.KEEP_LOCAL;
        TmmDraft gateway = new TmmDraft(UID, "GATEWAY RECEIVED", DeviceType.GATEWAY, DeviceStatus.PLANNED, "SYNTHETIC",
                Instant.EPOCH, Instant.ofEpochSecond(20), null, "", new Position(0, 1), null, null, 1);
        invalid(() -> use(inbox(gateway), saved, Field.TYPE));
        Plan valid = use(inbox(gateway), saved, Field.TYPE, Field.BEARING).prepare(inbox(gateway), saved).plan;
        assert valid.result.draft.type == DeviceType.GATEWAY && valid.result.draft.bearingDeg == null;
        assert valid.result.draft.imageRef.equals(local().imageRef);
        Snapshot maxRevision = snapshot(State.active(4, image(local(), "owned-local", Long.MAX_VALUE)));
        invalid(() -> use(inbox, maxRevision, Field.COMMENT));
        assert ImportDecisionPreparation.keepLocal(inbox, RECEIPT, maxRevision).preview.result.draft.revision == Long.MAX_VALUE;
        Snapshot maxVersion = snapshot(State.active(Long.MAX_VALUE, local()));
        invalid(() -> use(inbox, maxVersion, Field.COMMENT));
        assert ImportDecisionPreparation.keepLocal(inbox, RECEIPT, maxVersion).preview.result.version == Long.MAX_VALUE;
        // A foreign earlier update time is retained, not substituted for the previewed local edit time.
        TmmDraft old = new TmmDraft(UID, incoming().displayId, incoming().type, incoming().status, incoming().owner,
                Instant.EPOCH, Instant.ofEpochSecond(2), null, incoming().comment, incoming().position, incoming().bearingDeg, null, 1);
        assert use(inbox(old), saved, Field.COMMENT).preview.result.draft.updatedAt.equals(EDIT);
    }

    private static void staleStateAndReceipt() throws Exception {
        Snapshot saved = active(); InboxStore.Snapshot inbox = inbox(incoming());
        TmmDraft edited = local().edit(local().displayId, local().type, local().status, local().owner,
                local().deployedAt, "Locally edited", local().position, local().bearingDeg, local().imageRef, Instant.ofEpochSecond(11));
        for (Snapshot changed : Arrays.asList(
                snapshot(State.active(5, edited)), snapshot(State.active(4, edited)),
                snapshot(State.active(4, image(local(), "owned-new", 7))),
                snapshot(State.active(5, image(local(), null, 8))), snapshot(State.deleted(UID, 5)),
                snapshot(State.active(6, local())), snapshot(),
                new Snapshot(UUID.randomUUID(), saved.slots, saved.decisions))) {
            ImportDecisionPreparation review = use(inbox, saved, Field.COMMENT);
            stale(() -> review.prepare(inbox, changed));
            closed(() -> review.prepare(inbox, saved));
            assert review.preview.result.draft.comment.equals(incoming().comment);
        }
        for (InboxStore.Snapshot changed : Arrays.asList(
                new InboxStore.Snapshot(UUID.randomUUID(), inbox.receipts),
                new InboxStore.Snapshot(INBOX, Collections.emptyList()),
                new InboxStore.Snapshot(INBOX, Collections.singletonList(new InboxStore.Receipt(RECEIPT, "OTHER-CONTEXT", row(incoming()).firstObservedAt, incoming(), Collections.singletonList(null)))),
                new InboxStore.Snapshot(INBOX, Collections.singletonList(new InboxStore.Receipt(RECEIPT, "SYNTHETIC-SOURCE", Instant.EPOCH, incoming(), Collections.singletonList(null)))),
                inbox(image(local(), null, 1)))) {
            stale(() -> use(inbox, saved, Field.COMMENT).prepare(changed, saved));
        }
        Snapshot empty = snapshot();
        stale(() -> ImportDecisionPreparation.addLocally(inbox, RECEIPT, empty, false).prepare(inbox, saved));
        stale(() -> ImportDecisionPreparation.addLocally(inbox, RECEIPT, empty, false).prepare(inbox, snapshot(State.deleted(UID, 2))));
        Snapshot deleted = snapshot(State.deleted(UID, 8));
        stale(() -> ImportDecisionPreparation.addLocally(inbox, RECEIPT, deleted, true).prepare(inbox, snapshot(State.deleted(UID, 10))));
        InboxStore.Receipt expanded = new InboxStore.Receipt(RECEIPT, row(incoming()).contextId, row(incoming()).firstObservedAt,
                incoming(), Arrays.asList(null, image(local(), null, 7)));
        InboxStore.Receipt otherVariant = new InboxStore.Receipt(UUID.randomUUID(), "SYNTHETIC-SOURCE", Instant.EPOCH,
                image(local(), null, 1), Collections.singletonList(null));
        InboxStore.Snapshot newEvidence = new InboxStore.Snapshot(INBOX, Arrays.asList(expanded, otherVariant));
        TmmDraft otherUid = TmmDraft.create("PIR SYNTHETIC", DeviceType.PIR, "SYNTHETIC", new Position(0, 1), Instant.EPOCH);
        Snapshot unrelated = snapshot(saved.slots.get(0), State.active(1, otherUid));
        Prepared prepared = use(inbox, saved, Field.COMMENT).prepare(newEvidence, unrelated);
        Plan p = prepared.plan;
        prepared.requireCurrent(newEvidence, unrelated);
        stale(() -> prepared.requireCurrent(newEvidence, snapshot(State.deleted(UID, 5))));
        assert p.receipt == inbox.receipts.get(0) && p.receipt.localObservations.size() == 1;
        assert newEvidence.receipts.size() == 2 && expanded.localObservations.size() == 2;
    }

    private static void cancellationAndConcurrency() throws Exception {
        InboxStore.Snapshot inbox = inbox(incoming()); Snapshot saved = active();
        ImportDecisionPreparation cancelled = use(inbox, saved, Field.COMMENT); cancelled.close(); cancelled.close();
        closed(() -> cancelled.prepare(inbox, saved));
        ImportDecisionPreparation review = use(inbox, saved, Field.COMMENT);
        ExecutorService workers = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> attempt = () -> {
                start.await();
                try { review.prepare(inbox, saved); return true; }
                catch (IllegalStateException expected) { return false; }
            };
            Future<Boolean> a = workers.submit(attempt), b = workers.submit(attempt); start.countDown();
            assert a.get(10, TimeUnit.SECONDS) != b.get(10, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
        closed(() -> review.prepare(inbox, saved));
    }

    private static void candidateCompatible(Plan p, Snapshot saved) throws Exception {
        assert p.needsDecisionRecord();
        State before = p.reviewed.draft == null ? p.reviewed : State.active(p.reviewed.version, image(p.reviewed.draft, null, p.reviewed.draft.revision));
        Outcome outcome = p.effect == Effect.ADD_LOCAL ? Outcome.ADDED : p.effect == Effect.UPDATE_LOCAL ? Outcome.UPDATED : Outcome.KEPT;
        Decision evidence = new Decision(p.operationId, p.action, p.reviewed.uid, p.inboxId, p.receipt.id, p.receipt.contextId,
                p.receipt.firstObservedAt, p.receipt.incoming, before, p.reviewedFingerprint(), p.fieldMask, p.editTime,
                outcome, p.result.version, p.result.draft.revision, image(p.result.draft, null, 1));
        // A hypothetical in-memory frame checks format compatibility; no store is applied or written.
        List<State> slots = new ArrayList<>();
        for (State s : saved.slots) if (!s.uid.equals(p.reviewed.uid)) slots.add(s);
        slots.add(p.result);
        Snapshot hypothetical = new Snapshot(p.storeId, slots, Collections.singletonList(evidence));
        assert LocalV2Codec.decode(LocalV2Codec.encode(hypothetical)).decisions.get(0).id.equals(p.operationId);
    }

    private static void savedDataUnchanged() throws Exception {
        Path root = Files.createTempDirectory("tmm-import-preparation-");
        try {
            DraftStore drafts = new DraftStore(root.resolve("drafts")); TmmDraft actual = image(local(), "owned-local", 1); drafts.save(actual);
            Path image = root.resolve("drafts/owned-local"); Files.write(image, new byte[]{1, 3, 5});
            InboxStore inbox = new InboxStore(root.resolve("inbox")); inbox.initializeNew();
            InboxStore.Receipt receipt = inbox.receive("SYNTHETIC-SOURCE", incoming(), actual, Instant.EPOCH);
            Path draftFile = root.resolve("drafts/drafts.bin"), inboxFile = root.resolve("inbox/inbox.bin");
            byte[] draftBytes = Files.readAllBytes(draftFile), inboxBytes = Files.readAllBytes(inboxFile);
            Snapshot local = snapshot(State.active(4, actual)); InboxStore.Snapshot received = inbox.load();
            ImportDecisionPreparation cancelled = ImportDecisionPreparation.keepLocal(received, receipt.id, local); cancelled.close();
            closed(() -> cancelled.prepare(received, local));
            ImportDecisionPreparation review = ImportDecisionPreparation.useSelected(received, receipt.id, local, EnumSet.of(Field.COMMENT), EDIT);
            Prepared prepared = review.prepare(received, local); prepared.requireCurrent(inbox.load(), local);
            Plan plan = prepared.plan;
            assert plan.result.draft.revision == 2 && drafts.load().get(0).revision == 1;
            assert Arrays.equals(draftBytes, Files.readAllBytes(draftFile)) && Arrays.equals(inboxBytes, Files.readAllBytes(inboxFile));
            assert Arrays.equals(new byte[]{1, 3, 5}, Files.readAllBytes(image));
            assert inbox.load().receipts.get(0).localObservations.size() == 1;
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
    private static void ioFailure(Check action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid snapshots accepted"); } catch (IOException expected) { assert expected.getCause() == null; }
    }
    private static void invalid(Check action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid import choice accepted"); }
        catch (IllegalArgumentException expected) { assert expected.getCause() == null; }
    }
    private static void stale(Check action) throws Exception {
        try { action.run(); throw new AssertionError("Stale import review accepted"); }
        catch (IllegalStateException expected) { assert expected.getMessage().equals("Import review changed; review again"); }
    }
    private static void closed(Check action) throws Exception {
        try { action.run(); throw new AssertionError("Consumed/cancelled review accepted"); }
        catch (IllegalStateException expected) { assert expected.getMessage().equals("Import preparation is no longer pending"); }
    }
}
