package bd.ac.kuet.campuscycle.domain;

/**
 * Interface contract for fleet assets that can be rented across campus stations.
 */
public interface Rentable extends Identifiable {
    String label();
    AvailabilityStatus availabilityStatus();
    String pickupPoint();

    default boolean isAvailable() {
        return availabilityStatus() == AvailabilityStatus.AVAILABLE;
    }
}
