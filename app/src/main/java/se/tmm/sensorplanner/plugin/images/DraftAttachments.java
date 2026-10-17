package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import se.tmm.sensorplanner.core.DraftStore;
import se.tmm.sensorplanner.core.TmmDraft;
import se.tmm.sensorplanner.images.LocalImages;

/** Call only on the planner's serialized I/O worker, on API 26+. */
@TargetApi(26)
public final class DraftAttachments {
    private final DraftStore store;
    private final LocalImages images;

    public DraftAttachments(DraftStore store, LocalImages images) {
        this.store = Objects.requireNonNull(store, "store");
        this.images = Objects.requireNonNull(images, "images");
    }

    public Result attach(TmmDraft original, LocalImages.Pending pending, Instant now) throws IOException {
        pending.commit(reference -> store.save(withImage(original, reference, now)));
        return afterCommit(original.imageRef);
    }

    public Result detach(TmmDraft original, Instant now) throws IOException {
        store.save(withImage(original, null, now));
        return afterCommit(original.imageRef);
    }

    public Result delete(TmmDraft original) throws IOException {
        store.delete(original.uid, original.revision);
        return afterCommit(original.imageRef);
    }

    private static TmmDraft withImage(TmmDraft original, String reference, Instant now) {
        return original.edit(original.displayId, original.type, original.status, original.owner,
                original.deployedAt, original.comment, original.position, original.bearingDeg, reference, now);
    }

    private Result afterCommit(String previous) throws IOException {
        // A failed fresh read is ambiguous: preserve both copies and report the error.
        List<TmmDraft> snapshot = store.load();
        if (previous != null) {
            for (TmmDraft draft : snapshot) {
                if (previous.equals(draft.imageRef)) return new Result(snapshot, false);
            }
            try {
                images.deleteUnreferenced(previous);
            } catch (IOException e) {
                return new Result(snapshot, true);
            }
        }
        return new Result(snapshot, false);
    }

    public static final class Result {
        public final List<TmmDraft> drafts;
        public final boolean cleanupFailed;

        private Result(List<TmmDraft> drafts, boolean cleanupFailed) {
            this.drafts = drafts;
            this.cleanupFailed = cleanupFailed;
        }
    }
}
