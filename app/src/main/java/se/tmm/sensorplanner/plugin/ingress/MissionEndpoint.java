package se.tmm.sensorplanner.plugin.ingress;

import java.net.URI;
import java.util.Locale;

/** Caller-selected address only; validation does not establish credentials, permissions or source authority. */
public final class MissionEndpoint {
    public static final String LAB_BASE = "https://127.0.0.1:8443";
    public final String stream, base;
    private MissionEndpoint(String stream, String base) { this.stream=stream;this.base=base; }
    public static MissionEndpoint fromProfile(String stream, int apiPort) {
        try {
            if(stream==null||stream.length()>512||!stream.equals(stream.trim()))throw invalid();
            String[] parts=stream.split(":",-1);
            if(parts.length!=3||!"ssl".equals(parts[2])||!parts[1].matches("[0-9]{1,5}"))throw invalid();
            int port=Integer.parseInt(parts[1]);if(port<1||port>65535||apiPort<1||apiPort>65535)throw invalid();
            if(!parts[0].matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}"))throw invalid();
            String base=new URI("https",null,parts[0].toLowerCase(Locale.ROOT),apiPort,null,null,null).toASCIIString();
            return new MissionEndpoint(stream,checkedBase(base));
        }catch(Exception unsupported){throw invalid();}
    }
    public static String checkedBase(String value) {
        try {
            if(value==null||value.length()>512)throw invalid();URI u=new URI(value);
            if(!"https".equals(u.getScheme())||u.getHost()==null||u.getHost().contains(":")||u.getHost().contains("[")
                    ||u.getPort()<1||u.getPort()>65535||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null
                    ||(u.getRawPath()!=null&&!u.getRawPath().isEmpty()))throw invalid();
            String canonical=new URI("https",null,u.getHost().toLowerCase(Locale.ROOT),u.getPort(),null,null,null).toASCIIString();
            if(!canonical.equals(value))throw invalid();return value;
        }catch(Exception unsupported){throw invalid();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Unsupported configured TLS endpoint");}
}
