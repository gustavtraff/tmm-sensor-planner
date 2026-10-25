package se.tmm.sensorplanner.core;

import java.util.*;

/** Read-only grouping and metadata assessment of a committed inbox against supplied local state. */
public final class InboxReview {
    public final UUID inboxId;
    public final List<Group> groups;

    private InboxReview(UUID inboxId, List<Group> groups) {
        this.inboxId = inboxId;
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
    }

    /** Inputs must be stable snapshots; this neither reads stores nor guarantees later freshness. */
    public static InboxReview assess(InboxStore.Snapshot inbox, Collection<TmmDraft> localSnapshot) {
        Objects.requireNonNull(inbox, "inbox");
        Objects.requireNonNull(localSnapshot, "localSnapshot");
        Map<UUID, TmmDraft> locals = new HashMap<>();
        for (TmmDraft local : localSnapshot) {
            Objects.requireNonNull(local, "local snapshot entry");
            if (locals.put(local.uid, local) != null) {
                throw new IllegalArgumentException("Local snapshot contains duplicate UUIDs");
            }
        }
        Map<UUID, List<Variant>> variants = new LinkedHashMap<>();
        for (InboxStore.Receipt receipt : inbox.receipts) {
            TmmDraft local = locals.get(receipt.incoming.uid);
            ImportReview comparison = ImportReview.assess(local == null
                    ? Collections.emptyList() : Collections.singletonList(local), receipt.incoming);
            variants.computeIfAbsent(receipt.incoming.uid, uid -> new ArrayList<>())
                    .add(new Variant(receipt, comparison.kind));
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<Variant>> entry : variants.entrySet()) {
            groups.add(new Group(entry.getKey(), locals.get(entry.getKey()), entry.getValue()));
        }
        return new InboxReview(inbox.inboxId, groups);
    }

    public static final class Group {
        public final UUID uid;
        /** The full supplied local draft, including its image handle; null means absent in that snapshot. */
        public final TmmDraft currentLocal;
        /** One entry per receipt, including equal metadata from different source contexts. */
        public final List<Variant> variants;

        private Group(UUID uid, TmmDraft currentLocal, List<Variant> variants) {
            this.uid = uid;
            this.currentLocal = currentLocal;
            this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
        }
    }

    public static final class Variant {
        /** Original immutable receipt, with incoming metadata, provenance and historical local observations. */
        public final InboxStore.Receipt receipt;
        /** Existing ImportReview meaning only; never an apply permission or proof of concurrency. */
        public final ImportReview.Kind kind;

        private Variant(InboxStore.Receipt receipt, ImportReview.Kind kind) {
            this.receipt = receipt;
            this.kind = kind;
        }
    }
}
