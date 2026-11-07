package se.tmm.sensorplanner.plugin.ingress;

import org.junit.Test;
import static org.junit.Assert.*;

public class MissionEndpointTest {
    @Test public void selectedApiPortIsSeparateAndBaseIsCanonical() {
        MissionEndpoint endpoint=MissionEndpoint.fromProfile("TAK.Example.invalid:8089:ssl",9443);
        assertEquals("TAK.Example.invalid:8089:ssl",endpoint.stream);
        assertEquals("https://tak.example.invalid:9443",endpoint.base);
        assertEquals(MissionEndpoint.LAB_BASE,MissionEndpoint.fromProfile("127.0.0.1:8089:ssl",8443).base);
        assertEquals(endpoint.base,MissionEndpoint.checkedBase(endpoint.base));
    }
    @Test public void unsupportedProfilesAndPortsCannotBecomeUrls() {
        for(String stream:new String[]{null,""," host:8089:ssl","host:8089:ssl\n","host:0:ssl","host:65536:ssl","host:8x:ssl","host:8089:tcp","host:8089:SSL","user@host:8089:ssl","host/path:8089:ssl","[::1]:8089:ssl","host:8089:ssl:extra","host_unsafe:8089:ssl"})
            assertThrows(IllegalArgumentException.class,()->MissionEndpoint.fromProfile(stream,8443));
        for(int port:new int[]{-1,0,65536,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->MissionEndpoint.fromProfile("host:8089:ssl",port));
    }
    @Test public void payloadCannotSupplyNoncanonicalOrCredentialBearingAuthority() {
        for(String value:new String[]{null,"","http://host:8443","HTTPS://host:8443","https://HOST:8443","https://host","https://host:0","https://host:65536","https://user:pass@host:8443","https://host:8443/","https://host:8443/Marti","https://host:8443?x=1","https://host:8443#x","https://[::1]:8443","https://host:08443"})
            assertThrows(IllegalArgumentException.class,()->MissionEndpoint.checkedBase(value));
    }
}
