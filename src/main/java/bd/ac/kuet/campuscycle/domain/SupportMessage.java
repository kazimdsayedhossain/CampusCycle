package bd.ac.kuet.campuscycle.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

public record SupportMessage(String id, String senderName, String senderRole, String body, ZonedDateTime createdAt) {
    public SupportMessage {
        Objects.requireNonNull(id);
        Objects.requireNonNull(body);
    }
}
