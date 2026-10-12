package se.tmm.sensorplanner.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DistanceCalculator {
    private static final double EARTH_RADIUS_METERS = 6371008.8;

    private DistanceCalculator() {}

    public static double meters(Position from, Position to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        double latitudeDelta = Math.toRadians(to.latitude - from.latitude);
        double longitudeDelta = Math.toRadians(to.longitude - from.longitude);
        double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(from.latitude)) * Math.cos(Math.toRadians(to.latitude))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        // Rounding near antipodal positions can otherwise produce NaN.
        a = Math.max(0, Math.min(1, a));
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static Optional<Distance> nearestOther(TmmDraft source, Collection<TmmDraft> drafts) {
        List<Distance> sorted = sortedOthers(source, drafts, false);
        return sorted.isEmpty() ? Optional.empty() : Optional.of(sorted.get(0));
    }

    public static List<Distance> otherGateways(TmmDraft source, Collection<TmmDraft> drafts) {
        Objects.requireNonNull(source, "source");
        if (source.type != DeviceType.GATEWAY) {
            throw new IllegalArgumentException("gateway distances require a gateway source");
        }
        return sortedOthers(source, drafts, true);
    }

    private static List<Distance> sortedOthers(TmmDraft source, Collection<TmmDraft> drafts,
                                               boolean gatewaysOnly) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(drafts, "drafts");
        List<Distance> result = new ArrayList<>();
        for (TmmDraft candidate : drafts) {
            Objects.requireNonNull(candidate, "candidate");
            if (!source.uid.equals(candidate.uid) && (!gatewaysOnly || candidate.type == DeviceType.GATEWAY)) {
                result.add(new Distance(candidate, meters(source.position, candidate.position)));
            }
        }
        result.sort(Comparator.comparingDouble((Distance distance) -> distance.meters)
                .thenComparing(distance -> distance.draft.uid));
        return Collections.unmodifiableList(result);
    }

    public static final class Distance {
        public final TmmDraft draft;
        public final double meters;

        private Distance(TmmDraft draft, double meters) {
            this.draft = draft;
            this.meters = meters;
        }
    }
}
