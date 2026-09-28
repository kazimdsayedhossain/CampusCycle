package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Domain entity representing a student account registration submitted to the KUET Campus Office.
 */
public record UserRegistration(
        String id,
        String fullName,
        String studentRoll,
        String department,
        String email,
        String phone,
        String passwordHash,
        String verificationStatus,
        ZonedDateTime createdAt
) {
    public UserRegistration {
        Objects.requireNonNull(id);
        Objects.requireNonNull(fullName);
        Objects.requireNonNull(studentRoll);
        Objects.requireNonNull(department);
        Objects.requireNonNull(email);
        Objects.requireNonNull(phone);
        Objects.requireNonNull(passwordHash);
        Objects.requireNonNull(verificationStatus);
        Objects.requireNonNull(createdAt);
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(verificationStatus);
    }

    public boolean isPending() {
        return "PENDING_APPROVAL".equalsIgnoreCase(verificationStatus);
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(verificationStatus);
    }
}
