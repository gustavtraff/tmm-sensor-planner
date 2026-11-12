package se.tmm.sensorplanner.plugin.ingress;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import se.tmm.sensorplanner.plugin.images.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MissionImageFetchTest {
    private static final MissionSnapshotInput.Feed FEED=new MissionSnapshotInput.Feed("Synthetic images","00000000-0000-4000-8000-000000000206");
    private static byte[] bytes()throws Exception {
        try(InputStream in=MissionImageFetchTest.class.getResourceAsStream("/h1.tmmi")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();
        }
    }
    private static String hash(byte[] b)throws Exception {StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",v&255));return s.toString();}
    private static JSONObject item(String hash,long length)throws Exception {
        return new JSONObject().put("data",new JSONObject().put("name","synthetic.tmmi").put("hash",hash).put("size",length).put("mimeType","application/octet-stream"));
    }
    private static byte[] overview(JSONArray resources)throws Exception {
        return new JSONObject().put("type","Mission").put("data",new JSONArray().put(new JSONObject().put("name",FEED.name).put("guid",FEED.guid).put("contents",resources))).toString().getBytes(StandardCharsets.UTF_8);
    }
    private static MissionImageInput.Resource selected(byte[] b)throws Exception {
        return MissionImageInput.read(FEED,new ByteArrayInputStream(overview(new JSONArray().put(item(hash(b),b.length)))),()->true).get(0);
    }
    private static final class Http implements MissionImageFetch.Exchange {
        final byte[] body;byte[] before,after;int calls;final List<String> paths=new ArrayList<>();
        Http(byte[] body)throws Exception{this.body=body;before=after=overview(new JSONArray().put(item(hash(body),body.length)));}
        @Override public <T>T get(String path,String accept,int limit,MissionImageFetch.Decoder<T> decode)throws IOException {
            calls++;paths.add(path);byte[] actual=calls==1?before:calls==2?body:after;
            assertEquals(calls==2?"application/octet-stream":"application/json",accept);
            assertEquals(calls==2?body.length:MissionImageInput.MAX_BYTES,limit);
            return decode.read(new ByteArrayInputStream(actual));
        }
    }
    @Test public void exactIndependentContainerUsesOverviewContentOverviewAndKeepsClaims()throws Exception {
        byte[] b=bytes();Http http=new Http(b);MissionImageInput.Resource r=selected(b);
        ReceivedImagePayload payload=MissionImageFetch.read(FEED,r,http,()->true);
        assertEquals(Arrays.asList("/api/missions/guid/"+FEED.guid,"/sync/content?hash="+hash(b),"/api/missions/guid/"+FEED.guid),http.paths);
        assertEquals(ImageContainerCodec.read(new ByteArrayInputStream(b)).offer.metadata.uid,payload.offer.metadata.uid);
        assertArrayEquals(ImageContainerCodec.read(new ByteArrayInputStream(b)).image.bytes(),payload.image.bytes());
    }
    @Test public void absentOrChangedMembershipCannotAuthorizeReadableBytes()throws Exception {
        byte[] b=bytes();MissionImageInput.Resource r=selected(b);
        Http absent=new Http(b);absent.before=overview(new JSONArray());
        assertThrows(IOException.class,()->MissionImageFetch.read(FEED,r,absent,()->true));assertEquals(1,absent.calls);
        Http revoked=new Http(b);revoked.after=overview(new JSONArray());
        assertThrows(IOException.class,()->MissionImageFetch.read(FEED,r,revoked,()->true));assertEquals(3,revoked.calls);
        Http changed=new Http(b);changed.before=overview(new JSONArray().put(item(hash(b),b.length+1)));
        assertThrows(IOException.class,()->MissionImageFetch.read(FEED,r,changed,()->true));assertEquals(1,changed.calls);
    }
    @Test public void outerHashTruncationAndTrailingContentRefuseEvenWhenInnerPngIsValid()throws Exception {
        byte[] b=bytes();MissionImageInput.Resource r=selected(b);
        byte[] wrong=new byte[b.length];Arrays.fill(wrong,(byte)1);
        MissionImageInput.Resource wrongHash=MissionImageInput.read(FEED,new ByteArrayInputStream(overview(new JSONArray().put(item(hash(wrong),b.length)))),()->true).get(0);
        Http h=new Http(b);h.before=overview(new JSONArray().put(item(wrongHash.hash,b.length)));
        assertThrows(IOException.class,()->MissionImageFetch.read(FEED,wrongHash,h,()->true));
        for(byte[] bad:new byte[][]{Arrays.copyOf(b,b.length-1),Arrays.copyOf(b,b.length+1)}) {
            Http badHttp=new Http(bad);MissionImageInput.Resource badSelection=selected(bad);
            assertThrows(IOException.class,()->MissionImageFetch.read(FEED,badSelection,badHttp,()->true));assertEquals(2,badHttp.calls);
        }
    }
    @Test public void wrongGuidDuplicateHashesKeysAndOversizedInputRefuse()throws Exception {
        byte[] b=bytes();byte[] good=overview(new JSONArray().put(item(hash(b),b.length)));
        List<byte[]> invalid=new ArrayList<>();
        invalid.add(new String(good,StandardCharsets.UTF_8).replace(FEED.guid,"00000000-0000-4000-8000-000000000207").getBytes(StandardCharsets.UTF_8));
        invalid.add(overview(new JSONArray().put(item(hash(b),b.length)).put(item(hash(b),b.length))));
        invalid.add(new String(good,StandardCharsets.UTF_8).replace("\"type\":\"Mission\"","\"type\":\"Mission\",\"type\":\"Mission\"").getBytes(StandardCharsets.UTF_8));
        invalid.add(Arrays.copyOf(good,MissionImageInput.MAX_BYTES+1));
        invalid.add(overview(new JSONArray().put(item(hash(b),b.length).getJSONObject("data").put("data",1))));
        for(byte[] value:invalid)assertThrows(IOException.class,()->MissionImageInput.read(FEED,new ByteArrayInputStream(value),()->true));
    }
    @Test public void limitsTypesAndNonContainersAreNotSilentlyTruncated()throws Exception {
        JSONArray all=new JSONArray();for(int n=0;n<256;n++)all.put(item(String.format("%064x",n),134));
        List<MissionImageInput.Resource> list=MissionImageInput.read(FEED,new ByteArrayInputStream(overview(all)),()->true);assertEquals(256,list.size());assertThrows(UnsupportedOperationException.class,()->list.clear());
        all.put(item(String.format("%064x",256),134));assertThrows(IOException.class,()->MissionImageInput.read(FEED,new ByteArrayInputStream(overview(all)),()->true));
        byte[] b=bytes();JSONObject png=item(hash(b),b.length);png.getJSONObject("data").put("mimeType","image/png");
        assertTrue(MissionImageInput.read(FEED,new ByteArrayInputStream(overview(new JSONArray().put(png))),()->true).isEmpty());
        for(Object bad:new Object[]{"900",900.5,0,-1,ImageContainerCodec.MAX_BYTES+1}) {
            JSONObject value=item(hash(b),b.length);value.getJSONObject("data").put("size",bad);
            assertThrows(IOException.class,()->MissionImageInput.read(FEED,new ByteArrayInputStream(overview(new JSONArray().put(value))),()->true));
        }
    }
    @Test public void cancellationAndDeadlinesRefuseAndDoNotChangeMetadataBudget()throws Exception {
        byte[] b=bytes();Http h=new Http(b);assertThrows(IOException.class,()->MissionImageFetch.read(FEED,selected(b),h,()->false));assertEquals(0,h.calls);
        long start=10;assertFalse(new MissionReadAttempt(start).active(start+TimeUnit.SECONDS.toNanos(12)));
        MissionReadAttempt image=MissionReadAttempt.forImage(start);assertTrue(image.active(start+TimeUnit.SECONDS.toNanos(59)));assertFalse(image.active(start+TimeUnit.SECONDS.toNanos(60)));image.cancel();assertFalse(image.active(start+1));
        assertEquals(65536,MissionReadInput.MAX_BYTES);
    }
}
