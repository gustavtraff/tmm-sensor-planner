package se.tmm.sensorplanner.plugin.publication;

import android.annotation.TargetApi;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import se.tmm.sensorplanner.core.*;

/** Fixed synthetic canaries only; no saved-draft, file, source-context or image input. */
@TargetApi(26)
public final class PublicationEnvelopeFixtures {
    private PublicationEnvelopeFixtures() { }
    public static TmmDraft draft(DeviceType type) {
        Instant time = Instant.parse("2026-01-01T00:00:00.123456789Z");
        int index = java.util.Arrays.asList(DeviceType.MINI, DeviceType.SCOUT, DeviceType.PIR, DeviceType.GATEWAY).indexOf(type);
        double[] latitude = {0.123456789012, 0.223456789012, 0.323456789012, 0.423456789012};
        return new TmmDraft(UUID.fromString(String.format(Locale.ROOT, "00000000-0000-4000-8018-%012d", index + 1)),
                "TMM-PUB18-" + type.name(), type, DeviceStatus.PLANNED, "SYNTHETIC ROLE ÅÄÖ", time,
                time.plusSeconds(10), null, "Synthetic only\nTab:\tåäö < & > 🌲",
                new Position(latitude[index], -0.234567891234),
                type.supportsBearing() ? Double.valueOf(123.45678901234567) : null, null, 1);
    }
}
