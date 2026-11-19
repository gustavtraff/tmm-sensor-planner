package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.*;
import se.tmm.sensorplanner.plugin.ingress.*;

/**
 * #243 W1–W4: uploads one device's current owned photo as container v1 to the followed feed (U2) and later
 * removes the photo it replaced. No metadata, queue, retry or background write: every call is one attempt
 * started by an explicit user action, and fetching (U8) stays GET only. Nothing new is persisted; the frozen
 * bytes live in private no-backup storage only for the attempt, and W4 state lives in memory for the session.
 * Call off the UI thread; store reads match what the planner's worker already does.
 */
@TargetApi(26)
public final class FeedPhotoPublication implements AutoCloseable {
    public interface Transport {
        /** {@code POST /Marti/sync/upload?name=&creatorUid=} with exactly {@code container}; returns the reply's {@code Hash}. */
        String upload(String name,String creatorUid,byte[] container,MissionReadAttempt attempt)throws WriteFailure;
        /** {@code PUT /Marti/api/missions/guid/{guid}/contents?creatorUid=} with {@code {"hashes":[hash]}}. */
        void associate(String guid,String hash,String creatorUid,MissionReadAttempt attempt)throws WriteFailure;
        /** {@code DELETE /Marti/api/missions/guid/{guid}/contents?hash=&creatorUid=}: the feed only, not the content store. */
        void remove(String guid,String hash,String creatorUid,MissionReadAttempt attempt)throws WriteFailure;
    }
    public interface Host {
        boolean available();boolean defaultProvider();
        /** Enabled, connected, eligible stream connect strings. */
        List<String> profiles();
        Transport connect(MissionEndpoint endpoint);
        String creatorUid();
    }
    /** How far a write got. REJECTED is a definite non-403 4xx answer; UNKNOWN means bytes may have arrived. */
    public static final class WriteFailure extends IOException {
        public enum Kind { NOT_SENT, REFUSED, REJECTED, UNKNOWN }
        public final Kind kind;
        public WriteFailure(Kind kind){super("Feed write: "+kind);this.kind=kind;}
    }
    public enum Outcome { SUCCEEDED, REFUSED, FAILED, UNKNOWN }
    public static final class Result {
        public final UUID deviceUid;public final Outcome outcome;public final String message;
        Result(UUID deviceUid,Outcome outcome,String message){this.deviceUid=deviceUid;this.outcome=outcome;this.message=message;}
    }
    /** One frozen preview for one followed feed; consumed by the first {@link #publish}. */
    public static final class Request implements AutoCloseable {
        public final UUID deviceUid;public final ImagePublicationReview review;final FeedSyncState.Snapshot destination;final LocalV2.Selection selected;
        private boolean used;
        private Request(UUID deviceUid,ImagePublicationReview review,FeedSyncState.Snapshot destination,LocalV2.Selection selected){
            this.deviceUid=deviceUid;this.review=review;this.destination=destination;this.selected=selected;
        }
        public String feedName(){return destination.feedName;}
        @Override public void close(){review.close();}
    }
    private static final class Replacement {
        final FeedSyncState.Snapshot destination;final String newHash,oldHash;
        Replacement(FeedSyncState.Snapshot destination,String newHash,String oldHash){this.destination=destination;this.newHash=newHash;this.oldHash=oldHash;}
    }
    static final int UPLOAD_SECONDS=60,WRITE_SECONDS=12;
    static final String REFUSED_TEXT="Servern nekade skrivning till feeden (403). TMM:s anslutning har inte skrivbehörighet: feedens standardroll kan vara skrivskyddad, eller så är feeden lösenordsskyddad eller kräver inbjudan. Be feedens ägare om behörighet.";
    static final String UNKNOWN_TEXT="Okänt om bilden kom fram. Nästa hämtning visar om den finns i feeden. TMM skickar inte om automatiskt.";

