package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.MaintenanceTicket;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.WalletTransaction;
import javafx.concurrent.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service orchestrating background CSV export of fleet assets,
 * rental logs, maintenance tickets, and wallet transactions.
 */
public class ReportService {

    private static ReportService instance;
    private final LocalDatabase localDatabase;

    public static synchronized ReportService getInstance() {
        if (instance == null) {
            instance = new ReportService(LocalDatabase.getInstance());
        }
        return instance;
    }

    public ReportService() {
        this(LocalDatabase.getInstance());
    }

    public ReportService(LocalDatabase localDatabase) {
        this.localDatabase = Objects.requireNonNull(localDatabase, "LocalDatabase must not be null");
    }

    /**
     * Resolves the default destination file on the user's Desktop (or home directory).
     */
    public static File getDefaultReportFile() {
        String userHome = System.getProperty("user.home");
        File desktop = new File(userHome, "Desktop");
        File targetDir = (desktop.exists() && desktop.isDirectory()) ? desktop : new File(userHome);
        String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return new File(targetDir, "CampusCycle_Report_" + timestamp + ".csv");
    }

    /**
     * Creates a JavaFX Task for exporting the CSV report with progress tracking.
     */
    public Task<File> createExportTask() {
        return createExportTask(getDefaultReportFile());
    }

    /**
     * Creates a JavaFX Task for exporting the CSV report to a specific file.
     */
    public Task<File> createExportTask(File destinationFile) {
        return new ExportReportTask(destinationFile);
    }

    /**
     * Executes the report export asynchronously in the AppExecutor thread pool.
     */
    public CompletableFuture<File> exportReportAsync() {
        return exportReportAsync(getDefaultReportFile());
    }

    /**
     * Executes the report export asynchronously to the specified file in the AppExecutor thread pool.
     */
    public CompletableFuture<File> exportReportAsync(File targetFile) {
        Task<File> task = createExportTask(targetFile);
        CompletableFuture<File> future = new CompletableFuture<>();

        task.setOnSucceeded(e -> future.complete(task.getValue()));
        task.setOnFailed(e -> future.completeExceptionally(task.getException()));

        AppExecutor.execute(task);
        return future;
    }

    /**
     * Synchronously exports data to the target file.
     */
    public File exportReportSync(File targetFile) throws IOException {
        File file = targetFile != null ? targetFile : getDefaultReportFile();
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        List<CycleItem> cycles = localDatabase.getAllCycles();
        List<RentalRecord> rentals = localDatabase.getAllRentals();
        List<MaintenanceTicket> tickets = localDatabase.getAllMaintenanceTickets();
        List<WalletTransaction> transactions = localDatabase.getAllWalletTransactions();

        writeCsvReport(file, cycles, rentals, tickets, transactions);
        return file;
    }

    private void writeCsvReport(File file,
                                List<CycleItem> cycles,
                                List<RentalRecord> rentals,
                                List<MaintenanceTicket> tickets,
                                List<WalletTransaction> transactions) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write("# ==============================================================================");
            writer.newLine();
            writer.write("# CAMPUSCYCLE AUDIT & OPERATIONS REPORT");
            writer.newLine();
            writer.write("# Generated: " + ZonedDateTime.now().toString());
            writer.newLine();
            writer.write("# ==============================================================================");
            writer.newLine();
            writer.newLine();

            // SECTION 1: CYCLES
            writer.write("--- SECTION: FLEET CYCLES ---");
            writer.newLine();
            writer.write("CycleId,OwnerId,OwnerName,Label,Type,Condition,PickupPoint,Latitude,Longitude,ReviewStatus,AvailabilityStatus");
            writer.newLine();
            for (CycleItem c : cycles) {
                writer.write(String.join(",",
                        escapeCsv(c.id()),
                        escapeCsv(c.ownerId()),
                        escapeCsv(c.ownerName()),
                        escapeCsv(c.label()),
                        escapeCsv(c.type()),
                        escapeCsv(c.condition()),
                        escapeCsv(c.pickupPoint()),
                        escapeCsv(c.latitude()),
                        escapeCsv(c.longitude()),
                        escapeCsv(c.reviewStatus()),
                        escapeCsv(c.availabilityStatus())
                ));
                writer.newLine();
            }
            writer.newLine();

