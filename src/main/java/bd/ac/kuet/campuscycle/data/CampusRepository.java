package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.SupportConversation;
import bd.ac.kuet.campuscycle.domain.SupportMessage;

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

    /** Open a fare dispute for a RETURNED rental. Default unsupported for legacy impls. */
    default String openDispute(CampusUser renter, String rentalId, String reason) {
        throw new UnsupportedOperationException("Disputes are not supported by this repository.");
    }

    default String createSupportConversation(CampusUser student, String subject, String message) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default List<SupportConversation> supportConversations(CampusUser user) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default List<SupportMessage> supportMessages(CampusUser user, String conversationId) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default void postSupportMessage(CampusUser user, String conversationId, String body) {
        throw new UnsupportedOperationException("Support is not supported by this repository.");
    }

    default List<bd.ac.kuet.campuscycle.domain.DisputeItem> disputeQueue(CampusUser admin) {
        throw new UnsupportedOperationException("Disputes queue is not supported by this repository.");
    }
}
