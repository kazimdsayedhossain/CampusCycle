package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.TransactionType;
import bd.ac.kuet.campuscycle.domain.WalletTransaction;
import javafx.application.Platform;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Production-ready Wallet & Transaction Service for CampusCycle.
 * Manages campus digital student wallets, fare deduction on cycle return,
 * and ledger transaction recording with persistent SQLite storage.
 */
public class WalletService {

    public interface WalletListener {
        void onBalanceChanged(String userId, double newBalance);
    }

    public static final int INITIAL_STUDENT_BALANCE_POISHA = 2000; // ৳20.00 welcome credit

    private static WalletService instance;
    private final LocalDatabase localDatabase;

    private final Map<String, Integer> balanceCache = new ConcurrentHashMap<>();
    private final Map<String, List<WalletTransaction>> transactionCache = new ConcurrentHashMap<>();
    private final Set<String> initializedUsers = ConcurrentHashMap.newKeySet();
    private final List<WalletListener> listeners = new CopyOnWriteArrayList<>();

    public static synchronized WalletService getInstance() {
        if (instance == null) {
            instance = new WalletService(LocalDatabase.getInstance());
        }
        return instance;
    }

    public WalletService() {
        this(LocalDatabase.getInstance());
    }

    public WalletService(LocalDatabase localDatabase) {
        this.localDatabase = Objects.requireNonNull(localDatabase, "LocalDatabase must not be null");
    }

    public void addListener(WalletListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(WalletListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String userId, double newBalance) {
        for (WalletListener l : listeners) {
            try {
                if (Platform.isFxApplicationThread()) {
                    l.onBalanceChanged(userId, newBalance);
                } else {
                    Platform.runLater(() -> l.onBalanceChanged(userId, newBalance));
                }
            } catch (Exception ignored) {}
        }
    }

    private void ensureInitialized(String userId) {
        if (userId == null || userId.isBlank() || initializedUsers.contains(userId)) {
            return;
        }
        synchronized (this) {
            if (initializedUsers.contains(userId)) {
                return;
            }
            List<WalletTransaction> dbTransactions = localDatabase.getWalletTransactionsByUserId(userId);
            if (dbTransactions.isEmpty()) {
                // Grant initial welcome balance (2000 poisha / ৳20.00)
                int initialBalance = INITIAL_STUDENT_BALANCE_POISHA;
                WalletTransaction welcomeTx = new WalletTransaction(
                        "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        userId,
                        initialBalance,
                        TransactionType.DEPOSIT.name(),
                        initialBalance,
                        ZonedDateTime.now(),
                        "Welcome Student Bonus (৳20.00)",
                        "WELCOME-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                );
                localDatabase.saveWalletTransaction(welcomeTx);
                balanceCache.put(userId, initialBalance);
                transactionCache.put(userId, new CopyOnWriteArrayList<>(List.of(welcomeTx)));
            } else {
                int latestBalance = dbTransactions.get(0).balanceAfterPoisha();
                balanceCache.put(userId, latestBalance);
                transactionCache.put(userId, new CopyOnWriteArrayList<>(dbTransactions));
            }
            initializedUsers.add(userId);
        }
    }

    public int getBalancePoisha(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        ensureInitialized(userId);
        return balanceCache.getOrDefault(userId, 0);
    }

    public double getBalance(String userId) {
        return getBalancePoisha(userId) / 100.0;
    }

    public double getBalance(CampusUser user) {
        return user != null ? getBalance(user.id()) : 0.0;
    }

    public synchronized boolean deposit(String userId, int amountPoisha, String method, String refCode) {
        if (userId == null || userId.isBlank() || amountPoisha <= 0) {
            return false;
        }
        ensureInitialized(userId);

        int currentBalance = balanceCache.getOrDefault(userId, 0);
        int newBalance = currentBalance + amountPoisha;

        String description = (method != null && !method.isBlank()) ? "Deposit via " + method : "Wallet Deposit";
        String reference = (refCode != null && !refCode.isBlank()) ? refCode : "DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        WalletTransaction tx = new WalletTransaction(
                "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                userId,
                amountPoisha,
                TransactionType.DEPOSIT.name(),
                newBalance,
                ZonedDateTime.now(),
                description,
                reference
        );

        localDatabase.saveWalletTransaction(tx);
        balanceCache.put(userId, newBalance);
        transactionCache.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(0, tx);

        notifyListeners(userId, newBalance / 100.0);
        return true;
    }

    public synchronized boolean deposit(CampusUser user, double amountBdt, String method) {
        if (user == null) return false;
        return deposit(user.id(), (int) Math.round(amountBdt * 100), method, null);
    }

    public synchronized boolean deposit(CampusUser user, double amountBdt) {
        return deposit(user, amountBdt, "bKash");
    }

    public synchronized boolean deposit(String userId, double amountBdt, String method) {
        return deposit(userId, (int) Math.round(amountBdt * 100), method, null);
    }

    public synchronized boolean deposit(String userId, double amountBdt) {
        return deposit(userId, amountBdt, "bKash");
    }

    public synchronized boolean deposit(double amountBdt) {
        return deposit("default_user", amountBdt, "bKash");
    }

    public double getBalance() {
        return getBalance("default_user");
    }

    public synchronized boolean charge(String userId, int amountPoisha, String description, String refCode) {
        if (userId == null || userId.isBlank() || amountPoisha <= 0) {
            return false;
        }
        ensureInitialized(userId);

        int currentBalance = balanceCache.getOrDefault(userId, 0);
        if (currentBalance < amountPoisha) {
            return false;
        }
        int newBalance = currentBalance - amountPoisha;
        String desc = (description != null && !description.isBlank()) ? description : "Rental Charge";
        String reference = (refCode != null && !refCode.isBlank()) ? refCode : "CHG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        WalletTransaction tx = new WalletTransaction(
                "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                userId,
                -amountPoisha,
                TransactionType.RENTAL_CHARGE.name(),
                newBalance,
                ZonedDateTime.now(),
                desc,
                reference
        );

        localDatabase.saveWalletTransaction(tx);
        balanceCache.put(userId, newBalance);
        transactionCache.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(0, tx);

        notifyListeners(userId, newBalance / 100.0);
        return true;
    }

