package bd.ac.kuet.campuscycle.domain;

import bd.ac.kuet.campuscycle.domain.pricing.PricingStrategy;
import bd.ac.kuet.campuscycle.domain.pricing.RiderPricingStrategy;
import bd.ac.kuet.campuscycle.domain.pricing.StandardPricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PricingStrategyTest {

    private CycleItem testCycle;
    private CampusUser studentUser;
    private CampusUser adminUser;

    @BeforeEach
    void setUp() {
        studentUser = new CampusUser("user-" + UUID.randomUUID(), "Rahim Student", "rahim@kuet.ac.bd", Role.STUDENT);
        adminUser = new CampusUser("admin-" + UUID.randomUUID(), "Admin Officer", "admin@kuet.ac.bd", Role.ADMIN);

        testCycle = new CycleItem(
                "cycle-" + UUID.randomUUID(),
                "owner-1",
                "Owner One",
                "Standard Commuter",
                CycleType.CITY_BIKE,
                CycleCondition.EXCELLENT,
                "KUET Central Mosque",
                22.9009,
                89.5016,
                "Fleet test bike",
                ReviewStatus.APPROVED,
                AvailabilityStatus.AVAILABLE
        );
    }

    @Test
    void testStandardPricingStrategy() {
        PricingStrategy standard = new StandardPricingStrategy();
        assertEquals("Standard", standard.getStrategyName());

        // Zero or negative
        assertEquals(0, standard.calculatePricePoisha(testCycle, 0, studentUser));
        assertEquals(0, standard.calculatePricePoisha(testCycle, -5, studentUser));

        // Base 15 minutes: 2000 poisha (৳20.00)
        assertEquals(2000, standard.calculatePricePoisha(testCycle, 15, studentUser));
        assertEquals(2000, standard.calculatePricePoisha(testCycle, 10, studentUser));

        // 30 minutes: 2000 + 1000 = 3000 poisha (৳30.00)
        assertEquals(3000, standard.calculatePricePoisha(testCycle, 30, studentUser));

        // 45 minutes: 2000 + 2000 = 4000 poisha (৳40.00)
        assertEquals(4000, standard.calculatePricePoisha(testCycle, 45, studentUser));

        // Fractional block (e.g. 20 minutes -> rounds up to 1 extra block)
        assertEquals(3000, standard.calculatePricePoisha(testCycle, 20, studentUser));
    }

    @Test
    void testRiderPricingStrategyForStudent() {
        PricingStrategy riderStrategy = new RiderPricingStrategy();
        assertEquals("Rider", riderStrategy.getStrategyName());

        // 25% discount on base fare of 2000 poisha: 2000 * 0.75 = 1500 poisha (৳15.00)
        int price15 = riderStrategy.calculatePricePoisha(testCycle, 15, studentUser);
        assertEquals(1500, price15);

        // 25% discount on 30 min standard (3000 poisha): 3000 * 0.75 = 2250 poisha (৳22.50)
        int price30 = riderStrategy.calculatePricePoisha(testCycle, 30, studentUser);
        assertEquals(2250, price30);
    }

    @Test
    void testRiderPricingStrategyForNonStudent() {
        PricingStrategy riderStrategy = new RiderPricingStrategy();

        // Admin does not get student subsidy
        int adminPrice15 = riderStrategy.calculatePricePoisha(testCycle, 15, adminUser);
        assertEquals(2000, adminPrice15);

        int adminPrice30 = riderStrategy.calculatePricePoisha(testCycle, 30, adminUser);
        assertEquals(3000, adminPrice30);
    }
}
