package se.tmm.sensorplanner.plugin.publication;

import com.atakmap.coremap.cot.event.*;
import java.util.function.Consumer;
import se.tmm.sensorplanner.core.*;

/** Real SDK envelopes with a counted fake host boundary; no MapView/importer is started. */
public final class PublicationMapCopyCheck {
    public static void main(String[] args) throws Exception {
        for (DeviceType type : DeviceType.values()) {
            TmmDraft draft = PublicationEnvelopeFixtures.draft(type);
            PublicationPreparationReview.Prepared p = new PublicationPreparationReview(draft).prepare(draft);
            Fake host = new Fake(); PublicationMapCopy copy = new PublicationMapCopy(host);
            String result = copy.create(p, "Synthetic · r1");
            require(result.contains("Granskad kartkopia verifierad: Ja") && host.calls == 1);
            require(PublicationCot.matches(host.event, p));
            se.tmm.sensorplanner.tools.PublicationEnvelopeVerifier.verify(type.name().toLowerCase(java.util.Locale.ROOT),
                    host.event.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            result = copy.create(p, "Synthetic · r1");
            require(result.contains("Inget SDK-importanrop") && host.calls == 1);
            for (Consumer<Fake> mutate : java.util.Arrays.<Consumer<Fake>>asList(
                    h -> h.event.setUID("wrong"), h -> h.event.setType("b-m-p-w"),
                    h -> h.event.getDetail().getFirstChildByName(0, PublicationCot.TAG).setAttribute("status", "ACTIVE"),
                    h -> h.event.getDetail().getFirstChildByName(0, "contact").setAttribute("callsign", "Different label"),
                    h -> h.event.getDetail().getFirstChildByName(0, "contact").setAttribute("callsign", ""),
                    h -> h.count = 2, h -> h.count = 0, h -> h.event = null)) {
                host = new Fake(); copy = new PublicationMapCopy(host); copy.create(p, "Synthetic · r1");
                mutate.accept(host); result = copy.create(p, "Synthetic · r1");
                require(result.startsWith("Inte skapat") && host.calls == 1);
            }
            host = new Fake(); host.allowed = false;
            require(new PublicationMapCopy(host).create(p, "Synthetic · r1").startsWith("Inte skapat") && host.calls == 0);
            host = new Fake(); host.disconnectOnExport = true;
            require(new PublicationMapCopy(host).create(p, "Synthetic · r1").startsWith("Inte skapat") && host.calls == 0);
            host = new Fake();
            require(new PublicationMapCopy(host).create(p, "bad\nlabel").startsWith("Inte skapat") && host.calls == 0);
            host = new Fake(); host.throwAfterImport = true;
            result = new PublicationMapCopy(host).create(p, "Synthetic · r1");
            require(result.startsWith("SDK-import försökt") && !result.contains("PRIVATE-FAILURE") && host.calls == 1);
            host = new Fake(); host.drop = true;
            result = new PublicationMapCopy(host).create(p, "Synthetic · r1");
            require(result.contains("verifierad: Nej") && host.calls == 1);
        }
        System.out.println("Saved-draft map-copy SDK boundary passed all four types, exact reuse, conflicts, guards and ambiguous results. No actual host import or dispatch.");
    }
    private static final class Fake implements PublicationMapCopy.Port {
        CotEvent event;
        int count, calls;
        boolean allowed = true, disconnectOnExport, throwAfterImport, drop;
        @Override public boolean available() { return allowed; }
        @Override public int count(String uid) { return count; }
        @Override public CotEvent export(String uid) { if (disconnectOnExport) allowed = false; return event; }
        @Override public String importEvent(CotEvent value) {
            calls++;
            if (!drop) { event = value; count = 1; }
            if (throwAfterImport) throw new IllegalStateException("PRIVATE-FAILURE");
            return "SUCCESS";
        }
    }
    private static void require(boolean ok) { if (!ok) throw new AssertionError("Map-copy boundary failed"); }
}
