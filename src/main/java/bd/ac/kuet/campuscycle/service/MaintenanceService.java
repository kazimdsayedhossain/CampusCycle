package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.DatabaseConnection;
import bd.ac.kuet.campuscycle.domain.AppError;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusTime;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.IssueCategory;
import bd.ac.kuet.campuscycle.domain.MaintenanceTicket;
import bd.ac.kuet.campuscycle.domain.ReleaseDecision;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.TicketStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the cycle block/restore lifecycle (P-031..P-047).
 *
 * <ul>
 *   <li>STUDENTs may only <em>report</em>; TECHNICIANs resolve; ADMINs may also retire.</li>
 *   <li>Every availability change goes through a guarded single statement with a
 *       state precondition and an affected-row check — never a blind flip.</li>
 *   <li>Resolving requires an explicit {@link ReleaseDecision}; a resolve never
 *       silently returns a bike to service.</li>
 *   <li>Failures throw {@link AppError}; a missing ticket returns {@code false}.</li>
 * </ul>
 */
public class MaintenanceService {

    private static final Logger LOGGER = Logger.getLogger(MaintenanceService.class.getName());

    /** Repair cost at or above which a repaired cycle drops one condition grade. */
    private static final int HEAVY_REPAIR_THRESHOLD_POISHA = 5_000;

    private static MaintenanceService instance;

    public static synchronized MaintenanceService getInstance() {
        if (instance == null) {
            instance = new MaintenanceService();
        }
        return instance;
    }

    private MaintenanceService() {}

    // ------------------------------------------------------------------ report

    public MaintenanceTicket reportDamage(String cycleId, CampusUser actor, String issueCategory, String description) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required to report damage.");
        }
        actor.requireRole(Role.STUDENT, Role.TECHNICIAN, Role.ADMIN);
        Objects.requireNonNull(cycleId, "Cycle ID cannot be null");

        IssueCategory category = IssueCategory.fromString(issueCategory);
        String desc = (description != null && !description.isBlank()) ? description.trim() : "Damage reported on return";

        MaintenanceTicket ticket = new MaintenanceTicket(
                nextTicketId(), cycleId, actor.id(), category, desc);

        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required to report damage.");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO public.maintenance_tickets "
                                + "(id, cycle_id, reported_by_user_id, issue_category, description, status, reported_at, repair_cost_poisha) "
                                + "VALUES (?, ?, ?, ?, ?, 'OPEN', NOW(), 0)")) {
                    stmt.setString(1, ticket.id());
                    stmt.setString(2, cycleId);
                    stmt.setString(3, actor.id());
                    stmt.setString(4, category.name());
                    stmt.setString(5, desc);
                    stmt.executeUpdate();
                }

                // Block only a rentable-or-quarantined cycle; never a ride in progress (P-036).
                int blocked;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE public.cycles SET availability_status = 'MAINTENANCE', updated_at = NOW() "
                                + "WHERE id = ?::uuid AND availability_status IN ('AVAILABLE', 'QUARANTINE', 'MAINTENANCE')")) {
                    stmt.setString(1, cycleId);
                    blocked = stmt.executeUpdate();
                }
                if (blocked != 1) {
                    conn.rollback();
                    throw new AppError("CYCLE_NOT_BLOCKED",
                            "Cycle could not be blocked for maintenance (it may be rented, retired, or unknown). Nothing was recorded.");
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to record live maintenance ticket for cycle " + cycleId, e);
            throw new AppError("MAINTENANCE_REPORT_FAILED",
                    "Damage report could not be saved. Please retry.", e);
        }
        return ticket;
    }

    // ------------------------------------------------------------------ triage

    /** Technician picks up a ticket: OPEN -> IN_PROGRESS. */
    public boolean startWork(String ticketId, CampusUser actor) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required.");
        }
        actor.requireRole(Role.TECHNICIAN, Role.ADMIN);
        if (ticketId == null || ticketId.isBlank()) {
            return false;
        }
        String id = ticketId.trim();
        try {
            Optional<MaintenanceTicket> opt = getMaintenanceTicketById(id);
            if (opt.isEmpty() || !opt.get().isOpen()
                    || opt.get().status() == TicketStatus.IN_PROGRESS) {
                return false;
            }
            // Local cache removed - ticket status is authoritative in Supabase
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed triage for ticket " + id, e);
        }
        if (!DatabaseConnection.isAvailable()) {
            return true;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE public.maintenance_tickets SET status = 'IN_PROGRESS' WHERE id = ? AND status = 'OPEN'")) {
            stmt.setString(1, id);
            return stmt.executeUpdate() == 1;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed live triage for ticket " + id, e);
            throw new AppError("TICKET_UPDATE_FAILED", "Ticket could not be updated. Please retry.", e);
        }
    }

    // ------------------------------------------------------------------ resolve

    /**
     * Resolves a ticket and releases the cycle per {@code decision}.
     *
     * @return true if resolved; false if the ticket does not exist or is not open
     * @throws AppError if any write fails — partial state is never reported as success
     */
    public boolean resolveTicket(String ticketId, String technicianNotes, int repairCostPoisha,
                                 ReleaseDecision decision, String redeployHub,
                                 CampusUser actor, CampusRepository repo) {
        if (actor == null) {
            throw new SecurityException("Sign-in is required.");
        }
        actor.requireRole(Role.TECHNICIAN, Role.ADMIN);
        Objects.requireNonNull(decision, "Release decision is required.");
        if (decision == ReleaseDecision.RETIRE) {
            actor.requireRole(Role.ADMIN);
        }
        if (ticketId == null || ticketId.isBlank()) {
            return false;
        }
        String id = ticketId.trim();
        String notes = technicianNotes != null ? technicianNotes : "Repairs complete";
        int cost = Math.max(0, repairCostPoisha);
        String hub = redeployHub != null ? redeployHub.trim() : "";

        Optional<MaintenanceTicket> opt = getMaintenanceTicketById(id);
        if (opt.isEmpty() || !opt.get().isOpen()) {
            return false;
        }
        String cycleId = opt.get().cycleId();

        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required to resolve ticket.");
        }

        resolveLive(id, cycleId, notes, cost, decision, hub, actor, repo);

        if (repo != null) {
            repo.recordAudit(actor, "CYCLE", cycleId, "MAINTENANCE_RELEASED",
                    decision.name() + (hub.isEmpty() ? "" : " redeployed to " + hub));
        }
        bd.ac.kuet.campuscycle.data.EventBus.getInstance().publish(
                new bd.ac.kuet.campuscycle.domain.event.CycleStatusChangedEvent(
                        cycleId, targetStatus(decision)));
        return true;
    }

