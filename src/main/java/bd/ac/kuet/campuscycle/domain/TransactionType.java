package bd.ac.kuet.campuscycle.domain;

/**
 * Supported wallet transaction types.
 */
public enum TransactionType {
    DEPOSIT,
    RENTAL_CHARGE,
    OVERDUE_FINE,
    REFUND;

    public static TransactionType fromString(String type) {
        if (type == null || type.isBlank()) {
            return DEPOSIT;
        }
        for (TransactionType t : values()) {
            if (t.name().equalsIgnoreCase(type.trim())) {
                return t;
            }
        }
        return DEPOSIT;
    }
}
