package bd.ac.kuet.campuscycle.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationServiceTest {

    @Test
    void testSendApprovalEmail() throws Exception {
        NotificationService service = NotificationService.getInstance();
        CompletableFuture<Boolean> future = service.sendApprovalEmail("student@kuet.ac.bd", "Test Student");
        Boolean result = future.get(5, TimeUnit.SECONDS);
        // Returns false when no email transport is configured (expected in test environment)
        assertFalse(result, "Approval email dispatch returns false without transport");
    }

    @Test
    void testSendRejectionEmail() throws Exception {
        NotificationService service = NotificationService.getInstance();
        CompletableFuture<Boolean> future = service.sendRejectionEmail("student@kuet.ac.bd", "Incomplete Student ID");
        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertFalse(result, "Rejection email dispatch returns false without transport");
    }

    @Test
    void testSendOverdueAlert() throws Exception {
        NotificationService service = NotificationService.getInstance();
        CompletableFuture<Boolean> future = service.sendOverdueAlert("cyclist@kuet.ac.bd", "Solar Cruiser E-02", 25);
        Boolean result = future.get(5, TimeUnit.SECONDS);
        assertFalse(result, "Overdue alert dispatch returns false without transport");
    }
}
