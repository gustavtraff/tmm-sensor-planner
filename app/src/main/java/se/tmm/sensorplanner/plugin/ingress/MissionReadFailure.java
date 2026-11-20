package se.tmm.sensorplanner.plugin.ingress;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;

/** Fixed diagnostic labels only; never retains a server reply, exception message or cause. */
public final class MissionReadFailure extends IOException {
    public enum Stage {
        FEED_LIST, MEMBERSHIP_BEFORE, COT, MEMBERSHIP_AFTER, SNAPSHOT,
        METADATA_INTAKE, RESOURCE_LIST, STAGE_LOOKUP, IMAGE, IMAGE_INTAKE
    }
    public enum Kind { HTTP, CONNECTION, TIMEOUT, RESPONSE, LOCAL, UNKNOWN, MEMORY }
    public final Stage stage;
    public final Kind kind;
    public final int httpStatus;

    public MissionReadFailure(Stage stage, Kind kind, int httpStatus) {
        super("Mission read failed", null);
        this.stage = Objects.requireNonNull(stage);
        this.kind = Objects.requireNonNull(kind);
        if (kind == Kind.HTTP ? httpStatus < 100 || httpStatus > 599 : httpStatus != 0)
            throw new IllegalArgumentException("Invalid diagnostic status");
        this.httpStatus = httpStatus;
    }

    public static MissionReadFailure at(Stage stage, Throwable failure) {
        Objects.requireNonNull(stage);
        Objects.requireNonNull(failure);
        if (failure instanceof MissionReadFailure) return (MissionReadFailure) failure;
        Kind kind;
        if (failure instanceof OutOfMemoryError) kind = Kind.MEMORY;
        else if (failure instanceof SocketTimeoutException || failure instanceof TimeoutException) kind = Kind.TIMEOUT;
        else if (stage == Stage.METADATA_INTAKE || stage == Stage.STAGE_LOOKUP || stage == Stage.IMAGE_INTAKE) kind = Kind.LOCAL;
        else if (failure instanceof SocketException || failure instanceof UnknownHostException || failure instanceof SSLException) kind = Kind.CONNECTION;
        else if (failure instanceof IOException) kind = Kind.RESPONSE;
        else kind = Kind.UNKNOWN;
        return new MissionReadFailure(stage, kind, 0);
    }

    public String summary() {
        String step;
        switch (stage) {
            case FEED_LIST: step = "Läsa feedlista"; break;
            case MEMBERSHIP_BEFORE: step = "Läsa innehåll före metadata"; break;
            case COT: step = "Läsa kartmetadata"; break;
            case MEMBERSHIP_AFTER: step = "Läsa innehåll efter metadata"; break;
            case SNAPSHOT: step = "Kontrollera metadatasnapshot"; break;
            case METADATA_INTAKE: step = "Spara mottagna metadata"; break;
            case RESOURCE_LIST: step = "Läsa bildresurslista"; break;
            case STAGE_LOOKUP: step = "Läsa lokal bildhistorik"; break;
            case IMAGE: step = "Läsa bild"; break;
            case IMAGE_INTAKE: step = "Spara mottagen bild"; break;
            default: throw new AssertionError();
        }
        String reason;
        switch (kind) {
            case HTTP: reason = "HTTP " + httpStatus; break;
            case CONNECTION: reason = "anslutningen kunde inte verifieras"; break;
            case TIMEOUT: reason = "tidsgränsen nåddes"; break;
            case RESPONSE: reason = "svaret kunde inte verifieras"; break;
            case LOCAL: reason = "lokal lagring eller historik kunde inte verifieras"; break;
            case MEMORY: reason = "minnet räckte inte"; break;
            case UNKNOWN: reason = "orsaken kunde inte fastställas"; break;
            default: throw new AssertionError();
        }
        return step + ": " + reason + ".";
    }
}
