package se.tmm.sensorplanner.plugin.ingress;

import android.annotation.TargetApi;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import se.tmm.sensorplanner.plugin.images.ImageContainerCodec;

/** Resource claims from one bounded selected-feed overview; not source authority or verified image bytes. */
@TargetApi(26)
public final class MissionImageInput {
    public static final int MAX_BYTES=512*1024, MAX_RESOURCES=256;
    private MissionImageInput() { }
    public static final class Resource {
        public final String hash,name,mime;
        public final int length;
        private Resource(String hash,String name,String mime,int length) {
            this.hash=hash;this.name=name;this.mime=mime;this.length=length;
        }
        public boolean sameClaims(Resource other) {
            return other!=null&&hash.equals(other.hash)&&length==other.length&&mime.equals(other.mime);
        }
    }
    public static List<Resource> read(MissionSnapshotInput.Feed expected,InputStream input,
                                       BooleanSupplier active)throws IOException {
        try {
            Objects.requireNonNull(expected);
            String text=MissionReadInput.readText(input,active,MAX_BYTES);
            MissionReadInput.boundStructure(text);JSONTokener parser=new JSONTokener(text);
            Object root=parser.nextValue();if(!(root instanceof JSONObject)||parser.nextClean()!=0)throw new IOException();
            JSONObject object=(JSONObject)root;if(!"Mission".equals(object.get("type")))throw new IOException();
            JSONArray data=object.getJSONArray("data");if(data.length()!=1)throw new IOException();
            JSONObject mission=data.getJSONObject(0);
            if(!expected.guid.equals(mission.get("guid"))||!expected.name.equals(mission.get("name")))throw new IOException();
            JSONArray contents=mission.getJSONArray("contents");if(contents.length()>MAX_RESOURCES)throw new IOException();
            Set<String> hashes=new HashSet<>();List<Resource> result=new ArrayList<>();
            for(int n=0;n<contents.length();n++) {
                JSONObject item=contents.getJSONObject(n).getJSONObject("data");
                String hash=string(item,"hash"),name=string(item,"name"),mime=string(item,"mimeType");
                if(!hash.matches("[0-9a-f]{64}")||!hashes.add(hash))throw new IOException();
                label(name);Object size=item.get("size");
                if(!(size instanceof Integer)&&!(size instanceof Long))throw new IOException();
                long length=((Number)size).longValue();if(length<1)throw new IOException();
                if("application/octet-stream".equalsIgnoreCase(mime)&&name.toLowerCase(Locale.ROOT).endsWith(".tmmi")) {
                    if(length>ImageContainerCodec.MAX_BYTES)throw new IOException();
                    result.add(new Resource(hash,name,"application/octet-stream",(int)length));
                }
            }
            if(!active.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException();
            return Collections.unmodifiableList(result);
        }catch(Exception invalid){throw new IOException("Selected feed image resources could not be verified");}
        catch(OutOfMemoryError exhausted){throw new se.tmm.sensorplanner.images.ImagePreparationException(se.tmm.sensorplanner.images.ImagePreparationException.Reason.LOW_MEMORY);}
    }
    private static String string(JSONObject item,String key)throws Exception {
        Object value=item.get(key);if(!(value instanceof String))throw new IOException();return (String)value;
    }
    private static void label(String value)throws IOException {
        if(value.isEmpty()||value.length()>256||!value.equals(value.trim()))throw new IOException();
        for(int n=0;n<value.length();) {
            int cp=value.codePointAt(n),type=Character.getType(cp);
            if(Character.isISOControl(cp)||type==Character.FORMAT||type==Character.SURROGATE
                    ||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR)throw new IOException();
            n+=Character.charCount(cp);
        }
    }
}
