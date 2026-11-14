package se.tmm.sensorplanner.tools;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import com.atakmap.android.maps.*;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.cot.OpaqueHandler;
import com.atakmap.coremap.cot.event.*;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.json.JSONObject;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.plugin.publication.*;

/** Explicit isolated developer-host measurement only; never a production update caller. */
public final class GeometrySdkInstrumentation extends Instrumentation {
    private static final String UID="00000000-0000-4000-8189-000000000001";
    private Bundle arguments;private MapView map;private MapItem owned;private File output;
    private final JSONObject proof=new JSONObject();
    @Override public void onCreate(Bundle args){arguments=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            require(arguments!=null&&"G1-G5-20261114".equals(arguments.getString("confirmSyntheticLab")));
            require(getTargetContext().getPackageName().equals("com.atakmap.app.civ"));
            require(getTargetContext().getPackageManager().getPackageInfo("com.atakmap.app.civ",0).versionName.contains("5.6.0.24"));
            require(Settings.Global.getInt(getTargetContext().getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)==1);
            File fresh=getTargetContext().getExternalFilesDir("tmm-geometry189");require(fresh!=null&&fresh.isDirectory()&&fresh.list()!=null&&fresh.list().length==0);output=fresh;
            Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("com.atakmap.app.civ");require(launch!=null);startActivitySync(launch);
            proof.put("stage","host-startup");final boolean[] ready={false};
            for(int i=0;i<240;i++){runOnMainSync(()->{map=MapView.getMapView();ready[0]=map!=null&&com.atakmap.android.cot.CotMapComponent.getInstance()!=null&&map.getSelfMarker()!=null;});if(ready[0])break;Thread.sleep(500);}
            require(ready[0]);final Throwable[] failed={null};
            runOnMainSync(()->{try{measure();}catch(Throwable error){failed[0]=error;}finally{cleanup();}});
            if(failed[0]!=null)throw new IOException("SDK measurement failed",failed[0]);
            require(proof.optBoolean("removedOwnedFixtureFromLiveMap"));
            proof.put("passed",true);result.putString("result","PASS");
        }catch(Throwable error){try{proof.put("passed",false);proof.put("failureClass",error.getClass().getName());if(error.getCause()!=null)proof.put("causeClass",error.getCause().getClass().getName());}catch(Exception ignored){}result.putString("result","FAIL");}
        finally{try{if(output!=null)write("proof.json",proof.toString(2));}catch(Exception ignored){}finish(Activity.RESULT_OK,result);}
    }
    private void measure()throws Exception {
        proof.put("stage","fixture-admission");require(map.getRootGroup().deepFindUID(UID)==null);
        TmmDraft base=draft(0.323456789012,-0.234567891234,123.45678901234567,0);
        TmmDraft bearing=draft(base.position.latitude,base.position.longitude,234.56789012345678,1);
        TmmDraft moved=draft(0.333456789012,-0.244567891234,234.56789012345678,2);
        CotEvent initial=event(base);proof.put("fixtureMethod","public Marker and root-group add");
        Marker marker=new Marker(new GeoPoint(initial.getCotPoint().getLat(),initial.getCotPoint().getLon()),UID);
        marker.setType(initial.getType());marker.setMetaString("callsign",base.displayId);owned=marker;
        metadata(initial);map.getRootGroup().addItem(marker);com.atakmap.android.user.PlacePointTool.updateCallsign(marker);
        require(map.getRootGroup().deepFindUID(UID)==owned&&count()==1);
        proof.put("stage","base-readback");CotEvent before=export();require(matches(before,base));save("before.cot",before);
        proof.put("stage","bearing-readback");metadata(event(bearing));persist();CotEvent afterBearing=export();
        require(matches(afterBearing,bearing));require(before.getCotPoint().getLat()==afterBearing.getCotPoint().getLat()&&before.getCotPoint().getLon()==afterBearing.getCotPoint().getLon());
        proof.put("bearingOnlyPreservedOuterPoint",true);save("bearing.cot",afterBearing);
        proof.put("stage","point-before-payload");((PointMapItem)owned).setPoint(new GeoPoint(moved.position.latitude,moved.position.longitude));
        CotEvent partial=export();save("point-before-payload.cot",partial);boolean refused=false;
        try{PublicationCot.read(partial);}catch(RuntimeException mismatch){refused=true;}require(refused);proof.put("pointOnlyMismatchRefused",true);
        CotEvent candidate=event(moved);metadata(candidate);
        CotEvent raw=export();save("raw-point-and-payload.cot",raw);proof.put("rawPointCoherentInMemory",matches(raw,moved));
        // Use the established outgoing point projection, not raw private-coordinate precision.
        ((PointMapItem)owned).setPoint(new GeoPoint(candidate.getCotPoint().getLat(),candidate.getCotPoint().getLon()));
        proof.put("stage","canonical-readback");persist();CotEvent after=export();require(matches(after,moved));
        require(map.getRootGroup().deepFindUID(UID)==owned&&count()==1);save("moved.cot",after);
        proof.put("sameMapItemAndUuid",true);proof.put("exactMetadataAndRoundedPoint",true);
        proof.put("uid",UID);proof.put("persistRequested",true);proof.put("deliveryVerified",false);
    }
    private void cleanup(){
        try{if(owned!=null&&map.getRootGroup().deepFindUID(UID)==owned){owned.removeFromGroup();proof.put("removedOwnedFixtureFromLiveMap",map.getRootGroup().deepFindUID(UID)==null);}}
        catch(Exception error){try{proof.put("cleanupUnconfirmed",true);}catch(Exception ignored){}}
    }
    private int count(){int n=0;for(MapItem item:map.getRootGroup().getItemsRecursive())if(UID.equals(item.getUID()))n++;return n;}
    private CotEvent export(){CotEvent value=CotEventFactory.createCotEvent(owned);require(value!=null);return value;}
    private void metadata(CotEvent event){OpaqueHandler.getInstance().toMarkerMetadata(owned,event,event.getDetail().getFirstChildByName(0,PublicationCot.TAG));}
    private void persist(){owned.refresh(map.getMapEventDispatcher(),null,GeometrySdkInstrumentation.class);owned.persist(map.getMapEventDispatcher(),null,GeometrySdkInstrumentation.class);}
    private static TmmDraft draft(double latitude,double longitude,double bearing,int step){
        Instant time=Instant.parse("2026-01-01T00:00:00.123456789Z");
        return new TmmDraft(UUID.fromString(UID),"TMM-GEOMETRY189",DeviceType.PIR,DeviceStatus.PLANNED,"SYNTHETIC LAB",time,time.plusSeconds(10+step),null,"Approved G1-G5 SDK measurement",new Position(latitude,longitude),bearing,null,1+step);
    }
    private static CotEvent event(TmmDraft draft){try(PublicationPreparationReview review=new PublicationPreparationReview(draft)){return PublicationCot.create(review.prepare(draft),draft.displayId,new CoordinatedTime());}}
    private static boolean matches(CotEvent event,TmmDraft draft){try(PublicationPreparationReview review=new PublicationPreparationReview(draft)){return PublicationCot.matches(event,review.prepare(draft));}}
    private void save(String name,CotEvent value)throws IOException{write(name,value.toString());}
    private void write(String name,String text)throws IOException{try(FileOutputStream stream=new FileOutputStream(new File(output,name))){stream.write(text.getBytes(StandardCharsets.UTF_8));stream.getFD().sync();}}
    private static void require(boolean value){if(!value)throw new IllegalStateException("Synthetic geometry measurement refused");}
}
