package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/** Read-only association/receipt/current-metadata join; never proves live image bytes or source authority. */
public final class ReceivedImageReview {
    public final UUID registryId, inboxId, deviceUid;
    public final List<Item> images;

    private ReceivedImageReview(UUID registryId, UUID inboxId, UUID deviceUid, List<Item> images) {
        this.registryId = registryId; this.inboxId = inboxId; this.deviceUid = deviceUid;
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
    }

    /** Stable loaded snapshots only. An unavailable store is not an empty history or an absent device. */
    public static ReceivedImageReview assess(ImageAssociationRegistry.Snapshot registry,
                                             InboxStore.Snapshot inbox, LocalV2.Snapshot local,
                                             UUID deviceUid) throws IOException {
        if (registry == null || inbox == null || local == null || deviceUid == null
                || !registry.inboxId.equals(inbox.inboxId)) {
            throw new IOException("Received image review is unavailable; no empty history inferred");
        }
        Map<UUID, InboxStore.Receipt> receipts = new HashMap<>();
        for (InboxStore.Receipt receipt : inbox.receipts) {
            if (receipts.put(receipt.id, receipt) != null) throw new IOException("Received metadata history is inconsistent");
        }
        LocalV2.State current = local.state(deviceUid);
        List<Item> images = new ArrayList<>();
        for (ImageAssociationRegistry.Record record : registry.records) {
            if (!deviceUid.equals(record.deviceUid)) continue;
            InboxStore.Receipt receipt = receipts.get(record.receiptId);
            boolean linked = receipt != null && record.contextId.equals(receipt.contextId)
                    && record.deviceUid.equals(receipt.incoming.uid) && receipt.incoming.imageRef == null;
            ShareReview.Metadata metadata = linked ? new ShareReview.Metadata(receipt.incoming) : null;
            boolean same = linked && current.presence == LocalV2.Presence.ACTIVE
                    && ImportReview.sameMetadata(receipt.incoming, current.draft);
            // Keep broken links and every delivery/context; order never selects a current/newest picture.
            images.add(new Item(record, metadata, current.presence, same));
        }
        return new ReceivedImageReview(registry.registryId, registry.inboxId, deviceUid, images);
    }

    public static final class Item {
        public final ImageAssociationRegistry.Record record;
        /** Null means an unavailable receipt link, not permission to infer metadata from a file. */
        public final ShareReview.Metadata metadata;
        public final LocalV2.Presence localPresence;
        public final boolean sameCurrentMetadata;

        private Item(ImageAssociationRegistry.Record record, ShareReview.Metadata metadata,
                     LocalV2.Presence localPresence, boolean sameCurrentMetadata) {
            this.record = record; this.metadata = metadata; this.localPresence = localPresence;
            this.sameCurrentMetadata = sameCurrentMetadata;
        }

        /** Claims/coherence only. Caller must read this record's stage ID and verify actual content separately. */
        public boolean matchesOffer(ImageOffer offer) {
            return metadata != null && offer != null
                    && record.associationId.equals(offer.associationId) && record.deliveryId.equals(offer.deliveryId)
                    && record.deviceUid.equals(offer.metadata.uid)
                    && record.pngLength == offer.pngLength && record.width == offer.width && record.height == offer.height
                    && MessageDigest.isEqual(record.sha256(), offer.sha256())
                    && InboxCodec.sameMetadata(ImageOffer.carrier(metadata), ImageOffer.carrier(offer.metadata));
        }

        /** Rechecks the immutable retained receipt link; never substitutes a new source or metadata variant. */
        public boolean matchesReceipt(InboxStore.Receipt receipt) {
            return metadata != null && receipt != null && record.receiptId.equals(receipt.id)
                    && record.contextId.equals(receipt.contextId) && record.deviceUid.equals(receipt.incoming.uid)
                    && receipt.incoming.imageRef == null
                    && InboxCodec.sameMetadata(ImageOffer.carrier(metadata), receipt.incoming);
        }
    }
}
