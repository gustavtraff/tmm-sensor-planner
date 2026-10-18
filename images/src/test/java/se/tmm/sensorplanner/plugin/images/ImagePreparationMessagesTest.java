package se.tmm.sensorplanner.plugin.images;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import se.tmm.sensorplanner.images.ImagePreparationException;
import static org.junit.Assert.*;

public class ImagePreparationMessagesTest {
    @Test public void distinctFailuresExplainTheLimitAndNeverExposeProviderDetails() {
        Set<String> messages = new HashSet<>();
        for (ImagePreparationException.Reason reason : ImagePreparationException.Reason.values()) {
            String message = ImagePreparationMessages.forFailure(new ImagePreparationException(reason));
            assertTrue(message.endsWith("Befintlig bild behålls."));
            assertTrue(messages.add(message));
        }
        assertTrue(ImagePreparationMessages.forFailure(new ImagePreparationException(
                ImagePreparationException.Reason.INPUT_TOO_LARGE)).contains("Originalfilen"));
        assertTrue(ImagePreparationMessages.forFailure(new ImagePreparationException(
                ImagePreparationException.Reason.OUTPUT_TOO_LARGE)).contains("Bildkopian"));
        String provider = ImagePreparationMessages.forFailure(new IOException("content://synthetic-private-provider"));
        assertFalse(provider.contains("synthetic-private-provider"));
        assertTrue(provider.contains("lokal fil"));
        assertEquals(ImagePreparationMessages.forFailure(new ImagePreparationException(
                ImagePreparationException.Reason.LOW_MEMORY)),
                ImagePreparationMessages.forFailure(new OutOfMemoryError("synthetic-private-detail")));
    }
}
