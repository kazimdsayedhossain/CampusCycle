package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ReportServiceTest {

    private ReportService reportService;
    private LocalDatabase db;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit already started
        }
    }

    @BeforeEach
    void setUp() {
        db = LocalDatabase.getInstance();
        reportService = ReportService.getInstance();

        // Seed some data in local DB using proper UUIDs
        CampusUser user = new CampusUser(UUID.randomUUID().toString(), "Report Tester", "rep@kuet.ac.bd", Role.STUDENT);
        db.saveProfile(user);

        CycleItem cycle = new CycleItem(
                UUID.randomUUID().toString(),
                user.id(),
                user.displayName(),
                "Report Cruiser",
                CycleType.ROAD_BIKE,
                CycleCondition.EXCELLENT,
                "Student Welfare Centre",
                22.9017,
                89.5030,
                "Cycle for report export verification",
                ReviewStatus.APPROVED,
                AvailabilityStatus.AVAILABLE
        );
        db.saveCycle(cycle);

        RentalRecord rental = new RentalRecord(
                "R-" + UUID.randomUUID().toString().substring(0, 8),
                cycle.id(),
                cycle.label(),
                user.id(),
                30,
                2000,
                RentalStatus.ACTIVE,
                ZonedDateTime.now(),
                ZonedDateTime.now().plusMinutes(30),
                null
        );
        db.saveRental(rental);

        MaintenanceTicket ticket = new MaintenanceTicket(
                "TICK-" + UUID.randomUUID().toString().substring(0, 8),
                cycle.id(),
                user.id(),
                IssueCategory.ROUTINE_CHECKUP.name(),
                "Routine inspection passed",
                TicketStatus.OPEN.name(),
                ZonedDateTime.now(),
                null,
                null,
                0
        );
        db.saveMaintenanceTicket(ticket);

        WalletTransaction tx = new WalletTransaction(
                "WT-" + UUID.randomUUID().toString().substring(0, 8),
                user.id(),
                2000,
                TransactionType.DEPOSIT.name(),
                2000,
                ZonedDateTime.now(),
                "Welcome Bonus",
                "REF-TEST"
        );
        db.saveWalletTransaction(tx);
    }

    @Test
    void testSynchronousReportExport() throws IOException {
        File targetFile = tempDir.resolve("AuditReportTest.csv").toFile();
        File exported = reportService.exportReportSync(targetFile);

        assertTrue(exported.exists());
        assertTrue(exported.length() > 0);

        String content = Files.readString(exported.toPath());
        assertTrue(content.contains("--- SECTION: FLEET CYCLES ---"));
        assertTrue(content.contains("--- SECTION: RENTAL RECORDS ---"));
        assertTrue(content.contains("--- SECTION: MAINTENANCE TICKETS ---"));
        assertTrue(content.contains("--- SECTION: WALLET TRANSACTIONS ---"));
        assertTrue(content.contains("Report Cruiser"));
    }

    @Test
    void testExportTaskCreationAndExecution() throws Exception {
        File targetFile = tempDir.resolve("TaskReportTest.csv").toFile();
        Task<File> task = reportService.createExportTask(targetFile);
        assertNotNull(task);

        CountDownLatch latch = new CountDownLatch(1);
        task.setOnSucceeded(e -> latch.countDown());
        task.setOnFailed(e -> {
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            latch.countDown();
        });

        AppExecutor.execute(task);
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Task should complete within 10 seconds");
        assertTrue(targetFile.exists(), "Target report file should exist on disk");
        assertTrue(targetFile.length() > 0, "Target report file should not be empty");
    }
}
