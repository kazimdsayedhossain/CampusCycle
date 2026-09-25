package bd.ac.kuet.campuscycle.domain;

import java.util.Objects;

/** A display-safe cycle location. It contains no owner contact data. */
public record CycleMapPoint(String cycleId, String label, String pickupPoint, double latitude, double longitude) {
    public CycleMapPoint {
        Objects.requireNonNull(cycleId);
        Objects.requireNonNull(label);
        Objects.requireNonNull(pickupPoint);
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid map coordinate");
        }
    }
}
