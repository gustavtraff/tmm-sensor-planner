package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/** Synthetic acceptance inputs only; never imported automatically by the plugin. */
public final class HundredDeviceScenarioTest {
    private static final int COUNT = 100;
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final DeviceType[] TYPES = {DeviceType.MINI, DeviceType.SCOUT, DeviceType.PIR, DeviceType.GATEWAY};
    private static final DeviceStatus[] STATUSES = {DeviceStatus.PLANNED, DeviceStatus.DEPLOYED,
            DeviceStatus.ACTIVE, DeviceStatus.RETRIEVED, DeviceStatus.CANCELLED};

    public static void main(String[] args) throws Exception {
        if (!HundredDeviceScenarioTest.class.desiredAssertionStatus())
            throw new IllegalStateException("Run this synthetic test utility with -ea");
        if (args.length != 2) throw new IllegalArgumentException("Expected write, verify or exercise and a directory");
        Path directory = Paths.get(args[1]);
        switch (args[0]) {
            case "write":
                // Refuse existing files, directories and symlinks before touching any snapshot.
                Files.createDirectory(directory);
                DraftStore store = new DraftStore(directory);
                for (int i = 0; i < COUNT; i++) store.save(sample(i));
                verify(directory);
                System.out.println("Synthetic 100-device snapshot created and verified.");
                break;
            case "verify":
                verify(directory);
                System.out.println("Separate-process 100-device reopen and distance checks passed.");
                break;
            case "exercise":
                exercise(directory);
                System.out.println("100-device move, status, delete, stale-write and overwrite-refusal checks passed.");
                break;
            default: throw new IllegalArgumentException("Unknown scenario mode");
        }
    }

    private static TmmDraft sample(int index) {
        DeviceType type = TYPES[index % 4];
        return new TmmDraft(new UUID(0, index + 1),
                String.format(Locale.ROOT, "SYNTHETIC-%03d", index == 50 ? 1 : index + 1),
                type, STATUSES[index % 5], "SYNTHETIC SCALE GROUP",
                TIME.plusNanos(index), TIME.plusNanos(index), index % 2 == 0 ? TIME.plusSeconds(1) : null,
                index % 2 == 0 ? "Synthetic scale fixture åäö 🌲" : "",
                new Position(0, index * index * 0.00001),
                type.supportsBearing() && index % 8 < 4 ? 359.125 : null, null, 1);
    }

    private static void verify(Path directory) throws Exception {
        byte[] before = Files.readAllBytes(directory.resolve("drafts.bin"));
        List<TmmDraft> loaded = new DraftStore(directory).load();
        assert loaded.size() == COUNT;
        for (int i = 0; i < COUNT; i++) same(sample(i), loaded.get(i));
        assert loaded.get(0).displayId.equals(loaded.get(50).displayId);
        assert !loaded.get(0).uid.equals(loaded.get(50).uid);
        checkDistances(loaded);
        assert Arrays.equals(before, Files.readAllBytes(directory.resolve("drafts.bin")));
    }

    private static void exercise(Path directory) throws Exception {
        verify(directory);
        byte[] before = Files.readAllBytes(directory.resolve("drafts.bin"));
        try {
            main(new String[]{"write", directory.toString()});
            throw new AssertionError("Existing scenario overwritten");
        } catch (java.nio.file.FileAlreadyExistsException expected) { }
        assert Arrays.equals(before, Files.readAllBytes(directory.resolve("drafts.bin")));

        Path existingFile = Files.createTempFile(directory, "overwrite-guard-", ".tmp");
        Files.write(existingFile, new byte[]{7});
        try {
            main(new String[]{"write", existingFile.toString()});
            throw new AssertionError("Existing file overwritten");
        } catch (java.nio.file.FileAlreadyExistsException expected) { }
        assert Arrays.equals(new byte[]{7}, Files.readAllBytes(existingFile));
        Files.delete(existingFile);

        DraftStore store = new DraftStore(directory);
        TmmDraft original = sample(10);
        TmmDraft moved = edit(original, DeviceStatus.ACTIVE, new Position(0, 0.000001));
        store.save(moved);
        List<TmmDraft> current = new DraftStore(directory).load();
        assert DistanceCalculator.nearestOther(current.get(0), current).get().draft.uid.equals(moved.uid);
        byte[] committed = Files.readAllBytes(directory.resolve("drafts.bin"));
        try {
            store.save(moved);
            throw new AssertionError("Stale write accepted");
        } catch (IOException expected) { }
        assert Arrays.equals(committed, Files.readAllBytes(directory.resolve("drafts.bin")));
        checkDistances(current);

        TmmDraft retrieved = edit(moved, DeviceStatus.RETRIEVED, moved.position);
        store.save(retrieved);
        current = new DraftStore(directory).load();
        assert DistanceCalculator.nearestOther(current.get(0), current).get().draft.uid.equals(sample(1).uid);
        checkDistances(current);

        store.delete(sample(1).uid, 1);
        current = new DraftStore(directory).load();
        assert current.size() == 99;
        assert DistanceCalculator.nearestOther(current.get(0), current).get().draft.uid.equals(sample(2).uid);
        for (TmmDraft saved : current) {
            int index = (int) saved.uid.getLeastSignificantBits() - 1;
            same(index == 10 ? retrieved : sample(index), saved);
        }
        checkDistances(current);
    }

