package bd.ac.kuet.campuscycle.domain.pricing;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;

/**
 * Strategy interface for calculating rental prices.
 */
public interface PricingStrategy {

    /**
     * Calculates the price in poisha for renting a cycle for the given duration.
     *
     * @param cycle the cycle being rented
     * @param durationMinutes requested rental duration in minutes
     * @param user the campus user renting the cycle
     * @return calculated price in poisha (minor currency unit)
     */
    int calculatePricePoisha(CycleItem cycle, int durationMinutes, CampusUser user);

    /**
     * @return human-readable name of this pricing strategy
     */
    String getStrategyName();
}
