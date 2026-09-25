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
 * Live Supabase PostgreSQL repository for CampusCycle with automatic fallback
 * to InMemoryCampusRepository when offline or during connectivity interruptions.
 */
public final class SupabaseCampusRepository implements CampusRepository {

    private static final Logger LOGGER = Logger.getLogger(SupabaseCampusRepository.class.getName());
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private final InMemoryCampusRepository fallback = new InMemoryCampusRepository();

    public SupabaseCampusRepository() {
        LOGGER.info("SupabaseCampusRepository initialized with active database pooler check.");
    }

    @Override
    public List<CycleItem> allCycles(CampusUser admin) {
        if (!DatabaseConnection.isAvailable()) {
            return fallback.allCycles(admin);
        }

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

            if (items.isEmpty()) {
                return fallback.allCycles(admin);
            }
            return items;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load live allCycles; using local fallback.", e);
            return fallback.allCycles(admin);
        }
    }

    @Override
    public void addCycle(CycleItem cycle) {
        fallback.addCycle(cycle);
        LocalDatabase.getInstance().saveCycle(cycle);
    }

    @Override
    public void updateCycle(CycleItem cycle) {
        fallback.updateCycle(cycle);
        LocalDatabase.getInstance().saveCycle(cycle);
    }

    @Override
    public void deleteCycle(String cycleId) {
        fallback.deleteCycle(cycleId);
        LocalDatabase.getInstance().deleteCycle(cycleId);
    }

    @Override
    public void setCycleAvailability(String cycleId, AvailabilityStatus status) {
        fallback.setCycleAvailability(cycleId, status);
        LocalDatabase.getInstance().updateCycleAvailability(cycleId, status);
    }

    @Override
    public List<CycleItem> catalog(CampusUser user) {
        if (!DatabaseConnection.isAvailable()) {
            return fallback.catalog(user);
        }

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

            if (items.isEmpty()) {
                // If database table is empty, merge with fallback fleet
                return fallback.catalog(user);
            }
            return items;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load live catalog; using local fallback. [CATALOG_FALLBACK]");
            return fallback.catalog(user);
        }
    }

    @Override
    public List<CycleItem> pendingCycles() {
        if (!DatabaseConnection.isAvailable()) {
            return fallback.pendingCycles();
        }

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

            if (items.isEmpty()) {
                return fallback.pendingCycles();
            }
            return items;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load pending cycles; using local fallback. [PENDING_FALLBACK]");
            return fallback.pendingCycles();
        }
    }

    @Override
    public List<RentalRecord> rentals(CampusUser user) {
        if (!DatabaseConnection.isAvailable() || !isUuid(user.id())) {
            return fallback.rentals(user);
        }

        List<RentalRecord> records = new ArrayList<>();
        String sql = """
                SELECT r.id, r.cycle_id, c.label, r.renter_id, r.requested_minutes,
                       r.quoted_amount_poisha, r.state, r.started_at, r.returned_at
                FROM public.rentals r
                JOIN public.cycles c ON r.cycle_id = c.id
                WHERE r.renter_id = ?
                ORDER BY r.started_at DESC;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(user.id()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRentalRecord(rs));
                }
            }
            return records.isEmpty() ? fallback.rentals(user) : records;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load rentals; using local fallback. [RENTALS_FALLBACK]");
            return fallback.rentals(user);
        }
    }

    @Override
    public RentalRecord activeRental(CampusUser user) {
        if (!DatabaseConnection.isAvailable() || !isUuid(user.id())) {
            return fallback.activeRental(user);
        }

        String sql = """
                SELECT r.id, r.cycle_id, c.label, r.renter_id, r.requested_minutes,
                       r.quoted_amount_poisha, r.state, r.started_at, r.returned_at
                FROM public.rentals r
                JOIN public.cycles c ON r.cycle_id = c.id
                WHERE r.renter_id = ? AND r.state = 'ACTIVE'
                LIMIT 1;
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(user.id()));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRentalRecord(rs);
                }
            }
            return fallback.activeRental(user);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to query active rental; using local fallback. [ACTIVE_FALLBACK]");
            return fallback.activeRental(user);
        }
    }

    @Override
    public synchronized RentalRecord book(CampusUser renter, String cycleId, int minutes) {
        if (!isUuid(cycleId) || !isUuid(renter.id())) {
            return fallback.book(renter, cycleId, minutes);
        }
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Live store is not configured. Booking is unavailable offline.");
        }

        // Execute atomic database transaction with row locking
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Verify renter has no active rental
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
                String cycleSql = "SELECT label, availability_status, review_status, owner_id FROM public.cycles WHERE id = ? FOR UPDATE";
                String cycleLabel;
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
                        cycleLabel = rs.getString("label");
                    }
                }

                // 3. Compute tariff (single source; server RPC is authoritative in production)
                int totalPoisha = TariffService.quotePoisha(minutes);

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
                }

                // 5. Create payment record UNPAID until provider webhook
                String paySql = "INSERT INTO public.payment_records (id, rental_id, amount_poisha, currency, state) VALUES (?, ?, ?, 'BDT', 'UNPAID')";
                try (PreparedStatement pStmt = conn.prepareStatement(paySql)) {
                    pStmt.setObject(1, UUID.randomUUID());
                    pStmt.setObject(2, rentalId);
                    pStmt.setLong(3, (long) totalPoisha);
                    pStmt.executeUpdate();
                }

                // 6. Mark cycle as RENTED
                String updateCycle = "UPDATE public.cycles SET availability_status = 'RENTED', updated_at = now() WHERE id = ?";
                try (PreparedStatement upd = conn.prepareStatement(updateCycle)) {
                    upd.setObject(1, UUID.fromString(cycleId));
                    upd.executeUpdate();
                }

                // 7. Record audit event
                try (PreparedStatement aStmt = conn.prepareStatement(
                        "INSERT INTO public.audit_events (actor_id, object_type, object_id, action, details) VALUES (?, 'RENTAL', ?, 'STARTED', jsonb_build_object('minutes', ?::int, 'poisha', ?::int))")) {
                    aStmt.setObject(1, UUID.fromString(renter.id()));
                    aStmt.setObject(2, rentalId);
                    aStmt.setInt(3, minutes);
                    aStmt.setInt(4, totalPoisha);
                    aStmt.executeUpdate();
                }

                conn.commit();

                ZonedDateTime startZoned = nowTs.toInstant().atZone(DHAKA);
                ZonedDateTime dueZoned = dueTs.toInstant().atZone(DHAKA);
                return new RentalRecord(
                        rentalId.toString(),
                        cycleId,
                        cycleLabel,
                        renter.id(),
                        minutes,
                        totalPoisha,
                        RentalStatus.ACTIVE,
                        startZoned,
                        dueZoned,
                        null
                );

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live booking failed. [BOOK_FAILED]");
            throw new AppError("BOOK_FAILED", "Booking failed. Please retry.", e);
        }
    }

    @Override
    public synchronized void returnRental(CampusUser renter, String rentalId) {
        if (!isUuid(rentalId)) {
            fallback.returnRental(renter, rentalId);
            return;
        }
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Return is unavailable offline. Please retry when connected.");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find rental and associated cycle
                UUID cycleId = null;
                String findSql = "SELECT cycle_id, renter_id, state FROM public.rentals WHERE id = ? FOR UPDATE";
                try (PreparedStatement fStmt = conn.prepareStatement(findSql)) {
                    fStmt.setObject(1, UUID.fromString(rentalId));
                    try (ResultSet rs = fStmt.executeQuery()) {
                        if (!rs.next() || !"ACTIVE".equalsIgnoreCase(rs.getString("state"))) {
                            throw new IllegalStateException("Rental record is not active or already returned.");
                        }
                        cycleId = (UUID) rs.getObject("cycle_id");
                    }
                }

                // Update rental
                String updRental = "UPDATE public.rentals SET state = 'RETURNED', returned_at = now(), updated_at = now() WHERE id = ?";
                try (PreparedStatement rStmt = conn.prepareStatement(updRental)) {
                    rStmt.setObject(1, UUID.fromString(rentalId));
                    rStmt.executeUpdate();
                }

                // Update cycle back to AVAILABLE
                if (cycleId != null) {
                    String updCycle = "UPDATE public.cycles SET availability_status = 'AVAILABLE', updated_at = now() WHERE id = ?";
                    try (PreparedStatement cStmt = conn.prepareStatement(updCycle)) {
                        cStmt.setObject(1, cycleId);
                        cStmt.executeUpdate();
                    }
                }

                // Insert audit event
                try (PreparedStatement aStmt = conn.prepareStatement(
                        "INSERT INTO public.audit_events (actor_id, object_type, object_id, action, details) VALUES (?, 'RENTAL', ?, 'RETURNED', '{}'::jsonb)")) {
                    aStmt.setObject(1, UUID.fromString(renter.id()));
                    aStmt.setObject(2, UUID.fromString(rentalId));
                    aStmt.executeUpdate();
                }

                conn.commit();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live return failed. [RETURN_FAILED]");
            throw new AppError("RETURN_FAILED", "Return failed. Please retry.", e);
        }
    }

    @Override
    public synchronized void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason) {
        if (admin.role() != Role.ADMIN) {
            throw new SecurityException("Admin authorization required.");
        }
        if (!isUuid(cycleId)) {
            fallback.reviewCycle(admin, cycleId, approved, reason);
            return;
        }
        if (!approved && (reason == null || reason.trim().length() < 10)) {
            throw new IllegalArgumentException("A written reason (min 10 chars) is required to reject.");
        }
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Review is unavailable offline.");
        }

        String sql = "UPDATE public.cycles SET review_status = ?, updated_at = now() WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, approved ? "APPROVED" : "REJECTED");
            stmt.setObject(2, UUID.fromString(cycleId));
            int updated = stmt.executeUpdate();
            if (updated == 0) throw new IllegalStateException("Cycle not found.");

            // Insert audit event
            try (PreparedStatement aStmt = conn.prepareStatement(
                    "INSERT INTO public.audit_events (actor_id, object_type, object_id, action, details) VALUES (?, 'CYCLE', ?, ?, jsonb_build_object('reason', ?))")) {
                aStmt.setObject(1, UUID.fromString(admin.id()));
                aStmt.setObject(2, UUID.fromString(cycleId));
                aStmt.setString(3, approved ? "APPROVED" : "REJECTED");
                aStmt.setString(4, reason == null ? "" : reason.trim());
                aStmt.executeUpdate();
            }

        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live cycle review failed. [REVIEW_FAILED]");
            throw new AppError("REVIEW_FAILED", "Review failed. Please retry.", e);
        }
    }

    @Override
    public synchronized void rebalanceHub(String sourceHub, String targetHub, int count) {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Rebalance is unavailable offline.");
        }

        String sql = """
                UPDATE public.cycles
                SET pickup_point = ?, updated_at = now()
                WHERE id IN (
                    SELECT id FROM public.cycles
                    WHERE pickup_point = ? AND availability_status = 'AVAILABLE'
                    LIMIT ?
                );
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, targetHub);
            stmt.setString(2, sourceHub);
            stmt.setInt(3, count);
            stmt.executeUpdate();

        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live hub rebalancing failed. [REBALANCE_FAILED]");
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

        ZonedDateTime startZoned = startTs != null ? startTs.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA);
        int reqMins = rs.getInt("requested_minutes");
        ZonedDateTime dueZoned = startZoned.plusMinutes(reqMins);
        ZonedDateTime retZoned = retTs != null ? retTs.toInstant().atZone(DHAKA) : null;

        String stateStr = rs.getString("state");
        RentalStatus status = RentalStatus.ACTIVE;
        try {
            if (stateStr != null) status = RentalStatus.valueOf(stateStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        return new RentalRecord(
                rs.getString("id"),
                rs.getString("cycle_id"),
                rs.getString("label") != null ? rs.getString("label") : safeCycleLabel(rs.getString("cycle_id")),
                rs.getString("renter_id"),
                reqMins,
                rs.getInt("quoted_amount_poisha"),
                status,
                startZoned,
                dueZoned,
                retZoned
        );
    }

    private String safeCycleLabel(String cycleId) {
        if (cycleId == null || cycleId.length() < 4) return "Campus Cycle";
        return "Cycle #" + cycleId.substring(0, 4);
    }

    @Override
    public synchronized String openDispute(CampusUser renter, String rentalId, String reason) {
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 2000) {
            throw new IllegalArgumentException("Dispute reason must be 10-2000 characters.");
        }
        if (!isUuid(rentalId) || !isUuid(renter.id())) {
            throw new AppError("DISPUTE_FAILED", "Dispute failed. Invalid rental.");
        }
        if (!DatabaseConnection.isAvailable()) {
            return fallback.openDispute(renter, rentalId, reason);
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
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE public.rentals SET state = 'DISPUTED', updated_at = now() WHERE id = ?")) {
                    upd.setObject(1, UUID.fromString(rentalId));
                    upd.executeUpdate();
                }
                try (PreparedStatement aStmt = conn.prepareStatement(
                        "INSERT INTO public.audit_events (actor_id, object_type, object_id, action, details) VALUES (?, 'DISPUTE', ?, 'OPENED', '{}'::jsonb)")) {
                    aStmt.setObject(1, UUID.fromString(renter.id()));
                    aStmt.setObject(2, disputeId);
                    aStmt.executeUpdate();
                }
                conn.commit();
                return disputeId.toString();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (IllegalStateException | IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live dispute failed. [DISPUTE_FAILED]");
            throw new AppError("DISPUTE_FAILED", "Dispute failed. Please retry.", e);
        }
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
}