    private static TmmDraft edit(TmmDraft original, DeviceStatus status, Position position) {
        return original.edit(original.displayId, original.type, status, original.owner,
                original.deployedAt, original.comment, position, original.bearingDeg,
                original.imageRef, original.updatedAt.plusSeconds(1));
    }

    private static boolean eligible(TmmDraft draft) {
        return draft.status == DeviceStatus.PLANNED || draft.status == DeviceStatus.DEPLOYED
                || draft.status == DeviceStatus.ACTIVE;
    }

    // At latitude zero, arc length is radius × absolute longitude angle, independently of haversine.
    private static double expectedMeters(TmmDraft a, TmmDraft b) {
        assert a.position.latitude == 0 && b.position.latitude == 0;
        return 6371008.8 * Math.PI / 180 * Math.abs(a.position.longitude - b.position.longitude);
    }

    private static void checkDistances(List<TmmDraft> drafts) {
        for (TmmDraft source : drafts) {
            TmmDraft nearest = null;
            int gatewayCount = 0;
            for (TmmDraft candidate : drafts) {
                if (candidate.uid.equals(source.uid) || !eligible(candidate)) continue;
                if (candidate.type == DeviceType.GATEWAY) gatewayCount++;
                if (nearest == null || expectedMeters(source, candidate) < expectedMeters(source, nearest)
                        || (expectedMeters(source, candidate) == expectedMeters(source, nearest)
                            && candidate.uid.compareTo(nearest.uid) < 0)) nearest = candidate;
            }
            Optional<DistanceCalculator.Distance> actual = DistanceCalculator.nearestOther(source, drafts);
            assert nearest != null && actual.isPresent();
            assert nearest.uid.equals(actual.get().draft.uid);
            assert Math.abs(expectedMeters(source, nearest) - actual.get().meters) < 0.000001;
            if (source.type != DeviceType.GATEWAY) continue;
            List<DistanceCalculator.Distance> gateways = DistanceCalculator.otherGateways(source, drafts);
            assert gateways.size() == gatewayCount;
            Set<UUID> seen = new HashSet<>();
            double previous = -1;
            for (DistanceCalculator.Distance distance : gateways) {
                assert distance.draft.type == DeviceType.GATEWAY && eligible(distance.draft);
                assert !distance.draft.uid.equals(source.uid) && seen.add(distance.draft.uid);
                double expected = expectedMeters(source, distance.draft);
                assert Math.abs(expected - distance.meters) < 0.000001;
                assert expected >= previous;
                previous = expected;
            }
        }
    }

    private static void same(TmmDraft a, TmmDraft b) {
        assert a.uid.equals(b.uid) && a.displayId.equals(b.displayId) && a.type == b.type && a.status == b.status;
        assert a.owner.equals(b.owner) && a.createdAt.equals(b.createdAt) && a.updatedAt.equals(b.updatedAt);
        assert Objects.equals(a.deployedAt, b.deployedAt) && a.comment.equals(b.comment);
        assert a.position.latitude == b.position.latitude && a.position.longitude == b.position.longitude;
        assert Objects.equals(a.bearingDeg, b.bearingDeg) && Objects.equals(a.imageRef, b.imageRef);
        assert a.revision == b.revision;
    }
}
