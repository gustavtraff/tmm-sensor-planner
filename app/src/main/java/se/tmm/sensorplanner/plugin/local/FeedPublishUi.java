package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.LocalImageBitmaps;
import se.tmm.sensorplanner.plugin.ingress.*;

/**
 * #243 ordinary Publicera (M1–M5, W1–W4): one saved device to the followed feed (U2), with one preview, one
 * confirmation and one attempt. Metadata goes through the reviewed native host copy or update (G1–G5 geometry);
 * TMM cannot choose the native destination, so success is read back with the same GET as fetching. A new photo is
 * uploaded only after that readback (M5). No queue, retry or background write; fetching stays GET only.
 */
@TargetApi(26)
public final class FeedPublishUi {
    public interface Host {
        void post(Runnable action);
        boolean available();
        /** M2: the followed profile is the only enabled stream profile and it is connected; false when unreadable. */
        boolean singleStream(String streamConnect);
        String summary(ShareReview.Metadata metadata);
        String coordinates(Position position);
        /** UI thread: whether the map holds any item with this UUID (update) rather than none (create). */
        boolean hostCopyExists(UUID uid);
        /** UI thread: the reviewed host update with G1–G5 position and bearing. Throws when it cannot be reviewed. */
        SavedDraftCopyUi.PendingUpdate inspectUpdate(PublicationPreparationReview.Prepared prepared, String label);
        /** UI thread: the reviewed host copy for a device the map does not hold yet. */
        String create(PublicationPreparationReview.Prepared prepared, String label);
        /** GET-only reads of the followed feed: the same transport as fetching. */
        FeedFollowUi.Transport connect(MissionEndpoint endpoint);
        /** The readback showed the published M; the normal fetch then clears Ej publicerad (L = R). */
        void published();
    }
    private static final class Preview {
        final FeedSyncState.Snapshot destination;final LocalV2.Selection selected;final PublicationPreparationReview.Prepared prepared;
        final String label;final FeedPhotoPublication.Request photo;final Bitmap bitmap;SavedDraftCopyUi.PendingUpdate update;
        Preview(FeedSyncState.Snapshot destination,LocalV2.Selection selected,PublicationPreparationReview.Prepared prepared,String label,
                FeedPhotoPublication.Request photo,Bitmap bitmap){
            this.destination=destination;this.selected=selected;this.prepared=prepared;this.label=label;this.photo=photo;this.bitmap=bitmap;
        }
        void close(){if(photo!=null)photo.close();if(update!=null)update.close();}
    }
    static final String NOT_IN_FEED="Metadata ännu inte i feeden, tryck Publicera igen.";
    // M5: about 30 s of readback within the same tap; package-private so tests need not wait.
    long waitMillis=30_000,pollMillis=3_000;
    private final Context context;private final LocalV2Store store;private final LocalImages images;private final Executor worker;
    private final FeedPhotoPublication photos;private final FeedSyncState sync;private final Host host;
    private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"TMM-publish-deadline");t.setDaemon(true);return t;});
    private final Object lifecycle=new Object();
    private long generation;private boolean closed;private volatile boolean busy;
    private Preview pending;private CountDownLatch waiting;private volatile MissionReadAttempt reading;private AlertDialog dialog;

    public FeedPublishUi(Context context,LocalV2Store store,LocalImages images,java.nio.file.Path privateRoot,Executor worker,
                         FeedPhotoPublication photos,Host host){
        this.context=context;this.store=store;this.images=images;this.worker=worker;this.photos=photos;this.host=host;
        sync=new FeedSyncState(privateRoot.resolve("feed-sync-v1"));timer.setRemoveOnCancelPolicy(true);
    }
    public boolean isBusy(){return busy;}

    /** Publicera: checks and freezes one preview off the UI thread. Nothing is written before confirmation. */
    public void show(UUID uid){
        long token;
        synchronized(lifecycle){if(closed||busy||!host.available())return;busy=true;token=++generation;}
        progress("Förbereder publicering","Inget skickas före bekräftelse.");
        worker.execute(()->{
            Preview p=null;
            try{
                if(!valid(token))return;
                FeedSyncState.Snapshot s=sync.read();
                String refused=gate(s);
                if(refused!=null){post(token,()->result("Inte publicerat",refused));return;}
                LocalV2.Selection selected=store.load().select(uid);
                if(selected.draft==null)throw new IOException("No saved device");
                PublicationPreparationReview.Prepared prepared=new PublicationPreparationReview(selected.draft).prepare(selected.draft);
                PublicationCodec.decode(new ByteArrayInputStream(PublicationCodec.encode(prepared)));
                FeedPhotoPublication.Request photo=newPhoto(s,selected.draft)?photos.review(uid):null;
                try{p=new Preview(s,selected,prepared,selected.draft.displayId+" · r"+selected.draft.revision,photo,
                        photo==null?null:LocalImageBitmaps.preview(photo.review.preview()));}
                catch(IOException|RuntimeException e){if(photo!=null)photo.close();throw e;}
                Preview frozen=p;p=null;
                if(!post(token,()->inspect(frozen,token)))frozen.close();
            }catch(Exception|OutOfMemoryError unavailable){
                if(p!=null)p.close();
                post(token,()->result("Inte publicerat","Den sparade enheten eller dess bild kunde inte läsas. Inget har skickats."));
            }
        });
    }
    /** UI thread: the existing host copy decides update or create; its old values feed the G3 comparison. */
    private void inspect(Preview p,long token){
        ShareReview.Metadata old=null;
        try{
            if(host.hostCopyExists(p.prepared.metadata.uid)){
                p.update=Objects.requireNonNull(host.inspectUpdate(p.prepared,p.label));old=p.update.previous();
                // A photo-only save (e.g. adopting the published PNG) bumps the local revision but not the shared M.
                // Keep the host's own "ID · rN" label then, so an unchanged Publicera makes no host write.
                String kept=p.update.previousLabel();
                if(!p.label.equals(kept)&&sameShared(old,p.prepared.metadata)
                        &&kept.matches(java.util.regex.Pattern.quote(old.displayId+" · r")+"[1-9][0-9]*")){
                    p.update.close();p.update=null;
                    p.update=Objects.requireNonNull(host.inspectUpdate(p.prepared,kept));old=p.update.previous();
                }
            }
        }catch(RuntimeException refused){
            p.close();
            result("Inte publicerat","Kartobjektet med samma UUID kunde inte granskas. Det kan vara flyttat i ATAK, ha värdbilagor, ha ändrad typ eller finnas flera gånger. Inget har skickats.");
            return;
        }
        synchronized(lifecycle){if(!valid(token)){p.close();return;}pending=p;}
        preview(p,old,token);
    }
    private void preview(Preview p,ShareReview.Metadata old,long token){
        String feed=p.destination.feedName;ShareReview.Metadata next=p.prepared.metadata;
        boolean moved=old!=null&&(old.position.latitude!=next.position.latitude||old.position.longitude!=next.position.longitude);
        boolean turned=old!=null&&!Objects.equals(old.bearingDeg,next.bearingDeg);
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,16,24,16);
        StringBuilder text=new StringBuilder("Feed: ").append(feed).append("\n\n");
        // G3: old and new geometry side by side before the summary.
        if(moved||turned)text.append("ÄNDRAD GEOMETRI · före → efter\nPosition · MGRS: ").append(host.coordinates(old.position)).append(" → ")
                .append(host.coordinates(next.position)).append(moved?"":" (oförändrad)").append("\nBäring: ").append(bearing(old.bearingDeg))
                .append(" → ").append(bearing(next.bearingDeg)).append(turned?"":" (oförändrad)").append("\n\n");
        text.append(old==null?"NY ENHET I KARTAN\n":"METADATA SOM PUBLICERAS\n").append(host.summary(next)).append("\n\n")
                .append(p.photo!=null?"NY BILD · laddas upp direkt till "+feed+" när metadata syns där.":
                        p.selected.draft.imageRef==null?"Ingen bild. Endast metadata publiceras.":"Bilden är oförändrad och laddas inte upp igen.");
        TextView summary=new TextView(context);summary.setText(text);body.addView(summary);
        if(p.bitmap!=null){ImageView image=new ImageView(context);image.setImageBitmap(p.bitmap);image.setAdjustViewBounds(true);image.setContentDescription("Bild som publiceras");body.addView(image);}
        // M3: Data Sync decides the metadata destination; TMM reports success only from readback.
        TextView route=new TextView(context);
        route.setText("\nNÄSTA STEG I ATAK\n"+nativeStep(old==null,feed)
                +"\nATAK:s Data Sync bestämmer vart uppgifterna går; TMM kan inte styra det. Välj endast "+feed+"."
                +"\nTMM visar Publicerad när samma uppgifter har lästs tillbaka från "+feed+" (upp till 30 s)."
                +(p.photo!=null?" Syns den inte laddas ingen bild upp.":"")
                +"\nEndast syntetiska data i denna version.");
        body.addView(route);
        CheckBox position=new CheckBox(context);
        position.setText("Jag har kontrollerat den nya positionen. Den ersätter den gamla för alla som ser enheten.");
        position.setMinHeight(Math.round(48*context.getResources().getDisplayMetrics().density));position.setChecked(!moved);
        if(moved)body.addView(position);
        ScrollView scroll=new ScrollView(context);scroll.addView(body);
        AlertDialog shown=new AlertDialog.Builder(context).setTitle("Publicera till "+feed).setView(scroll)
                .setNegativeButton("Avbryt",(d,w)->cancel()).setPositiveButton("Publicera",null).create();
        replace(shown);shown.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(position.isChecked());
        position.setOnCheckedChangeListener((b,checked)->shown.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            synchronized(lifecycle){if(!valid(token)||pending!=p||!position.isChecked())return;pending=null;}
            progress("Nästa steg i ATAK",nativeStep(p.update==null,feed)
                    +"\n\nTryck Fortsätt i ATAK för att komma åt Data Sync. TMM fortsätter kontrollera feeden i upp till 30 s."
                    +" Avbryt ångrar inte kartändringar eller sådant som redan skickats.",true);
            worker.execute(()->{try{commit(p,token);}finally{p.close();}});
        });
    }
    /** Worker: W4, freshness and M2 rechecks, the host write, readback, then the photo. One attempt. */
    private void commit(Preview p,long token){
        BooleanSupplier active=()->valid(token)&&host.available();
        StringBuilder out=new StringBuilder();
        // W4 runs only here, in an explicit Publicera, for replacements whose new photo a fetch has shown.
        for(FeedPhotoPublication.Result r:photos.removeReplaced(active))out.append("\n\n").append(r.message);
        try{
            if(!p.selected.isCurrent(store.load())){post(token,()->result("Inte publicerat","Enheten har ändrats sedan granskningen. Granska på nytt. Inget har skickats."+out));return;}
        }catch(IOException|RuntimeException unreadable){post(token,()->result("Inte publicerat","Den sparade enheten kunde inte läsas. Inget har skickats."+out));return;}
        String refused=gate(p.destination);
        if(refused!=null){post(token,()->result("Inte publicerat",refused+" Inget har skickats."+out));return;}
        String written=handoff(()->p.update!=null?p.update.commit():host.create(p.prepared,p.label),token);
        if(written==null)return;
        String step="Kartsteg: "+written;
        if(written.startsWith("Inte ")){post(token,()->result("Ej publicerad","Kartsteget kunde inte slutföras. Dina sparade ändringar finns kvar.",step+out));return;}
        stage(token,"Väntar på feeden",nativeStep(p.update==null,p.destination.feedName)
                +"\n\nTMM kontrollerar att uppgifterna syns i "+p.destination.feedName+". Om det tar mer än 30 s behöver du trycka Publicera igen.");
        Boolean seen=readBack(p,active);
        if(seen==null)return;
        if(!seen){
            String why=p.photo!=null?NOT_IN_FEED+" Ingen bild har laddats upp.":"Metadata ännu inte i feeden. Kontrollera Data Sync-steget och tryck Publicera igen.";
            post(token,()->result("Ej publicerad",why+" Dina sparade ändringar finns kvar.",step+out));return;
        }
        if(p.photo!=null)stage(token,"Publicerar bilden","Uppgifterna finns i "+p.destination.feedName+". Bilden laddas nu upp till samma feed.");
        FeedPhotoPublication.Result photo=p.photo==null?null:photos.publish(p.photo,active);
        boolean done=photo==null||photo.outcome==FeedPhotoPublication.Outcome.SUCCEEDED;
        String text="Uppgifterna finns i feeden "+p.destination.feedName+"."+(photo==null?"":"\n\n"+photo.message)
                +"\n\nStatusen i listan uppdateras vid nästa hämtning.";
        post(token,()->{host.published();result(done?"Publicerad":"Metadata publicerad · bilden inte bekräftad",text,step+out);});
    }
    /** M5 readback: the same GET as fetching, repeated until the feed's M for this UUID is exactly the saved M. Null when cancelled. */
    private Boolean readBack(Preview p,BooleanSupplier active){
        TmmDraft saved=p.selected.draft;FeedSyncState.Snapshot s=p.destination;
        long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(waitMillis);
        while(true){
            if(!active.getAsBoolean())return null;
            try{
                MissionEndpoint endpoint=FeedFollowUi.endpoint(s);
                MissionSnapshotInput.Feed feed=new MissionSnapshotInput.Feed(s.feedName,s.contextId.substring(s.contextId.lastIndexOf('|')+1),endpoint.base);
                if(!feed.context().equals(s.contextId))return false;
                MissionReadAttempt attempt=new MissionReadAttempt(System.nanoTime());reading=attempt;
                try{
                    attempt.bindDeadline(timer.schedule(attempt::cancel,12,TimeUnit.SECONDS));
                    MissionSnapshotInput.Snapshot read=host.connect(endpoint).fetch(feed,attempt);
                    if(attempt.active(System.nanoTime())&&read.feed.context().equals(s.contextId))
                        for(TmmDraft remote:read.metadata)if(remote.uid.equals(saved.uid)&&exact(remote,saved))return true;
                }finally{attempt.clearTimers();reading=null;}
            }catch(Exception|OutOfMemoryError notYet){/* the next read decides; nothing is written */}
            if(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(pollMillis)>=until)return false;
            try{Thread.sleep(pollMillis);}catch(InterruptedException stop){Thread.currentThread().interrupt();return null;}
        }
    }
    /** M2 plus W2: following, the same stored destination, provider, connected profile and the single-stream rule. */
    private String gate(FeedSyncState.Snapshot s){
        if(s==null||!s.following)return "TMM följer ingen feed. Välj Följ feed först. Den valda feeden visas som mål när du publicerar.";
        String changed=photos.destinationChanged(s);
        if(changed!=null)return changed;
        try{if(!host.singleStream(s.streamConnect))return "Publicera kräver att den följda profilen är den enda aktiverade serverprofilen i ATAK och att den är ansluten.";}
        catch(RuntimeException unreadable){return "ATAK:s serverlista kunde inte läsas.";}
        return null;
    }
    /** A photo is new unless it is exactly the image base the feed last showed for this device. */
    private boolean newPhoto(FeedSyncState.Snapshot s,TmmDraft draft)throws IOException {
        if(draft.imageRef==null)return false;
        for(FeedSyncState.Record r:s.records)if(r.deviceUid.equals(draft.uid))
            return r.imageKind!=FeedSyncState.IMAGE_DIGEST||!MessageDigest.isEqual(FeedSyncApply.sha256(images.read(draft.imageRef)),r.imageDigest());
        return true;
    }
    /** The shared M as the host copy carries it (PublicationCodec fields); the revision is local only. */
    private static boolean sameShared(ShareReview.Metadata a,ShareReview.Metadata b){
        return a.uid.equals(b.uid)&&a.displayId.equals(b.displayId)&&a.type==b.type&&a.status==b.status&&Objects.equals(a.owner,b.owner)
                &&a.createdAt.equals(b.createdAt)&&a.updatedAt.equals(b.updatedAt)&&Objects.equals(a.deployedAt,b.deployedAt)
                &&Objects.equals(a.comment,b.comment)&&a.position.latitude==b.position.latitude&&a.position.longitude==b.position.longitude
                &&Objects.equals(a.bearingDeg,b.bearingDeg);
    }
    // U4 binds a photo to the exact M, so the readback compares edit time too.
    private static boolean exact(TmmDraft remote,TmmDraft saved){return FeedSyncDecision.sameContent(remote,saved)&&remote.updatedAt.equals(saved.updatedAt);}

    /** Worker waits while the UI thread runs the host write; ordinary edits cannot overtake it. Null when cancelled. */
    private String handoff(Supplier<String> operation,long token){
        CountDownLatch done=new CountDownLatch(1);String[] outcome=new String[1];
        synchronized(lifecycle){if(!valid(token))return null;waiting=done;}
        boolean posted=post(token,()->{
            synchronized(lifecycle){
                if(waiting!=done||!valid(token)){done.countDown();return;}
                try{outcome[0]=Objects.requireNonNull(operation.get());}
                catch(RuntimeException uncertain){outcome[0]="Kartsteget försökt – resultat oklart. Kontrollera kartan och Data Sync. Ingen automatisk återställning eller omsändning görs.";}
                finally{done.countDown();}
            }
        });
        if(posted)try{done.await(10,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        synchronized(lifecycle){
            if(waiting==done)waiting=null;
            if(!valid(token))return null;
            return outcome[0]!=null?outcome[0]:"Inte publicerat: kartsteget kunde inte startas. Granska på nytt.";
        }
    }
    private boolean valid(long token){synchronized(lifecycle){return !closed&&generation==token;}}
    private boolean post(long token,Runnable action){
        try{host.post(()->{if(!valid(token))return;if(!host.available()){cancel();return;}action.run();});return true;}
        catch(RuntimeException unavailable){return false;}
    }
    private void progress(String title,String message){
        progress(title,message,false);
    }
    private void progress(String title,String message,boolean nativeStep){
        AlertDialog.Builder builder=new AlertDialog.Builder(context).setTitle(title).setMessage(message).setNegativeButton("Avbryt",(d,w)->cancel());
        // Dismissing the modal exposes ATAK without cancelling the already confirmed attempt.
        if(nativeStep)builder.setNeutralButton("Fortsätt i ATAK",(d,w)->{if(dialog==d)dialog=null;});
        replace(builder.create());
    }
    private void stage(long token,String title,String message){
        post(token,()->{if(dialog!=null&&dialog.isShowing()){dialog.setTitle(title);dialog.setMessage(message);}});
    }
    private static String nativeStep(boolean newDevice,String feed){
        return newDevice?"Ny enhet: öppna "+feed+" i Data Sync, välj Add Items, markera enheten och tryck Done."
                :"Om ATAK visar Publish Content to Feeds, välj endast "+feed+" och bekräfta. Nej återställer inte den lokala kartändringen.";
    }
    private void result(String title,String message){
        result(title,message,null);
    }
    private void result(String title,String message,String details){
        AlertDialog.Builder builder=new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("Stäng",(d,w)->finish());
        if(details!=null&&!details.isEmpty())builder.setNeutralButton("Visa detaljer",(d,w)->
                replace(new AlertDialog.Builder(context).setTitle("Publiceringsdetaljer").setMessage(details)
                        .setPositiveButton("Tillbaka",(x,y)->result(title,message,details)).create()));
        replace(builder.create());
    }
    private void replace(AlertDialog next){
        if(dialog!=null)dialog.dismiss();
        dialog=next;dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->cancel());dialog.show();
    }
    /** Avbryt: ends this attempt; a host write or upload already under way is not undone. */
    public void cancel(){finish();}
    private void finish(){
        synchronized(lifecycle){invalidate();busy=false;}
        if(dialog!=null){dialog.dismiss();dialog=null;}
    }
    private void invalidate(){
        generation++;
        if(pending!=null){pending.close();pending=null;}
        if(waiting!=null){waiting.countDown();waiting=null;}
        MissionReadAttempt a=reading;if(a!=null)a.cancel();
    }
    public void close(){synchronized(lifecycle){closed=true;}finish();timer.shutdownNow();}
    private static String bearing(Double degrees){return degrees==null?"Ej angiven":BearingMilsEdit.format(degrees)+" mils";}
}
