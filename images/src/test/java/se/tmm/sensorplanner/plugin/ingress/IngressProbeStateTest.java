package se.tmm.sensorplanner.plugin.ingress;

import android.os.Bundle;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
public class IngressProbeStateTest {
    private static final String PIR="00000000-0000-4000-8018-000000000003";

    @Test public void onlyExactFourCanariesAreObservedAndNoProfileValuesSurvive() {
        IngressProbeState state=new IngressProbeState();long token=state.start();
        Bundle b=new Bundle();b.putString("from","SYNTHETIC-PRIVATE-ACCOUNT");b.putString("serverFrom","https://SYNTHETIC-PRIVATE-URI");
        b.putString("endpointFrom","SYNTHETIC-TOKEN");b.putString("SYNTHETIC-PRIVATE-KEY","SYNTHETIC-PRIVATE-VALUE");
        for(String uid:new String[]{null,"",PIR+"extra","00000000-0000-4000-8018-000000000005","00000000-0000-4000-8014-000000000003"})state.record(token,uid,b);
        assertTrue(state.snapshot().events.isEmpty());
        for(int i=1;i<=4;i++)state.record(token,String.format(Locale.ROOT,"00000000-0000-4000-8018-%012d",i),b);
        IngressProbeState.Snapshot saved=state.snapshot();assertEquals(4,saved.events.size());
        for(int i=0;i<4;i++){
            IngressProbeState.Observation event=saved.events.get(i);assertEquals(i,event.canaryIndex);
            assertEquals(IngressProbeState.Hint.STRING_OTHER,event.from);assertEquals(IngressProbeState.Hint.STRING_OTHER,event.serverFrom);
            assertEquals(IngressProbeState.Hint.STRING_OTHER,event.endpointFrom);assertEquals(1,event.otherKeyCount);
        }
        b.clear();b.putString("from",PIR);
        assertEquals(IngressProbeState.Hint.STRING_OTHER,saved.events.get(0).from);
        assertThrows(UnsupportedOperationException.class,()->saved.events.clear());
        state.start();assertEquals(4,saved.events.size());assertTrue(state.snapshot().events.isEmpty());
    }

    @Test public void absentNullFixedLiteralsAndOtherTypesAreDistinctNotAuthority() {
        IngressProbeState state=new IngressProbeState();long token=state.start();
        state.record(token,PIR,null);state.record(token,PIR,new Bundle());
        assertFalse(state.snapshot().events.get(0).extrasPresent);assertTrue(state.snapshot().events.get(1).extrasPresent);
        assertEquals(IngressProbeState.Hint.ABSENT,state.snapshot().events.get(0).from);
        String[] values={null,PIR,"internal","StateSaver","127.0.0.1:8089:ssl","other"};
        IngressProbeState.Hint[] kinds={IngressProbeState.Hint.NULL,IngressProbeState.Hint.STRING_CANARY_UID,
                IngressProbeState.Hint.STRING_INTERNAL,IngressProbeState.Hint.STRING_STATE_SAVER,
                IngressProbeState.Hint.STRING_TEST_ROUTE,IngressProbeState.Hint.STRING_OTHER};
        for(int i=0;i<values.length;i++){
            Bundle b=new Bundle();b.putString("from",values[i]);b.putString("serverFrom",values[i]);b.putString("endpointFrom",values[i]);
            state.record(token,PIR,b);IngressProbeState.Observation e=state.snapshot().events.get(i+2);
            assertEquals(kinds[i],e.from);assertEquals(kinds[i],e.serverFrom);assertEquals(kinds[i],e.endpointFrom);assertEquals(0,e.otherKeyCount);
        }
        Bundle other=new Bundle();other.putSerializable("from",new NoStringification());other.putInt("serverFrom",42);other.putBundle("endpointFrom",new Bundle());
        state.record(token,PIR,other);IngressProbeState.Observation e=state.snapshot().events.get(8);
        assertEquals(IngressProbeState.Hint.OTHER_TYPE,e.from);assertEquals(IngressProbeState.Hint.OTHER_TYPE,e.serverFrom);assertEquals(IngressProbeState.Hint.OTHER_TYPE,e.endpointFrom);
    }

