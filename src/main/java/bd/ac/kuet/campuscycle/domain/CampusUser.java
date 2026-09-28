package bd.ac.kuet.campuscycle.domain;

import java.util.Objects;

public record CampusUser(String id, String displayName, String email, Role role) {
    public CampusUser {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(email);
        Objects.requireNonNull(role);
    }

    public boolean hasRole(Role role) {
        return this.role == role;
    }

    /**
     * Throws SecurityException unless this user holds one of the allowed roles.
     * Callers must null-check the actor first; a null actor never passes.
     */
    public void requireRole(Role... allowed) {
        if (allowed == null || allowed.length == 0) {
            throw new SecurityException("Role check misconfigured: no allowed roles.");
        }
        for (Role r : allowed) {
            if (this.role == r) {
                return;
            }
        }
        throw new SecurityException("Role " + role + " is not permitted for this operation.");
    }
}
