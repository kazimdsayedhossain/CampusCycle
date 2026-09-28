package bd.ac.kuet.campuscycle.service;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.DatabaseConnection;
import bd.ac.kuet.campuscycle.data.SupabaseCampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.MaintenanceTicket;
import bd.ac.kuet.campuscycle.domain.RentalDue;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.WalletTransaction;
import javafx.concurrent.Task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Service orchestrating background CSV export of fleet assets,
 * rental logs, maintenance tickets, and wallet transactions.
 * Uses Supabase as the single source of truth (no local DB).
 */
public class ReportService {

    private static ReportService instance;

    public static synchronized ReportService getInstance() {
        if (instance == null) {
            instance = new ReportService();
        }
        return instance;
    }

    private ReportService() {}

    /**
     * Resolves the default destination file on the user's Desktop (or home directory).
     */
    public static File getDefaultReportFile() {
        return getDefaultReportFile(null);
    }

    public static File getDefaultReportFile(CampusUser user) {
        String userHome = System.getProperty("user.home");
        File desktop = new File(userHome, "Desktop");
        File targetDir = (desktop.exists() && desktop.isDirectory()) ? desktop : new File(userHome);
        String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String namePart = (user != null && user.displayName() != null)
                ? "_" + user.displayName().replaceAll("[^a-zA-Z0-9]", "")
                : "";
        return new File(targetDir, "CampusCycle_Report" + namePart + "_" + timestamp + ".csv");
    }

    /**
     * Creates a JavaFX Task for exporting the CSV report with progress tracking.
     */
    public Task<File> createExportTask() {
        return createExportTask(getDefaultReportFile(null), null);
    }

    public Task<File> createExportTask(File destinationFile) {
        return createExportTask(destinationFile, null);
    }

    public Task<File> createExportTask(File destinationFile, CampusUser currentUser) {
        return new ExportReportTask(destinationFile, currentUser);
    }

    /**
     * Executes the report export asynchronously in the AppExecutor thread pool.
     */
    public CompletableFuture<File> exportReportAsync() {
        return exportReportAsync(getDefaultReportFile(null), null);
    }

    public CompletableFuture<File> exportReportAsync(File targetFile) {
        return exportReportAsync(targetFile, null);
    }

    public CompletableFuture<File> exportReportAsync(File targetFile, CampusUser currentUser) {
        Task<File> task = createExportTask(targetFile, currentUser);
        CompletableFuture<File> future = new CompletableFuture<>();

        task.setOnSucceeded(e -> future.complete(task.getValue()));
        task.setOnFailed(e -> future.completeExceptionally(task.getException()));
        task.setOnCancelled(e -> future.completeExceptionally(new CancellationException("Report export cancelled")));

        AppExecutor.execute(task);
        return future;
    }

    /**
     * Synchronously exports data to the target file.
     */
    public File exportReportSync(File targetFile) throws IOException {
        return exportReportSync(targetFile, null);
    }

    public File exportReportSync(File targetFile, CampusUser currentUser) throws IOException {
        File file = targetFile != null ? targetFile : getDefaultReportFile(currentUser);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        ReportData data = collect(currentUser);
        writeCsvReport(file, data.cycles(), data.rentals(), data.tickets(), data.transactions(), data.dues());
        return file;
    }

    /** Collected report rows with the caller's visibility applied. */
    private record ReportData(
            List<CycleItem> cycles,
            List<RentalRecord> rentals,
            List<MaintenanceTicket> tickets,
            List<WalletTransaction> transactions,
            List<RentalDue> dues) {}

    /**
     * Shared collection used by both the sync and async (Task) export paths.
     * Fail-closed: a {@code null} user is least-privileged, never admin (P-009).
     */
    private ReportData collect(CampusUser currentUser) {
        List<CycleItem> cycles = new ArrayList<>();
        List<RentalRecord> rentals = new ArrayList<>();
        List<MaintenanceTicket> tickets = new ArrayList<>();
        List<WalletTransaction> transactions = new ArrayList<>();
        List<RentalDue> dues = new ArrayList<>();

        boolean isAdmin = currentUser != null && currentUser.role() == Role.ADMIN;

        if (DatabaseConnection.isAvailable()) {
            try {
                CampusRepository repo = new SupabaseCampusRepository();
                cycles.addAll(repo.allCycles(null));
                if (isAdmin) {
                    rentals.addAll(repo.allRentals(currentUser));
                    tickets.addAll(MaintenanceService.getInstance().getAllTickets());
                    transactions.addAll(fetchAllLiveTransactions(null));
                    try {
                        dues.addAll(repo.allDues(currentUser));
                    } catch (Exception ignored) {}
                } else if (currentUser != null) {
                    rentals.addAll(repo.rentals(currentUser));
                    transactions.addAll(fetchAllLiveTransactions(currentUser.id()));
                }
            } catch (Exception e) {
                // Log but continue with empty lists
                java.util.logging.Logger.getLogger(ReportService.class.getName())
                        .warning("Failed to collect live data for report: " + e.getMessage());
            }
        }

        return new ReportData(cycles, rentals, tickets, transactions, dues);
    }

