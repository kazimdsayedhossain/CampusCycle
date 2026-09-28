package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.DatabaseConnection;
import bd.ac.kuet.campuscycle.domain.AppError;
import bd.ac.kuet.campuscycle.domain.CampusTime;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.domain.TransactionType;
import bd.ac.kuet.campuscycle.domain.WalletTransaction;
import javafx.application.Platform;

import java.sql.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live, Production-Ready Cloud Wallet Service for CampusCycle.
 * Operates directly against Supabase PostgreSQL (public.wallets & public.wallet_transactions).
 * Requires active database connection - no offline fallback.
 *
 * <p>Money-integrity contract (P-021, P-022, P-023, P-128, P-129, P-172):
 * <ul>
 *   <li>All amounts are integer poisha. There are no {@code double}-BDT overloads;
 *       use {@link Money} for display and parsing at the UI boundary.</li>
 *   <li>Every charge/deposit/fine is keyed by {@code (user_id, reference_code)}
 *       (unique in the DB). A replayed reference returns normally WITHOUT moving
 *       money again. A caller-supplied reference is idempotent; a generated
 *       {@code CHG-}/{@code DEP-} reference is unique per call and therefore
 *       <em>not</em> idempotent across retries.</li>
 *   <li>Every balance mutation re-reads the authoritative row with
 *       {@code UPDATE ... RETURNING balance_poisha} and uses that value for the
 *       ledger row, the cache and listener broadcasts — never a stale cache.</li>
 *   <li>Blank user ids are rejected on every method.</li>
 * </ul>
 */
public class WalletService {

    private static final Logger LOGGER = Logger.getLogger(WalletService.class.getName());
    /** New wallets start at zero: there is no welcome bonus in production. */
    public static final int INITIAL_STUDENT_BALANCE_POISHA = 0;
    public static final int MAX_SINGLE_DEPOSIT_POISHA = 10_000_000; // ৳1,00,000 max single deposit
    public static final long BALANCE_CACHE_TTL_MS = 15_000L; // 15s balance cache freshness window

    public interface WalletListener {
        void onBalanceChanged(String userId, int newBalancePoisha);
    }

    private static WalletService instance;
    private final List<WalletListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> inMemoryBalanceCache = new ConcurrentHashMap<>();
    private final Map<String, Long> balanceCacheTimestamp = new ConcurrentHashMap<>();
    private final Map<String, List<WalletTransaction>> inMemoryTxCache = new ConcurrentHashMap<>();
    private final Map<String, Long> txCacheTimestamp = new ConcurrentHashMap<>();
    public static final long TX_CACHE_TTL_MS = 15_000L;

    public static synchronized WalletService getInstance() {
        if (instance == null) {
            instance = new WalletService();
        }
        return instance;
    }

    public WalletService() {}

