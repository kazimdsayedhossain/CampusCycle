package bd.ac.kuet.campuscycle.domain;

/**
 * Single source of truth for pilot tariff.
 * Mirrors supabase rate_cards version 1: 15 min / BDT 20.00 base,
 * +BDT 10.00 per extra 15-min block, 15..180 min.
 * All amounts in poisha (minor units). Server RPC remains authoritative.
 */
public final class TariffService {
    public static final int BASE_MINUTES = 15;
    public static final int BASE_CHARGE_POISHA = 2000;
    public static final int EXTRA_BLOCK_MINUTES = 15;
    public static final int EXTRA_BLOCK_CHARGE_POISHA = 1000;
    public static final int MIN_MINUTES = 15;
    public static final int MAX_MINUTES = 180;
    public static final double STUDENT_SUBSIDY_RATE = 0.25;

    /** Platform commission rate applied to the settled fare. */
    public static final double PLATFORM_FEE_RATE = 0.05;
    /** Platform commission cap per ride: BDT 1.50. */
    public static final int PLATFORM_FEE_CAP_POISHA = 150;

    /** Grace period after dueAt before an overdue fine starts accruing (15 min). */
    public static final int OVERDUE_GRACE_SECONDS = 900;
    /** One fine block accrues per 15 min past the grace period. */
    public static final int OVERDUE_FINE_BLOCK_SECONDS = 900;
    /** Fine per overdue block: BDT 5.00. */
    public static final int OVERDUE_BLOCK_POISHA = 500;
    /**
     * Abuse brake: this many separately-unpaid dues puts the account under
     * review — booking is blocked with a "contact the cycle office" message
     * until an admin waives or the dues clear. Fines themselves are uncapped.
     */
    public static final int OVERDUE_REVIEW_DUES_COUNT = 3;

    private TariffService() {}

    public static int quotePoisha(int minutes) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("Rental duration must be 15-180 minutes.");
        }
        int extra = Math.max(0, minutes - BASE_MINUTES);
        int blocks = (int) Math.ceil(extra / (double) EXTRA_BLOCK_MINUTES);
        return BASE_CHARGE_POISHA + blocks * EXTRA_BLOCK_CHARGE_POISHA;
    }

    public static int subsidyPoisha(int subtotalPoisha, boolean eligible) {
        if (!eligible) return 0;
        return (int) Math.round(subtotalPoisha * STUDENT_SUBSIDY_RATE);
    }

    public static String formatBdt(int poisha) {
        return String.format("BDT %.2f", poisha / 100.0);
    }

    /**
     * Platform commission on a settled fare: 5%, capped at {@link #PLATFORM_FEE_CAP_POISHA}.
     * Non-positive fares yield no fee. Rounding is half-up via {@link Math#round}.
     */
    public static int platformFeePoisha(int finalFarePoisha) {
        if (finalFarePoisha <= 0) return 0;
        long fee = Math.round(finalFarePoisha * PLATFORM_FEE_RATE);
        return (int) Math.min(fee, PLATFORM_FEE_CAP_POISHA);
    }

    /**
     * Bike-owner payout on a settled fare: fare minus the platform commission.
     * Never negative.
     */
    public static int ownerPayoutPoisha(int finalFarePoisha) {
        if (finalFarePoisha <= 0) return 0;
        return Math.max(0, finalFarePoisha - platformFeePoisha(finalFarePoisha));
    }

    /**
     * Splits an actually-collected amount across return-time debts.
     * Collection priority is fare-related debt (overtime) first, penalty
     * (overdue fine) last: the owner delivered the service, the fine is the
     * most deferrable part and becomes dues first.
     *
     * @return {@code {toOvertime, toFine}}; sums to {@code min(collected, owed)}.
     */
    public static int[] allocateCollection(int overtimeOwed, int fineOwed, int collected) {
        int o = Math.max(0, overtimeOwed);
        int f = Math.max(0, fineOwed);
        int c = Math.max(0, collected);
        int toOvertime = Math.min(o, c);
        int toFine = Math.min(f, c - toOvertime);
        return new int[]{toOvertime, toFine};
    }
}