    /**
     * Fetches all wallet transactions from Supabase for the given user (or all if admin).
     */
    private List<WalletTransaction> fetchAllLiveTransactions(String userIdOrNull) {
        List<WalletTransaction> list = new ArrayList<>();
        String sql = userIdOrNull == null
                ? "SELECT id, user_id, amount_poisha, transaction_type, balance_after_poisha, timestamp, description, reference_code FROM public.wallet_transactions ORDER BY timestamp DESC"
                : "SELECT id, user_id, amount_poisha, transaction_type, balance_after_poisha, timestamp, description, reference_code FROM public.wallet_transactions WHERE user_id = ? ORDER BY timestamp DESC";

        if (!DatabaseConnection.isAvailable()) {
            return list;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (userIdOrNull != null) {
                stmt.setString(1, userIdOrNull);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new WalletTransaction(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getInt("amount_poisha"),
                            rs.getString("transaction_type"),
                            rs.getInt("balance_after_poisha"),
                            rs.getTimestamp("timestamp") != null
                                    ? rs.getTimestamp("timestamp").toInstant().atZone(ZoneId.of("Asia/Dhaka"))
                                    : ZonedDateTime.now(),
                            rs.getString("description"),
                            rs.getString("reference_code")
                    ));
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(ReportService.class.getName())
                    .warning("Failed to fetch wallet transactions for report: " + e.getMessage());
        }
        return list;
    }

    private void writeCsvReport(File file,
                                List<CycleItem> cycles,
                                List<RentalRecord> rentals,
                                List<MaintenanceTicket> tickets,
                                List<WalletTransaction> transactions,
                                List<RentalDue> dues) throws IOException {
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
            writer.write("RentalId,CycleId,CycleLabel,RenterId,RequestedMinutes,QuotedAmountPoisha,FinalAmountPoisha,PlatformFeePoisha,OwnerPayoutPoisha,Status,StartedAt,DueAt,ReturnedAt");
            writer.newLine();
            for (RentalRecord r : rentals) {
                writer.write(String.join(",",
                        escapeCsv(r.id()),
                        escapeCsv(r.cycleId()),
                        escapeCsv(r.cycleLabel()),
                        escapeCsv(r.renterId()),
                        escapeCsv(r.requestedMinutes()),
                        escapeCsv(r.quotedAmountPoisha()),
                        escapeCsv(r.finalAmountPoisha()),
                        escapeCsv(r.platformFeePoisha()),
                        escapeCsv(r.ownerPayoutPoisha()),
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
            writer.newLine();

            // SECTION 5: RIDE DUES (persistent overdue/overtime debt)
            writer.write("--- SECTION: RIDE DUES ---");
            writer.newLine();
            writer.write("DueId,RentalId,UserId,AmountPoisha,PaidPoisha,OutstandingPoisha,State,Reason,CreatedAt");
            writer.newLine();
            for (RentalDue d : dues) {
                writer.write(String.join(",",
                        escapeCsv(d.id()),
                        escapeCsv(d.rentalId()),
                        escapeCsv(d.userId()),
                        escapeCsv(d.amountPoisha()),
                        escapeCsv(d.paidPoisha()),
                        escapeCsv(d.outstandingPoisha()),
                        escapeCsv(d.state()),
                        escapeCsv(d.reason()),
                        escapeCsv(d.createdAt())
                ));
                writer.newLine();
            }
        }
    }

    private static String escapeCsv(Object val) {
        if (val == null) return "";
        String s = String.valueOf(val);
        // Neutralise formula injection before quoting (P-010): user-controlled
        // label / description / message cells starting with = + - @ TAB CR
        // would otherwise execute on open in Excel/Sheets.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
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
        private final CampusUser currentUser;

        public ExportReportTask(File destinationFile) {
            this(destinationFile, null);
        }

        public ExportReportTask(File destinationFile, CampusUser currentUser) {
            this.destinationFile = destinationFile;
            this.currentUser = currentUser;
        }

        @Override
        protected File call() throws Exception {
            if (isCancelled()) return null;
            updateProgress(0, 4);
            ReportData data = collect(currentUser);

            if (isCancelled()) return null;
            updateProgress(1, 4);

            File file = destinationFile != null ? destinationFile : getDefaultReportFile(currentUser);
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            if (isCancelled()) return null;
            updateProgress(2, 4);

            writeCsvReport(file, data.cycles(), data.rentals(), data.tickets(), data.transactions(), data.dues());

            if (isCancelled()) return null;
            updateProgress(4, 4);
            return file;
        }
    }
}