    private final LocalV2Store local;private final LocalImages images;private final Host host;
    private final FeedSyncState sync;private final ImageStageStore stage;private final Path outbox;private final ImageSnapshotExports frozen;
    private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"TMM-photo-deadline");t.setDaemon(true);return t;});
    // Ready stage entries are immutable, so a computed container hash stays valid for the process.
    private final Map<UUID,String> containerHashes=new HashMap<>();
    private final Map<UUID,Replacement> replacements=new HashMap<>();
    private Map<String,List<ImageAssociationRegistry.Record>> seen=Collections.emptyMap();
    private volatile MissionReadAttempt current;private volatile boolean closed;

    public FeedPhotoPublication(LocalV2Store local,LocalImages images,Path privateRoot,Host host){
        this.local=local;this.images=images;this.host=host;
        sync=new FeedSyncState(privateRoot.resolve("feed-sync-v1"));stage=new ImageStageStore(privateRoot.resolve("received-image-stage-v1"));
        outbox=privateRoot.resolve("feed-photo-outbox");frozen=new ImageSnapshotExports(outbox);
        timer.setRemoveOnCancelPolicy(true);
    }

    /** Fresh preview of the device's saved state and owned photo for the followed feed. */
    public Request review(UUID deviceUid)throws IOException {
        FeedSyncState.Snapshot s=sync.read();
        if(s==null||!s.following)throw new IOException("Not following a feed");
        LocalV2.Selection selected=local.load().select(deviceUid);
        return new Request(deviceUid,new ImagePublicationReview(selected,s.contextId,s.feedName,images),s,selected);
    }

    /** W2/W3: one attempt. Never retried or resent; the caller shows the result and the user decides. */
    public synchronized Result publish(Request request,BooleanSupplier active){
        UUID uid=request.deviceUid;
        synchronized(request){if(request.used)throw new IllegalStateException("Request already used");request.used=true;}
        Path file=null;
        try{
            FeedSyncState.Snapshot shown=request.destination;
            String changed=destinationChanged(shown);
            if(changed!=null){request.close();return failed(uid,changed);}
            String oldHash=replacedBase(shown,uid);
            byte[] bytes;String hash,name;
            try{
                ImagePublicationReview.Prepared prepared=request.review.confirm(local.load(),shown.contextId,shown.feedName,images);
                clearOutbox();require(active);
                name=frozen.export(prepared,()->!closed&&active.getAsBoolean());file=outbox.resolve(name);
                bytes=readBack(file,prepared);hash=hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            }catch(Exception|OutOfMemoryError notReady){
                return failed(uid,"Bilden kunde inte förberedas för publicering. Granska på nytt.");
            }finally{request.close();}
            String creator=creator(),guid;
            if(creator==null)return failed(uid,"ATAK:s enhets-ID kunde inte användas.");
            try{guid=guid(shown);}catch(RuntimeException unsupported){return failed(uid,"Den sparade källan stöds inte längre.");}
            // W2: recheck immediately before the first byte leaves the device.
            changed=destinationChanged(shown);if(changed!=null||!canContinue(active))return failed(uid,changed!=null?changed:"Publiceringen avbröts.");
            Transport transport;
            try{transport=host.connect(endpoint(shown));}catch(RuntimeException unavailable){return failed(uid,"Den sparade källan kunde inte öppnas.");}
            if(!canContinue(active))return failed(uid,"Publiceringen avbröts.");
            String returned;
            try{returned=transport.upload(name,creator,bytes,attempt(UPLOAD_SECONDS));}
            catch(WriteFailure f){return outcome(uid,f.kind,"");}
            catch(RuntimeException unexpected){return new Result(uid,Outcome.UNKNOWN,UNKNOWN_TEXT);}
            if(!hash.equals(returned))return failed(uid,"Servern bekräftade inte samma bildinnehåll. Bilden kopplades inte till feeden.");
            String stored=" Bilden finns kvar i serverns innehållslager men är inte kopplad till feeden.";
            changed=destinationChanged(shown);if(changed!=null||!canContinue(active))return failed(uid,(changed!=null?changed:"Publiceringen avbröts.")+stored);
            try{transport.associate(guid,hash,creator,attempt(WRITE_SECONDS));}
            catch(WriteFailure f){
                if(f.kind==WriteFailure.Kind.UNKNOWN)remember(uid,shown,hash,oldHash);
                return outcome(uid,f.kind,f.kind==WriteFailure.Kind.UNKNOWN?"":stored);
            }catch(RuntimeException unexpected){remember(uid,shown,hash,oldHash);return new Result(uid,Outcome.UNKNOWN,UNKNOWN_TEXT);}
            remember(uid,shown,hash,oldHash);
            return new Result(uid,Outcome.SUCCEEDED,"Bilden är uppladdad och kopplad till "+shown.feedName+". Andra klienter får den vid nästa hämtning."
                    +(adoptSent(request,bytes)?"":" Den lokala bilden kunde inte ersättas med den publicerade kopian; nästa hämtning kan visa Konflikt."));
        }finally{
            current=null;
            if(file!=null)try{Files.deleteIfExists(file);}catch(IOException|RuntimeException kept){}
        }
    }

    /**
     * The export re-encodes the PNG (same pixels, no ancillary chunks), so the feed's photo differs byte-wise from the
     * local one and the next fetch would see a conflict. The local photo becomes exactly the published copy instead,
     * with the reviewed edit time kept, so U3 then finds R = L. Refused when the device changed since the review.
     */
    private boolean adoptSent(Request request,byte[] container){
        try{
            if(!request.selected.isCurrent(local.load()))return false;
            ReceivedImagePayload sent=ImageContainerCodec.read(new ByteArrayInputStream(container));
            try(LocalImages.Pending pending=images.prepareReceived(sent.image)){
                new DraftAttachments(local,images).attach(request.selected,pending,request.selected.draft.updatedAt);
            }
            return true;
        }catch(Exception|OutOfMemoryError notReplaced){return false;}
    }

    /** W4 input: the registry records present in a completed fetch ({@link FeedFollowUi.Host#onFetchCompleted}). */
    public synchronized void fetchCompleted(List<ImageAssociationRegistry.Record> records)throws IOException {
        Map<String,List<ImageAssociationRegistry.Record>> next=new HashMap<>();
        for(ImageAssociationRegistry.Record r:records){
            String hash=containerHashes.get(r.stageLocalId);
            if(hash==null){hash=FeedFollowUi.containerHash(stage.read(r.stageLocalId));containerHashes.put(r.stageLocalId,hash);}
            next.computeIfAbsent(hash,h->new ArrayList<>()).add(r);
        }
        seen=next;
    }

    /**
     * W4: for each publication whose new photo the latest fetch showed in the feed, one removal of the photo that
     * was the device's image base before it. Exactly that resource, only while it was also seen; no retry.
     */
    public synchronized List<Result> removeReplaced(BooleanSupplier active){
        List<Result> results=new ArrayList<>();
        for(Map.Entry<UUID,Replacement> e:new ArrayList<>(replacements.entrySet())){
            UUID uid=e.getKey();Replacement r=e.getValue();
            if(!seenFor(r.newHash,uid,r.destination.contextId))continue;
            replacements.remove(uid);
            if(r.oldHash==null||!seenFor(r.oldHash,uid,r.destination.contextId))continue;
            String changed=destinationChanged(r.destination),creator=creator();
            if(changed==null&&creator==null)changed="ATAK:s enhets-ID kunde inte användas.";
            if(changed!=null||!canContinue(active)){results.add(failed(uid,"Den ersatta bilden togs inte bort ur feeden: "+(changed!=null?changed:"avbrutet.")));continue;}
            try{
                Transport transport=host.connect(endpoint(r.destination));
                if(!canContinue(active)){results.add(failed(uid,"Den ersatta bilden togs inte bort ur feeden: avbrutet."));continue;}
                transport.remove(guid(r.destination),r.oldHash,creator,attempt(WRITE_SECONDS));
                results.add(new Result(uid,Outcome.SUCCEEDED,"Den ersatta bilden togs bort ur feeden. Den finns kvar i serverns innehållslager och hos klienter som redan hämtat den."));
            }catch(WriteFailure f){
                results.add(f.kind==WriteFailure.Kind.REFUSED?new Result(uid,Outcome.REFUSED,REFUSED_TEXT)
                        :new Result(uid,f.kind==WriteFailure.Kind.UNKNOWN?Outcome.UNKNOWN:Outcome.FAILED,"Den ersatta bilden kunde inte tas bort ur feeden. TMM försöker inte igen automatiskt."));
            }catch(RuntimeException unexpected){
                results.add(new Result(uid,Outcome.UNKNOWN,"Okänt om den ersatta bilden togs bort ur feeden. TMM försöker inte igen automatiskt."));
            }finally{current=null;}
        }
        return results;
    }

    /** Whether a removal is still waiting for its new photo to be seen. */
    public synchronized boolean awaitingRemoval(UUID deviceUid){return replacements.containsKey(deviceUid);}

    @Override public void close(){closed=true;MissionReadAttempt a=current;if(a!=null)a.cancel();timer.shutdownNow();}

    /** W2: null when following is on and profile, connect string, provider, service and GUID are as shown. */
    String destinationChanged(FeedSyncState.Snapshot shown){
        if(closed||!host.available())return "TMM är inte aktivt.";
        FeedSyncState.Snapshot now;
        try{now=sync.read();}catch(IOException|RuntimeException unreadable){return "Följningsfilen kunde inte läsas.";}
        if(now==null||!now.following)return "Följningen är avstängd.";
        if(!now.syncId.equals(shown.syncId)||!now.contextId.equals(shown.contextId)||!now.streamConnect.equals(shown.streamConnect)
                ||!now.feedName.equals(shown.feedName))return "Följd feed eller profil har ändrats. Granska på nytt.";
        try{
            if(!host.defaultProvider())return "ATAK:s kommunikationsleverantör har ändrats.";
            if(endpoint(now)==null)return "Den sparade källan stöds inte längre.";
            if(!host.profiles().contains(now.streamConnect))return "Den sparade profilen är inte ansluten.";
        }catch(RuntimeException unavailable){return "ATAK:s serverlista kunde inte läsas.";}
        return null;
    }
    /** The single feed resource that held the device's image base in the latest fetch, or null when not exactly one. */
    private String replacedBase(FeedSyncState.Snapshot s,UUID uid){
        FeedSyncState.Snapshot now;FeedSyncState.Record base=null;
        try{now=sync.read();}catch(IOException|RuntimeException unreadable){return null;}
        if(now!=null)for(FeedSyncState.Record r:now.records)if(r.deviceUid.equals(uid))base=r;
        if(base==null||base.imageKind!=FeedSyncState.IMAGE_DIGEST)return null;
        Set<String> hashes=new HashSet<>();
        for(Map.Entry<String,List<ImageAssociationRegistry.Record>> e:seen.entrySet())
            for(ImageAssociationRegistry.Record r:e.getValue())
                if(r.deviceUid.equals(uid)&&r.contextId.equals(s.contextId)&&Arrays.equals(r.sha256(),base.imageDigest()))hashes.add(e.getKey());
        return hashes.size()==1?hashes.iterator().next():null;
    }
    private boolean seenFor(String hash,UUID uid,String contextId){
        List<ImageAssociationRegistry.Record> list=seen.get(hash);
        if(list!=null)for(ImageAssociationRegistry.Record r:list)if(r.deviceUid.equals(uid)&&r.contextId.equals(contextId))return true;
        return false;
    }
    private void remember(UUID uid,FeedSyncState.Snapshot destination,String newHash,String oldHash){
        if(oldHash!=null&&!oldHash.equals(newHash))replacements.put(uid,new Replacement(destination,newHash,oldHash));else replacements.remove(uid);
    }
    private MissionReadAttempt attempt(int seconds){
        MissionReadAttempt a=seconds==UPLOAD_SECONDS?MissionReadAttempt.forImage(System.nanoTime()):new MissionReadAttempt(System.nanoTime());
        current=a;if(closed)a.cancel();
        try{a.bindDeadline(timer.schedule(a::cancel,seconds,TimeUnit.SECONDS));}catch(RejectedExecutionException stopped){a.cancel();}
        return a;
    }
    private byte[] readBack(Path file,ImagePublicationReview.Prepared prepared)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>ImageContainerCodec.MAX_BYTES)throw new IOException();
        byte[] bytes=Files.readAllBytes(file);
        ReceivedImagePayload back=ImageContainerCodec.read(new ByteArrayInputStream(bytes));
        if(!Arrays.equals(ImageOfferCodec.encode(back.offer),prepared.descriptor())||!Arrays.equals(back.image.bytes(),prepared.png()))throw new IOException();
        return bytes;
    }
    /** The outbox holds at most the current attempt's file; a crash can leave one, which the next attempt deletes. */
    private void clearOutbox()throws IOException {
        if(Files.notExists(outbox,LinkOption.NOFOLLOW_LINKS))return;
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(outbox)){
            for(Path f:entries)if(f.getFileName().toString().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(?:tmmi|pending)"))Files.delete(f);
        }
    }
    private Result outcome(UUID uid,WriteFailure.Kind kind,String suffix){
        switch(kind){
            case REFUSED:return new Result(uid,Outcome.REFUSED,REFUSED_TEXT+suffix);
            case UNKNOWN:return new Result(uid,Outcome.UNKNOWN,UNKNOWN_TEXT);
            case REJECTED:return failed(uid,"Servern tog inte emot bilden."+suffix);
            default:return failed(uid,"Bilden skickades inte. Kontrollera anslutningen och försök igen."+suffix);
        }
    }
    /** Query-safe ATAK device UID, or null. */
    private String creator(){
        try{String value=host.creatorUid();return value!=null&&value.matches("[A-Za-z0-9._:-]{1,128}")?value:null;}catch(RuntimeException unavailable){return null;}
    }
    private static Result failed(UUID uid,String why){return new Result(uid,Outcome.FAILED,why+" Enheten är fortfarande Ej publicerad.");}
    private boolean canContinue(BooleanSupplier active){return !closed&&!Thread.currentThread().isInterrupted()&&active.getAsBoolean();}
    private static void require(BooleanSupplier active)throws IOException {if(Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw new IOException();}
    private static MissionEndpoint endpoint(FeedSyncState.Snapshot s){return FeedFollowUi.endpoint(s);}
    private static String guid(FeedSyncState.Snapshot s){
        String guid=s.contextId.substring(s.contextId.lastIndexOf('|')+1);UUID id=UUID.fromString(guid);
        if(!id.toString().equals(guid))throw new IllegalArgumentException("Invalid mission GUID");return guid;
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