    public synchronized boolean deductFare(CampusUser user, int amountPoisha, String rentalId, String description) {
        String uid = user != null ? user.id() : null;
        return deductFare(uid, amountPoisha, rentalId, description);
    }

    public synchronized boolean deductFare(String userId, int amountPoisha, String rentalId, String description) {
        return charge(userId, amountPoisha, description, rentalId);
    }

    public synchronized boolean deductFare(CampusUser user, double fareBdt) {
        if (user == null) return false;
        return deductFare(user.id(), (int) Math.round(fareBdt * 100), "RIDE", "Ride Fare Deduction");
    }

    public synchronized boolean deductFare(String userId, double fareBdt) {
        return deductFare(userId, (int) Math.round(fareBdt * 100), "RIDE", "Ride Fare Deduction");
    }

    public synchronized boolean chargeOverdueFine(String userId, int amountPoisha, String rentalId) {
        if (userId == null || userId.isBlank() || amountPoisha <= 0) {
            return false;
        }
        ensureInitialized(userId);

        int currentBalance = balanceCache.getOrDefault(userId, 0);
        int newBalance = Math.max(0, currentBalance - amountPoisha);

        String reference = (rentalId != null && !rentalId.isBlank()) ? rentalId : "FINE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String desc = "Overdue fine for rental " + reference;

        WalletTransaction tx = new WalletTransaction(
                "WT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                userId,
                -amountPoisha,
                TransactionType.OVERDUE_FINE.name(),
                newBalance,
                ZonedDateTime.now(),
                desc,
                reference
        );

        localDatabase.saveWalletTransaction(tx);
        balanceCache.put(userId, newBalance);
        transactionCache.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(0, tx);

        notifyListeners(userId, newBalance / 100.0);
        return true;
    }

    public List<WalletTransaction> getTransactions(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        ensureInitialized(userId);
        List<WalletTransaction> list = transactionCache.get(userId);
        return list != null ? Collections.unmodifiableList(new ArrayList<>(list)) : List.of();
    }

    public synchronized void clearCache() {
        balanceCache.clear();
        transactionCache.clear();
        initializedUsers.clear();
    }

    public String formatBalance(double amount) {
        return String.format("৳ %.2f", amount);
    }
}
