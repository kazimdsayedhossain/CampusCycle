package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class InMemoryCampusRepository implements CampusRepository {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private final List<CycleItem> cycles = new ArrayList<>();
    private final List<RentalRecord> rentals = new ArrayList<>();

    public InMemoryCampusRepository() {
        cycles.add(new CycleItem(
                "C-101", "owner-1", "Nusrat Jahan", "Blue commuter",
                CycleType.CITY_BIKE, CycleCondition.EXCELLENT, "KUET Central Library",
                22.9009, 89.5016, "Reliable campus commuter with front basket.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-102", "owner-2", "Rakib Hasan", "Road runner",
                CycleType.ROAD_BIKE, CycleCondition.GOOD, "Student Welfare Centre",
                22.9017, 89.5030, "Lightweight 21-speed alloy road bike.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-103", "owner-3", "Sadia Islam", "E-bike 01",
                CycleType.ELECTRIC_BIKE, CycleCondition.EXCELLENT, "KUET Main Gate",
                22.8987, 89.4981, "Assisted pedal electric cycle with fast battery.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-104", "owner-4", "Tanvir Ahmed", "Campus Glide",
                CycleType.CITY_BIKE, CycleCondition.EXCELLENT, "Hall Gate",
                22.9045, 89.5060, "Comfortable step-through commuter frame.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-105", "owner-5", "Mehedi Hasan", "Eco Cruiser",
                CycleType.ELECTRIC_BIKE, CycleCondition.GOOD, "Academic Building",
                22.9015, 89.5010, "Smart throttle e-bike with solar dock lock.",
                ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE
        ));
        cycles.add(new CycleItem(
                "C-106", "3d1e3d69-ffc6-494f-a42c-26eeb258b581", "Arafat Rahman", "Daily rider",
                CycleType.CITY_BIKE, CycleCondition.GOOD, "Hall Gate",
                22.9045, 89.5060, "Student listing awaiting physical inspection.",
                ReviewStatus.PENDING_REVIEW, AvailabilityStatus.AVAILABLE
        ));
    }

    @Override
    public synchronized List<CycleItem> catalog(CampusUser user) {
        return cycles.stream()
                .filter(c -> c.reviewStatus() == ReviewStatus.APPROVED)
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .filter(c -> user == null || !c.ownerId().equals(user.id()))
                .sorted(Comparator.comparing(CycleItem::label))
                .toList();
    }

    @Override
    public synchronized List<CycleItem> pendingCycles() {
        return cycles.stream()
                .filter(c -> c.reviewStatus() == ReviewStatus.PENDING_REVIEW)
                .toList();
    }

    @Override
    public synchronized List<RentalRecord> rentals(CampusUser user) {
        return rentals.stream()
                .filter(r -> r.renterId().equals(user.id()))
                .sorted(Comparator.comparing(RentalRecord::startedAt).reversed())
                .toList();
    }

    @Override
    public synchronized RentalRecord activeRental(CampusUser user) {
        return rentals.stream()
                .filter(r -> r.renterId().equals(user.id()) && r.status() == RentalStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    @Override
    public synchronized RentalRecord book(CampusUser renter, String cycleId, int minutes) {
        if (activeRental(renter) != null) {
            throw new IllegalStateException("Return your active cycle before starting another rental.");
        }
        int index = findCycle(cycleId);
        CycleItem cycle = cycles.get(index);
        if (!cycle.canBeBookedBy(renter.id())) {
            throw new IllegalStateException("This cycle is no longer available.");
        }

        ZonedDateTime now = ZonedDateTime.now(DHAKA);
        int totalPoisha = TariffService.quotePoisha(minutes);

        RentalRecord rental = new RentalRecord(
                "R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                cycle.id(),
                cycle.label(),
                renter.id(),
                minutes,
                totalPoisha,
                RentalStatus.ACTIVE,
                now,
                now.plusMinutes(minutes),
                null
        );

        rentals.add(rental);
        cycles.set(index, updateCycleAvailability(cycle, AvailabilityStatus.RENTED));
        return rental;
    }

    @Override
    public synchronized void returnRental(CampusUser renter, String rentalId) {
        int rIndex = findRental(rentalId);
        RentalRecord record = rentals.get(rIndex);
        if (!record.renterId().equals(renter.id()) || record.status() != RentalStatus.ACTIVE) {
            throw new IllegalStateException("Rental cannot be returned.");
        }

        ZonedDateTime now = ZonedDateTime.now(DHAKA);
        rentals.set(rIndex, new RentalRecord(
                record.id(),
                record.cycleId(),
                record.cycleLabel(),
                record.renterId(),
                record.requestedMinutes(),
                record.quotedAmountPoisha(),
                RentalStatus.RETURNED,
                record.startedAt(),
                record.dueAt(),
                now
        ));

        int cIndex = findCycle(record.cycleId());
        cycles.set(cIndex, updateCycleAvailability(cycles.get(cIndex), AvailabilityStatus.AVAILABLE));
    }

    @Override
    public synchronized void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason) {
        if (admin.role() != Role.ADMIN) {
            throw new SecurityException("Admin authorization required.");
        }
        int index = findCycle(cycleId);
        CycleItem cycle = cycles.get(index);
        if (cycle.reviewStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Cycle listing has already been reviewed.");
        }

        cycles.set(index, new CycleItem(
                cycle.id(),
                cycle.ownerId(),
                cycle.ownerName(),
                cycle.label(),
                cycle.type(),
                cycle.condition(),
                cycle.pickupPoint(),
                cycle.latitude(),
                cycle.longitude(),
                cycle.description(),
                approved ? ReviewStatus.APPROVED : ReviewStatus.REJECTED,
                cycle.availabilityStatus()
        ));
    }

    @Override
    public synchronized void rebalanceHub(String sourceHub, String targetHub, int count) {
        // Move available cycles from sourceHub to targetHub
        int moved = 0;
        for (int i = 0; i < cycles.size() && moved < count; i++) {
            CycleItem c = cycles.get(i);
            if (c.pickupPoint().equalsIgnoreCase(sourceHub) && c.availabilityStatus() == AvailabilityStatus.AVAILABLE) {
                cycles.set(i, new CycleItem(
                        c.id(), c.ownerId(), c.ownerName(), c.label(), c.type(), c.condition(),
                        targetHub, c.latitude(), c.longitude(), c.description(),
                        c.reviewStatus(), c.availabilityStatus()
                ));
                moved++;
            }
        }
    }

    @Override
    public synchronized String openDispute(CampusUser renter, String rentalId, String reason) {
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 2000) {
            throw new IllegalArgumentException("Dispute reason must be 10-2000 characters.");
        }
        int rIndex = findRental(rentalId);
        RentalRecord record = rentals.get(rIndex);
        if (!record.renterId().equals(renter.id())) {
            throw new SecurityException("Only the renter can dispute this rental.");
        }
        if (record.status() != RentalStatus.RETURNED) {
            throw new IllegalStateException("Only RETURNED rentals can be disputed.");
        }
        String disputeId = "D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        rentals.set(rIndex, new RentalRecord(
                record.id(), record.cycleId(), record.cycleLabel(), record.renterId(),
                record.requestedMinutes(), record.quotedAmountPoisha(), RentalStatus.DISPUTED,
                record.startedAt(), record.dueAt(), record.returnedAt()));
        disputes.add(0, new DisputeItem(disputeId, rentalId, reason.trim(), "OPEN"));
        return disputeId;
    }

    @Override
    public synchronized List<DisputeItem> disputeQueue(CampusUser admin) {
        if (admin.role() != Role.ADMIN) {
            throw new SecurityException("Admin authorization required.");
        }
        return List.copyOf(disputes);
    }

    private final List<SupportConversation> conversations = new ArrayList<>();
    private final java.util.Map<String, List<SupportMessage>> threads = new java.util.HashMap<>();
    private final List<DisputeItem> disputes = new ArrayList<>();

    @Override
    public synchronized String createSupportConversation(CampusUser student, String subject, String message) {
        if (subject == null || subject.trim().length() < 3 || subject.trim().length() > 160) {
            throw new IllegalArgumentException("Subject must be 3-160 characters.");
        }
        if (message == null || message.trim().isEmpty() || message.trim().length() > 2000) {
            throw new IllegalArgumentException("Message must be 1-2000 characters.");
        }
        String id = "S-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ZonedDateTime now = ZonedDateTime.now(DHAKA);
        conversations.add(0, new SupportConversation(id, subject.trim(), "OPEN", "", now));
        threads.put(id, new ArrayList<>(List.of(
                new SupportMessage("M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        student.displayName(), student.role().name(), message.trim(), now))));
        return id;
    }

    @Override
    public synchronized List<SupportConversation> supportConversations(CampusUser user) {
        return List.copyOf(conversations);
    }

    @Override
    public synchronized List<SupportMessage> supportMessages(CampusUser user, String conversationId) {
        return List.copyOf(threads.getOrDefault(conversationId, List.of()));
    }

    @Override
    public synchronized void postSupportMessage(CampusUser user, String conversationId, String body) {
        if (body == null || body.trim().isEmpty() || body.trim().length() > 2000) {
            throw new IllegalArgumentException("Message must be 1-2000 characters.");
        }
        List<SupportMessage> thread = threads.get(conversationId);
        if (thread == null) throw new IllegalArgumentException("Conversation not found.");
        thread.add(new SupportMessage("M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                user.displayName(), user.role().name(), body.trim(), ZonedDateTime.now(DHAKA)));
    }

    private int findCycle(String id) {
        for (int i = 0; i < cycles.size(); i++) {
            if (cycles.get(i).id().equals(id)) return i;
        }
        throw new IllegalArgumentException("Cycle not found: " + id);
    }

    private int findRental(String id) {
        for (int i = 0; i < rentals.size(); i++) {
            if (rentals.get(i).id().equals(id)) return i;
        }
        throw new IllegalArgumentException("Rental not found: " + id);
    }

    private CycleItem updateCycleAvailability(CycleItem c, AvailabilityStatus status) {
        return new CycleItem(
                c.id(), c.ownerId(), c.ownerName(), c.label(), c.type(), c.condition(),
                c.pickupPoint(), c.latitude(), c.longitude(), c.description(),
                c.reviewStatus(), status
        );
    }
}
