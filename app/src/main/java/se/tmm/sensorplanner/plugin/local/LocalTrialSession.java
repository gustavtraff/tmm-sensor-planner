package se.tmm.sensorplanner.plugin.local;

import java.nio.file.Path;
import se.tmm.sensorplanner.core.FeedSyncState;

/**
 * Process-local consent; no network configuration or send capability. ADR 0013: a readable feed sync
 * state with following on also allows connected local use for this load, without the ADR 0009 dialog,
 * and blocks the lab tools for the session exactly like trial consent.
 */
public final class LocalTrialSession {
    private volatile boolean connectedTrial, followedFeed;

    public boolean canUseLocal(boolean offline) { return offline || connectedTrial || followedFeed; }
    /** Lab tools stay off for the whole session after any connected local use, even back offline. */
    public boolean canUseLab(boolean offline) { return offline && !connectedTrial && !followedFeed; }
    public boolean isConnectedTrial() { return connectedTrial; }
    public boolean isFollowedFeed() { return followedFeed; }
    public void startConnectedTrial() { connectedTrial = true; }
    /** Reads only; an absent, unreadable or not-following file leaves today's gate and is never reset. */
    public boolean resumeFollowedFeed(Path syncDirectory) {
        try {
            FeedSyncState.Snapshot saved = new FeedSyncState(syncDirectory).read();
            if (saved != null && saved.following) followedFeed = true;
        } catch (Exception | OutOfMemoryError unreadable) { /* today's behaviour */ }
        return followedFeed;
    }
    public void reset() { connectedTrial = false; followedFeed = false; }
}
