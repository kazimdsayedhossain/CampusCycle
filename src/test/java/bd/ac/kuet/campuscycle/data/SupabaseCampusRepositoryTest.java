package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SupabaseCampusRepositoryTest {

    private final CampusUser student = new CampusUser("3d1e3d69-ffc6-494f-a42c-26eeb258b581", "Arafat Rahman", "arafat@kuet.ac.bd", Role.STUDENT);
    private final CampusUser admin = new CampusUser("56d6f9dc-0ca7-4b49-9f9e-3c48a1b2089a", "KUET Cycle Office", "cycleoffice@kuet.ac.bd", Role.ADMIN);

    @Test
    void testCatalogAndPendingQueries() {
        if (!DatabaseConnection.isAvailable()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        List<CycleItem> catalog = repo.catalog(student);
        assertNotNull(catalog);
        assertFalse(catalog.isEmpty(), "Catalog should contain live seeded cycles");
        System.out.println("Live catalog count: " + catalog.size());

        List<CycleItem> pending = repo.pendingCycles();
        assertNotNull(pending);
        System.out.println("Live pending cycles count: " + pending.size());
    }

    @Test
    void testBookingAndReturnLifecycle() {
        if (!DatabaseConnection.isAvailable()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        List<CycleItem> catalog = repo.catalog(student);
        if (catalog.isEmpty()) return;

        CycleItem cycle = catalog.get(0);
        System.out.println("Booking cycle: " + cycle.id() + " (" + cycle.label() + ")");

        // Book cycle
        RentalRecord rental = repo.book(student, cycle.id(), 30);
        assertNotNull(rental);
        assertEquals(RentalStatus.ACTIVE, rental.status());
        assertEquals(cycle.id(), rental.cycleId());
        System.out.println("Booked rental ID: " + rental.id() + ", fare: " + rental.quotedAmountPoisha() + " poisha");

        // Verify active rental query
        RentalRecord active = repo.activeRental(student);
        assertNotNull(active);
        assertEquals(rental.id(), active.id());

        // Return cycle
        repo.returnRental(student, rental.id());
        System.out.println("Returned rental successfully.");

        // Verify no active rental
        RentalRecord postReturn = repo.activeRental(student);
        assertNull(postReturn);
    }
}
