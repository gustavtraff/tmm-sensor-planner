package se.tmm.sensorplanner.plugin.ingress;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class MissionReadAttemptTest {
    @Test public void deadlineUsesElapsedMonotonicTimeIncludingWrap(){
        MissionReadAttempt task=new MissionReadAttempt(Long.MAX_VALUE-10);
        assertTrue(task.active(Long.MIN_VALUE+20));
        assertFalse(task.active(Long.MIN_VALUE+TimeUnit.SECONDS.toNanos(12)));
        MissionReadAttempt ordinary=new MissionReadAttempt(100);assertFalse(ordinary.active(99));
        assertTrue(ordinary.active(100+TimeUnit.SECONDS.toNanos(12)-1));
        assertFalse(ordinary.active(100+TimeUnit.SECONDS.toNanos(12)));
    }
    @Test public void cancellationBeforeRegistrationStopsEveryLateResource(){
        MissionReadAttempt task=new MissionReadAttempt(0);task.cancel();AtomicInteger aborted=new AtomicInteger();
        FutureTask<Void> worker=new FutureTask<>(()->null),deadline=new FutureTask<>(()->null),guard=new FutureTask<>(()->null);
        task.bindAbort(aborted::incrementAndGet);task.bindWorker(worker);task.bindDeadline(deadline);task.bindGuard(guard);
        assertEquals(1,aborted.get());assertTrue(worker.isCancelled());assertTrue(deadline.isCancelled());assertTrue(guard.isCancelled());assertFalse(task.active(1));
    }
    @Test public void abortFailureCannotPreventWorkerOrTimerCancellation(){
        MissionReadAttempt task=new MissionReadAttempt(0);FutureTask<Void> worker=new FutureTask<>(()->null),timer=new FutureTask<>(()->null);
        task.bindAbort(()->{throw new IllegalStateException("SYNTHETIC-PRIVATE-PROVIDER");});task.bindWorker(worker);task.bindGuard(timer);
        task.cancel();task.cancel();assertTrue(worker.isCancelled());assertTrue(timer.isCancelled());assertFalse(task.active(0));
    }
    @Test public void completionClearsTimersWithoutCancellingWorkerAndResourcesCannotRebind(){
        MissionReadAttempt task=new MissionReadAttempt(0);FutureTask<Void> worker=new FutureTask<>(()->null),timer=new FutureTask<>(()->null);
        task.bindWorker(worker);task.bindDeadline(timer);task.clearTimers();assertTrue(timer.isCancelled());assertFalse(worker.isCancelled());
        assertThrows(IllegalStateException.class,()->task.bindWorker(new FutureTask<>(()->null)));
        task.bindAbort(()->{});assertThrows(IllegalStateException.class,()->task.bindAbort(()->{}));
    }
    @Test public void concurrentCancelAndResourceBindingNeverLeavesLiveWork()throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{for(int n=0;n<100;n++){
            MissionReadAttempt task=new MissionReadAttempt(0);FutureTask<Void> worker=new FutureTask<>(()->null),timer=new FutureTask<>(()->null);AtomicInteger aborted=new AtomicInteger();
            Future<?> register=pool.submit(()->{task.bindAbort(aborted::incrementAndGet);task.bindWorker(worker);task.bindDeadline(timer);});Future<?> cancel=pool.submit(task::cancel);
            register.get(5,TimeUnit.SECONDS);cancel.get(5,TimeUnit.SECONDS);assertTrue(worker.isCancelled());assertTrue(timer.isCancelled());assertTrue(aborted.get()>=1);assertFalse(task.active(1));
        }}finally{pool.shutdownNow();}
    }
}
