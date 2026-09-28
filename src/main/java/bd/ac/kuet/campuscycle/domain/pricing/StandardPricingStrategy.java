package bd.ac.kuet.campuscycle.domain.pricing;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.TariffService;

/**
 * Standard pricing strategy:
 * Base fare: 2000 poisha (BDT 20.00) for up to 15 minutes,
 * plus 1000 poisha (BDT 10.00) per additional 15-minute block.
 *
 * <p>All rates delegate to {@link TariffService}, the single source of truth
 * for the pilot tariff (P-028). Do not introduce local copies.
 */
public class StandardPricingStrategy implements PricingStrategy {

    public static final int BASE_MINUTES = TariffService.BASE_MINUTES;
    public static final int BASE_FARE_POISHA = TariffService.BASE_CHARGE_POISHA;
    public static final int ADDITIONAL_BLOCK_MINUTES = TariffService.EXTRA_BLOCK_MINUTES;
    public static final int ADDITIONAL_FARE_POISHA = TariffService.EXTRA_BLOCK_CHARGE_POISHA;

    @Override
    public int calculatePricePoisha(CycleItem cycle, int durationMinutes, CampusUser user) {
        if (durationMinutes <= 0) {
            return 0;
        }
        if (durationMinutes <= BASE_MINUTES) {
            return BASE_FARE_POISHA;
        }
        int extraMinutes = durationMinutes - BASE_MINUTES;
        int blocks = (int) Math.ceil((double) extraMinutes / ADDITIONAL_BLOCK_MINUTES);
        return BASE_FARE_POISHA + (blocks * ADDITIONAL_FARE_POISHA);
    }

    @Override
    public String getStrategyName() {
        return "Standard";
    }
}
