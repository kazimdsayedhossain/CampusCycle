package bd.ac.kuet.campuscycle.domain;

import java.util.List;
import java.util.Objects;

/**
 * Earnings roll-up for one bike owner over SETTLED (RETURNED) rentals only.
 * Pre-fee rows (NULL split) contribute their fare to gross with zero fee.
 * All amounts are integer poisha.
 */
public record OwnerEarnings(
        String ownerId,
        List<BikeEarning> bikes,
        long settledRides,
        int grossPoisha,
        int feesPoisha,
        int netPayoutPoisha
) {
    public OwnerEarnings {
        Objects.requireNonNull(ownerId, "Owner id must not be null");
        bikes = bikes == null ? List.of() : List.copyOf(bikes);
    }

    /** Per-bike settled earnings slice. */
    public record BikeEarning(
            String cycleId,
            String cycleLabel,
            long settledRides,
            int grossPoisha,
            int feesPoisha,
            int netPayoutPoisha
    ) {
        public BikeEarning {
            Objects.requireNonNull(cycleId, "Cycle id must not be null");
            Objects.requireNonNull(cycleLabel, "Cycle label must not be null");
        }
    }

    public static OwnerEarnings empty(String ownerId) {
        return new OwnerEarnings(ownerId, List.of(), 0, 0, 0, 0);
    }
}
