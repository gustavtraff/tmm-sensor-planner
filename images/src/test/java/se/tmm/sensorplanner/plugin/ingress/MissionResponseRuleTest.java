package se.tmm.sensorplanner.plugin.ingress;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static se.tmm.sensorplanner.plugin.ingress.MissionReadFailure.Rule.*;

/** #280: each failed response check names one fixed rule; native drawings are counted, not rejected. */
@RunWith(RobolectricTestRunner.class) @Config(sdk={26,35},manifest=Config.NONE)
public class MissionResponseRuleTest {
    private static final String GUID="00000000-0000-4000-8000-000000000171";
    private static final MissionSnapshotInput.Feed FEED=new MissionSnapshotInput.Feed("Synthetic",GUID);
    private interface Read{void run()throws Exception;}
    private static InputStream input(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    private static void assertRule(MissionReadFailure.Rule expected,Read read){
        MissionReadFailure.Violation failure=assertThrows(MissionReadFailure.Violation.class,read::run);
        assertEquals(expected,failure.rule);assertNull(failure.getCause());
    }
    private String tmm()throws Exception{
        String metadata;try(InputStream in=getClass().getResourceAsStream("/mini.xml")){assertNotNull(in);metadata=new String(in.readAllBytes(),StandardCharsets.UTF_8);metadata=metadata.substring(metadata.indexOf("?>")+2);}
        return "<event version=\"2.0\" uid=\"00000000-0000-4000-8018-000000000001\" type=\"b-m-p-s-p-loc\" how=\"h-g-i-g-o\" time=\"2026-01-01T00:00:00Z\" start=\"2026-01-01T00:00:00Z\" stale=\"2026-01-01T01:00:00Z\"><point lat=\"0.1234568\" lon=\"-0.2345679\"/><detail>"+metadata+"</detail></event>";
    }
    private static final String TMM_UID="00000000-0000-4000-8018-000000000001";
    /** Synthetic native ATAK-style drawing: a closed shape with corner links and an optional geofence detail. */
    private static String drawing(String uid,int corners,boolean geofence){
        StringBuilder links=new StringBuilder();for(int n=0;n<corners;n++)links.append("<link point=\"0.").append(n+1).append(",0.").append(n+2).append("\"/>");
        return "<event version=\"2.0\" uid=\""+uid+"\" type=\"u-d-f\" how=\"h-e\" time=\"2026-01-01T00:00:00Z\" start=\"2026-01-01T00:00:00Z\" stale=\"2026-01-02T00:00:00Z\">"
                +"<point lat=\"0.0\" lon=\"0.0\" hae=\"9999999.0\" ce=\"9999999.0\" le=\"9999999.0\"/><detail>"+links
                +"<strokeColor value=\"-1\"/><strokeWeight value=\"4.0\"/><fillColor value=\"-1761607681\"/><contact callsign=\"Yta\"/><remarks>Synthetic text</remarks><archive/><labels_on value=\"false\"/>"
                +(geofence?"<__geofence elevationMonitored=\"false\" minElevation=\"\" monitor=\"All\" trigger=\"Both\" tracking=\"false\" maxElevation=\"\" boundingSphere=\"100.0\"/>":"")
                +"<_flow-tags_ TAK-Server-00000000000000000000000000000000=\"2026-01-01T00:00:00Z\"/></detail></event>";
    }
    private static String overview(String uids,String contents){
        return "{\"type\":\"Mission\",\"data\":[{\"name\":\"Synthetic\",\"guid\":\""+GUID+"\",\"uids\":["+uids+"],\"contents\":["+contents+"]}]}";
    }

    @Test public void nativeDrawingsAndGeofencesBesideTmmItemsAreCountedNotRejected()throws Exception{
        String xml="<events>"+drawing("shape-1",4,false)+drawing("shape-2",5,false)+drawing("shape-3",4,false)+drawing("fence-1",6,true)+tmm()+"</events>";
        Set<String> members=new HashSet<>(Arrays.asList("shape-1","shape-2","shape-3","fence-1",TMM_UID));
        assertEquals(1,MissionSnapshotInput.metadata(input(xml),members,()->true).size());
    }

    @Test public void cotRulesNameTheFailedCheck()throws Exception{
        String event=tmm();Set<String> one=Collections.singleton(TMM_UID);
        assertRule(COT_MISSING,()->MissionSnapshotInput.metadata(input("<events>"+event+"</events>"),new HashSet<>(Arrays.asList(TMM_UID,"absent")),()->true));
        assertRule(COT_EXTRA,()->MissionSnapshotInput.metadata(input("<events>"+event+"</events>"),Collections.emptySet(),()->true));
        assertRule(DETAIL_CHILDREN,()->MissionSnapshotInput.metadata(input("<events>"+drawing("freehand",65,false)+"</events>"),Collections.singleton("freehand"),()->true));
        assertRule(XML_FORBIDDEN,()->MissionSnapshotInput.metadata(input("<events><!-- note -->"+event+"</events>"),one,()->true));
        assertRule(XML_FORBIDDEN,()->MissionSnapshotInput.metadata(input("<events xmlns:x=\"urn:x\">"+event+"</events>"),one,()->true));
        assertRule(XML_SYNTAX,()->MissionSnapshotInput.metadata(input("<events>"+event),one,()->true));
        assertRule(MEMBER_ID,()->MissionSnapshotInput.metadata(input("<events>"+event+event+"</events>"),one,()->true));
        assertRule(TMM_EVENT,()->MissionSnapshotInput.metadata(input("<events>"+event.replace("b-m-p-s-p-loc","a-u-G")+"</events>"),one,()->true));
        assertRule(TMM_TIME,()->MissionSnapshotInput.metadata(input("<events>"+event.replace("time=\"2026-01-01T00:00:00Z\"","time=\"2026-01-01 00:00\"")+"</events>"),one,()->true));
        assertRule(TMM_POSITION,()->MissionSnapshotInput.metadata(input("<events>"+event.replace("lat=\"0.1234568\"","lat=\"0.12345678\"")+"</events>"),one,()->true));
        assertRule(TMM_FIELDS,()->MissionSnapshotInput.metadata(input("<events>"+event.replace("<tmmPublication ","<tmmPublication extra=\"1\" ")+"</events>"),one,()->true));
    }

    @Test public void jsonAndResourceRulesNameTheFailedCheck(){
        assertRule(MEMBER_ID,()->MissionSnapshotInput.members(FEED,input(overview("{\"data\":1}","")),()->true));
        assertRule(JSON_LONG_VALUE,()->MissionSnapshotInput.members(FEED,input(overview("{\"data\":\"a\",\"details\":{\"callsign\":\""+"x".repeat(4097)+"\"}}","")),()->true));
        assertRule(JSON_KEY,()->MissionSnapshotInput.members(FEED,input(overview("","").replace("\"type\":","\"type\":\"Mission\",\"type\":")),()->true));
        assertRule(JSON_SYNTAX,()->MissionSnapshotInput.members(FEED,input(overview("","")+" garbage"),()->true));
        assertRule(FEED_MISMATCH,()->MissionSnapshotInput.members(FEED,input(overview("","").replace(GUID,GUID.replace("171","172"))),()->true));
        assertRule(NOT_MISSION,()->MissionSnapshotInput.feeds(input("{\"type\":\"Other\",\"data\":[]}"),()->true));
        String pad=String.join(",",Collections.nCopies(20,"\""+"y".repeat(4000)+"\""));
        assertRule(TOO_LARGE,()->MissionSnapshotInput.members(FEED,input(overview("{\"data\":\"a\",\"pad\":["+pad+"]}","")),()->true));
        String map="{\"data\":{\"hash\":\""+"a".repeat(64)+"\",\"name\":\"karta.tif\",\"size\":83886080}}";
        assertRule(RESOURCE_FIELD,()->MissionImageInput.read(FEED,input(overview("",map)),()->true));
    }

    @Test public void rulesReachTheVisibleSummaryWithoutReplyContent(){
        MissionReadFailure failure=MissionReadFailure.at(MissionReadFailure.Stage.COT,new MissionReadFailure.Violation(COT_MISSING));
        assertEquals(MissionReadFailure.Kind.RESPONSE,failure.kind);assertEquals(COT_MISSING,failure.rule);
        assertEquals("Läsa kartmetadata: svaret kunde inte verifieras (kartdata saknas för objekt som finns i feeden).",failure.summary());
        assertNull(MissionReadFailure.at(MissionReadFailure.Stage.COT,new IOException("private")).rule);
        assertThrows(IllegalArgumentException.class,()->new MissionReadFailure(MissionReadFailure.Stage.COT,MissionReadFailure.Kind.HTTP,403,COT_MISSING));
    }
}
