package bd.ac.kuet.campuscycle.domain;


/**
 * Platform-wide earnings roll-up over SETTLED (RETURNED) rentals.
 * Pre-fee rows (NULL split) contribute their fare to gross with zero fee.
 * All amounts are integer poisha. Admin-only read.
 */
public record PlatformEarnings(
        long settledRides,
        int grossPoisha,
        int feesPoisha,
        int payoutsPoisha
) {
    public PlatformEarnings {
        if (settledRides < 0 || grossPoisha < 0 || feesPoisha < 0 || payoutsPoisha < 0) {
            throw new IllegalArgumentException("Earnings must not be negative.");
        }
    }

    public static PlatformEarnings empty() {
        return new PlatformEarnings(0, 0, 0, 0);
    }

    public boolean isEmpty() {
        return settledRides == 0;
    }
}
