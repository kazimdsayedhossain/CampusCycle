package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.MaintenanceTicket;
import bd.ac.kuet.campuscycle.domain.TicketStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing cycle damage reports, maintenance lifecycles,
 * technician resolutions, and fleet redeployment.
 */
public class MaintenanceService {

    private static MaintenanceService instance;
    private final LocalDatabase localDatabase;
    private final CampusRepository repository;

    public static synchronized MaintenanceService getInstance() {
        if (instance == null) {
            instance = new MaintenanceService(LocalDatabase.getInstance(), null);
        }
        return instance;
    }

    public MaintenanceService() {
        this(LocalDatabase.getInstance(), null);
    }

    public MaintenanceService(LocalDatabase localDatabase) {
        this(localDatabase, null);
    }

    public MaintenanceService(LocalDatabase localDatabase, CampusRepository repository) {
        this.localDatabase = Objects.requireNonNull(localDatabase, "LocalDatabase must not be null");
        this.repository = repository;
    }

    public MaintenanceTicket reportDamage(String cycleId, CampusUser user, String issueCategory, String description) {
        return reportDamage(cycleId, user != null ? user.id() : "unknown", issueCategory, description);
    }

    public MaintenanceTicket reportDamage(String cycleId, String userId, String issueCategory, String description) {
        Objects.requireNonNull(cycleId, "Cycle ID cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        String ticketId = "TICK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String category = (issueCategory != null && !issueCategory.isBlank()) ? issueCategory.trim() : "ROUTINE_CHECKUP";
        String desc = (description != null && !description.isBlank()) ? description.trim() : "Damage reported";

        MaintenanceTicket ticket = new MaintenanceTicket(
                ticketId,
                cycleId,
                userId,
                category,
                desc,
                TicketStatus.OPEN.name(),
                ZonedDateTime.now(),
                null,
                null,
                0
        );

        localDatabase.saveMaintenanceTicket(ticket);
        localDatabase.updateCycleAvailability(cycleId, AvailabilityStatus.MAINTENANCE);

        return ticket;
    }

    public List<MaintenanceTicket> getOpenTickets() {
        return localDatabase.getOpenMaintenanceTickets();
    }

    public List<MaintenanceTicket> getAllTickets() {
        return localDatabase.getAllMaintenanceTickets();
    }

    public boolean resolveTicket(String ticketId, String technicianNotes, double repairCostBdt, CampusRepository repo) {
        int poisha = (int) Math.round(repairCostBdt * 100);
        boolean ok = resolveTicket(ticketId, technicianNotes, poisha, "");
        if (ok && repo != null) {
            Optional<MaintenanceTicket> opt = localDatabase.getMaintenanceTicketById(ticketId);
            opt.ifPresent(t -> {
                repo.setCycleAvailability(t.cycleId(), bd.ac.kuet.campuscycle.domain.AvailabilityStatus.AVAILABLE);
                bd.ac.kuet.campuscycle.data.EventBus.getInstance().publish(
                        new bd.ac.kuet.campuscycle.domain.event.CycleStatusChangedEvent(t.cycleId(), bd.ac.kuet.campuscycle.domain.AvailabilityStatus.AVAILABLE)
                );
            });
        }
        return ok;
    }

    public boolean resolveTicket(String ticketId, String technicianNotes, int repairCostPoisha, String redeployLocation) {
        if (ticketId == null || ticketId.isBlank()) {
            return false;
        }

        Optional<MaintenanceTicket> opt = localDatabase.getMaintenanceTicketById(ticketId);
        if (opt.isEmpty()) {
            return false;
        }

        MaintenanceTicket ticket = opt.get();
        if (ticket.isResolved()) {
            return false;
        }

        MaintenanceTicket resolved = ticket.withResolved(
                ZonedDateTime.now(),
                technicianNotes != null ? technicianNotes : "Repairs completed",
                Math.max(0, repairCostPoisha)
        );

        localDatabase.saveMaintenanceTicket(resolved);
        localDatabase.updateCycleAvailability(ticket.cycleId(), AvailabilityStatus.AVAILABLE);

        if (redeployLocation != null && !redeployLocation.isBlank()) {
            localDatabase.updateCycleLocation(ticket.cycleId(), redeployLocation.trim());
        }

        return true;
    }
}
