package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.InMemoryCampusRepository;
import bd.ac.kuet.campuscycle.domain.PasswordUtils;
import bd.ac.kuet.campuscycle.domain.UserRegistration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Live-DB integration test: runs only with CC_TEST_DB=true against a disposable database. Never the pilot.
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CC_TEST_DB", matches = "true")
public class WalletAndSecurityTest {

    @Test
    void testDepositLimitEnforced() {
        WalletService wallet = WalletService.getInstance();
        assertThrows(IllegalArgumentException.class, () -> {
            wallet.depositPoisha("test-user-id", WalletService.MAX_SINGLE_DEPOSIT_POISHA + 100, "bKash", null);
        });
    }

    @Test
    void testInMemoryChangePasswordFlow() {
        InMemoryCampusRepository repo = new InMemoryCampusRepository();
        String userId = UUID.randomUUID().toString();
        String email = "cyclist_" + UUID.randomUUID().toString().substring(0, 6) + "@kuet.ac.bd";
        String initialPassword = "oldSecretPassword123";
        String hashedInitial = PasswordUtils.hash(initialPassword);

        UserRegistration reg = new UserRegistration(
                userId,
                "Cyclist Name",
                "1907099",
                "CSE",
                email,
                "01711000000",
                hashedInitial,
                "APPROVED",
                ZonedDateTime.now()
        );
        repo.submitUserRegistration(reg, "InitialPass123!");

        // Fail wrong old password
        assertThrows(IllegalArgumentException.class, () -> {
            repo.changePassword(userId, "wrongPassword", "newSecretPassword456");
        });

        // Fail short new password (< 6 chars)
        assertThrows(IllegalArgumentException.class, () -> {
            repo.changePassword(userId, initialPassword, "123");
        });

        // Succeed with correct old password
        boolean changed = repo.changePassword(userId, initialPassword, "newSecretPassword456");
        assertTrue(changed, "Password change should succeed");

        // Verify updated password hash works with new password and fails with old
        UserRegistration updated = repo.getUserRegistration(userId).orElseThrow();
        assertTrue(PasswordUtils.verify("newSecretPassword456", updated.passwordHash()));
        assertFalse(PasswordUtils.verify(initialPassword, updated.passwordHash()));
    }
}
