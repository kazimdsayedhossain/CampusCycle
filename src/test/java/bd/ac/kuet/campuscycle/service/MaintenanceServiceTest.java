package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MaintenanceServiceTest {

    private MaintenanceService maintenanceService;
    private LocalDatabase db;
    private CycleItem testCycle;
    private CampusUser testUser;

    @BeforeEach
    void setUp() {
        db = LocalDatabase.getInstance();
        maintenanceService = MaintenanceService.getInstance();

        testUser = new CampusUser("user-" + UUID.randomUUID(), "Test Reporter", "reporter@kuet.ac.bd", Role.STUDENT);
        db.saveProfile(testUser);

        testCycle = new CycleItem(
                "cycle-" + UUID.randomUUID(),
                testUser.id(),
                testUser.displayName(),
                "Maintenance Test Bike",
                CycleType.CITY_BIKE,
                CycleCondition.GOOD,
                "KUET Central Library",
                22.9009,
                89.5016,
                "Cycle for damage reporting test",
                ReviewStatus.APPROVED,
                AvailabilityStatus.AVAILABLE
        );
        db.saveCycle(testCycle);
    }

    @Test
    void testDamageReportAndResolutionLifecycle() {
        // 1. Report damage
        MaintenanceTicket ticket = maintenanceService.reportDamage(
                testCycle.id(),
                testUser.id(),
                IssueCategory.FLAT_TIRE.name(),
                "Front tire punctured near Central Mosque"
        );

        assertNotNull(ticket);
        assertEquals("OPEN", ticket.status());
        assertEquals(IssueCategory.FLAT_TIRE.name(), ticket.issueCategory());

        // Cycle status in local DB should become MAINTENANCE
        Optional<CycleItem> cycleAfterReport = db.getCycleById(testCycle.id());
        assertTrue(cycleAfterReport.isPresent());
        assertEquals(AvailabilityStatus.MAINTENANCE, cycleAfterReport.get().availabilityStatus());

        // 2. Open tickets query
        List<MaintenanceTicket> openTickets = maintenanceService.getOpenTickets();
        assertTrue(openTickets.stream().anyMatch(t -> t.id().equals(ticket.id())));

        // 3. Resolve ticket
        boolean resolvedOk = maintenanceService.resolveTicket(
                ticket.id(),
                "Replaced inner tube with durable heavy-duty tube.",
                250, // 250 poisha / ৳2.50
                "KUET Main Gate" // Redeploy location
        );
        assertTrue(resolvedOk);

        // Cycle status in local DB should become AVAILABLE and pickup location updated
        Optional<CycleItem> cycleAfterResolve = db.getCycleById(testCycle.id());
        assertTrue(cycleAfterResolve.isPresent());
        assertEquals(AvailabilityStatus.AVAILABLE, cycleAfterResolve.get().availabilityStatus());
        assertEquals("KUET Main Gate", cycleAfterResolve.get().pickupPoint());

        // Ticket status should be RESOLVED
        Optional<MaintenanceTicket> ticketInDb = db.getMaintenanceTicketById(ticket.id());
        assertTrue(ticketInDb.isPresent());
        assertEquals("RESOLVED", ticketInDb.get().status());
        assertEquals(250, ticketInDb.get().repairCostPoisha());
        assertNotNull(ticketInDb.get().resolvedAt());
    }
}
