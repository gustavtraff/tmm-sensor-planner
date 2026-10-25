package se.tmm.sensorplanner.tools;

import android.os.Bundle;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import se.tmm.sensorplanner.core.ShareLabFields;
import se.tmm.sensorplanner.core.ShareReview;

/** Offline SDK construction/serialization only; never imports or dispatches an event. */
public final class PublicationSdkProbe {
    public static void main(String[] args) {
        try {
            if (args.length != 0) throw new IllegalArgumentException();
            for (String base : new String[]{"mini", "scout", "pir", "gateway", "pir-update", "pir-conflict"}) {
                String name = "sensor-" + base;
                ShareReview.Metadata metadata = DataSyncMetadataProbe.metadata(name);
                CotEvent event = create(metadata);
                if (!event.isValid()
                        || event.getDetail().getChildrenByName("archive").size() != 1
                        || event.getDetail().getChildrenByName(ShareLabFields.TAG).size() != 1) {
                    throw new IllegalArgumentException();
                }
                DataSyncMetadataProbe.verify(name, event.toString().getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("SDK construction/serialization passed for six synthetic sensor cases. Nothing imported or sent.");
        } catch (Exception rejected) {
            System.err.println("SDK publication probe failed; no import or delivery result is claimed.");
            System.exit(1);
        }
    }

    private static CotEvent create(ShareReview.Metadata metadata) {
        CotEvent event = new CotEvent();
        event.setUID(metadata.uid.toString());
        event.setType("b-m-p-s-p-loc");
        event.setHow("h-g-i-g-o");
        event.setPoint(new CotPoint(metadata.position.latitude, metadata.position.longitude,
                CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN));
        CoordinatedTime time = new CoordinatedTime(1767225600000L);
        event.setTime(time);
        event.setStart(time);
        event.setStale(time.addHours(1));
        CotDetail detail = new CotDetail("detail");
        detail.addChild(new CotDetail("archive"));
        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign", "TMM-PUBLICATION-SDK-PROBE");
        detail.addChild(contact);
        CotDetail fields = new CotDetail(ShareLabFields.TAG);
        for (Map.Entry<String, String> field : ShareLabFields.encode(metadata).entrySet()) {
            fields.setAttribute(field.getKey(), field.getValue());
        }
        detail.addChild(fields);
        event.setDetail(detail);
        return event;
    }

    // Compile-only candidate signature. This path is not equivalent to the tested Local SD import.
    private static ImportResult candidateImport(CotImporterManager manager, CotEvent reviewedCopy, Bundle options) {
        return manager.importData(reviewedCopy, options);
    }
}
