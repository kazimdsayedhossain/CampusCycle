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
        int finalAmountPoisha,
        int overdueFinePoisha,
        String dropoffHub,
        RentalStatus status,
        ZonedDateTime startedAt,
        ZonedDateTime dueAt,
        ZonedDateTime returnedAt,
        int platformFeePoisha,
        int ownerPayoutPoisha
) {
    public RentalRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(cycleId);
        Objects.requireNonNull(cycleLabel);
        Objects.requireNonNull(renterId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(dueAt);
        if (dropoffHub == null) {
            dropoffHub = "";
        }
        if (platformFeePoisha < 0) {
            throw new IllegalArgumentException("Platform fee must not be negative.");
        }
        if (ownerPayoutPoisha < 0) {
            throw new IllegalArgumentException("Owner payout must not be negative.");
        }
    }

    public RentalRecord(
            String id,
            String cycleId,
            String cycleLabel,
            String renterId,
            int requestedMinutes,
            int quotedAmountPoisha,
            int finalAmountPoisha,
            RentalStatus status,
            ZonedDateTime startedAt,
            ZonedDateTime dueAt,
            ZonedDateTime returnedAt
    ) {
        this(id, cycleId, cycleLabel, renterId, requestedMinutes, quotedAmountPoisha,
                finalAmountPoisha, 0, "", status, startedAt, dueAt, returnedAt,
                TariffService.platformFeePoisha(finalAmountPoisha),
                TariffService.ownerPayoutPoisha(finalAmountPoisha));
    }

    public RentalRecord(
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
        this(id, cycleId, cycleLabel, renterId, requestedMinutes, quotedAmountPoisha,
                status == RentalStatus.RETURNED ? quotedAmountPoisha : 0,
                status, startedAt, dueAt, returnedAt);
    }

    public int effectiveFarePoisha() {
        return finalAmountPoisha > 0 ? finalAmountPoisha : quotedAmountPoisha;
    }

    /** Fare plus any overdue fine: the total the rider actually paid or owes. */
    public int totalPaidPoisha() {
        return effectiveFarePoisha() + Math.max(0, overdueFinePoisha);
    }

    /** True once money has moved: a terminal rental or any positive settlement. */
    public boolean hasSettlement() {
        return status == RentalStatus.RETURNED
                || status == RentalStatus.CLOSED
                || status == RentalStatus.DISPUTED
                || finalAmountPoisha > 0;
    }
}
