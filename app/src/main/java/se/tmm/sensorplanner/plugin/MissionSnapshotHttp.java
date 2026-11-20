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
import java.nio.charset.StandardCharsets;
import org.apache.http.client.methods.*;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.params.*;
import se.tmm.sensorplanner.core.TmmDraft;
import se.tmm.sensorplanner.plugin.ingress.*;
import se.tmm.sensorplanner.plugin.local.FeedPhotoPublication.WriteFailure;

/**
 * Explicit configured-source reads, and the #243 photo writes that only {@code FeedPhotoPublication} calls after an
 * explicit user action. No credential copies, subscription or storage operation.
 */
@TargetApi(26)
final class MissionSnapshotHttp implements se.tmm.sensorplanner.plugin.local.SnapshotInboxUi.Transport, se.tmm.sensorplanner.plugin.local.ImageTrialUi.Transport,
        se.tmm.sensorplanner.plugin.local.FeedFollowUi.Transport, se.tmm.sensorplanner.plugin.local.FeedPhotoPublication.Transport {
    static final String BASE="https://127.0.0.1:8443";
    // The server may take a while to store a photo of up to 16 MiB; the attempt's 60 s deadline still bounds the upload.
    static final int READ_MILLIS=5000,UPLOAD_READ_MILLIS=55_000;
    static boolean defaultProvider(){
        try{return CommsProviderFactory.isDefault()&&!CommsProviderFactory.getProvider().hasFeature(CommsProvider.CommsFeature.MISSION_API);}
        catch(RuntimeException unavailable){return false;}
    }
    private final BooleanSupplier allowed;
    private final MissionEndpoint endpoint;
    MissionSnapshotHttp(BooleanSupplier allowed){this(allowed,MissionEndpoint.fromProfile(ShareLabRoute.STREAM,8443));}
    MissionSnapshotHttp(BooleanSupplier allowed,MissionEndpoint endpoint){this.allowed=allowed;this.endpoint=endpoint;}
    @Override public String description(){return endpoint.base+" · ATAK TLS "+endpoint.stream;}
    @Override public List<MissionSnapshotInput.Feed> list(MissionReadAttempt attempt)throws IOException {try(Read read=begin(attempt)){return read.feeds();}}
    @Override public MissionSnapshotInput.Snapshot fetch(MissionSnapshotInput.Feed feed,MissionReadAttempt attempt)throws IOException {try(Read read=begin(attempt)){return read.fetch(feed);}}
    public List<MissionImageInput.Resource> images(MissionSnapshotInput.Feed feed,MissionReadAttempt attempt)throws IOException {
        try(Read read=begin(attempt)) {
            if(!endpoint.base.equals(feed.service))throw new IOException("Selected source changed");
            return read.get(MissionReadFailure.Stage.RESOURCE_LIST,"/api/missions/guid/"+feed.guid,"application/json",MissionImageInput.MAX_BYTES,
                    in->MissionImageInput.read(feed,in,read::active));
        }
    }
    public se.tmm.sensorplanner.plugin.images.ReceivedImagePayload image(MissionSnapshotInput.Feed feed,
            MissionImageInput.Resource selected,MissionReadAttempt attempt)throws IOException {
        try(Read read=begin(attempt)) {
            if(!endpoint.base.equals(feed.service))throw new IOException("Selected source changed");
            return MissionImageFetch.read(feed,selected,new MissionImageFetch.Exchange() {
                public <T>T get(String path,String accept,int maxBytes,MissionImageFetch.Decoder<T> decoder)throws IOException {
                    return read.get(MissionReadFailure.Stage.IMAGE,path,accept,maxBytes,decoder);
                }
            },read::active);
        }
    }
    @Override public String upload(String name,String creatorUid,byte[] container,MissionReadAttempt attempt)throws WriteFailure {
        try(Read read=begin(attempt)){
            return read.write(HttpPost::new,"/sync/upload?name="+name+"&creatorUid="+creatorUid,container,"application/octet-stream",UPLOAD_READ_MILLIS,
                    in->MissionImageInput.uploadedHash(in,read::active));
        }
    }
    @Override public void associate(String guid,String hash,String creatorUid,MissionReadAttempt attempt)throws WriteFailure {
        try(Read read=begin(attempt)){
            read.write(HttpPut::new,"/api/missions/guid/"+guid+"/contents?creatorUid="+creatorUid,
                    ("{\"hashes\":[\""+hash+"\"]}").getBytes(StandardCharsets.UTF_8),"application/json",READ_MILLIS,in->null);
        }
    }
    @Override public void remove(String guid,String hash,String creatorUid,MissionReadAttempt attempt)throws WriteFailure {
        try(Read read=begin(attempt)){
            read.write(HttpDelete::new,"/api/missions/guid/"+guid+"/contents?hash="+hash+"&creatorUid="+creatorUid,null,null,READ_MILLIS,in->null);
        }
    }
    /** A failed exchange: the HTTP status when one arrived (else -1) and whether the request may have left the device. */
    private static final class Failed extends IOException {
        final int status;final boolean sent;final MissionReadFailure diagnostic;
        Failed(int status,boolean sent,MissionReadFailure diagnostic){this.status=status;this.sent=sent;this.diagnostic=diagnostic;}
    }
    /** Body sent at most once: not repeatable, so the client cannot replay it; records whether writing began. */
    private static final class Body extends AbstractHttpEntity {
        private final byte[] bytes;volatile boolean started;
        Body(byte[] bytes,String type){this.bytes=bytes;setContentType(type);}
        @Override public boolean isRepeatable(){return false;}
        @Override public long getContentLength(){return bytes.length;}
        @Override public boolean isStreaming(){return false;}
        @Override public InputStream getContent(){started=true;return new ByteArrayInputStream(bytes);}
        @Override public void writeTo(OutputStream out)throws IOException{started=true;out.write(bytes);out.flush();}
    }
    private interface Request{HttpRequestBase create(URI uri);}
    private interface Reply<T>{T read(TakHttpResponse response)throws Exception;}
    final class Read implements AutoCloseable {
        final MissionReadAttempt attempt;
        private final AtomicReference<HttpRequestBase> current=new AtomicReference<>();
        Read(MissionReadAttempt attempt){this.attempt=attempt;attempt.bindAbort(()->{HttpRequestBase request=current.getAndSet(null);if(request!=null)request.abort();});}
        private boolean active(){return allowed.getAsBoolean()&&attempt.active(System.nanoTime())&&!Thread.currentThread().isInterrupted();}
        private void require()throws IOException {if(!active()||!defaultProvider())throw new IOException("Snapshot access unavailable");}
        List<MissionSnapshotInput.Feed> feeds()throws IOException {
            return get(MissionReadFailure.Stage.FEED_LIST,"/api/missions","application/json",in->MissionSnapshotInput.feeds(in,this::active,endpoint.base));
        }
        MissionSnapshotInput.Snapshot fetch(MissionSnapshotInput.Feed feed)throws IOException {
            if(!endpoint.base.equals(feed.service))throw new IOException("Selected source changed");
            String route="/api/missions/guid/"+feed.guid;
            Set<String> before=get(MissionReadFailure.Stage.MEMBERSHIP_BEFORE,route,"application/json",in->MissionSnapshotInput.members(feed,in,this::active));
            List<TmmDraft> values=get(MissionReadFailure.Stage.COT,route+"/cot","application/xml",in->MissionSnapshotInput.metadata(in,before,this::active));
            Set<String> after=get(MissionReadFailure.Stage.MEMBERSHIP_AFTER,route,"application/json",in->MissionSnapshotInput.members(feed,in,this::active));
            if(!before.equals(after))throw new MissionReadFailure.Violation(MissionReadFailure.Rule.MEMBERS_CHANGED);require();return new MissionSnapshotInput.Snapshot(feed,after,values);
        }
        private <T>T get(MissionReadFailure.Stage stage,String path,String accept,MissionImageFetch.Decoder<T> decoder)throws IOException {
            return get(stage,path,accept,MissionReadInput.MAX_BYTES,decoder);
        }
        private <T>T get(MissionReadFailure.Stage stage,String path,String accept,int maxBytes,MissionImageFetch.Decoder<T> decoder)throws IOException {
            try{
                return exchange(stage,HttpGet::new,path,accept,maxBytes,null,READ_MILLIS,response->{
                    String type=response.getContentType();
                    if(type==null||!type.toLowerCase(Locale.ROOT).matches(accept+"(?:\\s*;\\s*charset=utf-8)?"))throw new IOException();
                    return decoder.read(response.getBody());
                });
            }catch(Failed failed){throw failed.diagnostic;}
        }
        /** One write; maps the exchange's failure to how far it got. A 403 is a feed permission refusal. */
        <T>T write(Request create,String path,byte[] body,String type,int readMillis,MissionImageFetch.Decoder<T> decoder)throws WriteFailure {
            if(!path.matches("[A-Za-z0-9/._:?&=-]{1,512}"))throw new WriteFailure(WriteFailure.Kind.NOT_SENT);
            // The add/remove replies echo the mission and are not read; the upload reply has its own 16 KiB bound.
            try{return exchange(null,create,path,"application/json",MissionImageInput.MAX_BYTES,body==null?null:new Body(body,type),readMillis,response->decoder.read(response.getBody()));}
            catch(Failed f){
                if(f.status==403)throw new WriteFailure(WriteFailure.Kind.REFUSED);
                if(f.status>=400&&f.status<500)throw new WriteFailure(WriteFailure.Kind.REJECTED);
                throw new WriteFailure(f.sent?WriteFailure.Kind.UNKNOWN:WriteFailure.Kind.NOT_SENT);
            }
        }
        /**
         * Shared request lifetime for reads and writes: ATAK's strict TLS factory for the stored profile, fixed origin,
         * no redirect or Basic fallback, bounded headers and reply, abort on cancel and full cleanup.
         */
        private <T>T exchange(MissionReadFailure.Stage stage,Request create,String path,String accept,int maxBytes,Body body,int readMillis,Reply<T> reply)throws Failed {
            HttpRequestBase request=null;TakHttpClient client=null;TakHttpResponse response=null;
            T result=null;boolean failed=false,executing=false;int status=-1;MissionReadFailure diagnostic=null;
            try{
                require();CertificateManager.ExtendedSSLSocketFactory sockets=CertificateManager.getSockFactory(false,NetConnectString.fromString(endpoint.stream),false);
                if(sockets==null)throw new IOException();client=new TakHttpClient(endpoint.base+"/Marti",sockets,5000,readMillis);
                URI expected=URI.create(endpoint.base+"/Marti"+path);if(!expected.equals(URI.create(client.getUrl(path))))throw new IOException();
                request=create.create(expected);current.set(request);require();
                if(body!=null)((HttpEntityEnclosingRequestBase)request).setEntity(body);
                request.setHeader("Accept",accept);request.setHeader("Accept-Encoding","identity");
                HttpClientParams.setRedirecting(request.getParams(),false);HttpClientParams.setAuthenticating(request.getParams(),false);
                HttpConnectionParams.setConnectionTimeout(request.getParams(),5000);HttpConnectionParams.setSoTimeout(request.getParams(),readMillis);
                request.getParams().setIntParameter(CoreConnectionPNames.MAX_HEADER_COUNT,32);request.getParams().setIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH,4096);
                executing=true;response=client.execute(request,false);status=response.getStatusCode();require();
                String encoding=response.getHeaderString("Content-Encoding");
                if(status!=200||(encoding!=null&&!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))||response.getLength()>maxBytes){
                    if(stage!=null)throw new MissionReadFailure(stage,status>=100&&status<=599&&status!=200?MissionReadFailure.Kind.HTTP:MissionReadFailure.Kind.RESPONSE,status>=100&&status<=599&&status!=200?status:0);
                    throw new IOException();
                }
                result=reply.read(response);require();
            }catch(Exception unavailable){
                failed=true;
                if(stage!=null){
                    diagnostic=MissionReadFailure.at(stage,unavailable);
                    if(response==null&&diagnostic.kind==MissionReadFailure.Kind.RESPONSE)
                        diagnostic=new MissionReadFailure(stage,MissionReadFailure.Kind.CONNECTION,0);
                }
            }
            finally{
                current.compareAndSet(request,null);
                try{if(request!=null)request.abort();}catch(RuntimeException ignored){}
                try{if(response!=null)response.close();}catch(RuntimeException unavailable){failed=true;}
                try{if(client!=null)client.shutdown();}catch(RuntimeException unavailable){failed=true;}
            }
            // Without a body, a started request may have reached the server; with one, only once writing began.
            boolean sent=status>=0||(body!=null?body.started:executing);
            if(failed)throw new Failed(status,sent,diagnostic!=null?diagnostic:stage==null?null:new MissionReadFailure(stage,MissionReadFailure.Kind.UNKNOWN,0));
            try{require();}catch(IOException late){throw new Failed(status,sent,stage==null?null:MissionReadFailure.at(stage,late));}
            return result;
        }
        @Override public void close(){HttpRequestBase request=current.getAndSet(null);if(request!=null)request.abort();}
    }
    Read begin(MissionReadAttempt attempt){return new Read(attempt);}
}
