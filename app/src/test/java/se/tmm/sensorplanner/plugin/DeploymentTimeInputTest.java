package se.tmm.sensorplanner.plugin;

import org.junit.Test;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;

public class DeploymentTimeInputTest {
    @Test public void localSelectionConvertsToUtcAcrossDateBoundary() {
        assertEquals(Instant.parse("2026-10-17T22:30:00Z"), DeploymentTimeInput.selectedInstant(
                LocalDate.of(2026, 10, 18), 0, 30, ZoneId.of("Europe/Stockholm")));
        assertEquals(Instant.parse("2027-01-01T04:45:00Z"), DeploymentTimeInput.selectedInstant(
                LocalDate.of(2026, 12, 31), 23, 45, ZoneId.of("America/New_York")));
    }

    @Test public void clockOverlapUsesFirstOccurrence() {
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), DeploymentTimeInput.selectedInstant(
                LocalDate.of(2026, 10, 25), 2, 30, ZoneId.of("Europe/Stockholm")));
    }

    @Test(expected = DateTimeException.class) public void nonexistentClockTimeIsRejected() {
        DeploymentTimeInput.selectedInstant(LocalDate.of(2026, 3, 29), 2, 30, ZoneId.of("Europe/Stockholm"));
    }
}
