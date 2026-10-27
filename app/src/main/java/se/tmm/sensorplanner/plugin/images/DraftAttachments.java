package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import se.tmm.sensorplanner.core.LocalV2;
import se.tmm.sensorplanner.core.LocalV2Store;
import se.tmm.sensorplanner.core.TmmDraft;
import se.tmm.sensorplanner.images.LocalImages;

/** Call only on the planner's serialized I/O worker, on API 26+. */
@TargetApi(26)
public final class DraftAttachments {
    private final LocalV2Store store;
    private final LocalImages images;

    public DraftAttachments(LocalV2Store store, LocalImages images) {
        this.store = Objects.requireNonNull(store, "store");
        this.images = Objects.requireNonNull(images, "images");
    }

    public Result attach(LocalV2.Selection selected, LocalImages.Pending pending, Instant now) throws IOException {
        TmmDraft original = selected.draft;
        pending.commit(reference -> store.save(selected.storeId, selected.state, withImage(original, reference, now)));
        return afterCommit(selected.storeId, original.imageRef);
    }

    public Result detach(LocalV2.Selection selected, Instant now) throws IOException {
        TmmDraft original = selected.draft;
        store.save(selected.storeId, selected.state, withImage(original, null, now));
        return afterCommit(selected.storeId, original.imageRef);
    }

    public Result delete(LocalV2.Selection selected) throws IOException {
        TmmDraft original = selected.draft;
        store.delete(selected.storeId, selected.state);
        return afterCommit(selected.storeId, original.imageRef);
    }

    private static TmmDraft withImage(TmmDraft original, String reference, Instant now) {
        return original.edit(original.displayId, original.type, original.status, original.owner,
                original.deployedAt, original.comment, original.position, original.bearingDeg, reference, now);
    }

    private Result afterCommit(java.util.UUID expectedStoreId, String previous) throws IOException {
        // A failed fresh read is ambiguous: preserve both copies and report the error.
        LocalV2Store.Inspection storage = store.inspect();
        List<TmmDraft> snapshot = storage.requireV2().activeDrafts();
        if (!storage.snapshot.storeId.equals(expectedStoreId)) throw new IOException("Local dataset changed; images preserved");
        if (previous != null) {
            for (TmmDraft draft : snapshot) {
                if (previous.equals(draft.imageRef)) return new Result(storage, false);
            }
            try {
                images.deleteUnreferenced(previous);
            } catch (IOException e) {
                return new Result(storage, true);
            }
        }
        return new Result(storage, false);
    }

    public static final class Result {
        public final LocalV2Store.Inspection storage;
        public final List<TmmDraft> drafts;
        public final boolean cleanupFailed;

        private Result(LocalV2Store.Inspection storage, boolean cleanupFailed) throws IOException {
            this.storage = storage;
            this.drafts = storage.requireV2().activeDrafts();
            this.cleanupFailed = cleanupFailed;
        }
    }
}
