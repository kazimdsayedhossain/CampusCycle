package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

public record RentalRecord(
        String id,
        String cycleId,
        String cycleLabel,
        String renterId,
        int requestedMinutes,
        int quotedAmountPoisha,
        RentalStatus status,
        ZonedDateTime startedAt,
        ZonedDateTime dueAt,
        ZonedDateTime returnedAt
) {
    public RentalRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(cycleId);
        Objects.requireNonNull(cycleLabel);
        Objects.requireNonNull(renterId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(dueAt);
    }
}
