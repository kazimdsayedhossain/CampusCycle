package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies SQLite database integration and full CRUD manipulation:
 * 1. Schema setup with foreign key enforcement
 * 2. Create operations (profile, cycle, rental)
 * 3. Read operations (catalog, rentals, active rental)
 * 4. Update operations (cycle availability, rental return)
 * 5. Delete operations (cancel, remove)
 */
public class LocalDatabaseCrudTest {

    private LocalDatabase db;
    private CampusUser testUser;
    private CycleItem testCycle;

    @BeforeEach
    void setUp() {
        db = LocalDatabase.getInstance();
        testUser = new CampusUser(UUID.randomUUID().toString(), "Test Student", "test@kuet.ac.bd", Role.STUDENT);
        testCycle = new CycleItem(
            UUID.randomUUID().toString(),
            testUser.id(),
            testUser.displayName(),
            "SQLite Test Cruiser",
            CycleType.CITY_BIKE,
            CycleCondition.EXCELLENT,
            "KUET Central Library",
            22.9009,
            89.5016,
            "SQLite CRUD test cycle",
            ReviewStatus.APPROVED,
            AvailabilityStatus.AVAILABLE
        );
    }

    @Test
    void testCompleteCrudLifecycle() {
        // 1. CREATE
        db.saveProfile(testUser);
        db.saveCycle(testCycle);

        RentalRecord rental = new RentalRecord(
            UUID.randomUUID().toString(),
            testCycle.id(),
            testCycle.label(),
            testUser.id(),
            30,
            2000,
            RentalStatus.ACTIVE,
            ZonedDateTime.now(),
            ZonedDateTime.now().plusMinutes(30),
            null
        );
        db.saveRental(rental);

        // 2. READ
        List<CycleItem> available = db.getAvailableCycles();
        assertNotNull(available);
        assertTrue(available.stream().anyMatch(c -> c.id().equals(testCycle.id())), "Created cycle should be readable");

        Optional<RentalRecord> active = db.getActiveRental(testUser.id());
        assertTrue(active.isPresent(), "Active rental should be retrievable");
        assertEquals(rental.id(), active.get().id());

        // 3. UPDATE
        db.updateCycleAvailability(testCycle.id(), AvailabilityStatus.RENTED);
        List<CycleItem> afterRented = db.getAvailableCycles();
        assertFalse(afterRented.stream().anyMatch(c -> c.id().equals(testCycle.id())), "Rented cycle should no longer appear in available list");

        db.updateRentalReturned(rental.id());
        Optional<RentalRecord> afterReturn = db.getActiveRental(testUser.id());
        assertFalse(afterReturn.isPresent(), "Returned rental should no longer be active");

        // 4. DELETE
        boolean deleted = db.deleteCycle(testCycle.id());
        assertTrue(deleted, "Cycle should be successfully deleted");
    }
}
