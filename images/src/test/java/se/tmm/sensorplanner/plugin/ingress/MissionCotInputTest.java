package se.tmm.sensorplanner.plugin.ingress;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
public class MissionCotInputTest {
    private static final String UID="00000000-0000-4000-8018-000000000001";
    private static final String EVENT="<event uid='"+UID+"' type='b-m-p-s-p-loc'><point lat='0' lon='0'/><detail><contact callsign='synthetic &amp; &quot;'/><tmmPublication commentUtf8=''/></detail></event>";
    private MissionCotInput.Batch read(String text)throws IOException{return MissionCotInput.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),()->true);}
    @Test public void extractsOnlyFixedCanariesAndCountsOrdinaryEvents()throws Exception{
        MissionCotInput.Batch result=read("<?xml version='1.0' encoding='UTF-8' standalone='yes'?><events><event uid='ordinary'/>"+EVENT+"</events>");
        assertEquals(2,result.events);assertEquals(1,result.canaries.size());
        String extracted=result.canaries.get(0);assertTrue(extracted.contains("&amp; &quot;"));assertFalse(extracted.contains("ordinary"));
        assertThrows(UnsupportedOperationException.class,()->result.canaries.clear());
        assertEquals(0,read("<events/>").events);
    }
    @Test public void framingDuplicatesAndNestedEventsFailClosed(){
        for(String value:new String[]{EVENT,"<other/>","<events uid='x'/>","<events>text</events>","<events>"+EVENT+EVENT+"</events>","<events><other/></events>","<events><event uid='x'><event uid='y'/></event></events>","<events>"+EVENT+"</events><events/>","<events>"+EVENT})assertThrows(IOException.class,()->read(value));
    }
    @Test public void byteEventDepthAndAttributeLimitsFailClosed(){
        assertThrows(IOException.class,()->read("<events>"+" ".repeat(MissionReadInput.MAX_BYTES)+"</events>"));
        StringBuilder events=new StringBuilder("<events>");for(int n=0;n<65;n++)events.append("<event uid='").append(n).append("'/>");
        assertThrows(IOException.class,()->read(events+"</events>"));
        assertThrows(IOException.class,()->read("<events><event uid='x'>"+"<x>".repeat(15)+"</x>".repeat(15)+"</event></events>"));
        assertThrows(IOException.class,()->read("<events><event uid='x' label='"+"x".repeat(4097)+"'/></events>"));
        assertThrows(IOException.class,()->read("<events><event uid='x' "+java.util.stream.IntStream.range(0,64).mapToObj(n->"a"+n+"='x' ").collect(java.util.stream.Collectors.joining())+"/></events>"));
    }
    @Test public void dtdEntitiesNamespacesCommentsAndInstructionsFailWithoutResolution(){
        for(String value:new String[]{"<!DOCTYPE events SYSTEM 'file:///unreadable-synthetic-test'><events/>","<!DOCTYPE events [<!ENTITY x SYSTEM 'https://invalid.invalid/private'>]><events>&x;</events>","<events xmlns='urn:other'/>","<events><!--comment--></events>","<events><![CDATA[test]]></events>","<?unsupported test?><events/>","<?xml version='1.1'?><events/>","<?xml version='1.0' encoding='ISO-8859-1'?><events/>","<events>&unknown;</events>"})assertThrows(IOException.class,()->read(value));
    }
    @Test public void malformedUtf8AndCancellationReturnNoBatch()throws Exception{
        assertThrows(IOException.class,()->MissionCotInput.read(new ByteArrayInputStream(new byte[]{(byte)0xc0,(byte)0xaf}),()->true));
        assertThrows(IOException.class,()->MissionCotInput.read(new ByteArrayInputStream(new byte[0]),()->false));
        AtomicBoolean active=new AtomicBoolean(true);InputStream in=new ByteArrayInputStream("<events/>".getBytes(StandardCharsets.UTF_8)){
            @Override public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);if(n<0)active.set(false);return n;}
        };assertThrows(IOException.class,()->MissionCotInput.read(in,active::get));
    }
    @Test public void inputOwnershipAndPrivateFailureMessagesArePreserved()throws Exception{
        AtomicBoolean closed=new AtomicBoolean();InputStream in=new ByteArrayInputStream("<events/>".getBytes(StandardCharsets.UTF_8)){
            @Override public void close(){closed.set(true);}
        };assertEquals(0,MissionCotInput.read(in,()->true).events);assertFalse(closed.get());
        IOException error=assertThrows(IOException.class,()->MissionCotInput.read(new InputStream(){public int read()throws IOException{throw new IOException("PRIVATE-SYNTHETIC-PROFILE");}},()->true));
        assertNull(error.getCause());assertFalse(error.getMessage().contains("PRIVATE"));
    }
}
