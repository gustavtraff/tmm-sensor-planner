package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import com.atakmap.coremap.cot.event.*;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.util.LinkedHashMap;
import java.util.Map;
import se.tmm.sensorplanner.core.*;

/** SDK envelope for the experimental, metadata-only review payload. */
@TargetApi(26)
final class ShareLabWire {
    private ShareLabWire() {}

    static CotEvent create(ShareReview.Metadata metadata) {
        CotEvent event = new CotEvent();
        event.setUID(metadata.uid.toString());
        event.setType("b-m-p-w");
        event.setHow("h-g-i-g-o");
        event.setPoint(new CotPoint(metadata.position.latitude, metadata.position.longitude,
                CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN));
        CoordinatedTime now = new CoordinatedTime();
        event.setTime(now); event.setStart(now); event.setStale(now.addHours(1));
        CotDetail detail = new CotDetail("detail");
        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign", "TMM-SHARE-LAB-" + metadata.uid.toString().substring(0, 8));
        detail.addChild(contact);
        CotDetail fields = new CotDetail(ShareLabFields.TAG);
        for (Map.Entry<String, String> field : ShareLabFields.encode(metadata).entrySet()) {
            fields.setAttribute(field.getKey(), field.getValue());
        }
        detail.addChild(fields); event.setDetail(detail);
        CotEvent encoded = CotEvent.parse(event.toString());
        ShareReview.Metadata roundTrip = read(encoded);
        if (!same(metadata, roundTrip)) throw new IllegalArgumentException("Lab encoding failed validation");
        return encoded;
    }

    static ShareReview.Metadata read(CotEvent event) {
        if (event == null || !event.isValid() || !"b-m-p-w".equals(event.getType()) || event.getDetail() == null
                || event.getDetail().getChildrenByName(ShareLabFields.TAG).size() != 1) throw invalid();
        CotDetail detail = event.getDetail().getFirstChildByName(0, ShareLabFields.TAG);
        if (detail.getAttributeCount() != 15 || detail.childCount() != 0
                || (detail.getInnerText() != null && !detail.getInnerText().isEmpty())) throw invalid();
        Map<String, String> fields = new LinkedHashMap<>();
        for (CotAttribute attribute : detail.getAttributes()) {
            if (attribute.getName().length() > 32 || attribute.getValue() == null
                    || attribute.getValue().length() > 87384) throw invalid();
            fields.put(attribute.getName(), attribute.getValue());
        }
        CotPoint point = event.getCotPoint();
        return ShareLabFields.decode(event.getUID(), new Position(point.getLat(), point.getLon()), fields);
    }

    static boolean same(ShareReview.Metadata first, ShareReview.Metadata second) {
        return first.uid.equals(second.uid) && first.position.latitude == second.position.latitude
                && first.position.longitude == second.position.longitude
                && ShareLabFields.encode(first).equals(ShareLabFields.encode(second));
    }

    static String summary(ShareReview.Metadata metadata) {
        return "UUID: " + metadata.uid + "\nID: " + metadata.displayId + "\nTyp: " + metadata.type.displayName
                + "\nStatus: " + metadata.status + "\nÄgare: " + metadata.owner
                + "\nLatitud: " + metadata.position.latitude + "\nLongitud: " + metadata.position.longitude
                + "\nSkapad UTC: " + metadata.createdAt + "\nÄndrad UTC: " + metadata.updatedAt
                + "\nUtplacerad UTC: " + (metadata.deployedAt == null ? "Ej angivet" : metadata.deployedAt)
                + "\nBäring från sann nord: " + (metadata.bearingDeg == null ? "Ej angivet" : metadata.bearingDeg + "°")
                + "\nKommentar: " + (metadata.comment.isEmpty() ? "Ingen" : metadata.comment)
                + "\nCoT-kartpunkt (sju decimaler): " + ShareLabFields.envelopePosition(metadata.position).latitude
                + ", " + ShareLabFields.envelopePosition(metadata.position).longitude
                + "\nExakta sparade koordinater ingår i metadata."
                + "\nBild delas inte. Lokal bildreferens och revisionsräknare ingår inte.";
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid lab event"); }
}