    public void addListener(WalletListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(WalletListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String userId, int newBalancePoisha) {
        for (WalletListener l : listeners) {
            try {
                if (Platform.isFxApplicationThread()) {
                    l.onBalanceChanged(userId, newBalancePoisha);
                } else {
                    Platform.runLater(() -> l.onBalanceChanged(userId, newBalancePoisha));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Wallet listener failed: " + l.getClass().getName(), e);
            }
        }
    }

    /** Refreshes the cache, invalidates the tx cache and broadcasts the authoritative balance. */
    private void publishBalance(String userId, int balancePoisha) {
        inMemoryBalanceCache.put(userId, balancePoisha);
        balanceCacheTimestamp.put(userId, System.currentTimeMillis());
        inMemoryTxCache.remove(userId);
        txCacheTimestamp.remove(userId);
        notifyListeners(userId, balancePoisha);
    }

    public int getBalancePoisha(String userId) {
        if (userId == null || userId.isBlank()) return 0;

        long now = System.currentTimeMillis();
        Integer cached = inMemoryBalanceCache.get(userId);
        Long cachedAt = balanceCacheTimestamp.get(userId);
        if (cached != null && cachedAt != null && (now - cachedAt < BALANCE_CACHE_TTL_MS)) {
            return cached;
        }

        if (!DatabaseConnection.isAvailable()) {
            if (cached != null) return cached;
            throw new AppError("OFFLINE", "Database connection required for wallet operations.");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = "SELECT balance_poisha FROM public.wallets WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int bal = rs.getInt("balance_poisha");
                        inMemoryBalanceCache.put(userId, bal);
                        balanceCacheTimestamp.put(userId, now);
                        return bal;
                    }
                }
            }

            // Cold wallet: create the row exactly once, even under races (P-129).
            // No welcome credit: new wallets start at zero. The no-op DO UPDATE
            // lets RETURNING report whether THIS call inserted the row.
            int initial = INITIAL_STUDENT_BALANCE_POISHA;
            String claimWallet = """
                    INSERT INTO public.wallets (user_id, balance_poisha, updated_at) VALUES (?, ?, NOW())
                    ON CONFLICT (user_id) DO UPDATE SET balance_poisha = public.wallets.balance_poisha
                    RETURNING balance_poisha
                    """;
            int balance;
            try (PreparedStatement stmt = conn.prepareStatement(claimWallet)) {
                stmt.setString(1, userId);
                stmt.setInt(2, initial);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new AppError("DB_ERROR", "Failed to initialize wallet.");
                    }
                    balance = rs.getInt("balance_poisha");
                }
            }

            // No welcome ledger entry: with zero initial credit there is nothing
            // to record, and a BDT 0.00 "bonus" row would be ledger noise.

            inMemoryBalanceCache.put(userId, balance);
            balanceCacheTimestamp.put(userId, now);
            return balance;

        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to query live wallet balance for " + userId, e);
            throw new AppError("DB_ERROR", "Failed to get wallet balance.", e);
        }
    }

    /** Clears the in-memory balance cache. Useful in tests to reset state between runs. */
    /**
     * Re-reads the live balance for {@code userId} and tells every listener.
     *
     * <p>Money now moves inside the rental settlement transaction rather than through
     * this service, so callers that change a balance outside it use this to drop the
     * cached figure and refresh every open screen. A read failure is not an error for
     * the caller — the next screen load reads the truth anyway.
     */
    public void refreshAndNotifyBalance(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            publishBalance(userId, getBalancePoisha(userId));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Balance refresh failed for " + userId, e);
            inMemoryBalanceCache.remove(userId);
            balanceCacheTimestamp.remove(userId);
        }
    }

    public void clearCache() {
        inMemoryBalanceCache.clear();
        balanceCacheTimestamp.clear();
        inMemoryTxCache.clear();
        txCacheTimestamp.clear();
    }

    /** Seeds the cache from a persisted balance (e.g. local ledger) if absent. */
    public void seedBalanceIfAbsent(String userId, int balancePoisha) {
        if (userId != null && !userId.isBlank() && balancePoisha >= 0) {
            inMemoryBalanceCache.putIfAbsent(userId, balancePoisha);
            balanceCacheTimestamp.putIfAbsent(userId, System.currentTimeMillis());
        }
    }

    /**
     * Charges a rental fare against Campus Pay.
     *
     * @param rentalId idempotency key: the rental this fare belongs to.
     *                 Repeating the call with the same rental id returns {@code true}
     *                 without charging again.
     */
    public boolean deductFare(CampusUser user, int amountPoisha, String rentalId, String description) {
        String uid = user != null ? user.id() : "";
        return chargePoisha(uid, amountPoisha, description, rentalId);
    }

    /**
     * Credits a wallet top-up.
     *
     * @param refCode idempotency key. Pass a stable per-top-up reference
     *                (e.g. {@code "TOPUP-<uuid>"}) to make retries safe. When the
     *                caller passes none, a unique {@code DEP-...} reference is
     *                generated — that call is then <em>not</em> idempotent.
     */
    public boolean depositPoisha(String userId, int amountPoisha, String method, String refCode) {
        if (userId == null || userId.isBlank() || amountPoisha <= 0) return false;
        if (amountPoisha > MAX_SINGLE_DEPOSIT_POISHA) {
            throw new IllegalArgumentException("Max single deposit is " + Money.formatTaka(MAX_SINGLE_DEPOSIT_POISHA)
                    + " (" + MAX_SINGLE_DEPOSIT_POISHA + " poisha).");
        }

        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for deposit.");
        }

        String description = "Wallet Recharge via "
                + (method != null && !method.isBlank() ? method : "Campus Pay")
                + " (" + Money.formatTaka(amountPoisha) + ")";
        String finalRef = (refCode != null && !refCode.isBlank())
                ? refCode.trim()
                : "DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer replayBalance = checkReplay(conn, userId, finalRef);
                if (replayBalance != null) {
                    conn.commit();
                    publishBalance(userId, replayBalance);
                    return true;
                }

                ensureWalletRow(conn, userId);
                int balanceAfter;
                String updateSql = "UPDATE public.wallets SET balance_poisha = balance_poisha + ?, updated_at = NOW() WHERE user_id = ? RETURNING balance_poisha";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, amountPoisha);
                    stmt.setString(2, userId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw new AppError("DB_ERROR", "Wallet row missing for deposit.");
                        }
                        balanceAfter = rs.getInt("balance_poisha");
                    }
                }

                WalletTransaction tx = new WalletTransaction(
                        newTxId(),
                        userId,
                        amountPoisha,
                        TransactionType.DEPOSIT,
                        balanceAfter,
                        CampusTime.now(),
                        description,
                        finalRef
                );
                if (!recordTransactionDirect(conn, tx)) {
                    // Lost a race with an identical reference: undo our credit, report replay.
                    conn.rollback();
                    publishBalance(userId, getBalancePoisha(userId));
                    return true;
                }
                conn.commit();
                publishBalance(userId, balanceAfter);
                return true;

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed live deposit for " + userId, e);
            throw new AppError("DB_ERROR", "Failed to process deposit.", e);
        }
    }

    /**
     * Debits the wallet when the balance covers the amount.
     *
     * @param refCode idempotency key (e.g. the rental id). A replay returns
     *                {@code true} without charging again. A generated
     *                {@code CHG-...} reference (caller passed none) is unique per
     *                call and therefore <em>not</em> idempotent.
     * @return {@code true} if charged (or already charged for this reference),
     *         {@code false} if the balance is insufficient — nothing is moved.
     */
    public boolean chargePoisha(String userId, int amountPoisha, String description, String refCode) {
        if (userId == null || userId.isBlank() || amountPoisha <= 0) return false;

        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for charge.");
        }

        String desc = description != null ? description : "Cycle Commute Charge";
        String finalRef = (refCode != null && !refCode.isBlank())
                ? refCode.trim()
                : "CHG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer replayBalance = checkReplay(conn, userId, finalRef);
                if (replayBalance != null) {
                    conn.commit();
                    publishBalance(userId, replayBalance);
                    return true;
                }

                int balanceAfter;
                String updateSql = "UPDATE public.wallets SET balance_poisha = balance_poisha - ?, updated_at = NOW() WHERE user_id = ? AND balance_poisha >= ? RETURNING balance_poisha";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, amountPoisha);
                    stmt.setString(2, userId);
                    stmt.setInt(3, amountPoisha);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            // Insufficient balance (or no wallet): nothing moved.
                            conn.rollback();
                            int actual = selectBalance(conn, userId);
                            publishBalance(userId, actual);
                            return false;
                        }
                        balanceAfter = rs.getInt("balance_poisha");
                    }
                }

                WalletTransaction tx = new WalletTransaction(
                        newTxId(),
                        userId,
                        -amountPoisha,
                        TransactionType.RENTAL_CHARGE,
                        balanceAfter,
                        CampusTime.now(),
                        desc,
                        finalRef
                );
                if (!recordTransactionDirect(conn, tx)) {
                    conn.rollback();
                    publishBalance(userId, getBalancePoisha(userId));
                    return true;
                }
                conn.commit();
                publishBalance(userId, balanceAfter);
                return true;

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed live charge for " + userId, e);
            throw new AppError("DB_ERROR", "Failed to process charge.", e);
        }
    }

    /**
     * Applies an overdue fine, clamped at zero (the wallet never goes negative).
     * The ledger records the fine assessed with the authoritative post-clamp
     * balance from {@code RETURNING} — never a stale cache value (P-021).
     *
     * @param rentalId the rental this fine belongs to; the ledger reference is
     *                 {@code "FINE-<rentalId>"} (unique per rental), so a repeated
     *                 fine for the same rental returns {@code true} without
     *                 charging again.
     */
    public boolean chargeOverdueFine(String userId, int finePoisha, String rentalId, String description) {
        if (userId == null || userId.isBlank() || finePoisha <= 0) return false;
        if (rentalId == null || rentalId.isBlank()) {
            throw new IllegalArgumentException("chargeOverdueFine requires the rental id (idempotency key).");
        }

        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for fine processing.");
        }

        String finalRef = "FINE-" + rentalId.trim();
        String desc = (description != null && !description.isBlank())
                ? description
                : "Overdue Station Late Return Fee";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer replayBalance = checkReplay(conn, userId, finalRef);
                if (replayBalance != null) {
                    conn.commit();
                    publishBalance(userId, replayBalance);
                    return true;
                }

                ensureWalletRow(conn, userId);
                int balanceAfter;
                String updateSql = "UPDATE public.wallets SET balance_poisha = GREATEST(0, balance_poisha - ?), updated_at = NOW() WHERE user_id = ? RETURNING balance_poisha";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, finePoisha);
                    stmt.setString(2, userId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw new AppError("DB_ERROR", "Wallet row missing for fine.");
                        }
                        balanceAfter = rs.getInt("balance_poisha");
                    }
                }

                WalletTransaction tx = new WalletTransaction(
                        newTxId(),
                        userId,
                        -finePoisha,
                        TransactionType.OVERDUE_FINE,
                        balanceAfter,
                        CampusTime.now(),
                        desc,
                        finalRef
                );
                if (!recordTransactionDirect(conn, tx)) {
                    conn.rollback();
                    publishBalance(userId, getBalancePoisha(userId));
                    return true;
                }
                conn.commit();
                publishBalance(userId, balanceAfter);
                return true;

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to record live overdue fine", e);
            throw new AppError("DB_ERROR", "Failed to process overdue fine.", e);
        }
    }

    /**
     * Reverses a previously recorded charge (compensating refund, P-017).
     * This is a guarded reversal, not a plain credit: it only moves money when
     * a ledger row with {@code refCode} already exists for this user; otherwise
     * it returns {@code false} and moves nothing, so a failed charge can never
     * be turned into free money. The refund row itself is idempotent under
     * {@code "REFUND-" + refCode}.
     *
     * @param refCode the reference of the original charge to reverse (required).
     */
    public boolean refundFare(CampusUser user, int amountPoisha, String refCode, String description) {
        if (user == null || user.id() == null || user.id().isBlank() || amountPoisha <= 0) return false;
        if (refCode == null || refCode.isBlank()) {
            throw new IllegalArgumentException("refundFare requires the original charge reference (idempotency key).");
        }

        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for refund.");
        }

        String uid = user.id();
        String chargeRef = refCode.trim();
        String refundRef = "REFUND-" + chargeRef;
        String desc = (description != null && !description.isBlank()) ? description : "Fare reversal";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer alreadyRefunded = checkReplay(conn, uid, refundRef);
                if (alreadyRefunded != null) {
                    conn.commit();
                    publishBalance(uid, alreadyRefunded);
                    return true;
                }
                if (!hasReference(conn, uid, chargeRef)) {
                    // No charge to reverse: move nothing.
                    conn.rollback();
                    return false;
                }

                ensureWalletRow(conn, uid);
                int balanceAfter;
                String updateSql = "UPDATE public.wallets SET balance_poisha = balance_poisha + ?, updated_at = NOW() WHERE user_id = ? RETURNING balance_poisha";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, amountPoisha);
                    stmt.setString(2, uid);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw new AppError("DB_ERROR", "Wallet row missing for refund.");
                        }
                        balanceAfter = rs.getInt("balance_poisha");
                    }
                }

                WalletTransaction tx = new WalletTransaction(
                        newTxId(),
                        uid,
                        amountPoisha,
                        TransactionType.REFUND,
                        balanceAfter,
                        CampusTime.now(),
                        desc,
                        refundRef
                );
                if (!recordTransactionDirect(conn, tx)) {
                    conn.rollback();
                    publishBalance(uid, getBalancePoisha(uid));
                    return true;
                }
                conn.commit();
                publishBalance(uid, balanceAfter);
                return true;

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed live refund for " + uid, e);
            throw new AppError("DB_ERROR", "Failed to process refund.", e);
        }
    }

    public List<WalletTransaction> getTransactions(String userId) {
        if (userId == null || userId.isBlank()) return List.of();

        long now = System.currentTimeMillis();
        List<WalletTransaction> cached = inMemoryTxCache.get(userId);
        Long cachedAt = txCacheTimestamp.get(userId);
        if (cached != null && cachedAt != null && (now - cachedAt < TX_CACHE_TTL_MS)) {
            return cached;
        }

        if (!DatabaseConnection.isAvailable()) {
            if (cached != null) return cached;
            throw new AppError("OFFLINE", "Database connection required for transaction history.");
        }

        List<WalletTransaction> list = new ArrayList<>();
        String sql = "SELECT id, user_id, amount_poisha, transaction_type, balance_after_poisha, timestamp, description, reference_code FROM public.wallet_transactions WHERE user_id = ? ORDER BY timestamp DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("timestamp");
                    ZonedDateTime dt = ts != null ? ts.toInstant().atZone(CampusTime.DHAKA) : CampusTime.now();
                    list.add(new WalletTransaction(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getInt("amount_poisha"),
                            rs.getString("transaction_type"),
                            rs.getInt("balance_after_poisha"),
                            dt,
                            rs.getString("description"),
                            rs.getString("reference_code")
                    ));
                }
            }
            List<WalletTransaction> unmodifiable = Collections.unmodifiableList(list);
            inMemoryTxCache.put(userId, unmodifiable);
            txCacheTimestamp.put(userId, now);
            return unmodifiable;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load live transactions for " + userId, e);
            throw new AppError("DB_ERROR", "Failed to load transaction history.", e);
        }
    }

    // ------------------------------------------------------------------ ledger

    /**
     * Idempotent ledger insert keyed on {@code (user_id, reference_code)}.
     *
     * @return {@code true} if the row was inserted; {@code false} if this
     *         reference was already recorded (replay — the caller must NOT move
     *         money again and should return normally).
     * @throws IllegalArgumentException if the reference code is blank: the key
     *         is what makes retries safe, so anonymous rows are refused.
     */
    private boolean recordTransactionDirect(Connection conn, WalletTransaction tx) throws SQLException {
        if (tx.referenceCode() == null || tx.referenceCode().isBlank()) {
            throw new IllegalArgumentException("Ledger writes require a non-blank reference_code (idempotency key).");
        }
        String insertSql = """
            INSERT INTO public.wallet_transactions (id, user_id, amount_poisha, transaction_type, balance_after_poisha, timestamp, description, reference_code)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, reference_code) DO NOTHING RETURNING id
        """;
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, tx.id());
            stmt.setString(2, tx.userId());
            stmt.setInt(3, tx.amountPoisha());
            stmt.setString(4, tx.type());
            stmt.setInt(5, tx.balanceAfterPoisha());
            stmt.setTimestamp(6, Timestamp.from(tx.timestamp().toInstant()));
            stmt.setString(7, tx.description());
            stmt.setString(8, tx.referenceCode());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Returns the current authoritative balance when {@code ref} was already recorded, else null. */
    private Integer checkReplay(Connection conn, String userId, String ref) throws SQLException {
        if (!hasReference(conn, userId, ref)) {
            return null;
        }
        return selectBalance(conn, userId);
    }

    private boolean hasReference(Connection conn, String userId, String ref) throws SQLException {
        String sql = "SELECT 1 FROM public.wallet_transactions WHERE user_id = ? AND reference_code = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, ref);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int selectBalance(Connection conn, String userId) throws SQLException {
        String sql = "SELECT balance_poisha FROM public.wallets WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("balance_poisha") : 0;
            }
        }
    }

    private void ensureWalletRow(Connection conn, String userId) throws SQLException {
        String sql = "INSERT INTO public.wallets (user_id, balance_poisha, updated_at) VALUES (?, 0, NOW()) ON CONFLICT (user_id) DO NOTHING";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        }
    }

    private static String newTxId() {
        return "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
