package se.tmm.sensorplanner.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static se.tmm.sensorplanner.core.LocalV2.*;

public final class LocalV2CodecTest {
    private static final UUID STORE = new UUID(0, 15);
    private interface ActionCheck { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        LocalV2.Snapshot golden = LocalV2Codec.decode(Base64.getDecoder().decode(new String(Files.readAllBytes(
                Paths.get("test-fixtures/synthetic/storage-v2/history-v2.base64")), StandardCharsets.US_ASCII).trim()));
        immutableValues(golden);
        invalidValues(golden);
        countLimits(golden);
        textAndFileLimits();
        System.out.println("Local v2 codec passed (immutable state/history, safe rejection, independent count caps, UTF-8 boundaries and exact 16 MiB limit; memory only).");
    }

    private static TmmDraft draft(UUID uid, String owner, String image, long revision) {
        return new TmmDraft(uid, "PIR SYNTHETIC V2", DeviceType.PIR, DeviceStatus.PLANNED, owner,
                Instant.EPOCH, Instant.EPOCH, null, "", new Position(-0.0, 1), -0.0, image, revision);
    }
    private static TmmDraft image(TmmDraft m, String image, long revision) {
        return new TmmDraft(m.uid, m.displayId, m.type, m.status, m.owner, m.createdAt, m.updatedAt,
                m.deployedAt, m.comment, m.position, m.bearingDeg, image, revision);
    }
    private static Decision copy(Decision d, UUID id, State before, byte[] fingerprint, int mask, TmmDraft incoming) {
        return new Decision(id, d.action, d.target, d.inboxId, d.receiptId, d.contextId, d.firstObservedAt,
                incoming, before, fingerprint, mask, d.editTime, d.outcome, d.afterVersion, d.afterRevision, d.result);
    }
    private static Snapshot decisions(Snapshot frame, List<Decision> decisions) { return new Snapshot(frame.storeId, frame.slots, decisions); }

    private static void immutableValues(Snapshot golden) throws Exception {
        List<State> slots = new ArrayList<>(golden.slots); List<Decision> rows = new ArrayList<>(golden.decisions);
        Snapshot frozen = new Snapshot(golden.storeId, slots, rows); slots.clear(); rows.clear();
        assert frozen.slots.size() == 4 && frozen.decisions.size() == 5;
        try { frozen.slots.clear(); throw new AssertionError("Mutable slots"); } catch (UnsupportedOperationException expected) { }
        try { frozen.decisions.clear(); throw new AssertionError("Mutable history"); } catch (UnsupportedOperationException expected) { }
        Decision original = golden.decisions.get(3); byte[] source = original.reviewedFingerprint();
        Decision owned = copy(original, original.id, original.before, source, original.fieldMask, original.incoming);
        byte[] expected = source.clone(); source[0] ^= 1;
        byte[] exposed = owned.reviewedFingerprint(); exposed[1] ^= 1;
        assert Arrays.equals(owned.reviewedFingerprint(), expected);
        assert Arrays.equals(LocalV2Codec.encode(golden), LocalV2Codec.encode(frozen));
        byte[] encoded = LocalV2Codec.encode(frozen); byte[] committed = encoded.clone();
        Snapshot decoded = LocalV2Codec.decode(encoded); encoded[0] ^= 1;
        assert Arrays.equals(LocalV2Codec.encode(decoded), committed);
        byte[] digest = LocalStateFingerprint.digest(golden.storeId, golden.slots.get(0)); digest[0] ^= 1;
        assert !Arrays.equals(digest, LocalStateFingerprint.digest(golden.storeId, golden.slots.get(0)));
    }

