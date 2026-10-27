package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static se.tmm.sensorplanner.core.LocalV2.*;

public final class InboxDecisionCoverageTest {
    private static final UUID INBOX = UUID.fromString("00000000-0000-4000-8015-000000000101");
    private interface Check { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        byte[] source = Files.readAllBytes(Paths.get("test-fixtures/synthetic/storage-v2/history-v2.base64"));
        Snapshot local = LocalV2Codec.decode(Base64.getDecoder().decode(new String(source, StandardCharsets.US_ASCII).trim()));
        InboxStore.Snapshot inbox = inbox(local);
        baselineAndPreservation(inbox, local);
        exactReceiptBinding(inbox, local);
        laterStateChanges(inbox, local);
        multipleDecisions(inbox, local);
        invalidAndEmpty(inbox, local);
        assert Arrays.equals(source, Files.readAllBytes(Paths.get("test-fixtures/synthetic/storage-v2/history-v2.base64")));
        System.out.println("Inbox decision coverage passed (exact receipt/provenance, current versus historical results, edits/images/delete/re-add and complete read-only retention).");
    }
    private static InboxStore.Snapshot inbox(Snapshot local) {
        List<InboxStore.Receipt> receipts = new ArrayList<>();
        for (Decision d : local.decisions) receipts.add(new InboxStore.Receipt(d.receiptId, d.contextId,
                d.firstObservedAt, d.incoming, Collections.singletonList(null)));
        return new InboxStore.Snapshot(INBOX, receipts);
    }
    private static TmmDraft copy(TmmDraft m, UUID uid, String comment, String image, long revision) {
        return new TmmDraft(uid, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt,
                m.deployedAt, comment, m.position, m.bearingDeg, image, revision);
    }
    private static Snapshot replace(Snapshot local, State state) {
        List<State> slots = new ArrayList<>();
        for (State old : local.slots) slots.add(old.uid.equals(state.uid) ? state : old);
        return new Snapshot(local.storeId, slots, local.decisions);
    }
    private static InboxStore.Snapshot receipt(InboxStore.Snapshot inbox, int index, InboxStore.Receipt value) {
        List<InboxStore.Receipt> receipts = new ArrayList<>(inbox.receipts); receipts.set(index, value);
        return new InboxStore.Snapshot(inbox.inboxId, receipts);
    }
    private static void baselineAndPreservation(InboxStore.Snapshot inbox, Snapshot local) throws Exception {
        byte[] beforeInbox = InboxCodec.encode(inbox), beforeLocal = LocalV2Codec.encode(local);
        InboxDecisionCoverage coverage = InboxDecisionCoverage.assess(inbox, local);
        assert coverage.inboxId.equals(INBOX) && coverage.storeId.equals(local.storeId);
        assert coverage.entries.size() == 5 && coverage.allDecisions.size() == 5;
        boolean[] current = {false, false, true, true, true};
        for (int i = 0; i < 5; i++) {
            InboxDecisionCoverage.Entry entry = coverage.entries.get(i);
            assert entry.receipt == inbox.receipts.get(i) && entry.decisions.size() == 1;
            assert entry.covered == current[i] && entry.decisions.get(0).current == current[i];
            assert entry.decisions.get(0).decision == local.decisions.get(i) && coverage.allDecisions.get(i) == local.decisions.get(i);
            assert entry.currentLocal == local.state(entry.receipt.incoming.uid);
        }
        assert coverage.entries.get(1).currentLocal.presence == Presence.DELETED; // Added earlier, now deleted.
        assert coverage.entries.get(0).currentLocal.draft.imageRef.equals("owned-new");
        assert coverage.entries.get(2).currentLocal.version == 9 && coverage.entries.get(2).currentLocal.draft.revision == 1; // Re-add.
        immutable(() -> coverage.entries.clear()); immutable(() -> coverage.allDecisions.clear());
        immutable(() -> coverage.entries.get(0).decisions.clear());
        assert Arrays.equals(beforeInbox, InboxCodec.encode(inbox)) && Arrays.equals(beforeLocal, LocalV2Codec.encode(local));
        InboxDecisionCoverage reopened = InboxDecisionCoverage.assess(inbox, LocalV2Codec.decode(beforeLocal));
        assert reopened.entries.get(3).covered && !reopened.entries.get(0).covered;
    }
    private static void exactReceiptBinding(InboxStore.Snapshot inbox, Snapshot local) throws Exception {
        int index = 3; InboxStore.Receipt r = inbox.receipts.get(index);
        List<InboxStore.Receipt> mismatches = Arrays.asList(
                new InboxStore.Receipt(UUID.randomUUID(), r.contextId, r.firstObservedAt, r.incoming, r.localObservations),
                new InboxStore.Receipt(r.id, "OTHER-SYNTHETIC-CONTEXT", r.firstObservedAt, r.incoming, r.localObservations),
                new InboxStore.Receipt(r.id, r.contextId, r.firstObservedAt.plusNanos(1), r.incoming, r.localObservations),
                new InboxStore.Receipt(r.id, r.contextId, r.firstObservedAt,
                        copy(r.incoming, r.incoming.uid, "Different received metadata", null, 1), r.localObservations),
                new InboxStore.Receipt(r.id, r.contextId, r.firstObservedAt,
                        copy(r.incoming, new UUID(9, 1), r.incoming.comment, null, 1), r.localObservations));
        for (InboxStore.Receipt changed : mismatches) {
            InboxDecisionCoverage coverage = InboxDecisionCoverage.assess(receipt(inbox, index, changed), local);
            assert !coverage.entries.get(index).covered && coverage.entries.get(index).decisions.isEmpty();
            assert coverage.entries.get(index).receipt == changed && coverage.allDecisions.size() == 5;
        }
        InboxDecisionCoverage otherInbox = InboxDecisionCoverage.assess(new InboxStore.Snapshot(UUID.randomUUID(), inbox.receipts), local);
        for (InboxDecisionCoverage.Entry entry : otherInbox.entries) assert !entry.covered && entry.decisions.isEmpty();
        assert otherInbox.allDecisions.size() == 5;
        List<InboxStore.Receipt> extra = new ArrayList<>(inbox.receipts);
        extra.add(new InboxStore.Receipt(UUID.randomUUID(), "SECOND-SYNTHETIC-CONTEXT", r.firstObservedAt, r.incoming, r.localObservations));
        extra.add(new InboxStore.Receipt(UUID.randomUUID(), r.contextId, r.firstObservedAt,
                copy(r.incoming, new UUID(9, 2), r.incoming.comment, null, 1), r.localObservations));
        InboxDecisionCoverage expanded = InboxDecisionCoverage.assess(new InboxStore.Snapshot(INBOX, extra), local);
        assert expanded.entries.size() == 7 && expanded.entries.get(index).covered;
        assert !expanded.entries.get(5).covered && !expanded.entries.get(6).covered; // Equal names/metadata do not inherit provenance.
        InboxStore.Receipt observedAgain = new InboxStore.Receipt(r.id, r.contextId, r.firstObservedAt, r.incoming,
                Arrays.asList(null, copy(local.state(r.incoming.uid).draft, r.incoming.uid, "Observed locally", null, 9)));
        assert InboxDecisionCoverage.assess(receipt(inbox, index, observedAgain), local).entries.get(index).covered;
        // Numeric zero equality follows ImportReview; byte hashes are not substituted for metadata equality.
        InboxStore.Receipt noOp = inbox.receipts.get(4); TmmDraft m = noOp.incoming;
        TmmDraft zero = new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt,
                m.deployedAt, m.comment, new Position(-0.0, m.position.longitude), m.bearingDeg, null, 1);
        assert InboxDecisionCoverage.assess(receipt(inbox, 4, new InboxStore.Receipt(noOp.id, noOp.contextId,
                noOp.firstObservedAt, zero, noOp.localObservations)), local).entries.get(4).covered;
    }
    private static void laterStateChanges(InboxStore.Snapshot inbox, Snapshot local) throws Exception {
        State before = local.slots.get(0); TmmDraft b = before.draft;
        for (State changed : Arrays.asList(
                State.active(7, copy(b, b.uid, "Later local edit", b.imageRef, 10)),
                State.active(7, copy(b, b.uid, b.comment, "owned-replacement", 10)),
                State.active(8, copy(b, b.uid, b.comment, b.imageRef, 11)), // Original values restored, later version.
                State.deleted(b.uid, 7), State.active(8, copy(b, b.uid, b.comment, null, 1)))) {
            InboxDecisionCoverage coverage = InboxDecisionCoverage.assess(inbox, replace(local, changed));
            for (int index : new int[]{0, 3, 4}) {
                assert !coverage.entries.get(index).covered && coverage.entries.get(index).decisions.size() == 1;
                assert !coverage.entries.get(index).decisions.get(0).current;
            }
            assert coverage.entries.get(2).covered && coverage.allDecisions.size() == 5; // Unrelated target stays covered.
        }
        Snapshot noHistory = new Snapshot(local.storeId, local.slots, Collections.emptyList());
        InboxStore.Receipt equal = new InboxStore.Receipt(UUID.randomUUID(), "SYNTHETIC", Instant.EPOCH,
                copy(b, b.uid, b.comment, null, 1), Collections.singletonList(null));
        InboxDecisionCoverage.Entry entry = InboxDecisionCoverage.assess(new InboxStore.Snapshot(INBOX, Collections.singletonList(equal)), noHistory).entries.get(0);
        assert !entry.covered && entry.decisions.isEmpty();
        assert ImportReview.assess(Collections.singletonList(b), equal.incoming).kind == ImportReview.Kind.SAME_METADATA;
    }
    private static void multipleDecisions(InboxStore.Snapshot inbox, Snapshot local) throws Exception {
        Decision previous = local.decisions.get(3); State old = local.state(previous.target);
        State latest = State.active(7, copy(old.draft, old.uid, old.draft.comment, "owned-later", 10));
        State before = State.active(7, copy(latest.draft, latest.uid, latest.draft.comment, null, 10));
        Decision recent = new Decision(UUID.randomUUID(), Action.KEEP_LOCAL, previous.target, previous.inboxId, previous.receiptId,
                previous.contextId, previous.firstObservedAt, previous.incoming, before,
                LocalStateFingerprint.digest(local.storeId, latest), 0, null, Outcome.KEPT, 7, 10,
                copy(latest.draft, latest.uid, latest.draft.comment, null, 1));
        List<Decision> decisions = new ArrayList<>(local.decisions); decisions.add(recent);
        Snapshot changed = replace(local, latest);
        InboxDecisionCoverage coverage = InboxDecisionCoverage.assess(inbox, new Snapshot(local.storeId, changed.slots, decisions));
        InboxDecisionCoverage.Entry entry = coverage.entries.get(3);
        assert entry.covered && entry.decisions.size() == 2;
        assert !entry.decisions.get(0).current && entry.decisions.get(1).current;
        assert entry.decisions.get(0).decision == previous && entry.decisions.get(1).decision == recent;
        assert !coverage.entries.get(4).covered; // A fresh choice for another receipt is not a winner for this variant.
        Decision duplicateChoice = new Decision(UUID.randomUUID(), recent.action, recent.target, recent.inboxId, recent.receiptId,
                recent.contextId, recent.firstObservedAt, recent.incoming, recent.before, recent.reviewedFingerprint(), 0,
                null, recent.outcome, recent.afterVersion, recent.afterRevision, recent.result);
        decisions.add(duplicateChoice);
        entry = InboxDecisionCoverage.assess(inbox, new Snapshot(local.storeId, changed.slots, decisions)).entries.get(3);
        assert entry.decisions.size() == 3 && entry.decisions.get(1).current && entry.decisions.get(2).current;
    }
    private static void invalidAndEmpty(InboxStore.Snapshot inbox, Snapshot local) throws Exception {
        State state = local.slots.get(0);
        fail(() -> InboxDecisionCoverage.assess(inbox, replace(local,
                State.active(state.version, copy(state.draft, state.uid, state.draft.comment, "different-same-version", state.draft.revision)))));
        fail(() -> InboxDecisionCoverage.assess(inbox, replace(local,
                State.active(state.version, copy(state.draft, state.uid, "Different result at reused version", state.draft.imageRef, state.draft.revision)))));
        fail(() -> InboxDecisionCoverage.assess(inbox, new Snapshot(local.storeId, Arrays.asList(state, state), Collections.emptyList())));
        fail(() -> InboxDecisionCoverage.assess(new InboxStore.Snapshot(INBOX, Arrays.asList(inbox.receipts.get(0), inbox.receipts.get(0))), local));
        InboxDecisionCoverage empty = InboxDecisionCoverage.assess(new InboxStore.Snapshot(INBOX, Collections.emptyList()), local);
        assert empty.entries.isEmpty() && empty.allDecisions.size() == 5;
        assert InboxDecisionCoverage.assess(inbox, new Snapshot(UUID.randomUUID(), Collections.emptyList(), Collections.emptyList())).entries.size() == 5;
    }
    private static void immutable(Check check) throws Exception {
        try { check.run(); throw new AssertionError("Mutable coverage output"); } catch (UnsupportedOperationException expected) { }
    }
    private static void fail(Check check) throws Exception {
        try { check.run(); throw new AssertionError("Invalid supplied snapshots accepted"); }
        catch (IOException expected) { assert expected.getCause() == null; }
    }
}
