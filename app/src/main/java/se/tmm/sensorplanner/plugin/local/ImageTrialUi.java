package se.tmm.sensorplanner.plugin.local;

import android.annotation.TargetApi;
import android.app.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.*;
import se.tmm.sensorplanner.plugin.images.*;
import se.tmm.sensorplanner.plugin.ingress.*;
import se.tmm.sensorplanner.plugin.ingress.MissionSnapshotInput.Feed;
import se.tmm.sensorplanner.plugin.ingress.MissionImageInput.Resource;

/** Owner-approved synthetic image trial: explicit export/read, separate verified viewing and existing metadata review. */
@TargetApi(26)
public final class ImageTrialUi {
    public interface Transport {
        String description();
        List<Feed> list(MissionReadAttempt attempt)throws Exception;
        List<Resource> images(Feed feed,MissionReadAttempt attempt)throws Exception;
        ReceivedImagePayload image(Feed feed,Resource resource,MissionReadAttempt attempt)throws Exception;
    }
    public interface Host {
        void post(Runnable action);boolean available();List<String> profiles();
        List<ConfiguredPublicationTrial.Stream> streams();boolean defaultProvider();
        Transport connect(MissionEndpoint endpoint);String summary(ShareReview.Metadata metadata);
        String coordinates(Position position);void changed(UUID uid);
    }
    private interface Work<T>{T run(long token)throws Exception;}
    private interface Network<T>{T run(MissionReadAttempt attempt)throws Exception;}
    private final Context context;private final Host host;private final Executor worker;
    private final LocalV2Store local;private final LocalImages images;private final InboxStore inbox;
    private final ImageStageStore stage;private final ImageAssociationRegistry registry;private final ImageSnapshotExports exports;
    private final Path inboxFile,registryFile,stageRoot;
    private final ConfiguredPublicationTrial trial=new ConfiguredPublicationTrial();
    private final MissionSourceChoiceUi sourceChoice;private final ServerTrialConsentUi consent;
    private final ImportDecisionUi decisions;private final ImportCommitUi commits;
    private final AtomicLong generation=new AtomicLong();private final AtomicBoolean guardQueued=new AtomicBoolean();
    private final ThreadPoolExecutor network=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"TMM-image-read");t.setDaemon(true);return t;});
    private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"TMM-image-guard");t.setDaemon(true);return t;});
    private volatile boolean closed,busy,writing;private volatile MissionReadAttempt pending;
    private volatile ReceivedImageReview grantedReview;private volatile ReceivedImageReview.Item grantedItem;
    private AlertDialog dialog;private Dialog viewer;private Preview preview;private volatile ImagePublicationReview pendingReview;
    private volatile MissionEndpoint endpoint;private volatile Transport transport;private UUID target;private boolean sending;

    public ImageTrialUi(Context context,LocalV2Store local,LocalImages images,Path privateRoot,Path exportRoot,
                         Executor worker,Host host) {
        this.context=context;this.local=local;this.images=images;this.worker=worker;this.host=host;
        inboxFile=privateRoot.resolve("metadata-inbox-v1/inbox.bin");registryFile=privateRoot.resolve("received-image-association-v1/associations.bin");stageRoot=privateRoot.resolve("received-image-stage-v1");
        inbox=new InboxStore(inboxFile.getParent());registry=new ImageAssociationRegistry(registryFile.getParent());stage=new ImageStageStore(stageRoot);exports=new ImageSnapshotExports(exportRoot);
        commits=new ImportCommitUi(context,worker,new ImportCommitUi.Host(){
            public void post(Runnable r){host.post(r);}public boolean available(){return ImageTrialUi.this.available();}
            public boolean sourceAvailable(UUID id,InboxStore.Receipt receipt){ReceivedImageReview g=grantedReview;ReceivedImageReview.Item item=grantedItem;return source()&&g!=null&&item!=null&&g.inboxId.equals(id)&&item.matchesReceipt(receipt);}
            public void changed(UUID uid){host.changed(uid);}
        },local,inbox);
        decisions=new ImportDecisionUi(context,worker,new ImportDecisionUi.Host(){
            public void post(Runnable r){host.post(r);}public boolean available(){return ImageTrialUi.this.available()&&source()&&grantedItem!=null;}
            public ImportDecisionUi.Sources load()throws Exception{return new ImportDecisionUi.Sources(inbox.load(),local.load());}
            public Instant editTime(){return Instant.now();}
        },host::coordinates,commits);
        consent=new ServerTrialConsentUi(context,new ServerTrialConsentUi.Host(){
            public boolean available(){return ImageTrialUi.this.available();}public List<String> profiles(){return host.profiles();}
            public void confirmed(String stream,boolean[] statements){
                try{if(endpoint==null||!endpoint.stream.equals(stream))throw new IOException();trial.begin(stream,statements);if(!source())throw new IOException();transport=host.connect(endpoint);listFeeds();}
                catch(Exception unavailable){cancel();info("Bildprovet kunde inte starta","Exakt en vald och ansluten TLS-server krävs. Ingen annan källa väljs automatiskt.");}
            }
        },"Endast syntetiskt bildprov. TMM förbereder en lokal bildfil för manuell publicering och hämtar bara efter uttryckligt val. Native feedval och sista Publish kan inte verifieras av TMM. Senare ändringar följer inte med en export.");
        sourceChoice=new MissionSourceChoiceUi(context,new MissionSourceChoiceUi.Host(){
            public boolean available(){return ImageTrialUi.this.available();}public List<String> profiles(){return host.profiles();}
            public void selected(MissionEndpoint value){endpoint=value;consent.showFor(value.stream);}
        },"Syntetiskt bildprov med separat granskning. Ingen bild hämtas eller exporteras nu. Utkast publiceras inte automatiskt. ");
        timer.setRemoveOnCancelPolicy(true);
        timer.scheduleAtFixedRate(()->{
            if(closed||!trial.live()||!guardQueued.compareAndSet(false,true))return;
            host.post(()->{try{if(!closed&&trial.live()&&!source())sourceLost();}finally{guardQueued.set(false);}});
        },250,250,TimeUnit.MILLISECONDS);
    }
    public boolean isBusy(){return busy||dialog!=null&&dialog.isShowing()||viewer!=null||sourceChoice.isBusy()||consent.isBusy()||decisions.isBusy();}
    private boolean available(){return !closed&&host.available();}
    private boolean source(){return available()&&trial.allows(host.streams(),host.defaultProvider());}
    private boolean valid(long token){return available()&&generation.get()==token;}
    private void require(long token,boolean source)throws IOException{if(!valid(token)||source&&!source())throw new IOException();}

    public void show(UUID uid){
        if(isBusy()||!available())return;target=uid;
        String[] choices=uid==null?new String[]{"Hämta bilder från feed","Alla mottagna bilder"}
                :new String[]{"Förbered bild för Data Sync","Hämta bilder från feed","Mottagna bilder för enheten"};
        replace(new AlertDialog.Builder(context).setTitle("Bilddelning · syntetiskt prov").setItems(choices,(d,n)->{
            if(dialog!=d||!available())return;
            if(uid==null){if(n==0)requestSource(false);else received(null);}
            else if(n==0)requestSource(true);else if(n==1)requestSource(false);else received(uid);
        }).setNegativeButton("Stäng",(d,w)->cancel()).create());
    }
    private void requestSource(boolean sending){cancel();this.sending=sending;sourceChoice.show();}
    private void listFeeds(){read("Läser feeds",a->transport.list(a),feeds->{
        if(feeds.isEmpty()){info("Inga feeds","Servern gav inga tillgängliga feeds. Ingen annan källa används.");return;}
        String[] labels=new String[feeds.size()];for(int n=0;n<labels.length;n++)labels[n]=feeds.get(n).name+"\n"+feeds.get(n).guid;
        replace(new AlertDialog.Builder(context).setTitle("Välj testfeed").setItems(labels,(d,n)->{
            if(dialog!=d||!source())return;
            if(sending)prepareExport(feeds.get(n));else resources(feeds.get(n));
        }).setNegativeButton("Avbryt",(d,w)->cancel()).create());
    });}
    private static final class Preview implements AutoCloseable {
        final ImagePublicationReview review;final Bitmap bitmap;
        Preview(ImagePublicationReview review)throws IOException{this.review=review;bitmap=LocalImageBitmaps.preview(review.preview());}
        public void close(){review.close();if(!bitmap.isRecycled())bitmap.recycle();}
    }
    private String destination(Feed feed){return feed.service+" · "+feed.name+" · "+feed.guid;}
    private void prepareExport(Feed feed){
        work("Förbereder granskad bild",true,token->{
            ImagePublicationReview review=new ImagePublicationReview(local.load().select(target),feed.context(),destination(feed),images);
            try{return new Preview(review);}catch(Exception|OutOfMemoryError failed){review.close();throw failed;}
        },p->{
            preview=p;pendingReview=p.review;LinearLayout body=column();body.addView(text(host.summary(p.review.offer.metadata)+"\n\nAvsedd feed: "+destination(feed)
                    +"\n\nEn fryst bild och metadata sparas i Hämtningar/TMM-Sensor-Planner, utanför privat lagring, tills du raderar filen. Högst 16 exporter/256 MiB. "
                    +"Välj sedan filen manuellt i Data Sync: Add Items → File Select → OK → Publish. TMM kan inte verifiera sista destinationen, leverans eller senare ändringar. Privat bild ersätts inte."));
            ImageView image=new ImageView(context);image.setImageBitmap(p.bitmap);image.setAdjustViewBounds(true);image.setContentDescription("Granskad bild för export");body.addView(image);
            replace(new AlertDialog.Builder(context).setTitle("Granska bildexport – inte publicerad").setView(scroll(body))
                    .setNegativeButton("Avbryt",(d,w)->cancel()).setPositiveButton("Spara export i Hämtningar",(d,w)->{
                        if(preview!=p||!source())return;preview=null;image.setImageDrawable(null);p.bitmap.recycle();
                        confirmExport(feed,p);
                    }).create());
        },()->{},Preview::close);
    }
    private void confirmExport(Feed feed,Preview p){
        read("Kontrollerar vald feed före export",a->transport.list(a),feeds->{
            long matches=feeds.stream().filter(f->f.context().equals(feed.context())&&f.name.equals(feed.name)).count();
            if(matches!=1){p.review.close();pendingReview=null;info("Vald feed ändrades","Ingen export skapades. Välj och granska på nytt.");return;}
            work("Sparar fryst export",true,token->{
                try(ImagePublicationReview review=p.review){
                    ImagePublicationReview.Prepared prepared=review.confirm(local.load(),feed.context(),destination(feed),images);
                    require(token,true);writing=true;return exports.export(prepared,()->valid(token)&&source());
                }
            },name->info("Export förberedd – inte publicerad","Fil: Hämtningar/TMM-Sensor-Planner/"+name
                    +"\nVälj denna fil och kontrollera feed separat i Data Sync. Filen ligger kvar tills du raderar den. Exporten ändras inte när utkastet ändras. Ingen leveranskvittens finns."),()->p.review.close());
        });
    }
    private void resources(Feed feed){read("Läser bildresurser",a->transport.images(feed,a),values->{
        if(values.isEmpty()){info("Inga stödda bildpaket","Vald feed innehåller inga verifierbara .tmmi-resursanspråk. Ingenting raderas lokalt.");return;}
        String[] labels=new String[values.size()];for(int n=0;n<labels.length;n++){Resource r=values.get(n);labels[n]=r.name+"\n"+r.length+" byte · "+r.hash.substring(0,12);}
        replace(new AlertDialog.Builder(context).setTitle("Välj bildpaket i "+feed.name).setItems(labels,(d,n)->{
            if(dialog!=d||!source())return;
            Resource r=values.get(n);replace(new AlertDialog.Builder(context).setTitle("Hämta och granska bild?")
                    .setMessage(destination(feed)+"\n"+r.name+"\n"+r.length+" byte\nSHA-256: "+r.hash
                            +"\nBild och metadata sparas separat för granskning. Högst 16 bildposter/256 MiB; inga äldre poster raderas för att göra plats. Lokala utkast/bilder ändras inte och ingen kvittens skickas.")
                    .setNegativeButton("Avbryt",(x,w)->cancel()).setPositiveButton("Hämta bildpaket",(x,w)->{if(dialog==x&&source())ensureReceiving(()->download(feed,r));}).create());
        }).setNegativeButton("Avbryt",(d,w)->cancel()).create());
    });}
    private void ensureReceiving(Runnable next){
        work("Kontrollerar bildhistoriken",true,token->{
            local.load();boolean box=Files.notExists(inboxFile,LinkOption.NOFOLLOW_LINKS),reg=Files.notExists(registryFile,LinkOption.NOFOLLOW_LINKS),missingStage=Files.notExists(stageRoot,LinkOption.NOFOLLOW_LINKS);
            InboxStore.Snapshot i=box?null:inbox.load();ImageAssociationRegistry.Snapshot r=reg?null:registry.load();
            if(r!=null&&(i==null||!r.inboxId.equals(i.inboxId)||missingStage&&!r.records.isEmpty()))throw new IOException();
            if(!missingStage)stage.inspect();return new boolean[]{box,reg,missingStage};
        },missing->{
            if(!missing[0]&&!missing[1]&&!missing[2]){next.run();return;}
            replace(new AlertDialog.Builder(context).setTitle("Starta mottagen bildhistorik?")
                    .setMessage("Läsbar historik saknas helt eller delvis. Det kan vara första start eller förlorad historik. Starta bara om du uttryckligen vill börja utan den saknade historiken. Befintliga filer ersätts inte och lokala enheter/bilder ändras inte.")
                    .setNegativeButton("Avbryt",(d,w)->{if(dialog==d)cancel();}).setPositiveButton("Starta saknad historik",(d,w)->{if(dialog!=d||!source())return;work("Startar bildhistorik",true,token->{
                        writing=true;require(token,true);if(missing[0])inbox.initializeNew();require(token,true);if(missing[2])stage.initializeNew();require(token,true);if(missing[1])registry.initializeNew(inbox.load().inboxId);return true;
                    },ignored->next.run(),()->{});}).create());
        },()->{});
    }
    private void download(Feed feed,Resource resource){read("Hämtar och verifierar bildpaket",a->transport.image(feed,resource,a),payload->{
        work("Sparar mottagen bild för granskning",true,token->{
            require(token,true);writing=true;ImageAssociationRegistry.Record r=new ReceivedImageIntake(inbox,registry,stage,local).receive(feed.context(),payload,Instant.now(),()->valid(token)&&source());
            ReceivedImageReview review=ReceivedImageReview.assess(registry.load(),inbox.load(),local.load(),r.deviceUid);
            ReceivedImageReview.Item item=review.images.stream().filter(v->v.record.stageLocalId.equals(r.stageLocalId)).findFirst().orElseThrow(IOException::new);
            return new Object[]{review,item};
        },result->{
            grantedReview=(ReceivedImageReview)result[0];grantedItem=(ReceivedImageReview.Item)result[1];
            replace(new AlertDialog.Builder(context).setTitle("Bild verifierad för granskning")
                    .setMessage("Bilden hör till "+grantedItem.metadata.displayId+"\nUUID: "+grantedReview.deviceUid+"\nIngen lokal bild eller metadata har ersatts. Mottagna varianter behålls; ingen kvittens skickades.")
                    .setNegativeButton("Stäng",(d,w)->{if(dialog==d)cancel();}).setNeutralButton("Granska metadata",(d,w)->{if(dialog==d)reviewMetadata();})
                    .setPositiveButton("Visa mottagen bild",(d,w)->{if(dialog==d)open(grantedReview,grantedItem);}).create());
        },()->{});
    });}
    private void reviewMetadata(){
        ReceivedImageReview g=grantedReview;ReceivedImageReview.Item item=grantedItem;
        if(g==null||item==null||!source())return;
        work("Läser mottagna metadata",true,token->inbox.load(),snapshot->{
            InboxStore.Receipt receipt=snapshot.receipts.stream().filter(item::matchesReceipt).findFirst().orElse(null);
            if(!g.inboxId.equals(snapshot.inboxId)||receipt==null){info("Underlaget ändrades","Läs om och granska på nytt.");return;}
            dismiss();decisions.show(snapshot.inboxId,receipt);
        },()->{});
    }
    public void received(UUID uid){
        cancel();work("Läser mottagna bilder",false,token->{
            ImageAssociationRegistry.Snapshot r=registry.load();InboxStore.Snapshot i=inbox.load();LocalV2.Snapshot l=local.load();
            if(uid!=null)return Collections.singletonList(ReceivedImageReview.assess(r,i,l,uid));
            Set<UUID> ids=new LinkedHashSet<>();for(ImageAssociationRegistry.Record record:r.records)ids.add(record.deviceUid);
            List<ReceivedImageReview> result=new ArrayList<>();for(UUID id:ids)result.add(ReceivedImageReview.assess(r,i,l,id));return result;
        },reviews->{
            List<ReceivedImageReview> owners=new ArrayList<>();List<ReceivedImageReview.Item> items=new ArrayList<>();List<String> labels=new ArrayList<>();
            for(ReceivedImageReview r:reviews)for(ReceivedImageReview.Item item:r.images){owners.add(r);items.add(item);labels.add((item.metadata==null?"Metadata saknas":item.metadata.displayId)+"\nUUID: "+r.deviceUid+"\n"+item.record.contextId+"\n"+item.record.verifiedAt);}
            if(items.isEmpty()){info("Inga mottagna bilder för valet","Den lästa historiken innehåller inga bildposter för detta UUID. Ingen historik har återskapats.");return;}
            replace(new AlertDialog.Builder(context).setTitle("Mottagna bilder – separata varianter").setItems(labels.toArray(new String[0]),(d,n)->{if(dialog==d&&available())open(owners.get(n),items.get(n));}).setNegativeButton("Stäng",(d,w)->{if(dialog==d)cancel();}).create());
        },()->{});
    }
    private void open(ReceivedImageReview selected,ReceivedImageReview.Item item){
        work("Verifierar lagrad bild",false,token->{
            ReceivedImageReview fresh=ReceivedImageReview.assess(registry.load(),inbox.load(),local.load(),selected.deviceUid);
            if(!fresh.registryId.equals(selected.registryId)||!fresh.inboxId.equals(selected.inboxId))throw new IOException();
            ReceivedImageReview.Item current=fresh.images.stream().filter(x->x.record.stageLocalId.equals(item.record.stageLocalId)).findFirst().orElseThrow(IOException::new);
            ReceivedImagePayload payload=ReceivedImageContent.open(selected,item,registry,inbox,stage);
            if(!current.matchesOffer(payload.offer))throw new IOException();
            return new Object[]{current,LocalImageBitmaps.inspection(payload.image.bytes())};
        },result->viewer((ReceivedImageReview.Item)result[0],(Bitmap)result[1]),()->{},result->((Bitmap)result[1]).recycle());
    }
    private void viewer(ReceivedImageReview.Item item,Bitmap bitmap){
        dismiss();
        Dialog full=new Dialog(context,android.R.style.Theme_Material_NoActionBar);LinearLayout body=column();body.setBackgroundColor(Color.BLACK);
        body.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        TextView title=text("Mottagen bild – "+item.metadata.displayId+"\nUUID: "+item.record.deviceUid+"\nErbjuden metadata: "+item.metadata.updatedAt
                +"\n"+(item.sameCurrentMetadata?"Vid öppning: samma metadata som lokalt":"Historisk/annan metadata – ingen lokal bild har ersatts")+"\nNyp/dubbeltryck för zoom. Dra för att flytta.");title.setTextColor(Color.WHITE);body.addView(title);
        ZoomImageView image=new ZoomImageView(context,bitmap);image.setContentDescription("Mottagen bild för "+item.metadata.displayId);body.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(context);for(String label:new String[]{"Zooma in","Zooma ut","Anpassa","Uppgifter","Stäng"}){Button button=new Button(context);button.setText(label);button.setMinWidth(0);button.setMinimumWidth(0);button.setOnClickListener(v->{if(label.equals("Zooma in"))image.zoomBy(2);else if(label.equals("Zooma ut"))image.zoomBy(.5f);else if(label.equals("Anpassa"))image.fit();else if(label.equals("Uppgifter"))viewerDetails(item);else full.dismiss();});controls.addView(button,new LinearLayout.LayoutParams(0,-2,1));}body.addView(controls);
        full.setContentView(body);full.setOnDismissListener(d->{image.clearImage();bitmap.recycle();if(viewer==full)viewer=null;});viewer=full;full.show();full.getWindow().setLayout(-1,-1);body.requestApplyInsets();
    }
    private void viewerDetails(ReceivedImageReview.Item item){
        LinearLayout body=column();body.addView(text(host.summary(item.metadata)+"\n\nKällkontext: "+item.record.contextId
                +"\nAssociation: "+item.record.associationId+"\nLeveransvariant: "+item.record.deliveryId
                +"\nVerifierad lokalt: "+item.record.verifiedAt+"\nDetta är bildens erbjudna metadata, inte en automatisk uppdatering av din enhet."));
        AlertDialog details=new AlertDialog.Builder(context).setTitle("Bildens mottagna uppgifter").setView(scroll(body))
                .setPositiveButton("Tillbaka till bilden",(d,w)->{if(dialog==d)dialog=null;}).create();
        replace(details);details.setOnCancelListener(d->{if(dialog==d)dialog=null;});
    }
    private <T>void work(String title,boolean needsSource,Work<T> work,Consumer<T> done,Runnable dropped){work(title,needsSource,work,done,dropped,result->{});}
    private <T>void work(String title,boolean needsSource,Work<T> work,Consumer<T> done,Runnable dropped,Consumer<T> discard){
        if(busy||!available())return;long token=generation.incrementAndGet();busy=true;writing=false;progress(title);
        worker.execute(()->{
            T result;
            try{require(token,needsSource);result=work.run(token);}
            catch(Exception|OutOfMemoryError failed){dropped.run();postFailure(token);return;}
            host.post(()->{if(!valid(token)||needsSource&&!source()){discard.accept(result);dropped.run();if(generation.get()==token)cancel();return;}busy=false;writing=false;done.accept(result);});
        });
    }
    private <T>void read(String title,Network<T> operation,Consumer<T> done){
        if(busy||!source())return;long token=generation.incrementAndGet();busy=true;writing=false;grantedReview=null;grantedItem=null;
        MissionReadAttempt attempt=MissionReadAttempt.forImage(System.nanoTime());pending=attempt;progress(title);
        try{
            attempt.bindDeadline(timer.schedule(()->{attempt.cancel();postFailure(token);},60,TimeUnit.SECONDS));
            attempt.bindWorker(network.submit(()->{
                try{require(token,true);T result=operation.run(attempt);require(token,true);if(!attempt.active(System.nanoTime()))throw new IOException();host.post(()->{
                    if(generation.get()!=token||pending!=attempt)return;
                    if(!available()||!source()||!attempt.active(System.nanoTime())){postFailure(token);return;}
                    pending=null;attempt.clearTimers();busy=false;done.accept(result);
                });}catch(Exception|OutOfMemoryError failed){attempt.cancel();postFailure(token);}
            }));
        }catch(RejectedExecutionException stopping){attempt.cancel();postFailure(token);}
    }
    private void postFailure(long token){host.post(()->{if(closed||generation.get()!=token)return;cancel();info("Bildåtgärden kunde inte bekräftas","Kontrollera källa, behörighet, lagringsutrymme och historik. En export, metadatamottagning eller bildpost kan redan vara sparad. Befintliga privata bilder/enheter ersätts inte. Inget återförsöks automatiskt. Ofullständiga exporter i Hämtningar måste granskas/raderas manuellt.");});}
    public void sourceLost(){
        if(!trial.live()&&pending==null&&pendingReview==null&&transport==null)return;
        long token=generation.get();trial.end();MissionReadAttempt a=pending;if(a!=null)a.cancel();grantedReview=null;grantedItem=null;
        host.post(()->{if(!closed&&generation.get()==token){cancel();info("Källåtkomsten avbröts","Välj källa och granska på nytt. Historik behålls; en export, mottagning eller ett bekräftat lokalt metadataval kan redan vara sparat. Läs om aktuellt tillstånd.");}});
    }
    private void progress(String title){replace(new AlertDialog.Builder(context).setTitle(title).setMessage("Kan avbrytas. Inget publiceras automatiskt.").setNegativeButton("Avbryt",(d,w)->{if(dialog==d)cancelWithNotice();}).create());}
    private void info(String title,String message){if(!available())return;replace(new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("Stäng",(d,w)->cancel()).create());}
    private void replace(AlertDialog next){dismiss();dialog=next;next.setCanceledOnTouchOutside(false);next.setOnCancelListener(d->{if(dialog==d)cancelWithNotice();});next.show();}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    private LinearLayout column(){LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,16,24,16);return body;}
    private TextView text(String value){TextView view=new TextView(context);view.setText(value);return view;}
    private ScrollView scroll(LinearLayout body){ScrollView scroll=new ScrollView(context);scroll.addView(body);return scroll;}
    private void cancelWithNotice(){boolean uncertain=writing;cancel();if(uncertain)info("Avbrutet – kontrollera sparat resultat","En export eller mottagning kan redan vara sparad. Kontrollera Hämtningar och mottagen historik före ett nytt försök.");}
    public void cancel(){generation.incrementAndGet();trial.end();grantedReview=null;grantedItem=null;MissionReadAttempt a=pending;pending=null;if(a!=null)a.cancel();transport=null;endpoint=null;busy=false;writing=false;sourceChoice.cancel();consent.cancel();decisions.cancel();commits.cancel();dismiss();if(preview!=null){preview.close();preview=null;}ImagePublicationReview r=pendingReview;pendingReview=null;if(r!=null)r.close();if(viewer!=null){viewer.dismiss();viewer=null;}}
    public void close(){closed=true;cancel();sourceChoice.close();consent.close();decisions.close();commits.close();network.shutdownNow();timer.shutdownNow();}
}
