package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Synthetic positions only: equator, poles and date line. Run with -ea. */
public final class CoreTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    public static void main(String[] args) {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (!assertionsEnabled) throw new IllegalStateException("Run tests with -ea");
        validation();
        edits();
        geographicDistances();
        neighbors();
        statusFilteredNeighbors();
        System.out.println("Core tests passed (validation, manual IDs, edits, bearing and filtered distances).");
    }

    private static void validation() {
        new Position(-90, -180);
        new Position(90, 180);
        rejects(() -> new Position(Double.NaN, 0));
        rejects(() -> new Position(91, 0));
        rejects(() -> new Position(0, Double.POSITIVE_INFINITY));
        rejects(() -> new Position(0, -181));
        rejects(() -> TmmDraft.create(" ", DeviceType.PIR, "TEST", new Position(0, 0), NOW));
        rejects(() -> TmmDraft.create("TEST-1", DeviceType.PIR, "", new Position(0, 0), NOW));
        assert DeviceType.MINI.displayName.equals("Mini");
        assert DeviceType.SCOUT.displayName.equals("Scout");
        assert DeviceType.PIR.displayName.equals("PIR");
        assert DeviceType.GATEWAY.displayName.equals("Gateway");
        draft(1, DeviceType.MINI, 0, null);
        rejects(() -> draft(1, DeviceType.MINI, 0, 90.0));
        draft(1, DeviceType.PIR, 0, 0.0);
        draft(1, DeviceType.SCOUT, 0, 359.99);
        rejects(() -> draft(1, DeviceType.PIR, 0, 360.0));
        rejects(() -> draft(1, DeviceType.PIR, 0, -0.1));
        rejects(() -> draft(1, DeviceType.SCOUT, 0, Double.NaN));
        rejects(() -> draft(1, DeviceType.SCOUT, 0, Double.POSITIVE_INFINITY));
        rejects(() -> draft(1, DeviceType.GATEWAY, 0, 90.0));
        rejects(() -> new TmmDraft(new UUID(0, 1), "TEST", DeviceType.PIR, DeviceStatus.PLANNED,
                "TEST", NOW, NOW.minusSeconds(1), null, "", new Position(0, 0), null, null, 1));
        rejects(() -> new TmmDraft(new UUID(0, 1), "TEST", DeviceType.PIR, DeviceStatus.PLANNED,
                "TEST", NOW, NOW, null, repeated('x', TmmDraft.MAX_COMMENT_LENGTH + 1),
                new Position(0, 0), null, null, 1));
        rejects(() -> new TmmDraft(new UUID(0, 1), "TEST", DeviceType.PIR, DeviceStatus.PLANNED,
                "TEST", NOW, NOW, null, "", new Position(0, 0), null, null, 0));
        TmmDraft created = TmmDraft.create(" TEST-1 ", DeviceType.PIR, "TEST", new Position(0, 0), NOW);
        assert created.displayId.equals("TEST-1");
        assert created.status == DeviceStatus.PLANNED && created.revision == 1;
        assert created.createdAt.equals(NOW) && created.updatedAt.equals(NOW);
        assert created.comment.isEmpty() && created.imageRef == null && created.deployedAt == null;
        TmmDraft manual = TmmDraft.create(" PIR 134 ", DeviceType.PIR, " TEST ROLE ", new Position(0, 0), NOW);
        TmmDraft duplicateLabel = TmmDraft.create("PIR 134", DeviceType.PIR, "TEST ROLE", new Position(0, 0), NOW);
        assert manual.displayId.equals("PIR 134") && manual.owner.equals("TEST ROLE");
        assert !manual.uid.equals(duplicateLabel.uid);
        TmmDraft.create("Custom / åäö 007", DeviceType.MINI, "TEST", new Position(0, 0), NOW);
        new TmmDraft(new UUID(0, 1), "TEST", DeviceType.PIR, DeviceStatus.PLANNED,
                "TEST", NOW, NOW, null, repeated('x', TmmDraft.MAX_COMMENT_LENGTH),
                new Position(0, 0), null, null, 1);
    }

    private static void edits() {
        Instant deployed = NOW.plusSeconds(10);
        Instant updated = NOW.plusSeconds(20);
        TmmDraft original = new TmmDraft(new UUID(0, 1), "PIR 134", DeviceType.PIR,
                DeviceStatus.DEPLOYED, "TEST ROLE", NOW, updated, deployed, "Keep",
                new Position(0, 0), 0.0, "owned-image", 7);
        for (DeviceStatus from : DeviceStatus.values()) {
            TmmDraft source = original.edit(original.displayId, original.type, from, original.owner,
                    deployed, original.comment, original.position, original.bearingDeg, original.imageRef, updated);
            for (DeviceStatus to : DeviceStatus.values()) {
                TmmDraft changed = source.edit(source.displayId, source.type, to, source.owner,
                        source.deployedAt, source.comment, source.position, source.bearingDeg,
                        source.imageRef, updated.plusSeconds(1));
                assert changed.status == to && changed.revision == source.revision + 1;
                assert changed.uid.equals(original.uid) && changed.createdAt.equals(NOW);
                assert changed.deployedAt.equals(deployed) && changed.imageRef.equals("owned-image");
                assert changed.comment.equals("Keep") && changed.updatedAt.equals(updated.plusSeconds(1));
                assert source.status == from && source.revision == 8;
            }
        }
        TmmDraft corrected = original.edit(" Scout 007 ", DeviceType.SCOUT, DeviceStatus.ACTIVE,
                " NEW ROLE ", null, "Corrected", new Position(0, 1), 359.999, null, updated);
        assert corrected.displayId.equals("Scout 007") && corrected.owner.equals("NEW ROLE");
        assert corrected.uid.equals(original.uid) && corrected.revision == 8;
        assert corrected.deployedAt == null && corrected.imageRef == null;
        assert corrected.position.longitude == 1 && corrected.bearingDeg == 359.999;
        rejects(() -> original.edit(" ", original.type, original.status, original.owner,
                deployed, "Keep", original.position, 0.0, original.imageRef, updated));
        rejects(() -> original.edit(original.displayId, DeviceType.GATEWAY, original.status,
                original.owner, deployed, "Keep", original.position, 0.0, original.imageRef, updated));
        rejects(() -> original.edit(original.displayId, original.type, original.status, original.owner,
                deployed, "Keep", original.position, 0.0, original.imageRef, updated.minusSeconds(1)));
        TmmDraft exhausted = new TmmDraft(original.uid, original.displayId, original.type,
                original.status, original.owner, NOW, updated, deployed, "Keep", original.position,
                0.0, original.imageRef, Long.MAX_VALUE);
        TmmDraft lastRevision = new TmmDraft(original.uid, original.displayId, original.type,
                original.status, original.owner, NOW, updated, deployed, "Keep", original.position,
                0.0, original.imageRef, Long.MAX_VALUE - 1);
        assert lastRevision.edit(lastRevision.displayId, lastRevision.type, lastRevision.status,
                lastRevision.owner, deployed, "Keep", lastRevision.position, 0.0,
                lastRevision.imageRef, updated).revision == Long.MAX_VALUE;
        rejects(() -> exhausted.edit(exhausted.displayId, exhausted.type, exhausted.status,
                exhausted.owner, deployed, "Keep", exhausted.position, 0.0, exhausted.imageRef, updated));
        assert original.revision == 7 && original.displayId.equals("PIR 134");
        assert original.deployedAt.equals(deployed) && original.imageRef.equals("owned-image");
    }

    private static void geographicDistances() {
        Position origin = new Position(0, 0);
        assert DistanceCalculator.meters(origin, origin) == 0;
        near(DistanceCalculator.meters(origin, new Position(0, 1)), 111195.08, 0.1);
        near(DistanceCalculator.meters(origin, new Position(1, 0)), 111195.08, 0.1);
        near(DistanceCalculator.meters(new Position(0, 179.9), new Position(0, -179.9)), 22239.02, 0.1);
        near(DistanceCalculator.meters(new Position(90, 0), new Position(90, 180)), 0, 0.001);
        near(DistanceCalculator.meters(origin, new Position(0, 180)), 20015114.44, 0.1);
        near(DistanceCalculator.meters(origin, new Position(0, 1)),
                DistanceCalculator.meters(new Position(0, 1), origin), 0.0001);
    }

    private static void neighbors() {
        TmmDraft gateway = draft(1, DeviceType.GATEWAY, 0, null);
        TmmDraft sameUidElsewhere = draft(1, DeviceType.GATEWAY, 0.001, null);
        TmmDraft farGateway = draft(2, DeviceType.GATEWAY, 2, null);
        TmmDraft nearGateway = draft(3, DeviceType.GATEWAY, 1, null);
        TmmDraft pir = draft(4, DeviceType.PIR, 0.5, 0.0);
        List<TmmDraft> drafts = Arrays.asList(gateway, sameUidElsewhere, farGateway, nearGateway, pir);
        assert DistanceCalculator.nearestOther(gateway, drafts).get().draft.uid.equals(pir.uid);
        List<DistanceCalculator.Distance> gateways = DistanceCalculator.otherGateways(gateway, drafts);
        assert gateways.size() == 2;
        assert gateways.get(0).draft.uid.equals(nearGateway.uid);
        assert gateways.get(0).meters < gateways.get(1).meters;
        assert !DistanceCalculator.nearestOther(gateway, Collections.singletonList(sameUidElsewhere)).isPresent();
        assert DistanceCalculator.otherGateways(gateway, Collections.emptyList()).isEmpty();
        rejects(() -> DistanceCalculator.otherGateways(pir, drafts));
        TmmDraft mini = draft(6, DeviceType.MINI, 0.25, null);
        assert DistanceCalculator.nearestOther(gateway, Arrays.asList(gateway, pir, mini))
                .get().draft.uid.equals(mini.uid);
        assert DistanceCalculator.otherGateways(gateway, Arrays.asList(gateway, mini)).isEmpty();
        TmmDraft movedPir = draft(4, DeviceType.PIR, 3, 0.0);
        assert DistanceCalculator.nearestOther(gateway, Arrays.asList(gateway, nearGateway, movedPir))
                .get().draft.uid.equals(nearGateway.uid);
        assert DistanceCalculator.nearestOther(gateway, Collections.singletonList(farGateway))
                .get().draft.uid.equals(farGateway.uid);
        TmmDraft tiedGateway = draft(5, DeviceType.GATEWAY, -1, null);
        assert DistanceCalculator.otherGateways(gateway, Arrays.asList(tiedGateway, nearGateway))
                .get(0).draft.uid.equals(nearGateway.uid);
    }

    private static TmmDraft draft(long id, DeviceType type, double longitude, Double bearing) {
        return new TmmDraft(new UUID(0, id), "TEST-" + id, type, DeviceStatus.PLANNED,
                "TEST-ROLE", NOW, NOW, null, "", new Position(0, longitude), bearing, null, 1);
    }

    private static void statusFilteredNeighbors() {
        TmmDraft source = draft(1, DeviceType.GATEWAY, 0, null);
        TmmDraft planned = draft(2, DeviceType.GATEWAY, 1, null);
        TmmDraft deployed = withStatus(draft(3, DeviceType.SCOUT, 0.5, 0.0), DeviceStatus.DEPLOYED);
        TmmDraft active = withStatus(draft(4, DeviceType.GATEWAY, -1, null), DeviceStatus.ACTIVE);
        TmmDraft retrieved = withStatus(draft(5, DeviceType.GATEWAY, 0.001, null), DeviceStatus.RETRIEVED);
        TmmDraft cancelled = withStatus(draft(6, DeviceType.PIR, 0.002, 0.0), DeviceStatus.CANCELLED);
        List<TmmDraft> all = Arrays.asList(active, cancelled, retrieved, deployed, planned, source);
        for (DeviceStatus state : DeviceStatus.values()) {
            TmmDraft selected = withStatus(source, state);
            assert DistanceCalculator.nearestOther(selected, all).get().draft.uid.equals(deployed.uid);
            List<DistanceCalculator.Distance> gateways = DistanceCalculator.otherGateways(selected, all);
            assert gateways.size() == 2;
            assert gateways.get(0).draft.uid.equals(planned.uid);
            assert gateways.get(1).draft.uid.equals(active.uid);
        }
        assert !DistanceCalculator.nearestOther(source, Arrays.asList(retrieved, cancelled)).isPresent();
        assert DistanceCalculator.otherGateways(source, Arrays.asList(retrieved, cancelled)).isEmpty();
        assert DistanceCalculator.nearestOther(source, Arrays.asList(active, planned))
                .get().draft.uid.equals(planned.uid);
        assert DistanceCalculator.nearestOther(source, Arrays.asList(retrieved, planned))
                .get().draft.uid.equals(planned.uid);
        assert DistanceCalculator.nearestOther(source, Arrays.asList(withStatus(retrieved, DeviceStatus.ACTIVE), planned))
                .get().draft.uid.equals(retrieved.uid);
        assert !DistanceCalculator.nearestOther(source,
                Collections.singletonList(withStatus(source, DeviceStatus.ACTIVE))).isPresent();
    }

    private static TmmDraft withStatus(TmmDraft draft, DeviceStatus status) {
        return draft.edit(draft.displayId, draft.type, status, draft.owner, draft.deployedAt,
                draft.comment, draft.position, draft.bearingDeg, draft.imageRef, NOW);
    }

    private static String repeated(char character, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, character);
        return new String(chars);
    }

    private static void near(double actual, double expected, double tolerance) {
        assert Math.abs(actual - expected) <= tolerance : "Unexpected synthetic distance";
    }

    private static void rejects(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected validation rejection");
    }
}
