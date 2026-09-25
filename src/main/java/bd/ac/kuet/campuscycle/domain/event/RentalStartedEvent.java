package bd.ac.kuet.campuscycle.domain.event;

import bd.ac.kuet.campuscycle.domain.RentalRecord;
import java.time.Instant;

public record RentalStartedEvent(RentalRecord rental, Instant timestamp) implements CampusCycleEvent {
    public RentalStartedEvent(RentalRecord rental) {
        this(rental, Instant.now());
    }
}
