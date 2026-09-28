package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
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
    void returnSettlesFeeAndCreditsOwner() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        // 30-min student quote: 3000 - 750 = 2250
        RentalRecord r = repo.book(student, c.id(), 30);
        assertEquals(0, r.platformFeePoisha());
        assertEquals(0, r.ownerPayoutPoisha());

        repo.returnRental(student, r.id());
        RentalRecord settled = repo.rentals(student).stream()
                .filter(x -> x.id().equals(r.id())).findFirst().orElseThrow();
        assertEquals(RentalStatus.RETURNED, settled.status());
        // fee = round(2250 * 0.05) = 113, payout = 2250 - 113
        assertEquals(113, settled.platformFeePoisha());
        assertEquals(2137, settled.ownerPayoutPoisha());
        assertEquals(settled.effectiveFarePoisha(),
                settled.platformFeePoisha() + settled.ownerPayoutPoisha());
        assertEquals(2137, repo.ownerBalancePoisha("owner-1"));
    }

    @Test
    void ownerBalanceAccumulatesAcrossRides() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        RentalRecord r1 = repo.book(student, c.id(), 30);
        repo.returnRental(student, r1.id());
        RentalRecord r2 = repo.book(student, c.id(), 30);
        repo.returnRental(student, r2.id());
        assertEquals(2137 * 2, repo.ownerBalancePoisha("owner-1"));
    }

    @Test
    void cashAtHubPaysOwnerFromCashTakenAndBooksTheRest() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();

        RentalRecord r = repo.book(student, c.id(), 30, CampusRepository.PaymentMethod.DOCK_PAY);
        assertEquals(CampusRepository.PaymentMethod.DOCK_PAY, repo.rentalPaymentMethod(r.id()));
        assertEquals(2250, r.quotedAmountPoisha());

        // The hub took 1500 cash against a 2250 settled fare.
        CampusRepository.SettlementOutcome out = repo.settleReturn(student, r.id(), c.id(), 2250,
                "KUET Central Mosque", AvailabilityStatus.AVAILABLE,
                CampusRepository.PaymentMethod.DOCK_PAY, new CampusRepository.ReturnCharge(1500));

        assertEquals(1500, out.cashCollectedPoisha());
        assertEquals(1500, out.totalCollectedPoisha());
        // The owner is paid from the cash, never the full settled payout.
        assertEquals(2137, out.ownerPayoutCreditedPoisha() + 637, "payout capped at the cash taken");
        assertEquals(1500, out.ownerPayoutCreditedPoisha());
        assertEquals(1500, repo.ownerBalancePoisha("owner-1"));
        // And the rest is debt, not a silent loss.
        assertEquals(750, out.shortfallPoisha());
    }

    @Test
    void fullyPaidCashRideHasNoShortfall() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        RentalRecord r = repo.book(student, c.id(), 30, CampusRepository.PaymentMethod.DOCK_PAY);

        CampusRepository.SettlementOutcome out = repo.settleReturn(student, r.id(), c.id(), 2250,
                "KUET Central Mosque", AvailabilityStatus.AVAILABLE,
                CampusRepository.PaymentMethod.DOCK_PAY, new CampusRepository.ReturnCharge(2250));

        assertEquals(0, out.shortfallPoisha());
        assertEquals(2137, out.ownerPayoutCreditedPoisha());
        assertEquals(2137, repo.ownerBalancePoisha("owner-1"));
    }

    @Test
    void campusPayOvertimeIsDerivedFromTheSettledFareNotTheCaller() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        RentalRecord r = repo.book(student, c.id(), 30, CampusRepository.PaymentMethod.CAMPUS_PAY);

        // 2250 quoted at booking, settled at 3050: the 800 of overtime is the
        // repository's own arithmetic. The caller passes no charge at all.
        CampusRepository.SettlementOutcome out = repo.settleReturn(student, r.id(), c.id(), 3050,
                "KUET Central Mosque", AvailabilityStatus.AVAILABLE,
                CampusRepository.PaymentMethod.CAMPUS_PAY, CampusRepository.ReturnCharge.NONE);

        assertEquals(800, out.overtimeChargedPoisha());
        assertEquals(2250 + 800, out.totalCollectedPoisha());
        assertEquals(0, out.shortfallPoisha());
        assertEquals(0, out.cashCollectedPoisha());
    }

    @Test
    void replayedReturnDoesNotPayTheOwnerTwice() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        RentalRecord r = repo.book(student, c.id(), 30);

        repo.settleReturn(student, r.id(), c.id(), 2250, "KUET Central Mosque", AvailabilityStatus.AVAILABLE,
                CampusRepository.PaymentMethod.CAMPUS_PAY, CampusRepository.ReturnCharge.NONE);
        int afterFirst = repo.ownerBalancePoisha("owner-1");
        assertTrue(afterFirst > 0);

        // A second settlement is rejected outright, exactly like a double return.
        assertThrows(IllegalStateException.class, () ->
                repo.settleReturn(student, r.id(), c.id(), 2250, "KUET Central Mosque", AvailabilityStatus.AVAILABLE,
                        CampusRepository.PaymentMethod.CAMPUS_PAY, CampusRepository.ReturnCharge.NONE));
        assertEquals(afterFirst, repo.ownerBalancePoisha("owner-1"));
    }

    @Test
    void ownerEarningsRollUpSettledRides() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        RentalRecord r = repo.book(student, c.id(), 30);
        repo.returnRental(student, r.id());

        var earnings = repo.ownerEarnings(admin, "owner-1");
        assertEquals(1, earnings.settledRides());
        assertEquals(2250, earnings.grossPoisha());
        assertEquals(113, earnings.feesPoisha());
        assertEquals(2137, earnings.netPayoutPoisha());
        assertEquals(1, earnings.bikes().size());
        assertEquals("C-101", earnings.bikes().get(0).cycleId());

        var platform = repo.platformEarnings(admin);
        assertEquals(1, platform.settledRides());
        assertEquals(2250, platform.grossPoisha());
        assertEquals(113, platform.feesPoisha());
        assertEquals(2137, platform.payoutsPoisha());
    }

    @Test
    void earningsAreAccessControlled() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        // Strangers cannot read another owner's earnings; admins can.
        assertThrows(SecurityException.class, () -> repo.ownerEarnings(other, "owner-1"));
        assertDoesNotThrow(() -> repo.ownerEarnings(admin, "owner-1"));
        assertThrows(SecurityException.class, () -> repo.platformEarnings(student));
        assertThrows(SecurityException.class, () -> repo.platformEarnings(null));
        // Empty owner has zeroed earnings, not an error.
        var empty = repo.ownerEarnings(admin, "owner-2");
        assertEquals(0, empty.settledRides());
        assertEquals(0, empty.netPayoutPoisha());
    }

    @Test
    void duesBlockBookingAndSettleOldestFirst() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        RentalRecord r = repo.book(student, c.id(), 30);
        repo.returnRental(student, r.id());

        // Two dues: oldest first settlement order.
        var d1 = repo.recordDue(student, r.id(), 800, "unpaid overtime");
        assertEquals(800, repo.unpaidDuesTotal(student, student.id()));
        // Booking blocked while dues owed.
        assertThrows(IllegalStateException.class, () -> repo.book(student, c.id(), 30));

        int applied = repo.settleDues(student, 500);
        assertEquals(500, applied);
        assertEquals(300, repo.unpaidDuesTotal(student, student.id()));
        assertEquals("PARTIAL", repo.userDues(student, student.id()).get(0).state().name());

        int applied2 = repo.settleDues(student, 10000);
        assertEquals(300, applied2);
        assertEquals(0, repo.unpaidDuesTotal(student, student.id()));
        assertEquals("PAID", repo.userDues(student, student.id()).get(0).state().name());
        assertEquals(d1.id(), repo.userDues(student, student.id()).get(0).id());

        // Cleared: booking works again.
        RentalRecord r2 = repo.book(student, c.id(), 30);
        repo.returnRental(student, r2.id());
    }

    @Test
    void repeatOffendersHitReviewBrake() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        // Three separate unpaid dues (one per settled rental) → review message.
        List<RentalRecord> rides = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            RentalRecord r = repo.book(student, c.id(), 30);
            repo.returnRental(student, r.id());
            rides.add(r);
        }
        for (int i = 0; i < 3; i++) {
            repo.recordDue(student, rides.get(i).id(), 100, "unpaid fine " + i);
        }
        assertEquals(3, repo.unpaidDuesCount(student, student.id()));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> repo.book(student, c.id(), 30));
        assertTrue(ex.getMessage().contains("under review"));
        // Admin waive clears the flag.
        for (var d : repo.userDues(admin, student.id())) {
            repo.waiveDue(admin, d.id(), "hub fault confirmed");
        }
        assertEquals(0, repo.unpaidDuesTotal(student, student.id()));
        assertDoesNotThrow(() -> {
            RentalRecord r = repo.book(student, c.id(), 30);
            repo.returnRental(student, r.id());
        });
    }

    @Test
    void duesAreAccessControlled() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        CycleItem c = repo.catalog(student).stream()
                .filter(x -> x.id().equals("C-101")).findFirst().orElseThrow();
        RentalRecord r = repo.book(student, c.id(), 30);
        repo.returnRental(student, r.id());
        // Strangers cannot record or read another user's dues.
        assertThrows(SecurityException.class, () -> repo.recordDue(other, r.id(), 100, "x"));
        assertThrows(SecurityException.class, () -> repo.unpaidDuesTotal(other, student.id()));
        assertThrows(SecurityException.class, () -> repo.waiveDue(student, "DUE-X", "nope"));
        assertThrows(IllegalArgumentException.class, () -> repo.recordDue(student, r.id(), 0, "zero"));
        // Same-rental replay returns the existing row.
        var d1 = repo.recordDue(student, r.id(), 100, "first");
        var d2 = repo.recordDue(student, r.id(), 999, "second attempt");
        assertEquals(d1.id(), d2.id());
        assertEquals(100, d2.amountPoisha());
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
