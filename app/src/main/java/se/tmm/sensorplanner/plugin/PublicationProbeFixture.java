package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import se.tmm.sensorplanner.core.*;

/** Fixed sensor-pir canary matching the independent offline checker; no draft/store input. */
@TargetApi(26)
final class PublicationProbeFixture {
    static final String UID = "00000000-0000-4000-8083-000000000003";
    static final String TYPE = "b-m-p-s-p-loc";

    private PublicationProbeFixture() { }

    static TmmDraft draft() {
        Instant created = Instant.parse("2026-01-01T00:00:00.123456789Z");
        return new TmmDraft(UUID.fromString(UID), "TMM-DATASYNC-LAB-PIR", DeviceType.PIR,
                DeviceStatus.PLANNED, "SYNTHETIC-GROUP-ÅÄÖ", created, created, null,
                "Synthetic only\nTab:\tåäö < & > BASELINE", new Position(0.123456789012, -0.234567891234),
                123.45678901234567, null, 1);
    }

    static CotEvent create(ShareReview.Metadata metadata) {
        if (!ShareLabFields.encode(metadata).equals(ShareLabFields.encode(
                new PublicationPreparationReview(draft()).metadata)) || !UID.equals(metadata.uid.toString())) {
            throw new IllegalArgumentException("Only the fixed synthetic fixture is allowed");
        }
        CotEvent event = new CotEvent();
        event.setUID(UID); event.setType(TYPE); event.setHow("h-g-i-g-o");
        event.setPoint(new CotPoint(metadata.position.latitude, metadata.position.longitude,
                CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN));
        CoordinatedTime now = new CoordinatedTime();
        event.setTime(now); event.setStart(now); event.setStale(now.addHours(1));
        CotDetail detail = new CotDetail("detail");
        detail.addChild(new CotDetail("archive"));
        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign", "TMM-SENSOR-LAB-PIR");
        detail.addChild(contact);
        CotDetail fields = new CotDetail(ShareLabFields.TAG);
        for (Map.Entry<String, String> field : ShareLabFields.encode(metadata).entrySet()) {
            fields.setAttribute(field.getKey(), field.getValue());
        }
        detail.addChild(fields); event.setDetail(detail);
        return event;
    }

    static boolean matches(CotEvent event) {
        if (event == null || !event.isValid() || !UID.equals(event.getUID()) || !TYPE.equals(event.getType())
                || event.getDetail() == null || event.getCotPoint() == null
                || event.getDetail().getChildrenByName(ShareLabFields.TAG).size() != 1) return false;
        ShareReview.Metadata expected = new PublicationPreparationReview(draft()).metadata;
        CotDetail fields = event.getDetail().getFirstChildByName(0, ShareLabFields.TAG);
        if (fields.getAttributeCount() != 15 || fields.childCount() != 0
                || (fields.getInnerText() != null && !fields.getInnerText().isEmpty())) return false;
        for (Map.Entry<String, String> field : ShareLabFields.encode(expected).entrySet()) {
            if (!field.getValue().equals(fields.getAttribute(field.getKey()))) return false;
        }
        Position point = ShareLabFields.envelopePosition(expected.position);
        Position actual = ShareLabFields.envelopePosition(new Position(
                event.getCotPoint().getLat(), event.getCotPoint().getLon()));
        return actual.latitude == point.latitude && actual.longitude == point.longitude;
    }
}
