package se.tmm.sensorplanner.plugin.local;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.junit.Test;
import se.tmm.sensorplanner.core.*;
import static org.junit.Assert.*;

public class InboxReviewTextTest {
    private static final class Values {
        String id = "PIR 134", owner = "Synthetic role", comment = "Synthetic\n\tåäö 🌲";
        DeviceType type = DeviceType.PIR; DeviceStatus status = DeviceStatus.PLANNED;
        Instant created = Instant.EPOCH, updated = Instant.EPOCH.plusSeconds(10), deployed;
        Position position = new Position(0, 1); Double bearing = 45.0;
        TmmDraft build(String image, long revision) {
            return new TmmDraft(new UUID(0, 15), id, type, status, owner, created, updated, deployed, comment, position, bearing, image, revision);
        }
    }
    @Test public void exactFieldDifferencesAgreeWithExistingClassifier() {
        Values base = new Values(); TmmDraft local = base.build("SYNTHETIC-IMAGE", 7);
        List<Consumer<Values>> mutations = Arrays.asList(v -> v.id = "Other", v -> v.type = DeviceType.SCOUT,
                v -> v.status = DeviceStatus.ACTIVE, v -> v.owner = "Other role", v -> v.created = Instant.EPOCH.plusNanos(1),
                v -> v.updated = v.updated.plusNanos(1), v -> v.deployed = Instant.EPOCH, v -> v.comment += " ",
                v -> v.position = new Position(1e-12, 1), v -> v.bearing = 45.000000000001);
        String[] names = {"ID", "Typ", "Status", "Ägare", "Skapad UTC", "Ändrad UTC", "Utplacerad UTC", "Kommentar", "Position · MGRS", "Bäring · sann nord"};
        for (int i = 0; i < mutations.size(); i++) {
            Values changed = new Values(); mutations.get(i).accept(changed); TmmDraft incoming = changed.build(null, 1);
            assertEquals(ImportReview.Kind.REVIEW_REQUIRED, ImportReview.assess(Collections.singleton(local), incoming).kind);
            List<InboxReviewText.Row> rows = InboxReviewText.rows(local, incoming, p -> "SYNTHETIC MGRS");
            assertEquals(10, rows.size());
            for (int j = 0; j < rows.size(); j++) assertEquals(names[j], j == i, rows.get(j).changed);
        }
    }
    @Test public void signedZeroImageAndRevisionDoNotBecomeFalseDifferences() {
        Values a = new Values(), b = new Values(); a.position = new Position(-0.0, 1); a.bearing = -0.0; b.bearing = 0.0;
        TmmDraft local = a.build("SYNTHETIC-IMAGE", 99), incoming = b.build(null, 1);
        assertEquals(ImportReview.Kind.SAME_METADATA, ImportReview.assess(Collections.singleton(local), incoming).kind);
        for (InboxReviewText.Row row : InboxReviewText.rows(local, incoming, p -> "SYNTHETIC MGRS")) assertFalse(row.changed);
        for (InboxReviewText.Row row : InboxReviewText.rows(null, incoming, p -> "SYNTHETIC MGRS")) assertFalse(row.changed);
        b.bearing = null;
        assertTrue(InboxReviewText.rows(local, b.build(null, 1), p -> "SYNTHETIC MGRS").get(9).changed);
    }
    @Test public void fullValuesAndPrecisionSurvivePresentationAndFormatterFailureIsSafe() {
        Values a = new Values(), b = new Values(); b.comment = ""; b.position = new Position(1e-12, 1);
        b.bearing = 45.000000000001; b.updated = Instant.EPOCH.plusSeconds(10).plusNanos(42);
        List<InboxReviewText.Row> rows = InboxReviewText.rows(a.build(null, 1), b.build(null, 1), p -> { throw new IllegalArgumentException("PRIVATE-PATH"); });
        assertEquals(a.comment, rows.get(7).local); assertEquals("(tom text)", rows.get(7).incoming);
        assertEquals("1970-01-01T00:00:10.000000042Z", rows.get(5).incoming);
        assertTrue(rows.get(8).incoming.contains("1.0E-12")); assertFalse(rows.get(8).incoming.contains("PRIVATE-PATH"));
        assertTrue(rows.get(8).incoming.contains("MGRS kunde inte visas"));
        assertTrue(rows.get(9).incoming.contains("800 mils (6400)") && rows.get(9).incoming.contains("45.000000000001°"));
    }
    @Test public void previewIsOnlyABoundedLabelAndStatusesHaveSwedishNames() {
        String value = String.join("", Collections.nCopies(80, "🌲"));
        String preview = InboxReviewText.preview(value); assertEquals(73, preview.codePointCount(0, preview.length()));
        assertTrue(preview.endsWith("…")); assertFalse(Character.isHighSurrogate(preview.charAt(preview.length() - 2)));
        assertEquals("A B C", InboxReviewText.preview("A\nB\tC"));
        assertEquals("Planerad", InboxReviewText.status(DeviceStatus.PLANNED));
        assertEquals("Utplacerad", InboxReviewText.status(DeviceStatus.DEPLOYED));
        assertEquals("Aktiv", InboxReviewText.status(DeviceStatus.ACTIVE));
        assertEquals("Intagen", InboxReviewText.status(DeviceStatus.RETRIEVED));
        assertEquals("Avbruten", InboxReviewText.status(DeviceStatus.CANCELLED));
        assertFalse(InboxReviewText.kind(ImportReview.Kind.NEW_UID).contains("Ny"));
        assertFalse(InboxReviewText.kind(ImportReview.Kind.REVIEW_REQUIRED).contains("samtidig"));
    }
}
