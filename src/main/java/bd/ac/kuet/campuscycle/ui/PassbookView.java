package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.RentalStatus;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Consumer-Grade Ride History & Digital Receipts Ledger (PassbookView).
 * Features:
 * 1. KPI summary cards (Total Rides, Total Spent ৳, Loyalty Points earned)
 * 2. Multi-parameter filter toolbar (Keyword search, Status dropdown, DatePicker)
 * 3. Responsive TableView with custom columns and action button
 * 4. Interactive Digital Transit Receipt voucher with Copy & Save functionality
 * 5. Full receipt modal dialog on demand
 */
public class PassbookView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;

    private final ObservableList<RentalRecord> masterList = FXCollections.observableArrayList();
    private final ObservableList<RentalRecord> filteredList = FXCollections.observableArrayList();
    private final Map<String, String> cycleStationMap = new ConcurrentHashMap<>();

    private final TableView<RentalRecord> tableView = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilterCombo = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker();

    private final Label totalRidesLabel = new Label("0");
    private final Label totalSpentLabel = new Label("৳ 0.00");
    private final Label loyaltyPointsLabel = new Label("0 Pts");

    private final ProgressIndicator loadingSpinner = new ProgressIndicator();
    private final Label countLabel = new Label("Loading commutes...");

    // Side Drawer / Receipt Preview Container
    private final VBox receiptDrawerContainer = new VBox();
    private RentalRecord currentlySelectedRecord;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a");

    public PassbookView(CampusUser user, CampusRepository repo) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = repo;

        setSpacing(24);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        HBox header = createHeader();
        GridPane kpiSummary = createKpiSummary();
        VBox filterToolbar = createFilterToolbar();
        HBox mainContent = createMainContentArea();

        getChildren().addAll(header, kpiSummary, filterToolbar, mainContent);
        ThemeManager.applyFadeIn(this);

        loadDataAsync();
    }

    private HBox createHeader() {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(3);
        Label title = new Label("Ride History & Receipts");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label sub = new Label("View your past campus commutes and digital receipts");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportBtn = new Button("Export Statement (CSV)");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_CHECK, 13, Color.web("#10B981")));
        exportBtn.setOnAction(e -> exportStatementCsv(exportBtn));

        row.getChildren().addAll(titleCol, spacer, exportBtn);
        return row;
    }

    private GridPane createKpiSummary() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMaxWidth(Double.MAX_VALUE);

        VBox c1 = createKpiCard("TOTAL RIDES", totalRidesLabel, "Completed & Verified Commutes", ThemeManager.ICON_BIKE, "#10B981");
        VBox c2 = createKpiCard("TOTAL SPENT (৳)", totalSpentLabel, "Zero-Carbon Transit Investment", ThemeManager.ICON_SHIELD, "#0EA5E9");
        VBox c3 = createKpiCard("LOYALTY POINTS", loyaltyPointsLabel, "KUET Eco-Rider Reward Points", ThemeManager.ICON_BOLT, "#F59E0B");

        grid.add(c1, 0, 0);
        grid.add(c2, 1, 0);
        grid.add(c3, 2, 0);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(33.33);
            grid.getColumnConstraints().add(col);
        }

        return grid;
    }

    private VBox createKpiCard(String label, Label valueLabel, String sub, String svgIcon, String accentHex) {
        VBox card = new VBox(6);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(svgIcon, 14, Color.web(accentHex)));
        iconBadge.setPrefSize(28, 28);
        iconBadge.getStyleClass().add("action-icon-btn");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        valueLabel.getStyleClass().add("metric-number");

        Label badge = new Label(sub);
        badge.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, valueLabel, badge);
        return card;
    }

    private VBox createFilterToolbar() {
        VBox container = new VBox(12);
        container.getStyleClass().add("bento-card");
        container.setPadding(new Insets(14, 20, 14, 20));

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        // Search Field
        searchField.setPromptText("Search cycle name, station, or rental ID...");
        searchField.getStyleClass().add("modern-input");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Status ComboBox
        statusFilterCombo.getItems().setAll("All Statuses", "Completed", "Active", "Cancelled");
        statusFilterCombo.setValue("All Statuses");
        statusFilterCombo.getStyleClass().add("filter-chip");
        statusFilterCombo.setOnAction(e -> applyFilters());

        // DatePicker
        datePicker.setPromptText("Filter from Date");
        datePicker.getStyleClass().add("date-picker");
        datePicker.setPrefWidth(160);
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Clear Date Button
        Button clearDateBtn = new Button("Clear Date");
        clearDateBtn.getStyleClass().add("secondary-button");
        clearDateBtn.setOnAction(e -> {
            datePicker.setValue(null);
            applyFilters();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        loadingSpinner.setPrefSize(16, 16);
        loadingSpinner.setVisible(false);

        countLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75; -fx-font-weight: 600;");

        row.getChildren().addAll(searchField, statusFilterCombo, datePicker, clearDateBtn, spacer, loadingSpinner, countLabel);
        container.getChildren().add(row);
        return container;
    }

    private HBox createMainContentArea() {
        HBox mainRow = new HBox(18);
        mainRow.setAlignment(Pos.TOP_LEFT);
        mainRow.setMaxWidth(Double.MAX_VALUE);

        // Left Side: TableView Card
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("bento-card");
        tableCard.setPadding(new Insets(18));
        HBox.setHgrow(tableCard, Priority.ALWAYS);

        Label secTitle = new Label("Commute Records Ledger");
        secTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800;");

        configureTableView();
        tableCard.getChildren().addAll(secTitle, tableView);

        // Right Side: Digital Receipt Preview Drawer
        receiptDrawerContainer.setMinWidth(380);
        receiptDrawerContainer.setMaxWidth(380);
        receiptDrawerContainer.setAlignment(Pos.TOP_CENTER);
        renderEmptyReceiptDrawer();

        mainRow.getChildren().addAll(tableCard, receiptDrawerContainer);
        return mainRow;
    }

    private void configureTableView() {
        tableView.setItems(filteredList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPrefHeight(420);

        // 1. Rental ID
        TableColumn<RentalRecord, String> idCol = new TableColumn<>("RENTAL ID");
        idCol.setMinWidth(95);
        idCol.setPrefWidth(100);
        idCol.setCellValueFactory(data -> {
            String id = data.getValue().id();
            String display = id.length() > 8 ? "#" + id.substring(0, 8) : "#" + id;
            return new SimpleStringProperty(display);
        });

        // 2. Cycle Name
        TableColumn<RentalRecord, String> cycleCol = new TableColumn<>("CYCLE NAME");
        cycleCol.setMinWidth(140);
        cycleCol.setPrefWidth(160);
        cycleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cycleLabel()));

        // 3. Pickup Station
        TableColumn<RentalRecord, String> stationCol = new TableColumn<>("PICKUP STATION");
        stationCol.setMinWidth(130);
        stationCol.setPrefWidth(150);
        stationCol.setCellValueFactory(data -> {
            String cycleId = data.getValue().cycleId();
            String station = cycleStationMap.getOrDefault(cycleId, resolveDefaultStation(cycleId));
            return new SimpleStringProperty(station);
        });

        // 4. Date & Time
        TableColumn<RentalRecord, String> timeCol = new TableColumn<>("DATE & TIME");
        timeCol.setMinWidth(140);
        timeCol.setPrefWidth(160);
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().startedAt().format(DATE_TIME_FORMATTER)));

        // 5. Duration
        TableColumn<RentalRecord, String> durCol = new TableColumn<>("DURATION");
        durCol.setMinWidth(80);
        durCol.setPrefWidth(90);
        durCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().requestedMinutes() + " mins"));

        // 6. Fare (৳)
        TableColumn<RentalRecord, String> fareCol = new TableColumn<>("FARE (৳)");
        fareCol.setMinWidth(85);
        fareCol.setPrefWidth(95);
        fareCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("৳ %.2f", data.getValue().quotedAmountPoisha() / 100.0)));

        // 7. Status badge
        TableColumn<RentalRecord, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setMinWidth(100);
        statusCol.setPrefWidth(110);
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().name()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label();
                    if ("RETURNED".equalsIgnoreCase(item) || "COMPLETED".equalsIgnoreCase(item)) {
                        badge.setText("COMPLETED");
                        badge.getStyleClass().add("badge-available");
                    } else if ("ACTIVE".equalsIgnoreCase(item)) {
                        badge.setText("ACTIVE");
                        badge.getStyleClass().add("badge-electric");
                    } else {
                        badge.setText("CANCELLED");
                        badge.getStyleClass().add("filter-chip");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // 8. Action: View Receipt
        TableColumn<RentalRecord, Void> actionCol = new TableColumn<>("ACTION");
        actionCol.setMinWidth(105);
        actionCol.setPrefWidth(115);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View Receipt");
            {
                viewBtn.getStyleClass().add("secondary-button");
                viewBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px; -fx-font-weight: 700;");
                viewBtn.setOnAction(e -> {
                    RentalRecord item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        getTableView().getSelectionModel().select(item);
                        updateReceiptDrawer(item);
                        showReceiptModal(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(viewBtn);
                }
            }
        });

        tableView.getColumns().setAll(List.of(idCol, cycleCol, stationCol, timeCol, durCol, fareCol, statusCol, actionCol));

        // Update side drawer on selection
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                updateReceiptDrawer(newSel);
            }
        });
    }

    private String resolveDefaultStation(String cycleId) {
        if (cycleId == null) return "KUET Central Library";
        int idx = Math.abs(cycleId.hashCode()) % CampusHubs.names().size();
        return CampusHubs.names().get(idx);
    }

    private void renderEmptyReceiptDrawer() {
        receiptDrawerContainer.getChildren().clear();

        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(Double.MAX_VALUE);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_SHIELD, 24, Color.web("#10B981")));
        icon.setPrefSize(50, 50);
        icon.setStyle("-fx-background-color: -fx-teal-soft; -fx-background-radius: 999px;");

        Label prompt = new Label("Digital Receipt Preview");
        prompt.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        Label sub = new Label("Select any commute record from the table to inspect or export its official digital receipt.");
        sub.setWrapText(true);
        sub.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.7; -fx-text-alignment: center;");

        card.getChildren().addAll(icon, prompt, sub);
        receiptDrawerContainer.getChildren().add(card);
    }

    private void updateReceiptDrawer(RentalRecord record) {
        this.currentlySelectedRecord = record;
        receiptDrawerContainer.getChildren().clear();

        VBox voucherCard = createReceiptVoucherNode(record, false);
        receiptDrawerContainer.getChildren().add(voucherCard);
    }

    private VBox createReceiptVoucherNode(RentalRecord record, boolean isModal) {
        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(20));
        card.setMaxWidth(Double.MAX_VALUE);

        // Header Title
        HBox topTitle = new HBox(8);
        topTitle.setAlignment(Pos.CENTER_LEFT);

        Label t = new Label("KUET CampusCycle Transit Receipt");
        t.setStyle("-fx-font-size: 15px; -fx-font-weight: 800;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label badge = new Label(record.status() == RentalStatus.RETURNED ? "PAID & VERIFIED" : record.status().name());
        badge.getStyleClass().add(record.status() == RentalStatus.RETURNED ? "badge-available" : "filter-chip");

        topTitle.getChildren().addAll(t, sp, badge);

        // Voucher Ticket Box
        VBox ticket = new VBox(10);
        ticket.getStyleClass().add("sub-panel");
        ticket.setPadding(new Insets(14, 16, 14, 16));

        Label uniHeader = new Label("KHULNA UNIVERSITY OF ENGINEERING & TECHNOLOGY (KUET)");
        uniHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-opacity: 0.65; -fx-letter-spacing: 0.06em;");

        Label transitSub = new Label("Official Campus Green Mobility Electronic Transit Voucher");
        transitSub.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700;");

        Separator sep1 = new Separator();

        String pickup = cycleStationMap.getOrDefault(record.cycleId(), resolveDefaultStation(record.cycleId()));
        String dropoff = "Student Welfare Centre / Campus Dock";

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(14);
        metaGrid.setVgap(8);

        metaGrid.add(createMetaRow("RIDER NAME", user.displayName()), 0, 0);
        metaGrid.add(createMetaRow("STUDENT ID / EMAIL", user.email()), 1, 0);
        metaGrid.add(createMetaRow("CYCLE MODEL", record.cycleLabel()), 0, 1);
        metaGrid.add(createMetaRow("RENTAL ID", "#" + record.id()), 1, 1);
        metaGrid.add(createMetaRow("COMMUTE ROUTE", pickup + " → " + dropoff), 0, 2, 2, 1);
        metaGrid.add(createMetaRow("COMMUTE DATE", record.startedAt().format(DATE_TIME_FORMATTER)), 0, 3);
        metaGrid.add(createMetaRow("DURATION", record.requestedMinutes() + " mins"), 1, 3);
        metaGrid.add(createMetaRow("TOTAL FARE", String.format("৳ %.2f (BDT)", record.quotedAmountPoisha() / 100.0)), 0, 4);
        metaGrid.add(createMetaRow("PAYMENT METHOD", "Campus Digital Wallet (Auto-settled)"), 1, 4);

        Separator sep2 = new Separator();

        Label footerNote = new Label("Zero-Emission University Commute • Thank you for keeping KUET green!");
        footerNote.setStyle("-fx-font-size: 10.5px; -fx-opacity: 0.75; -fx-font-style: italic;");

        ticket.getChildren().addAll(uniHeader, transitSub, sep1, metaGrid, sep2, footerNote);

        // Action Buttons Row (Copy Receipt & Save Receipt)
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button copyBtn = new Button("Copy Receipt");
        copyBtn.getStyleClass().add("secondary-button");
        copyBtn.setOnAction(e -> copyReceiptText(record, pickup, dropoff));

        Button saveBtn = new Button("Save Receipt");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> saveReceiptToFile(record, pickup, dropoff));

        buttonBar.getChildren().addAll(copyBtn, saveBtn);

        card.getChildren().addAll(topTitle, ticket, buttonBar);
        return card;
    }

    private VBox createMetaRow(String label, String value) {
        VBox b = new VBox(2);
        Label l = new Label(label);
        l.getStyleClass().add("metric-label");
        l.setStyle("-fx-font-size: 9px;");

        Label v = new Label(value);
        v.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        v.setWrapText(true);
        b.getChildren().addAll(l, v);
        return b;
    }

    private void showReceiptModal(RentalRecord record) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("KUET CampusCycle — Digital Receipt");
        DialogPane pane = dialog.getDialogPane();
        ThemeManager.install(pane);
        pane.getStyleClass().add("modal-sheet");
        pane.setMinWidth(480);

        VBox voucher = createReceiptVoucherNode(record, true);
        pane.setContent(voucher);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private String generateReceiptText(RentalRecord record, String pickup, String dropoff) {
        String shortId = record.id().length() > 8 ? record.id().substring(0, 8) : record.id();
        return String.format("""
            ====================================================
                       KUET CAMPUSCYCLE TRANSIT RECEIPT
                 Khulna University of Engineering & Technology
            ====================================================
            Rental ID:       #%s
            Rider Name:      %s
            Email / ID:      %s
            Cycle Model:     %s
            Departure Hub:   %s
            Destination Hub: %s
            Date & Time:     %s
            Duration:        %d mins
            Total Fare:      ৳ %.2f (BDT)
            Payment Method:  Campus Digital Wallet (Auto-settled)
            Status:          %s
            ====================================================
            Zero-emission transit • Thank you for keeping KUET green!
            ====================================================
            """,
                shortId,
                user.displayName(),
                user.email(),
                record.cycleLabel(),
                pickup,
                dropoff,
                record.startedAt().format(DATE_TIME_FORMATTER),
                record.requestedMinutes(),
                record.quotedAmountPoisha() / 100.0,
                record.status() == RentalStatus.RETURNED ? "COMPLETED & VERIFIED" : record.status().name()
        );
    }

    private void copyReceiptText(RentalRecord record, String pickup, String dropoff) {
        String text = generateReceiptText(record, pickup, dropoff);
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Receipt copied to clipboard successfully!", ButtonType.OK);
        alert.showAndWait();
    }

    private void saveReceiptToFile(RentalRecord record, String pickup, String dropoff) {
        String safeId = record.id().replaceAll("[^a-zA-Z0-9]", "_");
        Path downloadDir = Path.of(System.getProperty("user.home"), "Downloads");
        Path out = downloadDir.resolve("KUET_Receipt_" + safeId + ".txt");

        try {
            if (!Files.exists(downloadDir)) {
                Files.createDirectories(downloadDir);
            }
            String receiptContent = generateReceiptText(record, pickup, dropoff);
            Files.writeString(out, receiptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Receipt saved to " + out.toAbsolutePath(), ButtonType.OK);
            alert.showAndWait();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save receipt: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    private void exportStatementCsv(Button exportBtn) {
        exportBtn.setDisable(true);
        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        String safeUser = user.email().split("@")[0].replaceAll("[^a-zA-Z0-9]", "_");
                        Path downloadDir = Path.of(System.getProperty("user.home"), "Downloads");
                        if (!Files.exists(downloadDir)) Files.createDirectories(downloadDir);

                        Path out = downloadDir.resolve("KUET_Passbook_" + safeUser + ".csv");
                        StringBuilder sb = new StringBuilder("rental_id,cycle,pickup_station,started_at,minutes,amount_bdt,status\n");
                        for (RentalRecord r : masterList) {
                            String station = cycleStationMap.getOrDefault(r.cycleId(), resolveDefaultStation(r.cycleId()));
                            sb.append(String.format("%s,%s,%s,%s,%d,%.2f,%s%n",
                                    r.id(),
                                    r.cycleLabel().replace(",", " "),
                                    station.replace(",", " "),
                                    r.startedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                    r.requestedMinutes(),
                                    r.quotedAmountPoisha() / 100.0,
                                    r.status().name()));
                        }
                        Files.writeString(out, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        return out.toString();
                    } catch (Exception ex) {
                        throw new RuntimeException("Export failed", ex);
                    }
                },
                path -> {
                    exportBtn.setDisable(false);
                    Alert a = new Alert(Alert.AlertType.INFORMATION, "Transit statement exported to " + path, ButtonType.OK);
                    a.showAndWait();
                },
                err -> {
                    exportBtn.setDisable(false);
                    Alert a = new Alert(Alert.AlertType.ERROR, "Export failed. Please retry.", ButtonType.OK);
                    a.showAndWait();
                }
        );
    }

    private void loadDataAsync() {
        loadingSpinner.setVisible(true);
        countLabel.setText("Connecting to database...");

        AppExecutor.asyncThenFx(
                () -> {
                    List<RentalRecord> records;
                    try {
                        records = repo.rentals(user);
                    } catch (Exception e) {
                        records = LocalDatabase.getInstance().getRentalsByRenter(user.id());
                    }

                    // Populate cycle -> station mapping
                    try {
                        for (CycleItem c : repo.catalog(user)) {
                            if (c.pickupPoint() != null) {
                                cycleStationMap.put(c.id(), c.pickupPoint());
                            }
                        }
                    } catch (Exception ignored) {}

                    try {
                        for (CycleItem c : LocalDatabase.getInstance().getAvailableCycles()) {
                            if (c.pickupPoint() != null) {
                                cycleStationMap.put(c.id(), c.pickupPoint());
                            }
                        }
                    } catch (Exception ignored) {}

                    return records;
                },
                records -> {
                    loadingSpinner.setVisible(false);
                    masterList.setAll(records);
                    applyFilters();

                    // Update KPI Summary Metrics
                    long totalCount = records.size();
                    totalRidesLabel.setText(totalCount + (totalCount == 1 ? " Ride" : " Rides"));

                    long totalPoisha = records.stream()
                            .filter(r -> r.status() == RentalStatus.RETURNED || r.status() == RentalStatus.ACTIVE)
                            .mapToLong(RentalRecord::quotedAmountPoisha)
                            .sum();
                    totalSpentLabel.setText(String.format("৳ %.2f", totalPoisha / 100.0));

                    // Loyalty Points: 10 points per ride + 1 point per 5 BDT spent
                    long points = (totalCount * 10) + Math.round(totalPoisha / 500.0);
                    loyaltyPointsLabel.setText(points + " Pts");

                    if (!records.isEmpty()) {
                        tableView.getSelectionModel().select(0);
                        updateReceiptDrawer(records.get(0));
                    } else {
                        renderEmptyReceiptDrawer();
                    }
                },
                error -> {
                    loadingSpinner.setVisible(false);
                    countLabel.setText("Passbook is offline. Showing cached records.");
                }
        );
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedStatus = statusFilterCombo.getValue();
        LocalDate selectedDate = datePicker.getValue();

        List<RentalRecord> filtered = masterList.stream().filter(r -> {
            // Search filter
            if (!search.isEmpty()) {
                boolean matchesCycle = r.cycleLabel().toLowerCase().contains(search);
                boolean matchesId = r.id().toLowerCase().contains(search);
                String station = cycleStationMap.getOrDefault(r.cycleId(), resolveDefaultStation(r.cycleId())).toLowerCase();
                boolean matchesStation = station.contains(search);
                if (!matchesCycle && !matchesId && !matchesStation) return false;
            }

            // Status filter
            if (selectedStatus != null && !selectedStatus.equalsIgnoreCase("All Statuses")) {
                if (selectedStatus.equalsIgnoreCase("Completed") && r.status() != RentalStatus.RETURNED) return false;
                if (selectedStatus.equalsIgnoreCase("Active") && r.status() != RentalStatus.ACTIVE) return false;
                if (selectedStatus.equalsIgnoreCase("Cancelled") && r.status() != RentalStatus.CANCELLED) return false;
            }

            // Date filter: exact calendar day
            if (selectedDate != null) {
                LocalDate recordDate = r.startedAt().toLocalDate();
                if (!recordDate.isEqual(selectedDate)) {
                    return false;
                }
            }

            return true;
        }).collect(Collectors.toList());

        filteredList.setAll(filtered);
        countLabel.setText("Showing " + filtered.size() + " of " + masterList.size() + " commutes");
    }
}
