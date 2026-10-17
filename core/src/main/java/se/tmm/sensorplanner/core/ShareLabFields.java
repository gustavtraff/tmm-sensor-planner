package se.tmm.sensorplanner.core;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Experimental metadata-only lab payload, independent of the local snapshot schema. */
public final class ShareLabFields {
    public static final String TAG = "tmmShareLab";
    private ShareLabFields() {}

    public static Map<String, String> encode(ShareReview.Metadata metadata) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("experiment", "issue-14");
        fields.put("schemaVersion", "1");
        // Base64 preserves tabs/newlines and Unicode independently of XML attribute normalization.
        fields.put("displayIdUtf8", text(metadata.displayId));
        fields.put("latitude", Double.toString(metadata.position.latitude));
        fields.put("longitude", Double.toString(metadata.position.longitude));
        fields.put("type", metadata.type.name());
        fields.put("status", metadata.status.name());
        fields.put("ownerUtf8", text(metadata.owner));
        fields.put("createdAt", metadata.createdAt.toString());
        fields.put("updatedAt", metadata.updatedAt.toString());
        fields.put("deployedAt", metadata.deployedAt == null ? "" : metadata.deployedAt.toString());
        fields.put("commentUtf8", text(metadata.comment));
        fields.put("bearingDeg", metadata.bearingDeg == null ? "" : metadata.bearingDeg.toString());
        fields.put("bearingReference", "true-north");
        fields.put("imageIncluded", "false");
        return Collections.unmodifiableMap(fields);
    }

    /** Validates canonical lab fields; produces review metadata, never an importable stored draft. */
    public static ShareReview.Metadata decode(String uid, Position point, Map<String, String> fields) {
        try {
            if (fields.size() != 15 || !"issue-14".equals(fields.get("experiment"))
                    || !"1".equals(fields.get("schemaVersion"))
                    || !"false".equals(fields.get("imageIncluded"))
                    || !"true-north".equals(fields.get("bearingReference"))) throw invalid();
            for (Map.Entry<String, String> field : fields.entrySet()) {
                int limit = field.getKey().endsWith("Utf8") ? 87384 : 128;
                if (field.getValue() == null || field.getValue().length() > limit) throw invalid();
            }
            if (uid == null || uid.length() != 36) throw invalid();
            UUID identity = UUID.fromString(uid);
            if (!identity.toString().equals(uid)) throw invalid();
            String deployed = fields.get("deployedAt");
            String bearing = fields.get("bearingDeg");
            Position exact = new Position(Double.parseDouble(fields.get("latitude")), Double.parseDouble(fields.get("longitude")));
            Position expected = envelopePosition(exact);
            if (point.latitude != expected.latitude || point.longitude != expected.longitude) throw invalid();
            TmmDraft validated = new TmmDraft(identity, untext(fields.get("displayIdUtf8")),
                    DeviceType.valueOf(fields.get("type")), DeviceStatus.valueOf(fields.get("status")),
                    untext(fields.get("ownerUtf8")), Instant.parse(fields.get("createdAt")),
                    Instant.parse(fields.get("updatedAt")), deployed.isEmpty() ? null : Instant.parse(deployed),
                    untext(fields.get("commentUtf8")), exact,
                    bearing.isEmpty() ? null : Double.valueOf(bearing), null, 1);
            ShareReview.Metadata metadata = new ShareReview.Metadata(validated);
            if (!encode(metadata).equals(fields)) throw invalid();
            return metadata;
        } catch (Exception rejected) {
            throw invalid();
        }
    }

    /** Matches the inspected 5.6.0.24 CotPoint XML serializer, not the saved coordinate precision. */
    public static Position envelopePosition(Position exact) {
        return new Position(Math.round(exact.latitude * 1e7) / 1e7, Math.round(exact.longitude * 1e7) / 1e7);
    }

    private static String text(String text) {
        try {
            if (text.length() > 65536) throw invalid();
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            if (bytes.remaining() > 65536) throw invalid();
            byte[] copy = new byte[bytes.remaining()];
            bytes.get(copy);
            return Base64.getEncoder().encodeToString(copy);
        } catch (java.nio.charset.CharacterCodingException rejected) { throw invalid(); }
    }

    private static String untext(String text) throws java.nio.charset.CharacterCodingException {
        if (text == null || text.length() > 87384) throw invalid();
        byte[] bytes = Base64.getDecoder().decode(text);
        if (bytes.length > 65536) throw invalid();
        if (!Base64.getEncoder().encodeToString(bytes).equals(text)) throw invalid();
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported or invalid lab metadata");
    }
}
