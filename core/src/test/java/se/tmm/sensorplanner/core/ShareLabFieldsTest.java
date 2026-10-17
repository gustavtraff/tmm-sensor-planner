package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.util.*;

public final class ShareLabFieldsTest {
    public static void main(String[] args) {
        for (DeviceType type : DeviceType.values()) for (DeviceStatus status : DeviceStatus.values()) {
            TmmDraft draft = new TmmDraft(new UUID(0, 14), "PIR 134 åäö & < > \" '", type, status,
                    "TEST ROLE", Instant.EPOCH, Instant.EPOCH.plusNanos(123), null,
                    "Synthetic\tåäö & < > \" '\r\n🌲", new Position(.001234567891, -.002345678912),
                    type.supportsBearing() ? 359.125 : null, "must-stay-local", Long.MAX_VALUE);
            ShareReview.Metadata metadata = new ShareReview(draft, "synthetic", "Synthetic recipient").metadata;
            Map<String, String> fields = ShareLabFields.encode(metadata);
            assert fields.size() == 15 && !fields.containsKey("revision") && !fields.containsKey("imageRef");
            assert !fields.toString().contains(draft.imageRef);
            ShareReview.Metadata read = ShareLabFields.decode(draft.uid.toString(), ShareLabFields.envelopePosition(draft.position), fields);
            assert read.uid.equals(metadata.uid) && read.displayId.equals(metadata.displayId);
            assert read.type == type && read.status == status && read.owner.equals(draft.owner);
            assert read.comment.equals(draft.comment) && read.updatedAt.equals(draft.updatedAt);
            assert read.deployedAt == null && Objects.equals(read.bearingDeg, draft.bearingDeg);
            assert read.position.latitude == draft.position.latitude && read.position.longitude == draft.position.longitude;
            for (String key : fields.keySet()) {
                Map<String, String> missing = new LinkedHashMap<>(fields);
                missing.remove(key); rejects(draft.uid.toString(), missing);
            }
            for (String[] invalid : new String[][]{{"schemaVersion", "2"}, {"imageIncluded", "true"},
                    {"imageRef", "private"}, {"revision", "7"}, {"type", "unknown"}, {"status", "unknown"},
                    {"commentUtf8", "wyg="}, {"commentUtf8", "Zg"}, {"ownerUtf8", "IA=="},
                    {"bearingDeg", "NaN"}, {"createdAt", "invalid"}, {"bearingReference", "magnetic"}}) {
                Map<String, String> changed = new LinkedHashMap<>(fields);
                changed.put(invalid[0], invalid[1]); rejects(draft.uid.toString(), changed);
            }
            rejects("0-0-0-0-14", fields);
            rejects(null, fields);
        }
        TmmDraft deployed = new TmmDraft(new UUID(0, 15), "Gateway 1", DeviceType.GATEWAY, DeviceStatus.DEPLOYED,
                "TEST ROLE", Instant.EPOCH, Instant.EPOCH.plusSeconds(2), Instant.EPOCH.plusSeconds(1),
                "", new Position(0, 0), null, null, 1);
        ShareReview.Metadata metadata = new ShareReview(deployed, "synthetic", "Synthetic recipient").metadata;
        assert ShareLabFields.decode(deployed.uid.toString(), deployed.position, ShareLabFields.encode(metadata))
                .deployedAt.equals(deployed.deployedAt);
        Map<String, String> oversized = new LinkedHashMap<>(ShareLabFields.encode(metadata));
        oversized.put("commentUtf8", String.join("", Collections.nCopies(87385, "A")));
        rejects(deployed.uid.toString(), new Position(0, 0), oversized);
        oversized = new LinkedHashMap<>(ShareLabFields.encode(metadata));
        oversized.put("createdAt", String.join("", Collections.nCopies(129, "A")));
        rejects(deployed.uid.toString(), new Position(0, 0), oversized);
        Map<String, String> canonical = ShareLabFields.encode(metadata);
        rejects(deployed.uid.toString(), new Position(.0000001, 0), canonical);
        assert ShareLabFields.envelopePosition(new Position(-90, 180)).latitude == -90;
        assert ShareLabFields.envelopePosition(new Position(-90, 180)).longitude == 180;
        System.out.println("Lab metadata fields tests passed (all types/statuses, Unicode, optional and invalid fields).");
    }

    private static void rejects(String uid, Map<String, String> fields) {
        rejects(uid, new Position(.0012346, -.0023457), fields);
    }

    private static void rejects(String uid, Position point, Map<String, String> fields) {
        try { ShareLabFields.decode(uid, point, fields); throw new AssertionError("Accepted invalid lab fields"); }
        catch (IllegalArgumentException expected) { }
    }
}
