package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.plugin.ingress.*;
import se.tmm.sensorplanner.plugin.ingress.MissionSnapshotInput.Feed;
import se.tmm.sensorplanner.plugin.ingress.MissionSnapshotInput.Snapshot;

/** Explicit snapshot intake and existing review/commit composition; no publisher or image writer. */
@TargetApi(26)
public final class SnapshotInboxUi {
    public interface Transport {
        default String description(){return MissionEndpoint.LAB_BASE;}
        List<Feed> list(MissionReadAttempt attempt)throws Exception;
        Snapshot fetch(Feed feed,MissionReadAttempt attempt)throws Exception;
    }
    public interface Host {
        void post(Runnable action);
        boolean available();
        boolean sourceAvailable();
        void changed();
    }
    private final Context context;private final Host host;private final Executor worker;private final Transport transport;
    private final LocalV2Store local;private final InboxStore inbox;private final Path file;
    private final ImportCommitUi commits;private final ImportDecisionUi decisions;private final InboxReviewUi browser;
    private final AtomicLong generation=new AtomicLong();private final AtomicBoolean guardQueued=new AtomicBoolean();
    private final ThreadPoolExecutor network=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"TMM-snapshot-read");t.setDaemon(true);return t;});
    private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"TMM-snapshot-guard");t.setDaemon(true);return t;});
    private volatile boolean closed;private volatile long localTask=-1;private volatile MissionReadAttempt pending;private volatile Grant grant;
    private AlertDialog dialog;
    private static final class Grant {
        final UUID inboxId;final Snapshot snapshot;
        Grant(UUID inboxId,Snapshot snapshot){this.inboxId=inboxId;this.snapshot=snapshot;}
    }
    public SnapshotInboxUi(Context context,LocalV2Store local,Path directory,Executor worker,Host host,
                           Transport transport,Function<Position,String> coordinates){
        this.context=context;this.local=local;this.inbox=new InboxStore(directory);this.file=directory.resolve("inbox.bin");
        this.worker=worker;this.host=host;this.transport=transport;timer.setRemoveOnCancelPolicy(true);
        commits=new ImportCommitUi(context,worker,new ImportCommitUi.Host(){
            public void post(Runnable r){host.post(r);}public boolean available(){return SnapshotInboxUi.this.available();}
            public boolean sourceAvailable(UUID id,InboxStore.Receipt receipt){return trusted(id,receipt);}
            public void changed(UUID id){host.changed();}
        },local,inbox);
        decisions=new ImportDecisionUi(context,worker,new ImportDecisionUi.Host(){
            public void post(Runnable r){host.post(r);}public boolean available(){return SnapshotInboxUi.this.available()&&grant!=null&&source();}
            public ImportDecisionUi.Sources load()throws Exception{return new ImportDecisionUi.Sources(inbox.load(),local.load());}
            public Instant editTime(){return Instant.now();}
        },coordinates,commits);
        browser=new InboxReviewUi(context,coordinates,decisions);
        timer.scheduleAtFixedRate(()->{
            if(closed||(pending==null&&grant==null)||!guardQueued.compareAndSet(false,true))return;
            host.post(()->{try{if(!closed&&!source()){cancel();info("Källåtkomst avbruten","Den valda serveråtkomsten ändrades. Hämta och granska på nytt. Sparad historik behålls.");}}finally{guardQueued.set(false);}});
        },250,250,TimeUnit.MILLISECONDS);
    }
    private boolean available(){return !closed&&host.available();}
    private boolean source(){return available()&&host.sourceAvailable();}
    private boolean trusted(UUID id,InboxStore.Receipt receipt){
        Grant current=grant;return current!=null&&source()&&current.inboxId.equals(id)&&current.snapshot.feed.context().equals(receipt.contextId)&&current.snapshot.members.contains(receipt.incoming.uid.toString());
    }
    public boolean isBusy(){return pending!=null||localTask>=0||decisions.isBusy();}
    public void fetch(){
        if(isBusy()||!available())return;
        if(!source()){info("Ingen verifierad serveråtkomst","Välj en ansluten TLS-server och granska på nytt. Ingen annan källa används som reserv.");return;}
        long token=generation.incrementAndGet();localTask=token;progress("Kontrollerar inkorgen");
        worker.execute(()->{
            try{
                try{local.load();}catch(Exception localUnavailable){post(token,()->info("Lokala lagringen kunde inte läsas","En giltig lokal v2-lagring krävs före mottagning. Inkorgen startas inte som ersättning för saknade utkast."));return;}
                inbox.load();post(token,this::listFeeds);
            }
            catch(Exception unavailable){
                boolean missing=Files.notExists(file,LinkOption.NOFOLLOW_LINKS);
                post(token,()->{if(missing)initializeReview();else info("Inkorgen kunde inte läsas","Befintlig eller okänd historik ersätts inte. Kontrollera lagringen innan ett nytt försök.");});
            }finally{if(localTask==token)localTask=-1;}
        });
    }
    private void initializeReview(){
        replace(new AlertDialog.Builder(context).setTitle("Starta mottagningshistorik?")
                .setMessage("En läsbar inkorg saknas. Det kan vara en första start eller förlorad historik. Starta bara om du uttryckligen vill börja utan tidigare mottagningar. Lokala enheter och bilder ändras inte.")
                .setNegativeButton("Avbryt",(d,w)->cancel()).setPositiveButton("Starta ny inkorg",(d,w)->initialize()).create());
    }
    private void initialize(){
        if(!source())return;long token=generation.incrementAndGet();localTask=token;progress("Startar inkorgen");
        worker.execute(()->{try{if(!valid(token)||!source())return;local.load();inbox.initializeNew();post(token,this::listFeeds);}
            catch(Exception unknown){post(token,()->info("Starten är obekräftad","Inkorgen kan redan vara skapad. Läs den igen; inget återställs eller skrivs över automatiskt."));}
            finally{if(localTask==token)localTask=-1;}});
    }
    private void listFeeds(){read(null,feeds->{
        if(feeds.isEmpty()){info("Inga feeds","Servern gav inga tillgängliga feeds. Ingenting har raderats lokalt.");return;}
        String[] labels=new String[feeds.size()];for(int n=0;n<labels.length;n++)labels[n]=feeds.get(n).name+"\n"+feeds.get(n).guid;
        replace(new AlertDialog.Builder(context).setTitle("Välj feed")
                .setItems(labels,(d,n)->review(feeds.get(n))).setNegativeButton("Avbryt",(d,w)->cancel()).create());
    },null);}
    private void review(Feed feed){
        replace(new AlertDialog.Builder(context).setTitle("Hämta metadata till inkorgen?")
                .setMessage("Källa: "+transport.description()+"\nFeed: "+feed.name+"\nGUID: "+feed.guid
                        +"\nEndast aktuell snapshot: högst 64 poster och 64 KiB per svar. Mellanliggande ändringar kan saknas. "
                        +"Observerade varianter sparas för granskning. Inga lokala utkast ändras och frånvaro raderar ingenting. Bilder hämtas inte. Ingen publicering eller kvittens skickas.")
                .setNegativeButton("Avbryt",(d,w)->cancel()).setPositiveButton("Hämta till inkorgen",(d,w)->read(feed,null,this::stage)).create());
    }
    private void read(Feed feed,Consumer<List<Feed>> listed,Consumer<Snapshot> fetched){
        if(!source()||pending!=null)return;grant=null;long token=generation.incrementAndGet();
        MissionReadAttempt attempt=new MissionReadAttempt(System.nanoTime());pending=attempt;progress(feed==null?"Läser feeds":"Hämtar metadatasnapshot");
        try{
            attempt.bindDeadline(timer.schedule(()->{attempt.cancel();post(token,()->{cancel();info("Tidsgränsen nåddes","Inget nätverksresultat godtas. Ingen snapshot sparas av detta försök.");});},12,TimeUnit.SECONDS));
            attempt.bindWorker(network.submit(()->{
                try{
                    Object result=feed==null?transport.list(attempt):transport.fetch(feed,attempt);
                    if(feed!=null&&(!feed.context().equals(((Snapshot)result).feed.context())||!feed.name.equals(((Snapshot)result).feed.name)))throw new IllegalArgumentException("Snapshot target changed");
                    post(token,()->{
                        if(pending!=attempt||!attempt.active(System.nanoTime())||!source())return;
                        pending=null;attempt.clearTimers();
                        if(feed==null)listed.accept(castFeeds(result));else fetched.accept((Snapshot)result);
                    });
                }catch(Exception unavailable){post(token,()->{if(pending==attempt){pending=null;attempt.cancel();info("Hämtningen kunde inte verifieras","Ingen snapshot har sparats av detta nätverksförsök. Kontrollera anslutning, behörighet och feed. Befintlig inkorg behålls.");}});}
            }));
        }catch(RejectedExecutionException busy){pending=null;attempt.cancel();info("Föregående läsning avslutas","Starta ett nytt prov senare. Inget köas eller återförsöks automatiskt.");}
    }
    @SuppressWarnings("unchecked") private static List<Feed> castFeeds(Object value){return (List<Feed>)value;}
    private void stage(Snapshot snapshot){
        if(!source())return;long token=generation.incrementAndGet();localTask=token;progress("Sparar mottagna metadata");
        worker.execute(()->{
            try{
                if(!valid(token)||!source())return;LocalV2.Snapshot current=local.load();Map<UUID,TmmDraft> observations=new HashMap<>();
                for(TmmDraft draft:current.activeDrafts())observations.put(draft.uid,draft);
                if(!valid(token)||!source())return;
                inbox.receiveBatch(snapshot.feed.context(),snapshot.metadata,observations,Instant.now());
                InboxStore.Snapshot saved=inbox.load();
                post(token,()->{
                    if(!source())return;grant=new Grant(saved.inboxId,snapshot);
                    replace(new AlertDialog.Builder(context).setTitle("Snapshot sparad i inkorgen")
                            .setMessage(snapshot.metadata.size()+" TMM-poster kontrollerade. Tidigare varianter behålls; upprepade metadata skapar ingen ny mottagning. "
                                    +"Lokala utkast och bilder har inte ändrats. Granska innan du väljer vad som ska appliceras.")
                            .setNegativeButton("Stäng",(d,w)->dismiss()).setPositiveButton("Granska mottagna",(d,w)->show()).create());
                });
            }catch(Exception uncertain){grant=null;post(token,()->info("Sparandet är obekräftat","Hela den validerade uppsättningen kan redan vara sparad. Öppna inkorgen för kontroll. En ny uttrycklig hämtning deduplicerar redan sparade metadata; inget återställs automatiskt."));}
            finally{if(localTask==token)localTask=-1;}
        });
    }
    public void show(){
        if(isBusy()||!available())return;long token=generation.incrementAndGet();localTask=token;progress("Läser mottagningshistorik");
        worker.execute(()->{
            try{InboxStore.Snapshot saved=inbox.load();LocalV2.Snapshot current=local.load();InboxReview review=InboxReview.assess(saved,current.activeDrafts());InboxDecisionCoverage coverage=InboxDecisionCoverage.assess(saved,current);
                post(token,()->{dismiss();browser.show(review,coverage);});
            }catch(Exception unavailable){post(token,()->info("Inkorgen kunde inte läsas","En läsbar, uttryckligen startad inkorg och lokal v2-lagring krävs. Ingen tom historik skapas automatiskt."));}
            finally{if(localTask==token)localTask=-1;}
        });
    }
    private boolean valid(long token){return available()&&generation.get()==token;}
    private void post(long token,Runnable action){host.post(()->{if(valid(token))action.run();});}
    private void progress(String title){replace(new AlertDialog.Builder(context).setTitle(title).setMessage("Inga utkast appliceras i detta steg.").setNegativeButton("Avbryt",(d,w)->cancel()).create());}
    private void info(String title,String text){if(!closed)replace(new AlertDialog.Builder(context).setTitle(title).setMessage(text).setPositiveButton("Stäng",(d,w)->dismiss()).create());}
    private void replace(AlertDialog next){dismiss();dialog=next;next.setCanceledOnTouchOutside(false);next.setOnCancelListener(d->cancel());next.show();}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    public void cancel(){generation.incrementAndGet();grant=null;MissionReadAttempt old=pending;pending=null;if(old!=null)old.cancel();dismiss();browser.cancel();decisions.cancel();}
    public void close(){closed=true;cancel();browser.close();decisions.close();commits.close();network.shutdownNow();timer.shutdownNow();}
}
