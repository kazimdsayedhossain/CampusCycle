package bd.ac.kuet.campuscycle.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Single formatting/parsing helper for money. All amounts are integer poisha;
 * doubles never cross an API boundary (P-023, P-174).
 */
public final class Money {

    private Money() {}

    public static String formatBdt(int poisha) {
        return String.format(Locale.US, "BDT %.2f", poisha / 100.0);
    }

    public static String formatTaka(int poisha) {
        return String.format(Locale.US, "\u09F3 %.2f", poisha / 100.0);
    }

    /**
     * Parses user-typed BDT (e.g. "250", "250.50") to integer poisha.
     *
     * @throws IllegalArgumentException on blank, negative, or malformed input
     */
    public static int parseBdtToPoisha(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        final BigDecimal amount;
        try {
            amount = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Amount must be a number like 250 or 250.50.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative.");
        }
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount is too large.");
        }
    }
}
