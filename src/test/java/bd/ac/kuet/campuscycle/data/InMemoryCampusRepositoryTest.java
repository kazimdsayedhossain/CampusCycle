package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class InMemoryCampusRepositoryTest {

    private InMemoryCampusRepository repository;
    private CampusUser student;
    private CampusUser admin;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCampusRepository();
        student = new CampusUser("test-student", "Arafat Rahman", "arafat@kuet.ac.bd", Role.STUDENT);
        admin = new CampusUser("test-admin", "KUET Transport Office", "transport@kuet.ac.bd", Role.ADMIN);
    }

    @Test
    void booksAndReturnsCycleSuccessfully() {
        List<CycleItem> catalog = repository.catalog(student);
        assertFalse(catalog.isEmpty(), "Catalog should contain available cycles");

        CycleItem cycle = catalog.get(0);
        RentalRecord rental = repository.book(student, cycle.id(), 30);

        assertNotNull(rental);
        assertEquals(RentalStatus.ACTIVE, rental.status());
        assertEquals(cycle.id(), rental.cycleId());
        assertEquals(student.id(), rental.renterId());
        assertEquals(30, rental.requestedMinutes());
        assertTrue(rental.quotedAmountPoisha() > 0);

        // Cannot book another while active
        assertThrows(IllegalStateException.class, () -> repository.book(student, catalog.get(1).id(), 15));

        // Return rental
        repository.returnRental(student, rental.id());
        assertNull(repository.activeRental(student));

        List<RentalRecord> history = repository.rentals(student);
        assertEquals(1, history.size());
        assertEquals(RentalStatus.RETURNED, history.get(0).status());
    }

    @Test
    void reviewsPendingCycleListing() {
        List<CycleItem> pending = repository.pendingCycles();
        assertFalse(pending.isEmpty(), "Should have a pending cycle for review");

        CycleItem target = pending.get(0);
        repository.reviewCycle(admin, target.id(), true, "Hardware inspected and approved");

        List<CycleItem> afterReview = repository.pendingCycles();
        assertFalse(afterReview.stream().anyMatch(c -> c.id().equals(target.id())));
    }
}
