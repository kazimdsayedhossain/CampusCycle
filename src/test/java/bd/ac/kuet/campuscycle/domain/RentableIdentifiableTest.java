package bd.ac.kuet.campuscycle.domain;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RentableIdentifiableTest {

    @Test
    void testCycleItemImplementsRentableAndIdentifiable() {
        CycleItem cycle = new CycleItem(
                "cycle-xyz",
                "owner-1",
                "Owner One",
                "Campus Fleet 01",
                CycleType.CITY_BIKE,
                CycleCondition.EXCELLENT,
                "KUET Main Gate",
                22.8987,
                89.4981,
                "Test cycle",
                ReviewStatus.APPROVED,
                AvailabilityStatus.AVAILABLE
        );

        // Identifiable contract
        assertTrue(cycle instanceof Identifiable);
        assertEquals("cycle-xyz", cycle.id());

        // Rentable contract
        assertTrue(cycle instanceof Rentable);
        Rentable rentable = cycle;
        assertEquals("cycle-xyz", rentable.id());
        assertEquals("Campus Fleet 01", rentable.label());
        assertEquals(AvailabilityStatus.AVAILABLE, rentable.availabilityStatus());
        assertEquals("KUET Main Gate", rentable.pickupPoint());
        assertTrue(rentable.isAvailable());
    }

    @Test
    void testBaseEntityImplementsIdentifiable() {
        String testId = "entity-" + UUID.randomUUID();
        BaseEntity dummy = new BaseEntity(testId, ZonedDateTime.now(), null) {
            @Override
            public void validate() throws IllegalArgumentException {}
        };

        assertTrue(dummy instanceof Identifiable);
        assertEquals(testId, dummy.id());
        assertEquals(testId, dummy.getId());
    }
}
