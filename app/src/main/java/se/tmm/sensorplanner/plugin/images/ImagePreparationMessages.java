package se.tmm.sensorplanner.plugin.images;

import se.tmm.sensorplanner.images.ImagePreparationException;

/** User-facing errors omit provider exception text, paths and image metadata. */
public final class ImagePreparationMessages {
    private ImagePreparationMessages() { }

    public static String forFailure(Throwable failure) {
        String message = "Bilden kunde inte läsas eller förberedas. Välj en lokal fil och försök igen.";
        if (failure instanceof OutOfMemoryError) {
            message = "Minnet räckte inte till. Stäng andra appar eller välj en mindre bild.";
        } else if (failure instanceof ImagePreparationException) {
            switch (((ImagePreparationException) failure).reason) {
                case INPUT_TOO_LARGE: message = "Originalfilen är större än 10 MiB. Välj en mindre fil."; break;
                case PIXEL_LIMIT: message = "Originalbilden har fler än 20 miljoner pixlar. Välj en bild med lägre upplösning."; break;
                case INVALID_IMAGE: message = "Bilden kunde inte avkodas som en stödd stillbild. Välj en vanlig JPEG eller PNG."; break;
                case LOW_MEMORY: message = "Minnet räckte inte till. Stäng andra appar eller välj en mindre bild."; break;
                case OUTPUT_TOO_LARGE: message = "Bildkopian blev större än 10 MiB. Välj en mindre bild."; break;
                case SAVE_FAILED: message = "Bildkopian kunde inte sparas. Kontrollera ledigt lagringsutrymme och försök igen."; break;
            }
        }
        return message + " Befintlig bild behålls.";
    }
}
