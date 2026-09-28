package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Maintenance ticket domain record tracking fleet damage reports,
 * inspections, and repair resolutions.
 *
 * Status and category are typed enums (P-040). A ticket can only be resolved
 * once, and only by a technician or admin — enforced by MaintenanceService.
 */
public record MaintenanceTicket(
        String id,
        String cycleId,
        String reportedByUserId,
        IssueCategory issueCategory,
        String description,
        TicketStatus status,
        ZonedDateTime reportedAt,
        ZonedDateTime resolvedAt,
        String technicianNotes,
        int repairCostPoisha
) implements Identifiable {

    public MaintenanceTicket {
        Objects.requireNonNull(id, "Ticket id must not be null");
        Objects.requireNonNull(cycleId, "Cycle id must not be null");
        Objects.requireNonNull(reportedByUserId, "Reported by user id must not be null");
        Objects.requireNonNull(issueCategory, "Issue category must not be null");
        Objects.requireNonNull(description, "Description must not be null");
        Objects.requireNonNull(status, "Status must not be null");
        Objects.requireNonNull(reportedAt, "Reported at timestamp must not be null");
    }

    public MaintenanceTicket(String id, String cycleId, String reportedByUserId, IssueCategory issueCategory, String description) {
        this(id, cycleId, reportedByUserId, issueCategory, description, TicketStatus.OPEN, CampusTime.now(), null, "", 0);
    }

    public MaintenanceTicket(String id, String cycleId, String reportedByUserId, String issueCategory, String description) {
        this(id, cycleId, reportedByUserId, IssueCategory.fromString(issueCategory), description);
    }

    public MaintenanceTicket(String id, String cycleId, String reportedByUserId, String issueCategory,
                             String description, String status, ZonedDateTime reportedAt,
                             ZonedDateTime resolvedAt, String technicianNotes, int repairCostPoisha) {
        this(id, cycleId, reportedByUserId, IssueCategory.fromString(issueCategory), description,
                TicketStatus.fromString(status), reportedAt,
                resolvedAt, technicianNotes != null ? technicianNotes : "",
                Math.max(0, repairCostPoisha));
    }

    public boolean isOpen() {
        return status == TicketStatus.OPEN || status == TicketStatus.IN_PROGRESS;
    }

    public boolean isResolved() {
        return status == TicketStatus.RESOLVED;
    }

    public MaintenanceTicket withStartedWork() {
        if (isResolved()) {
            throw new IllegalStateException("Ticket already resolved: " + id);
        }
        return new MaintenanceTicket(id, cycleId, reportedByUserId, issueCategory, description,
                TicketStatus.IN_PROGRESS, reportedAt, resolvedAt, technicianNotes, repairCostPoisha);
    }

    public MaintenanceTicket withResolved(ZonedDateTime resolvedAt, String technicianNotes, int repairCostPoisha) {
        if (isResolved()) {
            throw new IllegalStateException("Ticket already resolved: " + id);
        }
        return new MaintenanceTicket(
                id,
                cycleId,
                reportedByUserId,
                issueCategory,
                description,
                TicketStatus.RESOLVED,
                reportedAt,
                resolvedAt != null ? resolvedAt : CampusTime.now(),
                technicianNotes != null ? technicianNotes : "",
                Math.max(0, repairCostPoisha)
        );
    }

    // Bean getters (String views for UI bindings / legacy callers)
    public String getId() { return id; }
    public String getCycleId() { return cycleId; }
    public String getReportedByUserId() { return reportedByUserId; }
    public String getIssueCategory() { return issueCategory.name(); }
    public String getDescription() { return description; }
    public String getStatus() { return status.name(); }
    public ZonedDateTime getReportedAt() { return reportedAt; }
    public ZonedDateTime getResolvedAt() { return resolvedAt; }
    public String getTechnicianNotes() { return technicianNotes; }
    public int getRepairCostPoisha() { return repairCostPoisha; }

    public String ticketId() { return id; }
    /** NOTE: returns the cycle id, not a human label (kept for compatibility). */
    public String cycleLabel() { return cycleId; }
    public String category() { return issueCategory.name(); }
    public String reportedBy() { return reportedByUserId; }
    public double repairCost() { return repairCostPoisha / 100.0; }
    public String reportedDate() {
        if (reportedAt == null) return "N/A";
        return reportedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a"));
    }
    public String resolvedDate() {
        if (resolvedAt == null) return "";
        return resolvedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a"));
    }
}
