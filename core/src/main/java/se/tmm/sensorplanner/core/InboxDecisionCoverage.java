package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.util.*;

/** Read-only association of retained decisions with exact inbox receipts and supplied local state. */
public final class InboxDecisionCoverage {
    public final UUID inboxId, storeId;
    /** One entry per current receipt, in inbox insertion order, including unreviewed variants. */
    public final List<Entry> entries;
    /** Complete retained local history, including decisions with no matching current inbox receipt. */
    public final List<LocalV2.Decision> allDecisions;

    private InboxDecisionCoverage(UUID inboxId, UUID storeId, List<Entry> entries, List<LocalV2.Decision> allDecisions) {
        this.inboxId = inboxId; this.storeId = storeId;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.allDecisions = Collections.unmodifiableList(new ArrayList<>(allDecisions));
    }

    /** Call with successfully loaded stable snapshots; no store access or decisions are performed here. */
    public static InboxDecisionCoverage assess(InboxStore.Snapshot inbox, LocalV2.Snapshot local) throws IOException {
        // Reuse full bounded validation, including current result/image consistency, without file I/O.
        InboxCodec.encode(Objects.requireNonNull(inbox, "inbox"));
        LocalV2Codec.encode(Objects.requireNonNull(local, "local"));
        List<Entry> entries = new ArrayList<>();
        for (InboxStore.Receipt receipt : inbox.receipts) {
            LocalV2.State state = local.state(receipt.incoming.uid);
            List<Review> reviews = new ArrayList<>();
            for (LocalV2.Decision decision : local.decisions) {
                if (matches(inbox.inboxId, receipt, decision)) {
                    boolean current = state.presence == LocalV2.Presence.ACTIVE
                            && state.version == decision.afterVersion && state.draft.revision == decision.afterRevision
                            && ImportReview.sameMetadata(state.draft, decision.result);
                    reviews.add(new Review(decision, current));
                }
            }
            entries.add(new Entry(receipt, state, reviews));
        }
        return new InboxDecisionCoverage(inbox.inboxId, local.storeId, entries, local.decisions);
    }

    private static boolean matches(UUID inboxId, InboxStore.Receipt receipt, LocalV2.Decision decision) {
        return inboxId.equals(decision.inboxId) && receipt.id.equals(decision.receiptId)
                && receipt.contextId.equals(decision.contextId) && receipt.firstObservedAt.equals(decision.firstObservedAt)
                && receipt.incoming.uid.equals(decision.target) && receipt.incoming.uid.equals(decision.incoming.uid)
                && ImportReview.sameMetadata(receipt.incoming, decision.incoming);
    }

    public static final class Entry {
        public final InboxStore.Receipt receipt;
        /** Full current supplied state, separate from metadata-only historical decision results. */
        public final LocalV2.State currentLocal;
        /** All exact-receipt matches in decision insertion order; none is selected as a winner. */
        public final List<Review> decisions;
        public final boolean covered;
        private Entry(InboxStore.Receipt receipt, LocalV2.State currentLocal, List<Review> decisions) {
            this.receipt = receipt; this.currentLocal = currentLocal;
            this.decisions = Collections.unmodifiableList(new ArrayList<>(decisions));
            boolean current = false;
            for (Review review : decisions) current |= review.current;
            this.covered = current;
        }
    }
    public static final class Review {
        public final LocalV2.Decision decision;
        /** True only while this recorded result remains the active supplied local version. */
        public final boolean current;
        private Review(LocalV2.Decision decision, boolean current) { this.decision = decision; this.current = current; }
    }
}
