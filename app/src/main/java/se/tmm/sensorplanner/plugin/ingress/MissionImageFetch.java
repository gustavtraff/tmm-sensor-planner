package se.tmm.sensorplanner.plugin.ingress;

import android.annotation.TargetApi;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.plugin.images.*;

/** Complete selected-feed read; caller supplies authenticated fixed-origin HTTP and owns stream cleanup. */
@TargetApi(26)
public final class MissionImageFetch {
    public interface Decoder<T> { T read(InputStream input)throws IOException; }
    public interface Exchange { <T>T get(String path,String accept,int maxBytes,Decoder<T> decoder)throws IOException; }
    private MissionImageFetch() { }
    public static ReceivedImagePayload read(MissionSnapshotInput.Feed feed,MissionImageInput.Resource selected,
                                             Exchange http,BooleanSupplier active)throws IOException {
        try {
            Objects.requireNonNull(feed);Objects.requireNonNull(selected);Objects.requireNonNull(http);
            require(active);String path="/api/missions/guid/"+feed.guid;
            List<MissionImageInput.Resource> before=http.get(path,"application/json",MissionImageInput.MAX_BYTES,
                    input->MissionImageInput.read(feed,input,active));
            member(before,selected);require(active);
            ReceivedImagePayload result=http.get("/sync/content?hash="+selected.hash,"application/octet-stream",selected.length,input->{
                Bounded body=new Bounded(input,selected.length,active);
                ReceivedImagePayload payload=ImageContainerCodec.read(body);
                if(body.count!=selected.length||!hex(body.digest.digest()).equals(selected.hash))throw new IOException();
                return payload;
            });
            require(active);
            List<MissionImageInput.Resource> after=http.get(path,"application/json",MissionImageInput.MAX_BYTES,
                    input->MissionImageInput.read(feed,input,active));
            member(after,selected);require(active);return result;
        }catch(se.tmm.sensorplanner.images.ImagePreparationException safe){throw safe;}
        catch(Exception unavailable){throw new IOException("Selected feed image download could not be verified");}
        catch(OutOfMemoryError exhausted){throw new se.tmm.sensorplanner.images.ImagePreparationException(se.tmm.sensorplanner.images.ImagePreparationException.Reason.LOW_MEMORY);}
    }
    private static void member(List<MissionImageInput.Resource> list,MissionImageInput.Resource selected)throws IOException {
        int matches=0;for(MissionImageInput.Resource item:list)if(item.sameClaims(selected))matches++;
        if(matches!=1)throw new IOException();
    }
    private static void require(BooleanSupplier active)throws IOException {
        if(active==null||Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw new IOException();
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static final class Bounded extends InputStream {
        final InputStream input;final int limit;final BooleanSupplier active;final MessageDigest digest;int count;
        Bounded(InputStream input,int limit,BooleanSupplier active)throws IOException {
            this.input=input;this.limit=limit;this.active=active;
            try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException unavailable){throw new IOException();}
        }
        @Override public int read()throws IOException {
            require(active);int value=input.read();if(value>=0){if(++count>limit)throw new IOException();digest.update((byte)value);}return value;
        }
        @Override public int read(byte[] bytes,int offset,int length)throws IOException {
            require(active);if(length==0)return 0;
            int n=input.read(bytes,offset,Math.min(length,limit+1-count));
            if(n>0){count+=n;if(count>limit)throw new IOException();digest.update(bytes,offset,n);}return n;
        }
    }
}
