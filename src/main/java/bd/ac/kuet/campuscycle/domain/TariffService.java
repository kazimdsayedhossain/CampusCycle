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
        return (int) (subtotalPoisha * STUDENT_SUBSIDY_RATE);
    }

    public static String formatBdt(int poisha) {
        return String.format("BDT %.2f", poisha / 100.0);
    }
}