    private static void invalidValues(Snapshot golden) throws Exception {
        Decision keep = golden.decisions.get(3);
        fail(() -> LocalV2Codec.encode(new Snapshot(golden.storeId, Arrays.asList(golden.slots.get(0), golden.slots.get(0)), Collections.emptyList())));
        fail(() -> LocalV2Codec.encode(new Snapshot(STORE, Collections.singletonList(State.neverSeen(new UUID(0, 1))), Collections.emptyList())));
        fail(() -> LocalV2Codec.encode(new Snapshot(STORE, Collections.singletonList(null), Collections.emptyList())));
        fail(() -> LocalV2Codec.encode(decisions(golden, Arrays.asList(keep, keep))));
        Decision contradictoryReceipt = new Decision(new UUID(7, 1), keep.action, keep.target, keep.inboxId, keep.receiptId,
                "SYNTHETIC-OTHER-CONTEXT", keep.firstObservedAt, keep.incoming, keep.before, keep.reviewedFingerprint(),
                0, null, keep.outcome, keep.afterVersion, keep.afterRevision, keep.result);
        fail(() -> LocalV2Codec.encode(decisions(golden, Arrays.asList(keep, contradictoryReceipt))));
        fail(() -> LocalV2Codec.encode(decisions(golden, Collections.singletonList(copy(keep, keep.id, keep.before, keep.reviewedFingerprint(), 256, keep.incoming)))));
        byte[] wrong = keep.reviewedFingerprint(); wrong[0] ^= 1;
        fail(() -> LocalV2Codec.encode(decisions(golden, Collections.singletonList(copy(keep, keep.id, keep.before, wrong, 0, keep.incoming)))));
        fail(() -> LocalV2Codec.encode(decisions(golden, Collections.singletonList(copy(keep, keep.id,
                State.active(keep.before.version, image(keep.before.draft, "private-image", keep.before.draft.revision)), keep.reviewedFingerprint(), 0, keep.incoming)))));
        fail(() -> LocalV2Codec.encode(decisions(golden, Collections.singletonList(copy(keep, keep.id, keep.before,
                keep.reviewedFingerprint(), 0, image(keep.incoming, "private-foreign-handle", 1))))));
        fail(() -> LocalV2Codec.encode(decisions(golden, Collections.singletonList(copy(keep, keep.id, keep.before,
                keep.reviewedFingerprint(), 0, image(keep.incoming, null, 2))))));
        fail(() -> LocalV2Codec.encode(decisions(new Snapshot(STORE, Collections.emptyList(), Collections.emptyList()), Collections.singletonList(keep))));
        fail(() -> LocalV2Codec.decode(new byte[63]));
        byte[] v1 = Base64.getDecoder().decode(new String(Files.readAllBytes(Paths.get("test-fixtures/synthetic/storage/v1.base64")), StandardCharsets.US_ASCII).trim());
        byte[] original = v1.clone(); fail(() -> LocalV2Codec.decode(v1)); assert Arrays.equals(original, v1);
        try { State.deleted(STORE, 0); throw new AssertionError("Zero version accepted"); } catch (IllegalArgumentException expected) { }
        try { State.active(-1, golden.slots.get(0).draft); throw new AssertionError("Negative version accepted"); } catch (IllegalArgumentException expected) { }
        try { copy(keep, keep.id, keep.before, new byte[31], 0, keep.incoming); throw new AssertionError("Bad digest length accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void countLimits(Snapshot golden) throws Exception {
        List<State> slots = new ArrayList<>(); slots.add(golden.slots.get(0));
        for (int i = 1; i < 1000; i++) slots.add(State.deleted(new UUID(1, i), i));
        List<Decision> rows = new ArrayList<>(); Decision keep = golden.decisions.get(3);
        for (int i = 0; i < 1000; i++) rows.add(copy(keep, new UUID(2, i), keep.before, keep.reviewedFingerprint(), 0, keep.incoming));
        Snapshot maximum = new Snapshot(golden.storeId, slots, rows);
        byte[] bytes = LocalV2Codec.encode(maximum); Snapshot read = LocalV2Codec.decode(bytes);
        assert read.slots.size() == 1000 && read.decisions.size() == 1000;
        slots.add(State.deleted(new UUID(1, 1000), Long.MAX_VALUE));
        fail(() -> LocalV2Codec.encode(new Snapshot(golden.storeId, slots, rows)));
        rows.add(copy(keep, new UUID(2, 1000), keep.before, keep.reviewedFingerprint(), 0, keep.incoming));
        fail(() -> LocalV2Codec.encode(new Snapshot(golden.storeId, maximum.slots, rows)));
        State exhausted = State.active(Long.MAX_VALUE, image(keep.before.draft, null, Long.MAX_VALUE));
        Snapshot counters = new Snapshot(STORE, Arrays.asList(exhausted, State.deleted(new UUID(0, 99), Long.MAX_VALUE)), Collections.emptyList());
        assert LocalV2Codec.decode(LocalV2Codec.encode(counters)).slots.get(0).version == Long.MAX_VALUE;
        assert LocalStateFingerprint.digest(STORE, exhausted).length == 32;
    }

    private static void textAndFileLimits() throws Exception {
        UUID uid = new UUID(0, 1); String ascii = repeat("x", 65536), unicode = repeat("å", 32768);
        for (String text : Arrays.asList(ascii, unicode)) {
            State state = State.active(1, draft(uid, text, text, 1));
            Snapshot value = new Snapshot(STORE, Collections.singletonList(state), Collections.emptyList());
            Snapshot read = LocalV2Codec.decode(LocalV2Codec.encode(value));
            assert read.slots.get(0).draft.owner.equals(text) && read.slots.get(0).draft.imageRef.equals(text);
            assert LocalStateFingerprint.digest(STORE, state).length == 32;
        }
        for (String bad : Arrays.asList(ascii + "x", unicode + "å", "private-\uD800")) {
            State state = State.active(1, draft(uid, bad, null, 1));
            fail(() -> LocalV2Codec.encode(new Snapshot(STORE, Collections.singletonList(state), Collections.emptyList())));
            fail(() -> LocalStateFingerprint.digest(STORE, state));
            State badImage = State.active(1, draft(uid, "SYNTHETIC", bad, 1));
            fail(() -> LocalStateFingerprint.digest(STORE, badImage));
        }
        List<State> slots = new ArrayList<>();
        for (int i = 0; i < 255; i++) slots.add(State.active(1, draft(new UUID(3, i), ascii, null, 1)));
        slots.add(State.active(1, draft(new UUID(3, 255), "x", null, 1)));
        int padding = LocalV2Codec.MAX_BYTES - LocalV2Codec.encode(new Snapshot(STORE, slots, Collections.emptyList())).length;
        assert padding > 0 && padding < 65535;
        slots.set(255, State.active(1, draft(new UUID(3, 255), repeat("x", padding + 1), null, 1)));
        Snapshot exact = new Snapshot(STORE, slots, Collections.emptyList());
        byte[] maximum = LocalV2Codec.encode(exact); assert maximum.length == LocalV2Codec.MAX_BYTES;
        assert LocalV2Codec.decode(maximum).slots.size() == 256;
        slots.set(255, State.active(1, draft(new UUID(3, 255), repeat("x", padding + 2), null, 1)));
        fail(() -> LocalV2Codec.encode(new Snapshot(STORE, slots, Collections.emptyList())));
        byte[] tooLarge = Arrays.copyOf(maximum, maximum.length + 1);
        byte[] before = LocalV2Binary.sha(tooLarge); fail(() -> LocalV2Codec.decode(tooLarge));
        assert Arrays.equals(before, LocalV2Binary.sha(tooLarge));
        assert exact.slots.get(255).draft.owner.length() == padding + 1;
    }
    private static String repeat(String value, int count) { return String.join("", Collections.nCopies(count, value)); }
    private static void fail(ActionCheck check) throws Exception {
        try { check.run(); throw new AssertionError("Invalid local v2 value accepted"); }
        catch (IOException expected) {
            assert expected.getCause() == null;
            assert expected.getMessage().equals("Invalid or unsupported local v2 data");
        }
    }
}
