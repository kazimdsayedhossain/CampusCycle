package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;

import java.util.List;

public interface CampusRepository {
    List<CycleItem> catalog(CampusUser user);
    List<CycleItem> pendingCycles();
    List<RentalRecord> rentals(CampusUser user);
    RentalRecord activeRental(CampusUser user);
    RentalRecord book(CampusUser renter, String cycleId, int minutes);
    void returnRental(CampusUser renter, String rentalId);
    void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason);
    void rebalanceHub(String sourceHub, String targetHub, int count);
}
