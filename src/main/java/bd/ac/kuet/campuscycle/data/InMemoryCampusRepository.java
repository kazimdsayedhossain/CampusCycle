package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class InMemoryCampusRepository implements CampusRepository {

    private final List<CycleItem> cycles = new ArrayList<>();
    private final List<RentalRecord> rentals = new ArrayList<>();
    private final List<UserRegistration> userRegistrations = new ArrayList<>();
    private final List<SupportConversation> conversations = new ArrayList<>();
    private final java.util.Map<String, List<SupportMessage>> threads = new java.util.HashMap<>();
    private final List<DisputeItem> disputes = new ArrayList<>();
    /**
     * Owner earnings double: credited owner balances plus settled payout refs.
     * Mirrors the Supabase settle path (owner wallet credit + PAYOUT- ledger)
     * so the split contract is unit-testable without a database.
     */
    private final java.util.Map<String, Integer> ownerBalances = new java.util.HashMap<>();
    private final java.util.Set<String> paidPayoutRefs = new java.util.HashSet<>();
    private final List<RentalDue> dues = new ArrayList<>();
    /** Payment method each rental was started with, so the return path can collect correctly. */
    private final java.util.Map<String, PaymentMethod> rentalMethods = new java.util.HashMap<>();

    /** Credited owner payout balance for tests. */
    public synchronized int ownerBalancePoisha(String ownerId) {
        return ownerBalances.getOrDefault(ownerId, 0);
    }

    /**
     * Initializes in-memory seed data. Note: Cycle owner IDs like "owner-1" are mock identifiers
     * reserved intentionally for unit test isolation and offline fallback demonstrations so that
     * real session UUIDs are never blocked from booking these cycles.
     */
    public InMemoryCampusRepository() {
        cycles.add(new CycleItem(
                "C-101", "owner-1", "Nusrat Jahan", "Blue commuter",
                CycleType.CITY_BIKE, CycleCondition.EXCELLENT, "KUET Central Mosque",
                22.9009, 89.5016, "Reliable campus commuter with front basket.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-102", "owner-2", "Rakib Hasan", "Road runner",
                CycleType.ROAD_BIKE, CycleCondition.GOOD, "Student Welfare Centre",
                22.9017, 89.5030, "Lightweight 21-speed alloy road bike.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-103", "owner-3", "Sadia Islam", "E-bike 01",
                CycleType.ELECTRIC_BIKE, CycleCondition.EXCELLENT, "KUET Main Gate",
                22.8987, 89.4981, "Assisted pedal electric cycle with fast battery.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-104", "owner-4", "Tanvir Ahmed", "Campus Glide",
                CycleType.CITY_BIKE, CycleCondition.EXCELLENT, "Hall Gate",
                22.9045, 89.5060, "Comfortable step-through commuter frame.",
                ReviewStatus.APPROVED, AvailabilityStatus.MAINTENANCE
        ));
        cycles.add(new CycleItem(
                "C-105", "owner-5", "Mehedi Hasan", "Eco Cruiser",
                CycleType.ELECTRIC_BIKE, CycleCondition.GOOD, "Academic Building",
                22.9015, 89.5010, "Smart throttle e-bike with solar dock lock.",
                ReviewStatus.APPROVED, AvailabilityStatus.RENTED
        ));
        cycles.add(new CycleItem(
                "C-106", "3d1e3d69-ffc6-494f-a42c-26eeb258b581", "Arafat Rahman", "Daily rider",
                CycleType.CITY_BIKE, CycleCondition.GOOD, "Hall Gate",
                22.9045, 89.5060, "Student listing awaiting physical inspection.",
                ReviewStatus.PENDING_REVIEW, AvailabilityStatus.AVAILABLE
        ));
        // Seed technician account for the offline fallback console (P-031/P-049).
        // Documented demo credential: technician@kuet.ac.bd / TechPass#2026.
        // Stored exactly like every other password in this store: PasswordUtils.hash
        // at rest, never plaintext; getAllUserRegistrations blanks it on the way out.
        userRegistrations.add(new UserRegistration(
                "tech-1", "KUET Workshop Technician", "TECH-001", "Transport Office",
                "technician@kuet.ac.bd", "01700000000",
                PasswordUtils.hash("TechPass#2026"), "APPROVED", CampusTime.now()
        ));
    }

    private static void requireAdmin(CampusUser actor) {
        if (actor == null) {
            throw new SecurityException("Admin authorization required.");
        }
        actor.requireRole(Role.ADMIN);
    }

    @Override
    public synchronized List<CycleItem> allCycles(CampusUser admin) {
        requireAdmin(admin);
        return new ArrayList<>(cycles);
    }

    @Override
    public synchronized void addCycle(CycleItem cycle) {
        Objects.requireNonNull(cycle, "Cycle is required.");
        cycles.add(cycle);
    }

    /** Admin-only fleet write: the interface overload carries no actor, so this is the checked path. */
    public synchronized void addCycle(CampusUser admin, CycleItem cycle) {
        requireAdmin(admin);
        addCycle(cycle);
    }

    @Override
    public synchronized void updateCycle(CycleItem cycle) {
        Objects.requireNonNull(cycle, "Cycle is required.");
        int index = findCycle(cycle.id());
        cycles.set(index, cycle);
    }

    /** Admin-only fleet write: the interface overload carries no actor, so this is the checked path. */
    public synchronized void updateCycle(CampusUser admin, CycleItem cycle) {
        requireAdmin(admin);
        updateCycle(cycle);
    }

    @Override
    public synchronized void deleteCycle(String cycleId) {
        Objects.requireNonNull(cycleId, "Cycle id is required.");
        cycles.removeIf(c -> c.id().equals(cycleId));
    }

    /** Admin-only fleet write: the interface overload carries no actor, so this is the checked path. */
    public synchronized void deleteCycle(CampusUser admin, String cycleId) {
        requireAdmin(admin);
        deleteCycle(cycleId);
    }

    /**
     * Single choke point for every manual availability transition (P-034).
     * System transitions owned by the lifecycle itself — booking (AVAILABLE to
     * RENTED) and returning (RENTED to AVAILABLE/MAINTENANCE) — go through the
     * private {@link #rewriteCycle} helper instead, so this guard never blocks
     * a student return and nobody can set RENTED directly.
     */
    @Override
    public synchronized void updateCycleState(String cycleId, AvailabilityStatus expectedCurrent,
                                              AvailabilityStatus next, CampusUser actor) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to change cycle state.");
        }
        Objects.requireNonNull(cycleId, "Cycle id is required.");
        Objects.requireNonNull(expectedCurrent, "Expected state is required.");
        Objects.requireNonNull(next, "Next state is required.");
        if (next == AvailabilityStatus.RENTED) {
            throw new IllegalStateException("Cycles enter RENTED only via booking.");
        }
        int index = findCycle(cycleId);
        CycleItem current = cycles.get(index);
        if (current.availabilityStatus() != expectedCurrent) {
            throw new IllegalStateException("Cycle " + cycleId + " is " + current.availabilityStatus()
                    + "; expected " + expectedCurrent + ".");
        }
        if (next == AvailabilityStatus.RETIRED
                || current.availabilityStatus() == AvailabilityStatus.RETIRED
                || current.availabilityStatus() == AvailabilityStatus.RENTED) {
            actor.requireRole(Role.ADMIN);
        } else {
            actor.requireRole(Role.TECHNICIAN, Role.ADMIN);
        }
        cycles.set(index, rewriteCycle(current, next));
    }

    @Override
    public synchronized List<CycleItem> catalog(CampusUser user) {
        // Rentable pool only: APPROVED and AVAILABLE. QUARANTINE, RETIRED,
        // MAINTENANCE, RENTED and unreviewed listings never appear here.
        return cycles.stream()
                .filter(c -> c.reviewStatus() == ReviewStatus.APPROVED)
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .filter(c -> user == null || !c.ownerId().equals(user.id()))
                .sorted(Comparator.comparing(CycleItem::label))
                .toList();
    }

    @Override
    public synchronized List<CycleItem> pendingCycles() {
        return cycles.stream()
                .filter(c -> c.reviewStatus() == ReviewStatus.PENDING_REVIEW)
                .toList();
    }

    /** Admin-only read: the interface overload carries no actor, so this is the checked path. */
    public synchronized List<CycleItem> pendingCycles(CampusUser admin) {
        requireAdmin(admin);
        return pendingCycles();
    }

    @Override
    public synchronized List<RentalRecord> rentals(CampusUser user) {
        return rentals.stream()
                .filter(r -> r.renterId().equals(user.id()))
                .sorted(Comparator.comparing(RentalRecord::startedAt).reversed())
                .toList();
    }

    @Override
    public synchronized RentalRecord activeRental(CampusUser user) {
        return rentals.stream()
                .filter(r -> r.renterId().equals(user.id()) && r.status() == RentalStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized RentalRecord book(CampusUser renter, String cycleId, int minutes) {
        return book(renter, cycleId, minutes, PaymentMethod.CAMPUS_PAY);
    }

    @Override
    public synchronized RentalRecord book(CampusUser renter, String cycleId, int minutes, PaymentMethod method) {
        if (activeRental(renter) != null) {
            throw new IllegalStateException("Return your active cycle before starting another rental.");
        }
        // Dues gate (no auto-settle here: the offline double models no renter
        // wallets, so arrears can only block, never clear — Supabase clears).
        if (method == PaymentMethod.CAMPUS_PAY) {
            int owed = unpaidDuesTotal(renter, renter.id());
            if (owed > 0) {
                int openCount = unpaidDuesCount(renter, renter.id());
                if (openCount >= TariffService.OVERDUE_REVIEW_DUES_COUNT) {
                    throw new IllegalStateException(
                            "Your account is under review for repeated unpaid dues ("
                                    + openCount + " open). Please contact the cycle office to ride again.");
                }
                throw new IllegalStateException(
                        "You have unpaid dues. Clear them to ride again.");
            }
        }
        int index = findCycle(cycleId);
        CycleItem cycle = cycles.get(index);
        if (!cycle.canBeBookedBy(renter.id())) {
            throw new IllegalStateException("This cycle is no longer available.");
        }

        ZonedDateTime now = CampusTime.now();
        int totalPoisha = TariffService.quotePoisha(minutes);
        if (renter.role() == Role.STUDENT) {
            totalPoisha -= TariffService.subsidyPoisha(totalPoisha, true);
        }

        RentalRecord rental = new RentalRecord(
                "R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                cycle.id(),
                cycle.label(),
                renter.id(),
                minutes,
                totalPoisha,
                0,
                0,
                "",
                RentalStatus.ACTIVE,
                now,
                now.plusMinutes(minutes),
                null,
                0,
                0
        );

        rentals.add(rental);
        rentalMethods.put(rental.id(), method);
        cycles.set(index, rewriteCycle(cycle, AvailabilityStatus.RENTED));
        return rental;
    }

    @Override
    public synchronized void returnRental(CampusUser renter, String rentalId) {
        if (renter == null) {
            throw new SecurityException("Sign-in is required to return a rental.");
        }
        int rIndex = findRental(rentalId);
        RentalRecord record = rentals.get(rIndex);
        returnRental(renter, rentalId, record.cycleId(), 0, null, null);
    }

    @Override
    public synchronized void returnRental(CampusUser renter, String rentalId, int finalAmountPoisha) {
        if (renter == null) {
            throw new SecurityException("Sign-in is required to return a rental.");
        }
        int rIndex = findRental(rentalId);
        RentalRecord record = rentals.get(rIndex);
        returnRental(renter, rentalId, record.cycleId(), finalAmountPoisha, null, null);
    }

    /**
     * Full return in one call. The cycle is resolved from the rental row and the
     * caller-supplied {@code cycleId} must match it (P-052): a rental UUID can
     * never relocate an unrelated cycle. The status/location moves are system
     * transitions owned by the return itself, applied with the same helpers as
     * every other lifecycle write.
     */
    @Override
    public synchronized void returnRental(CampusUser renter, String rentalId, String cycleId,
                                           int finalAmountPoisha, String returnLocation,
                                           AvailabilityStatus returnStatus) {
        settleReturn(renter, rentalId, cycleId, finalAmountPoisha, returnLocation, returnStatus, null, ReturnCharge.NONE);
    }

    /**
     * Same escrow rule as the live repository: collection, settlement and the owner
     * payout happen together, and the owner is credited out of what was actually
     * taken, never more. The remainder is the caller's to book as a due. Kept here so
     * tests exercise the production rules, not a friendlier double.
     */
    @Override
    public synchronized SettlementOutcome settleReturn(CampusUser renter, String rentalId, String cycleId,
                                                       int finalAmountPoisha, String returnLocation,
                                                       AvailabilityStatus returnStatus,
                                                       PaymentMethod method, ReturnCharge charge) {
        if (renter == null) {
            throw new SecurityException("Sign-in is required to return a rental.");
        }
        int rIndex = findRental(rentalId);
        RentalRecord record = rentals.get(rIndex);
        if (!record.renterId().equals(renter.id())) {
            throw new IllegalStateException("Rental cannot be returned.");
        }
        if (record.status() != RentalStatus.ACTIVE) {
            throw new IllegalStateException("Rental cannot be returned.");
        }
        if (cycleId != null && !cycleId.equals(record.cycleId())) {
            throw new IllegalArgumentException("Cycle " + cycleId + " does not belong to rental " + rentalId + ".");
        }

        ZonedDateTime now = CampusTime.now();
        int finalAmount = finalAmountPoisha > 0 ? finalAmountPoisha : record.quotedAmountPoisha();
        String hub = (returnLocation != null && !returnLocation.isBlank())
                ? returnLocation.trim() : record.dropoffHub();

        // Overtime and the fine are derived from the recorded times, never from the
        // caller, matching the live repository: a client cannot declare charges it
        // does not owe.
        boolean cashRide = method == PaymentMethod.DOCK_PAY;
        ReturnCharge due = charge != null ? charge : ReturnCharge.NONE;
        int billedFine = authoritativeFine(record.dueAt(), now);
        int overtime = 0;
        int fine = 0;
        int cash = 0;
        int heldAtBooking = 0;
        if (cashRide) {
            cash = Math.max(0, due.cashPoisha());
        } else {            overtime = Math.max(0, finalAmount - record.quotedAmountPoisha());
            fine = billedFine;
            heldAtBooking = record.quotedAmountPoisha();
        }
        int totalCollected = heldAtBooking + overtime + fine + cash;

        int fullPayout = TariffService.ownerPayoutPoisha(finalAmount);
        int payout = Math.min(fullPayout, totalCollected);
        // Owner payout double: credit once per rental (replay-safe via paid refs).
        String payoutRef = "PAYOUT-" + record.id();
        if (payout > 0 && paidPayoutRefs.add(payoutRef)) {
            int cIdx = findCycle(record.cycleId());
            String ownerId = cycles.get(cIdx).ownerId();
            ownerBalances.merge(ownerId, payout, Integer::sum);
        }
        rentals.set(rIndex, new RentalRecord(
                record.id(),
                record.cycleId(),
                record.cycleLabel(),
                record.renterId(),
                record.requestedMinutes(),
                record.quotedAmountPoisha(),
                finalAmount,
                record.overdueFinePoisha(),
                hub,
                RentalStatus.RETURNED,
                record.startedAt(),
                record.dueAt(),
                now,
                TariffService.platformFeePoisha(finalAmount),
                fullPayout
        ));

        int cIndex = findCycle(record.cycleId());
        cycles.set(cIndex, rewriteCycle(cycles.get(cIndex),
                returnStatus != null ? returnStatus : AvailabilityStatus.AVAILABLE));
        if (returnLocation != null && !returnLocation.isBlank()) {
            updateCycleLocation(renter, record.cycleId(), returnLocation.trim());
        }

        // Debt is booked by the settlement itself, exactly as the live repository
        // does inside its transaction, so the two can never drift apart. The fine is
        // recomputed from the recorded times, not taken from the caller.
        int billedFineFinal = authoritativeFine(record.dueAt(), now);
        int shortfall = Math.max(0, finalAmount + billedFineFinal - totalCollected);
        String dueId = null;
        if (shortfall > 0) {
            dueId = recordDue(renter, record.id(), shortfall,
                    "Uncollected on ride " + record.cycleLabel()
                            + (billedFineFinal > 0 ? " (includes " + billedFineFinal + " poisha overdue fine)" : "")
                            + (cashRide ? " — cash at hub" : " — Campus Pay")).id();
        }
        return new SettlementOutcome(
                finalAmount,
                billedFineFinal,
                record.quotedAmountPoisha(),
                overtime,
                fine,
                cash,
                totalCollected,
                payout,
                TariffService.platformFeePoisha(finalAmount),
                dueId,
                cashRide);
    }

    /** Same rule as {@link TariffService}: grace first, then a flat amount per block. */
    private static int authoritativeFine(ZonedDateTime dueAt, ZonedDateTime returnedAt) {
        if (dueAt == null || returnedAt == null) {
            return 0;
        }
        long overdueSeconds = java.time.Duration.between(dueAt, returnedAt).getSeconds();
        if (overdueSeconds <= TariffService.OVERDUE_GRACE_SECONDS) {
            return 0;
        }
        long over = overdueSeconds - TariffService.OVERDUE_GRACE_SECONDS;
        long blocks = (over + TariffService.OVERDUE_FINE_BLOCK_SECONDS - 1)
                / TariffService.OVERDUE_FINE_BLOCK_SECONDS;
        return (int) Math.min(Integer.MAX_VALUE, blocks * TariffService.OVERDUE_BLOCK_POISHA);
    }

    @Override
    public synchronized PaymentMethod rentalPaymentMethod(String rentalId) {
        int rIndex = findRental(rentalId);
        return rentalMethods.getOrDefault(rentals.get(rIndex).id(), PaymentMethod.CAMPUS_PAY);
    }

    @Override
    public synchronized void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason) {
        requireAdmin(admin);
        int index = findCycle(cycleId);
        CycleItem cycle = cycles.get(index);
        if (cycle.reviewStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Cycle listing has already been reviewed.");
        }

        cycles.set(index, new CycleItem(
                cycle.id(),
                cycle.ownerId(),
                cycle.ownerName(),
                cycle.label(),
                cycle.type(),
                cycle.condition(),
                cycle.pickupPoint(),
                cycle.latitude(),
                cycle.longitude(),
                cycle.description(),
                approved ? ReviewStatus.APPROVED : ReviewStatus.REJECTED,
                cycle.availabilityStatus()
        ));
    }

    @Override
    public synchronized int rebalanceHub(CampusUser admin, String sourceHub, String targetHub, int count) {
        requireAdmin(admin);
        if (sourceHub == null || sourceHub.isBlank() || targetHub == null || targetHub.isBlank()) {
            throw new IllegalArgumentException("Source and target hubs are required.");
        }
        if (count <= 0) {
            return 0;
        }
        // Move rentable stock only: APPROVED and AVAILABLE. Rented, blocked,
        // quarantined, retired and unreviewed cycles never move.
        int moved = 0;
        for (int i = 0; i < cycles.size() && moved < count; i++) {
            CycleItem c = cycles.get(i);
            if (c.pickupPoint().equalsIgnoreCase(sourceHub.trim())
                    && c.reviewStatus() == ReviewStatus.APPROVED
                    && c.availabilityStatus() == AvailabilityStatus.AVAILABLE) {
                cycles.set(i, new CycleItem(
                        c.id(), c.ownerId(), c.ownerName(), c.label(), c.type(), c.condition(),
                        targetHub.trim(), c.latitude(), c.longitude(), c.description(),
                        c.reviewStatus(), c.availabilityStatus()
                ));
                moved++;
            }
        }
        return moved;
    }

    @Override
    public synchronized String openDispute(CampusUser renter, String rentalId, String reason) {
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 2000) {
            throw new IllegalArgumentException("Dispute reason must be 10-2000 characters.");
        }
        int rIndex = findRental(rentalId);
        RentalRecord record = rentals.get(rIndex);
        if (!record.renterId().equals(renter.id())) {
            throw new SecurityException("Only the renter can dispute this rental.");
        }
        if (record.status() != RentalStatus.RETURNED) {
            throw new IllegalStateException("Only RETURNED rentals can be disputed.");
        }
        String disputeId = "D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // Full-arity construction: disputing must preserve the settled fare, the
        // overdue fine and the drop-off hub — never zero them (P-102).
        rentals.set(rIndex, new RentalRecord(
                record.id(),
                record.cycleId(),
                record.cycleLabel(),
                record.renterId(),
                record.requestedMinutes(),
                record.quotedAmountPoisha(),
                record.finalAmountPoisha(),
                record.overdueFinePoisha(),
                record.dropoffHub(),
                RentalStatus.DISPUTED,
                record.startedAt(),
                record.dueAt(),
                record.returnedAt(),
                record.platformFeePoisha(),
                record.ownerPayoutPoisha()));
        disputes.add(0, new DisputeItem(disputeId, rentalId, reason.trim(), "OPEN"));
        return disputeId;
    }

    @Override
    public synchronized List<DisputeItem> disputeQueue(CampusUser admin) {
        requireAdmin(admin);
        return List.copyOf(disputes);
    }

    @Override
    public synchronized void resolveDispute(CampusUser admin, String disputeId, String notes) {
        requireAdmin(admin);
        if (disputeId == null || disputeId.isBlank()) {
            throw new IllegalArgumentException("Dispute id is required.");
        }
        for (int i = 0; i < disputes.size(); i++) {
            DisputeItem current = disputes.get(i);
            if (current.disputeId().equals(disputeId)) {
                if ("RESOLVED".equalsIgnoreCase(current.state())) {
                    throw new IllegalStateException("Dispute has already been resolved.");
                }
                // DisputeItem carries no note/timestamp fields, so the resolution
                // note and its timestamp are appended to the record and audited.
                String note = (notes == null || notes.isBlank()) ? "Resolved by campus office." : notes.trim();
                disputes.set(i, new DisputeItem(
                        current.disputeId(),
                        current.rentalId(),
                        current.reason() + " | Resolution (" + CampusTime.now() + "): " + note,
                        "RESOLVED"));
                recordAudit(admin, "DISPUTE", disputeId, "DISPUTE_RESOLVED", note);
                return;
            }
        }
        throw new IllegalArgumentException("Dispute not found: " + disputeId);
    }

    @Override
    public synchronized String createSupportConversation(CampusUser student, String subject, String message) {
        if (subject == null || subject.trim().length() < 3 || subject.trim().length() > 160) {
            throw new IllegalArgumentException("Subject must be 3-160 characters.");
        }
        if (message == null || message.trim().isEmpty() || message.trim().length() > 2000) {
            throw new IllegalArgumentException("Message must be 1-2000 characters.");
        }
        String id = "S-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ZonedDateTime now = CampusTime.now();
        conversations.add(0, new SupportConversation(id, subject.trim(), "OPEN", "", now));
        threads.put(id, new ArrayList<>(List.of(
                new SupportMessage("M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        student.id(), student.displayName(), student.role().name(), message.trim(), now))));
        return id;
    }

    @Override
    public synchronized List<SupportConversation> supportConversations(CampusUser user) {
        return List.copyOf(conversations);
    }

    @Override
    public synchronized List<SupportMessage> supportMessages(CampusUser user, String conversationId) {
        return List.copyOf(threads.getOrDefault(conversationId, List.of()));
    }

    @Override
    public synchronized void postSupportMessage(CampusUser user, String conversationId, String body) {
        if (body == null || body.trim().isEmpty() || body.trim().length() > 2000) {
            throw new IllegalArgumentException("Message must be 1-2000 characters.");
        }
        List<SupportMessage> thread = threads.get(conversationId);
        if (thread == null) throw new IllegalArgumentException("Conversation not found.");
        thread.add(new SupportMessage("M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                user.id(), user.displayName(), user.role().name(), body.trim(), CampusTime.now()));
    }

    @Override
    public synchronized void updateCycleLocation(CampusUser actor, String cycleId, String newLocation) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to move a cycle.");
        }
        Objects.requireNonNull(cycleId, "Cycle id is required.");
        if (newLocation == null || newLocation.isBlank()) {
            throw new IllegalArgumentException("New location is required.");
        }
        int index = findCycle(cycleId);
        CycleItem c = cycles.get(index);
        cycles.set(index, new CycleItem(
                c.id(), c.ownerId(), c.ownerName(), c.label(), c.type(), c.condition(),
                newLocation.trim(), c.latitude(), c.longitude(), c.description(),
                c.reviewStatus(), c.availabilityStatus()
        ));
    }

    private int findCycle(String id) {
        for (int i = 0; i < cycles.size(); i++) {
            if (cycles.get(i).id().equals(id)) return i;
        }
        throw new IllegalArgumentException("Cycle not found: " + id);
    }

    private int findRental(String id) {
        for (int i = 0; i < rentals.size(); i++) {
            if (rentals.get(i).id().equals(id)) return i;
        }
        throw new IllegalArgumentException("Rental not found: " + id);
    }

    private int findRegistration(String id) {
        for (int i = 0; i < userRegistrations.size(); i++) {
            if (userRegistrations.get(i).id().equals(id)) return i;
        }
        throw new IllegalArgumentException("Registration not found: " + id);
    }

    @Override
    public synchronized List<RentalRecord> allRentals() {
        return new ArrayList<>(rentals);
    }

    /** Admin-only aggregate: the interface overload carries no actor, so this is the checked path. */
    public synchronized List<RentalRecord> allRentals(CampusUser admin) {
        requireAdmin(admin);
        return allRentals();
    }

    @Override
    public synchronized OwnerEarnings ownerEarnings(CampusUser caller, String ownerId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view earnings.");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner id is required.");
        }
        if (!caller.id().equals(ownerId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own bike earnings.");
        }
        java.util.Map<String, OwnerEarnings.BikeEarning> byBike = new java.util.LinkedHashMap<>();
        for (RentalRecord r : rentals) {
            if (r.status() != RentalStatus.RETURNED) continue;
            CycleItem cycle = cycles.stream().filter(c -> c.id().equals(r.cycleId())).findFirst().orElse(null);
            if (cycle == null || !cycle.ownerId().equals(ownerId)) continue;
            int fare = r.effectiveFarePoisha();
            OwnerEarnings.BikeEarning prev = byBike.get(cycle.id());
            byBike.put(cycle.id(), new OwnerEarnings.BikeEarning(
                    cycle.id(), cycle.label(),
                    (prev == null ? 0 : prev.settledRides()) + 1,
                    (prev == null ? 0 : prev.grossPoisha()) + fare,
                    (prev == null ? 0 : prev.feesPoisha()) + r.platformFeePoisha(),
                    (prev == null ? 0 : prev.netPayoutPoisha()) + r.ownerPayoutPoisha()));
        }
        List<OwnerEarnings.BikeEarning> bikes = new ArrayList<>(byBike.values());
        bikes.sort((a, b) -> Integer.compare(b.netPayoutPoisha(), a.netPayoutPoisha()));
        long rides = 0;
        int gross = 0, fees = 0, payouts = 0;
        for (OwnerEarnings.BikeEarning b : bikes) {
            rides += b.settledRides();
            gross += b.grossPoisha();
            fees += b.feesPoisha();
            payouts += b.netPayoutPoisha();
        }
        return new OwnerEarnings(ownerId, bikes, rides, gross, fees, payouts);
    }

    @Override
    public synchronized PlatformEarnings platformEarnings(CampusUser admin) {
        requireAdmin(admin);
        long rides = 0;
        int gross = 0, fees = 0, payouts = 0;
        for (RentalRecord r : rentals) {
            if (r.status() != RentalStatus.RETURNED) continue;
            rides++;
            gross += r.effectiveFarePoisha();
            fees += r.platformFeePoisha();
            payouts += r.ownerPayoutPoisha();
        }
        return new PlatformEarnings(rides, gross, fees, payouts);
    }

    @Override
    public synchronized RentalDue recordDue(CampusUser actor, String rentalId, int amountPoisha, String reason) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to record dues.");
        }
        if (rentalId == null || rentalId.isBlank()) {
            throw new IllegalArgumentException("Rental id is required.");
        }
        if (amountPoisha <= 0) {
            throw new IllegalArgumentException("Due amount must be positive.");
        }
        int rIndex = findRental(rentalId.trim());
        RentalRecord record = rentals.get(rIndex);
        if (!record.renterId().equals(actor.id())) {
            throw new SecurityException("Dues can only be recorded for your own rentals.");
        }
        for (RentalDue d : dues) {
            if (d.rentalId().equals(record.id())) {
                return d;
            }
        }
        RentalDue due = new RentalDue(
                "DUE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                record.id(), actor.id(), amountPoisha, 0, DueState.UNPAID,
                reason == null ? "" : reason.trim(), CampusTime.now());
        dues.add(0, due);
        return due;
    }

    private List<RentalDue> owedDues(String userId) {
        List<RentalDue> out = new ArrayList<>();
        // Oldest first: dues list is newest-first, so iterate in reverse.
        for (int i = dues.size() - 1; i >= 0; i--) {
            RentalDue d = dues.get(i);
            if (d.userId().equals(userId) && d.isOwed()) {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    public synchronized int unpaidDuesTotal(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        int total = 0;
        for (RentalDue d : owedDues(userId)) {
            total += d.outstandingPoisha();
        }
        return total;
    }

    @Override
    public synchronized int unpaidDuesCount(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        return owedDues(userId).size();
    }

    @Override
    public synchronized List<RentalDue> userDues(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        List<RentalDue> out = new ArrayList<>();
        for (RentalDue d : dues) {
            if (d.userId().equals(userId)) {
                out.add(d);
            }
        }
        return out;
    }

    @Override
    public synchronized int settleDues(CampusUser user, int maxPoisha) {
        if (user == null) {
            throw new SecurityException("Sign-in is required to settle dues.");
        }
        if (maxPoisha <= 0) {
            return 0;
        }
        // Offline double has no renter wallets: settle against the stated budget.
        int budget = maxPoisha;
        int applied = 0;
        List<RentalDue> owed = owedDues(user.id());
        for (RentalDue d : owed) {
            if (budget <= 0) break;
            int take = Math.min(d.outstandingPoisha(), budget);
            if (take <= 0) continue;
            replaceDue(new RentalDue(d.id(), d.rentalId(), d.userId(), d.amountPoisha(),
                    d.paidPoisha() + take,
                    d.paidPoisha() + take >= d.amountPoisha() ? DueState.PAID : DueState.PARTIAL,
                    d.reason(), d.createdAt()));
            applied += take;
            budget -= take;
        }
        return applied;
    }

    private void replaceDue(RentalDue updated) {
        for (int i = 0; i < dues.size(); i++) {
            if (dues.get(i).id().equals(updated.id())) {
                dues.set(i, updated);
                return;
            }
        }
    }

    @Override
    public synchronized List<RentalDue> allDues(CampusUser admin) {
        requireAdmin(admin);
        return new ArrayList<>(dues);
    }

    @Override
    public synchronized void waiveDue(CampusUser admin, String dueId, String reason) {
        requireAdmin(admin);
        if (dueId == null || dueId.isBlank()) {
            throw new IllegalArgumentException("Due id is required.");
        }
        String note = reason == null ? "" : reason.trim();
        if (note.length() < 3) {
            throw new IllegalArgumentException("A waive reason (min 3 characters) is required.");
        }
        for (int i = 0; i < dues.size(); i++) {
            RentalDue d = dues.get(i);
            if (d.id().equals(dueId.trim())) {
                if (!d.isOwed()) {
                    throw new IllegalStateException("Due is not open.");
                }
                dues.set(i, new RentalDue(d.id(), d.rentalId(), d.userId(), d.amountPoisha(),
                        d.paidPoisha(), DueState.WAIVED, note, d.createdAt()));
                return;
            }
        }
        throw new IllegalStateException("Due not found.");
    }

    @Override
    public synchronized java.util.Optional<CycleItem> getCycleById(String cycleId) {
        // Id-only match. Never fall back to label matching: labels are user
        // free text and can collide (P-053).
        if (cycleId == null) return java.util.Optional.empty();
        return cycles.stream().filter(c -> c.id().equals(cycleId)).findFirst();
    }

    @Override
    public synchronized List<UserRegistration> getAllUserRegistrations() {
        return blankedRegistrations();
    }

    /** Admin-only read: the interface overload carries no actor, so this is the checked path. */
    @Override
    public synchronized List<UserRegistration> getAllUserRegistrations(CampusUser admin) {
        requireAdmin(admin);
        return blankedRegistrations();
    }

    private List<UserRegistration> blankedRegistrations() {
        // Password hashes are never exposed in bulk reads (P-049).
        List<UserRegistration> out = new ArrayList<>();
        for (UserRegistration u : userRegistrations) {
            out.add(new UserRegistration(
                    u.id(), u.fullName(), u.studentRoll(), u.department(), u.email(),
                    u.phone(), "", u.verificationStatus(), u.createdAt()
            ));
        }
        return out;
    }

    @Override
    public synchronized java.util.Optional<UserRegistration> getUserRegistration(String identifier) {
        if (identifier == null) return java.util.Optional.empty();
        String t = identifier.trim().toLowerCase();
        return userRegistrations.stream()
                .filter(u -> u.id().equalsIgnoreCase(t) || u.email().equalsIgnoreCase(t) || u.studentRoll().equalsIgnoreCase(t))
                .findFirst();
    }

    @Override
    public synchronized String submitUserRegistration(UserRegistration registration, String plainPassword) {
        Objects.requireNonNull(registration, "Registration is required.");
        if (plainPassword == null || plainPassword.length() < 6) {
            throw new IllegalArgumentException("A password of at least 6 characters is required to register.");
        }
        userRegistrations.removeIf(u -> u.id().equals(registration.id()));
        userRegistrations.add(registration);
        return registration.id();
    }

    @Override
    public synchronized void approveUserRegistration(String registrationId) {
        int i = findRegistration(registrationId);
        UserRegistration u = userRegistrations.get(i);
        // Only the decision flips. Everything the student requested — including
        // any requested role — is preserved; nothing is hardcoded here.
        userRegistrations.set(i, new UserRegistration(
                u.id(), u.fullName(), u.studentRoll(), u.department(), u.email(),
                u.phone(), u.passwordHash(), "APPROVED", u.createdAt()
        ));
    }

    /** Admin-only write: the interface overload carries no actor, so this is the checked path. */
    public synchronized void approveUserRegistration(CampusUser admin, String registrationId) {
        requireAdmin(admin);
        approveUserRegistration(registrationId);
        recordAudit(admin, "REGISTRATION", registrationId, "REGISTRATION_APPROVED", "");
    }

    @Override
    public synchronized void rejectUserRegistration(String registrationId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required.");
        }
        int i = findRegistration(registrationId);
        UserRegistration u = userRegistrations.get(i);
        userRegistrations.set(i, new UserRegistration(
                u.id(), u.fullName(), u.studentRoll(), u.department(), u.email(),
                u.phone(), u.passwordHash(), "REJECTED", u.createdAt()
        ));
    }

    /** Admin-only write: the interface overload carries no actor, so this is the checked path. */
    public synchronized void rejectUserRegistration(CampusUser admin, String registrationId, String reason) {
        requireAdmin(admin);
        rejectUserRegistration(registrationId, reason);
        // UserRegistration carries no reason field, so the reason is kept on the
        // audit trail rather than dropped.
        recordAudit(admin, "REGISTRATION", registrationId, "REGISTRATION_REJECTED", reason.trim());
    }

    @Override
    public synchronized boolean changePassword(String userIdOrEmail, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters.");
        }
        for (int i = 0; i < userRegistrations.size(); i++) {
            UserRegistration u = userRegistrations.get(i);
            if (u.id().equals(userIdOrEmail) || u.email().equalsIgnoreCase(userIdOrEmail) || u.studentRoll().equalsIgnoreCase(userIdOrEmail)) {
                // Fail closed: a blank stored hash never verifies on its own —
                // the current password is always required (P-011).
                if (u.passwordHash() == null || u.passwordHash().isBlank()) {
                    throw new IllegalStateException("Password change is unavailable for this account.");
                }
                if (!PasswordUtils.verify(oldPassword, u.passwordHash())) {
                    throw new IllegalArgumentException("Current password does not match.");
                }
                userRegistrations.set(i, new UserRegistration(
                        u.id(), u.fullName(), u.studentRoll(), u.department(), u.email(),
                        u.phone(), PasswordUtils.hash(newPassword),
                        u.verificationStatus(), u.createdAt()
                ));
                return true;
            }
        }
        return false;
    }

    /**
     * Raw lifecycle rewrite for system-owned transitions (booking, returning).
     * No role check here by design — callers are the lifecycle methods above,
     * while every manual transition goes through {@link #updateCycleState}.
     */
    private static CycleItem rewriteCycle(CycleItem c, AvailabilityStatus status) {
        return new CycleItem(
                c.id(), c.ownerId(), c.ownerName(), c.label(), c.type(), c.condition(),
                c.pickupPoint(), c.latitude(), c.longitude(), c.description(),
                c.reviewStatus(), status
        );
    }
}
