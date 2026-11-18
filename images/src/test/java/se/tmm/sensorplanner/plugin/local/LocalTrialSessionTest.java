package se.tmm.sensorplanner.plugin.local;

import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import se.tmm.sensorplanner.core.FeedSyncState;
import static org.junit.Assert.*;

public class LocalTrialSessionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void connectedConsentAllowsOnlyLocalWorkEvenIfTheNetworkChanges() {
        LocalTrialSession session = new LocalTrialSession();
        assertTrue(session.canUseLocal(true));
        assertTrue(session.canUseLab(true));
        assertFalse(session.canUseLocal(false));
        assertFalse(session.canUseLab(false));
        session.startConnectedTrial();
        assertTrue(session.canUseLocal(false));
        assertTrue(session.canUseLocal(true));
        assertFalse(session.canUseLab(false));
        assertFalse(session.canUseLab(true));
        session.reset();
        assertFalse(session.isConnectedTrial());
        assertFalse(session.canUseLocal(false));
        assertTrue(session.canUseLocal(true));
        assertTrue(session.canUseLab(true));
        assertFalse(new LocalTrialSession().canUseLocal(false));
    }

    @Test public void followedFeedAllowsConnectedLocalUseButNeverTheConnectedLab() throws Exception {
        LocalTrialSession session = new LocalTrialSession();
        Path dir = syncState(true);
        byte[] before = Files.readAllBytes(dir.resolve("sync.bin"));
        assertTrue(session.resumeFollowedFeed(dir));
        assertTrue(session.isFollowedFeed());
        assertFalse(session.isConnectedTrial());
        assertTrue(session.canUseLocal(false));
        assertFalse(session.canUseLab(false));
        assertArrayEquals("Reading never rewrites the file", before, Files.readAllBytes(dir.resolve("sync.bin")));
        session.reset();
        assertFalse(session.isFollowedFeed());
        assertFalse("Unload or restart must read the file again", session.canUseLocal(false));
    }

    @Test public void missingStoppedOrCorruptStateKeepsTodaysGate() throws Exception {
        Path missing = temp.newFolder().toPath().resolve("feed-sync-v1");
        Path stopped = syncState(false);
        Path corrupt = syncState(true);
        byte[] bytes = Files.readAllBytes(corrupt.resolve("sync.bin"));
        bytes[bytes.length - 1] ^= 1;
        Files.write(corrupt.resolve("sync.bin"), bytes);
        for (Path dir : new Path[]{missing, stopped, corrupt}) {
            LocalTrialSession session = new LocalTrialSession();
            assertFalse(dir.toString(), session.resumeFollowedFeed(dir));
            assertFalse(session.canUseLocal(false));
            assertTrue(session.canUseLocal(true));
            assertTrue(session.canUseLab(true));
            assertFalse(session.canUseLab(false));
        }
        assertFalse("A reader never creates the file", Files.exists(missing));
        assertArrayEquals("A corrupt file is never reset", bytes, Files.readAllBytes(corrupt.resolve("sync.bin")));
    }

    private Path syncState(boolean following) throws Exception {
        Path dir = temp.newFolder().toPath().resolve("feed-sync-v1");
        new FeedSyncState(dir).create(new FeedSyncState.Snapshot(UUID.randomUUID(), UUID.randomUUID(), following,
                "mission-api-v1|https://lab.example.invalid:8443|00000000-0000-4000-8000-000000000242",
                "Synthetic follow", "lab.example.invalid:8089:ssl", Collections.emptyList()));
        return dir;
    }
}
