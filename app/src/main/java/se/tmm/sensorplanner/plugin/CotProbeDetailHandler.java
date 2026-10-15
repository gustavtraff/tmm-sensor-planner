package se.tmm.sensorplanner.plugin;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/** Retains only the validated synthetic detail on an already received host marker. */
final class CotProbeDetailHandler extends CotDetailHandler {
    private static final String KEY = "se.tmm.sensorplanner.issue5.fixture";

    CotProbeDetailHandler() {
        super(CotProbeFixture.TAG);
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event, CotDetail detail) {
        try {
            CotEvent clean = CotProbeFixture.forReturn(event);
            item.setMetaString(KEY, clean.toString());
            return ImportResult.SUCCESS;
        } catch (RuntimeException invalid) {
            return ImportResult.FAILURE;
        }
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail root) {
        String saved = item.getMetaString(KEY, null);
        if (saved == null) return false;
        try {
            CotEvent clean = CotProbeFixture.forReturn(CotEvent.parse(saved));
            if (!clean.getUID().equals(event.getUID())) return false;
            root.addChild(clean.getDetail().getFirstChildByName(0, CotProbeFixture.TAG));
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
