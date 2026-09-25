package bd.ac.kuet.campuscycle.domain.event;

import java.time.Instant;

/**
 * Marker interface for all campus cycle domain events.
 */
public interface CampusCycleEvent {
    Instant timestamp();
}
