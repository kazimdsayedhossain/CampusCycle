package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CycleMapPoint;
import bd.ac.kuet.campuscycle.domain.CycleRoute;
import bd.ac.kuet.campuscycle.domain.RoutePoint;

/** Retrieves a cycling route without passing renter or owner information to the routing provider. */
public interface CycleDirectionsRepository {
    CycleRoute cyclingRoute(RoutePoint origin, CycleMapPoint destination);
}
