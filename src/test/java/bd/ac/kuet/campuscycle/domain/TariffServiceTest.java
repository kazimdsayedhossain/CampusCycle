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
}
