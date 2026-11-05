package se.tmm.sensorplanner.plugin.ingress;

import android.annotation.TargetApi;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Bounded lab overview only; no returned identifier or source authority escapes. */
@TargetApi(26)
public final class MissionReadInput {
    public static final int MAX_BYTES=65536;
    public enum Feed {
        METADATA("tmm-ds25-selected-20261023"), IMAGES("tmm-image162-20261105");
        public final String name;
        Feed(String name){this.name=name;}
    }
    public static final class Summary {
        public final int uids,resources;
        private Summary(int uids,int resources){this.uids=uids;this.resources=resources;}
    }
    private MissionReadInput() {}

    public static Summary read(Feed feed,InputStream input,BooleanSupplier active) throws IOException {
        try {
            if(feed==null||input==null||active==null)throw new IOException();
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int total=0;
            while(true){
                requireActive(active);
                int n=input.read(buffer,0,Math.min(buffer.length,MAX_BYTES+1-total));
                if(n<0)break;
                if(n==0){int value=input.read();if(value<0)break;buffer[0]=(byte)value;n=1;}
                total+=n;if(total>MAX_BYTES)throw new IOException();out.write(buffer,0,n);
            }
            requireActive(active);
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();
            boundStructure(text);
            JSONTokener parser=new JSONTokener(text);Object value=parser.nextValue();
            if(!(value instanceof JSONObject)||parser.nextClean()!=0)throw new IOException();
            JSONArray data=((JSONObject)value).getJSONArray("data");
            if(data.length()!=1)throw new IOException();JSONObject mission=data.getJSONObject(0);
            Object name=mission.get("name"),guid=mission.get("guid");
            if(!(name instanceof String)||!feed.name.equals(name)||!(guid instanceof String))throw new IOException();
            UUID id=UUID.fromString((String)guid);
            if(!id.toString().equals(guid)||id.equals(new UUID(0,0)))throw new IOException();
            JSONArray uids=mission.getJSONArray("uids"),resources=mission.getJSONArray("contents");
            if(uids.length()>64||resources.length()>64)throw new IOException();
            requireActive(active);return new Summary(uids.length(),resources.length());
        }catch(Exception invalid){throw new IOException("Lab response could not be verified");}
    }
    private static void requireActive(BooleanSupplier active) throws IOException {
        if(Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw new IOException();
    }
    private static void boundStructure(String text) throws IOException {
        int depth=0,stringLength=0;boolean quoted=false,escaped=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quoted){
                if(++stringLength>4096)throw new IOException();
                if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;
            }else if(c=='"'){quoted=true;stringLength=0;}
            else if(c=='\'')throw new IOException();
            else if(c=='{'||c=='['){if(++depth>16)throw new IOException();}
            else if(c=='}'||c==']'){if(--depth<0)throw new IOException();}
        }
        if(quoted||depth!=0)throw new IOException();
    }
}
