package bd.ac.kuet.campuscycle.domain.event;

import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import java.time.Instant;

public record CycleStatusChangedEvent(String cycleId, AvailabilityStatus newStatus, Instant timestamp) implements CampusCycleEvent {
    public CycleStatusChangedEvent(String cycleId, AvailabilityStatus newStatus) {
        this(cycleId, newStatus, Instant.now());
    }
}
