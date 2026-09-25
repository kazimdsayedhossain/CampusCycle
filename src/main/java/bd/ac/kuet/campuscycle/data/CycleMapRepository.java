package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CycleMapPoint;

import java.util.List;

public interface CycleMapRepository {
    List<CycleMapPoint> availableCycleLocations();
}
