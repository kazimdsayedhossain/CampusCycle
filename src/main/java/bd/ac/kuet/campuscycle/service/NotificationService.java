package bd.ac.kuet.campuscycle.service;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for delivering system dispatch notifications (Email / Push / SMS)
 * to students and campus cyclists for registration approvals, rejections,
 * transit receipts, and overdue alerts.
 * Operates asynchronously through AppExecutor.
 *
 * <p>Note: no delivery transport is wired yet (no SMTP / Edge Function /
 * push provider). Until one exists every {@code send*} call logs
 * "notification not delivered: no transport" and completes with
 * {@code false} — callers must treat {@code false} as not delivered (P-063).
 */
public class NotificationService {

    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());
    private static NotificationService instance;

    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    private NotificationService() {}

    /**
     * Dispatches registration approval notification to the verified student.
     */
    public CompletableFuture<Boolean> sendApprovalEmail(String studentEmail, String studentName) {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.log(Level.WARNING,
                    "notification not delivered: no transport (approval email to {0}, name {1})",
                    new Object[]{studentEmail, studentName});
            return false;
        }, AppExecutor::execute);
    }

    /**
     * Dispatches registration rejection notification with technician reason.
     */
    public CompletableFuture<Boolean> sendRejectionEmail(String studentEmail, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.log(Level.WARNING,
                    "notification not delivered: no transport (rejection email to {0}, reason {1})",
                    new Object[]{studentEmail, reason});
            return false;
        }, AppExecutor::execute);
    }

    /**
     * Dispatches overdue fine & return reminder notification to the active renter.
     */
    public CompletableFuture<Boolean> sendOverdueAlert(String studentEmail, String cycleLabel, int overdueMinutes) {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.log(Level.WARNING,
                    "notification not delivered: no transport (overdue alert to {0}, cycle {1}, overdue {2}m)",
                    new Object[]{studentEmail, cycleLabel, overdueMinutes});
            return false;
        }, AppExecutor::execute);
    }
}
