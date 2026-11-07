package se.tmm.sensorplanner.plugin.publication;

import com.atakmap.coremap.cot.event.*;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.util.*;
import java.util.function.Consumer;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.plugin.local.SavedDraftCopyUi.PendingUpdate;

/** Actual SDK envelopes and counted fake importer; no MapView, network or file mutation. */
public final class PublicationHostUpdateCheck {
    public static void main(String[] args) {
        for (DeviceType type : DeviceType.values()) {
            TmmDraft original = PublicationEnvelopeFixtures.draft(type);
            TmmDraft edited = original.edit("SYNTHETIC ändring < & >", original.type, DeviceStatus.ACTIVE, "SYNTHETIC OWNER UPDATED",
                    original.updatedAt, "Synthetic edited metadata", original.position, original.bearingDeg,
                    original.imageRef, original.updatedAt.plusSeconds(1));
            PublicationPreparationReview.Prepared next = prepared(edited);
            Fake host = new Fake(original); CotEvent previousObject = host.event; String previousXml = previousObject.toString();
            PendingUpdate review = new PublicationMapCopy(host).inspectUpdate(next, "Synthetic · r2");
            require(host.imports == 0 && previousObject.toString().equals(previousXml));
            require(review.previous().status == original.status && review.previousLabel().equals("Synthetic · r1"));
            String result = review.commit();
            require(result.contains("Uppdaterad kartkopia verifierad: Ja") && host.imports == 1);
            require(PublicationCot.matches(host.event, next) && previousObject.toString().equals(previousXml));
            require(host.event.getDetail().getFirstChildByName(0,"foreign").getChild(0).getInnerText().equals("Synthetic preserved text"));
            require(host.event.getDetail().getFirstChildByName(0,"contact").getAttribute("extra").equals("untouched"));
            require("Synthetic access".equals(host.event.getAccess()) && "Synthetic caveat".equals(host.event.getCaveat())
                    && "Synthetic release".equals(host.event.getReleasableTo()) && "Synthetic qos".equals(host.event.getQos())
                    && "Synthetic opex".equals(host.event.getOpex()));
            require(review.commit().startsWith("Inte uppdaterat") && host.imports == 1);

            host = new Fake(original); review = new PublicationMapCopy(host).inspectUpdate(prepared(original),"Synthetic · r1");
            require(review.commit().contains("Ingen SDK-uppdatering") && host.imports == 0);
            host = new Fake(original); review = new PublicationMapCopy(host).inspectUpdate(next,"Synthetic · r2");review.close();
            require(review.commit().startsWith("Inte uppdaterat") && host.imports == 0);

            for (Consumer<Fake> change : Arrays.<Consumer<Fake>>asList(
                    h->h.allowed=false, h->h.count=0, h->h.count=2, h->h.attachments=true, h->h.identity=new Object(),
                    h->h.event.setUID("changed"), h->h.event.setAccess("changed"),
                    h->h.event.getDetail().getFirstChildByName(0,"contact").setAttribute("callsign","Changed"),
                    h->h.event.getDetail().getFirstChildByName(0,"foreign").getChild(0).setInnerText("Changed"),
                    h->h.event.getDetail().getFirstChildByName(0,PublicationCot.TAG).setAttribute("status","RETRIEVED"),
                    h->h.disconnectOnExport=true)) {
                host=new Fake(original);review=new PublicationMapCopy(host).inspectUpdate(next,"Synthetic · r2");change.accept(host);
                require(review.commit().startsWith("Inte uppdaterat") && host.imports==0);
            }
            host=new Fake(original);review=new PublicationMapCopy(host).inspectUpdate(next,"Synthetic · r2");
            host.event.setTime(new CoordinatedTime(1));host.event.setStart(new CoordinatedTime(1));host.event.setStale(new CoordinatedTime(2));
            require(review.commit().contains("verifierad: Ja") && host.imports==1);

            for (int failure=0;failure<4;failure++) {
                host=new Fake(original);review=new PublicationMapCopy(host).inspectUpdate(next,"Synthetic · r2");
                host.failure=failure;result=review.commit();require(host.imports==1);
                require(failure==0 ? result.contains("resultat oklart") : result.contains("verifierad: Nej"));
                require(!result.contains("PRIVATE-ERROR"));review.commit();require(host.imports==1);
            }
            for (Consumer<Fake> invalid : Arrays.<Consumer<Fake>>asList(
                    h->h.count=0,h->h.count=2,h->h.allowed=false,h->h.attachments=true,h->h.identity=null,
                    h->h.event.getDetail().addChild(new CotDetail("contact")),
                    h->h.event.getDetail().addChild(h.event.getDetail()),
                    h->h.event.getDetail().getFirstChildByName(0,"foreign").setAttribute("oversized",new String(new char[70000]).replace('\0','x')),
                    h->h.event.getDetail().getFirstChildByName(0,PublicationCot.TAG).setAttribute("schemaVersion","999"))) {
                host=new Fake(original);invalid.accept(host);Fake target=host;
                rejected(()->new PublicationMapCopy(target).inspectUpdate(next,"Synthetic · r2"));require(host.imports==0);
            }
            TmmDraft moved=edited.edit(edited.displayId,edited.type,edited.status,edited.owner,edited.deployedAt,edited.comment,
                    new Position(edited.position.latitude+0.01,edited.position.longitude),edited.bearingDeg,edited.imageRef,edited.updatedAt);
            host=new Fake(original);Fake target=host;
            rejected(()->new PublicationMapCopy(target).inspectUpdate(prepared(moved),"Synthetic · r2"));
            rejected(()->new PublicationMapCopy(target).inspectUpdate(next,"bad\nlabel"));require(host.imports==0);
        }
        System.out.println("Metadata-only host update SDK checks passed: all types, deep ownership, retained details, freshness, one-use/cancel, refusal and uncertain outcomes. No native import or delivery implied.");
    }
    private static PublicationPreparationReview.Prepared prepared(TmmDraft draft) { return new PublicationPreparationReview(draft).prepare(draft); }
    private static void rejected(Runnable action) { try {action.run();throw new AssertionError("Unsupported host update accepted");}catch(IllegalArgumentException expected){} }
    private static void require(boolean value) { if(!value)throw new AssertionError("Host update SDK check failed"); }
    private static final class Fake implements PublicationMapCopy.Port {
        CotEvent event;Object identity=new Object();int count=1,imports,failure=-1;boolean allowed=true,disconnectOnExport,attachments;
        Fake(TmmDraft draft) {
            event=PublicationCot.create(prepared(draft),"Synthetic · r1",new CoordinatedTime());
            event.setAccess("Synthetic access");event.setCaveat("Synthetic caveat");event.setReleasableTo("Synthetic release");event.setQos("Synthetic qos");event.setOpex("Synthetic opex");
            event.getDetail().getFirstChildByName(0,"contact").setAttribute("extra","untouched");
            CotDetail foreign=new CotDetail("foreign"),leaf=new CotDetail("leaf");leaf.setInnerText("Synthetic preserved text");foreign.addChild(leaf);event.getDetail().addChild(foreign);
        }
        public boolean available(){return allowed;}
        public int count(String uid){return count;}
        public boolean hasAttachments(String uid){return attachments;}
        public Object identity(String uid){return identity;}
        public CotEvent export(String uid){if(disconnectOnExport)allowed=false;return event;}
        public String importEvent(CotEvent value){throw new AssertionError("Update must not reimport a MapItem");}
        public String updateMetadata(String uid, Object expected, CotEvent value){
            require(identity==expected);
            imports++;
            if(failure!=1)event=value;
            if(failure==0)throw new IllegalStateException("PRIVATE-ERROR");
            if(failure==2)event.getDetail().getFirstChildByName(0,"foreign").getChild(0).setInnerText("Changed by importer");
            if(failure==3)count=2;
            return "SUCCESS";
        }
    }
}
