package se.tmm.sensorplanner.plugin.local;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalTrialSessionTest {
    @Test public void connectedConsentAllowsOnlyLocalWorkEvenIfTheNetworkChanges() {
        LocalTrialSession session = new LocalTrialSession();
        assertTrue(session.canUseLocal(true));
        assertTrue(session.canUseLab(true));
        assertFalse(session.canUseLocal(false));
        assertFalse(session.canUseLab(false));
        session.startConnectedTrial();
        assertTrue(session.canUseLocal(false));
        assertTrue(session.canUseLocal(true));
        assertFalse(session.canUseLab(false));
        assertFalse(session.canUseLab(true));
        session.reset();
        assertFalse(session.isConnectedTrial());
        assertFalse(session.canUseLocal(false));
        assertTrue(session.canUseLocal(true));
        assertTrue(session.canUseLab(true));
        assertFalse(new LocalTrialSession().canUseLocal(false));
    }
}
