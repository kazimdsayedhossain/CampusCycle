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
}
