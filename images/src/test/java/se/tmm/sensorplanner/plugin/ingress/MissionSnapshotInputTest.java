package se.tmm.sensorplanner.plugin.ingress;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import se.tmm.sensorplanner.core.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,35},manifest=Config.NONE)
public class MissionSnapshotInputTest {
    private static final String GUID="00000000-0000-4000-8000-000000000171";
    private InputStream input(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    private String fixture(String name)throws Exception{
        String metadata;try(InputStream in=getClass().getResourceAsStream("/"+name+".xml")){assertNotNull(in);metadata=new String(in.readAllBytes(),StandardCharsets.UTF_8);metadata=metadata.substring(metadata.indexOf("?>")+2);}
        int index=Arrays.asList("mini","scout","pir","gateway").indexOf(name)+1;
        return "<event version=\"2.0\" uid=\"00000000-0000-4000-8018-00000000000"+index+"\" type=\"b-m-p-s-p-loc\" how=\"h-g-i-g-o\" time=\"2026-01-01T00:00:00Z\" start=\"2026-01-01T00:00:00Z\" stale=\"2026-01-01T01:00:00Z\"><point lat=\"0."+index+"234568\" lon=\"-0.2345679\"/><detail>"+metadata+"</detail></event>";
    }
    private String overview(String fields){return "{\"type\":\"Mission\",\"data\":[{\"name\":\"Synthetic\",\"guid\":\""+GUID+"\",\"uids\":["+fields+"]}]}";}
    @Test public void feedIdentityUsesGuidNotMutableLabelAndDoesNotAcceptProviderUrls()throws Exception{
        List<MissionSnapshotInput.Feed> feeds=MissionSnapshotInput.feeds(input(overview("")),()->true);assertEquals(1,feeds.size());
        MissionSnapshotInput.Feed first=feeds.get(0),renamed=new MissionSnapshotInput.Feed("Renamed",GUID);
        assertEquals(first.context(),renamed.context());assertNotEquals(first.context(),new MissionSnapshotInput.Feed(first.name,GUID.replace("171","172")).context());
        assertThrows(IllegalArgumentException.class,()->new MissionSnapshotInput.Feed("Bad\nName",GUID));
        assertThrows(IllegalArgumentException.class,()->new MissionSnapshotInput.Feed("Name","https://invalid.invalid/path"));
        assertThrows(IOException.class,()->MissionSnapshotInput.members(renamed,input(overview("")),()->true));
    }
    @Test public void strictJsonAndMemberIdentityRejectAmbiguousOrIncompleteInput()throws Exception{
        MissionSnapshotInput.Feed feed=new MissionSnapshotInput.Feed("Synthetic",GUID);
        assertEquals(Collections.singleton("ordinary"),MissionSnapshotInput.members(feed,input(overview("{\"data\":\"ordinary\"}")),()->true));
        for(String text:new String[]{overview("{\"data\":\"x\"},{\"data\":\"x\"}"),overview("{\"data\":1}"),overview("").replace("\"name\":","name:"),overview("").replace("\"type\":","\"type\":\"Mission\",\"type\":"),overview("")+" garbage",overview("").replace(GUID,GUID.replace("171","172"))})assertThrows(IOException.class,()->MissionSnapshotInput.members(feed,input(text),()->true));
    }
    @Test public void independentFourTypeEnvelopesDecodeWithoutSdkOrImageHandles()throws Exception{
        StringBuilder xml=new StringBuilder("<events>");Set<String> members=new HashSet<>();
        for(String kind:new String[]{"mini","scout","pir","gateway"}){String event=fixture(kind);xml.append(event);members.add(event.split("uid=\"")[1].split("\"")[0]);}
        List<TmmDraft> values=MissionSnapshotInput.metadata(input(xml+"</events>"),members,()->true);assertEquals(4,values.size());
        for(TmmDraft d:values){assertNull(d.imageRef);assertEquals(1,d.revision);assertTrue(d.comment.contains("åäö"));}
        assertThrows(UnsupportedOperationException.class,()->values.clear());
    }
    @Test public void membershipMustMatchWholeResponseAndOrdinaryEventsAreNotImported()throws Exception{
        String event=fixture("mini"),uid=event.split("uid=\"")[1].split("\"")[0];
        assertEquals(1,MissionSnapshotInput.metadata(input("<events><event uid='ordinary'/>"+event+"</events>"),new HashSet<>(Arrays.asList("ordinary",uid)),()->true).size());
        assertThrows(IOException.class,()->MissionSnapshotInput.metadata(input("<events>"+event+"</events>"),Collections.emptySet(),()->true));
        assertThrows(IOException.class,()->MissionSnapshotInput.metadata(input("<events/>"),Collections.singleton(uid),()->true));
        assertThrows(IOException.class,()->MissionSnapshotInput.metadata(input("<events>"+event+event+"</events>"),Collections.singleton(uid),()->true));
    }
    @Test public void malformedMetadataAndEnvelopeCannotBecomePartialSuccess()throws Exception{
        String event=fixture("mini"),uid=event.split("uid=\"")[1].split("\"")[0];
        for(String bad:new String[]{event.replace("imageIncluded=\"false\"","imageIncluded=\"true\""),event.replace("lat=\"0.1234568\"","lat=\"0.0\""),event.replace("<tmmPublication","<other/><tmmPublication").replace("</detail>","<tmmShareLab/></detail>"),event.replace("<tmmPublication","<wrapper><tmmPublication").replace("</detail>","</wrapper></detail>"),event.replace("schemaVersion=\"1\"","schemaVersion=\"2\""),event.replace("</event>","</event>")+"x"})assertThrows(IOException.class,()->MissionSnapshotInput.metadata(input("<events>"+bad+"</events>"),Collections.singleton(uid),()->true));
    }
    @Test public void xmlSecurityBoundsAndCancellationPreserveCallerOwnership()throws Exception{
        for(String bad:new String[]{"<!DOCTYPE events SYSTEM 'https://invalid.invalid/private'><events/>","<events xmlns='urn:other'/>","<events><!--comment--></events>","<events>"+" ".repeat(65536)+"</events>","<?xml version='1.1'?><events/>"})assertThrows(IOException.class,()->MissionSnapshotInput.metadata(input(bad),Collections.emptySet(),()->true));
        assertThrows(IOException.class,()->MissionSnapshotInput.metadata(input("<events/>"),Collections.emptySet(),()->false));
        final boolean[] closed={false};InputStream in=new ByteArrayInputStream("<events/>".getBytes(StandardCharsets.UTF_8)){public void close(){closed[0]=true;}};
        assertTrue(MissionSnapshotInput.metadata(in,Collections.emptySet(),()->true).isEmpty());assertFalse(closed[0]);
    }
}
