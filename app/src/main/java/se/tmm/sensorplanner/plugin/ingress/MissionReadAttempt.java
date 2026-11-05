package se.tmm.sensorplanner.plugin.ingress;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** One non-reusable read lifetime; late resource registration cannot escape cancellation. */
public final class MissionReadAttempt {
    private final long startedNanos;
    private volatile boolean cancelled;
    private Runnable abort;
    private Future<?> worker,deadline,guard;
    public MissionReadAttempt(long startedNanos){this.startedNanos=startedNanos;}
    public boolean active(long nowNanos){long elapsed=nowNanos-startedNanos;return !cancelled&&elapsed>=0&&elapsed<TimeUnit.SECONDS.toNanos(12);}
    public void bindAbort(Runnable value){
        if(value==null)throw new NullPointerException();
        boolean stop;synchronized(this){if(abort!=null)throw new IllegalStateException();abort=value;stop=cancelled;}
        if(stop)abort(value);
    }
    public void bindWorker(Future<?> value){bind(value,0);}
    public void bindDeadline(Future<?> value){bind(value,1);}
    public void bindGuard(Future<?> value){bind(value,2);}
    private void bind(Future<?> value,int slot){
        if(value==null)throw new NullPointerException();boolean stop;
        synchronized(this){
            if((slot==0?worker:slot==1?deadline:guard)!=null)throw new IllegalStateException();
            if(slot==0)worker=value;else if(slot==1)deadline=value;else guard=value;stop=cancelled;
        }
        if(stop)cancel(value,slot==0);
    }
    public void clearTimers(){Future<?> d,g;synchronized(this){d=deadline;g=guard;}cancel(d,false);cancel(g,false);}
    public void cancel(){
        Runnable a;Future<?> w;
        synchronized(this){cancelled=true;a=abort;w=worker;}
        abort(a);cancel(w,true);clearTimers();
    }
    private static void abort(Runnable value){if(value!=null)try{value.run();}catch(RuntimeException ignored){}}
    private static void cancel(Future<?> value,boolean interrupt){if(value!=null)try{value.cancel(interrupt);}catch(RuntimeException ignored){}}
}
