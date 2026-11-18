package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import se.tmm.sensorplanner.plugin.images.*;
import se.tmm.sensorplanner.plugin.ingress.*;
import se.tmm.sensorplanner.plugin.ingress.MissionImageInput.Resource;
import se.tmm.sensorplanner.plugin.ingress.MissionSnapshotInput.Feed;

/**
 * #236 U1/U2/U7/U8: a stored, explicit feed choice and a GET-only fetch timer while the plugin is loaded.
 * Each fetch retains metadata in the inbox and verified images in the stage/registry, then
 * {@link FeedSyncApply} applies conflict-free updates locally (U3–U5). No publication, upload,
 * subscription or receipt.
 */
@TargetApi(26)
public final class FeedFollowUi {
    public interface Transport {
        String description();
        List<Feed> list(MissionReadAttempt attempt)throws Exception;
        MissionSnapshotInput.Snapshot fetch(Feed feed,MissionReadAttempt attempt)throws Exception;
        List<Resource> images(Feed feed,MissionReadAttempt attempt)throws Exception;
        ReceivedImagePayload image(Feed feed,Resource resource,MissionReadAttempt attempt)throws Exception;
    }
    public interface Host {
        void post(Runnable action);boolean available();
        /** Enabled, connected, eligible stream connect strings (empty for a non-default provider). */
        List<String> profiles();
        /** True when ATAK still has a stream profile with exactly this connect string, connected or not. */
        boolean configured(String stream);
        boolean defaultProvider();
        Transport connect(MissionEndpoint endpoint);
        void notice(String text);
        /** After a successful fetch and its local apply: inbox receipts and registry records present in that fetch. */
        default void onFetchCompleted(List<UUID> receiptIds,List<ImageAssociationRegistry.Record> records){}
        /** The app's field text for a device (including MGRS and mils), for the conflict view. */
        default String summary(TmmDraft draft){return draft.displayId;}
        /** A conflict was resolved: reload local devices and the markers. */
        default void resolved(){}
    }
    private interface Network<T>{T run(MissionReadAttempt attempt)throws Exception;}
    static final long INTERVAL=60_000,MAX_INTERVAL=300_000;
    private final Context context;private final Host host;private final Executor worker;private final LocalV2Store local;
    private final InboxStore inbox;private final ImageAssociationRegistry registry;private final ImageStageStore stage;private final FeedSyncState sync;private final FeedSyncApply apply;private final FeedConflictUi conflictUi;
    private final Path inboxFile,registryFile,stageRoot;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"TMM-feed-follow");t.setDaemon(true);return t;});
    private final MissionSourceChoiceUi sourceChoice;
    // Ready stage entries are immutable, so a computed container hash stays valid for the process.
    private final Map<UUID,String> containerHashes=new ConcurrentHashMap<>();
    private final AtomicLong generation=new AtomicLong(),setup=new AtomicLong();
    private final Runnable tick=this::trigger;
    private volatile boolean closed,loaded,running,busy;private volatile MissionReadAttempt pending;
    private volatile FeedSyncState.Snapshot state;private volatile String broken;
    private long delay=INTERVAL;private String paused,status="Ingen hämtning ännu.";private AlertDialog dialog;

    public FeedFollowUi(Context context,LocalV2Store local,LocalImages images,Path privateRoot,Executor worker,Host host){
        this.context=context;this.local=local;this.worker=worker;this.host=host;
        inboxFile=privateRoot.resolve("metadata-inbox-v1/inbox.bin");registryFile=privateRoot.resolve("received-image-association-v1/associations.bin");stageRoot=privateRoot.resolve("received-image-stage-v1");
        inbox=new InboxStore(inboxFile.getParent());registry=new ImageAssociationRegistry(registryFile.getParent());stage=new ImageStageStore(stageRoot);
        sync=new FeedSyncState(privateRoot.resolve("feed-sync-v1"));apply=new FeedSyncApply(sync,inbox,stage,local,images);
        conflictUi=new FeedConflictUi(context,worker,apply,images,new FeedConflictUi.Host(){
            public void post(Runnable action){host.post(action);}public boolean available(){return !closed&&host.available();}
            public String summary(TmmDraft draft){return host.summary(draft);}public void changed(){host.resolved();}
        });
        sourceChoice=new MissionSourceChoiceUi(context,new MissionSourceChoiceUi.Host(){
            public boolean available(){return !closed&&host.available();}public List<String> profiles(){return host.profiles();}
            public void selected(MissionEndpoint endpoint){listFeeds(endpoint);}
        },"Följ feed: TMM hämtar metadata och bilder från vald feed vid start, vid anslutning, var 60:e sekund och vid Uppdatera nu, så länge pluginet är laddat. Endast läsning (GET). Dina utkast och bilder skickas inte; ingen publicering, uppladdning, prenumeration eller kvittens. ");
    }

    /** Plugin load: read the stored choice and fetch at once when following. A reader never creates the file. */
    public void start(){
        if(closed)return;
        worker.execute(()->{
            FeedSyncState.Snapshot read=null;String error=null;
            try{read=sync.read();}catch(Exception unreadable){error="Följningsfilen kunde inte läsas. Följningen är stoppad; filen ändras eller återställs inte automatiskt.";}
            // U5: resolve an unconfirmed image adoption before any fetch; a failure is retried before the next plan.
            if(read!=null)try{FeedSyncState.Snapshot recovered=apply.recover();if(recovered!=null)read=recovered;}catch(Exception|OutOfMemoryError retry){}
            FeedSyncState.Snapshot value=read;String failure=error;
            host.post(()->{if(closed)return;state=value;broken=failure;loaded=true;if(failure!=null)status=failure;trigger();});
        });
    }
    public boolean isBusy(){return busy||conflictUi.isBusy()||dialog!=null&&dialog.isShowing()||sourceChoice.isBusy();}
    public boolean isFetching(){return running;}
    /** Read-only for the list: devices in conflict, and followed devices with unpublished local changes (L ≠ B). */
    public Set<UUID> conflicts(){return apply.conflicts();}
    public Set<UUID> unpublished(){return apply.unpublished();}
    /** Text marker for the list and details (not colour only): Konflikt, else Ej publicerad, else null. */
    public String marker(UUID uid){return conflicts().contains(uid)?"Konflikt":unpublished().contains(uid)?"Ej publicerad":null;}
    /** Side-by-side view with Behåll min and Använd inkommande for a device in {@link #conflicts()}. */
    public void showConflict(UUID uid){conflictUi.show(uid);}
    /** Devices changed locally by the latest apply. */
    public int applied(){return apply.applied();}
    public String label(){
        FeedSyncState.Snapshot s=state;
        if(broken!=null)return "Följ feed · stoppad";if(s==null)return "Följ feed";if(!s.following)return "Följ feed · avstängd";
        return paused!=null?"Följer "+s.feedName+" · pausad":"Följer "+s.feedName;
    }
    /** ATAK connection callback: resumes when the stored profile became connected, pauses when it went away. */
    public void connectionChanged(){
        host.post(()->{
            if(closed||!following())return;String reason=pauseReason();
            if(reason!=null){if(running)stopAttempt();pause(reason);reschedule();}
            else if(paused!=null&&!running)trigger();
        });
    }
    /** Uppdatera nu. */
    public void refresh(){if(!closed&&!running)trigger();}

    private boolean following(){FeedSyncState.Snapshot s=state;return loaded&&broken==null&&s!=null&&s.following;}
    private void trigger(){
        handler.removeCallbacks(tick);
        if(closed||!following()||running)return;
        String reason=pauseReason();
        if(reason!=null){pause(reason);reschedule();return;}
        paused=null;FeedSyncState.Snapshot s=state;MissionEndpoint endpoint=endpoint(s);Transport transport;
        try{transport=host.connect(endpoint);}catch(RuntimeException unavailable){pause("Den sparade källan kunde inte öppnas.");reschedule();return;}
        long token=generation.incrementAndGet();running=true;status="Hämtar …";
        try{network.execute(()->fetch(token,s,transport));}catch(RejectedExecutionException stopping){running=false;}
    }
    private void reschedule(){handler.removeCallbacks(tick);if(!closed&&following())handler.postDelayed(tick,delay);}
    private void pause(String reason){
        if(!reason.equals(paused))host.notice("TMM-följning pausad: "+reason);
        paused=reason;status="Pausad: "+reason+" Ingen annan källa används.";
    }
    private String pauseReason(){
        FeedSyncState.Snapshot s=state;
        if(!host.available())return "TMM är inte aktivt.";
        if(!host.defaultProvider())return "ATAK:s kommunikationsleverantör har ändrats.";
        if(endpoint(s)==null)return "Den sparade källan stöds inte längre.";
        try{
            if(host.profiles().contains(s.streamConnect))return null;
            return host.configured(s.streamConnect)?"Den sparade profilen är inte ansluten.":"Den sparade profilen saknas eller har ändrat anslutning.";
        }catch(RuntimeException unavailable){return "ATAK:s serverlista kunde inte läsas.";}
    }
    private static MissionEndpoint endpoint(FeedSyncState.Snapshot s){
        try{
            String rest=s.contextId.substring("mission-api-v1|".length());String service=rest.substring(0,rest.lastIndexOf('|'));
            MissionEndpoint endpoint=MissionEndpoint.fromProfile(s.streamConnect,URI.create(service).getPort());
            return endpoint.base.equals(service)?endpoint:null;
        }catch(RuntimeException unsupported){return null;}
    }
    private boolean connected(String stream){try{return !closed&&host.available()&&host.profiles().contains(stream);}catch(RuntimeException unavailable){return false;}}

    private void fetch(long token,FeedSyncState.Snapshot s,Transport transport){
        BooleanSupplier ok=()->!closed&&generation.get()==token&&connected(s.streamConnect);
        try{
            Feed feed=null;
            for(Feed f:read(false,ok,transport::list))if(f.context().equals(s.contextId)){if(feed!=null)throw new IOException("Ambiguous feed");feed=f;}
            if(feed==null)throw new IOException("Followed feed not listed");Feed target=feed;
            MissionSnapshotInput.Snapshot snapshot=read(false,ok,a->transport.fetch(target,a));
            if(!snapshot.feed.context().equals(s.contextId)||!snapshot.feed.name.equals(target.name))throw new IOException("Snapshot target changed");
            List<UUID> receipts=onWorker(ok,()->{
                if(!inbox.load().inboxId.equals(s.inboxId))throw new IOException("Inbox does not belong to this sync state");
                Map<UUID,TmmDraft> observations=new HashMap<>();for(TmmDraft draft:local.load().activeDrafts())observations.put(draft.uid,draft);
                require(ok);List<UUID> ids=new ArrayList<>();
                for(InboxStore.Receipt r:inbox.receiveBatch(s.contextId,snapshot.metadata,observations,Instant.now()))ids.add(r.id);
                return ids;
            });
            List<Resource> resources=read(false,ok,a->transport.images(target,a));
            Map<String,List<ImageAssociationRegistry.Record>> staged=onWorker(ok,()->staged(s.inboxId));
            List<ImageAssociationRegistry.Record> records=new ArrayList<>();
            for(Resource resource:resources){
                List<ImageAssociationRegistry.Record> known=staged.get(resource.hash);
                if(known!=null){records.addAll(known);continue;}
                ReceivedImagePayload payload=read(true,ok,a->transport.image(target,resource,a));
                records.add(onWorker(ok,()->new ReceivedImageIntake(inbox,registry,stage,local).receive(s.contextId,payload,Instant.now(),ok)));
            }
            FeedSyncState.Snapshot applied;
            try{applied=onWorker(ok,()->apply.apply(s,receipts,records,ok));}catch(Exception|OutOfMemoryError notApplied){applied=null;}
            FeedSyncState.Snapshot written=applied;
            host.post(()->finished(token,receipts,records,written,false));
        }catch(Exception|OutOfMemoryError failed){host.post(()->finished(token,null,null,null,true));}
    }
    /** Registry-referenced stage entries by the SHA-256 of their container v1 bytes, which is the feed resource hash. */
    private Map<String,List<ImageAssociationRegistry.Record>> staged(UUID inboxId)throws IOException {
        ImageAssociationRegistry.Snapshot saved=registry.load();if(!saved.inboxId.equals(inboxId))throw new IOException("Image history belongs to another inbox");
        Map<String,List<ImageAssociationRegistry.Record>> result=new HashMap<>();
        for(ImageAssociationRegistry.Record r:saved.records){
            String hash=containerHashes.get(r.stageLocalId);
            if(hash==null){hash=containerHash(stage.read(r.stageLocalId));containerHashes.put(r.stageLocalId,hash);}
            result.computeIfAbsent(hash,h->new ArrayList<>()).add(r);
        }
        return result;
    }
    static String containerHash(ReceivedImagePayload payload)throws IOException {
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException unavailable){throw new IOException(unavailable);}
        ImageContainerCodec.write(new DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int off,int len){}},digest),payload.offer,payload.image);
        StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private <T>T read(boolean image,BooleanSupplier ok,Network<T> operation)throws Exception {
        require(ok);MissionReadAttempt attempt=image?MissionReadAttempt.forImage(System.nanoTime()):new MissionReadAttempt(System.nanoTime());
        pending=attempt;Runnable deadline=attempt::cancel;handler.postDelayed(deadline,image?60_000:12_000);
        try{T result=operation.run(attempt);if(!attempt.active(System.nanoTime()))throw new IOException("Read deadline");require(ok);return result;}
        finally{handler.removeCallbacks(deadline);if(pending==attempt)pending=null;}
    }
    /** Store work stays on the planner's serialized worker; the fetch thread waits for it. */
    private <T>T onWorker(BooleanSupplier ok,Callable<T> work)throws Exception {
        FutureTask<T> task=new FutureTask<>(()->{require(ok);return work.call();});worker.execute(task);
        try{return task.get();}catch(ExecutionException failed){throw failed.getCause() instanceof Exception?(Exception)failed.getCause():failed;}
    }
    private static void require(BooleanSupplier ok)throws IOException {if(Thread.currentThread().isInterrupted()||!ok.getAsBoolean())throw new IOException("Feed fetch canceled");}
    private void finished(long token,List<UUID> receipts,List<ImageAssociationRegistry.Record> records,FeedSyncState.Snapshot written,boolean failed){
        if(closed||generation.get()!=token)return;
        running=false;pending=null;
        if(!failed){
            delay=INTERVAL;paused=null;if(written!=null)state=written;
            status="Senaste hämtning lyckades: "+receipts.size()+" metadataposter, "+records.size()+" bilder. "
                    +(written==null?"Lokala uppdateringar kunde inte tillämpas; nytt försök vid nästa hämtning."
                    :apply.applied()+" enheter uppdaterade lokalt, "+apply.conflicts().size()+" konflikter (din version behålls).");
            host.onFetchCompleted(Collections.unmodifiableList(receipts),Collections.unmodifiableList(records));
        }else{
            String reason=following()?pauseReason():null;
            if(reason!=null)pause(reason);
            else{delay=Math.min(delay*2,MAX_INTERVAL);status="Hämtningen misslyckades. Nytt försök om "+delay/1000+" s. Mottagen historik behålls.";}
        }
        reschedule();
    }
    private void stopAttempt(){generation.incrementAndGet();running=false;MissionReadAttempt a=pending;pending=null;if(a!=null)a.cancel();}

    public void show(){
        if(closed||isBusy()||!host.available()||!loaded)return;
        if(broken!=null){info("Följning stoppad",broken);return;}
        FeedSyncState.Snapshot s=state;
        if(s==null){sourceChoice.show();return;}
        String text="Feed: "+s.feedName+"\nKälla: "+s.contextId.substring("mission-api-v1|".length())+"\nProfil: "+s.streamConnect+"\n\n"
                +(s.following?status:"Följningen är avstängd. Mottagen historik finns kvar.")
                +"\n\nEndast läsning (GET). Inget publiceras, laddas upp eller kvitteras. Mottaget sparas i inkorgen och bildhistoriken. Konfliktfria uppdateringar tillämpas på lokala enheter och bilder; vid konflikt behålls din version.";
        AlertDialog.Builder b=new AlertDialog.Builder(context).setTitle(s.following?"Följer feed":"Följning avstängd").setMessage(text)
                .setNegativeButton("Stäng",(d,w)->{if(dialog==d)dismiss();});
        if(s.following)b.setPositiveButton("Uppdatera nu",(d,w)->{if(dialog==d){dismiss();refresh();}}).setNeutralButton("Sluta följa",(d,w)->{if(dialog==d)setFollowing(false);});
        else b.setPositiveButton("Följ igen",(d,w)->{if(dialog==d)setFollowing(true);});
        replace(b.create());
    }
    /** Sluta följa / Följ igen: rewrites only the flag; every record and the source are kept. */
    private void setFollowing(boolean on){
        FeedSyncState.Snapshot s=state;if(s==null||busy)return;busy=true;dismiss();
        if(!on){stopAttempt();handler.removeCallbacks(tick);}
        FeedSyncState.Snapshot next=new FeedSyncState.Snapshot(s.syncId,s.inboxId,on,s.contextId,s.feedName,s.streamConnect,s.records);
        worker.execute(()->{
            FeedSyncState.Snapshot saved=null;
            try{if(!closed){sync.replace(s,next);saved=sync.read();}}catch(Exception uncertain){saved=null;}
            FeedSyncState.Snapshot result=saved;
            host.post(()->{
                busy=false;if(closed)return;
                if(result==null){info("Ändringen är obekräftad","Följningsfilen kan redan vara ändrad. Öppna Följ feed igen för att läsa aktuellt läge.");loaded=false;start();return;}
                state=result;if(result.following){paused=null;delay=INTERVAL;status="Ingen hämtning ännu.";trigger();}
                else status="Följningen är avstängd.";
                show();
            });
        });
    }
    private void listFeeds(MissionEndpoint endpoint){
        if(closed||state!=null||!connected(endpoint.stream))throw new IllegalStateException();
        Transport transport=host.connect(endpoint);long token=setup.incrementAndGet();
        BooleanSupplier ok=()->!closed&&setup.get()==token&&connected(endpoint.stream);AlertDialog shown=progress("Läser feeds");
        network.execute(()->{
            List<Feed> feeds;
            try{feeds=read(false,ok,transport::list);}catch(Exception|OutOfMemoryError failed){feeds=null;}
            List<Feed> result=feeds;
            host.post(()->{
                if(closed||dialog!=shown)return;
                if(result!=null&&ok.getAsBoolean())chooseFeed(endpoint,result,ok);
                else info("Feeds kunde inte läsas","Kontrollera anslutning och behörighet. Ingen följning har sparats och ingen annan källa används.");
            });
        });
    }
    private void chooseFeed(MissionEndpoint endpoint,List<Feed> feeds,BooleanSupplier ok){
        if(feeds.isEmpty()){info("Inga feeds","Servern gav inga tillgängliga feeds. Ingen följning har sparats.");return;}
        String[] labels=new String[feeds.size()];for(int n=0;n<labels.length;n++)labels[n]=feeds.get(n).name+"\n"+feeds.get(n).guid;
        replace(new AlertDialog.Builder(context).setTitle("Välj feed att följa").setItems(labels,(d,n)->{
            if(dialog!=d||!ok.getAsBoolean())return;Feed feed=feeds.get(n);
            replace(new AlertDialog.Builder(context).setTitle("Följ feed?")
                    .setMessage("Källa: "+feed.service+"\nProfil: "+endpoint.stream+"\nFeed: "+feed.name+"\nGUID: "+feed.guid
                            +"\n\nValet sparas och återupptas efter omstart. Hämtning sker vid start, vid anslutning, var 60:e sekund och vid Uppdatera nu. "
                            +"Endast läsning; inget publiceras, laddas upp eller kvitteras. Inga nycklar eller lösenord sparas. Konfliktfria uppdateringar tillämpas på lokala enheter och bilder; vid konflikt behålls din version.")
                    .setNegativeButton("Avbryt",(x,w)->{if(dialog==x)cancel();})
                    .setPositiveButton("Följ feed",(x,w)->{if(dialog==x&&ok.getAsBoolean())ensureHistory(endpoint,feed,ok);}).create());
        }).setNegativeButton("Avbryt",(d,w)->{if(dialog==d)cancel();}).create());
    }
    /** Same explicit missing-history decision as the image trial; corrupt or mismatched history is never reset. */
    private void ensureHistory(MissionEndpoint endpoint,Feed feed,BooleanSupplier ok){
        local(ok,"Kontrollerar mottagningshistorik",()->{
            local.load();boolean box=Files.notExists(inboxFile,LinkOption.NOFOLLOW_LINKS),reg=Files.notExists(registryFile,LinkOption.NOFOLLOW_LINKS),missingStage=Files.notExists(stageRoot,LinkOption.NOFOLLOW_LINKS);
            InboxStore.Snapshot i=box?null:inbox.load();ImageAssociationRegistry.Snapshot r=reg?null:registry.load();
            if(r!=null&&(i==null||!r.inboxId.equals(i.inboxId)||missingStage&&!r.records.isEmpty()))throw new IOException();
            if(!missingStage)stage.inspect();return new boolean[]{box,reg,missingStage};
        },missing->{
            if(!missing[0]&&!missing[1]&&!missing[2]){create(endpoint,feed,ok);return;}
            replace(new AlertDialog.Builder(context).setTitle("Starta mottagen bildhistorik?")
                    .setMessage("Läsbar historik saknas helt eller delvis. Det kan vara första start eller förlorad historik. Starta bara om du uttryckligen vill börja utan den saknade historiken. Befintliga filer ersätts inte och lokala enheter/bilder ändras inte.")
                    .setNegativeButton("Avbryt",(d,w)->{if(dialog==d)cancel();}).setPositiveButton("Starta saknad historik",(d,w)->{if(dialog!=d||!ok.getAsBoolean())return;local(ok,"Startar mottagningshistorik",()->{
                        if(missing[0])inbox.initializeNew();require(ok);if(missing[2])stage.initializeNew();require(ok);if(missing[1])registry.initializeNew(inbox.load().inboxId);return true;
                    },ignored->create(endpoint,feed,ok));}).create());
        });
    }
    private void create(MissionEndpoint endpoint,Feed feed,BooleanSupplier ok){
        local(ok,"Sparar följning",()->{
            // contextId comes from the approved source selection; streamConnect is used only for the SDK credential lookup.
            sync.create(new FeedSyncState.Snapshot(UUID.randomUUID(),inbox.load().inboxId,true,feed.context(),feed.name,endpoint.stream,Collections.emptyList()));
            return sync.read();
        },saved->{dismiss();state=saved;paused=null;delay=INTERVAL;status="Ingen hämtning ännu.";trigger();show();});
    }
    private interface Work<T>{T run()throws Exception;}
    private <T>void local(BooleanSupplier ok,String title,Work<T> work,java.util.function.Consumer<T> done){
        AlertDialog shown=progress(title);
        worker.execute(()->{
            T value;try{require(ok);value=work.run();}catch(Exception|OutOfMemoryError failed){value=null;}
            T result=value;
            host.post(()->{
                if(closed||dialog!=shown)return;
                if(result!=null&&ok.getAsBoolean())done.accept(result);
                else info("Följningen kunde inte sparas","Kontrollera anslutning, lagring och historik. Befintliga filer återställs inte automatiskt; en del kan redan vara skapad. Öppna Följ feed igen.");
            });
        });
    }
    private AlertDialog progress(String title){replace(new AlertDialog.Builder(context).setTitle(title).setMessage("Inget publiceras.").setNegativeButton("Avbryt",(d,w)->{if(dialog==d)cancel();}).create());return dialog;}
    private void info(String title,String message){if(closed)return;replace(new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("Stäng",(d,w)->{if(dialog==d)dismiss();}).create());}
    private void replace(AlertDialog next){dismiss();dialog=next;next.setCanceledOnTouchOutside(false);next.setOnCancelListener(d->{if(dialog==d)cancel();});next.show();}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    /** Cancels the explicit setup dialogs only; following and its timer are unaffected. */
    public void cancel(){setup.incrementAndGet();sourceChoice.cancel();dismiss();}
    /** Unload: stop the timer and any attempt; later callbacks are rejected by generation and the closed flag. */
    public void close(){closed=true;conflictUi.close();cancel();stopAttempt();handler.removeCallbacksAndMessages(null);network.shutdownNow();sourceChoice.close();}
}
