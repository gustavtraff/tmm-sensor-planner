package se.tmm.sensorplanner.plugin;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Disposable wire fixture, not the production draft format or a publishing adapter. */
final class CotProbeFixture {
    static final String TAG = "tmmProbe";
    static final String[] TYPES = {"Mini", "Scout", "PIR", "Gateway"};

    private CotProbeFixture() { }

    static final class Invalid extends IllegalArgumentException {
        final String field;
        Invalid(String field) {
            super("Synthetic fixture mismatch");
            this.field = field;
        }
    }

    static void selfTest() {
        for (String type : TYPES) {
            CotEvent original = create(type);
            CotEvent decoded = CotEvent.parse(original.toString());
            if (!original.getUID().equals(decoded.getUID()) || !type.equals(validate(decoded))) {
                throw new IllegalStateException("SDK fixture round-trip failed");
            }
        }
        CotEvent future = create("PIR");
        future.getDetail().getFirstChildByName(0, TAG).setAttribute("schemaVersion", "2");
        rejects(() -> validate(future));
        CotEvent image = create("PIR");
        image.getDetail().getFirstChildByName(0, TAG).setAttribute("imageRef", "local-only");
        rejects(() -> validate(image));
        CotEvent moved = create("Scout");
        moved.setPoint(new CotPoint(0, 1, 0, 100, 100));
        rejects(() -> validate(moved));
    }

    private static void rejects(Runnable invalid) {
        try {
            invalid.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("SDK fixture validation failed open");
    }

    static CotEvent create(String type) {
        CotEvent event = new CotEvent();
        event.setUID(UUID.randomUUID().toString());
        event.setType("b-m-p-w");
        event.setHow("h-g-i-g-o");
        event.setPoint(new CotPoint(0, 0, 0, 100, 100));
        refreshTime(event);
        CotDetail detail = new CotDetail("detail");
        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign", "TMM-COT-PROBE-" + type);
        detail.addChild(contact);
        CotDetail payload = new CotDetail(TAG);
        for (Map.Entry<String, String> field : fields(type).entrySet()) {
            payload.setAttribute(field.getKey(), field.getValue());
        }
        detail.addChild(payload);
        event.setDetail(detail);
        // Exercise SDK XML escaping before exposing any send operation.
        validate(CotEvent.parse(event.toString()));
        return event;
    }

    static void refreshTime(CotEvent event) {
        CoordinatedTime now = new CoordinatedTime();
        event.setTime(now);
        event.setStart(now);
        event.setStale(now.addHours(1));
    }

    static boolean isProbe(CotEvent event) {
        return event != null && event.getDetail() != null
                && event.getDetail().getFirstChildByName(0, TAG) != null;
    }

    static String validate(CotEvent event) {
        if (!isProbe(event) || !event.isValid()
                || !UUID.fromString(event.getUID()).toString().equals(event.getUID())
                || !"b-m-p-w".equals(event.getType())
                || event.getCotPoint().getLat() != 0 || event.getCotPoint().getLon() != 0
                || event.getCotPoint().getHae() != 0 || event.getCotPoint().getCe() != 100
                || event.getCotPoint().getLe() != 100) {
            throw new Invalid("envelope");
        }
        CotDetail payload = event.getDetail().getFirstChildByName(0, TAG);
        String type = payload.getAttribute("type");
        for (Map.Entry<String, String> field : fields(type).entrySet()) {
            if (!field.getValue().equals(payload.getAttribute(field.getKey()))) {
                throw new Invalid(field.getKey());
            }
        }
        if (payload.getAttribute("imageRef") != null) {
            throw new IllegalArgumentException("Local image references must not be transmitted");
        }
        return type;
    }

    static CotEvent forReturn(CotEvent received) {
        CotEvent clean = create(validate(received));
        clean.setUID(received.getUID());
        return clean;
    }

    static String summary(CotEvent event) {
        String type = validate(event);
        StringBuilder text = new StringBuilder("ENDAST SYNTETISKT LABB\nUUID: ")
                .append(event.getUID()).append("\nPosition: 0°, 0°\n");
        for (Map.Entry<String, String> field : fields(type).entrySet()) {
            text.append(field.getKey()).append(": ").append(field.getValue()).append('\n');
        }
        return text.append("Bild och lokal filsökväg: ingår inte").toString();
    }

    private static Map<String, String> fields(String type) {
        boolean supported = false;
        for (String allowed : TYPES) supported |= allowed.equals(type);
        if (!supported) throw new IllegalArgumentException("Unknown synthetic fixture type");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("experiment", "issue-5");
        fields.put("schemaVersion", "1");
        fields.put("displayId", "TMM-COT-PROBE-" + type);
        fields.put("type", type);
        fields.put("status", "ACTIVE");
        fields.put("owner", "TEST-ROLE & <lab>");
        fields.put("createdAt", "2026-01-01T00:00:00Z");
        fields.put("updatedAt", "2026-01-02T00:00:00Z");
        fields.put("deployedAt", "2026-01-01T12:00:00Z");
        fields.put("comment", "Syntetiskt prov: åäö & < > \" '");
        fields.put("bearingDeg", "Scout".equals(type) || "PIR".equals(type) ? "359.125" : "");
        fields.put("revision", "7");
        fields.put("imageIncluded", "false");
        return fields;
    }
}
