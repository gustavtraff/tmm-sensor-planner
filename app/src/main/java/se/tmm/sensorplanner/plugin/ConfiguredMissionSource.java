package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import com.atakmap.comms.*;
import java.util.*;
import se.tmm.sensorplanner.plugin.ingress.MissionEndpoint;

/** SDK registry bindings only; never reads profile usernames/passwords or copies certificate material. */
@TargetApi(26)
final class ConfiguredMissionSource {
    private ConfiguredMissionSource() { }
    static List<String> profiles() {
        if(!MissionSnapshotHttp.defaultProvider())return Collections.emptyList();
        TAKServer[] all=TAKServerListener.getInstance().getServers();
        if(all==null||all.length>32)throw new IllegalStateException("Server registry unavailable or oversized");
        Set<String> seen=new HashSet<>();List<String> result=new ArrayList<>();
        for(TAKServer server:all){
            if(server==null||!server.isEnabled()||!server.isStream()||!server.isConnected())continue;
            String stream=server.getConnectString();
            try{MissionEndpoint.fromProfile(stream,8443);}catch(IllegalArgumentException unsupported){continue;}
            if(!seen.add(stream))throw new IllegalStateException("Ambiguous configured source");
            result.add(stream);
        }
        Collections.sort(result);return Collections.unmodifiableList(result);
    }
    /** Null when the registry cannot be read; the trial treats that as a refusal. */
    static List<se.tmm.sensorplanner.core.ConfiguredPublicationTrial.Stream> trialStreams() {
        try{
            TAKServer[] all=TAKServerListener.getInstance().getServers();
            if(all==null||all.length>32)return null;
            List<se.tmm.sensorplanner.core.ConfiguredPublicationTrial.Stream> result=new ArrayList<>();
            for(TAKServer server:all){
                if(server==null)return null;
                if(server.isStream())result.add(new se.tmm.sensorplanner.core.ConfiguredPublicationTrial.Stream(server.isEnabled(),server.isConnected(),server.getConnectString()));
            }
            return result;
        }catch(RuntimeException unavailable){return null;}
    }
    static boolean connected(String stream) {
        try{return profiles().contains(stream);}catch(RuntimeException unavailable){return false;}
    }
}
