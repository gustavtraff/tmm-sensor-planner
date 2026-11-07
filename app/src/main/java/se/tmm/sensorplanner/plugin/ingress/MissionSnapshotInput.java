package se.tmm.sensorplanner.plugin.ingress;

import android.annotation.TargetApi;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;
import se.tmm.sensorplanner.core.*;

/** Validated current Mission snapshots only; no transport, source grant, storage or draft application. */
@TargetApi(26)
public final class MissionSnapshotInput {
    public static final int MAX_MEMBERS=64;
    private MissionSnapshotInput() { }
    public static final class Feed {
        public final String name,guid,service;
        public Feed(String name,String guid){this(name,guid,MissionEndpoint.LAB_BASE);}
        public Feed(String name,String guid,String service){
            if(name==null||name.isEmpty()||name.length()>256||!name.equals(name.trim()))throw new IllegalArgumentException("Invalid mission name");
            for(int n=0;n<name.length();){int cp=name.codePointAt(n),type=Character.getType(cp);
                if(Character.isISOControl(cp)||type==Character.FORMAT||type==Character.SURROGATE||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR)throw new IllegalArgumentException("Invalid mission name");n+=Character.charCount(cp);
            }
            UUID id=UUID.fromString(guid);if(!id.toString().equals(guid)||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Invalid mission GUID");
            this.name=name;this.guid=guid;this.service=MissionEndpoint.checkedBase(service);
        }
        public String context(){return "mission-api-v1|"+service+"|"+guid;}
    }
    public static final class Snapshot {
        public final Feed feed;public final Set<String> members;public final List<TmmDraft> metadata;
        public Snapshot(Feed feed,Set<String> members,List<TmmDraft> metadata){
            this.feed=Objects.requireNonNull(feed);this.members=Collections.unmodifiableSet(new HashSet<>(members));
            List<TmmDraft> copy=new ArrayList<>(metadata);Set<UUID> seen=new HashSet<>();
            if(members.size()>MAX_MEMBERS||copy.size()>MAX_MEMBERS)throw new IllegalArgumentException("Snapshot limit exceeded");
            for(TmmDraft item:copy)if(item==null||item.imageRef!=null||!members.contains(item.uid.toString())||!seen.add(item.uid))throw new IllegalArgumentException("Invalid snapshot members");
            this.metadata=Collections.unmodifiableList(copy);
        }
    }
    public static List<Feed> feeds(InputStream input,BooleanSupplier active)throws IOException {
        return feeds(input,active,MissionEndpoint.LAB_BASE);
    }
    public static List<Feed> feeds(InputStream input,BooleanSupplier active,String service)throws IOException {
        try {
            JSONArray missions=json(input,active);if(missions.length()>MAX_MEMBERS)throw new IOException();
            List<Feed> result=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(int n=0;n<missions.length();n++){Feed feed=feed(missions.getJSONObject(n),service);if(!seen.add(feed.guid))throw new IOException();result.add(feed);}
            return Collections.unmodifiableList(result);
        }catch(Exception invalid){throw failure();}
    }
    public static Set<String> members(Feed expected,InputStream input,BooleanSupplier active)throws IOException {
        try {
            JSONArray data=json(input,active);if(data.length()!=1)throw new IOException();JSONObject mission=data.getJSONObject(0);Feed actual=feed(mission,expected.service);
            if(!actual.guid.equals(expected.guid)||!actual.name.equals(expected.name))throw new IOException();
            JSONArray uids=mission.getJSONArray("uids");if(uids.length()>MAX_MEMBERS)throw new IOException();Set<String> result=new HashSet<>();
            for(int n=0;n<uids.length();n++){
                Object value=uids.getJSONObject(n).get("data");if(!(value instanceof String))throw new IOException();String uid=(String)value;
                if(uid.isEmpty()||uid.length()>128||!uid.equals(uid.trim())||!result.add(uid))throw new IOException();
            }
            return Collections.unmodifiableSet(result);
        }catch(Exception invalid){throw failure();}
    }
    private static Feed feed(JSONObject value,String service)throws Exception {
        Object name=value.get("name"),guid=value.get("guid");if(!(name instanceof String)||!(guid instanceof String))throw new IOException();return new Feed((String)name,(String)guid,service);
    }
    private static JSONArray json(InputStream input,BooleanSupplier active)throws Exception {
        String text=MissionReadInput.readText(input,active);MissionReadInput.boundStructure(text);JSONTokener parser=new JSONTokener(text);Object root=parser.nextValue();
        if(!(root instanceof JSONObject)||parser.nextClean()!=0||!"Mission".equals(((JSONObject)root).get("type")))throw new IOException();return ((JSONObject)root).getJSONArray("data");
    }
    public static List<TmmDraft> metadata(InputStream input,Set<String> members,BooleanSupplier active)throws IOException {
        try {
            Handler handler=new Handler(active);MissionCotInput.reader(handler).parse(new InputSource(new StringReader(MissionCotInput.xmlText(input,active))));
            if(!handler.complete||!handler.uids.equals(members)||!active.getAsBoolean())throw new IOException();
            return Collections.unmodifiableList(handler.drafts);
        }catch(Exception invalid){throw failure();}
        catch(javax.xml.parsers.FactoryConfigurationError invalid){throw failure();}
    }
    private static IOException failure(){return new IOException("Mission snapshot could not be verified");}
    private static final class Handler extends DefaultHandler2 {
        final BooleanSupplier active;final Set<String> uids=new HashSet<>();final List<TmmDraft> drafts=new ArrayList<>();
        Map<String,String> event,point;int depth,points,payloads,details;boolean complete,oldTag,inPayload,inDetail,inPoint;int detailChildren;String payload;
        Handler(BooleanSupplier active){this.active=active;}
        private void check()throws SAXException {if(Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw invalid();}
        @Override public void startElement(String uri,String local,String name,Attributes attrs)throws SAXException {
            check();if(++depth>16||inPoint||!uri.isEmpty()||!local.equals(name)||attrs.getLength()>64||inPayload)throw invalid();
            Map<String,String> values=new LinkedHashMap<>();
            for(int n=0;n<attrs.getLength();n++){
                String key=attrs.getQName(n),value=attrs.getValue(n);
                if(!attrs.getURI(n).isEmpty()||key.length()>128||value.length()>87384)throw invalid();values.put(key,value);
            }
            if(depth==1){if(!"events".equals(name)||!values.isEmpty())throw invalid();}
            else if(depth==2){
                if(!"event".equals(name)||uids.size()==MAX_MEMBERS)throw invalid();event=values;point=null;points=payloads=details=0;oldTag=false;inDetail=inPoint=false;detailChildren=0;payload=null;
                String uid=event.get("uid");if(uid==null||uid.isEmpty()||uid.length()>128||!uids.add(uid))throw invalid();
            }else if("event".equals(name)||"events".equals(name))throw invalid();
            if(depth==3&&"point".equals(name)){point=values;points++;inPoint=true;}
            if(depth==3&&"detail".equals(name)){details++;inDetail=true;}
            if(inDetail&&depth==4&&++detailChildren>64)throw invalid();
            if("tmmPublication".equals(name)&&!(depth==4&&inDetail))throw invalid();
            if(depth==4&&inDetail&&"tmmShareLab".equals(name))oldTag=true;
            if(depth==4&&inDetail&&"tmmPublication".equals(name)){
                if(++payloads!=1||values.size()!=15)throw invalid();inPayload=true;StringBuilder xml=new StringBuilder("<tmmPublication");
                for(Map.Entry<String,String> value:values.entrySet())xml.append(' ').append(value.getKey()).append("=\"").append(escape(value.getValue())).append('"');
                payload=xml.append("/>").toString();if(payload.length()>PublicationCodec.MAX_BYTES)throw invalid();
            }
        }
        @Override public void endElement(String uri,String local,String name)throws SAXException {
            check();if(depth==4&&inPayload)inPayload=false;
            if(depth==3){inDetail=false;inPoint=false;}
            if(depth==2&&payload!=null){
                try {
                    if(points!=1||details!=1||oldTag||!"2.0".equals(event.get("version"))||!"b-m-p-s-p-loc".equals(event.get("type"))||event.get("how")==null||event.get("how").isEmpty())throw invalid();
                    for(String key:new String[]{"time","start","stale"})Instant.parse(event.get(key));
                    ShareReview.Metadata m=PublicationCodec.decode(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
                    if(!m.uid.toString().equals(event.get("uid"))||Double.parseDouble(point.get("lat"))!=rounded(m.position.latitude)||Double.parseDouble(point.get("lon"))!=rounded(m.position.longitude))throw invalid();
                    drafts.add(new TmmDraft(m.uid,m.displayId,m.type,m.status,m.owner,m.createdAt,m.updatedAt,m.deployedAt,m.comment,m.position,m.bearingDeg,null,1));
                }catch(Exception invalid){throw invalid();}
            }
            if(--depth==0)complete=true;
        }
        @Override public void characters(char[] text,int start,int length)throws SAXException {
            check();if(inPayload||depth<=1)for(int n=start;n<start+length;n++)if(text[n]!=' '&&text[n]!='\t'&&text[n]!='\r'&&text[n]!='\n')throw invalid();
        }
        private static double rounded(double value){return Math.round(value*1e7)/1e7;}
        private static String escape(String v){return v.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;").replace("\r","&#13;").replace("\n","&#10;").replace("\t","&#9;");}
        @Override public void startPrefixMapping(String p,String u)throws SAXException {throw invalid();}
        @Override public void processingInstruction(String t,String d)throws SAXException {throw invalid();}
        @Override public void startDTD(String n,String p,String s)throws SAXException {throw invalid();}
        @Override public void startCDATA()throws SAXException {throw invalid();}
        @Override public void comment(char[] t,int s,int n)throws SAXException {throw invalid();}
        @Override public InputSource resolveEntity(String p,String s)throws SAXException {throw invalid();}
        @Override public InputSource resolveEntity(String n,String p,String b,String s)throws SAXException {throw invalid();}
        @Override public void error(SAXParseException e)throws SAXException {throw invalid();}
        @Override public void fatalError(SAXParseException e)throws SAXException {throw invalid();}
        private SAXException invalid(){return new SAXException("Invalid mission metadata");}
    }
}
