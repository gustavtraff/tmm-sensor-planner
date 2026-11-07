package se.tmm.sensorplanner.plugin.publication;

import android.annotation.TargetApi;
import com.atakmap.coremap.cot.event.*;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.plugin.local.SavedDraftCopyUi;

/** One-use metadata-only host update. It neither selects nor confirms a Data Sync destination. */
@TargetApi(26)
final class PublicationHostUpdate implements SavedDraftCopyUi.PendingUpdate {
    private final PublicationMapCopy.Port port;
    private final PublicationPreparationReview.Prepared prepared;
    private final String label;
    private final CotEvent before;
    private final Object hostIdentity;
    private final ShareReview.Metadata previous;
    private final AtomicBoolean consumed = new AtomicBoolean();

    PublicationHostUpdate(PublicationMapCopy.Port port, PublicationPreparationReview.Prepared prepared, String label) {
        this.port = port; this.prepared = prepared; this.label = label;
        String uid = prepared.metadata.uid.toString();
        if (!port.available() || port.count(uid) != 1 || port.hasAttachments(uid)) throw invalid();
        hostIdentity = port.identity(uid); if (hostIdentity == null) throw invalid();
        before = copy(port.export(uid)); previous = PublicationCot.read(before);
        ShareReview.Metadata next = prepared.metadata;
        if (!previous.uid.equals(next.uid) || !previous.createdAt.equals(next.createdAt)
                || previous.type != next.type || !same(previous.bearingDeg, next.bearingDeg)
                || previous.position.latitude != next.position.latitude || previous.position.longitude != next.position.longitude) throw invalid();
        contact(before.getDetail());
        PublicationCot.create(prepared, label, new CoordinatedTime());
        if (!port.available() || port.identity(uid) != hostIdentity) throw invalid();
    }

    @Override public ShareReview.Metadata previous() { return previous; }
    @Override public String previousLabel() { return contact(before.getDetail()).getAttribute("callsign"); }
    @Override public void close() { consumed.set(true); }

    @Override public String commit() {
        if (consumed.getAndSet(true)) return "Inte uppdaterat: granskningen är redan använd eller avslutad.";
        boolean attempted = false;
        try {
            String uid = prepared.metadata.uid.toString();
            if (!port.available() || port.count(uid) != 1 || port.hasAttachments(uid) || port.identity(uid) != hostIdentity) return refused();
            CotEvent current = copy(port.export(uid));
            if (!stable(before, current)) return refused();
            CotEvent candidate = patch(current);
            if (!port.available() || port.count(uid) != 1 || port.hasAttachments(uid) || port.identity(uid) != hostIdentity) return refused();
            if (stable(current, candidate)) return "Oförändrad kartkopia verifierad. Ingen SDK-uppdatering har begärts.";
            CotEvent expected = copy(candidate);
            attempted = true;
            String result = port.updateMetadata(uid, hostIdentity, candidate);
            CotEvent after = port.export(uid);
            boolean verified = port.count(uid) == 1 && port.identity(uid) == hostIdentity && !port.hasAttachments(uid) && PublicationCot.matches(after, prepared)
                    && label.equals(contact(after.getDetail()).getAttribute("callsign")) && stable(expected, copy(after));
            return "SDK-resultat: " + result + "\nUppdaterad kartkopia verifierad: " + (verified ? "Ja" : "Nej")
                    + "\nFeedpublicering och leverans är obekräftade. Kontrollera Data Sync. Ingen automatisk återställning eller omsändning görs.";
        } catch (RuntimeException unavailable) {
            return attempted ? "SDK-uppdatering försökt – resultat oklart. Kontrollera kartan och feeds. Ingen automatisk återställning eller omsändning görs."
                    : refused();
        }
    }

    private CotEvent patch(CotEvent current) {
        CotEvent candidate = copy(current);
        CotDetail root = candidate.getDetail();
        CotDetail payload = new CotDetail(PublicationCot.TAG);
        for (Map.Entry<String, String> field : PublicationCodec.fields(prepared).entrySet()) payload.setAttribute(field.getKey(), field.getValue());
        for (int i = 0; i < root.childCount(); i++) if (PublicationCot.TAG.equals(root.getChild(i).getElementName())) root.setChild(i, payload);
        contact(root).setAttribute("callsign", label);
        CoordinatedTime now = new CoordinatedTime(); candidate.setTime(now); candidate.setStart(now); candidate.setStale(now.addHours(1));
        if (!PublicationCot.matches(candidate, prepared)) throw invalid();
        return candidate;
    }

