package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.core.*;

/** Explicit trusted-feed intake on the owning worker. No initialization, private draft/image writes or feedback. */
@TargetApi(26)
public final class ReceivedImageIntake {
    private final InboxStore inbox;private final ImageAssociationRegistry registry;
    private final ImageStageStore stage;private final LocalV2Store local;
    public ReceivedImageIntake(InboxStore inbox,ImageAssociationRegistry registry,ImageStageStore stage,LocalV2Store local) {
        this.inbox=inbox;this.registry=registry;this.stage=stage;this.local=local;
    }
    /** Caller must obtain context and the complete payload from the approved fresh selected-feed adapter. */
    public ImageAssociationRegistry.Record receive(String context,ReceivedImagePayload payload,Instant now,
                                                   BooleanSupplier active)throws IOException {
        try { return apply(context,payload,now,active); }
        catch(se.tmm.sensorplanner.images.ImagePreparationException safe){throw safe;}
        catch(Exception unavailable){throw new IOException("Image intake outcome is unconfirmed; reload retained history before retrying");}
        catch(OutOfMemoryError exhausted){throw new se.tmm.sensorplanner.images.ImagePreparationException(se.tmm.sensorplanner.images.ImagePreparationException.Reason.LOW_MEMORY);}
    }
    private ImageAssociationRegistry.Record apply(String context,ReceivedImagePayload payload,Instant now,
                                                   BooleanSupplier active)throws IOException {
        require(active);LocalV2.Snapshot current=local.load();InboxStore.Snapshot received=inbox.load();
        ImageAssociationRegistry.Snapshot saved=registry.load();
        if(!saved.inboxId.equals(received.inboxId))throw new IOException("Image history belongs to another inbox");
        ReceivedImageReview review=ReceivedImageReview.assess(saved,received,current,payload.offer.metadata.uid);
        for(ReceivedImageReview.Item item:review.images) {
            ImageAssociationRegistry.Record r=item.record;
            if(r.contextId.equals(context)&&r.associationId.equals(payload.offer.associationId)&&r.deliveryId.equals(payload.offer.deliveryId)) {
                if(!item.matchesOffer(payload.offer))throw new IOException("Image delivery conflicts with retained history");
                ReceivedImageContent.open(review,item,registry,inbox,stage);require(active);return r;
            }
        }
        ImageStageStore.Inspection state=stage.inspect();
        if(state.recoveryRequired())throw new IOException("Image stage requires explicit recovery");
        Set<UUID> referenced=new HashSet<>();for(ImageAssociationRegistry.Record r:saved.records)referenced.add(r.stageLocalId);
        UUID ready=null;
        // A prior uncertain association may have left a verified unreferenced pair; never recreate a missing historical link.
        for(UUID id:state.ready)if(!referenced.contains(id)) {
            require(active);ReceivedImagePayload candidate=stage.read(id);
            if(Arrays.equals(ImageOfferCodec.encode(candidate.offer),ImageOfferCodec.encode(payload.offer))
                    &&Arrays.equals(candidate.image.bytes(),payload.image.bytes())){ready=id;break;}
        }
        require(active);
        ShareReview.Metadata m=payload.offer.metadata;
        TmmDraft incoming=new TmmDraft(m.uid,m.displayId,m.type,m.status,m.owner,m.createdAt,m.updatedAt,
                m.deployedAt,m.comment,m.position,m.bearingDeg,null,1);
        inbox.receive(context,incoming,current.state(m.uid).draft,now);
        require(active);
        if(ready==null)ready=stage.stage(ImageStageStore.prepare(payload));
        require(active);
        ImageAssociationRegistry.Record result=registry.associate(inbox,context,payload.offer,ready,now,stage::requireReady);
        require(active);return result;
    }
    private static void require(BooleanSupplier active)throws IOException {
        if(active==null||Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw new IOException("Image intake canceled; reload actual history");
    }
}
