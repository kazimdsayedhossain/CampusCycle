package bd.ac.kuet.campuscycle.domain;

/**
 * Status lifecycle for cycle maintenance tickets.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED;

    public static TicketStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return OPEN;
        }
        for (TicketStatus s : values()) {
            if (s.name().equalsIgnoreCase(status.trim())) {
                return s;
            }
        }
        return OPEN;
    }
}