            // SECTION 2: RENTALS
            writer.write("--- SECTION: RENTAL RECORDS ---");
            writer.newLine();
            writer.write("RentalId,CycleId,CycleLabel,RenterId,RequestedMinutes,QuotedAmountPoisha,Status,StartedAt,DueAt,ReturnedAt");
            writer.newLine();
            for (RentalRecord r : rentals) {
                writer.write(String.join(",",
                        escapeCsv(r.id()),
                        escapeCsv(r.cycleId()),
                        escapeCsv(r.cycleLabel()),
                        escapeCsv(r.renterId()),
                        escapeCsv(r.requestedMinutes()),
                        escapeCsv(r.quotedAmountPoisha()),
                        escapeCsv(r.status()),
                        escapeCsv(r.startedAt()),
                        escapeCsv(r.dueAt()),
                        escapeCsv(r.returnedAt())
                ));
                writer.newLine();
            }
            writer.newLine();

            // SECTION 3: MAINTENANCE TICKETS
            writer.write("--- SECTION: MAINTENANCE TICKETS ---");
            writer.newLine();
            writer.write("TicketId,CycleId,ReportedByUserId,IssueCategory,Description,Status,ReportedAt,ResolvedAt,TechnicianNotes,RepairCostPoisha");
            writer.newLine();
            for (MaintenanceTicket t : tickets) {
                writer.write(String.join(",",
                        escapeCsv(t.id()),
                        escapeCsv(t.cycleId()),
                        escapeCsv(t.reportedByUserId()),
                        escapeCsv(t.issueCategory()),
                        escapeCsv(t.description()),
                        escapeCsv(t.status()),
                        escapeCsv(t.reportedAt()),
                        escapeCsv(t.resolvedAt()),
                        escapeCsv(t.technicianNotes()),
                        escapeCsv(t.repairCostPoisha())
                ));
                writer.newLine();
            }
            writer.newLine();

            // SECTION 4: WALLET TRANSACTIONS
            writer.write("--- SECTION: WALLET TRANSACTIONS ---");
            writer.newLine();
            writer.write("TransactionId,UserId,AmountPoisha,Type,BalanceAfterPoisha,Timestamp,Description,ReferenceCode");
            writer.newLine();
            for (WalletTransaction w : transactions) {
                writer.write(String.join(",",
                        escapeCsv(w.id()),
                        escapeCsv(w.userId()),
                        escapeCsv(w.amountPoisha()),
                        escapeCsv(w.type()),
                        escapeCsv(w.balanceAfterPoisha()),
                        escapeCsv(w.timestamp()),
                        escapeCsv(w.description()),
                        escapeCsv(w.referenceCode())
                ));
                writer.newLine();
            }
        }
    }

    private static String escapeCsv(Object val) {
        if (val == null) return "";
        String s = String.valueOf(val);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * JavaFX Task implementation with graceful headless handling for test runners.
     */
    public class ExportReportTask extends Task<File> {
        private final File destinationFile;

        public ExportReportTask(File destinationFile) {
            this.destinationFile = destinationFile != null ? destinationFile : getDefaultReportFile();
        }

        @Override
        protected File call() throws Exception {
            updateSafeMessage("Preparing report export...");
            updateSafeProgress(0, 5);

            if (destinationFile.getParentFile() != null && !destinationFile.getParentFile().exists()) {
                destinationFile.getParentFile().mkdirs();
            }

            updateSafeMessage("Exporting Fleet Cycles...");
            List<CycleItem> cycles = localDatabase.getAllCycles();
            updateSafeProgress(1, 5);

            updateSafeMessage("Exporting Rental Logs...");
            List<RentalRecord> rentals = localDatabase.getAllRentals();
            updateSafeProgress(2, 5);

            updateSafeMessage("Exporting Maintenance Tickets...");
            List<MaintenanceTicket> tickets = localDatabase.getAllMaintenanceTickets();
            updateSafeProgress(3, 5);

            updateSafeMessage("Exporting Wallet Transactions...");
            List<WalletTransaction> transactions = localDatabase.getAllWalletTransactions();
            updateSafeProgress(4, 5);

            updateSafeMessage("Writing CSV Report to disk...");
            writeCsvReport(destinationFile, cycles, rentals, tickets, transactions);
            updateSafeProgress(5, 5);

            updateSafeMessage("Export completed: " + destinationFile.getName());
            return destinationFile;
        }

        private void updateSafeProgress(long workDone, long max) {
            try {
                updateProgress(workDone, max);
            } catch (IllegalStateException ignored) {
                // Non-JavaFX headless test runner
            }
        }

        private void updateSafeMessage(String msg) {
            try {
                updateMessage(msg);
            } catch (IllegalStateException ignored) {
                // Non-JavaFX headless test runner
            }
        }
    }
}
