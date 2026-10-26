package se.tmm.sensorplanner.plugin.publication;

import android.annotation.TargetApi;
import com.atakmap.coremap.cot.event.*;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import se.tmm.sensorplanner.core.*;

/** SDK envelope adapter only; creates no MapItem, store, destination or dispatch operation. */
@TargetApi(26)
public final class PublicationCot {
    public static final String TAG = "tmmPublication";
    public static final String TYPE = "b-m-p-s-p-loc";
    private PublicationCot() { }

    public static CotEvent create(PublicationPreparationReview.Prepared prepared, String label, CoordinatedTime now) {
        try {
            if (label == null || label.isEmpty() || label.length() > 128 || !label.equals(label.trim())) throw invalid();
            for (int i = 0; i < label.length();) {
                int p = label.codePointAt(i), category = Character.getType(p);
                if (Character.isISOControl(p) || category == Character.FORMAT || category == Character.SURROGATE
                        || category == Character.LINE_SEPARATOR || category == Character.PARAGRAPH_SEPARATOR
                        || p == 0xfffe || p == 0xffff) throw invalid();
                i += Character.charCount(p);
            }
            Map<String, String> fields = PublicationCodec.fields(prepared);
            ShareReview.Metadata m = prepared.metadata;
            CotEvent event = new CotEvent();
            event.setUID(m.uid.toString()); event.setType(TYPE); event.setHow("h-g-i-g-o");
            event.setPoint(new CotPoint(rounded(m.position.latitude), rounded(m.position.longitude),
                    CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN));
            event.setTime(now); event.setStart(now); event.setStale(now.addHours(1));
            CotDetail detail = new CotDetail("detail"); detail.addChild(new CotDetail("archive"));
            CotDetail contact = new CotDetail("contact"); contact.setAttribute("callsign", label); detail.addChild(contact);
            CotDetail payload = new CotDetail(TAG);
            for (Map.Entry<String, String> field : fields.entrySet()) payload.setAttribute(field.getKey(), field.getValue());
            detail.addChild(payload); event.setDetail(detail);
            read(event);
            return event;
        } catch (Exception rejected) { throw invalid(); }
    }

    /** Validates metadata, not source trust, freshness, feed membership or application permission. */
    public static ShareReview.Metadata read(CotEvent event) {
        try {
            if (event == null || !event.isValid() || !TYPE.equals(event.getType()) || event.getCotPoint() == null) throw invalid();
            CotDetail payload = payload(event);
            if (payload.getAttributeCount() != 15 || payload.childCount() != 0
                    || (payload.getInnerText() != null && !payload.getInnerText().trim().isEmpty())) throw invalid();
            for (CotAttribute a : payload.getAttributes()) {
                if (a.getName() == null || a.getName().length() > 32 || a.getValue() == null
                        || a.getValue().length() > (a.getName().endsWith("Utf8") ? 87384 : 128)) throw invalid();
            }
            LimitedXml xml = new LimitedXml(); payload.buildXml(xml);
            ShareReview.Metadata m = PublicationCodec.decode(new ByteArrayInputStream(
                    xml.text.toString().getBytes(StandardCharsets.UTF_8)));
            if (!m.uid.toString().equals(event.getUID()) || event.getCotPoint().getLat() != rounded(m.position.latitude)
                    || event.getCotPoint().getLon() != rounded(m.position.longitude)) throw invalid();
            return m;
        } catch (Exception rejected) { throw invalid(); }
    }

    /** Exact reviewed wire values; not an inbox duplicate/equality policy. */
    public static boolean matches(CotEvent event, PublicationPreparationReview.Prepared expected) {
        try {
            read(event);
            CotDetail payload = payload(event);
            for (Map.Entry<String, String> field : PublicationCodec.fields(expected).entrySet()) {
                if (!field.getValue().equals(payload.getAttribute(field.getKey()))) return false;
            }
            return true;
        } catch (RuntimeException rejected) { return false; }
    }

    private static CotDetail payload(CotEvent event) {
        CotDetail root = event.getDetail(), found = null;
        if (root == null || !"detail".equals(root.getElementName()) || root.childCount() > 64) throw invalid();
        for (int i = 0; i < root.childCount(); i++) {
            CotDetail child = root.getChild(i);
            if ("tmmShareLab".equals(child.getElementName())) throw invalid();
            if (TAG.equals(child.getElementName())) {
                if (found != null) throw invalid();
                found = child;
            }
        }
        if (found == null) throw invalid();
        return found;
    }

    private static double rounded(double value) { return Math.round(value * 1e7) / 1e7; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid publication envelope"); }

    private static final class LimitedXml implements Appendable {
        final StringBuilder text = new StringBuilder();
        @Override public Appendable append(CharSequence value) throws IOException { return append(value, 0, value.length()); }
        @Override public Appendable append(CharSequence value, int start, int end) throws IOException {
            if (end - start > PublicationCodec.MAX_BYTES - text.length()) throw new IOException("Envelope metadata too large");
            text.append(value, start, end); return this;
        }
        @Override public Appendable append(char value) throws IOException {
            if (text.length() == PublicationCodec.MAX_BYTES) throw new IOException("Envelope metadata too large");
            text.append(value); return this;
        }
    }
}
