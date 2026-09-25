package bd.ac.kuet.campuscycle.domain;

import java.util.Objects;

public record CampusUser(String id, String displayName, String email, Role role) {
    public CampusUser {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(email);
        Objects.requireNonNull(role);
    }
}
