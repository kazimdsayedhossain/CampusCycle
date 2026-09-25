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
    public List<CycleItem> catalog(CampusUser user) {
        if (!DatabaseConnection.isAvailable()) {
            return fallback.catalog(user);
        }

        List<CycleItem> items = new ArrayList<>();
        String sql = """
                SELECT c.id, c.owner_id, COALESCE(c.owner_name, 'KUET Student') AS owner_name,
                       c.label, c.cycle_type, c.physical_condition, c.pickup_point,
                       c.latitude, c.longitude, c.description, c.review_status, c.availability_status
                FROM public.cycles c
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
                // If database table is empty, merge with fallback fleet for demonstration
                return fallback.catalog(user);
            }
            return items;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load live catalog from PostgreSQL, using fallback: " + e.getMessage());
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
                SELECT c.id, c.owner_id, COALESCE(c.owner_name, 'KUET Student') AS owner_name,
                       c.label, c.cycle_type, c.physical_condition, c.pickup_point,
                       c.latitude, c.longitude, c.description, c.review_status, c.availability_status
                FROM public.cycles c
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
            LOGGER.log(Level.WARNING, "Failed to load pending cycles from PostgreSQL, using fallback: " + e.getMessage());
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
                       r.quoted_amount_poisha, r.state, r.started_at, r.due_at, r.returned_at
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
            LOGGER.log(Level.WARNING, "Failed to load rentals from PostgreSQL, using fallback: " + e.getMessage());
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
                       r.quoted_amount_poisha, r.state, r.started_at, r.due_at, r.returned_at
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
            LOGGER.log(Level.WARNING, "Failed to query active rental from PostgreSQL, using fallback: " + e.getMessage());
            return fallback.activeRental(user);
        }
    }

    @Override
    public synchronized RentalRecord book(CampusUser renter, String cycleId, int minutes) {
        if (!DatabaseConnection.isAvailable() || !isUuid(cycleId) || !isUuid(renter.id())) {
            return fallback.book(renter, cycleId, minutes);
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

                // 3. Compute tariff
                int basePoisha = 2000;
                int extraMinutes = Math.max(0, minutes - 15);
                int extraBlocks = (int) Math.ceil(extraMinutes / 15.0);
                int totalPoisha = basePoisha + (extraBlocks * 1000);

                UUID rentalId = UUID.randomUUID();
                UUID idempotencyKey = UUID.randomUUID();
                Timestamp nowTs = new Timestamp(System.currentTimeMillis());
                Timestamp dueTs = new Timestamp(System.currentTimeMillis() + (minutes * 60L * 1000L));

                String insertSql = """
                        INSERT INTO public.rentals
                        (id, cycle_id, renter_id, rate_card_version, quoted_amount_poisha, requested_minutes, state, started_at, due_at, currency, idempotency_key)
                        VALUES (?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?, 'BDT', ?);
                        """;

                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    ins.setObject(1, rentalId);
                    ins.setObject(2, UUID.fromString(cycleId));
                    ins.setObject(3, UUID.fromString(renter.id()));
                    ins.setLong(4, (long) totalPoisha);
                    ins.setInt(5, minutes);
                    ins.setTimestamp(6, nowTs);
                    ins.setTimestamp(7, dueTs);
                    ins.setObject(8, idempotencyKey);
                    ins.executeUpdate();
                }

                // 4. Create payment record
                String paySql = "INSERT INTO public.payment_records (id, rental_id, amount_poisha, currency, state) VALUES (?, ?, ?, 'BDT', 'PAID')";
                try (PreparedStatement pStmt = conn.prepareStatement(paySql)) {
                    pStmt.setObject(1, UUID.randomUUID());
                    pStmt.setObject(2, rentalId);
                    pStmt.setLong(3, (long) totalPoisha);
                    pStmt.executeUpdate();
                } catch (Exception pe) {
                    LOGGER.log(Level.FINE, "Payment record insert note: " + pe.getMessage());
                }

                // 5. Mark cycle as RENTED
                String updateCycle = "UPDATE public.cycles SET availability_status = 'RENTED', is_available = false, updated_at = now() WHERE id = ?";
                try (PreparedStatement upd = conn.prepareStatement(updateCycle)) {
                    upd.setObject(1, UUID.fromString(cycleId));
                    upd.executeUpdate();
                }

                // 6. Record audit event
                try (PreparedStatement aStmt = conn.prepareStatement(
                        "INSERT INTO public.audit_events (actor_id, action, object_type, object_id, details) VALUES (?, 'RENTAL_STARTED', 'RENTAL', ?, ?::jsonb)")) {
                    aStmt.setObject(1, UUID.fromString(renter.id()));
                    aStmt.setObject(2, rentalId);
                    aStmt.setString(3, "{\"minutes\":" + minutes + ",\"poisha\":" + totalPoisha + "}");
                    aStmt.executeUpdate();
                } catch (Exception ignored) {}

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
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live booking failed, delegating to memory repository: " + e.getMessage());
            return fallback.book(renter, cycleId, minutes);
        }
    }

    @Override
    public synchronized void returnRental(CampusUser renter, String rentalId) {
        if (!DatabaseConnection.isAvailable() || !isUuid(rentalId)) {
            fallback.returnRental(renter, rentalId);
            return;
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
                    String updCycle = "UPDATE public.cycles SET availability_status = 'AVAILABLE', is_available = true, updated_at = now() WHERE id = ?";
                    try (PreparedStatement cStmt = conn.prepareStatement(updCycle)) {
                        cStmt.setObject(1, cycleId);
                        cStmt.executeUpdate();
                    }
                }

                // Insert audit event
                try (PreparedStatement aStmt = conn.prepareStatement(
                        "INSERT INTO public.audit_events (actor_id, action, object_type, object_id, details) VALUES (?, 'RENTAL_RETURNED', 'RENTAL', ?, '{}'::jsonb)")) {
                    aStmt.setObject(1, UUID.fromString(renter.id()));
                    aStmt.setObject(2, UUID.fromString(rentalId));
                    aStmt.executeUpdate();
                } catch (Exception ignored) {}

                conn.commit();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live return failed, delegating to memory repository: " + e.getMessage());
            fallback.returnRental(renter, rentalId);
        }
    }

    @Override
    public synchronized void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason) {
        if (admin.role() != Role.ADMIN) {
            throw new SecurityException("Admin authorization required.");
        }
        if (!DatabaseConnection.isAvailable() || !isUuid(cycleId)) {
            fallback.reviewCycle(admin, cycleId, approved, reason);
            return;
        }

        String sql = "UPDATE public.cycles SET review_status = ?, is_verified = ?, updated_at = now() WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, approved ? "APPROVED" : "REJECTED");
            stmt.setBoolean(2, approved);
            stmt.setObject(3, UUID.fromString(cycleId));
            stmt.executeUpdate();

            // Insert audit event
            try (PreparedStatement aStmt = conn.prepareStatement(
                    "INSERT INTO public.audit_events (actor_id, action, object_type, object_id, details) VALUES (?, ?, 'CYCLE', ?, ?::jsonb)")) {
                aStmt.setObject(1, UUID.fromString(admin.id()));
                aStmt.setString(2, approved ? "CYCLE_APPROVED" : "CYCLE_REJECTED");
                aStmt.setObject(3, UUID.fromString(cycleId));
                aStmt.setString(4, "{\"reason\":\"" + (reason != null ? reason.replace("\"", "'") : "") + "\"}");
                aStmt.executeUpdate();
            } catch (Exception ignored) {}

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live cycle review failed, delegating to fallback: " + e.getMessage());
            fallback.reviewCycle(admin, cycleId, approved, reason);
        }
    }

    @Override
    public synchronized void rebalanceHub(String sourceHub, String targetHub, int count) {
        if (!DatabaseConnection.isAvailable()) {
            fallback.rebalanceHub(sourceHub, targetHub, count);
            return;
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

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live hub rebalancing failed, delegating to fallback: " + e.getMessage());
            fallback.rebalanceHub(sourceHub, targetHub, count);
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
        Timestamp dueTs = null;
        try {
            dueTs = rs.getTimestamp("due_at");
        } catch (SQLException ignored) {}
        Timestamp retTs = rs.getTimestamp("returned_at");

        ZonedDateTime startZoned = startTs != null ? startTs.toInstant().atZone(DHAKA) : ZonedDateTime.now(DHAKA);
        int reqMins = rs.getInt("requested_minutes");
        ZonedDateTime dueZoned = dueTs != null ? dueTs.toInstant().atZone(DHAKA) : startZoned.plusMinutes(reqMins);
        ZonedDateTime retZoned = retTs != null ? retTs.toInstant().atZone(DHAKA) : null;

        String stateStr = rs.getString("state");
        RentalStatus status = RentalStatus.ACTIVE;
        try {
            if (stateStr != null) status = RentalStatus.valueOf(stateStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        return new RentalRecord(
                rs.getString("id"),
                rs.getString("cycle_id"),
                rs.getString("label") != null ? rs.getString("label") : "Cycle #" + rs.getString("cycle_id").substring(0, 4),
                rs.getString("renter_id"),
                reqMins,
                rs.getInt("quoted_amount_poisha"),
                status,
                startZoned,
                dueZoned,
                retZoned
        );
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
