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
    /** #280: which fixed response check failed; labels name the rule only, never a value from the reply. */
    public enum Rule {
        TOO_LARGE("svaret är större än gränsen"),
        JSON_SYNTAX("ogiltig JSON"),
        JSON_DEPTH("JSON är för djupt nästlad"),
        JSON_KEY("dubblerat eller för långt fältnamn i JSON"),
        JSON_LONG_VALUE("ett JSON-värde är längre än 4096 tecken"),
        NOT_MISSION("svaret beskriver inte en feed"),
        FEED_FIELDS("feedens namn eller GUID saknas eller är ogiltigt"),
        FEED_MISMATCH("feedens namn eller GUID stämmer inte med den följda feeden"),
        FEED_NOT_LISTED("den följda feeden finns inte i feedlistan"),
        FEED_COUNT("för många feeds eller objekt"),
        MEMBER_ID("ogiltigt eller dubblerat objekt-ID i feeden"),
        MEMBERS_CHANGED("feedens innehåll ändrades under hämtningen"),
        XML_SYNTAX("ogiltig XML"),
        XML_FORBIDDEN("XML innehåller DTD, kommentar, CDATA, namnrymd eller instruktion"),
        XML_SHAPE("oväntad XML-struktur, nästling, text eller attributmängd"),
        DETAIL_CHILDREN("ett objekt har fler än 64 element i detail"),
        COT_MISSING("kartdata saknas för objekt som finns i feeden"),
        COT_EXTRA("kartdata finns för objekt utanför feeden"),
        TMM_EVENT("TMM-objektets typ, version, how, punkt eller detail avviker"),
        TMM_TIME("TMM-objektets tidsfält har oväntat format"),
        TMM_FIELDS("TMM-datan har fel antal fält eller ligger fel"),
        TMM_PAYLOAD("TMM-datan kunde inte avkodas"),
        TMM_POSITION("TMM-objektets position stämmer inte med TMM-datan"),
        RESOURCE_FIELD("en fil i feeden saknar eller har ogiltig hash, namn, typ eller storlek");
        public final String label;
        Rule(String label) { this.label = label; }
    }
    /** A failed fixed check; carries only the rule, never a message or cause from the reply. */
    public static final class Violation extends IOException {
        public final Rule rule;
        public Violation(Rule rule) { super("Mission response rule failed", null); this.rule = Objects.requireNonNull(rule); }
    }
    public final Stage stage;
    public final Kind kind;
    public final int httpStatus;
    public final Rule rule;

    public MissionReadFailure(Stage stage, Kind kind, int httpStatus) { this(stage, kind, httpStatus, null); }
    public MissionReadFailure(Stage stage, Kind kind, int httpStatus, Rule rule) {
        super("Mission read failed", null);
        this.stage = Objects.requireNonNull(stage);
        this.kind = Objects.requireNonNull(kind);
        if (kind == Kind.HTTP ? httpStatus < 100 || httpStatus > 599 : httpStatus != 0)
            throw new IllegalArgumentException("Invalid diagnostic status");
        if (rule != null && kind != Kind.RESPONSE) throw new IllegalArgumentException("Rules describe responses only");
        this.httpStatus = httpStatus;
        this.rule = rule;
    }

    public static MissionReadFailure at(Stage stage, Throwable failure) {
        Objects.requireNonNull(stage);
        Objects.requireNonNull(failure);
        if (failure instanceof MissionReadFailure) return (MissionReadFailure) failure;
        if (failure instanceof Violation) return new MissionReadFailure(stage, Kind.RESPONSE, 0, ((Violation) failure).rule);
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
        return step + ": " + reason + (rule == null ? "" : " (" + rule.label + ")") + ".";
    }
}
