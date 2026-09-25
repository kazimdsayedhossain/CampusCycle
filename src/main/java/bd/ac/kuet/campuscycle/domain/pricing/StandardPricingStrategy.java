package bd.ac.kuet.campuscycle.domain.pricing;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;

/**
 * Standard pricing strategy:
 * Base fare: 1500 poisha (BDT 15.00) for up to 15 minutes,
 * plus 1000 poisha (BDT 10.00) per additional 15-minute block.
 */
public class StandardPricingStrategy implements PricingStrategy {

    public static final int BASE_MINUTES = 15;
    public static final int BASE_FARE_POISHA = 1500;
    public static final int ADDITIONAL_BLOCK_MINUTES = 15;
    public static final int ADDITIONAL_FARE_POISHA = 1000;

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
