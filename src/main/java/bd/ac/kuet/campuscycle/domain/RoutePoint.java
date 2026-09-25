package bd.ac.kuet.campuscycle.domain;

/** A latitude/longitude coordinate used to display a cycle route. */
public record RoutePoint(double latitude, double longitude) {
    public RoutePoint {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid route coordinate");
        }
    }
}
