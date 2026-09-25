package bd.ac.kuet.campuscycle.domain;

import java.util.Objects;

public record CycleItem(
        String id,
        String ownerId,
        String ownerName,
        String label,
        CycleType type,
        CycleCondition condition,
        String pickupPoint,
        double latitude,
        double longitude,
        String description,
        ReviewStatus reviewStatus,
        AvailabilityStatus availabilityStatus
) {
    public CycleItem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(ownerName);
        Objects.requireNonNull(label);
        Objects.requireNonNull(type);
        Objects.requireNonNull(condition);
        Objects.requireNonNull(pickupPoint);
        Objects.requireNonNull(description);
        Objects.requireNonNull(reviewStatus);
        Objects.requireNonNull(availabilityStatus);
    }

    public boolean canBeBookedBy(String userId) {
        return reviewStatus == ReviewStatus.APPROVED
                && availabilityStatus == AvailabilityStatus.AVAILABLE
                && !ownerId.equals(userId);
    }
}
