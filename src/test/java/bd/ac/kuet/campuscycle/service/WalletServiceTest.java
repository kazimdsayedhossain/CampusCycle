package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.WalletTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WalletServiceTest {

    private WalletService walletService;
    private String testUserId;

    @BeforeEach
    void setUp() {
        walletService = WalletService.getInstance();
        walletService.clearCache();
        testUserId = "user-" + UUID.randomUUID();
    }

    @Test
    void testInitialWelcomeBalance() {
        // First query for user grants initial welcome balance of 2000 poisha (৳20.00)
        int balance = walletService.getBalancePoisha(testUserId);
        assertEquals(2000, balance);

        List<WalletTransaction> txs = walletService.getTransactions(testUserId);
        assertFalse(txs.isEmpty());
        assertEquals(2000, txs.get(0).balanceAfterPoisha());
        assertTrue(txs.get(0).isDeposit());
    }

    @Test
    void testDepositAndChargeLifecycle() {
        // Initial balance: 2000 poisha
        assertEquals(2000, walletService.getBalancePoisha(testUserId));

        // Deposit 1500 poisha (৳15.00)
        boolean depOk = walletService.deposit(testUserId, 1500, "bKash", "TX-12345");
        assertTrue(depOk);
        assertEquals(3500, walletService.getBalancePoisha(testUserId));

        // Charge 1000 poisha for rental
        boolean chargeOk = walletService.charge(testUserId, 1000, "Rental Fare 30m", "R-101");
        assertTrue(chargeOk);
        assertEquals(2500, walletService.getBalancePoisha(testUserId));

        // Overdue fine: 500 poisha
        boolean fineOk = walletService.chargeOverdueFine(testUserId, 500, "R-101");
        assertTrue(fineOk);
        assertEquals(2000, walletService.getBalancePoisha(testUserId));

        // Verify transaction history length
        List<WalletTransaction> history = walletService.getTransactions(testUserId);
        assertEquals(4, history.size()); // Welcome + Deposit + Charge + Fine
    }

    @Test
    void testInsufficientFundsCharge() {
        walletService.getBalancePoisha(testUserId); // Init 2000
        // Attempt to charge more than balance
        boolean ok = walletService.charge(testUserId, 5000, "Expensive Booking", "R-FAIL");
        assertFalse(ok, "Charge exceeding balance should be rejected");
        assertEquals(2000, walletService.getBalancePoisha(testUserId));
    }
}
