package se.tmm.sensorplanner.plugin.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class TmmMapReviewTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-4000-8000-000000000abc");
    private static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000def");

    @Test public void resolvesCurrentEligibilityAgainWhenAnEarlierMenuActionRuns() {
        Set<UUID> active = new HashSet<>(Collections.singleton(FIRST));
        List<UUID> opened = new ArrayList<>();
        TmmMapReview review = new TmmMapReview(active::contains, opened::add);
        assertEquals(FIRST, review.match(FIRST.toString()));
        active.remove(FIRST);
        review.open(FIRST.toString());
        assertNull(review.match(FIRST.toString()));
        assertTrue(opened.isEmpty());
        active.add(FIRST);
        review.open(FIRST.toString());
        assertEquals(Collections.singletonList(FIRST), opened);
    }

    @Test public void unknownMalformedAndNoncanonicalUidsAreIgnored() {
        List<UUID> opened = new ArrayList<>();
        TmmMapReview review = new TmmMapReview(FIRST::equals, opened::add);
        String[] rejected = {null, "", "150", "not-a-uuid", "1-1-1-1-1",
                FIRST.toString().toUpperCase(Locale.ROOT), " " + FIRST, FIRST + " ", SECOND.toString()};
        for (String uid : rejected) {
            assertNull(uid, review.match(uid));
            review.open(uid);
        }
        assertTrue(opened.isEmpty());
    }

    @Test public void callbackUsesUuidAndResolvesTheLatestDeviceInsteadOfAnEarlierLabel() {
        Map<UUID, String> devices = new HashMap<>();
        devices.put(FIRST, "150");
        devices.put(SECOND, "150");
        List<String> opened = new ArrayList<>();
        TmmMapReview review = new TmmMapReview(devices::containsKey,
                uid -> opened.add(uid + ":" + devices.get(uid)));
        assertEquals(FIRST, review.match(FIRST.toString()));
        devices.put(FIRST, "updated device");
        review.open(FIRST.toString());
        review.open(SECOND.toString());
        assertEquals(Arrays.asList(FIRST + ":updated device", SECOND + ":150"), opened);
    }

    @Test public void closeIsIdempotentAndStopsLateSelectionWithoutCallingPredicates() {
        AtomicInteger predicates = new AtomicInteger();
        List<UUID> opened = new ArrayList<>();
        TmmMapReview review = new TmmMapReview(uid -> { predicates.incrementAndGet(); return true; }, opened::add);
        assertEquals(FIRST, review.match(FIRST.toString()));
        review.close();review.close();
        assertNull(review.match(FIRST.toString()));
        review.open(FIRST.toString());
        assertEquals(1, predicates.get());
        assertTrue(opened.isEmpty());
    }

    @Test public void closingDuringEligibilityCheckPreventsOpening() {
        List<UUID> opened = new ArrayList<>();
        TmmMapReview[] review = new TmmMapReview[1];
        review[0] = new TmmMapReview(uid -> { review[0].close(); return true; }, opened::add);
        review[0].open(FIRST.toString());
        assertTrue(opened.isEmpty());
    }

    @Test public void predicateAndCallbackProgrammingErrorsAreNotSilenced() {
        IllegalStateException failure = new IllegalStateException("synthetic programming error");
        TmmMapReview predicateFailure = new TmmMapReview(uid -> { throw failure; }, uid -> fail("must not open"));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> predicateFailure.match(FIRST.toString())));
        TmmMapReview callbackFailure = new TmmMapReview(uid -> true, uid -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> callbackFailure.open(FIRST.toString())));
    }
}
