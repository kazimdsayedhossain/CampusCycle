package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;

import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live Supabase PostgreSQL repository for CampusCycle.
 * Requires active database connection - no offline fallback.
 */
public final class SupabaseCampusRepository implements CampusRepository {

    private static final Logger LOGGER = Logger.getLogger(SupabaseCampusRepository.class.getName());
    // Single clock source (P-044, P-064): never use the system default zone.
    private static final ZoneId DHAKA = CampusTime.DHAKA;

    public SupabaseCampusRepository() {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required. Application cannot run offline.");
        }
        LOGGER.info("SupabaseCampusRepository initialized with active database connection.");
    }

    @Override
    public List<CycleItem> allCycles(CampusUser admin) {
        // P-049: full fleet (including PENDING_REVIEW / QUARANTINE / RETIRED) is admin-only.
        if (admin == null) {
            throw new SecurityException("Sign-in is required to list the full fleet.");
        }
        admin.requireRole(Role.ADMIN);
        List<CycleItem> items = new ArrayList<>();
        String sql = """
                SELECT c.id, c.owner_id, COALESCE(p.display_name, 'KUET Member') AS owner_name,
                       c.label, c.cycle_type, c.physical_condition, c.pickup_point,
                       c.latitude, c.longitude, c.description, c.review_status, c.availability_status
                FROM public.cycles c LEFT JOIN public.profiles p ON p.id = c.owner_id
                ORDER BY c.label;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                items.add(mapCycleItem(rs));
            }
            return items;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load live allCycles from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load cycles from database.", e);
        }
    }

    @Override
    public void addCycle(CycleItem cycle) {
        // No-arg path = student self-registration: always force PENDING_REVIEW
        // so a caller can never self-approve. Admins use addCycle(admin, cycle).
        CycleItem pending = new CycleItem(
                cycle.id(), cycle.ownerId(), cycle.ownerName(), cycle.label(),
                cycle.type(), cycle.condition(), cycle.pickupPoint(),
                cycle.latitude(), cycle.longitude(), cycle.description(),
                ReviewStatus.PENDING_REVIEW, AvailabilityStatus.AVAILABLE);
        insertCycleRow(pending);
    }

    /**
     * Adds a cycle with admin authorization (P-049).
     * @param admin the admin user (must have Role.ADMIN)
     * @param cycle the cycle to add
     * @throws SecurityException if admin is null or not ADMIN
     */
    @Override
    public void addCycle(CampusUser admin, CycleItem cycle) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to add cycle.");
        }
        insertCycleRow(cycle);
    }

    /** Shared INSERT used by both the student self-registration path and the admin path. */
    private void insertCycleRow(CycleItem cycle) {
        UUID idUuid = toUuid(cycle.id());
        UUID ownerUuid = toUuid(cycle.ownerId());
        String sql = """
            INSERT INTO public.cycles (id, owner_id, label, cycle_type, physical_condition, pickup_point, latitude, longitude, description, owner_phone, review_status, availability_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '+8801700000000', ?, ?, NOW(), NOW())
            ON CONFLICT (id) DO UPDATE SET
                label = EXCLUDED.label,
                cycle_type = EXCLUDED.cycle_type,
                physical_condition = EXCLUDED.physical_condition,
                pickup_point = EXCLUDED.pickup_point,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                description = EXCLUDED.description,
                review_status = EXCLUDED.review_status,
                availability_status = EXCLUDED.availability_status,
                updated_at = NOW();
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, idUuid);
            stmt.setObject(2, ownerUuid);
            stmt.setString(3, cycle.label());
            stmt.setString(4, cycle.type().name());
            stmt.setString(5, cycle.condition().name());
            stmt.setString(6, cycle.pickupPoint());
            stmt.setDouble(7, cycle.latitude());
            stmt.setDouble(8, cycle.longitude());
            stmt.setString(9, cycle.description() != null ? cycle.description() : "");
            stmt.setString(10, cycle.reviewStatus().name());
            stmt.setString(11, cycle.availabilityStatus().name());
            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to insert cycle into Supabase", e);
            throw new AppError("DB_ERROR", "Failed to add cycle.", e);
        }
    }

    @Override
    public void updateCycle(CycleItem cycle) {
        updateCycle(null, cycle);
    }

    /**
     * Updates a cycle with admin authorization (P-049).
     * @param admin the admin user (must have Role.ADMIN)
     * @param cycle the cycle to update
     * @throws SecurityException if admin is null or not ADMIN
     */
    @Override
    public void updateCycle(CampusUser admin, CycleItem cycle) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to update cycle.");
        }
        UUID idUuid = toUuid(cycle.id());
        String sql = """
            UPDATE public.cycles
            SET label = ?, cycle_type = ?, physical_condition = ?, pickup_point = ?,
                latitude = ?, longitude = ?, description = ?, review_status = ?, availability_status = ?, updated_at = NOW()
            WHERE id = ?;
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cycle.label());
            stmt.setString(2, cycle.type().name());
            stmt.setString(3, cycle.condition().name());
            stmt.setString(4, cycle.pickupPoint());
            stmt.setDouble(5, cycle.latitude());
            stmt.setDouble(6, cycle.longitude());
            stmt.setString(7, cycle.description() != null ? cycle.description() : "");
            stmt.setString(8, cycle.reviewStatus().name());
            stmt.setString(9, cycle.availabilityStatus().name());
            stmt.setObject(10, toUuid(cycle.id()));
            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update cycle in Supabase", e);
            throw new AppError("DB_ERROR", "Failed to update cycle.", e);
        }
    }

    @Override
    public void deleteCycle(String cycleId) {
        deleteCycle(null, cycleId);
    }

    /**
     * Deletes a cycle with admin authorization (P-049).
     * @param admin the admin user (must have Role.ADMIN)
     * @param cycleId the cycle ID to delete
     * @throws SecurityException if admin is null or not ADMIN
     */
    @Override
    public void deleteCycle(CampusUser admin, String cycleId) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to delete cycle.");
        }
        UUID idUuid = toUuid(cycleId);
        String sql = "DELETE FROM public.cycles WHERE id = ?;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, idUuid);
            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete cycle in Supabase", e);
            throw new AppError("DB_ERROR", "Failed to delete cycle.", e);
        }
    }

    /**
     * Single choke point for every availability transition (P-034).
     * Role rule: MAINTENANCE / QUARANTINE / AVAILABLE transitions allow
     * TECHNICIAN or ADMIN; any transition into or out of RETIRED requires ADMIN;
     * RENTED is never set directly — only the book/return paths may use it (P-061).
     * The write is a single guarded statement; rowcount != 1 fails loudly (P-039).
     */
    @Override
    public void updateCycleState(String cycleId, AvailabilityStatus expectedCurrent,
                                 AvailabilityStatus next, CampusUser actor) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to change cycle state.");
        }
        if (cycleId == null || cycleId.isBlank() || !isUuid(cycleId)) {
            throw new AppError("INVALID_ID", "Invalid cycle ID format.");
        }
        if (expectedCurrent == null || next == null) {
            throw new IllegalArgumentException("Expected and next availability states are required.");
        }
        if (next == AvailabilityStatus.RENTED) {
            throw new IllegalStateException("RENTED may only be set via the book/return paths.");
        }
        if (next == AvailabilityStatus.RETIRED || expectedCurrent == AvailabilityStatus.RETIRED) {
            actor.requireRole(Role.ADMIN);
        } else {
            actor.requireRole(Role.TECHNICIAN, Role.ADMIN);
        }

        String sql = "UPDATE public.cycles SET availability_status = ?, updated_at = NOW() "
                + "WHERE id = ?::uuid AND availability_status = ?;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, next.name());
            stmt.setObject(2, UUID.fromString(cycleId));
            stmt.setString(3, expectedCurrent.name());
            int updated = stmt.executeUpdate();
            if (updated != 1) {
                throw new AppError("CYCLE_STATE_MISMATCH",
                        "Cycle is not in the expected state; no changes were made.");
            }
            if (next == AvailabilityStatus.RETIRED) {
                stampRetiredAt(conn, cycleId);
            }
        } catch (AppError | IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update cycle state in Supabase", e);
            throw new AppError("DB_ERROR", "Failed to update cycle state.", e);
        }
    }

    /** Best-effort retired_at stamp; auxiliary to the guarded state change above. */
    private void stampRetiredAt(Connection conn, String cycleId) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE public.cycles SET retired_at = NOW() WHERE id = ?::uuid")) {
            stmt.setObject(1, UUID.fromString(cycleId));
            stmt.executeUpdate();
        } catch (Exception e) {
            // Live variance: older databases lack retired_at — the RETIRED state itself already committed.
            LOGGER.log(Level.WARNING, "Could not stamp retired_at for cycle " + cycleId, e);
        }
    }

    @Override
    public void updateCycleLocation(CampusUser actor, String cycleId, String newLocation) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to move a cycle.");
        }
        actor.requireRole(Role.TECHNICIAN, Role.ADMIN);
        if (cycleId == null || cycleId.isBlank() || !isUuid(cycleId)) {
            throw new AppError("INVALID_ID", "Invalid cycle ID format.");
        }
        if (newLocation == null || newLocation.isBlank()) {
            throw new IllegalArgumentException("New location is required.");
        }
        String sql = "UPDATE public.cycles SET pickup_point = ?, updated_at = NOW() WHERE id = ?::uuid;";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newLocation.trim());
            stmt.setObject(2, UUID.fromString(cycleId));
            int updated = stmt.executeUpdate();
            if (updated != 1) {
                throw new AppError("CYCLE_NOT_FOUND", "Cycle not found; location unchanged.");
            }
        } catch (AppError | IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update cycle location in Supabase", e);
            throw new AppError("DB_ERROR", "Failed to update cycle location.", e);
        }
    }

    private UUID toUuid(String val) {
        if (val == null || val.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(val);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(val.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Override
    public List<CycleItem> catalog(CampusUser user) {
        List<CycleItem> items = new ArrayList<>();
        String sql = """
                SELECT c.id, c.owner_id, COALESCE(p.display_name, 'KUET Member') AS owner_name,
                       c.label, c.cycle_type, c.physical_condition, c.pickup_point,
                       c.latitude, c.longitude, c.description, c.review_status, c.availability_status
                FROM public.cycles c LEFT JOIN public.profiles p ON p.id = c.owner_id
                WHERE c.review_status = 'APPROVED' AND c.availability_status = 'AVAILABLE'
                ORDER BY c.label;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String ownerId = rs.getString("owner_id");
                if (user != null && ownerId != null && ownerId.equals(user.id())) {
                    continue; // Student cannot rent their own cycle
                }

                items.add(mapCycleItem(rs));
            }
            return items;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load live catalog from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load catalog.", e);
        }
    }

    @Override
    public List<CycleItem> pendingCycles() {
        // P-049 NOTE: no actor in contract — admin gating lives with the caller (see addCycle).
        List<CycleItem> items = new ArrayList<>();
        String sql = """
                SELECT c.id, c.owner_id, COALESCE(p.display_name, 'KUET Member') AS owner_name,
                       c.label, c.cycle_type, c.physical_condition, c.pickup_point,
                       c.latitude, c.longitude, c.description, c.review_status, c.availability_status
                FROM public.cycles c LEFT JOIN public.profiles p ON p.id = c.owner_id
                WHERE c.review_status = 'PENDING_REVIEW'
                ORDER BY c.updated_at DESC;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                items.add(mapCycleItem(rs));
            }
            return items;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load pending cycles from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load pending cycles.", e);
        }
    }

    @Override
    public List<RentalRecord> rentals(CampusUser user) {
        if (!isUuid(user.id())) {
            throw new AppError("INVALID_USER", "Invalid user ID format.");
        }

        List<RentalRecord> records = new ArrayList<>();
        String sql = "SELECT r.id, r.cycle_id, c.label, r.renter_id, r.requested_minutes, "
                + "r.quoted_amount_poisha, r.final_amount_poisha, r.overdue_fine_poisha"
                + rentalFeeColumns()
                + ", r.dropoff_hub, r.state, r.started_at, r.due_at, r.returned_at "
                + "FROM public.rentals r "
                + "JOIN public.cycles c ON r.cycle_id = c.id "
                + "WHERE r.renter_id = ? "
                + "ORDER BY r.started_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(user.id()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRentalRecord(rs));
                }
            }
            return records;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load rentals from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load rentals.", e);
        }
    }

    @Override
    public RentalRecord activeRental(CampusUser user) {
        if (!isUuid(user.id())) {
            throw new AppError("INVALID_USER", "Invalid user ID format.");
        }

        String sql = "SELECT r.id, r.cycle_id, c.label, r.renter_id, r.requested_minutes, "
                + "r.quoted_amount_poisha, r.final_amount_poisha, r.overdue_fine_poisha"
                + rentalFeeColumns()
                + ", r.dropoff_hub, r.state, r.started_at, r.due_at, r.returned_at "
                + "FROM public.rentals r "
                + "JOIN public.cycles c ON r.cycle_id = c.id "
                + "WHERE r.renter_id = ? AND r.state = 'ACTIVE' "
                + "LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(user.id()));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRentalRecord(rs);
                }
            }
            return null;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to query active rental from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to query active rental.", e);
        }
    }

    /**
     * @deprecated Legacy path without atomic wallet charge; prefer
     * {@link #book(CampusUser, String, int, PaymentMethod)}. Kept only for
     * backward compatibility — production callers use the 4-arg method.
     */
    @Deprecated
    @Override
    public RentalRecord book(CampusUser renter, String cycleId, int minutes) {
        if (renter == null) {
            throw new SecurityException("Sign-in is required to book a cycle.");
        }
        if (!isUuid(cycleId) || !isUuid(renter.id())) {
            throw new AppError("INVALID_ID", "Invalid cycle or user ID format.");
        }

        // Execute atomic database transaction with row locking.
        // No `synchronized` by design (P-151): instances are created ad hoc, so a JVM
        // lock excludes nothing. Correctness rests on SELECT ... FOR UPDATE plus the
        // partial unique index on ACTIVE rentals (P-152).
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Verify renter has no active rental.
                // NOTE (P-152): FOR UPDATE locks existing rows only — with no ACTIVE row it
                // locks nothing. The real guard is the partial unique index; its 23505
                // violation is translated below instead of becoming a generic failure.
                String activeCheck = "SELECT id FROM public.rentals WHERE renter_id = ? AND state = 'ACTIVE' FOR UPDATE";
                try (PreparedStatement chk = conn.prepareStatement(activeCheck)) {
                    chk.setObject(1, UUID.fromString(renter.id()));
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next()) {
                            throw new IllegalStateException("Return your active cycle before starting another rental.");
                        }
                    }
                }

                // 2. Lock cycle row
                String cycleSql = "SELECT availability_status, review_status, owner_id FROM public.cycles WHERE id = ? FOR UPDATE";
                try (PreparedStatement cStmt = conn.prepareStatement(cycleSql)) {
                    cStmt.setObject(1, UUID.fromString(cycleId));
                    try (ResultSet rs = cStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Cycle not found in registry.");
                        }
                        String avail = rs.getString("availability_status");
                        String rev = rs.getString("review_status");
                        String owner = rs.getString("owner_id");

                        if (!"AVAILABLE".equalsIgnoreCase(avail) || !"APPROVED".equalsIgnoreCase(rev)) {
                            throw new IllegalStateException("Cycle was just booked by another student. Please select an adjacent bike.");
                        }
                        if (owner != null && owner.equalsIgnoreCase(renter.id())) {
                            throw new IllegalStateException("You cannot rent your own cycle listing.");
                        }
                    }
                }

                // 3. Compute tariff client-side for the affordability check and the
                // payment-row estimate. The fare trigger recomputes authoritatively on
                // INSERT, so these values are advisory (P-016, P-024).
                int totalPoisha = TariffService.quotePoisha(minutes);
                if (renter.role() == Role.STUDENT) {
                    totalPoisha -= TariffService.subsidyPoisha(totalPoisha, true);
                }

                // 4. Resolve active rate card
                UUID rateCardId;
                int rateVersion;
                try (PreparedStatement rc = conn.prepareStatement(
                        "SELECT id, version FROM public.rate_cards WHERE active_from <= now() AND (active_until IS NULL OR active_until > now()) ORDER BY version DESC LIMIT 1")) {
                    try (ResultSet rs = rc.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("No active rate card.");
                        rateCardId = (UUID) rs.getObject("id");
                        rateVersion = rs.getInt("version");
                    }
                }

                // 5. P-025: affordability is enforced here, inside the booking
                // transaction, against the live wallet row (locked) — never against a
                // stale UI balance.
                int balancePoisha = 0;
                boolean hasWallet = false;
                try (PreparedStatement wStmt = conn.prepareStatement(
                        "SELECT balance_poisha FROM public.wallets WHERE user_id = ? FOR UPDATE")) {
                    wStmt.setString(1, renter.id());
                    try (ResultSet rs = wStmt.executeQuery()) {
                        if (rs.next()) {
                            balancePoisha = rs.getInt("balance_poisha");
                            hasWallet = true;
                        }
                    }
                }
                if (!hasWallet || balancePoisha < totalPoisha) {
                    throw new IllegalStateException(
                            "Insufficient Campus Pay balance. Please top up before riding.");
                }

                UUID rentalId = UUID.randomUUID();
                UUID idempotencyKey = UUID.randomUUID();
                Timestamp nowTs = new Timestamp(System.currentTimeMillis());
                Timestamp dueTs = new Timestamp(System.currentTimeMillis() + (minutes * 60L * 1000L));

                String insertSql = """
                        INSERT INTO public.rentals
                        (id, cycle_id, renter_id, rate_card_version, quoted_amount_poisha, requested_minutes, state, started_at, due_at, currency, idempotency_key)
                        VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 'BDT', ?);
                        """;

                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    ins.setObject(1, rentalId);
                    ins.setObject(2, UUID.fromString(cycleId));
                    ins.setObject(3, UUID.fromString(renter.id()));
                    ins.setInt(4, rateVersion);
                    ins.setLong(5, (long) totalPoisha);
                    ins.setInt(6, minutes);
                    ins.setTimestamp(7, nowTs);
                    ins.setTimestamp(8, dueTs);
                    ins.setObject(9, idempotencyKey);
                    ins.executeUpdate();
                } catch (SQLException se) {
                    // P-152: the partial unique index fired — a retry or a double-click,
                    // not a generic failure.
                    if ("23505".equals(se.getSQLState())) {
                        String detail = String.valueOf(se.getMessage()).toLowerCase();
                        if (detail.contains("cycle")) {
                            throw new IllegalStateException(
                                    "Cycle was just booked by another student. Please select an adjacent bike.");
                        }
                        throw new IllegalStateException(
                                "Return your active cycle before starting another rental.");
                    }
                    throw se;
                }

                // 6. Create payment record UNPAID until provider webhook (P-026).
                insertPaymentUnpaid(conn, UUID.randomUUID(), rentalId, totalPoisha);

                // 7. Mark cycle as RENTED — guarded so a concurrent transition fails loudly (P-162).
                String updateCycle = "UPDATE public.cycles SET availability_status = 'RENTED', updated_at = now() "
                        + "WHERE id = ? AND availability_status = 'AVAILABLE'";
                try (PreparedStatement upd = conn.prepareStatement(updateCycle)) {
                    upd.setObject(1, UUID.fromString(cycleId));
                    int moved = upd.executeUpdate();
                    if (moved != 1) {
                        throw new AppError("CYCLE_UNAVAILABLE",
                                "Cycle was just booked by another student. Please select an adjacent bike.");
                    }
                }

                // 8. Record audit event (canonical columns, legacy fallback inside helper).
                insertAuditEvent(conn, renter.id(), "RENTAL", rentalId.toString(), "STARTED", "{}");

                // 9. Read back the stored row: the fare trigger is authoritative, so the
                // response is built from STORED values, never from the client quote.
                RentalRecord stored;
                try (PreparedStatement sel = conn.prepareStatement(
                        """
                        SELECT r.id, r.cycle_id, c.label, r.renter_id, r.requested_minutes,
                               r.quoted_amount_poisha, r.final_amount_poisha, r.overdue_fine_poisha,
                               r.dropoff_hub, r.state, r.started_at, r.due_at, r.returned_at
                        FROM public.rentals r JOIN public.cycles c ON r.cycle_id = c.id
                        WHERE r.id = ?""")) {
                    sel.setObject(1, rentalId);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            throw new AppError("BOOK_FAILED", "Booking failed. Please retry.");
                        }
                        stored = mapRentalRecord(rs);
                    }
                }

                conn.commit();
                return stored;

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    LOGGER.log(Level.WARNING, "Booking rollback failed", rb);
                }
                throw e;
            } finally {
                // P-162: do not rely on pool-release coupling to restore autocommit.
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ac) {
                    LOGGER.log(Level.WARNING, "Failed to restore autocommit after booking", ac);
                }
            }
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            // P-059: domain failures keep their actionable message.
            throw e;
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Live booking failed", e);
            throw new AppError("BOOK_FAILED", "Booking failed. Please retry.", e);
        }
    }

    @Override
    public RentalRecord book(CampusUser renter, String cycleId, int minutes, PaymentMethod method) {
        if (renter == null) {
            throw new SecurityException("Sign-in is required to book a cycle.");
        }
        if (!isUuid(cycleId) || !isUuid(renter.id())) {
            throw new AppError("INVALID_ID", "Invalid cycle or user ID format.");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Verify renter has no active rental.
                String activeCheck = "SELECT id FROM public.rentals WHERE renter_id = ? AND state = 'ACTIVE' FOR UPDATE";
                try (PreparedStatement chk = conn.prepareStatement(activeCheck)) {
                    chk.setObject(1, UUID.fromString(renter.id()));
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next()) {
                            throw new IllegalStateException("Return your active cycle before starting another rental.");
                        }
                    }
                }

                // 2. Lock cycle row
                String cycleSql = "SELECT availability_status, review_status, owner_id FROM public.cycles WHERE id = ? FOR UPDATE";
                try (PreparedStatement cStmt = conn.prepareStatement(cycleSql)) {
                    cStmt.setObject(1, UUID.fromString(cycleId));
                    try (ResultSet rs = cStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Cycle not found in registry.");
                        }
                        String avail = rs.getString("availability_status");
                        String rev = rs.getString("review_status");
                        String owner = rs.getString("owner_id");

                        if (!"AVAILABLE".equalsIgnoreCase(avail) || !"APPROVED".equalsIgnoreCase(rev)) {
                            throw new IllegalStateException("Cycle was just booked by another student. Please select an adjacent bike.");
                        }
                        if (owner != null && owner.equalsIgnoreCase(renter.id())) {
                            throw new IllegalStateException("You cannot rent your own cycle listing.");
                        }
                    }
                }

                // 3. Compute tariff client-side for the affordability check.
                int totalPoisha = TariffService.quotePoisha(minutes);
                if (renter.role() == Role.STUDENT) {
                    totalPoisha -= TariffService.subsidyPoisha(totalPoisha, true);
                }

                // 4. Resolve active rate card
                UUID rateCardId;
                int rateVersion;
                try (PreparedStatement rc = conn.prepareStatement(
                        "SELECT id, version FROM public.rate_cards WHERE active_from <= now() AND (active_until IS NULL OR active_until > now()) ORDER BY version DESC LIMIT 1")) {
                    try (ResultSet rs = rc.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("No active rate card.");
                        rateCardId = (UUID) rs.getObject("id");
                        rateVersion = rs.getInt("version");
                    }
                }

                // 4b. Dues gate + auto-settle (CAMPUS_PAY only). Unpaid dues block
                // new bookings; when the wallet holds money, arrears clear
                // oldest-first before the fare check, so returning riders recover
                // automatically. Repeat offenders hit the review brake instead.
                if (method == PaymentMethod.CAMPUS_PAY && duesTableAvailable()) {
                    int owed = duesOwedOn(conn, renter.id());
                    if (owed > 0) {
                        int openCount = duesOpenCountOn(conn, renter.id());
                        if (openCount >= TariffService.OVERDUE_REVIEW_DUES_COUNT) {
                            throw new IllegalStateException(
                                    "Your account is under review for repeated unpaid dues ("
                                            + openCount + " open). Please contact the cycle office to ride again.");
                        }
                        int cleared = settleDuesInTx(conn, renter, Integer.MAX_VALUE);
                        int remaining = duesOwedOn(conn, renter.id());
                        if (remaining > 0) {
                            throw new IllegalStateException(
                                    "You have " + Money.formatTaka(remaining) + " in unpaid dues"
                                            + (cleared > 0 ? " (" + Money.formatTaka(cleared)
                                                    + " auto-cleared just now)" : "")
                                            + ". Top up to ride again.");
                        }
                    }
                }

                // 5. Affordability enforced inside the booking transaction (P-025) —
                // Campus Pay only. Cash-at-hub (DOCK_PAY) needs no wallet balance:
                // the renter settles in cash at return and the owner is credited then.
                if (method == PaymentMethod.CAMPUS_PAY) {
                    int balancePoisha = 0;
                    boolean hasWallet = false;
                    try (PreparedStatement wStmt = conn.prepareStatement(
                            "SELECT balance_poisha FROM public.wallets WHERE user_id = ? FOR UPDATE")) {
                        wStmt.setString(1, renter.id());
                        try (ResultSet rs = wStmt.executeQuery()) {
                            if (rs.next()) {
                                balancePoisha = rs.getInt("balance_poisha");
                                hasWallet = true;
                            }
                        }
                    }
                    if (!hasWallet || balancePoisha < totalPoisha) {
                        throw new IllegalStateException("Insufficient Campus Pay balance. Please top up before riding.");
                    }
                }

                UUID rentalId = UUID.randomUUID();
                UUID idempotencyKey = UUID.randomUUID();
                Timestamp nowTs = new Timestamp(System.currentTimeMillis());
                Timestamp dueTs = new Timestamp(System.currentTimeMillis() + (minutes * 60L * 1000L));

                // payment_method is added by migration 004. Before that the insert must
                // not reference it: a failed statement aborts the whole booking.
                // Pre-migration rows read back as CAMPUS_PAY, which is correct.
                boolean paymentCols = paymentMethodColumnAvailable();
                StringBuilder insertSql = new StringBuilder("""
                        INSERT INTO public.rentals
                        (id, cycle_id, renter_id, rate_card_version, quoted_amount_poisha, requested_minutes, state, started_at, due_at, currency, idempotency_key""");
                if (paymentCols) {
                    insertSql.append(", payment_method");
                }
                insertSql.append(")\n VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 'BDT', ?");
                if (paymentCols) {
                    insertSql.append(", ?");
                }
                insertSql.append(");\n");

                try (PreparedStatement ins = conn.prepareStatement(insertSql.toString())) {
                    // Bind strictly in column order: payment_method is the last
                    // column, so idempotency_key is bound first.
                    int idx = 1;
                    ins.setObject(idx++, rentalId);
                    ins.setObject(idx++, UUID.fromString(cycleId));
                    ins.setObject(idx++, UUID.fromString(renter.id()));
                    ins.setInt(idx++, rateVersion);
                    ins.setLong(idx++, (long) totalPoisha);
                    ins.setInt(idx++, minutes);
                    ins.setTimestamp(idx++, nowTs);
                    ins.setTimestamp(idx++, dueTs);
                    ins.setObject(idx++, idempotencyKey);
                    if (paymentCols) {
                        ins.setString(idx, method.name());
                    }
                    ins.executeUpdate();
                } catch (SQLException se) {
                    if ("23505".equals(se.getSQLState())) {
                        String detail = String.valueOf(se.getMessage()).toLowerCase();
                        if (detail.contains("cycle")) {
                            throw new IllegalStateException("Cycle was just booked by another student. Please select an adjacent bike.");
                        }
                        throw new IllegalStateException("Return your active cycle before starting another rental.");
                    }
                    throw se;
                }

                // 6. Payment record: PAID for Campus Pay, UNPAID for Dock Pay (P-026).
                if (method == PaymentMethod.CAMPUS_PAY) {
                    // Deduct from wallet in the same transaction.
                    String deductSql = "UPDATE public.wallets SET balance_poisha = balance_poisha - ?, updated_at = NOW() WHERE user_id = ? AND balance_poisha >= ? RETURNING balance_poisha";
                    try (PreparedStatement dStmt = conn.prepareStatement(deductSql)) {
                        dStmt.setInt(1, totalPoisha);
                        dStmt.setString(2, renter.id());
                        dStmt.setInt(3, totalPoisha);
                        try (ResultSet rs = dStmt.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                throw new IllegalStateException("Insufficient Campus Pay balance. Please top up before riding.");
                            }
                        }
                    }
                    insertPaymentPaid(conn, UUID.randomUUID(), rentalId, totalPoisha);
                } else {
                    insertPaymentUnpaid(conn, UUID.randomUUID(), rentalId, totalPoisha);
                }

                // 7. Mark cycle as RENTED — guarded so a concurrent transition fails loudly (P-162).
                String updateCycle = "UPDATE public.cycles SET availability_status = 'RENTED', updated_at = now() WHERE id = ? AND availability_status = 'AVAILABLE'";
                try (PreparedStatement upd = conn.prepareStatement(updateCycle)) {
                    upd.setObject(1, UUID.fromString(cycleId));
                    int moved = upd.executeUpdate();
                    if (moved != 1) {
                        throw new AppError("CYCLE_UNAVAILABLE", "Cycle was just booked by another student. Please select an adjacent bike.");
                    }
                }

                // 8. Record audit event.
                insertAuditEvent(conn, renter.id(), "RENTAL", rentalId.toString(), "STARTED", "{}");

                // 9. Read back the stored row: fare trigger is authoritative.
                RentalRecord stored;
                try (PreparedStatement sel = conn.prepareStatement(
                        """
                        SELECT r.id, r.cycle_id, c.label, r.renter_id, r.requested_minutes,
                               r.quoted_amount_poisha, r.final_amount_poisha, r.overdue_fine_poisha,
                               r.dropoff_hub, r.state, r.started_at, r.due_at, r.returned_at
                        FROM public.rentals r JOIN public.cycles c ON r.cycle_id = c.id
                        WHERE r.id = ?""")) {
                    sel.setObject(1, rentalId);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            throw new AppError("BOOK_FAILED", "Booking failed. Please retry.");
                        }
                        stored = mapRentalRecord(rs);
                    }
                }

                conn.commit();
                return stored;

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    LOGGER.log(Level.WARNING, "Booking rollback failed", rb);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ac) {
                    LOGGER.log(Level.WARNING, "Failed to restore autocommit after booking", ac);
                }
            }
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Live booking failed", e);
            throw new AppError("BOOK_FAILED", "Booking failed. Please retry.", e);
        }
    }

    @Override
    public void returnRental(CampusUser renter, String rentalId) {
        returnRental(renter, rentalId, null, 0, null, null);
    }

    @Override
    public void returnRental(CampusUser renter, String rentalId, int finalAmountPoisha) {
        returnRental(renter, rentalId, null, finalAmountPoisha, null, null);
    }

    /**
     * Legacy 5-arg convenience overload (kept for existing callers): the cycle is
     * resolved from the rental row. Delegates to the 6-arg core.
     */
    public void returnRental(CampusUser renter, String rentalId, int finalAmountPoisha, String returnLocation, AvailabilityStatus returnStatus) {
        returnRental(renter, rentalId, null, finalAmountPoisha, returnLocation, returnStatus);
    }

    /**
     * Core return path (P-016, P-020). The cycle is resolved from the rental row; a
     * caller-supplied cycleId is VERIFIED against it, never trusted. Client fare
     * values are sent through but the fare trigger recomputes authoritatively.
     */
    @Override
    public void returnRental(CampusUser renter, String rentalId, String cycleId, int finalAmountPoisha,
                             String returnLocation, AvailabilityStatus returnStatus) {
        settleReturn(renter, rentalId, cycleId, finalAmountPoisha, returnLocation, returnStatus, null, ReturnCharge.NONE);
    }

    @Override
    public SettlementOutcome settleReturn(CampusUser renter, String rentalId, String cycleId, int finalAmountPoisha,
                                          String returnLocation, AvailabilityStatus returnStatus,
                                          PaymentMethod method, ReturnCharge charge) {
        if (renter == null) {
            throw new SecurityException("Sign-in is required to return a rental.");
        }
        if (!isUuid(rentalId)) {
            throw new AppError("INVALID_ID", "Invalid rental ID format.");
        }
        if (cycleId != null && !cycleId.isBlank() && !isUuid(cycleId)) {
            throw new AppError("INVALID_ID", "Invalid cycle ID format.");
        }
        ReturnCharge due = charge != null ? charge : ReturnCharge.NONE;

        AvailabilityStatus targetStatus = returnStatus != null ? returnStatus : AvailabilityStatus.AVAILABLE;
        if (targetStatus == AvailabilityStatus.RENTED) {
            throw new IllegalArgumentException("A return cannot leave the cycle RENTED.");
        }
        if (targetStatus == AvailabilityStatus.RETIRED) {
            renter.requireRole(Role.ADMIN);
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find rental and associated cycle (P-020: ownership verified, never discarded).
                UUID rowCycleId;
                int quotedAmount;
                String findSql = "SELECT cycle_id, renter_id, state, quoted_amount_poisha FROM public.rentals WHERE id = ? FOR UPDATE";
                try (PreparedStatement fStmt = conn.prepareStatement(findSql)) {
                    fStmt.setObject(1, UUID.fromString(rentalId));
                    try (ResultSet rs = fStmt.executeQuery()) {
                        if (!rs.next() || !"ACTIVE".equalsIgnoreCase(rs.getString("state"))) {
                            throw new IllegalStateException("Rental record is not active or already returned.");
                        }
                        if (!renter.id().equalsIgnoreCase(rs.getString("renter_id"))) {
                            throw new SecurityException("Only the renter can return this rental.");
                        }
                        rowCycleId = (UUID) rs.getObject("cycle_id");
                        quotedAmount = rs.getInt("quoted_amount_poisha");
                    }
                }
                if (rowCycleId == null) {
                    throw new AppError("RETURN_FAILED", "Return failed. Please retry.");
                }
                if (cycleId != null && !cycleId.isBlank()
                        && !cycleId.equalsIgnoreCase(rowCycleId.toString())) {
                    throw new IllegalArgumentException("Rental does not belong to the supplied cycle.");
                }

                int effectiveFinal = finalAmountPoisha > 0 ? finalAmountPoisha : quotedAmount;
                boolean cashRide = method == PaymentMethod.DOCK_PAY;

                // 1. SETTLE the rental. The fare trigger recomputes the final amount
                //    from the real elapsed time (P-016); the client value is advisory
                //    and is deliberately ignored when the money moves. The payment
                //    columns are added by migration 004; before that the statement must
                //    not reference them, because a failed statement aborts the return.
                boolean paymentCols = paymentMethodColumnAvailable();
                StringBuilder updRental = new StringBuilder(
                        "UPDATE public.rentals SET state = 'RETURNED', returned_at = now(), final_amount_poisha = ?");
                if (paymentCols) {
                    // coalesce: a legacy caller passes no method, so keep whatever the
                    // ride was actually started with instead of blanking it.
                    updRental.append(", payment_method = coalesce(?, payment_method)");
                }
                updRental.append(", updated_at = now() WHERE id = ?");
                try (PreparedStatement rStmt = conn.prepareStatement(updRental.toString())) {
                    int idx = 1;
                    rStmt.setInt(idx++, effectiveFinal);
                    if (paymentCols) {
                        if (method != null) {
                            rStmt.setString(idx++, method.name());
                        } else {
                            rStmt.setNull(idx++, java.sql.Types.VARCHAR);
                        }
                    }
                    rStmt.setObject(idx, UUID.fromString(rentalId));
                    int n = rStmt.executeUpdate();
                    if (n != 1) {
                        throw new AppError("RETURN_FAILED", "Return failed. Please retry.");
                    }
                }

                // 2. Read the authoritative amounts back, then COLLECT against them.
                //    Overtime and the fine are derived from the stored times, never
                //    from the caller, so no client can drain a wallet by declaring
                //    charges it does not owe. Campus Pay takes overtime then the fine
                //    from the wallet, each capped by the balance actually held; a dock
                //    ride is paid by the cash the hub confirmed. Everything runs in
                //    this one transaction, so a failure anywhere below rolls the whole
                //    return back and charges nothing (P-018).
                int settledFare = readSettledFare(conn, rentalId);
                int billedFine = fineFor(conn, rentalId, null);
                Collection taken = collectOnReturn(conn, renter, rentalId, quotedAmount,
                        cashRide, billedFine, due.cashPoisha());

                if (paymentCols && taken.cashPoisha() > 0) {
                    try (PreparedStatement cashStmt = conn.prepareStatement(
                            "UPDATE public.rentals SET cash_collected_poisha = ? WHERE id = ?")) {
                        cashStmt.setInt(1, taken.cashPoisha());
                        cashStmt.setObject(2, UUID.fromString(rentalId));
                        cashStmt.executeUpdate();
                    }
                }

                // Atomically update cycle state, location, and availability in the exact same transaction.
                StringBuilder updCycle = new StringBuilder("UPDATE public.cycles SET updated_at = now()");
                if (returnLocation != null && !returnLocation.isBlank()) {
                    updCycle.append(", pickup_point = ?");
                }
                updCycle.append(", availability_status = ?");
                updCycle.append(" WHERE id = ?");

                try (PreparedStatement cStmt = conn.prepareStatement(updCycle.toString())) {
                    int paramIdx = 1;
                    if (returnLocation != null && !returnLocation.isBlank()) {
                        cStmt.setString(paramIdx++, returnLocation.trim());
                    }
                    cStmt.setString(paramIdx++, targetStatus.name());
                    cStmt.setObject(paramIdx, rowCycleId);
                    int n = cStmt.executeUpdate();
                    if (n != 1) {
                        throw new AppError("RETURN_FAILED", "Return failed. Please retry.");
                    }
                }

                // P-026: settle the UNPAID payment row created at booking. Absent
                // table/row is live variance → skip silently-with-log, never fail the return.
                markPaymentPaid(conn, UUID.fromString(rentalId), settledFare, cashRide);

                // 3. PAY THE OWNER out of what was collected: min(settled payout,
                //    collected). The platform never pays out money it has not taken.
                int cap = taken.totalPoisha();
                int credited = creditOwnerPayout(conn, UUID.fromString(rentalId), cap);

                // 4. Book whatever is still owed as a due, in this same transaction:
                //    settling and recording the debt together is what makes it
                //    impossible for uncollected money to be forgotten. The fine is
                //    recomputed here from the stored times, so a tampered or lagging
                //    client can neither invent a fine nor dodge one.
                String dueId = null;
                int owed = Math.max(0, settledFare) + billedFine;
                int shortfall = Math.max(0, owed - cap);
                if (shortfall > 0) {
                    dueId = recordShortfallDue(conn, renter, rentalId, shortfall, billedFine, cashRide);
                }

                // Insert audit event.
                insertAuditEvent(conn, renter.id(), "RENTAL", rentalId, "RETURNED", "{}");

                SettlementOutcome outcome = readSettlementOutcome(
                        conn, UUID.fromString(rentalId), quotedAmount, billedFine, taken, credited,
                        cashRide, dueId);
                conn.commit();
                return outcome;

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    LOGGER.log(Level.WARNING, "Return rollback failed", rb);
                }
                throw e;
            } finally {
                // P-162: do not rely on pool-release coupling to restore autocommit.
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ac) {
                    LOGGER.log(Level.WARNING, "Failed to restore autocommit after return", ac);
                }
            }
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            // P-059: domain failures keep their actionable message.
            throw e;
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Live atomic return failed", e);
            throw new AppError("RETURN_FAILED", "Return failed. Please retry.", e);
        }
    }

    /**
     * Recomputes the overdue fine from the stored rental times using the same rules
     * as {@link bd.ac.kuet.campuscycle.domain.TariffService}: nothing is billed for
     * the grace window, then a flat amount per completed block, uncapped.
     *
     * <p>The caller's own fine figure is never trusted. Called before the return is
     * written it uses the database clock, which is what {@code returned_at} will be
     * set to, so the amount charged and the amount owed cannot disagree.
     */
    private int fineFor(Connection conn, String rentalId, Timestamp returnedAt) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement(
                returnedAt == null
                        ? "SELECT due_at, now() AS at FROM public.rentals WHERE id = ?"
                        : "SELECT due_at, returned_at AS at FROM public.rentals WHERE id = ?")) {
            sel.setObject(1, UUID.fromString(rentalId));
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                Timestamp due = rs.getTimestamp("due_at");
                Timestamp at = rs.getTimestamp("at");
                if (due == null || at == null) {
                    return 0;
                }
                long overdueSeconds = (at.getTime() - due.getTime()) / 1000L;
                if (overdueSeconds <= TariffService.OVERDUE_GRACE_SECONDS) {
                    return 0;
                }
                long over = overdueSeconds - TariffService.OVERDUE_GRACE_SECONDS;
                long blocks = (over + TariffService.OVERDUE_FINE_BLOCK_SECONDS - 1)
                        / TariffService.OVERDUE_FINE_BLOCK_SECONDS;
                long fine = blocks * TariffService.OVERDUE_BLOCK_POISHA;
                return (int) Math.min(Integer.MAX_VALUE, fine);
            }
        }
    }

    /** The fine owed by an already-settled rental, from its own stored times. */
    private int authoritativeFine(Connection conn, String rentalId) throws SQLException {
        return fineFor(conn, rentalId, null);
    }

    /**
     * Writes the uncollected remainder of a settled ride as a rental due, on the
     * caller's open transaction. Keyed on {@code rental_id} so a retried settlement
     * can never double-book the same debt.
     *
     * @return the due id, or null when the dues table is not migrated yet
     */
    private String recordShortfallDue(Connection conn, CampusUser renter, String rentalId, int shortfallPoisha,
                                      int billedFinePoisha, boolean cashRide) throws SQLException {
        if (!duesTableAvailable()) {
            LOGGER.severe("Uncollected amount of " + shortfallPoisha
                    + " poisha for rental " + rentalId
                    + " could NOT be recorded: public.rental_dues is missing (migration 202609280003).");
            return null;
        }
        String dueId = "DUE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        StringBuilder reason = new StringBuilder("Uncollected on ride ").append(rentalId);
        if (billedFinePoisha > 0) {
            reason.append(" (includes ").append(billedFinePoisha).append(" poisha overdue fine)");
        }
        reason.append(cashRide ? " — cash at hub" : " — Campus Pay");

        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO public.rental_dues (id, rental_id, user_id, amount_poisha, paid_poisha, state, reason) "
                        + "VALUES (?, ?::uuid, ?, ?, 0, 'UNPAID', ?) "
                        + "ON CONFLICT (rental_id) DO UPDATE SET "
                        + "amount_poisha = public.rental_dues.amount_poisha + EXCLUDED.amount_poisha, "
                        + "state = 'UNPAID', reason = EXCLUDED.reason, updated_at = NOW() "
                        + "WHERE public.rental_dues.state IN ('UNPAID', 'PARTIAL') "
                        + "RETURNING id")) {
            ins.setString(1, dueId);
            ins.setObject(2, UUID.fromString(rentalId));
            ins.setString(3, renter.id());
            ins.setInt(4, shortfallPoisha);
            ins.setString(5, reason.toString());
            try (ResultSet rs = ins.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** What the return actually took, per component. */
    private record Collection(int overtimePoisha, int finePoisha, int cashPoisha, int quotedPoisha) {
        int totalPoisha() {
            return quotedPoisha + overtimePoisha + finePoisha + cashPoisha;
        }
    }

    /** The fare the trigger settled, read back on the caller's transaction. */
    private int readSettledFare(Connection conn, String rentalId) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT COALESCE(final_amount_poisha, 0) FROM public.rentals WHERE id = ?")) {
            sel.setObject(1, UUID.fromString(rentalId));
            try (ResultSet rs = sel.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt(1)) : 0;
            }
        }
    }

    /**
     * Takes the ride's money inside the caller's return transaction.
     *
     * <p>Campus Pay: the wallet is locked and drained in the documented order —
     * overtime first, overdue fine last — each capped by the balance actually held,
     * so a short wallet collects a short payment instead of failing the return.
     * Dock Pay: no wallet is touched; the hub's confirmed cash is the collection.
     *
     * <p>Each debit writes a uniquely-referenced ledger row first, so a replayed
     * return can never take the same money twice. The fine charged is the one the
     * database derives from the stored due time and its own clock — the caller's
     * figure is advisory, so a client can neither inflate nor dodge a penalty.
     */
    private Collection collectOnReturn(Connection conn, CampusUser renter, String rentalId, int quotedPoisha,
                                       boolean cashRide, int billedFine, int cashConfirmed) throws SQLException {
        if (cashRide) {
            // A dock ride was never charged at booking, so the whole settled fare is
            // collected here in cash and the quoted fare is not held. The hub's
            // confirmed amount is taken as the truth: it can only ever cap the owner
            // payout (never inflate it, since the payout is the settled split), and
            // any gap against the authoritative fare is booked as a due.
            return new Collection(0, 0, Math.max(0, cashConfirmed), 0);
        }
        int overtime = Math.max(0, readSettledFare(conn, rentalId) - quotedPoisha);
        if (overtime <= 0 && billedFine <= 0) {
            return new Collection(0, 0, 0, quotedPoisha);
        }
        int balance = 0;
        try (PreparedStatement lock = conn.prepareStatement(
                "SELECT balance_poisha FROM public.wallets WHERE user_id = ? FOR UPDATE")) {
            lock.setString(1, renter.id());
            try (ResultSet rs = lock.executeQuery()) {
                balance = rs.next() ? rs.getInt("balance_poisha") : 0;
            }
        }
        int running = balance;
        int takenOvertime = takeFromWallet(conn, renter.id(), running, overtime,
                "OVERTIME-" + rentalId, "RENTAL_CHARGE",
                "Transit overtime fare for ride " + rentalId);
        running -= takenOvertime;
        int takenFine = takeFromWallet(conn, renter.id(), running, billedFine,
                "FINE-" + rentalId, "OVERDUE_FINE",
                "Overdue return fine for ride " + rentalId);
        int totalTaken = takenOvertime + takenFine;
        if (totalTaken > 0) {
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE public.wallets SET balance_poisha = balance_poisha - ?, updated_at = NOW() "
                            + "WHERE user_id = ?")) {
                upd.setInt(1, totalTaken);
                upd.setString(2, renter.id());
                upd.executeUpdate();
            }
        }
        return new Collection(takenOvertime, takenFine, 0, quotedPoisha);
    }

    /**
     * Takes up to {@code want} from the wallet, never more than {@code running}
     * balance, and records the ledger row. Returns the amount actually taken (0 when
     * the reference already exists, i.e. a replay).
     */
    private int takeFromWallet(Connection conn, String userId, int running, int want,
                               String reference, String type, String description) throws SQLException {
        if (want <= 0 || running <= 0) {
            return 0;
        }
        int take = Math.min(want, running);
        int inserted;
        try (PreparedStatement tx = conn.prepareStatement(
                "INSERT INTO public.wallet_transactions "
                        + "(id, user_id, amount_poisha, transaction_type, balance_after_poisha, "
                        + "timestamp, description, reference_code) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(), ?, ?) "
                        + "ON CONFLICT (user_id, reference_code) DO NOTHING")) {
            tx.setString(1, "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            tx.setString(2, userId);
            tx.setInt(3, -take);
            tx.setString(4, type);
            tx.setInt(5, running - take);
            tx.setString(6, description);
            tx.setString(7, reference);
            inserted = tx.executeUpdate();
        }
        return inserted == 1 ? take : 0;
    }

    /**
     * Reads the post-trigger settlement back so the caller can compute the shortfall
     * as debt from the authoritative fare, never from its own estimate.
     */
    private SettlementOutcome readSettlementOutcome(Connection conn, UUID rentalId, int quotedAmount,
                                                     int billedFine, Collection taken, int credited,
                                                     boolean cashRide, String dueId) {
        int finalAmount = 0;
        int fee = 0;
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT COALESCE(final_amount_poisha, 0) AS final_amount, "
                        + "COALESCE(platform_fee_poisha, 0) AS fee FROM public.rentals WHERE id = ?")) {
            sel.setObject(1, rentalId);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    finalAmount = Math.max(0, rs.getInt("final_amount"));
                    fee = Math.max(0, rs.getInt("fee"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Settlement read-back failed for rental " + rentalId, e);
        }
        return new SettlementOutcome(
                finalAmount,
                billedFine,
                Math.max(0, quotedAmount),
                taken.overtimePoisha(),
                taken.finePoisha(),
                taken.cashPoisha(),
                taken.totalPoisha(),
                credited,
                fee,
                dueId,
                cashRide);
    }

    @Override
    public PaymentMethod rentalPaymentMethod(String rentalId) {
        if (rentalId == null || !isUuid(rentalId.trim())) {
            return PaymentMethod.CAMPUS_PAY;
        }
        if (!paymentMethodColumnAvailable()) {
            // Pre-migration: every stored ride was started as Campus Pay.
            return PaymentMethod.CAMPUS_PAY;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT payment_method FROM public.rentals WHERE id = ?")) {
            stmt.setObject(1, UUID.fromString(rentalId.trim()));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString("payment_method");
                    if ("DOCK_PAY".equalsIgnoreCase(raw)) {
                        return PaymentMethod.DOCK_PAY;
                    }
                }
            }
            return PaymentMethod.CAMPUS_PAY;
        } catch (Exception e) {
            if (!isMissingRelation(e)) {
                LOGGER.log(Level.WARNING, "Payment method read failed for " + rentalId, e);
            }
            return PaymentMethod.CAMPUS_PAY;
        }
    }

    /** Cached probe for the migration-004 payment columns. */
    private static volatile Boolean paymentMethodColumn;

    private static boolean paymentMethodColumnAvailable() {
        if (paymentMethodColumn != null) {
            return paymentMethodColumn;
        }
        if (!DatabaseConnection.isAvailable()) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT count(*) AS n FROM information_schema.columns "
                             + "WHERE table_schema = 'public' AND table_name = 'rentals' "
                             + "AND column_name in ('payment_method', 'cash_collected_poisha')")) {
            try (ResultSet rs = stmt.executeQuery()) {
                boolean present = rs.next() && rs.getInt("n") == 2;
                if (present) {
                    paymentMethodColumn = Boolean.TRUE;
                }
                return present;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Payment-method column probe failed; treating as absent", e);
            return false;
        }
    }

    /**
     * P-026 reconciliation: flip the booking-time UNPAID row to PAID. Runs inside the
     * return transaction under a savepoint so a missing table/row (live variance)
     * cannot abort the return itself.
     */
    private void markPaymentPaid(Connection conn, UUID rentalId, int settledAmountPoisha, boolean cashRide) {
        Savepoint sp = null;
        try {
            sp = conn.setSavepoint("return_payment_settle");
            // Cash-at-hub rides settle for the cash the hub actually took, so the
            // payment record matches the money in hand rather than the quoted fare.
            String sql = cashRide
                    ? "UPDATE public.payment_records SET state = 'PAID', amount_poisha = ?, updated_at = NOW() WHERE rental_id = ?"
                    : "UPDATE public.payment_records SET state = 'PAID', updated_at = NOW() WHERE rental_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (cashRide) {
                    stmt.setInt(1, Math.max(0, settledAmountPoisha));
                    stmt.setObject(2, rentalId);
                } else {
                    stmt.setObject(1, rentalId);
                }
                stmt.executeUpdate();
            }
            conn.releaseSavepoint(sp);
        } catch (Exception e) {
            try {
                if (sp != null) {
                    conn.rollback(sp);
                }
            } catch (Exception rb) {
                LOGGER.log(Level.WARNING, "Payment-settle savepoint rollback failed", rb);
            }
            LOGGER.log(Level.WARNING,
                    "payment_records PAID settlement skipped for rental " + rentalId + ": " + e.getMessage());
        }
    }

    /**
     * Credits the bike owner's wallet with the trigger-settled payout and records
     * the {@code OWNER_PAYOUT} ledger row, inside the caller's return transaction.
     * Replay-safe: the {@code PAYOUT-<rentalId>} reference is checked first (the
     * rental {@code FOR UPDATE} lock serializes concurrent returns of one rental),
     * and the ledger insert carries {@code ON CONFLICT DO NOTHING} as backstop.
     * Pre-fee schema (columns absent) or NULL/zero payouts credit nothing and
     * never fail the return.
     */
    private int creditOwnerPayout(Connection conn, UUID rentalId, int cap) throws SQLException {
        // Pre-fee schema: issue NO statements at all. A failed statement would
        // abort this Postgres transaction and take down the audit write with it.
        Boolean present = FEE_COLUMNS_PRESENT;
        if (present == null) {
            rentalFeeColumns();
            present = FEE_COLUMNS_PRESENT;
        }
        if (!Boolean.TRUE.equals(present)) {
            return 0;
        }
        Savepoint sp = conn.setSavepoint("owner_payout");
        try {
            int credited = doCreditOwnerPayout(conn, rentalId, cap);
            conn.releaseSavepoint(sp);
            return credited;
        } catch (SQLException e) {
            rollbackTo(conn, sp);
            throw e;
        }
    }

    /**
     * @return the poisha actually credited to the owner (0 when nothing was owed,
     *         the rental predates the fee columns, or the payout already ran)
     */
    private int doCreditOwnerPayout(Connection conn, UUID rentalId, int cap) throws SQLException {
        int payout = 0;
        String ownerId = null;
        String cycleLabel = "";
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT r.owner_payout_poisha, c.owner_id, c.label "
                        + "FROM public.rentals r JOIN public.cycles c ON c.id = r.cycle_id "
                        + "WHERE r.id = ?")) {
            sel.setObject(1, rentalId);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                int p = rs.getInt("owner_payout_poisha");
                if (!rs.wasNull() && p > 0) {
                    payout = p;
                }
                Object o = rs.getObject("owner_id");
                ownerId = o == null ? null : o.toString();
                String l = rs.getString("label");
                if (l != null) {
                    cycleLabel = l;
                }
            }
        }
        // Escrow cap: the platform never pays out money it has not received. What is
        // withheld stays with the platform as recorded debt, not as lost money.
        boolean capped = false;
        if (payout > cap) {
            payout = cap;
            capped = true;
        }
        if (payout <= 0 || ownerId == null || ownerId.isBlank()) {
            return 0;
        }

        String ref = "PAYOUT-" + rentalId;
        try (PreparedStatement chk = conn.prepareStatement(
                "SELECT 1 FROM public.wallet_transactions WHERE user_id = ? AND reference_code = ?")) {
            chk.setString(1, ownerId);
            chk.setString(2, ref);
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next()) {
                    return 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Owner payout replay-check skipped: " + e.getMessage());
            return 0;
        }

        try (PreparedStatement w = conn.prepareStatement(
                "INSERT INTO public.wallets (user_id, balance_poisha, updated_at) "
                        + "VALUES (?, 0, NOW()) ON CONFLICT (user_id) DO NOTHING")) {
            w.setString(1, ownerId);
            w.executeUpdate();
        }
        int balanceAfter = payout;
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE public.wallets SET balance_poisha = balance_poisha + ?, updated_at = NOW() "
                        + "WHERE user_id = ? RETURNING balance_poisha")) {
            upd.setInt(1, payout);
            upd.setString(2, ownerId);
            try (ResultSet rs = upd.executeQuery()) {
                if (rs.next()) {
                    balanceAfter = rs.getInt(1);
                }
            }
        }
        try (PreparedStatement tx = conn.prepareStatement(
                "INSERT INTO public.wallet_transactions "
                        + "(id, user_id, amount_poisha, transaction_type, balance_after_poisha, "
                        + "timestamp, description, reference_code) "
                        + "VALUES (?, ?, ?, 'OWNER_PAYOUT', ?, NOW(), ?, ?) "
                        + "ON CONFLICT (user_id, reference_code) DO NOTHING")) {
            tx.setString(1, "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            tx.setString(2, ownerId);
            tx.setInt(3, payout);
            tx.setInt(4, balanceAfter);
            String note = capped
                    ? "Owner payout (partially held pending unpaid balance): " + cycleLabel + " (" + rentalId + ")"
                    : "Owner payout: " + cycleLabel + " (" + rentalId + ")";
            tx.setString(5, note);
            tx.setString(6, ref);
            tx.executeUpdate();
        }
        return payout;
    }

    @Override
    public OwnerEarnings ownerEarnings(CampusUser caller, String ownerId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view earnings.");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner id is required.");
        }
        if (!caller.id().equals(ownerId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own bike earnings.");
        }
        List<OwnerEarnings.BikeEarning> bikes = new ArrayList<>();
        String sql = """
                SELECT c.id AS cycle_id, c.label AS cycle_label,
                       COUNT(*) AS rides,
                       COALESCE(SUM(COALESCE(NULLIF(r.final_amount_poisha, 0), r.quoted_amount_poisha, 0)), 0) AS gross,
                       COALESCE(SUM(COALESCE(r.platform_fee_poisha, 0)), 0) AS fees,
                       COALESCE(SUM(COALESCE(r.owner_payout_poisha, 0)), 0) AS payouts
                FROM public.rentals r JOIN public.cycles c ON c.id = r.cycle_id
                WHERE r.state = 'RETURNED' AND c.owner_id = ?::uuid
                GROUP BY c.id, c.label
                ORDER BY payouts DESC;
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, toUuid(ownerId.trim()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bikes.add(new OwnerEarnings.BikeEarning(
                            String.valueOf(rs.getObject("cycle_id")),
                            rs.getString("cycle_label"),
                            rs.getLong("rides"),
                            rs.getInt("gross"),
                            rs.getInt("fees"),
                            rs.getInt("payouts")));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load owner earnings from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load earnings.", e);
        }
        long rides = 0;
        int gross = 0, fees = 0, payouts = 0;
        for (OwnerEarnings.BikeEarning b : bikes) {
            rides += b.settledRides();
            gross += b.grossPoisha();
            fees += b.feesPoisha();
            payouts += b.netPayoutPoisha();
        }
        return new OwnerEarnings(ownerId.trim(), bikes, rides, gross, fees, payouts);
    }

    @Override
    public PlatformEarnings platformEarnings(CampusUser admin) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to view platform earnings.");
        }
        String sql = """
                SELECT COUNT(*) AS rides,
                       COALESCE(SUM(COALESCE(NULLIF(r.final_amount_poisha, 0), r.quoted_amount_poisha, 0)), 0) AS gross,
                       COALESCE(SUM(COALESCE(r.platform_fee_poisha, 0)), 0) AS fees,
                       COALESCE(SUM(COALESCE(r.owner_payout_poisha, 0)), 0) AS payouts
                FROM public.rentals r
                WHERE r.state = 'RETURNED';
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new PlatformEarnings(
                        rs.getLong("rides"),
                        rs.getInt("gross"),
                        rs.getInt("fees"),
                        rs.getInt("payouts"));
            }
            return PlatformEarnings.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load platform earnings from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load earnings.", e);
        }
    }

    @Override
    public RentalDue recordDue(CampusUser actor, String rentalId, int amountPoisha, String reason) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to record dues.");
        }
        if (rentalId == null || rentalId.isBlank() || !isUuid(rentalId.trim())) {
            throw new AppError("INVALID_ID", "Invalid rental ID format.");
        }
        if (amountPoisha <= 0) {
            throw new IllegalArgumentException("Due amount must be positive.");
        }
        String note = reason == null ? "" : reason.trim();
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Rental must belong to the actor: dues are personal debt.
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT renter_id FROM public.rentals WHERE id = ?")) {
                chk.setObject(1, UUID.fromString(rentalId.trim()));
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next() || !actor.id().equalsIgnoreCase(rs.getString("renter_id"))) {
                        throw new SecurityException("Dues can only be recorded for your own rentals.");
                    }
                }
            }
            String dueId = "DUE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO public.rental_dues (id, rental_id, user_id, amount_poisha, paid_poisha, state, reason) "
                            + "VALUES (?, ?::uuid, ?, ?, 0, 'UNPAID', ?) "
                            + "ON CONFLICT (rental_id) DO NOTHING")) {
                ins.setString(1, dueId);
                ins.setObject(2, UUID.fromString(rentalId.trim()));
                ins.setString(3, actor.id());
                ins.setInt(4, amountPoisha);
                ins.setString(5, note);
                ins.executeUpdate();
            }
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT id, rental_id, user_id, amount_poisha, paid_poisha, state, reason, created_at "
                            + "FROM public.rental_dues WHERE rental_id = ?::uuid")) {
                sel.setObject(1, UUID.fromString(rentalId.trim()));
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        return mapRentalDue(rs);
                    }
                }
            }
            throw new AppError("DB_ERROR", "Failed to record due.");
        } catch (AppError | IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to record due for rental " + rentalId, e);
            throw new AppError("DB_ERROR", "Failed to record due.", e);
        }
    }

    @Override
    public int unpaidDuesTotal(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COALESCE(SUM(amount_poisha - paid_poisha), 0) AS owed "
                             + "FROM public.rental_dues WHERE user_id = ? AND state IN ('UNPAID', 'PARTIAL')")) {
            stmt.setString(1, userId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt("owed")) : 0;
            }
        } catch (Exception e) {
            if (isMissingRelation(e)) {
                // Pre-dues migration: no table means no debt. Never block bookings.
                return 0;
            }
            LOGGER.log(Level.SEVERE, "Failed to total dues for " + userId, e);
            throw new AppError("DB_ERROR", "Failed to load dues.", e);
        }
    }

    /** True when the failure is an absent table (pre-migration), not real corruption. */
    private static boolean isMissingRelation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException se && "42P01".equals(se.getSQLState())) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && msg.contains("does not exist") && msg.contains("rental_dues")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cached probe for the dues table. A failed statement inside a transaction
     * poisons it, so the check must happen on its own autocommit connection before
     * the booking transaction starts. Only a positive result is cached, so the
     * arrears gate switches itself on as soon as the migration is applied.
     */
    private static volatile Boolean duesTablePresent;

    private static boolean duesTableAvailable() {
        if (Boolean.TRUE.equals(duesTablePresent)) {
            return true;
        }
        if (!DatabaseConnection.isAvailable()) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT to_regclass('public.rental_dues') IS NOT NULL AS present")) {
            try (ResultSet rs = stmt.executeQuery()) {
                boolean present = rs.next() && rs.getBoolean("present");
                if (present) {
                    duesTablePresent = Boolean.TRUE;
                }
                return present;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Dues table probe failed; treating as absent", e);
            return false;
        }
    }

    /** Outstanding dues for a user read on the caller's transaction. */
    private static int duesOwedOn(Connection conn, String userId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COALESCE(SUM(amount_poisha - paid_poisha), 0) AS owed FROM public.rental_dues "
                        + "WHERE user_id = ? AND state IN ('UNPAID', 'PARTIAL')")) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt("owed")) : 0;
            }
        }
    }

    /** Open dues row count for a user read on the caller's transaction. */
    private static int duesOpenCountOn(Connection conn, String userId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) AS n FROM public.rental_dues "
                        + "WHERE user_id = ? AND state IN ('UNPAID', 'PARTIAL')")) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt("n")) : 0;
            }
        }
    }

    @Override
    public int unpaidDuesCount(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) AS n FROM public.rental_dues "
                             + "WHERE user_id = ? AND state IN ('UNPAID', 'PARTIAL')")) {
            stmt.setString(1, userId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt("n")) : 0;
            }
        } catch (Exception e) {
            if (isMissingRelation(e)) {
                return 0;
            }
            LOGGER.log(Level.SEVERE, "Failed to count dues for " + userId, e);
            throw new AppError("DB_ERROR", "Failed to load dues.", e);
        }
    }

    @Override
    public List<RentalDue> userDues(CampusUser caller, String userId) {
        if (caller == null) {
            throw new SecurityException("Sign-in is required to view dues.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (!caller.id().equals(userId) && caller.role() != Role.ADMIN) {
            throw new SecurityException("You can only view your own dues.");
        }
        List<RentalDue> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, rental_id, user_id, amount_poisha, paid_poisha, state, reason, created_at "
                             + "FROM public.rental_dues WHERE user_id = ? ORDER BY created_at DESC")) {
            stmt.setString(1, userId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRentalDue(rs));
                }
            }
            return list;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load dues for " + userId, e);
            throw new AppError("DB_ERROR", "Failed to load dues.", e);
        }
    }

    private RentalDue mapRentalDue(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return new RentalDue(
                rs.getString("id"),
                String.valueOf(rs.getObject("rental_id")),
                rs.getString("user_id"),
                rs.getInt("amount_poisha"),
                rs.getInt("paid_poisha"),
                DueState.fromString(rs.getString("state")),
                rs.getString("reason") != null ? rs.getString("reason") : "",
                ts != null ? ts.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA));
    }

    @Override
    public int settleDues(CampusUser user, int maxPoisha) {
        if (user == null) {
            throw new SecurityException("Sign-in is required to settle dues.");
        }
        if (maxPoisha <= 0) {
            return 0;
        }
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required to settle dues.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int applied = settleDuesInTx(conn, user, maxPoisha);
                conn.commit();
                return applied;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    LOGGER.log(Level.WARNING, "Dues settle rollback failed", rb);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ac) {
                    LOGGER.log(Level.WARNING, "Failed to restore autocommit after dues settle", ac);
                }
            }
        } catch (AppError | IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to settle dues for " + user.id(), e);
            throw new AppError("DB_ERROR", "Failed to settle dues.", e);
        }
    }

    /**
     * Clears dues on the caller's own open transaction so a booking and its arrears
     * settlement commit or roll back together: a rider can never pay off dues and
     * then lose the ride. {@code conn} must already be in manual-commit mode.
     */
    private int settleDuesInTx(Connection conn, CampusUser user, int maxPoisha) throws Exception {
        int balance;
        try (PreparedStatement lock = conn.prepareStatement(
                "SELECT balance_poisha FROM public.wallets WHERE user_id = ? FOR UPDATE")) {
            lock.setString(1, user.id());
            try (ResultSet rs = lock.executeQuery()) {
                balance = rs.next() ? rs.getInt("balance_poisha") : 0;
            }
        }
        List<String> dueIds = new ArrayList<>();
        List<Integer> dueOwed = new ArrayList<>();
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT id, (amount_poisha - paid_poisha) AS owed FROM public.rental_dues "
                        + "WHERE user_id = ? AND state IN ('UNPAID', 'PARTIAL') "
                        + "ORDER BY created_at FOR UPDATE")) {
            sel.setString(1, user.id());
            try (ResultSet rs = sel.executeQuery()) {
                while (rs.next()) {
                    int owed = Math.max(0, rs.getInt("owed"));
                    if (owed > 0) {
                        dueIds.add(rs.getString("id"));
                        dueOwed.add(owed);
                    }
                }
            }
        }
        int budget = Math.min(Math.max(0, balance), maxPoisha);
        int applied = 0;
        int running = balance;
        for (int i = 0; i < dueIds.size() && budget > 0; i++) {
            int take = Math.min(dueOwed.get(i), budget);
            if (take <= 0) continue;
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE public.rental_dues SET paid_poisha = paid_poisha + ?, "
                            + "state = CASE WHEN paid_poisha + ? >= amount_poisha THEN 'PAID' ELSE 'PARTIAL' END "
                            + "WHERE id = ?")) {
                upd.setInt(1, take);
                upd.setInt(2, take);
                upd.setString(3, dueIds.get(i));
                upd.executeUpdate();
            }
            running -= take;
            try (PreparedStatement tx = conn.prepareStatement(
                    "INSERT INTO public.wallet_transactions "
                            + "(id, user_id, amount_poisha, transaction_type, balance_after_poisha, "
                            + "timestamp, description, reference_code) "
                            + "VALUES (?, ?, ?, 'DUES_SETTLEMENT', ?, NOW(), ?, ?) "
                            + "ON CONFLICT (user_id, reference_code) DO NOTHING")) {
                tx.setString(1, "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                tx.setString(2, user.id());
                tx.setInt(3, -take);
                tx.setInt(4, running);
                tx.setString(5, "Dues settlement (" + dueIds.get(i) + ")");
                tx.setString(6, "DUES-" + dueIds.get(i));
                tx.executeUpdate();
            }
            applied += take;
            budget -= take;
        }
        if (applied > 0) {
            try (PreparedStatement dec = conn.prepareStatement(
                    "UPDATE public.wallets SET balance_poisha = balance_poisha - ?, updated_at = NOW() "
                            + "WHERE user_id = ?")) {
                dec.setInt(1, applied);
                dec.setString(2, user.id());
                dec.executeUpdate();
            }
        }
        return applied;
    }

    @Override
    public List<RentalDue> allDues(CampusUser admin) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to view all dues.");
        }
        List<RentalDue> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, rental_id, user_id, amount_poisha, paid_poisha, state, reason, created_at "
                             + "FROM public.rental_dues ORDER BY created_at DESC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRentalDue(rs));
                }
            }
            return list;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load all dues from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load dues.", e);
        }
    }

    @Override
    public void waiveDue(CampusUser admin, String dueId, String reason) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to waive dues.");
        }
        if (dueId == null || dueId.isBlank()) {
            throw new IllegalArgumentException("Due id is required.");
        }
        String note = reason == null ? "" : reason.trim();
        if (note.length() < 3) {
            throw new IllegalArgumentException("A waive reason (min 3 characters) is required.");
        }
        String rentalUuid;
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Waiving money and recording why must not come apart: a waiver with no
            // audit trail is worse than a failed one.
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE public.rental_dues SET state = 'WAIVED', reason = ? "
                                + "WHERE id = ? AND state IN ('UNPAID', 'PARTIAL') "
                                + "RETURNING rental_id")) {
                    stmt.setString(1, note);
                    stmt.setString(2, dueId.trim());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new AppError("NOT_FOUND", "Due not found or already settled.");
                        }
                        Object r = rs.getObject("rental_id");
                        rentalUuid = r == null ? dueId.trim() : r.toString();
                    }
                }
                insertAuditEvent(conn, admin.id(), "RENTAL", rentalUuid, "DUE_WAIVED",
                        "{\"reason\":\"" + jsonEscape(note) + "\"}");
                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rb) {
                    LOGGER.log(Level.WARNING, "Waive rollback failed", rb);
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ac) {
                    LOGGER.log(Level.WARNING, "Failed to restore autocommit after waive", ac);
                }
            }
        } catch (AppError | IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to waive due " + dueId, e);
            throw new AppError("DB_ERROR", "Failed to waive due.", e);
        }
    }

    @Override
    public void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason) {
        if (admin == null) {
            throw new SecurityException("Sign-in is required to review a cycle.");
        }
        admin.requireRole(Role.ADMIN);
        if (!isUuid(cycleId)) {
            throw new AppError("INVALID_ID", "Invalid cycle ID format.");
        }
        if (!approved && (reason == null || reason.trim().length() < 10)) {
            throw new IllegalArgumentException("A written reason (min 10 chars) is required to reject.");
        }

        // Guarded to PENDING_REVIEW: an already-reviewed cycle cannot be reviewed again (P-039).
        String sql = "UPDATE public.cycles SET review_status = ?, updated_at = now() "
                + "WHERE id = ?::uuid AND review_status = 'PENDING_REVIEW'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, approved ? "APPROVED" : "REJECTED");
            stmt.setObject(2, UUID.fromString(cycleId));
            int updated = stmt.executeUpdate();
            if (updated != 1) throw new IllegalStateException("Cycle not found or already reviewed.");

            // Insert audit event
            insertAuditEvent(conn, admin.id(), "CYCLE", cycleId,
                    approved ? "APPROVED" : "REJECTED",
                    "{\"reason\":\"" + jsonEscape(reason == null ? "" : reason.trim()) + "\"}");

        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Live cycle review failed", e);
            throw new AppError("REVIEW_FAILED", "Review failed. Please retry.", e);
        }
    }

    /**
     * Moves up to {@code count} AVAILABLE cycles between hubs (P-060).
     *
     * @return the number of cycles actually moved — never the requested count.
     */
    @Override
    public int rebalanceHub(CampusUser admin, String sourceHub, String targetHub, int count) {
        if (admin == null) {
            throw new SecurityException("Sign-in is required to rebalance hubs.");
        }
        admin.requireRole(Role.ADMIN);
        if (sourceHub == null || sourceHub.isBlank() || targetHub == null || targetHub.isBlank()) {
            throw new IllegalArgumentException("Source and target hubs are required.");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive.");
        }
        String sql = """
                UPDATE public.cycles
                SET pickup_point = ?, updated_at = now()
                WHERE id IN (
                    SELECT id FROM public.cycles
                    WHERE pickup_point = ? AND availability_status = 'AVAILABLE'
                    ORDER BY id
                    LIMIT ?
                );
                """;

        try (Connection conn = DatabaseConnection.getConnection();
              PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, targetHub.trim());
            stmt.setString(2, sourceHub.trim());
            stmt.setInt(3, count);
            return stmt.executeUpdate();

        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Live hub rebalancing failed", e);
            throw new AppError("REBALANCE_FAILED", "Rebalance failed. Please retry.", e);
        }
    }

    private CycleItem mapCycleItem(ResultSet rs) throws SQLException {
        String typeStr = rs.getString("cycle_type");
        CycleType type = CycleType.CITY_BIKE;
        try {
            if (typeStr != null) type = CycleType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        String condStr = rs.getString("physical_condition");
        CycleCondition cond = CycleCondition.GOOD;
        try {
            if (condStr != null) cond = CycleCondition.valueOf(condStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        String revStr = rs.getString("review_status");
        ReviewStatus rev = ReviewStatus.APPROVED;
        try {
            if (revStr != null) rev = ReviewStatus.valueOf(revStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        String availStr = rs.getString("availability_status");
        AvailabilityStatus avail = AvailabilityStatus.AVAILABLE;
        try {
            if (availStr != null) avail = AvailabilityStatus.valueOf(availStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        double lat = rs.getDouble("latitude");
        if (rs.wasNull()) lat = 22.9009;

        double lng = rs.getDouble("longitude");
        if (rs.wasNull()) lng = 89.5016;

        return new CycleItem(
                rs.getString("id"),
                rs.getString("owner_id") != null ? rs.getString("owner_id") : "system",
                rs.getString("owner_name") != null ? rs.getString("owner_name") : "KUET Member",
                rs.getString("label"),
                type,
                cond,
                rs.getString("pickup_point"),
                lat,
                lng,
                rs.getString("description") != null ? rs.getString("description") : "",
                rev,
                avail
        );
    }

    private RentalRecord mapRentalRecord(ResultSet rs) throws SQLException {
        Timestamp startTs = rs.getTimestamp("started_at");
        Timestamp retTs = rs.getTimestamp("returned_at");
        Timestamp dueTs = null;
        try {
            dueTs = rs.getTimestamp("due_at");
        } catch (SQLException ignored) {}

        ZonedDateTime startZoned = startTs != null ? startTs.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA);
        int reqMins = rs.getInt("requested_minutes");
        ZonedDateTime dueZoned = dueTs != null ? dueTs.toInstant().atZone(DHAKA) : startZoned.plusMinutes(reqMins);
        ZonedDateTime retZoned = retTs != null ? retTs.toInstant().atZone(DHAKA) : null;

        String stateStr = rs.getString("state");
        RentalStatus status = RentalStatus.ACTIVE;
        try {
            if (stateStr != null) status = RentalStatus.valueOf(stateStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        int quotedPoisha = rs.getInt("quoted_amount_poisha");
        int finalPoisha = quotedPoisha;
        try {
            int f = rs.getInt("final_amount_poisha");
            if (!rs.wasNull() && f > 0) {
                finalPoisha = f;
            }
        } catch (SQLException ignored) {}

        // New-migration columns; live databases may lack them → default defensively.
        int overduePoisha = 0;
        try {
            int o = rs.getInt("overdue_fine_poisha");
            if (!rs.wasNull() && o > 0) {
                overduePoisha = o;
            }
        } catch (SQLException ignored) {}
        String dropoffHub = "";
        try {
            String d = rs.getString("dropoff_hub");
            if (d != null) {
                dropoffHub = d;
            }
        } catch (SQLException ignored) {}

        // Platform fee columns (nullable: pre-fee rows read as 0).
        int feePoisha = 0;
        try {
            int f = rs.getInt("platform_fee_poisha");
            if (!rs.wasNull() && f > 0) {
                feePoisha = f;
            }
        } catch (SQLException ignored) {}
        int payoutPoisha = 0;
        try {
            int p = rs.getInt("owner_payout_poisha");
            if (!rs.wasNull() && p > 0) {
                payoutPoisha = p;
            }
        } catch (SQLException ignored) {}

        return new RentalRecord(
                rs.getString("id"),
                rs.getString("cycle_id"),
                rs.getString("label") != null ? rs.getString("label") : safeCycleLabel(rs.getString("cycle_id")),
                rs.getString("renter_id"),
                reqMins,
                quotedPoisha,
                finalPoisha,
                overduePoisha,
                dropoffHub,
                status,
                startZoned,
                dueZoned,
                retZoned,
                feePoisha,
                payoutPoisha
        );
    }

    private String safeCycleLabel(String cycleId) {
        if (cycleId == null || cycleId.length() < 4) return "Campus Cycle";
        return "Cycle #" + cycleId.substring(0, 4);
    }

    /**
     * Fee-column fragment for rental SELECTs, probed once per JVM lifetime.
     * Returns real columns when migration 202609280001 is applied, otherwise
     * {@code 0 AS} placeholders so pre-migration databases keep working with
     * zeroed fees instead of failing every rentals query. Probe failures fail
     * open toward the migrated shape so the real error surfaces loudly.
     */
    private static volatile Boolean FEE_COLUMNS_PRESENT = null;

    private static String rentalFeeColumns() {
        Boolean cached = FEE_COLUMNS_PRESENT;
        if (cached == null) {
            synchronized (SupabaseCampusRepository.class) {
                cached = FEE_COLUMNS_PRESENT;
                if (cached == null) {
                    boolean present = true;
                    try (Connection probe = DatabaseConnection.getConnection();
                         PreparedStatement ps = probe.prepareStatement(
                                 "SELECT 1 FROM information_schema.columns "
                                         + "WHERE table_schema = 'public' AND table_name = 'rentals' "
                                         + "AND column_name = 'platform_fee_poisha'")) {
                        try (ResultSet rs = ps.executeQuery()) {
                            present = rs.next();
                        }
                    } catch (Exception probeFailed) {
                        LOGGER.log(Level.FINE, "Fee-column probe failed, assuming migrated: "
                                + probeFailed.getMessage());
                        present = true;
                    }
                    FEE_COLUMNS_PRESENT = present;
                    cached = present;
                }
            }
        }
        return cached
                ? ", r.platform_fee_poisha, r.owner_payout_poisha"
                : ", 0 AS platform_fee_poisha, 0 AS owner_payout_poisha";
    }

    @Override
    public String openDispute(CampusUser renter, String rentalId, String reason) {
        if (renter == null) {
            throw new SecurityException("Sign-in is required to open a dispute.");
        }
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 2000) {
            throw new IllegalArgumentException("Dispute reason must be 10-2000 characters.");
        }
        if (!isUuid(rentalId) || !isUuid(renter.id())) {
            throw new AppError("DISPUTE_FAILED", "Dispute failed. Invalid rental.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String check = "SELECT renter_id, state FROM public.rentals WHERE id = ? FOR UPDATE";
                try (PreparedStatement chk = conn.prepareStatement(check)) {
                    chk.setObject(1, UUID.fromString(rentalId));
                    try (ResultSet rs = chk.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Rental not found.");
                        if (!renter.id().equals(rs.getString("renter_id"))) {
                            throw new SecurityException("Only the renter can dispute this rental.");
                        }
                        if (!"RETURNED".equalsIgnoreCase(rs.getString("state"))) {
                            throw new IllegalStateException("Only RETURNED rentals can be disputed.");
                        }
                    }
                }
                UUID disputeId = UUID.randomUUID();
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO public.disputes (id, rental_id, opened_by, reason) VALUES (?, ?, ?, ?)")) {
                    ins.setObject(1, disputeId);
                    ins.setObject(2, UUID.fromString(rentalId));
                    ins.setObject(3, UUID.fromString(renter.id()));
                    ins.setString(4, reason.trim());
                    ins.executeUpdate();
                }
                // P-130: only the state flips to DISPUTED — quoted/final amounts are
                // preserved untouched, so the settled fare survives the dispute.
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE public.rentals SET state = 'DISPUTED', updated_at = now() WHERE id = ?")) {
                    upd.setObject(1, UUID.fromString(rentalId));
                    upd.executeUpdate();
                }
                insertAuditEvent(conn, renter.id(), "DISPUTE", disputeId.toString(), "OPENED", "{}");
                conn.commit();
                return disputeId.toString();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    LOGGER.log(Level.WARNING, "Dispute rollback failed", rb);
                }
                throw e;
            } finally {
                // P-162: do not rely on pool-release coupling to restore autocommit.
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ac) {
                    LOGGER.log(Level.WARNING, "Failed to restore autocommit after dispute", ac);
                }
            }
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Live dispute failed", e);
            throw new AppError("DISPUTE_FAILED", "Dispute failed. Please retry.", e);
        }
    }

    @Override
    public List<RentalRecord> allRentals() {
        return allRentals(null);
    }

    /**
     * Returns all rentals with admin authorization (P-049, P-050).
     * @param admin the admin user (must have Role.ADMIN)
     * @return list of all rental records
     * @throws SecurityException if admin is null or not ADMIN
     */
    @Override
    public List<RentalRecord> allRentals(CampusUser admin) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to access all rentals.");
        }
        List<RentalRecord> records = new ArrayList<>();
        String sql = "SELECT r.id, r.cycle_id, c.label, r.renter_id, r.requested_minutes, "
                + "r.quoted_amount_poisha, r.final_amount_poisha, r.overdue_fine_poisha"
                + rentalFeeColumns()
                + ", r.dropoff_hub, r.state, r.started_at, r.due_at, r.returned_at "
                + "FROM public.rentals r "
                + "LEFT JOIN public.cycles c ON r.cycle_id = c.id "
                + "ORDER BY r.started_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                records.add(mapRentalRecord(rs));
            }
            return records;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load live allRentals from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load all rentals.", e);
        }
    }

    @Override
    public java.util.Optional<CycleItem> getCycleById(String cycleId) {
        if (cycleId == null || cycleId.isBlank()) return java.util.Optional.empty();

        // P-053: id only — the old `OR label` fallback could resolve the wrong cycle
        // on a label collision and behaved differently from the in-memory repository.
        String sql = """
                SELECT c.id, c.owner_id, COALESCE(p.display_name, 'KUET Member') AS owner_name,
                       c.label, c.cycle_type, c.physical_condition, c.pickup_point,
                       c.latitude, c.longitude, c.description, c.review_status, c.availability_status
                FROM public.cycles c LEFT JOIN public.profiles p ON p.id = c.owner_id
                WHERE c.id::text = ?
                LIMIT 1;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cycleId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.of(mapCycleItem(rs));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get live cycle by id from Supabase: " + cycleId, e);
            throw new AppError("DB_ERROR", "Failed to get cycle by ID.", e);
        }
        return java.util.Optional.empty();
    }

    @Override
    public List<UserRegistration> getAllUserRegistrations() {
        return getAllUserRegistrations(null);
    }

    /**
     * Returns all user registrations with admin authorization (P-049).
     * @param admin the admin user (must have Role.ADMIN)
     * @return list of user registrations (password hashes are never included)
     * @throws SecurityException if admin is null or not ADMIN
     */
    @Override
    public List<UserRegistration> getAllUserRegistrations(CampusUser admin) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to access all user registrations.");
        }
        // P-049: password hashes are never selected for list views. The domain record
        // still requires a hash slot, so it is filled with "" (never a real hash).
        List<UserRegistration> list = new ArrayList<>();
        String sql = """
                SELECT id, full_name, student_roll, department, email, phone, verification_status, created_at
                FROM public.pending_registrations
                ORDER BY created_at DESC;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                ZonedDateTime zdt = ts != null ? ts.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA);
                list.add(new UserRegistration(
                        rs.getString("id"),
                        rs.getString("full_name"),
                        rs.getString("student_roll"),
                        rs.getString("department"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        "",
                        rs.getString("verification_status"),
                        zdt
                ));
            }
            return list;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load pending_registrations from Supabase", e);
            throw new AppError("DB_ERROR", "Failed to load user registrations.", e);
        }
    }

    @Override
    public java.util.Optional<UserRegistration> getUserRegistration(String identifier) {
        if (identifier == null || identifier.isBlank()) return java.util.Optional.empty();

        String trimmed = identifier.trim().toLowerCase();
        String sql = """
                SELECT id, full_name, student_roll, department, email, phone, password_hash, verification_status, created_at
                FROM public.pending_registrations
                WHERE id::text = ? OR LOWER(email) = ? OR LOWER(student_roll) = ?
                ORDER BY created_at DESC
                LIMIT 1;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, trimmed);
            stmt.setString(2, trimmed);
            stmt.setString(3, trimmed);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    ZonedDateTime zdt = ts != null ? ts.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA);
                    return java.util.Optional.of(new UserRegistration(
                            rs.getString("id"),
                            rs.getString("full_name"),
                            rs.getString("student_roll"),
                            rs.getString("department"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("password_hash"),
                            rs.getString("verification_status"),
                            zdt
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to query pending_registration from Supabase for " + identifier, e);
            throw new AppError("DB_ERROR", "Failed to get user registration.", e);
        }
        return java.util.Optional.empty();
    }

    /**
     * Records a new student registration.
     *
     * <p>The registration id MUST be a real Supabase auth user id. {@code profiles.id}
     * carries a foreign key to {@code auth.users(id)}, so the profile insert that
     * approval performs can only succeed when an auth user already exists. Creating
     * the auth identity here — with no profile — is what makes approval possible
     * while keeping the approved profile the access gate.
     *
     * <p>If auth provisioning fails, the submission fails and no registration row is
     * written: an orphan row could never be approved and would just sit in the admin
     * queue failing forever.
     */
    @Override
    public String submitUserRegistration(UserRegistration registration, String plainPassword) {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Registration submission requires database connection.");
        }

        String email = registration.email() == null ? "" : registration.email().trim().toLowerCase();
        if (email.isBlank()) {
            throw new AppError("INVALID_INPUT", "A university email is required to register.");
        }
        if (plainPassword == null || plainPassword.length() < 6) {
            throw new AppError("INVALID_INPUT", "A password of at least 6 characters is required to register.");
        }

        // The plaintext password exists only here, at submission — the one moment a
        // GoTrue identity can be created. The stored hash cannot provision one.
        String authUid = SupabaseAuthService.createAuthIdentity(email, plainPassword, registration.fullName());

        String sql = """
                INSERT INTO public.pending_registrations
                (id, full_name, student_roll, department, email, phone, password_hash, verification_status, created_at)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    student_roll = EXCLUDED.student_roll,
                    department = EXCLUDED.department,
                    email = EXCLUDED.email,
                    phone = EXCLUDED.phone,
                    password_hash = EXCLUDED.password_hash,
                    verification_status = EXCLUDED.verification_status;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, toUuid(authUid));
            stmt.setString(2, registration.fullName());
            stmt.setString(3, registration.studentRoll());
            stmt.setString(4, registration.department());
            stmt.setString(5, email);
            stmt.setString(6, registration.phone());
            stmt.setString(7, registration.passwordHash());
            stmt.setString(8, registration.verificationStatus());
            stmt.executeUpdate();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to submit pending_registration to Supabase", e);
            throw new AppError("DB_ERROR", "Failed to submit registration.", e);
        }
        return authUid;
    }

    @Override
    public void approveUserRegistration(String registrationId) {
        // P-049 NOTE: no actor in contract — admin gating lives with the caller (see addCycle).
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Registration approval requires database connection.");
        }
        if (registrationId == null || registrationId.isBlank() || !isUuid(registrationId.trim())) {
            throw new AppError("INVALID_ID", "Invalid registration ID.");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                UUID profileId = UUID.fromString(registrationId.trim());

                // 1. Fetch the registration first: unknown ids fail closed (no phantom profile).
                String studentName = null;
                String studentEmail = null;
                String fetchSql = "SELECT id, full_name, email FROM public.pending_registrations WHERE id = ?::uuid";
                try (PreparedStatement stmt = conn.prepareStatement(fetchSql)) {
                    stmt.setObject(1, profileId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new AppError("NOT_FOUND", "Registration not found.");
                        }
                        studentName = rs.getString("full_name");
                        studentEmail = rs.getString("email");
                    }
                }
                if (studentName == null || studentName.isBlank()) {
                    studentName = "Verified Student";
                }
                if (studentEmail == null || studentEmail.isBlank()) {
                    studentEmail = profileId + "@kuet.ac.bd";
                }

                // The profile insert below needs a real auth user (profiles.id references
                // auth.users.id). Registrations created before auth provisioning was
                // wired up have a locally generated id that matches nothing, so say so
                // plainly instead of surfacing a bare foreign-key violation.
                if (!authUserExists(conn, profileId)) {
                    throw new AppError("AUTH_USER_MISSING",
                            "This registration has no Supabase auth account, so it cannot be approved. "
                                    + "The applicant must register again (this registration predates auth provisioning).");
                }

                // 2. Preserve the registration's requested role (default STUDENT).
                // The role column exists only on newer databases → probe defensively.
                Role grantedRole = readRequestedRole(conn, profileId);

                // 3. Update verification_status to APPROVED
                String updSql = "UPDATE public.pending_registrations SET verification_status = 'APPROVED' WHERE id = ?::uuid";
                try (PreparedStatement stmt = conn.prepareStatement(updSql)) {
                    stmt.setObject(1, profileId);
                    int n = stmt.executeUpdate();
                    if (n != 1) {
                        throw new AppError("NOT_FOUND", "Registration not found.");
                    }
                }

                // 2b. auth.users provisioning is operator-side (Supabase Admin API / GoTrue signup).
                // Never write to auth.users directly (P-012). The profile insert will fail
                // on its FK if the auth user doesn't exist, which is the correct behavior.

                // 3. Upsert into public.profiles with the requested (validated) role.
                // The value comes from the Role enum, so interpolating it is injection-safe.
                String profSql = "INSERT INTO public.profiles (id, display_name, role) "
                        + "VALUES (?::uuid, ?, '" + grantedRole.name() + "') "
                        + "ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name, role = '" + grantedRole.name() + "'";
                try (PreparedStatement stmt = conn.prepareStatement(profSql)) {
                    stmt.setObject(1, profileId);
                    stmt.setString(2, studentName);
                    stmt.executeUpdate();
                }

                // 4. Initialize wallet at zero: no welcome bonus in production.
                String wallSql = """
                        INSERT INTO public.wallets (user_id, balance_poisha, updated_at)
                        VALUES (?, 0, NOW())
                        ON CONFLICT (user_id) DO NOTHING;
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(wallSql)) {
                    stmt.setString(1, profileId.toString());
                    stmt.executeUpdate();
                }

                // 5. Audit event. Canonical audit types are CYCLE/RENTAL/DISPUTE/SUPPORT,
                // so an account approval is recorded as SUPPORT/USER_APPROVED with the
                // user id and granted role in details.
                insertAuditEvent(conn, profileId.toString(), "SUPPORT", profileId.toString(),
                        "USER_APPROVED", "{\"role\":\"" + grantedRole.name() + "\"}");

                conn.commit();

            } catch (Exception ex) {
                try {
                    conn.rollback();
                } catch (Exception rb) {
                    LOGGER.log(Level.WARNING, "Approval rollback failed", rb);
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ac) {
                    LOGGER.log(Level.WARNING, "Failed to restore autocommit after approval", ac);
                }
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to approve registration in Supabase", e);
            throw new AppError("DB_ERROR", "Failed to approve registration.", e);
        }
    }

    /**
     * Reads the registration's requested role when the column exists; defaults to
     * STUDENT and coerces anything unrecognized to STUDENT. Runs under a savepoint
     * so the probe cannot abort the surrounding approval transaction on older schemas.
     */
    private Role readRequestedRole(Connection conn, UUID profileId) {
        Savepoint sp = null;
        try {
            sp = conn.setSavepoint("approve_role_probe");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT role FROM public.pending_registrations WHERE id = ?::uuid")) {
                stmt.setObject(1, profileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String raw = rs.getString("role");
                        if (raw != null && !raw.isBlank()) {
                            try {
                                return Role.valueOf(raw.trim().toUpperCase());
                            } catch (IllegalArgumentException unknown) {
                                LOGGER.log(Level.WARNING,
                                        "Unknown requested role '" + raw + "' for " + profileId + "; defaulting to STUDENT.");
                            }
                        }
                    }
                }
            }
            conn.releaseSavepoint(sp);
        } catch (Exception e) {
            try {
                if (sp != null) {
                    conn.rollback(sp);
                }
            } catch (Exception rb) {
                LOGGER.log(Level.WARNING, "Role-probe savepoint rollback failed", rb);
            }
            // Older schema without a role column — STUDENT is the safe default.
            LOGGER.log(Level.FINE, "Role probe skipped for " + profileId + ": " + e.getMessage());
        }
        return Role.STUDENT;
    }

    /** True when a Supabase auth user exists for this id. */
    private static boolean authUserExists(Connection conn, UUID userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM auth.users WHERE id = ?")) {
            stmt.setObject(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void rejectUserRegistration(String registrationId, String reason) {
        // P-049 NOTE: no actor in contract — admin gating lives with the caller (see addCycle).
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Registration rejection requires database connection.");
        }
        if (registrationId == null || registrationId.isBlank() || !isUuid(registrationId.trim())) {
            throw new AppError("INVALID_ID", "Invalid registration ID.");
        }

        String sql = "UPDATE public.pending_registrations SET verification_status = 'REJECTED' WHERE id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(registrationId.trim()));
            int n = stmt.executeUpdate();
            if (n != 1) {
                throw new AppError("NOT_FOUND", "Registration not found.");
            }

        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to reject registration in Supabase", e);
            throw new AppError("DB_ERROR", "Failed to reject registration.", e);
        }
    }

    @Override
    public boolean changePassword(String userIdOrEmail, String oldPassword, String newPassword) {
        // P-011: Password change must rotate the live auth credential.
        // This implementation uses the Supabase Admin API to update auth.users.encrypted_password.
        // Requires SUPABASE_SERVICE_ROLE_KEY (service role) in ClientConfig.
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required to change password.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters.");
        }

        // First, find the user's auth UID and verify the old password against the local hash.
        String query = """
            SELECT p.id, pr.password_hash
            FROM public.profiles p
            JOIN public.pending_registrations pr ON pr.id::uuid = p.id
            WHERE p.id::text = ? OR LOWER(pr.email) = LOWER(?) OR LOWER(pr.student_roll) = LOWER(?)
            LIMIT 1;
        """;

        String targetUid = null;
        String existingHash = null;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userIdOrEmail);
            stmt.setString(2, userIdOrEmail);
            stmt.setString(3, userIdOrEmail);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    targetUid = rs.getString("id");
                    existingHash = rs.getString("password_hash");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to lookup user for password change", e);
            throw new AppError("DB_ERROR", "Failed to lookup user.", e);
        }

        if (targetUid == null) {
            return false;
        }

        // Fail closed: blank stored hash must never waive the old-password check.
        if (existingHash == null || existingHash.isBlank()) {
            throw new IllegalStateException("Password reset required. Please contact the Cycle Office.");
        }
        if (!bd.ac.kuet.campuscycle.domain.PasswordUtils.verify(oldPassword, existingHash)) {
            throw new IllegalArgumentException("Current password does not match.");
        }

        // 1. Update the local hash in pending_registrations (for local fallback auth)
        String newHash = bd.ac.kuet.campuscycle.domain.PasswordUtils.hash(newPassword);
        String updateSql = "UPDATE public.pending_registrations SET password_hash = ? WHERE id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, newHash);
            stmt.setObject(2, UUID.fromString(targetUid));
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new AppError("NOT_FOUND", "User registration not found.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update local password hash in Supabase", e);
            throw new AppError("DB_ERROR", "Failed to update local password.", e);
        }

        // 2. Update the live auth credential via Supabase Admin API (requires service role key)
        try {
            String serviceRoleKey = bd.ac.kuet.campuscycle.config.ClientConfig.get("SUPABASE_SERVICE_ROLE_KEY");
            if (serviceRoleKey != null && !serviceRoleKey.isBlank()) {
                updateAuthUserPasswordViaAdminApi(targetUid, newPassword, serviceRoleKey);
            } else {
                LOGGER.log(Level.WARNING, "SUPABASE_SERVICE_ROLE_KEY not configured; auth.users credential not rotated. User must use Supabase Auth password reset.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to rotate auth credential via Admin API: " + e.getMessage());
            // Don't fail the whole operation - local hash was updated
        }

        // 3. Sync local cache - removed (LocalDatabase removed)

        return true;
    }

    /**
     * Updates the auth.users password via Supabase Admin API (GoTrue management endpoint).
     * Requires service role key with admin privileges.
     */
    private void updateAuthUserPasswordViaAdminApi(String uid, String newPassword, String serviceRoleKey) {
        java.net.http.HttpClient adminClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        String base = bd.ac.kuet.campuscycle.config.ClientConfig.supabaseUrl();
        String url = base + "/auth/v1/admin/users/" + uid;

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("password", newPassword);

        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("Content-Type", "application/json")
                .method("PUT", java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            java.net.http.HttpResponse<String> res = adminClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new RuntimeException("Admin API password update failed: " + res.statusCode() + " " + res.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Admin API request failed", e);
        }
    }

    @Override
    public String createSupportConversation(CampusUser student, String subject, String message) {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for support.");
        }
        if (subject == null || subject.trim().length() < 3 || subject.trim().length() > 160) {
            throw new IllegalArgumentException("Subject must be 3-160 characters.");
        }
        if (message == null || message.trim().length() < 1 || message.trim().length() > 2000) {
            throw new IllegalArgumentException("Message must be 1-2000 characters.");
        }

        String sql = "INSERT INTO public.support_conversations (student_id, subject) VALUES (?::uuid, ?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(student.id()));
            stmt.setString(2, subject.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String conversationId = rs.getString("id");
                    // Add initial message
                    String msgSql = "INSERT INTO public.support_messages (conversation_id, sender_id, body) VALUES (?::uuid, ?::uuid, ?)";
                    try (PreparedStatement msgStmt = conn.prepareStatement(msgSql)) {
                        msgStmt.setObject(1, UUID.fromString(conversationId));
                        msgStmt.setObject(2, UUID.fromString(student.id()));
                        msgStmt.setString(3, message.trim());
                        msgStmt.executeUpdate();
                    }
                    return conversationId;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create support conversation", e);
            throw new AppError("DB_ERROR", "Failed to create support conversation.", e);
        }
        throw new AppError("DB_ERROR", "Failed to create support conversation.");
    }

    @Override
    public List<SupportConversation> supportConversations(CampusUser user) {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for support.");
        }

        List<SupportConversation> list = new ArrayList<>();
        String sql;
        if (user.role() == Role.ADMIN) {
            sql = """
                SELECT c.id, c.subject, c.state,
                       COALESCE(p.display_name, 'Cycle Office') AS admin_name,
                       c.updated_at
                FROM public.support_conversations c
                LEFT JOIN public.profiles p ON p.id = c.assigned_admin
                ORDER BY c.updated_at DESC;
                """;
        } else {
            sql = """
                SELECT c.id, c.subject, c.state,
                       COALESCE(p.display_name, 'Cycle Office') AS admin_name,
                       c.updated_at
                FROM public.support_conversations c
                LEFT JOIN public.profiles p ON p.id = c.assigned_admin
                WHERE c.student_id = ?::uuid
                ORDER BY c.updated_at DESC;
                """;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (user.role() != Role.ADMIN) {
                stmt.setObject(1, UUID.fromString(user.id()));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("updated_at");
                    ZonedDateTime zdt = ts != null ? ts.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA);
                    list.add(new SupportConversation(
                            rs.getString("id"),
                            rs.getString("subject"),
                            rs.getString("state"),
                            rs.getString("admin_name"),
                            zdt
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load support conversations", e);
            throw new AppError("DB_ERROR", "Failed to load support conversations.", e);
        }
        return list;
    }

    @Override
    public List<SupportMessage> supportMessages(CampusUser user, String conversationId) {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for support.");
        }

        List<SupportMessage> list = new ArrayList<>();
        String sql = """
            SELECT m.id, m.sender_id, p.display_name AS sender_name, p.role AS sender_role, m.body, m.created_at
            FROM public.support_messages m
            JOIN public.profiles p ON p.id = m.sender_id
            WHERE m.conversation_id = ?::uuid
            ORDER BY m.created_at;
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(conversationId));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    ZonedDateTime zdt = ts != null ? ts.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA);
                    list.add(new SupportMessage(
                            rs.getString("id"),
                            rs.getString("sender_id"),
                            rs.getString("sender_name"),
                            rs.getString("sender_role"),
                            rs.getString("body"),
                            zdt
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load support messages", e);
            throw new AppError("DB_ERROR", "Failed to load support messages.", e);
        }
        return list;
    }

    @Override
    public void postSupportMessage(CampusUser user, String conversationId, String body) {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for support.");
        }
        if (body == null || body.trim().length() < 1 || body.trim().length() > 2000) {
            throw new IllegalArgumentException("Message must be 1-2000 characters.");
        }

        String sql = "INSERT INTO public.support_messages (conversation_id, sender_id, body) VALUES (?::uuid, ?::uuid, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(conversationId));
            stmt.setObject(2, UUID.fromString(user.id()));
            stmt.setString(3, body.trim());
            stmt.executeUpdate();

            // Update conversation timestamp and assign admin if admin is posting
            if (user.role() == Role.ADMIN) {
                String updSql = "UPDATE public.support_conversations SET updated_at = NOW(), assigned_admin = ?::uuid WHERE id = ?::uuid";
                try (PreparedStatement updStmt = conn.prepareStatement(updSql)) {
                    updStmt.setObject(1, UUID.fromString(user.id()));
                    updStmt.setObject(2, UUID.fromString(conversationId));
                    updStmt.executeUpdate();
                }
            } else {
                String updSql = "UPDATE public.support_conversations SET updated_at = NOW() WHERE id = ?::uuid";
                try (PreparedStatement updStmt = conn.prepareStatement(updSql)) {
                    updStmt.setObject(1, UUID.fromString(conversationId));
                    updStmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to post support message", e);
            throw new AppError("DB_ERROR", "Failed to post support message.", e);
        }
    }

    @Override
    public List<DisputeItem> disputeQueue(CampusUser admin) {
        if (admin == null) {
            throw new SecurityException("Sign-in is required to view the dispute queue.");
        }
        admin.requireRole(Role.ADMIN);
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for disputes.");
        }

        List<DisputeItem> list = new ArrayList<>();
        String sql = """
            SELECT d.id, d.rental_id, d.reason, d.state, d.created_at
            FROM public.disputes d
            WHERE d.state IN ('OPEN', 'UNDER_REVIEW')
            ORDER BY d.created_at;
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new DisputeItem(
                        rs.getString("id"),
                        rs.getString("rental_id"),
                        rs.getString("reason"),
                        rs.getString("state")
                ));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load dispute queue", e);
            throw new AppError("DB_ERROR", "Failed to load dispute queue.", e);
        }
        return list;
    }

    @Override
    public void resolveDispute(CampusUser admin, String disputeId, String notes) {
        if (admin == null) {
            throw new SecurityException("Sign-in is required to resolve disputes.");
        }
        admin.requireRole(Role.ADMIN);
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required to resolve disputes.");
        }
        if (!isUuid(disputeId)) {
            throw new AppError("INVALID_ID", "Invalid dispute ID.");
        }
        // Canonical schema requires resolution/resolved_at on RESOLVED rows; default a
        // compliant resolution when the operator leaves notes blank.
        String resolution = (notes == null || notes.isBlank()) ? "Resolved by Campus Office." : notes.trim();
        // Guarded to open states: a resolved dispute cannot be resolved again (P-039).
        String sql = "UPDATE public.disputes SET state = 'RESOLVED', resolution = ?, "
                + "assigned_admin_id = ?::uuid, resolved_at = NOW(), updated_at = NOW() "
                + "WHERE id = ?::uuid AND state IN ('OPEN', 'UNDER_REVIEW')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, resolution);
            stmt.setObject(2, UUID.fromString(admin.id()));
            stmt.setObject(3, UUID.fromString(disputeId));
            int n = stmt.executeUpdate();
            if (n != 1) {
                throw new AppError("DISPUTE_NOT_OPEN", "Dispute not found or already resolved.");
            }
            insertAuditEvent(conn, admin.id(), "DISPUTE", disputeId, "RESOLVED", "{}");
        } catch (SQLException schemaVariance) {
            // Live variance: older disputes tables lack resolution/assigned_admin_id.
            // Retry the minimal transition on a fresh connection rather than failing the
            // operator action; the guard against double-resolve is preserved.
            LOGGER.log(Level.WARNING, "Full dispute resolution failed, retrying minimal form: "
                    + schemaVariance.getMessage());
            try (Connection conn2 = DatabaseConnection.getConnection();
                 PreparedStatement stmt2 = conn2.prepareStatement(
                         "UPDATE public.disputes SET state = 'RESOLVED', updated_at = NOW() "
                         + "WHERE id = ?::uuid AND state IN ('OPEN', 'UNDER_REVIEW')")) {
                stmt2.setObject(1, UUID.fromString(disputeId));
                int n = stmt2.executeUpdate();
                if (n != 1) {
                    throw new AppError("DISPUTE_NOT_OPEN", "Dispute not found or already resolved.");
                }
            } catch (AppError e) {
                throw e;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to resolve dispute", e);
                throw new AppError("DB_ERROR", "Failed to resolve dispute.", e);
            }
        } catch (AppError | IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to resolve dispute", e);
            throw new AppError("DB_ERROR", "Failed to resolve dispute.", e);
        }
    }

    /**
     * Real audit sink (overrides the logging-only default): canonical
     * {@code (actor_id, entity_type, entity_id, action, details)} columns with a
     * legacy {@code (actor_id, action, object_type, object_id)} fallback for older
     * databases. Text entity ids (e.g. TICK-…) cannot fit the uuid column and are
     * skipped with a warning instead of failing the caller.
     */
    @Override
    public void recordAudit(CampusUser actor, String entityType, String entityId,
                            String action, String details) {
        String type = (entityType == null || entityType.isBlank()) ? "SUPPORT" : entityType.trim().toUpperCase();
        String act = (action == null || action.isBlank()) ? "EVENT" : action.trim();
        String note = (details == null || details.isBlank()) ? "{}"
                : "{\"note\":\"" + jsonEscape(details.trim()) + "\"}";
        try (Connection conn = DatabaseConnection.getConnection()) {
            insertAuditEvent(conn, actor == null ? null : actor.id(), type, entityId, act, note);
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Audit event not persisted: " + act + " " + type + "/" + entityId, e);
        }
    }

    /**
     * Transaction-scoped audit write. Tries the canonical schema first, then the
     * legacy live shape, each under a savepoint so a schema miss cannot abort the
     * caller's transaction. Throws only when neither shape exists.
     */
    private void insertAuditEvent(Connection conn, String actorId, String entityType,
                                  String entityId, String action, String detailsJson) {
        UUID entityUuid;
        try {
            entityUuid = UUID.fromString(entityId);
        } catch (Exception notUuid) {
            // Text entity ids (maintenance TICK-… ids) have no uuid column to live in.
            LOGGER.log(Level.WARNING,
                    "Audit event skipped (non-uuid entity id): " + action + " " + entityType + "/" + entityId);
            return;
        }
        Object actorUuid = null;
        if (actorId != null && !actorId.isBlank()) {
            try {
                actorUuid = UUID.fromString(actorId.trim());
            } catch (Exception notUuid) {
                LOGGER.log(Level.WARNING, "Audit event with unparseable actor id: " + actorId);
            }
        }
        String details = (detailsJson == null || detailsJson.isBlank()) ? "{}" : detailsJson;

        // Savepoints only exist inside a transaction. Several callers (review, waive,
        // dispute resolution) run in autocommit, where setSavepoint is illegal and a
        // failed audit write would otherwise be reported as a failed action even though
        // the action itself already committed.
        boolean inTransaction;
        try {
            inTransaction = !conn.getAutoCommit();
        } catch (SQLException e) {
            inTransaction = false;
        }

        // Canonical shape first, then the live database's older (object_type/object_id)
        // shape. A savepoint isolates the attempt when a transaction is open, so a
        // failed audit write cannot poison the caller's work.
        Object[] canonical = {actorUuid, entityType, entityUuid, action, details};
        if (tryAuditInsert(conn, "INSERT INTO public.audit_events "
                        + "(actor_id, entity_type, entity_id, action, details) "
                        + "VALUES (?::uuid, ?, ?::uuid, ?, ?::jsonb)",
                canonical, inTransaction, "audit_canonical")) {
            return;
        }
        Object[] legacy = {actorUuid, action, entityType, entityUuid};
        if (tryAuditInsert(conn, "INSERT INTO public.audit_events "
                        + "(actor_id, action, object_type, object_id) VALUES (?::uuid, ?, ?, ?::uuid)",
                legacy, inTransaction, "audit_legacy")) {
            return;
        }
        throw new AppError("AUDIT_FAILED", "Failed to record audit event for " + action + ".");
    }

    /**
     * Runs one audit insert, isolating it with a savepoint when a transaction is open.
     * In autocommit the single statement is all or nothing already, and savepoints
     * are illegal — which is why they are only used when a transaction exists.
     *
     * @return true when the event was recorded
     */
    private boolean tryAuditInsert(Connection conn, String sql, Object[] params,
                                   boolean inTransaction, String savepointName) {
        Savepoint sp = null;
        try {
            if (inTransaction) {
                sp = conn.setSavepoint(savepointName);
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
            }
            if (inTransaction) {
                conn.releaseSavepoint(sp);
            }
            return true;
        } catch (Exception e) {
            if (inTransaction) {
                rollbackTo(conn, sp);
            }
            LOGGER.log(Level.FINE, "Audit insert " + savepointName + " failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Booking-time UNPAID payment row (P-026). The live schema carries a currency
     * column the canonical one lacks — try with, retry without, each under a
     * savepoint. A wholly missing table is live variance: log loudly and continue,
     * since the fare trigger (not this row) is the settlement authority.
     */
    private void insertPaymentUnpaid(Connection conn, UUID paymentId, UUID rentalId, long amountPoisha) {
        Savepoint sp = null;
        try {
            sp = conn.setSavepoint("payment_with_currency");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO public.payment_records (id, rental_id, amount_poisha, currency, state) "
                    + "VALUES (?, ?, ?, 'BDT', 'UNPAID')")) {
                stmt.setObject(1, paymentId);
                stmt.setObject(2, rentalId);
                stmt.setLong(3, amountPoisha);
                stmt.executeUpdate();
            }
            conn.releaseSavepoint(sp);
            return;
        } catch (Exception withCurrency) {
            rollbackTo(conn, sp);
            LOGGER.log(Level.FINE, "Payment insert with currency failed, retrying plain: "
                    + withCurrency.getMessage());
        }
        try {
            sp = conn.setSavepoint("payment_plain");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO public.payment_records (id, rental_id, amount_poisha, state) "
                    + "VALUES (?, ?, ?, 'UNPAID')")) {
                stmt.setObject(1, paymentId);
                stmt.setObject(2, rentalId);
                stmt.setLong(3, amountPoisha);
                stmt.executeUpdate();
            }
            conn.releaseSavepoint(sp);
        } catch (Exception plainFailed) {
            rollbackTo(conn, sp);
            LOGGER.log(Level.WARNING, "payment_records UNPAID row skipped for rental " + rentalId
                    + ": " + plainFailed.getMessage());
        }
    }

    /**
     * Booking-time PAID payment row (P-026). Used when wallet is charged in the same
     * transaction as the booking. Mirrors insertPaymentUnpaid but with state='PAID'.
     */
    private void insertPaymentPaid(Connection conn, UUID paymentId, UUID rentalId, long amountPoisha) {
        Savepoint sp = null;
        try {
            sp = conn.setSavepoint("payment_paid_with_currency");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO public.payment_records (id, rental_id, amount_poisha, currency, state) "
                    + "VALUES (?, ?, ?, 'BDT', 'PAID')")) {
                stmt.setObject(1, paymentId);
                stmt.setObject(2, rentalId);
                stmt.setLong(3, amountPoisha);
                stmt.executeUpdate();
            }
            conn.releaseSavepoint(sp);
            return;
        } catch (Exception withCurrency) {
            rollbackTo(conn, sp);
            LOGGER.log(Level.FINE, "Payment PAID insert with currency failed, retrying plain: "
                    + withCurrency.getMessage());
        }
        try {
            sp = conn.setSavepoint("payment_paid_plain");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO public.payment_records (id, rental_id, amount_poisha, state) "
                    + "VALUES (?, ?, ?, 'PAID')")) {
                stmt.setObject(1, paymentId);
                stmt.setObject(2, rentalId);
                stmt.setLong(3, amountPoisha);
                stmt.executeUpdate();
            }
            conn.releaseSavepoint(sp);
        } catch (Exception plainFailed) {
            rollbackTo(conn, sp);
            LOGGER.log(Level.WARNING, "payment_records PAID row skipped for rental " + rentalId
                    + ": " + plainFailed.getMessage());
        }
    }

    private void rollbackTo(Connection conn, Savepoint sp) {
        if (sp == null) {
            return;
        }
        try {
            conn.rollback(sp);
        } catch (Exception rb) {
            LOGGER.log(Level.WARNING, "Savepoint rollback failed", rb);
        }
    }

    private static String jsonEscape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private boolean isUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String createUserWithRole(CampusUser admin, String email, String password,
                                     String displayName, Role role) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new SecurityException("Role.ADMIN required to create users with roles.");
        }
        return SupabaseAuthService.createUserWithRole(email, password, displayName, role);
    }
}
