package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import com.atakmap.comms.*;
import com.atakmap.comms.http.*;
import com.atakmap.net.CertificateManager;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.params.*;
import se.tmm.sensorplanner.core.TmmDraft;
import se.tmm.sensorplanner.plugin.ingress.*;

/** Explicit configured-source snapshot reader; no credential copies, subscription or storage operation. */
@TargetApi(26)
final class MissionSnapshotHttp implements se.tmm.sensorplanner.plugin.local.SnapshotInboxUi.Transport {
    static final String BASE="https://127.0.0.1:8443";
    static boolean defaultProvider(){
        try{return CommsProviderFactory.isDefault()&&!CommsProviderFactory.getProvider().hasFeature(CommsProvider.CommsFeature.MISSION_API);}
        catch(RuntimeException unavailable){return false;}
    }
    interface Decoder<T>{T read(InputStream input)throws IOException;}
    private final BooleanSupplier allowed;
    private final MissionEndpoint endpoint;
    MissionSnapshotHttp(BooleanSupplier allowed){this(allowed,MissionEndpoint.fromProfile(ShareLabRoute.STREAM,8443));}
    MissionSnapshotHttp(BooleanSupplier allowed,MissionEndpoint endpoint){this.allowed=allowed;this.endpoint=endpoint;}
    @Override public String description(){return endpoint.base+" · ATAK TLS "+endpoint.stream;}
    @Override public List<MissionSnapshotInput.Feed> list(MissionReadAttempt attempt)throws IOException {try(Read read=begin(attempt)){return read.feeds();}}
    @Override public MissionSnapshotInput.Snapshot fetch(MissionSnapshotInput.Feed feed,MissionReadAttempt attempt)throws IOException {try(Read read=begin(attempt)){return read.fetch(feed);}}
    final class Read implements AutoCloseable {
        final MissionReadAttempt attempt;
        private final AtomicReference<HttpGet> current=new AtomicReference<>();
        Read(MissionReadAttempt attempt){this.attempt=attempt;attempt.bindAbort(()->{HttpGet request=current.getAndSet(null);if(request!=null)request.abort();});}
        private boolean active(){return allowed.getAsBoolean()&&attempt.active(System.nanoTime())&&!Thread.currentThread().isInterrupted();}
        private void require()throws IOException {if(!active()||!defaultProvider())throw new IOException("Snapshot access unavailable");}
        List<MissionSnapshotInput.Feed> feeds()throws IOException {
            return get("/api/missions","application/json",in->MissionSnapshotInput.feeds(in,this::active,endpoint.base));
        }
        MissionSnapshotInput.Snapshot fetch(MissionSnapshotInput.Feed feed)throws IOException {
            if(!endpoint.base.equals(feed.service))throw new IOException("Selected source changed");
            String route="/api/missions/guid/"+feed.guid;
            Set<String> before=get(route,"application/json",in->MissionSnapshotInput.members(feed,in,this::active));
            List<TmmDraft> values=get(route+"/cot","application/xml",in->MissionSnapshotInput.metadata(in,before,this::active));
            Set<String> after=get(route,"application/json",in->MissionSnapshotInput.members(feed,in,this::active));
            if(!before.equals(after))throw new IOException("Mission membership changed; fetch again");require();return new MissionSnapshotInput.Snapshot(feed,after,values);
        }
        private <T>T get(String path,String accept,Decoder<T> decoder)throws IOException {
            HttpGet request=null;TakHttpClient client=null;TakHttpResponse response=null;
            T result=null;boolean failed=false;
            try{
                require();CertificateManager.ExtendedSSLSocketFactory sockets=CertificateManager.getSockFactory(false,NetConnectString.fromString(endpoint.stream),false);
                if(sockets==null)throw new IOException();client=new TakHttpClient(endpoint.base+"/Marti",sockets,5000,5000);
                URI expected=URI.create(endpoint.base+"/Marti"+path);if(!expected.equals(URI.create(client.getUrl(path))))throw new IOException();
                request=new HttpGet(expected);current.set(request);require();
                request.setHeader("Accept",accept);request.setHeader("Accept-Encoding","identity");
                HttpClientParams.setRedirecting(request.getParams(),false);HttpClientParams.setAuthenticating(request.getParams(),false);
                HttpConnectionParams.setConnectionTimeout(request.getParams(),5000);HttpConnectionParams.setSoTimeout(request.getParams(),5000);
                request.getParams().setIntParameter(CoreConnectionPNames.MAX_HEADER_COUNT,32);request.getParams().setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH,4096);
                response=client.execute(request,false);require();String type=response.getContentType(),encoding=response.getHeaderString("Content-Encoding");
                if(response.getStatusCode()!=200||type==null||!type.toLowerCase(Locale.ROOT).matches(accept+"(?:\\s*;\\s*charset=utf-8)?")
                        ||(encoding!=null&&!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))||response.getLength()>MissionReadInput.MAX_BYTES)throw new IOException();
                result=decoder.read(response.getBody());require();
            }catch(Exception unavailable){failed=true;}
            finally{
                current.compareAndSet(request,null);
                try{if(request!=null)request.abort();}catch(RuntimeException ignored){}
                try{if(response!=null)response.close();}catch(RuntimeException unavailable){failed=true;}
                try{if(client!=null)client.shutdown();}catch(RuntimeException unavailable){failed=true;}
            }
            if(failed)throw new IOException("Mission snapshot read failed; nothing accepted");require();return result;
        }
        @Override public void close(){HttpGet request=current.getAndSet(null);if(request!=null)request.abort();}
    }
    Read begin(MissionReadAttempt attempt){return new Read(attempt);}
}
