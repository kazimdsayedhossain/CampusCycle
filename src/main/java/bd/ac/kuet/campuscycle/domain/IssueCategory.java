package bd.ac.kuet.campuscycle.domain;

/**
 * Standard categories for cycle maintenance tickets.
 */
public enum IssueCategory {
    FLAT_TIRE,
    BRAKE_ISSUE,
    CHAIN_GEAR,
    BATTERY_ELECTRICAL,
    STRUCTURAL,
    ROUTINE_CHECKUP;

    public static IssueCategory fromString(String category) {
        if (category == null || category.isBlank()) {
            return ROUTINE_CHECKUP;
        }
        for (IssueCategory c : values()) {
            if (c.name().equalsIgnoreCase(category.trim())) {
                return c;
            }
        }
        return ROUTINE_CHECKUP;
    }
}
