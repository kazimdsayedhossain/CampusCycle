package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.SupportConversation;
import bd.ac.kuet.campuscycle.domain.SupportMessage;

import bd.ac.kuet.campuscycle.domain.UserRegistration;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public interface CampusRepository {
    List<CycleItem> catalog(CampusUser user);
    List<CycleItem> pendingCycles();
    List<RentalRecord> rentals(CampusUser user);
    RentalRecord activeRental(CampusUser user);

    /** Payment method for {@link #book} — determines if wallet is charged at booking. */
    enum PaymentMethod { CAMPUS_PAY, DOCK_PAY }

    /**
     * Books a cycle and optionally charges the wallet in the same transaction.
     * When {@code method == CAMPUS_PAY}, the wallet is debited atomically and
     * the payment record is inserted as PAID. When {@code method == DOCK_PAY},
     * no wallet charge occurs and the payment record is inserted as UNPAID.
     */
    RentalRecord book(CampusUser renter, String cycleId, int minutes, PaymentMethod method);

    /** @deprecated use {@link #book(CampusUser, String, int, PaymentMethod)} */
    @Deprecated
    default RentalRecord book(CampusUser renter, String cycleId, int minutes) {
        return book(renter, cycleId, minutes, PaymentMethod.CAMPUS_PAY);
    }

    void returnRental(CampusUser renter, String rentalId);
    default void returnRental(CampusUser renter, String rentalId, int finalAmountPoisha) {
        returnRental(renter, rentalId);
    }
    /**
     * Full return in one call. Implementations must resolve the cycle from the
     * rental row — never trust a caller-supplied cycle id, and never accept the
     * old default's rentalId-as-cycleId confusion (P-052).
     */
    void returnRental(CampusUser renter, String rentalId, String cycleId, int finalAmountPoisha,
                      String returnLocation, AvailabilityStatus returnStatus);

    /**
     * What the return should collect from real-world money.
     *
     * <p>Only {@code cashPoisha} is honoured: it is the amount the hub attendant
     * confirmed, which the database cannot know. The fare and the overdue fine are
     * always derived server-side from the stored ride times, so a client can neither
     * inflate a charge nor dodge a penalty — anything it under-declares simply shows
     * up as recorded debt.
     */
    record ReturnCharge(int cashPoisha) {
        /** Nothing to collect beyond what the server will compute itself. */
        public static final ReturnCharge NONE = new ReturnCharge(0);

        public ReturnCharge {
            if (cashPoisha < 0) {
                throw new IllegalArgumentException("Collected cash must not be negative.");
            }
        }
    }

    /**
     * Authoritative settlement result of {@link #settleReturn}.
     *
     * <p>{@code finalAmountPoisha} and {@code fineBilledPoisha} are what the
     * database settled from the real elapsed time (never the client's estimate), and
     * {@code totalCollectedPoisha} is what the platform actually holds for the ride.
     * Anything still missing is recorded as a rental due inside the same transaction
     * and reported here as {@link #shortfallPoisha()}.
     */
    record SettlementOutcome(
            int finalAmountPoisha,
            int fineBilledPoisha,
            int quotedPoisha,
            int overtimeChargedPoisha,
            int fineChargedPoisha,
            int cashCollectedPoisha,
            int totalCollectedPoisha,
            int ownerPayoutCreditedPoisha,
            int platformFeePoisha,
            String dueId,
            boolean cashRide) {

        /** What the rider owes for this ride: settled fare plus any overdue fine. */
        public int owedTotalPoisha() {
            return Math.max(0, finalAmountPoisha) + Math.max(0, fineBilledPoisha);
        }

        /** Poisha the platform is still owed for this ride: booked as a due. */
        public int shortfallPoisha() {
            return Math.max(0, owedTotalPoisha() - totalCollectedPoisha);
        }
    }

    /**
     * Settles a return, collecting the ride's money, paying the owner and booking any
     * shortfall as debt — all in ONE transaction, so a failure can never take a
     * rider's money without returning the cycle (P-018), and the debt can never be
     * lost between settling and recording it.
     *
     * <p>Settlement model: the platform holds the fare in escrow and the owner is
     * credited at return, never before. The quoted fare was already taken from the
     * wallet at booking; {@code charge} collects what the ride actually owes now.
     * The owner is credited {@code min(settled payout, collected)}: the platform never
     * pays out money it has not received, and what is left is recorded as debt rather
     * than lost.
     */
    default SettlementOutcome settleReturn(CampusUser renter, String rentalId, String cycleId,
                                            int finalAmountPoisha, String returnLocation,
                                            AvailabilityStatus returnStatus,
                                            PaymentMethod method, ReturnCharge charge) {
        returnRental(renter, rentalId, cycleId, finalAmountPoisha, returnLocation, returnStatus);
        return new SettlementOutcome(finalAmountPoisha, 0, 0, 0, 0, 0, 0, 0, 0, null, false);
    }

    /**
     * Payment method a rental was started with. Pre-migration rows have no stored
     * method, and those were all Campus Pay, so the default is {@code CAMPUS_PAY}.
     */
    default PaymentMethod rentalPaymentMethod(String rentalId) {
        return PaymentMethod.CAMPUS_PAY;
    }

    void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason);

    /**
     * Moves up to {@code count} AVAILABLE cycles between hubs.
     *
     * @return the number of cycles actually moved
     */
    int rebalanceHub(CampusUser admin, String sourceHub, String targetHub, int count);

    /**
     * Single choke point for every availability transition (P-034).
     * Implementations must enforce the actor's role, the expected-current-state
     * precondition, and the affected-row count — all inside one statement.
     */
    void updateCycleState(String cycleId, AvailabilityStatus expectedCurrent,
                          AvailabilityStatus next, CampusUser actor);

    void updateCycleLocation(CampusUser actor, String cycleId, String newLocation);

    default void resolveDispute(CampusUser admin, String disputeId, String notes) {
        throw new UnsupportedOperationException("Disputes are not supported by this repository.");
    }

    default List<CycleItem> allCycles(CampusUser admin) {
        return catalog(null);
    }

    default Optional<CycleItem> getCycleById(String cycleId) {
        return allCycles(null).stream().filter(c -> c.id().equals(cycleId)).findFirst();
    }

    default List<RentalRecord> allRentals() {
        throw new UnsupportedOperationException("Rental aggregates are not supported by this repository.");
    }

    /**
     * Admin-only aggregate read (P-049, P-050). Student dashboards must never
     * call this — use {@link #rentals(CampusUser)} instead.
     */
    default List<RentalRecord> allRentals(CampusUser admin) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to access all rentals.");
        }
        return allRentals();
    }

    default void addCycle(CycleItem cycle) {
        throw new UnsupportedOperationException("Fleet writes are not supported by this repository.");
    }

    /**
     * Admin fleet write (P-049). The no-arg {@link #addCycle(CycleItem)} is the
     * student self-registration path and must force PENDING_REVIEW.
     */
    default void addCycle(CampusUser admin, CycleItem cycle) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to add cycle.");
        }
        addCycle(cycle);
    }

    default void updateCycle(CycleItem cycle) {
        throw new UnsupportedOperationException("Fleet writes are not supported by this repository.");
    }

    /** Admin fleet write (P-049). */
    default void updateCycle(CampusUser admin, CycleItem cycle) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to update cycle.");
        }
        updateCycle(cycle);
    }

    default void deleteCycle(String cycleId) {
        throw new UnsupportedOperationException("Fleet writes are not supported by this repository.");
    }

    /** Admin fleet write (P-049). */
    default void deleteCycle(CampusUser admin, String cycleId) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to delete cycle.");
        }
        deleteCycle(cycleId);
    }

    /**
     * Audit hook. The default logs loudly instead of silently dropping the
     * event (P-051); server-backed implementations must persist it.
     */
    default void recordAudit(CampusUser actor, String entityType, String entityId,
                             String action, String details) {
        Logger.getLogger(CampusRepository.class.getName()).warning(
                "Audit event not persisted (unsupported repository): "
                        + action + " " + entityType + "/" + entityId
                        + " by " + (actor == null ? "unknown" : actor.id()));
    }

    default List<bd.ac.kuet.campuscycle.domain.UserRegistration> getAllUserRegistrations() {
        return List.of();
    }

    /**
     * Returns all user registrations with admin authorization (P-049).
     * Implementations must reject non-ADMIN callers.
     */
    default List<bd.ac.kuet.campuscycle.domain.UserRegistration> getAllUserRegistrations(CampusUser admin) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to access all user registrations.");
        }
        return getAllUserRegistrations();
    }

    default java.util.Optional<bd.ac.kuet.campuscycle.domain.UserRegistration> getUserRegistration(String identifier) {
        return java.util.Optional.empty();
    }

    /**
     * Records a student registration.
     *
     * <p>{@code plainPassword} is required and is used only to provision the Supabase
     * auth identity: {@code profiles.id} references {@code auth.users(id)}, so a
     * registration is only approvable when a real auth user already exists — and a
     * stored hash cannot provision one. It is passed explicitly rather than kept on
     * {@link bd.ac.kuet.campuscycle.domain.UserRegistration} so a secret never travels
     * inside a domain record.
     *
     * @return the created registration id, which is the Supabase auth user id
     */
    default String submitUserRegistration(bd.ac.kuet.campuscycle.domain.UserRegistration registration,
                                        String plainPassword) {
        return registration.id();
    }

    default void approveUserRegistration(String registrationId) {}

    default void rejectUserRegistration(String registrationId, String reason) {}

    default boolean changePassword(String userIdOrEmail, String oldPassword, String newPassword) {
        return false;
    }

    /** Open a fare dispute for a RETURNED rental. Default unsupported for legacy impls. */
    default String openDispute(CampusUser renter, String rentalId, String reason) {
        throw new UnsupportedOperationException("Disputes are not supported by this repository.");
    }

    default String createSupportConversation(CampusUser student, String subject, String message) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default List<SupportConversation> supportConversations(CampusUser user) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default List<SupportMessage> supportMessages(CampusUser user, String conversationId) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default void postSupportMessage(CampusUser user, String conversationId, String body) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default List<bd.ac.kuet.campuscycle.domain.DisputeItem> disputeQueue(CampusUser admin) {
        throw new UnsupportedOperationException("Disputes queue is not supported by this repository.");
    }

    /**
     * Settled earnings for one bike owner. The caller may read only their own
     * earnings unless they are ADMIN. Pre-fee rows count toward gross with zero fee.
     */
    default bd.ac.kuet.campuscycle.domain.OwnerEarnings ownerEarnings(CampusUser caller, String ownerId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view earnings.");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner id is required.");
        }
        if (!caller.id().equals(ownerId) && caller.role() != bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            throw new SecurityException("You can only view your own bike earnings.");
        }
        throw new UnsupportedOperationException("Earnings are not supported by this repository.");
    }

    /**
     * Platform-wide settled earnings. ADMIN only.
     */
    default bd.ac.kuet.campuscycle.domain.PlatformEarnings platformEarnings(CampusUser admin) {
        if (admin == null || admin.role() != bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to view platform earnings.");
        }
        throw new UnsupportedOperationException("Earnings are not supported by this repository.");
    }

    /**
     * Records persistent ride debt for a rental (overtime/fine the wallet could
     * not cover at return). The caller must be the renter; at most one due per
     * rental (replay returns the existing row).
     */
    default bd.ac.kuet.campuscycle.domain.RentalDue recordDue(
            CampusUser actor, String rentalId, int amountPoisha, String reason) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to record dues.");
        }
        throw new UnsupportedOperationException("Dues are not supported by this repository.");
    }

    /** Sum of UNPAID + PARTIAL dues outstanding for a user. Own-or-ADMIN. */
    default int unpaidDuesTotal(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        throw new UnsupportedOperationException("Dues are not supported by this repository.");
    }

    /** Count of UNPAID + PARTIAL dues (abuse-brake input). Own-or-ADMIN. */
    default int unpaidDuesCount(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        throw new UnsupportedOperationException("Dues are not supported by this repository.");
    }

    /** All dues for a user, newest first. Own-or-ADMIN. */
    default java.util.List<bd.ac.kuet.campuscycle.domain.RentalDue> userDues(
            CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        throw new UnsupportedOperationException("Dues are not supported by this repository.");
    }

    /**
     * Applies up to {@code maxPoisha} of wallet money to the user's oldest
     * dues first. Returns the amount actually applied. Single transaction.
     */
    default int settleDues(CampusUser user, int maxPoisha) {
        if (user == null) {
            throw new SecurityException("Sign-in is required to settle dues.");
        }
        throw new UnsupportedOperationException("Dues are not supported by this repository.");
    }

    /**
     * Every due in the system, newest first. ADMIN only (back office queue).
     */
    default java.util.List<bd.ac.kuet.campuscycle.domain.RentalDue> allDues(CampusUser admin) {
        if (admin == null || admin.role() != bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to view all dues.");
        }
        throw new UnsupportedOperationException("Dues are not supported by this repository.");
    }

    /**
     * Admin terminal action: marks a due WAIVED with a recorded reason.
     * Waived dues no longer block booking.
     */
    default void waiveDue(CampusUser admin, String dueId, String reason) {
        if (admin == null || admin.role() != bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to waive dues.");
        }
        throw new UnsupportedOperationException("Dues are not supported by this repository.");
    }

    /**
     * Creates a new user with a specific role via Supabase Admin API.
     * Requires SUPABASE_SERVICE_ROLE_KEY to be configured.
     *
     * @param admin   the admin creating the user (must have ADMIN role)
     * @param email   user email
     * @param password initial password
     * @param displayName display name
     * @param role    role to assign (STUDENT, TECHNICIAN, ADMIN)
     * @return the created user's UUID
     * @throws SecurityException if admin is not ADMIN
     * @throws AppError if Admin API is not configured or request fails
     */
    default String createUserWithRole(CampusUser admin, String email, String password,
                                      String displayName, Role role) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to create users with roles.");
        }
        throw new UnsupportedOperationException("User creation via Admin API not supported by this repository.");
    }
}
