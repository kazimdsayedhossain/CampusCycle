package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.RentalStatus;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.animation.PauseTransition;
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
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final ScrollPane receiptScroll = new ScrollPane();
    private final Label drawerStatusLabel = new Label();
    private RentalRecord currentlySelectedRecord;
    private boolean receiptVisible = true;

    /** Debounces the search field so filtering runs ~150ms after the last keystroke (P-120). */
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(150));

    /** Cached hub names — CampusHubs.names() is already cached, keep one local ref (P-120). */
    private final List<String> hubNames = CampusHubs.names();

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yy");

    private static String cleanStationName(String station) {
        if (station == null || station.isBlank()) {
            return "Central Mosque";
        }
        String cleaned = station.replaceAll("(?i)^KUET\\s*[-–—:]?\\s*", "").trim();
        return cleaned.isEmpty() ? "Central Mosque" : cleaned;
    }

    public PassbookView(CampusUser user, CampusRepository repo) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = repo;

        setSpacing(24);
        setPadding(new Insets(6, 16, 24, 16));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-background-color: transparent;");

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

        Label sub = new Label("View your past campus commutes and digital receipts");
        sub.getStyleClass().add("view-subtitle");
        sub.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: -fx-ink-700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportBtn = new Button("Export Statement (CSV)");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_CHECK, 13, Color.web("#10B981")));
        exportBtn.setOnAction(e -> exportStatementCsv(exportBtn));

        row.getChildren().addAll(sub, spacer, exportBtn);
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
        iconBadge.getStyleClass().add("icon-badge");

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

        // Search Field (debounced ~150ms so typing never rebuilds per keystroke)
        searchField.setPromptText("Search cycle name, station, or rental ID...");
        searchField.getStyleClass().add("modern-input");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            searchDebounce.stop();
            searchDebounce.playFromStart();
        });
        searchDebounce.setOnFinished(e -> applyFilters());

        // Status ComboBox
        statusFilterCombo.getItems().setAll("All Statuses", "Completed", "Active", "Cancelled");
        statusFilterCombo.setValue("All Statuses");
        statusFilterCombo.getStyleClass().add("filter-select");
        statusFilterCombo.setOnAction(e -> applyFilters());

        // DatePicker (exact-day semantics)
        datePicker.setPromptText("Filter by date");
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

        loadingSpinner.setPrefSize(16, 16);
        loadingSpinner.setVisible(false);

        countLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75; -fx-font-weight: 600;");
        countLabel.setAlignment(Pos.CENTER_RIGHT);
        countLabel.setMaxWidth(Double.MAX_VALUE);

        // Spacer BEFORE the count label so it stays right-aligned and is clipped last (P-147).
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(searchField, statusFilterCombo, datePicker, clearDateBtn, loadingSpinner, spacer, countLabel);
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
        tableCard.setMinWidth(0);

        HBox tableHeader = new HBox(10);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        Label secTitle = new Label("Commute Records Ledger");
        secTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800;");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Button collapseBtn = new Button("Hide Receipt");
        collapseBtn.getStyleClass().add("secondary-button");
        collapseBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px; -fx-font-weight: 700;");
        collapseBtn.setOnAction(e -> {
            receiptVisible = !receiptVisible;
            receiptDrawerContainer.setVisible(receiptVisible);
            receiptDrawerContainer.setManaged(receiptVisible);
            collapseBtn.setText(receiptVisible ? "Hide Receipt" : "Show Receipt");
        });
        tableHeader.getChildren().addAll(secTitle, headerSpacer, collapseBtn);

        configureTableView();
        tableCard.getChildren().addAll(tableHeader, tableView);

        // Right Side: Digital Receipt Preview Drawer (collapsible; min 0 so the table keeps its minimum)
        receiptDrawerContainer.setMinWidth(0);
        receiptDrawerContainer.setMaxWidth(320);
        receiptDrawerContainer.setPrefWidth(320);
        receiptDrawerContainer.setAlignment(Pos.TOP_CENTER);
        receiptScroll.setFitToWidth(true);
        receiptScroll.setFitToHeight(false);
        receiptScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(receiptScroll, Priority.ALWAYS);
        receiptDrawerContainer.getChildren().add(receiptScroll);
        drawerStatusLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #059669;");
        drawerStatusLabel.setVisible(false);
        drawerStatusLabel.setManaged(false);
        renderEmptyReceiptDrawer();

        mainRow.getChildren().addAll(tableCard, receiptDrawerContainer);
        return mainRow;
    }

    private void configureTableView() {
        tableView.setItems(filteredList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPrefHeight(420);
        tableView.getStyleClass().add("passbook-table");

        // Empty vs broken are distinct states (P-139); the CTA clears filters.
        tableView.setPlaceholder(emptyPlaceholder());

        // 1. Rental ID (fixed max width so it is not squeezed first under CONSTRAINED_RESIZE)
        TableColumn<RentalRecord, String> idCol = new TableColumn<>("RENTAL ID");
        idCol.setMinWidth(75);
        idCol.setPrefWidth(82);
        idCol.setMaxWidth(110);
        idCol.setCellValueFactory(data -> {
            String id = data.getValue().id();
            String display = id.length() > 8 ? "#" + id.substring(0, 8) : "#" + id;
            return new SimpleStringProperty(display);
        });
        idCol.setCellFactory(col -> tooltipCell());

        // 2. Cycle Name
        TableColumn<RentalRecord, String> cycleCol = new TableColumn<>("CYCLE NAME");
        cycleCol.setMinWidth(115);
        cycleCol.setPrefWidth(125);
        cycleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cycleLabel()));
        cycleCol.setCellFactory(col -> tooltipCell());

        // 3. Pickup Station (Direct place name without KUET)
        TableColumn<RentalRecord, String> stationCol = new TableColumn<>("PICKUP STATION");
        stationCol.setMinWidth(120);
        stationCol.setPrefWidth(135);
        stationCol.setCellValueFactory(data ->
                new SimpleStringProperty(cleanStationName(stationFor(data.getValue().cycleId()))));
        stationCol.setCellFactory(col -> tooltipCell());

        // 4. Date (xx/xx/xx format, sorted newest-first on the real timestamp)
        TableColumn<RentalRecord, String> dateCol = new TableColumn<>("DATE");
        dateCol.setMinWidth(68);
        dateCol.setPrefWidth(75);
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().startedAt().format(DATE_FORMATTER)));
        dateCol.setComparator((a, b) -> {
            try {
                return LocalDate.parse(a, DATE_FORMATTER).compareTo(LocalDate.parse(b, DATE_FORMATTER));
            } catch (Exception e) {
                return String.valueOf(a).compareTo(String.valueOf(b));
            }
        });
        dateCol.setSortType(TableColumn.SortType.DESCENDING);
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    RentalRecord rec = getTableRow() == null ? null : getTableRow().getItem();
                    if (rec != null) {
                        setTooltip(new Tooltip(rec.startedAt().format(DATE_TIME_FORMATTER)));
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });

        // 5. Duration
        TableColumn<RentalRecord, String> durCol = new TableColumn<>("DURATION");
        durCol.setMinWidth(76);
        durCol.setPrefWidth(84);
        durCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().requestedMinutes() + " mins"));

        // 6. Fare (settled amount, matching the receipt)
        TableColumn<RentalRecord, String> fareCol = new TableColumn<>("FARE");
        fareCol.setMinWidth(60);
        fareCol.setPrefWidth(68);
        fareCol.setCellValueFactory(data ->
                new SimpleStringProperty(Money.formatTaka(data.getValue().effectiveFarePoisha())));

        // 7. Status badge
        TableColumn<RentalRecord, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setMinWidth(92);
        statusCol.setPrefWidth(98);
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().name()));
        statusCol.setSortType(TableColumn.SortType.ASCENDING);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label();
                    badge.setStyle("-fx-font-size: 10.5px; -fx-padding: 3px 8px; -fx-font-weight: 700;");
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
        actionCol.setMinWidth(104);
        actionCol.setPrefWidth(110);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View Receipt");
            {
                viewBtn.getStyleClass().add("secondary-button");
                viewBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px; -fx-font-weight: 700;");
                viewBtn.setOnAction(e -> {
                    RentalRecord item = getTableRow() == null ? null : getTableRow().getItem();
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

        tableView.getColumns().setAll(List.of(idCol, cycleCol, stationCol, dateCol, durCol, fareCol, statusCol, actionCol));
        tableView.getSortOrder().add(dateCol);

        // Update side drawer on selection
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                updateReceiptDrawer(newSel);
            }
        });
    }

    /** Default empty-state placeholder with a CTA (P-139). */
    private VBox emptyPlaceholder() {
        VBox emptyState = new VBox(10);
        emptyState.setAlignment(Pos.CENTER);
        Label emptyTitle = new Label("No commutes yet");
        emptyTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");
        Label emptySub = new Label("Your completed rides will appear here.");
        emptySub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.65;");
        Button clearFiltersBtn = new Button("Clear Filters");
        clearFiltersBtn.getStyleClass().add("secondary-button");
        clearFiltersBtn.setOnAction(e -> {
            searchField.clear();
            statusFilterCombo.setValue("All Statuses");
            datePicker.setValue(null);
            applyFilters();
        });
        emptyState.getChildren().addAll(emptyTitle, emptySub, clearFiltersBtn);
        return emptyState;
    }

    /** Tooltip cell factory for free-text columns so clipped values stay readable (P-138). */
    private TableCell<RentalRecord, String> tooltipCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    private String resolveDefaultStation(String cycleId) {
        if (cycleId == null) return "Central Mosque";
        int idx = Math.abs(cycleId.hashCode()) % hubNames.size();
        return cleanStationName(hubNames.get(idx));
    }

    /** Pre-resolved station lookup — avoids per-row work during filtering (P-120). */
    private String stationFor(String cycleId) {
        String cached = cycleStationMap.get(cycleId);
        if (cached != null) {
            return cached;
        }
        String resolved = resolveDefaultStation(cycleId);
        if (cycleId != null) {
            cycleStationMap.putIfAbsent(cycleId, resolved);
        }
        return resolved;
    }

    private void renderEmptyReceiptDrawer() {
        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.image.ImageView lakebridgeArt = BikeArt.bannerView("campus-lakebridge.png", 300, 110);
        if (lakebridgeArt != null) {
            card.getChildren().add(lakebridgeArt);
        } else {
            StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_SHIELD, 24, Color.web("#10B981")));
            icon.setPrefSize(50, 50);
            icon.setStyle("-fx-background-color: -fx-teal-soft; -fx-background-radius: 999px;");
            card.getChildren().add(icon);
        }

        Label prompt = new Label("Digital Receipt Preview");
        prompt.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        Label sub = new Label("Select any commute record from the table to inspect or export its official digital receipt.");
        sub.setWrapText(true);
        sub.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.7; -fx-text-alignment: center;");

        card.getChildren().addAll(prompt, sub);
        receiptScroll.setContent(card);
    }

    private void updateReceiptDrawer(RentalRecord record) {
        this.currentlySelectedRecord = record;

        VBox voucherCard = createReceiptVoucherNode(record, false);
        receiptScroll.setContent(voucherCard);
    }

    private VBox createReceiptVoucherNode(RentalRecord record, boolean isModal) {
        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(20));
        card.setMaxWidth(Double.MAX_VALUE);

        // Header Title
        HBox topTitle = new HBox(8);
        topTitle.setAlignment(Pos.CENTER_LEFT);

        Label t = new Label("Transit Receipt");
        t.setStyle("-fx-font-size: 15px; -fx-font-weight: 800;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label badge = new Label(record.status() == RentalStatus.RETURNED ? "PAID & VERIFIED" : record.status().name());
        badge.getStyleClass().add(record.status() == RentalStatus.RETURNED ? "badge-available" : "filter-chip");
        badge.setMinWidth(Region.USE_PREF_SIZE);

        topTitle.getChildren().addAll(t, sp, badge);

        // Voucher Ticket Box
        VBox ticket = new VBox(10);
        ticket.getStyleClass().add("sub-panel");
        ticket.setPadding(new Insets(14, 16, 14, 16));

        Label uniHeader = new Label("CAMPUSCYCLE COMMUTE VOUCHER");
        uniHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-opacity: 0.65; -fx-letter-spacing: 0.06em;");

        Label transitSub = new Label("Campus Green Mobility Electronic Transit Voucher");
        transitSub.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700;");

        Separator sep1 = new Separator();

        String pickup = cleanStationName(stationFor(record.cycleId()));
        String dropoff = record.dropoffHub() == null || record.dropoffHub().isBlank()
                ? "Student Welfare Centre / Campus Dock"
                : cleanStationName(record.dropoffHub());

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(14);
        metaGrid.setVgap(8);

        List<VBox> metaRows = List.of(
                createMetaRow("RIDER NAME", user.displayName()),
                createMetaRow("STUDENT ID / EMAIL", user.email()),
                createMetaRow("CYCLE MODEL", record.cycleLabel()),
                createMetaRow("RENTAL ID", "#" + record.id()),
                createMetaRow("COMMUTE ROUTE", pickup + " → " + dropoff),
                createMetaRow("COMMUTE DATE", record.startedAt().format(DATE_TIME_FORMATTER)),
                createMetaRow("DURATION", record.requestedMinutes() + " mins"),
                createMetaRow("TOTAL FARE", Money.formatTaka(record.effectiveFarePoisha()) + " (BDT)"),
                createMetaRow("OVERDUE FINE", Money.formatTaka(Math.max(0, record.overdueFinePoisha())) + " (BDT)"),
                createMetaRow("TOTAL PAID", Money.formatTaka(record.totalPaidPoisha()) + " (BDT)"),
                createMetaRow("PAYMENT METHOD", "Campus Digital Wallet (Auto-settled)"));
        layoutMetaGrid(metaGrid, metaRows, false);
        // Single-column layout when the drawer is narrow so Copy/Save/Dispute stay reachable (B-35).
        card.widthProperty().addListener((obs, oldW, newW) ->
                layoutMetaGrid(metaGrid, metaRows, newW.doubleValue() > 0 && newW.doubleValue() < 300));

        ticket.getChildren().addAll(uniHeader, transitSub, sep1, metaGrid);

        // Action Buttons Row (Copy Receipt, Save Receipt, and Dispute) — wraps so nothing clips in the narrow drawer
        FlowPane buttonBar = new FlowPane(10, 10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button copyBtn = new Button("Copy Receipt");
        copyBtn.getStyleClass().add("secondary-button");
        copyBtn.setOnAction(e -> copyReceiptText(record, pickup, dropoff));

        Button saveBtn = new Button("Save Receipt");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> saveReceiptToFile(record, pickup, dropoff));

        buttonBar.getChildren().addAll(copyBtn, saveBtn);

        if (record.status() == RentalStatus.RETURNED) {
            Button disputeBtn = new Button("Dispute");
            disputeBtn.getStyleClass().add("secondary-button");
            disputeBtn.setStyle("-fx-text-fill: #EF4444; -fx-border-color: rgba(239, 68, 68, 0.35);");
            disputeBtn.setOnAction(e -> promptOpenDispute(record));
            buttonBar.getChildren().add(disputeBtn);
        }

        card.getChildren().addAll(topTitle, ticket, buttonBar);
        return card;
    }

    private void promptOpenDispute(RentalRecord record) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Dispute Ride Fare");
        dialog.setHeaderText("Dispute Ride #" + (record.id().length() > 8 ? record.id().substring(0, 8) : record.id()));
        dialog.setContentText("Explain the issue with this ride or fare calculation (min 10 characters):");

        DialogPane pane = dialog.getDialogPane();
        ThemeManager.install(pane);
        pane.getStyleClass().add("modal-sheet");

        dialog.showAndWait().ifPresent(reason -> {
            if (reason.trim().length() < 10) {
                Alert err = new Alert(Alert.AlertType.WARNING, "Dispute reason must be at least 10 characters.", ButtonType.OK);
                ThemeManager.install(err.getDialogPane());
                err.showAndWait();
                return;
            }
            AppExecutor.asyncThenFx(
                    () -> repo.openDispute(user, record.id(), reason.trim()),
                    disputeId -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                "Dispute submitted successfully!\nDispute Reference: " + disputeId + "\nOur team will review and contact you.",
                                ButtonType.OK);
                        ThemeManager.install(alert.getDialogPane());
                        alert.showAndWait();
                        loadDataAsync();
                    },
                    error -> {
                        Alert err = new Alert(Alert.AlertType.ERROR, "Failed to submit dispute: " + error.getMessage(), ButtonType.OK);
                        ThemeManager.install(err.getDialogPane());
                        err.showAndWait();
                    }
            );
        });
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

    /** Two-column meta layout, collapsing to a single column in narrow mode (B-35). */
    private void layoutMetaGrid(GridPane grid, List<VBox> rows, boolean singleColumn) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        if (singleColumn) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100);
            grid.getColumnConstraints().add(col);
            for (int i = 0; i < rows.size(); i++) {
                grid.add(rows.get(i), 0, i);
            }
            return;
        }
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            col.setMinWidth(0);
            grid.getColumnConstraints().add(col);
        }
        int row = 0;
        for (int i = 0; i < rows.size(); i++) {
            VBox node = rows.get(i);
            boolean fullWidth = "COMMUTE ROUTE".equals(((Label) node.getChildren().get(0)).getText());
            if (fullWidth) {
                grid.add(node, 0, row, 2, 1);
                row++;
            } else if (i + 1 < rows.size()
                    && !"COMMUTE ROUTE".equals(((Label) rows.get(i + 1).getChildren().get(0)).getText())) {
                grid.add(node, 0, row);
                grid.add(rows.get(i + 1), 1, row);
                row++;
                i++;
            } else {
                grid.add(node, 0, row, 2, 1);
                row++;
            }
        }
    }

    /** Transient inline confirmation inside the drawer — no modal alert (P-122). */
    private void flashDrawerStatus(String message) {
        drawerStatusLabel.setText(message);
        if (!receiptDrawerContainer.getChildren().contains(drawerStatusLabel)) {
            receiptDrawerContainer.getChildren().add(drawerStatusLabel);
        }
        drawerStatusLabel.setVisible(true);
        drawerStatusLabel.setManaged(true);
        PauseTransition hide = new PauseTransition(Duration.seconds(2.5));
        hide.setOnFinished(e -> {
            drawerStatusLabel.setVisible(false);
            drawerStatusLabel.setManaged(false);
        });
        hide.playFromStart();
    }

    private void showReceiptModal(RentalRecord record) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("CampusCycle — Digital Receipt");
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
        return String.format(java.util.Locale.US, """
            ====================================================
                        CAMPUSCYCLE TRANSIT RECEIPT
            ====================================================
            Rental ID:       #%s
            Rider Name:      %s
            Email / ID:      %s
            Cycle Model:     %s
            Departure Hub:   %s
            Destination Hub: %s
            Date & Time:     %s
            Duration:        %d mins
            Total Fare:      %s (BDT)
            Overdue Fine:    %s (BDT)
            Total Paid:      %s (BDT)
            Payment Method:  Campus Digital Wallet (Auto-settled)
            Status:          %s
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
                Money.formatTaka(record.effectiveFarePoisha()),
                Money.formatTaka(Math.max(0, record.overdueFinePoisha())),
                Money.formatTaka(record.totalPaidPoisha()),
                record.status() == RentalStatus.RETURNED ? "COMPLETED & VERIFIED" : record.status().name()
        );
    }

    private void copyReceiptText(RentalRecord record, String pickup, String dropoff) {
        String text = generateReceiptText(record, pickup, dropoff);
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);

        flashDrawerStatus("Receipt copied to clipboard.");
    }

    private void saveReceiptToFile(RentalRecord record, String pickup, String dropoff) {
        String safeId = record.id().replaceAll("[^a-zA-Z0-9]", "_");
        String receiptContent = generateReceiptText(record, pickup, dropoff);
        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        Path downloadDir = Path.of(System.getProperty("user.home"), "Downloads");
                        if (!Files.exists(downloadDir)) {
                            Files.createDirectories(downloadDir);
                        }
                        Path out = downloadDir.resolve("KUET_Receipt_" + safeId + ".txt");
                        Files.writeString(out, receiptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        return out.toAbsolutePath().toString();
                    } catch (Exception ex) {
                        throw new RuntimeException("Receipt save failed", ex);
                    }
                },
                path -> flashDrawerStatus("Receipt saved to " + path),
                err -> flashDrawerStatus("Could not save receipt — please retry.")
        );
    }

    private void exportStatementCsv(Button exportBtn) {
        exportBtn.setDisable(true);
        // Snapshot on the FX thread — ObservableList is not thread-safe (P-119).
        final List<RentalRecord> snapshot = List.copyOf(masterList);
        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        String safeUser = user.email().split("@")[0].replaceAll("[^a-zA-Z0-9]", "_");
                        Path downloadDir = Path.of(System.getProperty("user.home"), "Downloads");
                        if (!Files.exists(downloadDir)) Files.createDirectories(downloadDir);

                        Path out = downloadDir.resolve("KUET_Passbook_" + safeUser + ".csv");
                        StringBuilder sb = new StringBuilder("rental_id,cycle,pickup_station,started_at,minutes,amount_bdt,status\n");
                        for (RentalRecord r : snapshot) {
                            String station = cleanStationName(stationFor(r.cycleId()));
                            sb.append(String.format(java.util.Locale.US, "%s,%s,%s,%s,%d,%.2f,%s%n",
                                    r.id(),
                                    r.cycleLabel().replace(",", " "),
                                    station.replace(",", " "),
                                    r.startedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                    r.requestedMinutes(),
                                    r.effectiveFarePoisha() / 100.0,
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
                    // Fetch rentals and cycle catalog concurrently on parallel pooled connections
                    CompletableFuture<List<RentalRecord>> rentalsFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return repo.rentals(user);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to load commute records", e);
                        }
                    }, AppExecutor.io());

                    CompletableFuture<List<CycleItem>> cyclesFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return repo.allCycles(user);
                        } catch (Exception e) {
                            // Station names degrade to the deterministic fallback; rentals still load.
                            return List.<CycleItem>of();
                        }
                    }, AppExecutor.io());

                    List<RentalRecord> records = rentalsFuture.join();
                    List<CycleItem> cycles = cyclesFuture.join();

                    for (CycleItem c : cycles) {
                        if (c.pickupPoint() != null) {
                            cycleStationMap.put(c.id(), c.pickupPoint());
                        }
                    }
                    // Pre-resolve every rental's station once so filtering never does per-row work (P-120).
                    for (RentalRecord r : records) {
                        stationFor(r.cycleId());
                    }

                    return records;
                },
                records -> {
                    loadingSpinner.setVisible(false);
                    tableView.setPlaceholder(emptyPlaceholder());
                    masterList.setAll(records);
                    applyFilters();

                    // Update KPI Summary Metrics
                    long totalCount = records.size();
                    totalRidesLabel.setText(totalCount + (totalCount == 1 ? " Ride" : " Rides"));

                    long totalPoisha = records.stream()
                            .filter(r -> r.status() == RentalStatus.RETURNED || r.status() == RentalStatus.ACTIVE)
                            .mapToLong(RentalRecord::effectiveFarePoisha)
                            .sum();
                    totalSpentLabel.setText(Money.formatTaka((int) totalPoisha));

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
                    masterList.clear();
                    filteredList.clear();
                    renderEmptyReceiptDrawer();
                    VBox errorState = new VBox(10);
                    errorState.setAlignment(Pos.CENTER);
                    Label errorTitle = new Label("Could not load commute records.");
                    errorTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");
                    Label errorSub = new Label("Check your connection and try again.");
                    errorSub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.65;");
                    Button retryBtn = new Button("Retry");
                    retryBtn.getStyleClass().add("secondary-button");
                    retryBtn.setOnAction(e -> loadDataAsync());
                    errorState.getChildren().addAll(errorTitle, errorSub, retryBtn);
                    tableView.setPlaceholder(errorState);
                    countLabel.setText("Could not load commute records — Retry available.");
                }
        );
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedStatus = statusFilterCombo.getValue();
        LocalDate selectedDate = datePicker.getValue();

        List<RentalRecord> filtered = masterList.stream().filter(r -> {
            // Search filter (stations pre-resolved after load; no per-row work here)
            if (!search.isEmpty()) {
                boolean matchesCycle = r.cycleLabel().toLowerCase().contains(search);
                boolean matchesId = r.id().toLowerCase().contains(search);
                String station = cleanStationName(stationFor(r.cycleId())).toLowerCase();
                if (!matchesCycle && !matchesId && !station.contains(search)) return false;
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
