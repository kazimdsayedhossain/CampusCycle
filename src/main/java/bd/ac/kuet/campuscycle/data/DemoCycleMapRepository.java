package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CycleMapPoint;

import java.util.List;

/** Local development data used only when a live Supabase project is not configured. */
public final class DemoCycleMapRepository implements CycleMapRepository {
    @Override
    public List<CycleMapPoint> availableCycleLocations() {
        return List.of(
                new CycleMapPoint("demo-1", "Blue commuter", "KUET Central Library", 22.9009, 89.5016),
                new CycleMapPoint("demo-2", "Road runner", "Student Welfare Centre", 22.9017, 89.5030),
                new CycleMapPoint("demo-3", "E-bike 01", "KUET Main Gate", 22.8987, 89.4981)
        );
    }
}
