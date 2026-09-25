package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Domain record representing an immutable ledger entry in a user's wallet.
 */
public record WalletTransaction(
        String id,
        String userId,
        int amountPoisha,
        String type,
        int balanceAfterPoisha,
        ZonedDateTime timestamp,
        String description,
        String referenceCode
) implements Identifiable {

    public WalletTransaction {
        Objects.requireNonNull(id, "Transaction id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(type, "Transaction type must not be null");
        Objects.requireNonNull(timestamp, "Timestamp must not be null");
    }

    public WalletTransaction(String id, String userId, int amountPoisha, TransactionType type, int balanceAfterPoisha, ZonedDateTime timestamp, String description, String referenceCode) {
        this(id, userId, amountPoisha, type != null ? type.name() : TransactionType.DEPOSIT.name(), balanceAfterPoisha, timestamp, description, referenceCode);
    }

    public boolean isDeposit() {
        return TransactionType.DEPOSIT.name().equalsIgnoreCase(type) || amountPoisha > 0;
    }

    public boolean isCharge() {
        return amountPoisha < 0;
    }

    // Bean getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public int getAmountPoisha() { return amountPoisha; }
    public String getType() { return type; }
    public int getBalanceAfterPoisha() { return balanceAfterPoisha; }
    public ZonedDateTime getTimestamp() { return timestamp; }
    public String getDescription() { return description; }
    public String getReferenceCode() { return referenceCode; }
}
