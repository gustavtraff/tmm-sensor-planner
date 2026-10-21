package se.tmm.sensorplanner.plugin;

import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.MutableMGRSPoint;
import se.tmm.sensorplanner.core.Position;
import java.util.Locale;

/** UTM-based WGS84 MGRS at one-meter precision; numeric storage stays unchanged. */
final class MgrsCoordinates {
    private MgrsCoordinates() { }

    static String compact(String text) {
        return text == null ? "" : text.replaceAll("[ \\t\\r\\n]", "").toUpperCase(Locale.ROOT);
    }

    static String format(Position position) {
        if (position.latitude < -80 || position.latitude > 84) throw new IllegalArgumentException("UTM range");
        MGRSPoint point = MGRSPoint.fromLatLng(Ellipsoid.WGS_84, position.latitude, position.longitude == 180 ? -180 : position.longitude, null);
        return point.getZoneDescriptor() + " " + point.getGridDescriptor() + " "
                + point.getEastingDescriptor() + " " + point.getNorthingDescriptor();
    }

    static Position parse(String text) {
        String value = compact(text);
        if (!value.matches("(?:0?[1-9]|[1-5][0-9]|60)[C-HJ-NP-X][A-HJ-NP-Z][A-HJ-NP-V][0-9]{10}")) {
            throw new IllegalArgumentException("MGRS requires a zone, square and five digits per axis");
        }
        int zoneLength = Character.isDigit(value.charAt(1)) ? 2 : 1;
        int zone = Integer.parseInt(value.substring(0, zoneLength));
        String columns = new String[]{"ABCDEFGH", "JKLMNPQR", "STUVWXYZ"}[(zone - 1) % 3];
        if (columns.indexOf(value.charAt(zoneLength + 1)) < 0) throw new IllegalArgumentException("Invalid grid column");
        MGRSPoint point = MGRSPoint.decodeString(value, Ellipsoid.WGS_84, null);
        String padded = zoneLength == 1 ? "0" + value : value;
        // A one-meter cell can straddle a zone/band boundary; prefer its center, then an interior corner.
        for (double east : new double[]{0.5, 0.01, 0.98}) {
            for (double north : new double[]{0.5, 0.01, 0.98}) {
                MutableMGRSPoint cell = new MutableMGRSPoint(point);
                cell.offset(east, north);
                double[] latLon = cell.toLatLng(null);
                Position position = new Position(latLon[0], latLon[1]);
                if (position.latitude >= -80 && position.latitude <= 84 && compact(format(position)).equals(padded)) return position;
            }
        }
        throw new IllegalArgumentException("MGRS grid does not round trip");
    }
}
