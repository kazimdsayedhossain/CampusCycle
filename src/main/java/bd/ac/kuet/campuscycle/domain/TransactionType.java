package bd.ac.kuet.campuscycle.domain;

/**
 * Supported wallet transaction types.
 */
public enum TransactionType {
    DEPOSIT,
    RENTAL_CHARGE,
    OVERDUE_FINE,
    REFUND,
    /** Bike-owner share of a settled fare credited on return. */
    OWNER_PAYOUT,
    /** Collection against persistent ride dues (overdue/overtime arrears). */
    DUES_SETTLEMENT;

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
