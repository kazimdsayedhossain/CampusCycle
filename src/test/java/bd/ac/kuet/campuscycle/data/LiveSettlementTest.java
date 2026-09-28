package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.DueState;
import bd.ac.kuet.campuscycle.domain.RentalDue;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.RentalStatus;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.TariffService;import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end money path against the real database: collect, settle, pay the owner,
 * and book whatever could not be collected as debt. Every test starts and ends from
 * a clean account so the app stays usable by hand afterwards.
 */
public class LiveSettlementTest {

    private static final CampusUser STUDENT =
            new CampusUser("11305bb5-f12e-4c3c-a87c-ce0eca55aa18", "KUET member", "student@kuet.ac.bd", Role.STUDENT);
    private static final CampusRepository.PaymentMethod CAMPUS = CampusRepository.PaymentMethod.CAMPUS_PAY;
    private static final CampusRepository.PaymentMethod DOCK = CampusRepository.PaymentMethod.DOCK_PAY;

    private SupabaseCampusRepository repo;

    private static boolean live() {
        return DatabaseConnection.isAvailable();
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!live()) return;
        repo = new SupabaseCampusRepository();
        clearDues();
        returnAnyActiveRide();
        resetWallet(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (!live()) return;
        returnAnyActiveRide();
        clearDues();
        resetWallet(0);
    }

    // ---------------------------------------------------------------- helpers

