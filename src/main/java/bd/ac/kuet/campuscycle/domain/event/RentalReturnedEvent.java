package bd.ac.kuet.campuscycle.domain.event;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import java.time.Instant;

public record RentalReturnedEvent(CampusUser user, String rentalId, Instant timestamp) implements CampusCycleEvent {
    public RentalReturnedEvent(CampusUser user, String rentalId) {
        this(user, rentalId, Instant.now());
    }
}
