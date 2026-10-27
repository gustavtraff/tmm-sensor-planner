package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import java.util.*;
import java.util.function.Function;
import se.tmm.sensorplanner.core.*;

/** Presentation only; equality follows ImportReview, never formatted/rounded text. */
@TargetApi(26)
final class InboxReviewText {
    static final class Row {
        final String name, local, incoming;
        final boolean changed;
        Row(String name, String local, String incoming, boolean changed) {
            this.name = name; this.local = local; this.incoming = incoming; this.changed = changed;
        }
    }
    private InboxReviewText() { }
    static List<Row> rows(TmmDraft local, TmmDraft incoming, Function<Position, String> coordinates) {
        List<Row> rows = new ArrayList<>();
        add(rows, "ID", local == null ? null : local.displayId, incoming.displayId, local, Function.identity());
        add(rows, "Typ", local == null ? null : local.type, incoming.type, local, v -> v.displayName);
        add(rows, "Status", local == null ? null : local.status, incoming.status, local, InboxReviewText::status);
        add(rows, "Ägare", local == null ? null : local.owner, incoming.owner, local, Function.identity());
        add(rows, "Skapad UTC", local == null ? null : local.createdAt, incoming.createdAt, local, Object::toString);
        add(rows, "Ändrad UTC", local == null ? null : local.updatedAt, incoming.updatedAt, local, Object::toString);
        add(rows, "Utplacerad UTC", local == null ? null : local.deployedAt, incoming.deployedAt, local, Object::toString);
        add(rows, "Kommentar", local == null ? null : local.comment, incoming.comment, local, Function.identity());
        String position = position(incoming.position, coordinates);
        rows.add(new Row("Position · MGRS", local == null ? "Saknas lokalt" : position(local.position, coordinates), position,
                local != null && (local.position.latitude != incoming.position.latitude || local.position.longitude != incoming.position.longitude)));
        rows.add(new Row("Bäring · sann nord", local == null ? "Saknas lokalt" : bearing(local.bearingDeg), bearing(incoming.bearingDeg),
                local != null && !numberEquals(local.bearingDeg, incoming.bearingDeg)));
        return Collections.unmodifiableList(rows);
    }
    private static <T> void add(List<Row> rows, String name, T local, T incoming, TmmDraft current, Function<T, String> format) {
        rows.add(new Row(name, current == null ? "Saknas lokalt" : value(local, format), value(incoming, format),
                current != null && !Objects.equals(local, incoming)));
    }
    private static <T> String value(T v, Function<T, String> format) {
        if (v == null) return "Ej angivet";
        String text = format.apply(v); return text.isEmpty() ? "(tom text)" : text;
    }
    private static String position(Position p, Function<Position, String> coordinates) {
        String grid;
        try { grid = Objects.requireNonNull(coordinates.apply(p)); }
        catch (RuntimeException unsupported) { grid = "MGRS kunde inte visas"; }
        return grid + "\nExakta lat/lon: " + p.latitude + ", " + p.longitude + "°";
    }
    private static String bearing(Double degrees) {
        return degrees == null ? "Ej angiven" : BearingMilsEdit.format(degrees) + " mils (6400)\nExakt: " + degrees + "°";
    }
    private static boolean numberEquals(Double a, Double b) { return a == null ? b == null : b != null && a.doubleValue() == b.doubleValue(); }
    static String status(DeviceStatus status) {
        switch (status) {
            case PLANNED: return "Planerad";
            case DEPLOYED: return "Utplacerad";
            case ACTIVE: return "Aktiv";
            case RETRIEVED: return "Intagen";
            case CANCELLED: return "Avbruten";
            default: return status.name();
        }
    }
    static String kind(ImportReview.Kind kind) {
        switch (kind) {
            case NEW_UID: return "Saknas lokalt i underlaget";
            case SAME_METADATA: return "Samma metadata";
            case REVIEW_REQUIRED: return "Avvikande metadata · granskning behövs";
            default: return kind.name();
        }
    }
    static String preview(String text) {
        String line = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return line.codePointCount(0, line.length()) <= 72 ? line : line.substring(0, line.offsetByCodePoints(0, 72)) + "…";
    }
}
