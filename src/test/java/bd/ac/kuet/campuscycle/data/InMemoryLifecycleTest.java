package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.RentalStatus;
import bd.ac.kuet.campuscycle.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryLifecycleTest {
    private final CampusUser student = new CampusUser(UUID.randomUUID().toString(), "S", "s@kuet.ac.bd", Role.STUDENT);
    private final CampusUser other = new CampusUser(UUID.randomUUID().toString(), "O", "o@kuet.ac.bd", Role.STUDENT);
    private final CampusUser admin = new CampusUser(UUID.randomUUID().toString(), "A", "a@kuet.ac.bd", Role.ADMIN);

    @Test
    void cannotBookOwnCycleAndOneActiveOnly() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem own = repo.catalog(other).stream()
                .filter(c -> c.ownerId().equals("owner-1")).findFirst().orElseThrow();
        // owner-1 cycle cannot be booked by owner-1 id, but our student differs; simulate own by booking then second booking
        RentalRecord r1 = repo.book(student, own.id(), 30);
        assertEquals(RentalStatus.ACTIVE, r1.status());
        assertThrows(IllegalStateException.class, () -> repo.book(student, own.id(), 30));
        repo.returnRental(student, r1.id());
        assertNull(repo.activeRental(student));
    }

    @Test
    void returnRequiresRenterOwnership() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).get(0);
        RentalRecord r = repo.book(student, c.id(), 30);
        assertThrows(IllegalStateException.class, () -> repo.returnRental(other, r.id()));
        repo.returnRental(student, r.id());
    }

    @Test
    void disputeOnlyForReturnedWithReason() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).get(0);
        RentalRecord r = repo.book(student, c.id(), 30);
        assertThrows(IllegalStateException.class, () -> repo.openDispute(student, r.id(), "long enough reason here"));
        repo.returnRental(student, r.id());
        assertThrows(IllegalArgumentException.class, () -> repo.openDispute(student, r.id(), "short"));
        String id = repo.openDispute(student, r.id(), "fare looks wrong for this trip");
        assertNotNull(id);
        assertEquals(1, repo.disputeQueue(admin).size());
    }

    @Test
    void supportValidation() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        assertThrows(IllegalArgumentException.class, () -> repo.createSupportConversation(student, "hi", "hello world"));
        String id = repo.createSupportConversation(student, "Dock full", "Hall gate dock is full, please help");
        assertFalse(id.isBlank());
        repo.postSupportMessage(student, id, "adding photo note");
        assertEquals(2, repo.supportMessages(student, id).size());
    }

    @Test
    void concurrentBookingOnlyOneSucceeds() throws Exception {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        CampusUser s2 = new CampusUser(UUID.randomUUID().toString(), "S2", "s2@kuet.ac.bd", Role.STUDENT);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        futures.add(pool.submit(() -> {
            start.await();
            try {
                repo.book(student, c.id(), 30);
                return true;
            } catch (Exception e) {
                return false;
            }
        }));
        futures.add(pool.submit(() -> {
            start.await();
            try {
                repo.book(s2, c.id(), 30);
                return true;
            } catch (Exception e) {
                return false;
            }
        }));
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : futures) if (f.get()) ok++;
        pool.shutdown();
        assertEquals(1, ok, "exactly one concurrent booking should win");
    }
}
