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
        geographicDistances();
        neighbors();
        System.out.println("Core tests passed (validation, bearing, distances and neighbor updates).");
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
