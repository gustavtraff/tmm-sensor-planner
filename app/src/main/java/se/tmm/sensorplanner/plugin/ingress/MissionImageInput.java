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
            JSONObject object=(JSONObject)root;JSONArray data=object.optJSONArray("data");
            if(!"Mission".equals(object.opt("type"))||data==null||data.length()!=1)throw rule(MissionReadFailure.Rule.NOT_MISSION);
            JSONObject mission=data.getJSONObject(0);
            if(!expected.guid.equals(mission.opt("guid"))||!expected.name.equals(mission.opt("name")))throw rule(MissionReadFailure.Rule.FEED_MISMATCH);
            JSONArray contents=mission.optJSONArray("contents");if(contents==null)throw rule(MissionReadFailure.Rule.RESOURCE_FIELD);if(contents.length()>MAX_RESOURCES)throw rule(MissionReadFailure.Rule.FEED_COUNT);
            Set<String> hashes=new HashSet<>();List<Resource> result=new ArrayList<>();
            for(int n=0;n<contents.length();n++) {
                JSONObject item;String hash,name,mime;long length;
                try{
                    item=contents.getJSONObject(n).getJSONObject("data");
                    hash=string(item,"hash");name=string(item,"name");mime=string(item,"mimeType");
                    if(!hash.matches("[0-9a-f]{64}")||!hashes.add(hash))throw new IOException();
                    label(name);Object size=item.get("size");
                    if(!(size instanceof Integer)&&!(size instanceof Long))throw new IOException();
                    length=((Number)size).longValue();if(length<1)throw new IOException();
                }catch(Exception invalid){throw rule(MissionReadFailure.Rule.RESOURCE_FIELD);}
                if("application/octet-stream".equalsIgnoreCase(mime)&&name.toLowerCase(Locale.ROOT).endsWith(".tmmi")) {
                    if(length>ImageContainerCodec.MAX_BYTES)throw new IOException();
                    result.add(new Resource(hash,name,"application/octet-stream",(int)length));
                }
            }
            if(!active.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException();
            return Collections.unmodifiableList(result);
        }catch(MissionReadFailure.Violation rule){throw rule;}
        catch(Exception invalid){throw new IOException("Selected feed image resources could not be verified");}
        catch(OutOfMemoryError exhausted){throw new se.tmm.sensorplanner.images.ImagePreparationException(se.tmm.sensorplanner.images.ImagePreparationException.Reason.LOW_MEMORY);}
    }
    private static MissionReadFailure.Violation rule(MissionReadFailure.Rule rule){return new MissionReadFailure.Violation(rule);}
    /** #243: the {@code Hash} of one bounded {@code /sync/upload} JSON reply; comparing it with the sent bytes is the caller's job. */
    public static String uploadedHash(InputStream input,BooleanSupplier active)throws IOException {
        try {
            String text=MissionReadInput.readText(input,active,16384);MissionReadInput.boundStructure(text);
            JSONTokener parser=new JSONTokener(text);Object root=parser.nextValue();
            if(!(root instanceof JSONObject)||parser.nextClean()!=0)throw new IOException();
            String hash=string((JSONObject)root,"Hash");if(!hash.matches("[0-9a-f]{64}"))throw new IOException();
            return hash;
        }catch(Exception invalid){throw new IOException("Upload reply could not be verified");}
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
