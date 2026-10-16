package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/** Synthetic form inputs only. */
public final class DraftFormTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    public static void main(String[] args) {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (!assertionsEnabled) throw new IllegalStateException("Run tests with -ea");
        createAndEdit();
        invalidFields();
        decimalBoundaries();
        duplicates();
        assert DraftForm.decimalText(0.0000001).equals("0.0000001");
        assert DraftForm.decimalText(-0.0000001).equals("-0.0000001");
        assert DraftForm.decimalText(0).equals("0");
        assert valid(null, DraftForm.decimalText(0.0000001), "0", "", NOW).isValid();
        System.out.println("Draft form tests passed (field errors, manual IDs, decimal input, edits and duplicates).");
    }

    private static void createAndEdit() {
        DraftForm.Result result = DraftForm.validate(null, " PIR 134 ", DeviceType.PIR,
                DeviceStatus.DEPLOYED, " TEST ROLE ", "2026-01-01T00:00:01Z", "Synthetic",
                " 0,5 ", "-0.25", "359,99", NOW);
        assert result.isValid() && result.errors.isEmpty();
        TmmDraft created = result.draft;
        assert created.displayId.equals("PIR 134") && created.owner.equals("TEST ROLE");
        assert created.position.latitude == 0.5 && created.position.longitude == -0.25;
        assert created.bearingDeg == 359.99 && created.status == DeviceStatus.DEPLOYED;
        assert created.deployedAt.equals(NOW.plusSeconds(1));
        assert created.createdAt.equals(NOW) && created.updatedAt.equals(NOW) && created.revision == 1;
        assert created.imageRef == null && created.comment.equals("Synthetic");

        TmmDraft original = original(3);
        DraftForm.Result edited = DraftForm.validate(original, "Custom / åäö 007", DeviceType.MINI,
                DeviceStatus.CANCELLED, "OTHER TEST ROLE", " ", null, "-90", "180", "", NOW.plusSeconds(1));
        assert edited.isValid();
        TmmDraft draft = edited.draft;
        assert draft.uid.equals(original.uid) && draft.createdAt.equals(original.createdAt);
        assert draft.imageRef.equals(original.imageRef) && draft.revision == 4;
        assert draft.updatedAt.equals(NOW.plusSeconds(1));
        assert draft.bearingDeg == null && draft.deployedAt == null && draft.comment.isEmpty();
        assert original.bearingDeg == 90 && original.deployedAt.equals(NOW);
        assert original.type == DeviceType.PIR && original.revision == 3;

        assert valid(original, "0", "0", "0", NOW).isValid();
        assert valid(original, "0", "0", "0", NOW.minusNanos(1)).errors.get(DraftForm.Field.FORM)
                == DraftForm.Error.CLOCK_BEFORE_UPDATE;
        assert valid(original(Long.MAX_VALUE), "0", "0", "0", NOW).errors.get(DraftForm.Field.FORM)
                == DraftForm.Error.REVISION_EXHAUSTED;
        String boundary = repeat("\uD83D\uDE00", 1000);
        assert DraftForm.validate(null, "ID", DeviceType.PIR, DeviceStatus.PLANNED, "TEST", "",
                boundary, "0", "0", "", NOW).isValid();
        assert DraftForm.validate(null, "ID", DeviceType.PIR, DeviceStatus.PLANNED, "TEST", "",
                boundary + "x", "0", "0", "", NOW).errors.get(DraftForm.Field.COMMENT)
                == DraftForm.Error.COMMENT_TOO_LONG;
    }

    private static void invalidFields() {
        DraftForm.Result invalid = DraftForm.validate(null, " ", null, null, null, "yesterday",
                repeat("x", 2001), "NaN", "181", "360", NOW);
        assert !invalid.isValid() && invalid.draft == null && invalid.errors.size() == 9;
        assert invalid.errors.get(DraftForm.Field.DISPLAY_ID) == DraftForm.Error.REQUIRED;
        assert invalid.errors.get(DraftForm.Field.TYPE) == DraftForm.Error.REQUIRED;
        assert invalid.errors.get(DraftForm.Field.STATUS) == DraftForm.Error.REQUIRED;
        assert invalid.errors.get(DraftForm.Field.OWNER) == DraftForm.Error.REQUIRED;
        assert invalid.errors.get(DraftForm.Field.DEPLOYED_AT) == DraftForm.Error.INVALID_TIMESTAMP;
        assert invalid.errors.get(DraftForm.Field.COMMENT) == DraftForm.Error.COMMENT_TOO_LONG;
        assert invalid.errors.get(DraftForm.Field.LATITUDE) == DraftForm.Error.INVALID_NUMBER;
        assert invalid.errors.get(DraftForm.Field.LONGITUDE) == DraftForm.Error.OUT_OF_RANGE;
        assert invalid.errors.get(DraftForm.Field.BEARING) == DraftForm.Error.OUT_OF_RANGE;
        try {
            invalid.errors.clear();
            throw new AssertionError("Mutable validation errors");
        } catch (UnsupportedOperationException expected) {
            // UI callers must not change a validation result.
        }
        for (DeviceType type : new DeviceType[] {DeviceType.MINI, DeviceType.GATEWAY}) {
            assert DraftForm.validate(null, "ID", type, DeviceStatus.PLANNED, "TEST", "", "",
                    "0", "0", "90", NOW).errors.get(DraftForm.Field.BEARING)
                    == DraftForm.Error.BEARING_UNSUPPORTED;
        }
        for (String timestamp : Arrays.asList("2026-02-30T00:00:00Z", "2026-01-01", "2026-01-01T01:00:00+01:00")) {
            assert DraftForm.validate(null, "ID", DeviceType.PIR, DeviceStatus.PLANNED, "TEST", timestamp,
                    "", "0", "0", "", NOW).errors.get(DraftForm.Field.DEPLOYED_AT)
                    == DraftForm.Error.INVALID_TIMESTAMP;
        }
    }

    private static void decimalBoundaries() {
        assert valid(null, "-90", "-180", "0", NOW).isValid();
        assert valid(null, "+90", "+180", "359.999", NOW).isValid();
        assert valid(null, ".5", "-,5", "", NOW).isValid();
        assert valid(null, "", "0", "", NOW).errors.get(DraftForm.Field.LATITUDE) == DraftForm.Error.REQUIRED;
        assert valid(null, "90.001", "0", "", NOW).errors.get(DraftForm.Field.LATITUDE) == DraftForm.Error.OUT_OF_RANGE;
        assert valid(null, "0", "-180.01", "", NOW).errors.get(DraftForm.Field.LONGITUDE) == DraftForm.Error.OUT_OF_RANGE;
        for (String number : Arrays.asList("NaN", "Infinity", "-Infinity", "1e2", "0x1p0", "1,2.3", "1 234", "1_000", ".", "--1")) {
            assert valid(null, number, "0", "", NOW).errors.get(DraftForm.Field.LATITUDE)
                    == DraftForm.Error.INVALID_NUMBER : number;
        }
        for (String bearing : Arrays.asList("360", "-0.001")) {
            assert valid(null, "0", "0", bearing, NOW).errors.get(DraftForm.Field.BEARING)
                    == DraftForm.Error.OUT_OF_RANGE;
        }
        assert valid(null, repeat("9", 400), "0", "", NOW).errors.get(DraftForm.Field.LATITUDE)
                == DraftForm.Error.OUT_OF_RANGE;
    }

    private static void duplicates() {
        TmmDraft first = original(1);
        TmmDraft second = TmmDraft.create("pir 134", DeviceType.MINI, "TEST", new Position(0, 0), NOW);
        assert DraftForm.hasDuplicateId(" PIR 134 ", null, Arrays.asList(first, second));
        assert !DraftForm.hasDuplicateId("PIR 134", first.uid, Collections.singletonList(first));
        assert DraftForm.hasDuplicateId("PIR 134", first.uid, Arrays.asList(first, second));
        assert !DraftForm.hasDuplicateId("PIR134", null, Arrays.asList(first, second));
        assert !DraftForm.hasDuplicateId(" ", null, Arrays.asList(first, second));
        assert !DraftForm.hasDuplicateId(null, null, Collections.emptyList());
        assert !first.uid.equals(second.uid);
    }

    private static DraftForm.Result valid(TmmDraft original, String lat, String lon, String bearing, Instant now) {
        return DraftForm.validate(original, "PIR 134", DeviceType.PIR, DeviceStatus.PLANNED,
                "TEST", "", "", lat, lon, bearing, now);
    }

    private static TmmDraft original(long revision) {
        return new TmmDraft(new UUID(0, 1), "PIR 134", DeviceType.PIR, DeviceStatus.ACTIVE,
                "TEST", NOW.minusSeconds(10), NOW, NOW, "Original", new Position(0, 0),
                90.0, "synthetic-local-image", revision);
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
