package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import se.tmm.sensorplanner.core.ImageOffer;
import se.tmm.sensorplanner.core.ImageOfferCodec;
import se.tmm.sensorplanner.images.ImageTransferPng;

/** Standalone synthetic app-sandbox probe; never starts ATAK or opens another application's data. */
@TargetApi(26)
public final class ImageStageProbe {
    private ImageStageProbe() { }
    public static void run(Instrumentation instrumentation, Bundle arguments) {
        Bundle result=new Bundle();
        try {
            check(Build.VERSION.SDK_INT>=26);
            String run=arguments.getString("runId"),phase=arguments.getString("phase");
            UUID id=UUID.fromString(run);check(id.toString().equals(run) && !id.equals(new UUID(0,0)));
            Path parent=instrumentation.getContext().getNoBackupFilesDir().toPath().resolve("synthetic-image-stage-150").resolve(run);
            Path root=parent.resolve("stage"),control=parent.resolve("worker-state.txt");
            if("write-and-kill".equals(phase)) {
                Files.createDirectories(parent);check(!Files.exists(root) && !Files.exists(control));
                ImageStageStore store=new ImageStageStore(root);store.initializeNew();
                ImageStageStore.Prepared ready=ImageStageStore.prepare(payload(instrumentation,1)),pending=ImageStageStore.prepare(payload(instrumentation,2));
                store.stage(ready);
                ImageStageStore.Point selected=ImageStageStore.Point.valueOf(arguments.getString("killAfter","PNG_SYNCED"));
                check(selected!=ImageStageStore.Point.ROOT_CREATED && selected!=ImageStageStore.Point.MARKER_SYNCED);
                byte[] state=(ready.id+"\n"+pending.id+"\n"+selected.name()+"\n").getBytes(StandardCharsets.US_ASCII);
                ImageStageStore interrupted=new ImageStageStore(root,point->{
                    if(point==selected) {
                        try(FileOutputStream output=new FileOutputStream(control.toFile())) {
                            output.write(state);output.getFD().sync();
                        }
                        Bundle reached=new Bundle();reached.putString("stagePoint",point.name());instrumentation.sendStatus(73,reached);
                        Process.killProcess(Process.myPid());
                        throw new AssertionError("Controlled test process must terminate");
                    }
                });
                interrupted.stage(pending);throw new AssertionError("Pending must not publish");
            } else if("read".equals(phase)) {
                List<String> state=Files.readAllLines(control,StandardCharsets.US_ASCII);
                check(state.size()==3);
                ImageStageStore.Point point=ImageStageStore.Point.valueOf(state.get(2));
                boolean published=point==ImageStageStore.Point.RENAMED || point==ImageStageStore.Point.READY_VERIFIED;
                boolean hasOffer=point!=ImageStageStore.Point.PENDING_CREATED;
                boolean hasPng=point==ImageStageStore.Point.PNG_SYNCED || point==ImageStageStore.Point.PENDING_VERIFIED || published;
                UUID ready=UUID.fromString(state.get(0)),pending=UUID.fromString(state.get(1));
                ImageStageStore store=new ImageStageStore(root);ImageStageStore.Inspection saved=store.inspect();
                check(saved.ready.size()==(published?2:1) && saved.ready.contains(ready));
                check(published?saved.ready.contains(pending):saved.pending.equals(Collections.singletonList(pending)));
                check(saved.recoveryRequired()!=published && saved.pending.size()==(published?0:1));
                long bytes=887+(hasOffer?asset(instrumentation,"offer-h2.bin").length:0)+(hasPng?asset(instrumentation,"h2.png").length:0);
                check(saved.logicalBytes==bytes);
                check(Arrays.equals(asset(instrumentation,"h1.png"),store.read(ready).image.bytes()));
                check(Arrays.equals(asset(instrumentation,"offer-h1.bin"),Files.readAllBytes(root.resolve("ready-"+ready).resolve("offer.bin"))));
                Path second=root.resolve((published?"ready-":"pending-")+pending);
                if(hasPng) check(Arrays.equals(asset(instrumentation,"h2.png"),Files.readAllBytes(second.resolve("image.png"))));
                else check(!Files.exists(second.resolve("image.png")));
                if(hasOffer) check(Arrays.equals(asset(instrumentation,"offer-h2.bin"),Files.readAllBytes(second.resolve("offer.bin"))));
                else check(!Files.exists(second.resolve("offer.bin")));
                check(store.reconcile(ready,payload(instrumentation,1))==ImageStageStore.Outcome.READY);
                check(store.reconcile(pending,payload(instrumentation,2))==(published?ImageStageStore.Outcome.READY:ImageStageStore.Outcome.PENDING));
                if(!published) {
                    try { store.stage(ImageStageStore.prepare(payload(instrumentation,1)));throw new AssertionError(); }
                    catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.RECOVERY_REQUIRED); }
                }
                try(FileChannel channel=FileChannel.open(root.resolve("stage.lock"),StandardOpenOption.READ,StandardOpenOption.WRITE);
                    FileLock held=channel.lock()) {
                    try { store.inspect();throw new AssertionError(); }
                    catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.BUSY); }
                }
                check(store.inspect().logicalBytes==bytes);
                result.putString("result","PASS");result.putString("scope","Native filesystem ready/pending readback after controlled Android process kill; no ATAK/source/transport");
                instrumentation.finish(Activity.RESULT_OK,result);
            } else if("filesystem".equals(phase)) {
                Path quota=parent.resolve("quota");ImageStageStore quotaStore=new ImageStageStore(quota);quotaStore.initializeNew();
                Path last=null;
                for(int i=0;i<16;i++) {
                    Path entry=Files.createDirectory(quota.resolve("pending-"+UUID.randomUUID()));last=entry.resolve("image.png");
                    try(RandomAccessFile file=new RandomAccessFile(last.toFile(),"rw")) { file.setLength(16777216-(i==15?8:0)); }
                }
                check(quotaStore.inspect().logicalBytes==ImageStageStore.MAX_BYTES);
                try(RandomAccessFile file=new RandomAccessFile(last.toFile(),"rw")) { file.setLength(16777216-7); }
                try { quotaStore.inspect();throw new AssertionError(); }
                catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.CAPACITY); }
                check(Files.size(last)==16777216-7);
                Path linked=parent.resolve("linked");ImageStageStore linkedStore=new ImageStageStore(linked);linkedStore.initializeNew();
                ImageStageStore.Prepared candidate=ImageStageStore.prepare(payload(instrumentation,1));linkedStore.stage(candidate);
                Path image=linked.resolve("ready-"+candidate.id).resolve("image.png"),target=parent.resolve("synthetic-target.png");
                Files.move(image,target);Files.createSymbolicLink(image,target);
                try { linkedStore.inspect();throw new AssertionError(); }
                catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.INVALID_STATE); }
                check(Files.isSymbolicLink(image) && Arrays.equals(asset(instrumentation,"h1.png"),Files.readAllBytes(target)));
                Path unknown=parent.resolve("unknown");ImageStageStore unknownStore=new ImageStageStore(unknown);unknownStore.initializeNew();
                Files.write(unknown.resolve("format"),"TMMISTG2".getBytes(StandardCharsets.US_ASCII));
                try { unknownStore.inspect();throw new AssertionError(); }
                catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.INVALID_STATE); }
                check(Arrays.equals(Files.readAllBytes(unknown.resolve("format")),"TMMISTG2".getBytes(StandardCharsets.US_ASCII)));
                byte[] png;
                Bitmap bitmap=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);
                try {
                    int[] pixels=new int[1920*1080];Random random=new Random(150);
                    for(int i=0;i<pixels.length;i++) pixels[i]=0xff000000 | random.nextInt(0x1000000);
                    bitmap.setPixels(pixels,0,1920,0,0,1920,1080);
                    ByteArrayOutputStream output=new ByteArrayOutputStream();check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));
                    png=output.toByteArray();
                } finally { bitmap.recycle(); }
                ImageTransferPng.VerifiedContent content=ImageTransferPng.prepareExport(png);
                check(content.identity.width==1920 && content.identity.height==1080 && content.identity.length>1048576);
                ImageOffer offer=new ImageOffer(UUID.randomUUID(),UUID.randomUUID(),payload(instrumentation,1).offer.metadata,
                        content.identity.sha256(),content.identity.length,1920,1080);
                ReceivedImagePayload hd=ReceivedImagePayload.read(new ByteArrayInputStream(ImageOfferCodec.encode(offer)),new ByteArrayInputStream(content.bytes()));
                ImageStageStore hdStore=new ImageStageStore(parent.resolve("full-hd"));hdStore.initializeNew();
                ImageStageStore.Prepared prepared=ImageStageStore.prepare(hd);hdStore.stage(prepared);
                String controlText=prepared.id+"\n"+Base64.getEncoder().encodeToString(content.identity.sha256())+"\n"+content.identity.length+"\n";
                try(FileOutputStream output=new FileOutputStream(parent.resolve("hd-control.txt").toFile())) {
                    output.write(controlText.getBytes(StandardCharsets.US_ASCII));output.getFD().sync();
                }
                checkHd(parent);
                result.putString("result","PASS");result.putString("scope","Native quota/symlink/marker controls and synthetic high-entropy 1920x1080 stage readback");
                instrumentation.finish(Activity.RESULT_OK,result);
            } else if("filesystem-read".equals(phase)) {
                checkHd(parent);
                try { new ImageStageStore(parent.resolve("quota")).inspect();throw new AssertionError(); }
                catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.CAPACITY); }
                try { new ImageStageStore(parent.resolve("linked")).inspect();throw new AssertionError(); }
                catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.INVALID_STATE); }
                try { new ImageStageStore(parent.resolve("unknown")).inspect();throw new AssertionError(); }
                catch(ImageStageStore.Failure expected) { check(expected.code==ImageStageStore.Code.INVALID_STATE); }
                result.putString("result","PASS");result.putString("scope","Read-only native Full-HD and invalid-state preservation after guest reboot");
                instrumentation.finish(Activity.RESULT_OK,result);
            } else throw new IllegalArgumentException();
        } catch(Throwable failure) {
            result.putString("result","FAIL");result.putString("errorType",failure.getClass().getSimpleName());
            instrumentation.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private static void checkHd(Path parent) throws IOException {
        List<String> control=Files.readAllLines(parent.resolve("hd-control.txt"),StandardCharsets.US_ASCII);check(control.size()==3);
        UUID id=UUID.fromString(control.get(0));ImageStageStore store=new ImageStageStore(parent.resolve("full-hd"));
        check(store.inspect().ready.equals(Collections.singletonList(id)));
        ReceivedImagePayload payload=store.read(id);
        check(payload.offer.width==1920 && payload.offer.height==1080 && payload.offer.pngLength==Integer.parseInt(control.get(2)));
        check(Arrays.equals(payload.offer.sha256(),Base64.getDecoder().decode(control.get(1))));
    }
    private static ReceivedImagePayload payload(Instrumentation instrumentation,int n) throws IOException {
        return ReceivedImagePayload.read(new ByteArrayInputStream(asset(instrumentation,"offer-h"+n+".bin")),new ByteArrayInputStream(asset(instrumentation,"h"+n+".png")));
    }
    private static byte[] asset(Instrumentation instrumentation,String name) throws IOException {
        try(InputStream input=instrumentation.getContext().getAssets().open(name)) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
            for(int count;(count=input.read(buffer))!=-1;) bytes.write(buffer,0,count);return bytes.toByteArray();
        }
    }
    private static void check(boolean valid) { if(!valid) throw new AssertionError(); }
}
