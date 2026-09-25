package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;

import java.io.File;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Embedded SQLite database manager demonstrating:
 * 1. SQLite database setup with foreign key constraints
 * 2. Complete CRUD (Create, Read, Update, Delete) operations
 * 3. Offline/Local persistence cache
 */
public class LocalDatabase {

    private static final String DB_DIR = System.getProperty("user.home") + File.separator + ".campuscycle";
    private static final String DB_PATH = DB_DIR + File.separator + "campuscycle.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    private static LocalDatabase instance;

    public static synchronized LocalDatabase getInstance() {
        if (instance == null) {
            instance = new LocalDatabase();
        }
        return instance;
    }

    private LocalDatabase() {
        initDatabase();
    }

    private Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(JDBC_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    private void initDatabase() {
        File dir = new File(DB_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Profiles table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS local_profiles (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'STUDENT',
                    created_at TEXT NOT NULL
                );
            """);

            // 2. Cycles table with Foreign Key to Profiles
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS local_cycles (
                    id TEXT PRIMARY KEY,
                    cycle_id TEXT UNIQUE NOT NULL,
                    owner_id TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    label TEXT NOT NULL,
                    cycle_type TEXT NOT NULL,
                    physical_condition TEXT NOT NULL,
                    pickup_point TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    description TEXT,
                    review_status TEXT NOT NULL DEFAULT 'APPROVED',
                    availability_status TEXT NOT NULL DEFAULT 'AVAILABLE',
                    FOREIGN KEY (owner_id) REFERENCES local_profiles(id) ON DELETE CASCADE
                );
            """);

            // 3. Rentals table with Foreign Keys to Cycles and Profiles
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS local_rentals (
                    id TEXT PRIMARY KEY,
                    cycle_id TEXT NOT NULL,
                    cycle_label TEXT NOT NULL,
                    renter_id TEXT NOT NULL,
                    requested_minutes INTEGER NOT NULL,
                    quoted_amount_poisha INTEGER NOT NULL,
                    state TEXT NOT NULL DEFAULT 'ACTIVE',
                    started_at TEXT NOT NULL,
                    due_at TEXT NOT NULL,
                    returned_at TEXT,
                    FOREIGN KEY (cycle_id) REFERENCES local_cycles(id) ON DELETE CASCADE,
                    FOREIGN KEY (renter_id) REFERENCES local_profiles(id) ON DELETE CASCADE
                );
            """);

            // 4. Wallet Transactions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS local_wallet_transactions (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    amount_poisha INTEGER NOT NULL,
                    transaction_type TEXT NOT NULL,
                    balance_after_poisha INTEGER NOT NULL,
                    timestamp TEXT NOT NULL,
                    description TEXT,
                    reference_code TEXT
                );
            """);

            // 5. Maintenance Tickets table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS local_maintenance_tickets (
                    id TEXT PRIMARY KEY,
                    cycle_id TEXT NOT NULL,
                    reported_by_user_id TEXT NOT NULL,
                    issue_category TEXT NOT NULL,
                    description TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    reported_at TEXT NOT NULL,
                    resolved_at TEXT,
                    technician_notes TEXT,
                    repair_cost_poisha INTEGER NOT NULL DEFAULT 0
                );
            """);

        } catch (SQLException e) {
            System.err.println("Failed to initialize SQLite local database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==========================================
    // CREATE (C in CRUD)
    // ==========================================

    public void saveProfile(CampusUser user) {
        String sql = """
            INSERT INTO local_profiles (id, display_name, email, role, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET display_name = excluded.display_name, email = excluded.email;
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.id());
            pstmt.setString(2, user.displayName());
            pstmt.setString(3, user.email());
            pstmt.setString(4, user.role().name());
            pstmt.setString(5, ZonedDateTime.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite saveProfile error: " + e.getMessage());
        }
    }

    public void saveCycle(CycleItem cycle) {
        String sql = """
            INSERT INTO local_cycles (id, cycle_id, owner_id, owner_name, label, cycle_type, physical_condition, pickup_point, latitude, longitude, description, review_status, availability_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                label = excluded.label,
                physical_condition = excluded.physical_condition,
                pickup_point = excluded.pickup_point,
                latitude = excluded.latitude,
                longitude = excluded.longitude,
                description = excluded.description,
                review_status = excluded.review_status,
                availability_status = excluded.availability_status;
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cycle.id());
            pstmt.setString(2, "C-" + cycle.id().substring(0, Math.min(8, cycle.id().length())));
            pstmt.setString(3, cycle.ownerId());
            pstmt.setString(4, cycle.ownerName());
            pstmt.setString(5, cycle.label());
            pstmt.setString(6, cycle.type().name());
            pstmt.setString(7, cycle.condition().name());
            pstmt.setString(8, cycle.pickupPoint());
            pstmt.setDouble(9, cycle.latitude());
            pstmt.setDouble(10, cycle.longitude());
            pstmt.setString(11, cycle.description());
            pstmt.setString(12, cycle.reviewStatus().name());
            pstmt.setString(13, cycle.availabilityStatus().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite saveCycle error: " + e.getMessage());
        }
    }

    public void saveRental(RentalRecord rental) {
        String sql = """
            INSERT INTO local_rentals (id, cycle_id, cycle_label, renter_id, requested_minutes, quoted_amount_poisha, state, started_at, due_at, returned_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                state = excluded.state,
                returned_at = excluded.returned_at;
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rental.id());
            pstmt.setString(2, rental.cycleId());
            pstmt.setString(3, rental.cycleLabel());
            pstmt.setString(4, rental.renterId());
            pstmt.setInt(5, rental.requestedMinutes());
            pstmt.setInt(6, rental.quotedAmountPoisha());
            pstmt.setString(7, rental.status().name());
            pstmt.setString(8, rental.startedAt().toString());
            pstmt.setString(9, rental.dueAt().toString());
            pstmt.setString(10, rental.returnedAt() != null ? rental.returnedAt().toString() : null);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite saveRental error: " + e.getMessage());
        }
    }

    // ==========================================
    // READ (R in CRUD)
    // ==========================================

    public List<CycleItem> getAvailableCycles() {
        List<CycleItem> list = new ArrayList<>();
        String sql = "SELECT * FROM local_cycles WHERE review_status = 'APPROVED' AND availability_status = 'AVAILABLE' ORDER BY label;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCycle(rs));
            }
        } catch (SQLException e) {
            System.err.println("SQLite getAvailableCycles error: " + e.getMessage());
        }
        return list;
    }


    public List<RentalRecord> getRentalsByRenter(String renterId) {
        List<RentalRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM local_rentals WHERE renter_id = ? ORDER BY started_at DESC;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, renterId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRental(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLite getRentalsByRenter error: " + e.getMessage());
        }
        return list;
    }

    public Optional<RentalRecord> getActiveRental(String renterId) {
        String sql = "SELECT * FROM local_rentals WHERE renter_id = ? AND state = 'ACTIVE' LIMIT 1;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, renterId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRental(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLite getActiveRental error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<CampusUser> getProfileByEmail(String email) {
        String sql = "SELECT * FROM local_profiles WHERE LOWER(email) = LOWER(?) LIMIT 1;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new CampusUser(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        Role.valueOf(rs.getString("role"))
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLite getProfileByEmail error: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ==========================================
    // UPDATE (U in CRUD)
    // ==========================================

    public void updateCycleAvailability(String cycleId, AvailabilityStatus status) {
        String sql = "UPDATE local_cycles SET availability_status = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, cycleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite updateCycleAvailability error: " + e.getMessage());
        }
    }

    public void updateRentalReturned(String rentalId) {
        String sql = "UPDATE local_rentals SET state = 'RETURNED', returned_at = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ZonedDateTime.now().toString());
            pstmt.setString(2, rentalId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite updateRentalReturned error: " + e.getMessage());
        }
    }

    // ==========================================
    // DELETE (D in CRUD)
    // ==========================================

    public boolean deleteCycle(String cycleId) {
        String sql = "DELETE FROM local_cycles WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cycleId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("SQLite deleteCycle error: " + e.getMessage());
            return false;
        }
    }

    public boolean cancelRental(String rentalId) {
        String sql = "DELETE FROM local_rentals WHERE id = ? AND state = 'ACTIVE';";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rentalId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("SQLite cancelRental error: " + e.getMessage());
            return false;
        }
    }

    // ==========================================
    // WALLET TRANSACTIONS CRUD
    // ==========================================

    public void saveWalletTransaction(WalletTransaction tx) {
        String sql = """
            INSERT INTO local_wallet_transactions (id, user_id, amount_poisha, transaction_type, balance_after_poisha, timestamp, description, reference_code)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                balance_after_poisha = excluded.balance_after_poisha,
                description = excluded.description,
                reference_code = excluded.reference_code;
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tx.id());
            pstmt.setString(2, tx.userId());
            pstmt.setInt(3, tx.amountPoisha());
            pstmt.setString(4, tx.type());
            pstmt.setInt(5, tx.balanceAfterPoisha());
            pstmt.setString(6, tx.timestamp().toString());
            pstmt.setString(7, tx.description());
            pstmt.setString(8, tx.referenceCode());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite saveWalletTransaction error: " + e.getMessage());
        }
    }

    public List<WalletTransaction> getWalletTransactionsByUserId(String userId) {
        List<WalletTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM local_wallet_transactions WHERE user_id = ? ORDER BY timestamp DESC;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapWalletTransaction(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLite getWalletTransactionsByUserId error: " + e.getMessage());
        }
        return list;
    }

    public List<WalletTransaction> getAllWalletTransactions() {
        List<WalletTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM local_wallet_transactions ORDER BY timestamp DESC;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapWalletTransaction(rs));
            }
        } catch (SQLException e) {
            System.err.println("SQLite getAllWalletTransactions error: " + e.getMessage());
        }
        return list;
    }

    public Optional<Integer> getLatestWalletBalance(String userId) {
        String sql = "SELECT balance_after_poisha FROM local_wallet_transactions WHERE user_id = ? ORDER BY timestamp DESC LIMIT 1;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("balance_after_poisha"));
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLite getLatestWalletBalance error: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ==========================================
    // MAINTENANCE TICKETS CRUD
    // ==========================================

    public void saveMaintenanceTicket(MaintenanceTicket ticket) {
        String sql = """
            INSERT INTO local_maintenance_tickets (id, cycle_id, reported_by_user_id, issue_category, description, status, reported_at, resolved_at, technician_notes, repair_cost_poisha)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                status = excluded.status,
                resolved_at = excluded.resolved_at,
                technician_notes = excluded.technician_notes,
                repair_cost_poisha = excluded.repair_cost_poisha;
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticket.id());
            pstmt.setString(2, ticket.cycleId());
            pstmt.setString(3, ticket.reportedByUserId());
            pstmt.setString(4, ticket.issueCategory());
            pstmt.setString(5, ticket.description());
            pstmt.setString(6, ticket.status());
            pstmt.setString(7, ticket.reportedAt().toString());
            pstmt.setString(8, ticket.resolvedAt() != null ? ticket.resolvedAt().toString() : null);
            pstmt.setString(9, ticket.technicianNotes());
            pstmt.setInt(10, ticket.repairCostPoisha());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite saveMaintenanceTicket error: " + e.getMessage());
        }
    }

    public Optional<MaintenanceTicket> getMaintenanceTicketById(String ticketId) {
        String sql = "SELECT * FROM local_maintenance_tickets WHERE id = ? LIMIT 1;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMaintenanceTicket(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLite getMaintenanceTicketById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<MaintenanceTicket> getOpenMaintenanceTickets() {
        List<MaintenanceTicket> list = new ArrayList<>();
        String sql = "SELECT * FROM local_maintenance_tickets WHERE status != 'RESOLVED' ORDER BY reported_at DESC;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapMaintenanceTicket(rs));
            }
        } catch (SQLException e) {
            System.err.println("SQLite getOpenMaintenanceTickets error: " + e.getMessage());
        }
        return list;
    }

    public List<MaintenanceTicket> getAllMaintenanceTickets() {
        List<MaintenanceTicket> list = new ArrayList<>();
        String sql = "SELECT * FROM local_maintenance_tickets ORDER BY reported_at DESC;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapMaintenanceTicket(rs));
            }
        } catch (SQLException e) {
            System.err.println("SQLite getAllMaintenanceTickets error: " + e.getMessage());
        }
        return list;
    }

    // ==========================================
    // FLEET & RENTALS AUDIT / EXTENSIONS
    // ==========================================

    public List<CycleItem> getAllCycles() {
        List<CycleItem> list = new ArrayList<>();
        String sql = "SELECT * FROM local_cycles ORDER BY label;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCycle(rs));
            }
        } catch (SQLException e) {
            System.err.println("SQLite getAllCycles error: " + e.getMessage());
        }
        return list;
    }

    public Optional<CycleItem> getCycleById(String cycleId) {
        String sql = "SELECT * FROM local_cycles WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cycleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCycle(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLite getCycleById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<RentalRecord> getAllRentals() {
        List<RentalRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM local_rentals ORDER BY started_at DESC;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRental(rs));
            }
        } catch (SQLException e) {
            System.err.println("SQLite getAllRentals error: " + e.getMessage());
        }
        return list;
    }

    public void updateCycleLocation(String cycleId, String newLocation) {
        String sql = "UPDATE local_cycles SET pickup_point = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newLocation);
            pstmt.setString(2, cycleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQLite updateCycleLocation error: " + e.getMessage());
        }
    }

    // ==========================================
    // MAPPERS
    // ==========================================

    private CycleItem mapCycle(ResultSet rs) throws SQLException {
        return new CycleItem(
            rs.getString("id"),
            rs.getString("owner_id"),
            rs.getString("owner_name"),
            rs.getString("label"),
            CycleType.valueOf(rs.getString("cycle_type")),
            CycleCondition.valueOf(rs.getString("physical_condition")),
            rs.getString("pickup_point"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getString("description"),
            ReviewStatus.valueOf(rs.getString("review_status")),
            AvailabilityStatus.valueOf(rs.getString("availability_status"))
        );
    }

    private RentalRecord mapRental(ResultSet rs) throws SQLException {
        ZonedDateTime start = ZonedDateTime.parse(rs.getString("started_at"));
        ZonedDateTime due = ZonedDateTime.parse(rs.getString("due_at"));
        String retStr = rs.getString("returned_at");
        ZonedDateTime returned = retStr != null ? ZonedDateTime.parse(retStr) : null;

        return new RentalRecord(
            rs.getString("id"),
            rs.getString("cycle_id"),
            rs.getString("cycle_label"),
            rs.getString("renter_id"),
            rs.getInt("requested_minutes"),
            rs.getInt("quoted_amount_poisha"),
            RentalStatus.valueOf(rs.getString("state")),
            start,
            due,
            returned
        );
    }

    private WalletTransaction mapWalletTransaction(ResultSet rs) throws SQLException {
        return new WalletTransaction(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getInt("amount_poisha"),
            rs.getString("transaction_type"),
            rs.getInt("balance_after_poisha"),
            ZonedDateTime.parse(rs.getString("timestamp")),
            rs.getString("description"),
            rs.getString("reference_code")
        );
    }

    private MaintenanceTicket mapMaintenanceTicket(ResultSet rs) throws SQLException {
        String resStr = rs.getString("resolved_at");
        ZonedDateTime resolved = resStr != null ? ZonedDateTime.parse(resStr) : null;

        return new MaintenanceTicket(
            rs.getString("id"),
            rs.getString("cycle_id"),
            rs.getString("reported_by_user_id"),
            rs.getString("issue_category"),
            rs.getString("description"),
            rs.getString("status"),
            ZonedDateTime.parse(rs.getString("reported_at")),
            resolved,
            rs.getString("technician_notes"),
            rs.getInt("repair_cost_poisha")
        );
    }
}
