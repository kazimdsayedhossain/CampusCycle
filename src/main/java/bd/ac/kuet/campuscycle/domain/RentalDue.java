package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Persistent ride debt: return-time amounts (overtime, overdue fine) the wallet
 * could not cover. Recorded once per rental, blocks the next booking until
 * cleared, auto-settles when money appears. All amounts integer poisha.
 */
public record RentalDue(
        String id,
        String rentalId,
        String userId,
        int amountPoisha,
        int paidPoisha,
        DueState state,
        String reason,
        ZonedDateTime createdAt
) implements Identifiable {

    public RentalDue {
        Objects.requireNonNull(id, "Due id must not be null");
        Objects.requireNonNull(rentalId, "Rental id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(state, "Due state must not be null");
        if (amountPoisha <= 0) {
            throw new IllegalArgumentException("Due amount must be positive.");
        }
        if (paidPoisha < 0 || paidPoisha > amountPoisha) {
            throw new IllegalArgumentException("Paid must be within [0, amount].");
        }
        if (reason == null) {
            reason = "";
        }
    }

    /**
     * Amount still owed on this due. Money that has been paid or formally waived is
     * not outstanding, so a settled or forgiven due reads zero — the same figure the
     * database totals with, and what every total on screen is built from.
     */
    public int outstandingPoisha() {
        return isOwed() ? Math.max(0, amountPoisha - paidPoisha) : 0;
    }

    /** True while any amount is still owed (UNPAID or PARTIAL). */
    public boolean isOwed() {
        return state == DueState.UNPAID || state == DueState.PARTIAL;
    }
}
