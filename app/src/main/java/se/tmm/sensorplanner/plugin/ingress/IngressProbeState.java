package se.tmm.sensorplanner.plugin.ingress;

import android.os.Bundle;
import java.util.*;

/** Bounded structural observations of fixed synthetic canaries; no values, source authority or files. */
public final class IngressProbeState {
    public static final int MAX_EVENTS = 64, MAX_EXTRA_KEYS = 64;
    private static final List<String> UIDS = Collections.unmodifiableList(Arrays.asList(
            "00000000-0000-4000-8018-000000000001", "00000000-0000-4000-8018-000000000002",
            "00000000-0000-4000-8018-000000000003", "00000000-0000-4000-8018-000000000004"));
    public enum Hint { ABSENT, NULL, STRING_CANARY_UID, STRING_INTERNAL, STRING_STATE_SAVER,
        STRING_TEST_ROUTE, STRING_OTHER, OTHER_TYPE }
    private final List<Observation> events = new ArrayList<>();
    private long generation;
    private boolean active, invalidated, closed;
    private int dropped;

    public synchronized long start() {
        if (closed || generation == Long.MAX_VALUE) throw new IllegalStateException("Probe cannot start");
        generation++; events.clear(); dropped=0; invalidated=false; active=true; return generation;
    }
    public synchronized boolean accepting(long token) { return active && token==generation; }
    public static boolean isCanary(String uid) { return UIDS.contains(uid); }
    public synchronized void record(long token, String uid, Bundle extras) {
        if (!active || token != generation || !isCanary(uid)) return;
        try {
            int total=extras==null?0:extras.size();
            if (events.size() == MAX_EVENTS || total > MAX_EXTRA_KEYS) {
                drop(token); return;
            }
            int known=0;
            for (String key:new String[]{"from","serverFrom","endpointFrom"}) if(extras!=null&&extras.containsKey(key))known++;
            Observation observation=new Observation(UIDS.indexOf(uid),extras!=null,
                    hint(extras,"from"),hint(extras,"serverFrom"),hint(extras,"endpointFrom"),total-known);
            if(known>total||(extras!=null&&extras.size()!=total)){drop(token);return;}
            events.add(observation);
        } catch (RuntimeException unreadable) { drop(token); }
    }
    public synchronized void drop(long token) {
        if (!active || token!=generation) return;
        if(dropped<Integer.MAX_VALUE)dropped++;
        invalidated=true;active=false;
    }
    public synchronized void invalidate(long token) {
        if (closed || token!=generation) return;
        invalidated=true;active=false;
    }
    public synchronized void stop() { active=false; }
    public synchronized void close() { active=false;closed=true;events.clear(); }
    public synchronized Snapshot snapshot() { return new Snapshot(generation,active,invalidated,dropped,events); }

    private static Hint hint(Bundle extras,String key) {
        if(extras==null||!extras.containsKey(key))return Hint.ABSENT;
        Object value=extras.get(key);
        if(value==null)return Hint.NULL;
        if(!(value instanceof String))return Hint.OTHER_TYPE;
        String text=(String)value;
        if(isCanary(text))return Hint.STRING_CANARY_UID;
        if("internal".equals(text))return Hint.STRING_INTERNAL;
        if("StateSaver".equals(text))return Hint.STRING_STATE_SAVER;
        if("127.0.0.1:8089:ssl".equals(text))return Hint.STRING_TEST_ROUTE;
        return Hint.STRING_OTHER;
    }
    public static final class Observation {
        public final int canaryIndex;
        public final boolean extrasPresent;
        public final Hint from,serverFrom,endpointFrom;
        /** Unknown names and values are deliberately not retained. */
        public final int otherKeyCount;
        private Observation(int index,boolean present,Hint from,Hint server,Hint endpoint,int other) {
            this.canaryIndex=index;this.extrasPresent=present;this.from=from;serverFrom=server;endpointFrom=endpoint;otherKeyCount=other;
        }
    }
    public static final class Snapshot {
        public final long generation;
        public final boolean active,invalidated;
        public final int dropped;
        public final List<Observation> events;
        private Snapshot(long generation,boolean active,boolean invalidated,int dropped,List<Observation> events) {
            this.generation=generation;this.active=active;this.invalidated=invalidated;this.dropped=dropped;
            this.events=Collections.unmodifiableList(new ArrayList<>(events));
        }
    }
}
