package se.tmm.sensorplanner.plugin.ingress;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLException;
import org.junit.Test;
import static org.junit.Assert.*;
import static se.tmm.sensorplanner.plugin.ingress.MissionReadFailure.Stage.*;
import static se.tmm.sensorplanner.plugin.ingress.MissionReadFailure.Kind.*;

public class MissionReadFailureTest {
    @Test public void wrappingPreservesTheOriginalStageAndHttpStatus() {
        MissionReadFailure original = new MissionReadFailure(COT, HTTP, 403);
        assertSame(original, MissionReadFailure.at(SNAPSHOT, original));
        assertEquals(COT, original.stage);
        assertEquals(HTTP, original.kind);
        assertEquals(403, original.httpStatus);
        assertTrue(original.summary().contains("HTTP 403"));
    }

    @Test public void categoriesUseTypesAndNeverGuessFromMessages() {
        assertEquals(TIMEOUT, MissionReadFailure.at(FEED_LIST, new SocketTimeoutException("private")).kind);
        assertEquals(CONNECTION, MissionReadFailure.at(COT, new ConnectException("private")).kind);
        assertEquals(CONNECTION, MissionReadFailure.at(COT, new SSLException("private")).kind);
        assertEquals(RESPONSE, MissionReadFailure.at(RESOURCE_LIST, new IOException("HTTP 403 timeout")).kind);
        assertEquals(UNKNOWN, MissionReadFailure.at(COT, new IllegalStateException("timeout")).kind);
        for (MissionReadFailure.Stage stage : new MissionReadFailure.Stage[]{METADATA_INTAKE, STAGE_LOOKUP, IMAGE_INTAKE}) {
            assertEquals(LOCAL, MissionReadFailure.at(stage, new IOException("private")).kind);
            assertEquals(LOCAL, MissionReadFailure.at(stage, new IllegalStateException("private")).kind);
            assertEquals(MEMORY, MissionReadFailure.at(stage, new OutOfMemoryError("private")).kind);
            assertEquals(TIMEOUT, MissionReadFailure.at(stage, new SocketTimeoutException("private")).kind);
        }
    }

    @Test public void diagnosticsNeverRetainPrivateMessagesOrCauses() {
        String secret = "SYNTHETIC-PRIVATE-https://private.invalid/token";
        IOException original = new IOException(secret, new IllegalStateException(secret));
        MissionReadFailure diagnostic = MissionReadFailure.at(COT, original);
        assertNull(diagnostic.getCause());
        assertEquals(0, diagnostic.getSuppressed().length);
        assertFalse(diagnostic.getMessage().contains(secret));
        assertFalse(diagnostic.summary().contains(secret));
        StringWriter stack = new StringWriter();
        diagnostic.printStackTrace(new PrintWriter(stack));
        assertFalse(stack.toString().contains(secret));
        assertThrows(IllegalStateException.class, () -> diagnostic.initCause(original));
        for (MissionReadFailure.Stage stage : MissionReadFailure.Stage.values())
            assertFalse(MissionReadFailure.at(stage, original).summary().contains(secret));
    }

    @Test public void onlyHttpDiagnosticsCanCarryAValidStatus() {
        for (int value : new int[]{99, 600, -1, 0})
            assertThrows(IllegalArgumentException.class, () -> new MissionReadFailure(FEED_LIST, HTTP, value));
        for (MissionReadFailure.Kind kind : MissionReadFailure.Kind.values())
            if (kind != HTTP) assertThrows(IllegalArgumentException.class, () -> new MissionReadFailure(COT, kind, 403));
        assertEquals(100, new MissionReadFailure(FEED_LIST, HTTP, 100).httpStatus);
        assertEquals(599, new MissionReadFailure(FEED_LIST, HTTP, 599).httpStatus);
    }
}
