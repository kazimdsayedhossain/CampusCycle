package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

public record SupportConversation(String id, String subject, String state, String adminName, ZonedDateTime updatedAt) {
    public SupportConversation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(state);
    }
}