    @Test public void oldCallbacksStopCloseAndRestartCannotResumeOrPolluteNewSession() {
        IngressProbeState state=new IngressProbeState();long first=state.start();state.record(first,PIR,null);
        state.stop();assertFalse(state.accepting(first));state.record(first,PIR,null);assertEquals(1,state.snapshot().events.size());assertFalse(state.snapshot().active);
        long second=state.start();assertFalse(state.accepting(first));assertTrue(state.accepting(second));state.record(first,PIR,null);assertTrue(state.snapshot().events.isEmpty());state.record(second,PIR,null);
        assertEquals(1,state.snapshot().events.size());state.close();assertTrue(state.snapshot().events.isEmpty());
        state.record(second,PIR,null);assertTrue(state.snapshot().events.isEmpty());assertThrows(IllegalStateException.class,state::start);
    }

    @Test public void guardLossAndDropsInvalidateUntilExplicitNewStart() {
        IngressProbeState state=new IngressProbeState();long token=state.start();state.record(token,PIR,null);state.drop(token);
        assertFalse(state.snapshot().active);assertTrue(state.snapshot().invalidated);assertEquals(1,state.snapshot().dropped);
        state.record(token,PIR,null);assertEquals(1,state.snapshot().events.size());
        long fresh=state.start();assertTrue(state.snapshot().active);assertFalse(state.snapshot().invalidated);assertEquals(0,state.snapshot().dropped);
        state.drop(token);assertTrue(state.snapshot().active);state.stop();state.invalidate(fresh);assertTrue(state.snapshot().invalidated);
    }

    @Test public void capacityNeverEvictsEvidenceOrPretendsCompleteCoverage() {
        IngressProbeState state=new IngressProbeState();long token=state.start();
        for(int i=0;i<IngressProbeState.MAX_EVENTS;i++)state.record(token,PIR,null);
        assertEquals(IngressProbeState.MAX_EVENTS,state.snapshot().events.size());assertTrue(state.snapshot().active);
        state.record(token,PIR,null);assertEquals(IngressProbeState.MAX_EVENTS,state.snapshot().events.size());
        assertTrue(state.snapshot().invalidated);assertFalse(state.snapshot().active);assertEquals(1,state.snapshot().dropped);
        long next=state.start();Bundle oversized=new Bundle();for(int i=0;i<=IngressProbeState.MAX_EXTRA_KEYS;i++)oversized.putString("synthetic"+i,"not retained");
        state.record(next,PIR,oversized);assertTrue(state.snapshot().events.isEmpty());assertTrue(state.snapshot().invalidated);assertEquals(1,state.snapshot().dropped);
        long max=state.start();oversized.remove("synthetic64");state.record(max,PIR,oversized);
        assertEquals(IngressProbeState.MAX_EXTRA_KEYS,state.snapshot().events.get(0).otherKeyCount);assertTrue(state.snapshot().active);
    }

    @Test public void concurrentObservationIsBoundedAndSnapshotsRemainStable() throws Exception {
        IngressProbeState state=new IngressProbeState();long token=state.start();ExecutorService threads=Executors.newFixedThreadPool(4);
        try {List<Callable<Void>> tasks=new ArrayList<>();for(int i=0;i<32;i++)tasks.add(()->{state.record(token,PIR,new Bundle());return null;});for(Future<Void> result:threads.invokeAll(tasks))result.get();}
        finally{threads.shutdownNow();}
        IngressProbeState.Snapshot before=state.snapshot();assertEquals(32,before.events.size());state.stop();state.start();assertEquals(32,before.events.size());assertTrue(state.snapshot().events.isEmpty());
    }

    @Test public void generationCannotWrapToAcceptAncientCallbacks() throws Exception {
        IngressProbeState state=new IngressProbeState();Field generation=IngressProbeState.class.getDeclaredField("generation");generation.setAccessible(true);generation.setLong(state,Long.MAX_VALUE);
        assertThrows(IllegalStateException.class,state::start);assertFalse(state.snapshot().active);
    }
    private static final class NoStringification implements Serializable {
        private static final long serialVersionUID=1;
        @Override public String toString(){throw new AssertionError("Observation must not stringify values");}
    }
}
