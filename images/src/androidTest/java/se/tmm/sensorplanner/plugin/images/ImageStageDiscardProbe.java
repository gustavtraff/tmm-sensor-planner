package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Synthetic test-app data only; never opens ATAK, active images or another app's storage. */
@TargetApi(26)
final class ImageStageDiscardProbe {
    private ImageStageDiscardProbe() { }
    static void run(Instrumentation instrumentation,Bundle arguments) {
        Bundle result=new Bundle();
        try {
            check(Build.VERSION.SDK_INT>=26);
            String run=arguments.getString("runId"),phase=arguments.getString("phase");UUID namespace=UUID.fromString(run);
            check(namespace.toString().equals(run) && !namespace.equals(new UUID(0,0)));
            Path parent=instrumentation.getContext().getNoBackupFilesDir().toPath().resolve("synthetic-image-discard-156").resolve(run);
            Path root=parent.resolve("stage"),control=parent.resolve("worker-state.txt");
            if("discard-write-and-kill".equals(phase)) {
                Files.createDirectories(parent);check(!Files.exists(root)&&!Files.exists(control));
                ImageStageStore.Point selected=ImageStageStore.Point.valueOf(arguments.getString("killAfter"));
                check(EnumSet.range(ImageStageStore.Point.DISCARD_MARKED_PENDING,ImageStageStore.Point.DISCARD_DIRECTORY_REMOVED).contains(selected));
                ImageStageStore store=new ImageStageStore(root);store.initializeNew();
                ImageStageStore.Prepared first=ImageStageStore.prepare(payload(instrumentation,1)),second=ImageStageStore.prepare(payload(instrumentation,2));
                store.stage(first);store.stage(second);
                byte[] state=(first.id+"\n"+second.id+"\n"+selected.name()+"\n").getBytes(StandardCharsets.US_ASCII);
                ImageStageStore interrupted=new ImageStageStore(root,point->{
                    if(point==selected) {
                        try(FileOutputStream output=new FileOutputStream(control.toFile())) {output.write(state);output.getFD().sync();}
                        Bundle reached=new Bundle();reached.putString("stagePoint",point.name());instrumentation.sendStatus(73,reached);
                        Process.killProcess(Process.myPid());throw new AssertionError("Controlled test process must terminate");
                    }
                });
                interrupted.discard(interrupted.prepareDiscard(second.id));throw new AssertionError();
            } else if("discard-read".equals(phase)) {
                List<String> state=Files.readAllLines(control,StandardCharsets.US_ASCII);check(state.size()==3);
                UUID first=UUID.fromString(state.get(0)),second=UUID.fromString(state.get(1));
                ImageStageStore.Point point=ImageStageStore.Point.valueOf(state.get(2));
                check(EnumSet.range(ImageStageStore.Point.DISCARD_MARKED_PENDING,ImageStageStore.Point.DISCARD_DIRECTORY_REMOVED).contains(point));
                boolean absent=point==ImageStageStore.Point.DISCARD_DIRECTORY_REMOVED;
                boolean hasOffer=point==ImageStageStore.Point.DISCARD_MARKED_PENDING;
                boolean hasPng=hasOffer||point==ImageStageStore.Point.DISCARD_OFFER_REMOVED;
                ImageStageStore store=new ImageStageStore(root);ImageStageStore.Inspection saved=store.inspect();
                check(saved.ready.equals(Collections.singletonList(first)));
                check(saved.pending.equals(absent?Collections.emptyList():Collections.singletonList(second)) && saved.recoveryRequired()!=absent);
                long bytes=887+(hasOffer?asset(instrumentation,"offer-h2.bin").length:0)+(hasPng?asset(instrumentation,"h2.png").length:0);
                check(saved.logicalBytes==bytes && Arrays.equals(asset(instrumentation,"h1.png"),store.read(first).image.bytes()));
                Path pending=root.resolve("pending-"+second);check(Files.exists(pending)!=absent);
                if(hasOffer)check(Arrays.equals(asset(instrumentation,"offer-h2.bin"),Files.readAllBytes(pending.resolve("offer.bin"))));
                else check(!Files.exists(pending.resolve("offer.bin")));
                if(hasPng)check(Arrays.equals(asset(instrumentation,"h2.png"),Files.readAllBytes(pending.resolve("image.png"))));
                else check(!Files.exists(pending.resolve("image.png")));
                check(store.reconcile(second,payload(instrumentation,2))==(absent?ImageStageStore.Outcome.ABSENT:ImageStageStore.Outcome.PENDING));
                if(!absent) {
                    try {store.stage(ImageStageStore.prepare(payload(instrumentation,2)));throw new AssertionError();}
                    catch(ImageStageStore.Failure expected){check(expected.code==ImageStageStore.Code.RECOVERY_REQUIRED);}
                }
            } else if("discard-success".equals(phase)) {
                ImageStageStore store=new ImageStageStore(parent.resolve("successful"));store.initializeNew();
                ImageStageStore.Prepared first=ImageStageStore.prepare(payload(instrumentation,1)),second=ImageStageStore.prepare(payload(instrumentation,2));
                store.stage(first);store.stage(second);ImageStageStore.DiscardPlan plan=store.prepareDiscard(second.id);
                check(!plan.pending && plan.fileCount==2 && plan.logicalBytes==889);
                check(Arrays.equals(asset(instrumentation,"h2.png"),plan.readyPayload().image.bytes()));
                ImageStageStore.Inspection after=store.discard(plan);
                check(after.ready.equals(Collections.singletonList(first.id)) && after.pending.isEmpty() && after.logicalBytes==887);
                check(Arrays.equals(asset(instrumentation,"h1.png"),new ImageStageStore(parent.resolve("successful")).read(first.id).image.bytes()));
                try{store.discard(plan);throw new AssertionError();}
                catch(ImageStageStore.Failure expected){check(expected.code==ImageStageStore.Code.ALREADY_ATTEMPTED);}
            } else throw new IllegalArgumentException();
            result.putString("result","PASS");result.putString("scope","Synthetic explicit stage discard only; no UI/ingress/association/active-image change");
            instrumentation.finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("result","FAIL");result.putString("errorType",failure.getClass().getSimpleName());
            instrumentation.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private static ReceivedImagePayload payload(Instrumentation instrumentation,int n)throws IOException {
        return ReceivedImagePayload.read(new ByteArrayInputStream(asset(instrumentation,"offer-h"+n+".bin")),new ByteArrayInputStream(asset(instrumentation,"h"+n+".png")));
    }
    private static byte[] asset(Instrumentation instrumentation,String name)throws IOException {
        try(InputStream input=instrumentation.getContext().getAssets().open(name)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
            for(int n;(n=input.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();
        }
    }
    private static void check(boolean valid){if(!valid)throw new AssertionError();}
}