private void resolveLive(String ticketId, String cycleId, String notes, int cost,
                              ReleaseDecision decision, String redeployHub,
                              CampusUser actor, CampusRepository repo) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int tickets;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE public.maintenance_tickets SET status = 'RESOLVED', resolved_at = NOW(), "
                                + "resolved_by_user_id = ?, release_decision = ?, technician_notes = ?, repair_cost_poisha = ? "
                                + "WHERE id = ? AND status IN ('OPEN', 'IN_PROGRESS')")) {
                    stmt.setString(1, actor.id());
                    stmt.setString(2, decision.name());
                    stmt.setString(3, notes);
                    stmt.setInt(4, cost);
                    stmt.setString(5, ticketId);
                    tickets = stmt.executeUpdate();
                }
                if (tickets != 1) {
                    conn.rollback();
                    throw new AppError("TICKET_NOT_OPEN", "Ticket is not open.");
                }

                // Release only a blocked cycle; never flip a ride in progress (P-033).
                int cycles;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE public.cycles SET availability_status = ?, "
                                + "pickup_point = COALESCE(NULLIF(?, ''), pickup_point), "
                                + "physical_condition = CASE "
                                + "WHEN ? >= " + HEAVY_REPAIR_THRESHOLD_POISHA + " AND physical_condition = 'EXCELLENT' THEN 'GOOD' "
                                + "WHEN ? >= " + HEAVY_REPAIR_THRESHOLD_POISHA + " AND physical_condition = 'GOOD' THEN 'FAIR' "
                                + "ELSE physical_condition END, "
                                + "retired_at = CASE WHEN ? = 'RETIRED' THEN NOW() ELSE retired_at END, "
                                + "updated_at = NOW() "
                                + "WHERE id = ?::uuid AND availability_status = 'MAINTENANCE'")) {
                    stmt.setString(1, targetStatus(decision).name());
                    stmt.setString(2, redeployHub);
                    stmt.setInt(3, cost);
                    stmt.setInt(4, cost);
                    stmt.setString(5, targetStatus(decision).name());
                    stmt.setString(6, cycleId);
                    cycles = stmt.executeUpdate();
                }
                if (cycles != 1) {
                    conn.rollback();
                    throw new AppError("CYCLE_NOT_BLOCKED",
                            "Cycle is not blocked for maintenance; refusing to release it.");
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed live resolution for ticket " + ticketId, e);
            throw new AppError("TICKET_RESOLVE_FAILED", "Ticket could not be resolved. No changes were made.", e);
        }
    }

    private static AvailabilityStatus targetStatus(ReleaseDecision decision) {
        return switch (decision) {
            case RETURN_TO_SERVICE -> AvailabilityStatus.AVAILABLE;
            case QUARANTINE -> AvailabilityStatus.QUARANTINE;
            case RETIRE -> AvailabilityStatus.RETIRED;
        };
    }

    // ------------------------------------------------------------------ queries

    public List<MaintenanceTicket> getOpenTickets() {
        return getTicketsByStatus(EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS));
    }

    public List<MaintenanceTicket> getTicketsByStatus(Set<TicketStatus> statuses) {
        Set<TicketStatus> wanted = (statuses == null || statuses.isEmpty())
                ? EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)
                : EnumSet.copyOf(statuses);
        List<MaintenanceTicket> merged = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MaintenanceTicket t : queryLiveTickets(null)) {
            if (wanted.contains(t.status()) && seen.add(t.id())) {
                merged.add(t);
            }
        }
        merged.sort((a, b) -> b.reportedAt().compareTo(a.reportedAt()));
        return merged;
    }

    public List<MaintenanceTicket> getAllTickets() {
        return queryLiveTickets(null);
    }

    public Optional<MaintenanceTicket> getMaintenanceTicketById(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return Optional.empty();
        }
        String id = ticketId.trim();
        List<MaintenanceTicket> live = queryLiveTickets(id);
        if (!live.isEmpty()) {
            return Optional.of(live.get(0));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ internals

    private static String nextTicketId() {
        return "TICK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private List<MaintenanceTicket> queryLiveTickets(String ticketIdOrNull) {
        if (!DatabaseConnection.isAvailable()) {
            return List.of();
        }
        String sql = ticketIdOrNull == null
                ? "SELECT * FROM public.maintenance_tickets ORDER BY reported_at DESC LIMIT 500"
                : "SELECT * FROM public.maintenance_tickets WHERE id = ?";
        List<MaintenanceTicket> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (ticketIdOrNull != null) {
                stmt.setString(1, ticketIdOrNull);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to execute live maintenance query", e);
        }
        return list;
    }

    private static MaintenanceTicket mapTicket(ResultSet rs) throws java.sql.SQLException {
        Timestamp repTs = rs.getTimestamp("reported_at");
        Timestamp resTs = null;
        try {
            resTs = rs.getTimestamp("resolved_at");
        } catch (java.sql.SQLException ignored) {
            // Older schema without resolved_at.
        }
        ZonedDateTime repDt = repTs != null
                ? repTs.toInstant().atZone(CampusTime.DHAKA) : CampusTime.now();
        ZonedDateTime resDt = resTs != null ? resTs.toInstant().atZone(CampusTime.DHAKA) : null;
        String notes = null;
        try {
            notes = rs.getString("technician_notes");
        } catch (java.sql.SQLException ignored) {
            // Older schema.
        }
        int cost = 0;
        try {
            cost = rs.getInt("repair_cost_poisha");
        } catch (java.sql.SQLException ignored) {
            // Older schema.
        }
        return new MaintenanceTicket(
                rs.getString("id"),
                rs.getString("cycle_id"),
                rs.getString("reported_by_user_id"),
                rs.getString("issue_category"),
                rs.getString("description"),
                rs.getString("status"),
                repDt,
                resDt,
                notes,
                cost);
    }
}
