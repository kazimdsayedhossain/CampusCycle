package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Maintenance ticket domain record tracking fleet damage reports,
 * inspections, and repair resolutions.
 */
public record MaintenanceTicket(
        String id,
        String cycleId,
        String reportedByUserId,
        String issueCategory,
        String description,
        String status,
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

    public MaintenanceTicket(String id, String cycleId, String reportedByUserId, String issueCategory, String description) {
        this(id, cycleId, reportedByUserId, issueCategory, description, TicketStatus.OPEN.name(), ZonedDateTime.now(), null, null, 0);
    }

    public MaintenanceTicket(String id, String cycleId, String reportedByUserId, IssueCategory issueCategory, String description) {
        this(id, cycleId, reportedByUserId, issueCategory.name(), description, TicketStatus.OPEN.name(), ZonedDateTime.now(), null, null, 0);
    }

    public boolean isOpen() {
        return TicketStatus.OPEN.name().equalsIgnoreCase(status) || TicketStatus.IN_PROGRESS.name().equalsIgnoreCase(status);
    }

    public boolean isResolved() {
        return TicketStatus.RESOLVED.name().equalsIgnoreCase(status);
    }

    public MaintenanceTicket withResolved(ZonedDateTime resolvedAt, String technicianNotes, int repairCostPoisha) {
        return new MaintenanceTicket(
                id,
                cycleId,
                reportedByUserId,
                issueCategory,
                description,
                TicketStatus.RESOLVED.name(),
                reportedAt,
                resolvedAt != null ? resolvedAt : ZonedDateTime.now(),
                technicianNotes != null ? technicianNotes : "",
                Math.max(0, repairCostPoisha)
        );
    }

    // Bean getters
    public String getId() { return id; }
    public String getCycleId() { return cycleId; }
    public String getReportedByUserId() { return reportedByUserId; }
    public String getIssueCategory() { return issueCategory; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public ZonedDateTime getReportedAt() { return reportedAt; }
    public ZonedDateTime getResolvedAt() { return resolvedAt; }
    public String getTechnicianNotes() { return technicianNotes; }
    public int getRepairCostPoisha() { return repairCostPoisha; }

    public String ticketId() { return id; }
    public String cycleLabel() { return cycleId; }
    public String category() { return issueCategory; }
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
