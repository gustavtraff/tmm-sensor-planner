package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Opt-in only: the write phase deliberately halts its own isolated Gradle test worker. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageStageProcessRestartTest {
    @Test public void controlledWorkerPhase() throws Exception {
        String parent=System.getenv("TMM_STAGE_RESTART_ROOT"), phase=System.getenv("TMM_STAGE_RESTART_PHASE");
        Assume.assumeTrue("Run scripts/test-image-stage-restart.py for isolated worker phases",
                parent!=null && phase!=null);
        Path workspace=Paths.get(parent), root=workspace.resolve("stage"), state=workspace.resolve("worker-state.txt");
        assertEquals("TMM-SYNTHETIC-RESTART-148",new String(Files.readAllBytes(workspace.resolve("harness-marker")),StandardCharsets.US_ASCII));
        if (phase.equals("write-and-halt")) {
            assertFalse(Files.exists(state)); assertFalse(Files.exists(root));
            ImageStageStore store=new ImageStageStore(root);store.initializeNew();
            ImageStageStore.Prepared ready=ImageStageStore.prepare(payload(1)),pending=ImageStageStore.prepare(payload(2));
            store.stage(ready);
            byte[] record=(ready.id+"\n"+pending.id+"\nHALT_AFTER_PNG_SYNC\n").getBytes(StandardCharsets.US_ASCII);
            ImageStageStore interrupted=new ImageStageStore(root,point->{
                if(point==ImageStageStore.Point.PNG_SYNCED) {
                    try(FileOutputStream output=new FileOutputStream(state.toFile())) {
                        output.write(record); output.getFD().sync();
                    }
                    Runtime.getRuntime().halt(73);
                }
            });
            interrupted.stage(pending);
            fail("The isolated write worker must halt before publication");
        } else if (phase.equals("read")) {
            List<String> record=Files.readAllLines(state,StandardCharsets.US_ASCII);
            assertEquals(3,record.size());assertEquals("HALT_AFTER_PNG_SYNC",record.get(2));
            UUID ready=UUID.fromString(record.get(0)),pending=UUID.fromString(record.get(1));
            ImageStageStore store=new ImageStageStore(root);
            ImageStageStore.Inspection inspection=store.inspect();
            assertEquals(Collections.singletonList(ready),inspection.ready);
            assertEquals(Collections.singletonList(pending),inspection.pending);assertTrue(inspection.recoveryRequired());
            assertEquals(1776,inspection.logicalBytes);
            assertArrayEquals(resource("/image-transfer/h1.png"),store.read(ready).image.bytes());
            assertArrayEquals(resource("/offer-h2.bin"),Files.readAllBytes(root.resolve("pending-"+pending).resolve("offer.bin")));
            assertArrayEquals(resource("/image-transfer/h2.png"),Files.readAllBytes(root.resolve("pending-"+pending).resolve("image.png")));
            assertEquals(ImageStageStore.Outcome.READY,store.reconcile(ready,payload(1)));
            assertEquals(ImageStageStore.Outcome.PENDING,store.reconcile(pending,payload(2)));
            ImageStageStore.Failure failure=assertThrows(ImageStageStore.Failure.class,()->store.stage(ImageStageStore.prepare(payload(1))));
            assertEquals(ImageStageStore.Code.RECOVERY_REQUIRED,failure.code);
            assertEquals(1776,store.inspect().logicalBytes);
        } else fail("Unknown isolated-worker phase");
    }
    private static ReceivedImagePayload payload(int number) throws IOException {
        return ReceivedImagePayload.read(new ByteArrayInputStream(resource("/offer-h"+number+".bin")),
                new ByteArrayInputStream(resource("/image-transfer/h"+number+".png")));
    }
    private static byte[] resource(String name) throws IOException {
        try(InputStream input=ImageStageProcessRestartTest.class.getResourceAsStream(name)) {
            assertNotNull(input);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
            for(int n;(n=input.read(buffer))!=-1;) bytes.write(buffer,0,n);return bytes.toByteArray();
        }
    }
}