    private static CotDetail contact(CotDetail root) {
        CotDetail found = null;
        for (int i = 0; i < root.childCount(); i++) if ("contact".equals(root.getChild(i).getElementName())) {
            if (found != null) throw invalid(); found = root.getChild(i);
        }
        if (found == null || found.getAttribute("callsign") == null || found.getAttribute("callsign").isEmpty()) throw invalid();
        return found;
    }

    private static boolean same(Double a, Double b) { return a == null ? b == null : b != null && a.doubleValue() == b.doubleValue(); }
    private static String refused() { return "Inte uppdaterat: kartkopian eller labbåtkomsten har ändrats eller kunde inte verifieras. Granska på nytt."; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Host update requires one valid matching metadata-only copy"); }

    static CotEvent copy(CotEvent event) {
        if (event == null || !event.isValid()) throw invalid();
        CotEvent result = new CotEvent(event);
        // The SDK's CotDetail copy constructor retains child references; rebuild the whole bounded tree.
        result.setDetail(detail(event.getDetail(), 0, new int[]{0, 0}));
        if (!equal(event.getDetail(), result.getDetail())) throw invalid();
        return result;
    }
    private static CotDetail detail(CotDetail value, int depth, int[] budget) {
        if (value == null || depth > 16 || ++budget[0] > 1024 || value.childCount() > 64 || value.getAttributeCount() > 64) throw invalid();
        CotDetail result = new CotDetail(text(value.getElementName(), budget));
        for (CotAttribute attribute : value.getAttributes()) result.setAttribute(text(attribute.getName(), budget), text(attribute.getValue(), budget));
        if (value.getInnerText() != null) result.setInnerText(text(value.getInnerText(), budget));
        for (int i = 0; i < value.childCount(); i++) result.addChild(detail(value.getChild(i), depth + 1, budget));
        return result;
    }
    private static String text(String value, int[] budget) {
        if (value == null || value.length() > 65536 - budget[1]) throw invalid(); budget[1] += value.length(); return value;
    }
    private static boolean stable(CotEvent a, CotEvent b) {
        // Export timestamps are delivery fields, not host-edit versions. All other exported content stays bound.
        return Objects.equals(a.getUID(), b.getUID()) && Objects.equals(a.getType(), b.getType())
                && Objects.equals(a.getVersion(), b.getVersion()) && Objects.equals(a.getHow(), b.getHow())
                && Objects.equals(a.getAccess(), b.getAccess()) && Objects.equals(a.getOpex(), b.getOpex())
                && Objects.equals(a.getQos(), b.getQos()) && Objects.equals(a.getCaveat(), b.getCaveat())
                && Objects.equals(a.getReleasableTo(), b.getReleasableTo())
                && a.getCotPoint().getLat() == b.getCotPoint().getLat() && a.getCotPoint().getLon() == b.getCotPoint().getLon()
                && a.getCotPoint().getHae() == b.getCotPoint().getHae() && a.getCotPoint().getCe() == b.getCotPoint().getCe()
                && a.getCotPoint().getLe() == b.getCotPoint().getLe() && equal(a.getDetail(), b.getDetail());
    }
    private static boolean equal(CotDetail a, CotDetail b) {
        if (!Objects.equals(a.getElementName(), b.getElementName()) || !Objects.equals(a.getInnerText(), b.getInnerText())
                || a.childCount() != b.childCount() || !attributes(a).equals(attributes(b))) return false;
        for (int i = 0; i < a.childCount(); i++) if (!equal(a.getChild(i), b.getChild(i))) return false;
        return true;
    }
    private static Map<String, String> attributes(CotDetail node) {
        Map<String, String> values = new HashMap<>();
        for (CotAttribute attribute : node.getAttributes()) values.put(attribute.getName(), attribute.getValue());
        return values;
    }
}
