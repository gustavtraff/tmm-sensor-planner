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
public class MissionReadInputTest {
    private static final String VALID="{\"data\":[{\"name\":\"tmm-image162-20261105\",\"guid\":\"00000000-0000-4000-8000-000000000001\",\"uids\":[{}],\"contents\":[{},{}]}]}";
    private MissionReadInput.Summary read(String text)throws IOException{return MissionReadInput.read(MissionReadInput.Feed.IMAGES,new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),()->true);}
    @Test public void onlyStructuralCountsEscapeAndInputStaysCallerOwned()throws Exception{
        AtomicBoolean closed=new AtomicBoolean();InputStream in=new ByteArrayInputStream(VALID.getBytes(StandardCharsets.UTF_8)){
            @Override public void close(){closed.set(true);}
        };
        MissionReadInput.Summary result=MissionReadInput.read(MissionReadInput.Feed.IMAGES,in,()->true);
        assertEquals(1,result.uids);assertEquals(2,result.resources);assertFalse(closed.get());
        assertEquals(2,MissionReadInput.Summary.class.getDeclaredFields().length);
    }
    @Test public void wrongFeedGuidMissingArraysExtraMissionsAndTrailingInputFail(){
        for(String value:new String[]{VALID.replace("tmm-image162-20261105","other"),VALID.replace("4000-8000","0000-0000").replace("000000000001","000000000000"),VALID.replace("00000000-0000-4000-8000-000000000001","1-1-1-1-1"),VALID.replace("\"uids\"","\"other\""),"{\"data\":[]}",VALID+"x","[]"})assertThrows(IOException.class,()->read(value));
    }
    @Test public void responseByteDepthStringAndCountCapsFailClosed()throws Exception{
        assertThrows(IOException.class,()->read(VALID+" ".repeat(MissionReadInput.MAX_BYTES)));
        assertThrows(IOException.class,()->read("[".repeat(17)+"0"+"]".repeat(17)));
        assertThrows(IOException.class,()->read(VALID.replace("\"guid\":","\"ignored\":\""+"x".repeat(4097)+"\",\"guid\":")));
        assertThrows(IOException.class,()->read(VALID.replace("[{}]","["+"{},".repeat(64)+"{}]")));
        String maximum=VALID+" ".repeat(MissionReadInput.MAX_BYTES-VALID.length());assertEquals(2,read(maximum).resources);
    }
    @Test public void boundedReadHandlesZeroProgressAndNeverLeaksProviderMessages()throws Exception{
        byte[] bytes=VALID.getBytes(StandardCharsets.UTF_8);InputStream zero=new ByteArrayInputStream(bytes){
            @Override public synchronized int read(byte[] b,int off,int len){return 0;}
        };assertEquals(2,MissionReadInput.read(MissionReadInput.Feed.IMAGES,zero,()->true).resources);
        IOException failure=assertThrows(IOException.class,()->MissionReadInput.read(MissionReadInput.Feed.IMAGES,new InputStream(){public int read()throws IOException{throw new IOException("SYNTHETIC-PRIVATE-PATH");}},()->true));
        assertNull(failure.getCause());assertFalse(failure.getMessage().contains("PRIVATE"));
    }
    @Test public void cancellationBeforeAndAtEofReturnsNoSummary()throws Exception{
        assertThrows(IOException.class,()->MissionReadInput.read(MissionReadInput.Feed.IMAGES,new ByteArrayInputStream(new byte[0]),()->false));
        AtomicBoolean active=new AtomicBoolean(true);InputStream in=new ByteArrayInputStream(VALID.getBytes(StandardCharsets.UTF_8)){
            @Override public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);if(n<0)active.set(false);return n;}
        };assertThrows(IOException.class,()->MissionReadInput.read(MissionReadInput.Feed.IMAGES,in,active::get));
        Thread.currentThread().interrupt();try{assertThrows(IOException.class,()->read(VALID));}finally{Thread.interrupted();}
    }
    @Test public void malformedUtf8AndTruncationFail(){
        assertThrows(IOException.class,()->MissionReadInput.read(MissionReadInput.Feed.IMAGES,new ByteArrayInputStream(new byte[]{(byte)0xc0,(byte)0xaf}),()->true));
        for(int n=0;n<VALID.length();n++){String truncated=VALID.substring(0,n);assertThrows(IOException.class,()->read(truncated));}
    }
}
