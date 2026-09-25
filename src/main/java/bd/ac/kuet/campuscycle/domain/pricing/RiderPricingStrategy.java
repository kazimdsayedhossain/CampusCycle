package bd.ac.kuet.campuscycle.domain.pricing;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.Role;

/**
 * Rider pricing strategy:
 * Applies a 20% discount for students/riders on base rate calculation.
 */
public class RiderPricingStrategy implements PricingStrategy {

    public static final double DISCOUNT_RATE = 0.20;
    private final StandardPricingStrategy standardPricing;

    public RiderPricingStrategy() {
        this(new StandardPricingStrategy());
    }

    public RiderPricingStrategy(StandardPricingStrategy standardPricing) {
        this.standardPricing = standardPricing != null ? standardPricing : new StandardPricingStrategy();
    }

    @Override
    public int calculatePricePoisha(CycleItem cycle, int durationMinutes, CampusUser user) {
        int standardPrice = standardPricing.calculatePricePoisha(cycle, durationMinutes, user);
        if (standardPrice <= 0) {
            return 0;
        }

        boolean isRider = (user == null || user.role() == Role.STUDENT);
        if (!isRider) {
            return standardPrice;
        }

        int discount = (int) Math.round(standardPrice * DISCOUNT_RATE);
        return Math.max(0, standardPrice - discount);
    }

    @Override
    public String getStrategyName() {
        return "Rider";
    }

    public double getDiscountRate() {
        return DISCOUNT_RATE;
    }
}
