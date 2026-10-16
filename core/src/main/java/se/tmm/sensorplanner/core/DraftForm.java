package se.tmm.sensorplanner.core;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Parses local form text without storage or SDK side effects. */
public final class DraftForm {
    public enum Field {
        DISPLAY_ID, TYPE, STATUS, OWNER, DEPLOYED_AT, COMMENT, LATITUDE, LONGITUDE, BEARING, FORM
    }

    public enum Error {
        REQUIRED, INVALID_NUMBER, OUT_OF_RANGE, BEARING_UNSUPPORTED, INVALID_TIMESTAMP,
        COMMENT_TOO_LONG, CLOCK_BEFORE_UPDATE, REVISION_EXHAUSTED
    }

    public static final class Result {
        public final TmmDraft draft;
        public final Map<Field, Error> errors;

        private Result(TmmDraft draft, EnumMap<Field, Error> errors) {
            this.draft = draft;
            this.errors = Collections.unmodifiableMap(new EnumMap<>(errors));
        }

        public boolean isValid() {
            return draft != null;
        }
    }

    private DraftForm() {}

    public static Result validate(TmmDraft original, String displayId, DeviceType type,
                                  DeviceStatus status, String owner, String deployedAt,
                                  String comment, String latitude, String longitude,
                                  String bearing, Instant now) {
        Objects.requireNonNull(now, "now");
        EnumMap<Field, Error> errors = new EnumMap<>(Field.class);
        if (text(displayId).isEmpty()) errors.put(Field.DISPLAY_ID, Error.REQUIRED);
        if (type == null) errors.put(Field.TYPE, Error.REQUIRED);
        if (status == null) errors.put(Field.STATUS, Error.REQUIRED);
        if (text(owner).isEmpty()) errors.put(Field.OWNER, Error.REQUIRED);
        if (comment != null && comment.length() > TmmDraft.MAX_COMMENT_LENGTH) {
            errors.put(Field.COMMENT, Error.COMMENT_TOO_LONG);
        }
        Double lat = decimal(latitude, Field.LATITUDE, -90, 90, false, errors);
        Double lon = decimal(longitude, Field.LONGITUDE, -180, 180, false, errors);
        Double direction = null;
        if (!text(bearing).isEmpty()) {
            direction = decimal(bearing, Field.BEARING, 0, 360, true, errors);
            if (type != null && !type.supportsBearing()) {
                errors.put(Field.BEARING, Error.BEARING_UNSUPPORTED);
            }
        }
        Instant deployed = null;
        if (!text(deployedAt).isEmpty()) {
            try {
                if (!text(deployedAt).endsWith("Z")) throw new DateTimeParseException("UTC required", "", 0);
                deployed = Instant.parse(text(deployedAt));
            } catch (DateTimeParseException invalid) {
                errors.put(Field.DEPLOYED_AT, Error.INVALID_TIMESTAMP);
            }
        }
        if (original != null) {
            if (now.isBefore(original.updatedAt)) errors.put(Field.FORM, Error.CLOCK_BEFORE_UPDATE);
            else if (original.revision == Long.MAX_VALUE) errors.put(Field.FORM, Error.REVISION_EXHAUSTED);
        }
        if (!errors.isEmpty()) return new Result(null, errors);
        Position position = new Position(lat, lon);
        TmmDraft draft = original == null
                ? new TmmDraft(UUID.randomUUID(), displayId, type, status, owner, now, now,
                        deployed, comment, position, direction, null, 1)
                : original.edit(displayId, type, status, owner, deployed, comment, position,
                        direction, original.imageRef, now);
        return new Result(draft, errors);
    }

    /** Prefill with ordinary decimal notation accepted by this form's parser. */
    public static String decimalText(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public static boolean hasDuplicateId(String displayId, UUID excludedUid,
                                         Collection<TmmDraft> drafts) {
        String candidate = text(displayId);
        if (candidate.isEmpty()) return false;
        for (TmmDraft draft : drafts) {
            if (!draft.uid.equals(excludedUid) && candidate.equalsIgnoreCase(draft.displayId.trim())) {
                return true;
            }
        }
        return false;
    }

    private static Double decimal(String input, Field field, double min, double max,
                                   boolean exclusiveMax, EnumMap<Field, Error> errors) {
        String value = text(input);
        if (value.isEmpty()) {
            errors.put(field, Error.REQUIRED);
            return null;
        }
        // A single decimal separator is allowed; exponents, grouping and hex are not form input.
        if (!value.matches("[+-]?(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)")) {
            errors.put(field, Error.INVALID_NUMBER);
            return null;
        }
        double parsed = Double.parseDouble(value.replace(',', '.'));
        if (!Double.isFinite(parsed) || parsed < min || (exclusiveMax ? parsed >= max : parsed > max)) {
            errors.put(field, Error.OUT_OF_RANGE);
            return null;
        }
        return parsed;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