    private void clearDues() throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("DELETE FROM public.rental_dues WHERE user_id = ?")) {
            ps.setString(1, STUDENT.id());
            ps.executeUpdate();
        }
    }

    private void resetWallet(int balancePoisha) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO public.wallets (user_id, balance_poisha) VALUES (?, ?) "
                             + "ON CONFLICT (user_id) DO UPDATE SET balance_poisha = excluded.balance_poisha")) {
            ps.setString(1, STUDENT.id());
            ps.setInt(2, balancePoisha);
            ps.executeUpdate();
        }
    }

    private void returnAnyActiveRide() {
        try {
            RentalRecord active = repo.activeRental(STUDENT);
            if (active != null) {
                repo.settleReturn(STUDENT, active.id(), active.cycleId(), 0, null, AvailabilityStatus.AVAILABLE,
                        CAMPUS, CampusRepository.ReturnCharge.NONE);
            }
        } catch (Exception ignored) {
            // A stale active rental from an earlier failed run must not fail the test.
        }
    }

    private CycleItem availableCycle() {
        return repo.catalog(STUDENT).stream()
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .findFirst()
                .orElse(null);
    }

    private int walletBalance() throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("SELECT balance_poisha FROM public.wallets WHERE user_id = ?")) {
            ps.setString(1, STUDENT.id());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int ownerBalance(String ownerId) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("SELECT balance_poisha FROM public.wallets WHERE user_id = ?")) {
            ps.setString(1, ownerId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Backdates the ride's start so the settled fare the trigger derives from real
     * elapsed time is genuinely higher than the quoted amount — this is what an
     * actually-late ride looks like to the database, rather than a client asking for
     * a bigger number. {@code due_at} is kept after {@code started_at} because the
     * live table enforces {@code check (due_at > started_at)}.
     */
    private void makeRideLong(String rentalId, int elapsedMinutes) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(
                     "UPDATE public.rentals SET started_at = now() - make_interval(mins => ?), "
                             + "due_at = now() - make_interval(mins => ?) + make_interval(mins => requested_minutes) "
                             + "WHERE id = ?::uuid")) {
            ps.setInt(1, elapsedMinutes);
            ps.setInt(2, elapsedMinutes);
            ps.setString(3, rentalId);
            ps.executeUpdate();
        }
    }

    private List<String> ledgerRefs(String userId) throws Exception {
        List<String> refs = new java.util.ArrayList<>();
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT reference_code FROM public.wallet_transactions "
                             + "WHERE user_id = ? ORDER BY timestamp")) {
            ps.setString(1, userId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    refs.add(rs.getString(1));
                }
            }
        }
        return refs;
    }

    // ----------------------------------------------------------------- tests

    @Test
    void cashAtHubPaysTheOwnerAndClearsNoDebt() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }
        int ownerBefore = ownerBalance(cycle.ownerId());

        RentalRecord rental = repo.book(STUDENT, cycle.id(), 30, DOCK);
        assertEquals(DOCK, repo.rentalPaymentMethod(rental.id()),
                "a dock ride must be identified as cash-at-hub at return time");
        assertEquals(0, walletBalance(), "a dock ride must not touch the rider's wallet");

        int fare = rental.quotedAmountPoisha();
        CampusRepository.SettlementOutcome out = repo.settleReturn(
                STUDENT, rental.id(), cycle.id(), fare, "KUET Central Mosque", AvailabilityStatus.AVAILABLE,
                DOCK, new CampusRepository.ReturnCharge(fare));

        assertEquals(0, out.shortfallPoisha(), "paying the full fare in cash leaves no debt");
        assertEquals(out.ownerPayoutCreditedPoisha(),
                out.finalAmountPoisha() - TariffService.platformFeePoisha(out.finalAmountPoisha()),
                "owner receives the settled fare minus the platform fee");
        assertEquals(ownerBefore + out.ownerPayoutCreditedPoisha(), ownerBalance(cycle.ownerId()));
        assertEquals(0, repo.unpaidDuesTotal(STUDENT, STUDENT.id()));
        System.out.println("Cash settle: fare=" + out.finalAmountPoisha()
                + " cash=" + out.cashCollectedPoisha()
                + " payout=" + out.ownerPayoutCreditedPoisha());
    }

    @Test
    void partialCashPaysTheOwnerFromCashAndBooksTheRestAsDebt() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }
        int ownerBefore = ownerBalance(cycle.ownerId());

        RentalRecord rental = repo.book(STUDENT, cycle.id(), 30, DOCK);
        int cash = rental.quotedAmountPoisha() / 2;
        // The ride really ran long, so the database settles a higher fare than the
        // client quoted. The client reports the (smaller) amount it believes is owed;
        // the difference must become debt rather than silently vanish.
        makeRideLong(rental.id(), 60);

        CampusRepository.SettlementOutcome out = repo.settleReturn(
                STUDENT, rental.id(), cycle.id(), rental.quotedAmountPoisha(), "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, DOCK, new CampusRepository.ReturnCharge(cash));

        assertTrue(out.finalAmountPoisha() > rental.quotedAmountPoisha(),
                "precondition: a 60-minute ride settles above the 30-minute quote");
        // The escrow invariant: the owner is paid from the cash, never the full fare.
        assertEquals(cash, out.ownerPayoutCreditedPoisha());
        assertEquals(ownerBefore + cash, ownerBalance(cycle.ownerId()));
        assertEquals(out.owedTotalPoisha() - cash, out.shortfallPoisha(),
                "everything the platform did not receive — fare and fine — is debt");

        var due = repo.userDues(STUDENT, STUDENT.id()).stream().findFirst().orElse(null);
        assertNotNull(due, "an unpaid cash ride must record a due");
        assertEquals(DueState.UNPAID, due.state());
        assertEquals(out.owedTotalPoisha() - cash, due.outstandingPoisha());
        assertTrue(due.rentalId().equals(rental.id()));
        System.out.println("Partial cash: settled=" + out.finalAmountPoisha() + " cash=" + cash
                + " payout=" + out.ownerPayoutCreditedPoisha() + " due=" + due.outstandingPoisha());
    }

    @Test
    void overdueFineIsBilledAndAnythingUncollectedBecomesDebt() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }
        int ownerBefore = ownerBalance(cycle.ownerId());

        RentalRecord rental = repo.book(STUDENT, cycle.id(), 15, DOCK);
        // A 60-minute ride on a 15-minute booking: 45 minutes past due, so after the
        // 15-minute grace there are 2 blocks of 15 minutes to bill.
        makeRideLong(rental.id(), 60);
        long overdueSeconds = 45L * 60 - TariffService.OVERDUE_GRACE_SECONDS;
        int expectedFine = (int) ((overdueSeconds + TariffService.OVERDUE_FINE_BLOCK_SECONDS - 1)
                / TariffService.OVERDUE_FINE_BLOCK_SECONDS) * TariffService.OVERDUE_BLOCK_POISHA;
        assertTrue(expectedFine > 0, "the backdated rental must actually be overdue");

        // The client asks for no fine at all: the database must bill the real one.
        CampusRepository.SettlementOutcome out = repo.settleReturn(
                STUDENT, rental.id(), cycle.id(), rental.quotedAmountPoisha(), "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, DOCK,
                new CampusRepository.ReturnCharge(0));

        assertEquals(expectedFine, out.fineBilledPoisha(),
                "the fine is recomputed server-side; a client cannot dodge it");
        assertEquals(0, out.cashCollectedPoisha());
        assertEquals(0, out.ownerPayoutCreditedPoisha());
        assertEquals(ownerBefore, ownerBalance(cycle.ownerId()), "no payout without payment");

        var due = repo.userDues(STUDENT, STUDENT.id()).stream().findFirst().orElse(null);
        assertNotNull(due, "an uncollected fine must be recorded, never dropped");
        assertEquals(out.owedTotalPoisha(), due.amountPoisha(),
                "the settled fare plus the fine is owed when nothing was collected");
        assertTrue(due.reason().toLowerCase().contains("fine"),
                "the due should say it includes the fine: " + due.reason());
        System.out.println("Overdue: fare=" + out.finalAmountPoisha() + " fine=" + out.fineBilledPoisha()
                + " due=" + due.outstandingPoisha());
    }

    @Test
    void duesBlockTheNextCampusPayRideAndAutoSettleOnTopUp() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }

        // Leave a debt behind with a cash ride.
        RentalRecord first = repo.book(STUDENT, cycle.id(), 30, DOCK);
        makeRideLong(first.id(), 60);
        int cash = first.quotedAmountPoisha() / 4;
        CampusRepository.SettlementOutcome firstOut = repo.settleReturn(
                STUDENT, first.id(), cycle.id(), first.quotedAmountPoisha(), "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, DOCK, new CampusRepository.ReturnCharge(cash));
        int owed = repo.unpaidDuesTotal(STUDENT, STUDENT.id());
        assertTrue(owed > 0, "precondition: an unpaid due exists");
        assertEquals(1, repo.unpaidDuesCount(STUDENT, STUDENT.id()));

        // Campus Pay must refuse while the debt is open.
        resetWallet(0);
        CycleItem next = availableCycle();
        assertNotNull(next, "precondition: another cycle is free");
        IllegalStateException blocked = assertThrows(IllegalStateException.class,
                () -> repo.book(STUDENT, next.id(), 30, CAMPUS),
                "an unpaid due must block the next Campus Pay booking");
        assertTrue(blocked.getMessage().toLowerCase().contains("dues"),
                "the rider must be told why: " + blocked.getMessage());

        // A dock ride is still allowed: cash settles at the hub.
        RentalRecord dockRide = repo.book(STUDENT, next.id(), 30, DOCK);
        assertNotNull(dockRide, "cash-at-hub must not be blocked by an open due");
        int dockFare = dockRide.quotedAmountPoisha();
        repo.settleReturn(STUDENT, dockRide.id(), next.id(), dockFare, "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, DOCK, new CampusRepository.ReturnCharge(dockFare));

        // Top up: the next Campus Pay booking clears the arrears from the wallet
        // before it charges the fare, so the rider is not turned away.
        CycleItem third = availableCycle();
        assertNotNull(third, "precondition: a third cycle is free");
        int quote = TariffService.quotePoisha(30);
        quote -= TariffService.subsidyPoisha(quote, true);
        resetWallet(owed + quote + 5000);
        int before = walletBalance();

        RentalRecord second = repo.book(STUDENT, third.id(), 30, CAMPUS);
        assertNotNull(second, "a topped-up rider must be able to book again");

        assertEquals(0, repo.unpaidDuesTotal(STUDENT, STUDENT.id()),
                "booking on a funded wallet clears the arrears");
        assertEquals(0, repo.unpaidDuesCount(STUDENT, STUDENT.id()));
        assertEquals(before - owed - quote, walletBalance(),
                "the wallet pays the cleared dues plus the new fare, nothing else");

        var cleared = repo.userDues(STUDENT, STUDENT.id());
        assertTrue(cleared.isEmpty() || cleared.stream().allMatch(d -> d.state() == DueState.PAID),
                "a settled due is PAID, not deleted");
        assertTrue(ledgerRefs(STUDENT.id()).stream().anyMatch(r -> r.startsWith("DUES-")),
                "the arrears settlement must be visible in the wallet ledger");

        repo.settleReturn(STUDENT, second.id(), third.id(), quote, "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, CAMPUS, CampusRepository.ReturnCharge.NONE);
        System.out.println("Auto-settle: owed=" + owed + " cleared on booking, ride went through");
    }

    @Test
    void campusPayOvertimeComesOutOfTheWalletAndCapsTheOwnerPayout() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }
        int ownerBefore = ownerBalance(cycle.ownerId());

        int quote = TariffService.quotePoisha(30);
        quote -= TariffService.subsidyPoisha(quote, true);
        resetWallet(quote + 20_000);
        int before = walletBalance();

        RentalRecord rental = repo.book(STUDENT, cycle.id(), 30, CAMPUS);
        int afterBooking = walletBalance();
        assertEquals(before - quote, afterBooking, "the quoted fare leaves the wallet at booking");

        // A real 45-minute ride on a 30-minute booking: the extra 15 minutes cost
        // 1000 before the student subsidy, and the database works that out itself.
        // It is still inside the 15-minute grace, so no fine.
        makeRideLong(rental.id(), 45);
        CampusRepository.SettlementOutcome out = repo.settleReturn(
                STUDENT, rental.id(), cycle.id(), 0, "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, CAMPUS, CampusRepository.ReturnCharge.NONE);

        int expectedOvertime = out.finalAmountPoisha() - quote;
        assertTrue(expectedOvertime > 0, "precondition: the ride overran its booking");
        assertEquals(0, out.fineBilledPoisha(), "inside the grace window there is no fine");
        assertEquals(expectedOvertime, out.overtimeChargedPoisha(), "overtime is collected at return");
        assertEquals(0, out.shortfallPoisha(), "a funded wallet owes nothing");
        assertEquals(afterBooking - expectedOvertime, walletBalance(), "overtime leaves the wallet at return");
        assertEquals(ownerBefore + out.ownerPayoutCreditedPoisha(), ownerBalance(cycle.ownerId()));

        var settled = repo.rentals(STUDENT).stream()
                .filter(r -> r.id().equals(rental.id())).findFirst().orElseThrow();
        assertEquals(RentalStatus.RETURNED, settled.status());
        assertEquals(settled.effectiveFarePoisha(),
                settled.platformFeePoisha() + settled.ownerPayoutPoisha(),
                "the stored split must always add up to the settled fare");
        assertTrue(ledgerRefs(STUDENT.id()).contains("OVERTIME-" + rental.id()),
                "overtime must be a traceable ledger entry");
        System.out.println("Campus Pay: quote=" + quote + " overtime=" + expectedOvertime
                + " payout=" + out.ownerPayoutCreditedPoisha());
    }

    @Test
    void anAdminCanWaiveADueAndThatFreesTheRiderAgain() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }
        CampusUser admin = new CampusUser("ac2b8d64-c379-4223-ba0a-71bde5d6edcb",
                "KUET Admin", "admin@kuet.ac.bd", Role.ADMIN);

        // Create a debt.
        RentalRecord rental = repo.book(STUDENT, cycle.id(), 30, DOCK);
        makeRideLong(rental.id(), 60);
        repo.settleReturn(STUDENT, rental.id(), cycle.id(), 0, "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, DOCK, new CampusRepository.ReturnCharge(500));
        assertTrue(repo.unpaidDuesTotal(STUDENT, STUDENT.id()) > 0, "precondition: a due exists");

        var due = repo.userDues(STUDENT, STUDENT.id()).stream()
                .filter(d -> d.state() == DueState.UNPAID).findFirst().orElseThrow();

        // The Admin Dues tab reads allDues and then calls waiveDue on the row.
        assertTrue(repo.allDues(admin).stream().anyMatch(d -> d.id().equals(due.id())),
                "the due must show up in the admin list");
        assertTrue(repo.allDues(admin).stream().anyMatch(RentalDue::isOwed),
                "an open due must be reported as owed");

        repo.waiveDue(admin, due.id(), "Goodwill waiver agreed at the cycle office");

        assertEquals(0, repo.unpaidDuesTotal(STUDENT, STUDENT.id()),
                "a waived due stops counting as owed");
        var waived = repo.userDues(STUDENT, STUDENT.id()).stream()
                .filter(d -> d.id().equals(due.id())).findFirst().orElseThrow();
        assertEquals(DueState.WAIVED, waived.state());
        assertEquals(0, waived.outstandingPoisha());

        // And the rider can book again, because the block really was released.
        resetWallet(50_000);
        CycleItem next = availableCycle();
        assertNotNull(next, "precondition: another cycle is free");
        RentalRecord ride = repo.book(STUDENT, next.id(), 30, CAMPUS);
        assertNotNull(ride, "a waived due must not keep blocking the rider");
        repo.settleReturn(STUDENT, ride.id(), next.id(), 0, "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, CAMPUS, CampusRepository.ReturnCharge.NONE);
        System.out.println("Waive: due " + due.id() + " cleared by admin, booking allowed again");
    }

    @Test
    void aClientCannotInflateTheFineItIsCharged() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }

        // Book, then return it straight away: not overdue at all.
        int quote = TariffService.quotePoisha(15);
        quote -= TariffService.subsidyPoisha(quote, true);
        resetWallet(quote + 50_000);
        RentalRecord rental = repo.book(STUDENT, cycle.id(), 15, CAMPUS);
        int walletBefore = walletBalance();

        // A hostile client declares a huge fine and a huge overtime.
        CampusRepository.ReturnCharge hostile = new CampusRepository.ReturnCharge(0);
        CampusRepository.SettlementOutcome out = repo.settleReturn(
                STUDENT, rental.id(), cycle.id(), 0, "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, CAMPUS, hostile);

        assertEquals(0, out.fineBilledPoisha(), "an on-time ride can never be fined");
        assertEquals(0, out.overtimeChargedPoisha(), "an on-time ride has no overtime");
        assertEquals(walletBefore, walletBalance(), "a false claim must not touch the wallet");
        assertEquals(0, out.shortfallPoisha());
        assertEquals(0, repo.unpaidDuesTotal(STUDENT, STUDENT.id()), "no phantom debt either");
        System.out.println("Hostile client: billed fine=" + out.fineBilledPoisha()
                + " charged=" + out.overtimeChargedPoisha() + ", wallet untouched");
    }

    @Test
    void emptyWalletStillReturnsTheBikeAndRecordsTheWholeShortfall() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        CycleItem cycle = availableCycle();
        if (cycle == null) {
            System.out.println("SKIP: no available cycle");
            return;
        }
        int ownerBefore = ownerBalance(cycle.ownerId());

        int quote = TariffService.quotePoisha(30);
        quote -= TariffService.subsidyPoisha(quote, true);
        resetWallet(quote);      // exactly enough to book, nothing left over
        RentalRecord rental = repo.book(STUDENT, cycle.id(), 30, CAMPUS);
        assertEquals(0, walletBalance());

        // A genuinely long ride: the settled fare is above what was quoted, and the
        // empty wallet cannot cover the difference.
        makeRideLong(rental.id(), 60);
        CampusRepository.SettlementOutcome out = repo.settleReturn(
                STUDENT, rental.id(), cycle.id(), quote, "KUET Central Mosque",
                AvailabilityStatus.AVAILABLE, CAMPUS, new CampusRepository.ReturnCharge(0));

        assertTrue(out.finalAmountPoisha() > quote, "precondition: the ride overran");
        int expectedShortfall = out.owedTotalPoisha() - quote;
        assertEquals(0, out.overtimeChargedPoisha(), "an empty wallet collects nothing");
        assertEquals(expectedShortfall, out.shortfallPoisha(), "the whole overrun becomes debt");
        // The owner is paid only out of the quoted fare the platform actually held.
        assertEquals(quote, out.ownerPayoutCreditedPoisha(),
                "the payout is capped at the fare that was really collected");
        assertEquals(ownerBefore + quote, ownerBalance(cycle.ownerId()));

        var due = repo.userDues(STUDENT, STUDENT.id()).stream().findFirst().orElse(null);
        assertNotNull(due, "an uncollected overrun must be booked, not forgiven");
        assertEquals(expectedShortfall, due.outstandingPoisha());

        // The bike really is back on the hub, not stranded.
        assertNull(repo.activeRental(STUDENT));
        assertTrue(repo.catalog(STUDENT).stream()
                .anyMatch(c -> c.id().equals(cycle.id()) && c.availabilityStatus() == AvailabilityStatus.AVAILABLE));
        System.out.println("Short wallet: owed=" + out.shortfallPoisha()
                + " payout=" + out.ownerPayoutCreditedPoisha() + ", bike returned");
    }
}
