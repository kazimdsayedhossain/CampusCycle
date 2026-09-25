package bd.ac.kuet.campuscycle.domain;

import java.util.List;
import java.util.Objects;

/** A display-safe cycling route returned by the configured directions provider. */
public record CycleRoute(String destinationCycleId, List<RoutePoint> points, int distanceMetres, int durationSeconds) {
    public CycleRoute {
        Objects.requireNonNull(destinationCycleId);
        points = List.copyOf(Objects.requireNonNull(points));
        if (points.size() < 2 || distanceMetres < 0 || durationSeconds < 0) {
            throw new IllegalArgumentException("Invalid route response");
        }
    }
}
