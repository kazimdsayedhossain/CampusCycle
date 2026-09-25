package bd.ac.kuet.campuscycle.domain;

import java.util.Objects;

public record DisputeItem(String disputeId, String rentalId, String reason, String state) {
    public DisputeItem {
        Objects.requireNonNull(disputeId);
        Objects.requireNonNull(rentalId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(state);
    }
}
