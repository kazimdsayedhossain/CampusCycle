package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.RentalStatus;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.application.Platform;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production-ready Passbook & Transit Ledger View.
 * Implements:
 * 1. JavaFX TableView showcase with sortable columns and dynamic cell rendering
 * 2. Advanced Controls: DatePicker, ComboBox, RadioButton, ToggleGroup, ListView, ContextMenu, Tooltip
 * 3. Asynchronous non-blocking concurrency via AppExecutor
 * 4. Reactive filtering by date, status, search keyword, and subsidy classification
 * 5. Dynamic Electronic Receipt generation tied to TableView selection
 */
public class PassbookView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;

    private final ObservableList<RentalRecord> masterList = FXCollections.observableArrayList();
    private final ObservableList<RentalRecord> filteredList = FXCollections.observableArrayList();
    private final TableView<RentalRecord> tableView = new TableView<>();

    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilterCombo = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker();
    private final RadioButton rbAll = new RadioButton("All Transits");
    private final RadioButton rbSubsidized = new RadioButton("Subsidized Only (25%)");

    private final Label completedVal = new Label("0");
    private final Label distanceVal = new Label("0.0 km");
    private final Label subsidyVal = new Label("BDT 0.00");

    private final ProgressIndicator loadingSpinner = new ProgressIndicator();
    private final Label countLabel = new Label("Loading commutes...");

    // Receipt display labels
    private final Label receiptCycleVal = new Label("—");
    private final Label receiptDateVal = new Label("—");
    private final Label receiptFareVal = new Label("—");
    private final Label receiptStatusVal = new Label("—");
    private final Label receiptRentalIdVal = new Label("—");

    // Audit Trail
    private final ListView<String> auditListView = new ListView<>();

    public PassbookView(CampusUser user, CampusRepository repo) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = repo;

        setSpacing(24);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        HBox header = createHeader();
        GridPane summary = createSummaryBento();
        VBox filterBar = createFilterToolbar();
        VBox tableSection = createTableSection();
        HBox bottomSection = createBottomSection();

        getChildren().addAll(header, summary, filterBar, tableSection, bottomSection);
        ThemeManager.applyFadeIn(this);

        loadDataAsync();
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Transit Passbook & Receipts");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label sub = new Label("Official record of completed university bicycle commutes and subsidized fares");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportBtn = new Button("Export Statement (CSV)");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_CHECK, 13, Color.web("#2EB5A4")));
        exportBtn.setTooltip(new Tooltip("Generate KUET Green Mobility transit statement (CSV)"));
        exportBtn.setOnAction(e -> exportStatementCsv(exportBtn));

        row.getChildren().addAll(titleCol, spacer, exportBtn);
        return row;
    }

    private GridPane createSummaryBento() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        VBox c1 = createMetricCard("COMPLETED RIDES", completedVal, "Verified handovers", ThemeManager.ICON_BIKE, "#2EB5A4");
        VBox c2 = createMetricCard("TOTAL DISTANCE", distanceVal, "Zero-emission travel", ThemeManager.ICON_PIN, "#2EB5A4");
        VBox c3 = createMetricCard("CAMPUS SUBSIDY SAVED", subsidyVal, "25% KUET Green Mobility", ThemeManager.ICON_SHIELD, "#2EB5A4");

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

    private VBox createMetricCard(String label, Label valueLabel, String sub, String svgIcon, String accentHex) {
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
        container.setPadding(new Insets(16, 20, 16, 20));

        HBox row1 = new HBox(14);
        row1.setAlignment(Pos.CENTER_LEFT);

        // Search Field
        searchField.setPromptText("Search cycle name or rental ID...");
        searchField.getStyleClass().add("modern-input");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Status ComboBox
        statusFilterCombo.getItems().setAll("All Statuses", "COMPLETED (RETURNED)", "ACTIVE", "CANCELLED");
        statusFilterCombo.setValue("All Statuses");
        statusFilterCombo.getStyleClass().add("filter-chip");
        statusFilterCombo.setOnAction(e -> applyFilters());

        // DatePicker
        datePicker.setPromptText("Filter from Date");
        datePicker.getStyleClass().add("date-picker");
        datePicker.setPrefWidth(160);
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Reset Date Button
        Button clearDateBtn = new Button("Clear Date");
        clearDateBtn.getStyleClass().add("secondary-button");
        clearDateBtn.setOnAction(e -> {
            datePicker.setValue(null);
            applyFilters();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // RadioButtons for view mode
        ToggleGroup tg = new ToggleGroup();
        rbAll.setToggleGroup(tg);
        rbSubsidized.setToggleGroup(tg);
        rbAll.setSelected(true);
        rbAll.getStyleClass().add("radio-button");
        rbSubsidized.getStyleClass().add("radio-button");

        tg.selectedToggleProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        HBox radioGroup = new HBox(12, rbAll, rbSubsidized);
        radioGroup.setAlignment(Pos.CENTER_LEFT);

        row1.getChildren().addAll(searchField, statusFilterCombo, datePicker, clearDateBtn, spacer, radioGroup);
        container.getChildren().add(row1);
        return container;
    }

    private VBox createTableSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(20));

        HBox tableTop = new HBox(12);
        tableTop.setAlignment(Pos.CENTER_LEFT);

        Label secTitle = new Label("Commute Records Ledger");
        secTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        loadingSpinner.setPrefSize(18, 18);
        loadingSpinner.setVisible(false);

        countLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");

        tableTop.getChildren().addAll(secTitle, sp, loadingSpinner, countLabel);

        configureTableView();

        card.getChildren().addAll(tableTop, tableView);
        return card;
    }

    private void configureTableView() {
        tableView.setItems(filteredList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPrefHeight(280);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a");

        // 1. Rental ID
        TableColumn<RentalRecord, String> idCol = new TableColumn<>("RENTAL ID");
        idCol.setPrefWidth(110);
        idCol.setCellValueFactory(data -> {
            String id = data.getValue().id();
            String display = id.length() > 8 ? "#" + id.substring(0, 8) : "#" + id;
            return new SimpleStringProperty(display);
        });

        // 2. Cycle Label
        TableColumn<RentalRecord, String> cycleCol = new TableColumn<>("CYCLE MODEL");
        cycleCol.setPrefWidth(220);
        cycleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cycleLabel()));

        // 3. Start Time
        TableColumn<RentalRecord, String> timeCol = new TableColumn<>("COMMUTE START");
        timeCol.setPrefWidth(190);
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().startedAt().format(dtf)));

        // 4. Duration
        TableColumn<RentalRecord, String> durCol = new TableColumn<>("DURATION");
        durCol.setPrefWidth(110);
        durCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().requestedMinutes() + " mins"));

        // 5. Tariff
        TableColumn<RentalRecord, String> fareCol = new TableColumn<>("TARIFF (BDT)");
        fareCol.setPrefWidth(120);
        fareCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("BDT %.2f", data.getValue().quotedAmountPoisha() / 100.0)));

        // 6. Status with Styled Badge
        TableColumn<RentalRecord, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setPrefWidth(130);
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().name()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    if ("ACTIVE".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-available");
                    } else if ("RETURNED".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-electric");
                    } else {
                        badge.getStyleClass().add("filter-chip");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        tableView.getColumns().setAll(List.of(idCol, cycleCol, timeCol, durCol, fareCol, statusCol));

        // Row Selection Listener -> Updates Electronic Receipt
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                updateReceiptDetails(newSel);
            }
        });

        // ContextMenu for Right-click Operations
        ContextMenu cm = new ContextMenu();
        MenuItem viewReceiptItem = new MenuItem("View Electronic Receipt");
        viewReceiptItem.setOnAction(e -> {
            RentalRecord sel = tableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                updateReceiptDetails(sel);
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Receipt loaded below for Rental #" + sel.id(), ButtonType.OK);
                a.showAndWait();
            }
        });

        MenuItem copyIdItem = new MenuItem("Copy Rental UUID");
        copyIdItem.setOnAction(e -> {
            RentalRecord sel = tableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(sel.id());
                clipboard.setContent(content);
            }
        });

        MenuItem disputeItem = new MenuItem("Report Fare Dispute");
        disputeItem.setOnAction(e -> {
            RentalRecord sel = tableView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                openDisputeDialog(sel);
            }
        });

        cm.getItems().addAll(viewReceiptItem, copyIdItem, new SeparatorMenuItem(), disputeItem);
        tableView.setContextMenu(cm);
    }

    private HBox createBottomSection() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.TOP_LEFT);

        VBox receiptCard = createDigitalReceipt();
        VBox auditCard = createAuditTrailSection();

        HBox.setHgrow(receiptCard, Priority.ALWAYS);
        HBox.setHgrow(auditCard, Priority.ALWAYS);

        row.getChildren().addAll(receiptCard, auditCard);
        return row;
    }

    private VBox createDigitalReceipt() {
        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(20));

        Label title = new Label("University Electronic Receipt Voucher");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        VBox ticket = new VBox(10);
        ticket.getStyleClass().add("sub-panel");
        ticket.setPadding(new Insets(16));

        Label uniHeader = new Label("KHULNA UNIVERSITY OF ENGINEERING & TECHNOLOGY (KUET)");
        uniHeader.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 800; -fx-opacity: 0.6; -fx-letter-spacing: 0.08em;");

        Label receiptTitle = new Label("CampusCycle Official Audit Voucher");
        receiptTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        Separator sep = new Separator();

        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(16);
        metaGrid.setVgap(10);

        metaGrid.add(createReceiptMeta("RIDER NAME", user.displayName()), 0, 0);
        metaGrid.add(createReceiptMeta("STUDENT ID / EMAIL", user.email()), 1, 0);
        metaGrid.add(createReceiptMeta("CYCLE MODEL", receiptCycleVal), 0, 1);
        metaGrid.add(createReceiptMeta("COMMUTE DATE", receiptDateVal), 1, 1);
        metaGrid.add(createReceiptMeta("SETTLED FARE", receiptFareVal), 0, 2);
        metaGrid.add(createReceiptMeta("RENTAL STATUS", receiptStatusVal), 1, 2);
        metaGrid.add(createReceiptMeta("RENTAL ID", receiptRentalIdVal), 0, 3);
        metaGrid.add(createReceiptMeta("PAYMENT", "UNPAID (no collection in pilot)"), 1, 3);

        ticket.getChildren().addAll(uniHeader, receiptTitle, sep, metaGrid);
        card.getChildren().addAll(title, ticket);
        return card;
    }

    private VBox createReceiptMeta(String label, String value) {
        return createReceiptMeta(label, new Label(value));
    }

    private VBox createReceiptMeta(String label, Label valueLabel) {
        VBox b = new VBox(2);
        Label l = new Label(label);
        l.getStyleClass().add("metric-label");
        l.setStyle("-fx-font-size: 9px;");

        valueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        b.getChildren().addAll(l, valueLabel);
        return b;
    }

    private VBox createAuditTrailSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(20));

        Label title = new Label("Live Audit & Subsidy Event Ledger");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        auditListView.setPrefHeight(170);
        auditListView.getStyleClass().add("list-view");

        card.getChildren().addAll(title, auditListView);
        return card;
    }

    private void updateReceiptDetails(RentalRecord record) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a");
        receiptCycleVal.setText(record.cycleLabel());
        receiptDateVal.setText(record.startedAt().format(dtf));
        receiptFareVal.setText(String.format("BDT %.2f", record.quotedAmountPoisha() / 100.0));
        receiptStatusVal.setText(record.status().name());
        receiptRentalIdVal.setText(record.id());
    }

    private void openDisputeDialog(RentalRecord record) {
        if (record.status() != RentalStatus.RETURNED) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Only RETURNED rentals can be disputed.", ButtonType.OK);
            a.showAndWait();
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Report Fare Dispute");
        dialog.setHeaderText("Rental " + record.id());
        dialog.setContentText("Reason (10-2000 chars):");
        dialog.showAndWait().ifPresent(reason -> {
            if (reason.trim().length() < 10) {
                Alert a = new Alert(Alert.AlertType.WARNING, "Reason must be at least 10 characters.", ButtonType.OK);
                a.showAndWait();
                return;
            }
            AppExecutor.asyncThenFx(
                    () -> repo.openDispute(user, record.id(), reason.trim()),
                    disputeId -> {
                        Alert a = new Alert(Alert.AlertType.INFORMATION,
                                "Dispute " + disputeId + " opened. Cycle Office will review.", ButtonType.OK);
                        a.showAndWait();
                        loadDataAsync();
                    },
                    err -> {
                        Alert a = new Alert(Alert.AlertType.ERROR, "Dispute failed. Please retry.", ButtonType.OK);
                        a.showAndWait();
                    });
        });
    }

    private void exportStatementCsv(Button exportBtn) {
        exportBtn.setDisable(true);
        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        String safeUser = user.email().split("@")[0].replaceAll("[^a-zA-Z0-9]", "_");
                        java.nio.file.Path out = java.nio.file.Path.of(
                                System.getProperty("user.home"), "Downloads", "KUET_Passbook_" + safeUser + ".csv");
                        StringBuilder sb = new StringBuilder("rental_id,cycle,started_at,minutes,amount_bdt,status\n");
                        for (RentalRecord r : masterList) {
                            sb.append(String.format("%s,%s,%s,%d,%.2f,%s%n",
                                    r.id(), r.cycleLabel().replace(",", " "),
                                    r.startedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                    r.requestedMinutes(), r.quotedAmountPoisha() / 100.0, r.status().name()));
                        }
                        java.nio.file.Files.writeString(out, sb.toString(),
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                        return out.toString();
                    } catch (Exception ex) {
                        throw new RuntimeException("Export failed", ex);
                    }
                },
                path -> {
                    exportBtn.setDisable(false);
                    Alert a = new Alert(Alert.AlertType.INFORMATION, "Statement exported to " + path, ButtonType.OK);
                    a.showAndWait();
                },
                err -> {
                    exportBtn.setDisable(false);
                    Alert a = new Alert(Alert.AlertType.ERROR, "Export failed. Please retry.", ButtonType.OK);
                    a.showAndWait();
                });
    }

    private void loadDataAsync() {
        loadingSpinner.setVisible(true);
        countLabel.setText("Connecting to database...");

        AppExecutor.asyncThenFx(
                () -> {
                    // Multi-threaded background execution
                    List<RentalRecord> records;
                    try {
                        records = repo.rentals(user);
                    } catch (Exception e) {
                        // Fallback to SQLite local cache
                        records = LocalDatabase.getInstance().getRentalsByRenter(user.id());
                    }
                    return records;
                },
                records -> {
                    loadingSpinner.setVisible(false);
                    masterList.setAll(records);
                    applyFilters();

                    // Update Bento Metrics
                    long completed = records.stream().filter(r -> r.status() == RentalStatus.RETURNED).count();
                    completedVal.setText(completed + " Commutes");
                    distanceVal.setText(String.format("%.1f km", completed * 2.8));

                    long totalPoisha = records.stream().mapToLong(RentalRecord::quotedAmountPoisha).sum();
                    double subsidySaved = (totalPoisha * 0.25) / 100.0;
                    subsidyVal.setText(String.format("BDT %.2f", subsidySaved));

                    // Build Audit Ledger
                    ObservableList<String> auditLogs = FXCollections.observableArrayList();
                    for (RentalRecord r : records) {
                        auditLogs.add("• [" + r.startedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "] " +
                                r.cycleLabel() + " — BDT " + String.format("%.2f", r.quotedAmountPoisha() / 100.0) +
                                " [" + r.status().name() + "]");
                    }
                    if (auditLogs.isEmpty()) {
                        auditLogs.add("• No transactions recorded in local ledger yet.");
                    }
                    auditListView.setItems(auditLogs);

                    if (!records.isEmpty()) {
                        tableView.getSelectionModel().select(0);
                        updateReceiptDetails(records.get(0));
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
        boolean subsidizedOnly = rbSubsidized.isSelected();

        List<RentalRecord> filtered = masterList.stream().filter(r -> {
            // Search filter
            if (!search.isEmpty()) {
                boolean matchesCycle = r.cycleLabel().toLowerCase().contains(search);
                boolean matchesId = r.id().toLowerCase().contains(search);
                if (!matchesCycle && !matchesId) return false;
            }

            // Status filter
            if (selectedStatus != null && !selectedStatus.equals("All Statuses")) {
                if (selectedStatus.contains("RETURNED") && r.status() != RentalStatus.RETURNED) return false;
                if (selectedStatus.contains("ACTIVE") && r.status() != RentalStatus.ACTIVE) return false;
                if (selectedStatus.contains("CANCELLED") && r.status() != RentalStatus.CANCELLED) return false;
            }

            // Date filter: exact calendar day
            if (selectedDate != null) {
                LocalDate recordDate = r.startedAt().toLocalDate();
                if (!recordDate.isEqual(selectedDate)) {
                    return false;
                }
            }

            // Subsidized filter: completed RETURNED rides carry the applied subsidy
            if (subsidizedOnly && r.status() != RentalStatus.RETURNED) {
                return false;
            }

            return true;
        }).collect(Collectors.toList());

        filteredList.setAll(filtered);
        countLabel.setText("Showing " + filtered.size() + " of " + masterList.size() + " commutes");
    }
}
