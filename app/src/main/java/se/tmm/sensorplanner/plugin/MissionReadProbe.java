package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.CommsProvider;
import com.atakmap.comms.CommsProviderFactory;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.net.CertificateManager;
import gov.tak.api.ui.IHostUIService;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpConnectionParams;
import se.tmm.sensorplanner.plugin.ingress.MissionReadInput;
import se.tmm.sensorplanner.plugin.ingress.MissionReadAttempt;

/** Explicit GET-only lab experiment; never grants source authority or writes received data. */
@TargetApi(26)
final class MissionReadProbe {
    private static final String BASE="https://127.0.0.1:8443";
    private final MapView map;
    private final IHostUIService ui;
    private final BooleanSupplier allowed;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new SynchronousQueue<>(),r->{Thread t=new Thread(r,"TMM-lab-read");t.setDaemon(true);return t;});
    private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,
            r->{Thread t=new Thread(r,"TMM-lab-read-guard");t.setDaemon(true);return t;});
    private volatile boolean closed;
    private volatile MissionReadAttempt job;
    private AlertDialog dialog;

    MissionReadProbe(MapView map,IHostUIService ui,BooleanSupplier allowed){
        this.map=map;this.ui=ui;this.allowed=allowed;timer.setRemoveOnCancelPolicy(true);
    }
    private boolean permitted(){return !closed&&allowed.getAsBoolean()&&ShareLabRoute.connected(map.getContext())&&defaultHttpProvider();}
    private static boolean defaultHttpProvider(){return CommsProviderFactory.isDefault()
            &&!CommsProviderFactory.getProvider().hasFeature(CommsProvider.CommsFeature.MISSION_API);}
    void show(){
        if(!permitted()){cancel();ui.showToast("Läsprovet kräver isolerat loopback-labb och flygplansläge");return;}
        if(job!=null){ui.showToast("Ett läsprov pågår. Avbryt det först.");return;}
        dismiss();
        dialog=new AlertDialog.Builder(map.getContext()).setTitle("Mission API · endast läsning i labb")
                .setItems(new String[]{MissionReadInput.Feed.METADATA.name,MissionReadInput.Feed.IMAGES.name},(d,n)->review(MissionReadInput.Feed.values()[n]))
                .setNegativeButton("Stäng",null).create();dialog.show();
    }
    private void review(MissionReadInput.Feed feed){
        if(!permitted()||job!=null)return;dismiss();
        dialog=new AlertDialog.Builder(map.getContext()).setTitle("Granska syntetiskt läsprov")
                .setMessage("Endast GET till isolerade labbservern 127.0.0.1:8443.\nFeed: "+feed.name
                        +"\nVärdens klientcertifikat används utan nyckelkopior. Högst 64 KiB; resultat godtas inom 12 sekunder. "
                        +"Endast antal poster och kontroll av namn/GUID-form visas. Inga data sparas, importeras eller skickas till andra klienter. "
                        +"Detta verifierar inte källa, historik, metadata eller bildkoppling.")
                .setNegativeButton("Avbryt",null).setPositiveButton("Läs labböversikt",(d,w)->start(feed)).create();dialog.show();
    }
    private void start(MissionReadInput.Feed feed){
        if(!permitted()||job!=null)return;
        MissionReadAttempt task=new MissionReadAttempt(System.nanoTime());job=task;
        dismiss();dialog=new AlertDialog.Builder(map.getContext()).setTitle("Läser labböversikt")
                .setMessage("Inga utkast eller bilder ändras.").setNegativeButton("Avbryt",(d,w)->cancel()).create();
        dialog.setOnCancelListener(d->cancel());dialog.setCanceledOnTouchOutside(false);dialog.show();
        try{
            task.bindDeadline(timer.schedule(()->{task.cancel();ui.queueEvent(()->finishCancelled(task,"Tidsgränsen nåddes. Inget resultat godtas."));},12,TimeUnit.SECONDS));
            task.bindGuard(timer.scheduleAtFixedRate(()->ui.queueEvent(()->{
                if(job==task&&!permitted())finishCancelled(task,"Labbvillkoren ändrades. Inget resultat godtas.");
            }),250,250,TimeUnit.MILLISECONDS));
            task.bindWorker(worker.submit(()->read(task,feed)));
        }catch(RejectedExecutionException busy){finishCancelled(task,"Föregående läsning avslutas fortfarande. Starta ett nytt prov senare.");}
    }
    private void read(MissionReadAttempt task,MissionReadInput.Feed feed){
        HttpGet request=null;TakHttpClient client=null;TakHttpResponse response=null;String message="Läsningen kunde inte verifieras. Ingen källa är registrerad.";
        try{
            requireActive(task);
            if(!defaultHttpProvider())throw new IOException();
            // Fresh host-managed factory: no cached permissive factory or allow-all hostname mode.
            CertificateManager.ExtendedSSLSocketFactory sockets=CertificateManager.getSockFactory(false,
                    NetConnectString.fromString(ShareLabRoute.STREAM),false);
            if(sockets==null)throw new IOException();
            client=new TakHttpClient(BASE+"/Marti",sockets,5000,5000);
            URI expected=URI.create(BASE+"/Marti/api/missions/"+feed.name);
            if(!expected.equals(URI.create(client.getUrl("/api/missions/"+feed.name))))throw new IOException();
            request=new HttpGet(expected);task.bindAbort(request::abort);
            request.setHeader("Accept","application/json");request.setHeader("Accept-Encoding","identity");
            HttpClientParams.setRedirecting(request.getParams(),false);
            HttpClientParams.setAuthenticating(request.getParams(),false);
            HttpConnectionParams.setConnectionTimeout(request.getParams(),5000);
            HttpConnectionParams.setSoTimeout(request.getParams(),5000);
            request.getParams().setIntParameter(CoreConnectionPNames.MAX_HEADER_COUNT,32);
            request.getParams().setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH,4096);
            requireActive(task);if(!defaultHttpProvider())throw new IOException();
            response=client.execute(request,false);requireActive(task);
            String type=response.getContentType(),encoding=response.getHeaderString("Content-Encoding");
            if(response.getStatusCode()!=200||type==null||!type.toLowerCase(java.util.Locale.ROOT).matches("application/json(?:\\s*;\\s*charset=utf-8)?")
                    ||(encoding!=null&&!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))
                    ||response.getLength()>MissionReadInput.MAX_BYTES)throw new IOException();
            MissionReadInput.Summary result=MissionReadInput.read(feed,response.getBody(),()->!closed&&task.active(System.nanoTime()));
            requireActive(task);
            message="Labböversikt läst: namn och GUID-form kontrollerade.\nUID-poster: "+result.uids+" · resursposter: "+result.resources
                    +"\nEndast strukturräkning. Ingen källa, originalvariant, mottagningskvittens eller bildkoppling verifierad. Inget sparat.";
        }catch(Exception unavailable){/* Raw provider/network errors can contain private profile information. */}
        finally{
            try{if(request!=null)request.abort();}catch(RuntimeException ignored){}
            try{if(response!=null)response.close();}catch(RuntimeException unavailable){message="Avslutningen kunde inte verifieras. Inget resultat godtas.";}
            try{if(client!=null)client.shutdown();}catch(RuntimeException unavailable){message="Avslutningen kunde inte verifieras. Inget resultat godtas.";}
        }
        String outcome=message;ui.queueEvent(()->{
            if(closed||job!=task||!task.active(System.nanoTime()))return;
            if(!permitted()){finishCancelled(task,"Labbvillkoren ändrades. Inget resultat godtas.");return;}
            job=null;task.clearTimers();showResult(outcome);
        });
    }
    private void requireActive(MissionReadAttempt task)throws IOException{
        if(closed||!task.active(System.nanoTime())||Thread.currentThread().isInterrupted())throw new IOException();
    }
    private void finishCancelled(MissionReadAttempt task,String message){if(job!=task)return;cancel();if(!closed)showResult(message);}
    private void showResult(String message){dismiss();dialog=new AlertDialog.Builder(map.getContext()).setTitle("Labbresultat · inte en import")
            .setMessage(message).setPositiveButton("Stäng",null).create();dialog.show();}
    private void cancel(){MissionReadAttempt old=job;job=null;if(old!=null)old.cancel();}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    void stop(){closed=true;cancel();worker.shutdownNow();timer.shutdownNow();}
    void close(){stop();dismiss();}
}
