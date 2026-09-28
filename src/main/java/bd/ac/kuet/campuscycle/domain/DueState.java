package bd.ac.kuet.campuscycle.domain;

/**
 * Lifecycle of a {@link RentalDue}. UNPAID → PARTIAL → PAID on collection;
 * WAIVED is terminal via admin action with a recorded reason.
 */
public enum DueState {
    UNPAID,
    PARTIAL,
    PAID,
    WAIVED;

    public static DueState fromString(String raw) {
        if (raw == null) return UNPAID;
        for (DueState s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) {
                return s;
            }
        }
        return UNPAID;
    }
}
