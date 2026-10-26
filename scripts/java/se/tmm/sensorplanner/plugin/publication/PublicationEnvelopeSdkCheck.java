package se.tmm.sensorplanner.plugin.publication;

import com.atakmap.coremap.cot.event.*;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Consumer;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.tools.PublicationEnvelopeVerifier;

/** Actual SDK objects only; no Android parser stub, host import, store or dispatch. */
public final class PublicationEnvelopeSdkCheck {
    public static void main(String[] args) throws Exception {
        for (DeviceType type : DeviceType.values()) {
            TmmDraft draft = PublicationEnvelopeFixtures.draft(type);
            PublicationPreparationReview.Prepared prepared = new PublicationPreparationReview(draft).prepare(draft);
            CotEvent event = create(prepared);
            require(PublicationCot.matches(event, prepared));
            require(PublicationCot.read(event).position.latitude == draft.position.latitude);
            require(PublicationCot.read(event).updatedAt.equals(draft.updatedAt));
            PublicationEnvelopeVerifier.verify(type.name().toLowerCase(Locale.ROOT), event.toString().getBytes(StandardCharsets.UTF_8));
            reject(prepared, e -> e.setUID("00000000-0000-4000-8018-999999999999"));
            reject(prepared, e -> e.setType("b-m-p-w"));
            reject(prepared, e -> e.setPoint(new CotPoint(e.getCotPoint().getLat() + 1e-12, e.getCotPoint().getLon(),
                    CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN)));
            reject(prepared, e -> e.getDetail().addChild(new CotDetail(payload(e))));
            reject(prepared, e -> e.getDetail().addChild(new CotDetail("tmmShareLab")));
            reject(prepared, e -> payload(e).addChild(new CotDetail("extra")));
            reject(prepared, e -> payload(e).setAttribute("imageRef", "forbidden"));
            reject(prepared, e -> payload(e).setAttribute("schemaVersion", "2"));
            reject(prepared, e -> payload(e).setAttribute("bearingReference", "magnetic"));
            reject(prepared, e -> payload(e).setAttribute("ownerUtf8", "/w=="));
            reject(prepared, e -> payload(e).setAttribute("ownerUtf8", repeat('a', 87385)));
            reject(prepared, e -> { for (int i = 0; i < 65; i++) e.getDetail().addChild(new CotDetail("extra")); });
            event = create(prepared);
            payload(event).setAttribute("ownerUtf8", Base64.getEncoder().encodeToString("Different synthetic role".getBytes(StandardCharsets.UTF_8)));
            require(PublicationCot.read(event).owner.equals("Different synthetic role"));
            require(!PublicationCot.matches(event, prepared));
            event = create(prepared);
            event.getDetail().getFirstChildByName(0, "contact").setAttribute("callsign", "Different host label");
            event.setTime(new CoordinatedTime(1800000000000L));
            require(PublicationCot.matches(event, prepared));
            for (String label : new String[]{null, "", " leading", "trailing ", "line\nbreak", "format\u200b", "bad\ud800", repeat('x', 129)}) {
                try { PublicationCot.create(prepared, label, new CoordinatedTime(1767225600000L));
                    throw new AssertionError("Invalid host label accepted"); }
                catch (IllegalArgumentException expected) { sanitized(expected); }
            }
        }
        System.out.println("Production envelope SDK construction, independent four-type wire checks and rejection controls passed. Nothing imported or sent.");
    }
    private static CotEvent create(PublicationPreparationReview.Prepared p) {
        return PublicationCot.create(p, "Synthetic reviewed copy", new CoordinatedTime(1767225600000L));
    }
    private static CotDetail payload(CotEvent e) { return e.getDetail().getFirstChildByName(0, PublicationCot.TAG); }
    private static void reject(PublicationPreparationReview.Prepared p, Consumer<CotEvent> mutate) {
        CotEvent e = create(p); mutate.accept(e);
        require(!PublicationCot.matches(e, p));
        try { PublicationCot.read(e); throw new AssertionError("Invalid SDK envelope accepted"); }
        catch (IllegalArgumentException expected) { sanitized(expected); }
    }
    private static void sanitized(IllegalArgumentException e) {
        require("Invalid publication envelope".equals(e.getMessage()) && e.getCause() == null);
    }
    private static String repeat(char value, int count) {
        char[] text = new char[count]; java.util.Arrays.fill(text, value); return new String(text);
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Publication SDK check failed"); }
}
