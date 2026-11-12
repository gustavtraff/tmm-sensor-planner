package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.IOException;
import se.tmm.sensorplanner.core.ImageAssociationRegistry;
import se.tmm.sensorplanner.core.InboxStore;
import se.tmm.sensorplanner.core.ReceivedImageReview;
import se.tmm.sensorplanner.images.ImagePreparationException;

/** Read-only local content opening; no network, new association, local attachment or UI operation. */
@TargetApi(26)
public final class ReceivedImageContent {
    private ReceivedImageContent() { }

    /** Call on the owning serialized worker. Actual stage/offer bytes are reverified before returning. */
    public static ReceivedImagePayload open(ReceivedImageReview reviewed, ReceivedImageReview.Item selected,
                                            ImageAssociationRegistry registry, InboxStore inbox,
                                            ImageStageStore stage) throws IOException {
        try {
            if (reviewed == null || selected == null || !reviewed.images.contains(selected)) throw new IOException();
            InboxStore.Snapshot current = inbox.load();
            if (!reviewed.inboxId.equals(current.inboxId)) throw new IOException();
            boolean linked = false;
            for (InboxStore.Receipt receipt : current.receipts) if (selected.matchesReceipt(receipt)) { linked = true; break; }
            if (!linked) throw new IOException();
            return registry.withRecord(reviewed.registryId, reviewed.inboxId, selected.record, () -> {
                ReceivedImagePayload actual = stage.read(selected.record.stageLocalId);
                if (!selected.matchesOffer(actual.offer)) throw new IOException();
                return actual;
            });
        } catch (ImagePreparationException safe) {
            throw safe;
        } catch (Exception unavailable) {
            throw new IOException("Received image content is unavailable; private data is unchanged");
        } catch (OutOfMemoryError exhausted) {
            throw new ImagePreparationException(ImagePreparationException.Reason.LOW_MEMORY);
        }
    }
}
