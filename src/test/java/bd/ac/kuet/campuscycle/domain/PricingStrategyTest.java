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
                "KUET Central Library",
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

        // Base 15 minutes: 1500 poisha (৳15.00)
        assertEquals(1500, standard.calculatePricePoisha(testCycle, 15, studentUser));
        assertEquals(1500, standard.calculatePricePoisha(testCycle, 10, studentUser));

        // 30 minutes: 1500 + 1000 = 2500 poisha (৳25.00)
        assertEquals(2500, standard.calculatePricePoisha(testCycle, 30, studentUser));

        // 45 minutes: 1500 + 2000 = 3500 poisha (৳35.00)
        assertEquals(3500, standard.calculatePricePoisha(testCycle, 45, studentUser));

        // Fractional block (e.g. 20 minutes -> rounds up to 1 extra block)
        assertEquals(2500, standard.calculatePricePoisha(testCycle, 20, studentUser));
    }

    @Test
    void testRiderPricingStrategyForStudent() {
        PricingStrategy riderStrategy = new RiderPricingStrategy();
        assertEquals("Rider", riderStrategy.getStrategyName());

        // 20% discount on base fare of 1500 poisha: 1500 * 0.8 = 1200 poisha (৳12.00)
        int price15 = riderStrategy.calculatePricePoisha(testCycle, 15, studentUser);
        assertEquals(1200, price15);

        // 20% discount on 30 min standard (2500 poisha): 2500 * 0.8 = 2000 poisha (৳20.00)
        int price30 = riderStrategy.calculatePricePoisha(testCycle, 30, studentUser);
        assertEquals(2000, price30);
    }

    @Test
    void testRiderPricingStrategyForNonStudent() {
        PricingStrategy riderStrategy = new RiderPricingStrategy();

        // Admin does not get student subsidy
        int adminPrice15 = riderStrategy.calculatePricePoisha(testCycle, 15, adminUser);
        assertEquals(1500, adminPrice15);

        int adminPrice30 = riderStrategy.calculatePricePoisha(testCycle, 30, adminUser);
        assertEquals(2500, adminPrice30);
    }
}
