package se.tmm.sensorplanner.core;

import java.io.*;
import java.util.Objects;
import java.util.UUID;
import static se.tmm.sensorplanner.core.LocalV2Binary.*;

/** Pure TMMR format 1 digest; binds a supplied local state, never consent or image bytes. */
public final class LocalStateFingerprint {
    private LocalStateFingerprint() { }

    public static byte[] digest(UUID storeId, LocalV2.State state) throws IOException {
        Objects.requireNonNull(storeId, "storeId"); Objects.requireNonNull(state, "state");
        Buffer buffer = new Buffer(LocalV2Codec.MAX_BYTES);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            if (state.draft != null) validateDraft(state.draft);
            out.writeInt(0x544d4d52); out.writeInt(1); uuid(out, storeId); uuid(out, state.uid);
            out.writeByte(state.presence.code); out.writeLong(state.version);
            if (state.draft != null) local(out, state.draft, true);
        } catch (IOException e) { throw invalid(); }
        return sha(buffer.bytes());
    }
}
