package bd.ac.kuet.campuscycle.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TariffServiceTest {
    @Test
    void boundaryFares() {
        assertEquals(2000, TariffService.quotePoisha(15));
        assertEquals(3000, TariffService.quotePoisha(16));
        assertEquals(3000, TariffService.quotePoisha(30));
        assertEquals(13000, TariffService.quotePoisha(180));
        assertEquals("BDT 20.00", TariffService.formatBdt(2000));
    }

    @Test
    void invalidDurationsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TariffService.quotePoisha(14));
        assertThrows(IllegalArgumentException.class, () -> TariffService.quotePoisha(181));
    }

    @Test
    void subsidyOnlyWhenEligible() {
        assertEquals(500, TariffService.subsidyPoisha(2000, true));
        assertEquals(0, TariffService.subsidyPoisha(2000, false));
    }

    @Test
    void platformFeeIsFivePercentCappedAt150() {
        assertEquals(100, TariffService.platformFeePoisha(2000));
        // 2250 * 0.05 = 112.5 rounds half-up to 113
        assertEquals(113, TariffService.platformFeePoisha(2250));
        // Cap boundary: 3000 * 0.05 = 150 exactly
        assertEquals(150, TariffService.platformFeePoisha(3000));
        // Above cap: clamped
        assertEquals(150, TariffService.platformFeePoisha(3001));
        assertEquals(150, TariffService.platformFeePoisha(4000));
        assertEquals(150, TariffService.platformFeePoisha(13000));
        // Non-positive fares yield no fee
        assertEquals(0, TariffService.platformFeePoisha(0));
        assertEquals(0, TariffService.platformFeePoisha(-50));
    }

    @Test
    void ownerPayoutIsFareMinusFee() {
        assertEquals(1900, TariffService.ownerPayoutPoisha(2000));
        assertEquals(2137, TariffService.ownerPayoutPoisha(2250));
        assertEquals(2850, TariffService.ownerPayoutPoisha(3000));
        assertEquals(3850, TariffService.ownerPayoutPoisha(4000));
        assertEquals(0, TariffService.ownerPayoutPoisha(0));
        assertEquals(0, TariffService.ownerPayoutPoisha(-50));
    }

    @Test
    void collectionPaysOvertimeBeforeFine() {
        // Full coverage pays both in full.
        assertArrayEquals(new int[]{800, 500}, TariffService.allocateCollection(800, 500, 1300));
        assertArrayEquals(new int[]{800, 500}, TariffService.allocateCollection(800, 500, 5000));
        // Partial coverage fills overtime first, remainder to fine.
        assertArrayEquals(new int[]{800, 200}, TariffService.allocateCollection(800, 500, 1000));
        assertArrayEquals(new int[]{300, 0}, TariffService.allocateCollection(800, 500, 300));
        // Nothing collected pays nothing; negatives clamp to zero.
        assertArrayEquals(new int[]{0, 0}, TariffService.allocateCollection(800, 500, 0));
        assertArrayEquals(new int[]{0, 0}, TariffService.allocateCollection(-10, -5, -3));
        // Fine-only and overtime-only debts.
        assertArrayEquals(new int[]{0, 400}, TariffService.allocateCollection(0, 500, 400));
        assertArrayEquals(new int[]{700, 0}, TariffService.allocateCollection(700, 0, 700));
    }

    @Test
    void feePlusPayoutEqualsFare() {
        for (int fare : new int[]{1, 100, 1500, 2000, 2250, 2999, 3000, 3001, 9750, 13000}) {
            assertEquals(fare,
                    TariffService.platformFeePoisha(fare) + TariffService.ownerPayoutPoisha(fare),
                    "fee + payout must equal fare for " + fare);
        }
    }
}
