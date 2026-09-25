package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.*;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.MaintenanceService;
import bd.ac.kuet.campuscycle.service.ReportService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.io.File;
import java.util.*;

/**
 * Production Admin Operations Console:
 * 1. Fleet Management: Master cycle inventory table, Add/Edit/Delete, and pending listings review
 * 2. Maintenance Tickets: Student damage reports and technician re-commissioning modal
 * 3. Station Rebalancing: Real-time hub inventory and cycle transfer dispatch
 * 4. System Audit Export: Multi-threaded background CSV generation with live ProgressBar
 */
public class AdminOperationsView extends VBox {

    private final CampusUser admin;
    private final CampusRepository repo;
    private final Runnable onRefresh;

    // Bento metric labels
    private final Label totalFleetVal = new Label("—");
    private final Label availableVal = new Label("—");
    private final Label inMaintenanceVal = new Label("—");
    private final Label activeHubsVal = new Label("5 Hubs");

    // Fleet Management Tab
    private final ObservableList<CycleItem> fleetList = FXCollections.observableArrayList();
    private final TableView<CycleItem> fleetTableView = new TableView<>();

    // Maintenance Tickets Tab
    private final ObservableList<MaintenanceTicket> ticketList = FXCollections.observableArrayList();
    private final TableView<MaintenanceTicket> ticketTableView = new TableView<>();

    // Station Rebalancing Tab
    private final Map<String, Label> hubCountLabels = new LinkedHashMap<>();

    public AdminOperationsView(CampusUser admin, CampusRepository repo, Runnable onRefresh) {
        ThemeManager.install(this);
        this.admin = admin;
        this.repo = repo;
        this.onRefresh = onRefresh;

        setSpacing(24);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        HBox header = createHeader();
        GridPane bento = createAdminBento();
        TabPane tabPane = createOperationalTabs();

        getChildren().addAll(header, bento, tabPane);
        ThemeManager.applyFadeIn(this);

        loadAllDataAsync();
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Campus Fleet Operations Console");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");

        Label sub = new Label("Real-time hub operations, fleet maintenance, and audit ledger");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75; -fx-text-fill: -fx-ink-700;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Data");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_REFRESH, 13, Color.web("#10B981")));
        refreshBtn.setOnAction(e -> loadAllDataAsync());

        row.getChildren().addAll(titleCol, spacer, refreshBtn);
        return row;
    }

    private GridPane createAdminBento() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        VBox c1 = createBentoCard("TOTAL FLEET", totalFleetVal, new Label("Live Tracked Units"), ThemeManager.ICON_BIKE, "#10B981");
        VBox c2 = createBentoCard("AVAILABLE", availableVal, new Label("Ready to Ride"), ThemeManager.ICON_CHECK, "#3B82F6");
        VBox c3 = createBentoCard("IN MAINTENANCE", inMaintenanceVal, new Label("Open Work Orders"), ThemeManager.ICON_ALERT, "#F59E0B");
        VBox c4 = createBentoCard("ACTIVE STATIONS", activeHubsVal, new Label("Campus Hub Network"), ThemeManager.ICON_PIN, "#10B981");

        grid.add(c1, 0, 0);
        grid.add(c2, 1, 0);
        grid.add(c3, 2, 0);
        grid.add(c4, 3, 0);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25.0);
            grid.getColumnConstraints().add(col);
        }

        return grid;
    }

    private VBox createBentoCard(String title, Label valLbl, Label subLbl, String svgIcon, String accentHex) {
        VBox card = new VBox(6);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(title);
        lbl.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(svgIcon, 14, Color.web(accentHex)));
        iconBadge.setPrefSize(28, 28);
        iconBadge.getStyleClass().add("action-icon-btn");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        valLbl.getStyleClass().add("metric-number");
        subLbl.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, valLbl, subLbl);
        return card;
    }

    private TabPane createOperationalTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-tab-min-width: 140px; -fx-tab-min-height: 36px;");

        Tab fleetTab = new Tab("Fleet Management", createFleetManagementTab());
        Tab maintenanceTab = new Tab("Maintenance Tickets", createMaintenanceTab());
        Tab rebalanceTab = new Tab("Station Rebalancing", createRebalanceTab());
        Tab auditTab = new Tab("System Audit Export", createAuditExportTab());

        tabPane.getTabs().addAll(fleetTab, maintenanceTab, rebalanceTab, auditTab);
        return tabPane;
    }

    // =========================================================================
    // TAB 1: FLEET MANAGEMENT
    // =========================================================================
    private VBox createFleetManagementTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 0, 0, 0));

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label heading = new Label("Campus Fleet Inventory");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("Live status of all cycles, hardware condition, and registered units");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("Add Cycle");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> showAddCycleModal());

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> {
            CycleItem selected = fleetTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditCycleModal(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "Selection Required", "Please select a cycle from the table to edit.");
            }
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> {
            CycleItem selected = fleetTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                confirmAndDeleteCycle(selected);
            } else {
                showAlert(Alert.AlertType.WARNING, "Selection Required", "Please select a cycle from the table to delete.");
            }
        });

        Button reviewBtn = new Button("Review Pending Listings");
        reviewBtn.getStyleClass().add("secondary-button");
        reviewBtn.setOnAction(e -> showPendingReviewModal());

        toolbar.getChildren().addAll(titleBox, spacer, addBtn, editBtn, deleteBtn, reviewBtn);

        // Configure TableView
        fleetTableView.setItems(fleetList);
        fleetTableView.setPrefHeight(340);
        fleetTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<CycleItem, String> labelCol = new TableColumn<>("Model / Label");
        labelCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().label()));
        labelCol.setPrefWidth(160);

        TableColumn<CycleItem, String> ownerCol = new TableColumn<>("Owner");
        ownerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ownerName()));
        ownerCol.setPrefWidth(130);

        TableColumn<CycleItem, String> hubCol = new TableColumn<>("Current Hub");
        hubCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pickupPoint()));
        hubCol.setPrefWidth(160);

        TableColumn<CycleItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type().name().replace("_", " ")));
        typeCol.setPrefWidth(110);

        TableColumn<CycleItem, String> condCol = new TableColumn<>("Condition");
        condCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().condition().name()));
        condCol.setPrefWidth(100);

        TableColumn<CycleItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().availabilityStatus().name()));
        statusCol.setPrefWidth(120);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.setStyle("-fx-padding: 3px 8px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: 700;");
                    switch (item) {
                        case "AVAILABLE" -> badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: #10B981;");
                        case "RENTED" -> badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(59, 130, 246, 0.15); -fx-text-fill: #3B82F6;");
                        case "MAINTENANCE" -> badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #EF4444;");
                        default -> badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(100, 116, 139, 0.15); -fx-text-fill: #64748B;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<CycleItem, String> descCol = new TableColumn<>("Notes / Description");
        descCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        descCol.setPrefWidth(200);

        fleetTableView.getColumns().setAll(List.of(labelCol, ownerCol, hubCol, typeCol, condCol, statusCol, descCol));

        content.getChildren().addAll(toolbar, fleetTableView);
        return content;
    }

    private void showAddCycleModal() {
        Dialog<CycleItem> dialog = new Dialog<>();
        dialog.setTitle("Add Cycle to Campus Fleet");
        dialog.setHeaderText("Register a new cycle into the campus fleet inventory");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField labelField = new TextField();
        labelField.setPromptText("e.g. Phoenix Alloy Commuter");

        ComboBox<CycleType> typeCombo = new ComboBox<>(FXCollections.observableArrayList(CycleType.values()));
        typeCombo.setValue(CycleType.CITY_BIKE);

        ComboBox<CycleCondition> condCombo = new ComboBox<>(FXCollections.observableArrayList(CycleCondition.values()));
        condCombo.setValue(CycleCondition.EXCELLENT);

        ComboBox<String> hubCombo = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        hubCombo.setValue(CampusHubs.names().get(0));

        TextField descField = new TextField();
        descField.setPromptText("Hardware specs, accessory notes...");

        grid.add(new Label("Cycle Model / Label:"), 0, 0);
        grid.add(labelField, 1, 0);
        grid.add(new Label("Cycle Type:"), 0, 1);
        grid.add(typeCombo, 1, 1);
        grid.add(new Label("Condition:"), 0, 2);
        grid.add(condCombo, 1, 2);
        grid.add(new Label("Station Hub:"), 0, 3);
        grid.add(hubCombo, 1, 3);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(descField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String lbl = labelField.getText() == null ? "" : labelField.getText().trim();
                if (lbl.isEmpty()) lbl = "Campus Cycle";
                String hub = hubCombo.getValue();
                CampusHubs.Hub hRef = CampusHubs.byName(hub);
                return new CycleItem(
                        "C-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                        admin.id(),
                        admin.displayName(),
                        lbl,
                        typeCombo.getValue(),
                        condCombo.getValue(),
                        hub,
                        hRef.lat(),
                        hRef.lng(),
                        descField.getText() == null ? "" : descField.getText().trim(),
                        ReviewStatus.APPROVED,
                        AvailabilityStatus.AVAILABLE
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(cycle -> {
            repo.addCycle(cycle);
            LocalDatabase.getInstance().saveCycle(cycle);
            loadAllDataAsync();
        });
    }

    private void showEditCycleModal(CycleItem cycle) {
        Dialog<CycleItem> dialog = new Dialog<>();
        dialog.setTitle("Edit Cycle Details");
        dialog.setHeaderText("Update fleet record for: " + cycle.label());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField labelField = new TextField(cycle.label());
        ComboBox<CycleType> typeCombo = new ComboBox<>(FXCollections.observableArrayList(CycleType.values()));
        typeCombo.setValue(cycle.type());

        ComboBox<CycleCondition> condCombo = new ComboBox<>(FXCollections.observableArrayList(CycleCondition.values()));
        condCombo.setValue(cycle.condition());

        ComboBox<String> hubCombo = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        hubCombo.setValue(cycle.pickupPoint());

        ComboBox<AvailabilityStatus> statusCombo = new ComboBox<>(FXCollections.observableArrayList(AvailabilityStatus.values()));
        statusCombo.setValue(cycle.availabilityStatus());

        TextField descField = new TextField(cycle.description());

        grid.add(new Label("Cycle Model / Label:"), 0, 0);
        grid.add(labelField, 1, 0);
        grid.add(new Label("Cycle Type:"), 0, 1);
        grid.add(typeCombo, 1, 1);
        grid.add(new Label("Condition:"), 0, 2);
        grid.add(condCombo, 1, 2);
        grid.add(new Label("Station Hub:"), 0, 3);
        grid.add(hubCombo, 1, 3);
        grid.add(new Label("Availability Status:"), 0, 4);
        grid.add(statusCombo, 1, 4);
        grid.add(new Label("Description:"), 0, 5);
        grid.add(descField, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                CampusHubs.Hub hRef = CampusHubs.byName(hubCombo.getValue());
                return new CycleItem(
                        cycle.id(),
                        cycle.ownerId(),
                        cycle.ownerName(),
                        labelField.getText().trim(),
                        typeCombo.getValue(),
                        condCombo.getValue(),
                        hubCombo.getValue(),
                        hRef.lat(),
                        hRef.lng(),
                        descField.getText().trim(),
                        cycle.reviewStatus(),
                        statusCombo.getValue()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            repo.updateCycle(updated);
            LocalDatabase.getInstance().saveCycle(updated);
            loadAllDataAsync();
        });
    }

    private void confirmAndDeleteCycle(CycleItem cycle) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete cycle: " + cycle.label() + " (" + cycle.id() + ")");
        alert.setContentText("Are you sure you want to permanently remove this bicycle from the campus fleet?");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                repo.deleteCycle(cycle.id());
                LocalDatabase.getInstance().deleteCycle(cycle.id());
                loadAllDataAsync();
            }
        });
    }

    private void showPendingReviewModal() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Review Pending Cycle Listings");
        dialog.setHeaderText("Student bicycle submissions queued for inspection and authorization");

        VBox box = new VBox(12);
        box.setPrefWidth(550);
        box.setPadding(new Insets(16));

        List<CycleItem> pending = repo.pendingCycles();
        if (pending.isEmpty()) {
            Label noPending = new Label("No pending cycle submissions waiting for review.");
            noPending.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");
            box.getChildren().add(noPending);
        } else {
            for (CycleItem c : pending) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("sub-panel");
                row.setPadding(new Insets(10, 14, 10, 14));

                VBox details = new VBox(2);
                Label name = new Label(c.label() + " • " + c.type().name().replace("_", " "));
                name.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
                Label owner = new Label("Owner: " + c.ownerName() + " | Hub: " + c.pickupPoint());
                owner.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
                details.getChildren().addAll(name, owner);

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button approve = new Button("Approve");
                approve.getStyleClass().add("primary-button");
                approve.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
                approve.setOnAction(e -> {
                    repo.reviewCycle(admin, c.id(), true, "Approved for campus circulation");
                    dialog.close();
                    loadAllDataAsync();
                });

                Button reject = new Button("Reject");
                reject.getStyleClass().add("danger-button");
                reject.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
                reject.setOnAction(e -> {
                    TextInputDialog reasonDlg = new TextInputDialog("Inspection criteria not met");
                    reasonDlg.setTitle("Reject Submission");
                    reasonDlg.setHeaderText("Reason for rejection:");
                    reasonDlg.showAndWait().ifPresent(reason -> {
                        repo.reviewCycle(admin, c.id(), false, reason);
                        dialog.close();
                        loadAllDataAsync();
                    });
                });

                row.getChildren().addAll(details, sp, approve, reject);
                box.getChildren().add(row);
            }
        }

        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // =========================================================================
    // TAB 2: MAINTENANCE TICKETS
    // =========================================================================
    private VBox createMaintenanceTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 0, 0, 0));

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label heading = new Label("Bicycle Maintenance & Work Orders");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("Damage reports filed by students on return; re-commission repaired cycles to available status");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resolveBtn = new Button("Resolve & Re-commission");
        resolveBtn.getStyleClass().add("primary-button");
        resolveBtn.setOnAction(e -> {
            MaintenanceTicket selected = ticketTableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "Selection Required", "Please select a maintenance ticket from the table.");
                return;
            }
            if (selected.isResolved()) {
                showAlert(Alert.AlertType.INFORMATION, "Already Resolved", "This maintenance ticket has already been resolved.");
                return;
            }
            showResolveModal(selected);
        });

        toolbar.getChildren().addAll(titleBox, spacer, resolveBtn);

        // Configure Ticket TableView
        ticketTableView.setItems(ticketList);
        ticketTableView.setPrefHeight(340);
        ticketTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<MaintenanceTicket, String> idCol = new TableColumn<>("Ticket ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().id()));
        idCol.setPrefWidth(90);

        TableColumn<MaintenanceTicket, String> cycleCol = new TableColumn<>("Cycle ID");
        cycleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cycleId()));
        cycleCol.setPrefWidth(90);

        TableColumn<MaintenanceTicket, String> userCol = new TableColumn<>("Reported By");
        userCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reportedBy()));
        userCol.setPrefWidth(120);

        TableColumn<MaintenanceTicket, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category()));
        catCol.setPrefWidth(120);

        TableColumn<MaintenanceTicket, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        descCol.setPrefWidth(220);

        TableColumn<MaintenanceTicket, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.setStyle("-fx-padding: 3px 8px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: 700;");
                    if ("RESOLVED".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: #10B981;");
                    } else {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #F59E0B;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<MaintenanceTicket, String> dateCol = new TableColumn<>("Reported Date");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reportedDate()));
        dateCol.setPrefWidth(140);

        ticketTableView.getColumns().setAll(List.of(idCol, cycleCol, userCol, catCol, descCol, statusCol, dateCol));

        content.getChildren().addAll(toolbar, ticketTableView);
        return content;
    }

    private void showResolveModal(MaintenanceTicket ticket) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Resolve Maintenance Ticket");
        dialog.setHeaderText("Resolve work order for Cycle: " + ticket.cycleId() + " (" + ticket.id() + ")");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label issueLbl = new Label(ticket.category() + ": " + ticket.description());
        issueLbl.setStyle("-fx-font-weight: 600; -fx-opacity: 0.85;");
        issueLbl.setWrapText(true);

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Enter technician repair notes (e.g. replaced tube, calibrated brakes)...");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        TextField costField = new TextField("0.00");
        costField.setPromptText("Repair Cost in ৳ (e.g. 150.00)");

        grid.add(new Label("Reported Defect:"), 0, 0);
        grid.add(issueLbl, 1, 0);
        grid.add(new Label("Technician Notes:"), 0, 1);
        grid.add(notesArea, 1, 1);
        grid.add(new Label("Repair Cost (৳ BDT):"), 0, 2);
        grid.add(costField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        ButtonType confirmType = new ButtonType("Resolve & Re-commission Cycle", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == confirmType) {
                double cost = 0.0;
                try {
                    cost = Double.parseDouble(costField.getText().trim());
                } catch (Exception ignored) {}
                String notes = notesArea.getText() == null ? "Repairs verified and completed." : notesArea.getText().trim();
                MaintenanceService.getInstance().resolveTicket(ticket.id(), notes, cost, repo);
                return true;
            }
            return false;
        });

        dialog.showAndWait().ifPresent(res -> {
            if (res) {
                showAlert(Alert.AlertType.INFORMATION, "Cycle Re-commissioned",
                        "Ticket " + ticket.id() + " marked RESOLVED.\nCycle " + ticket.cycleId() + " has been reset to AVAILABLE!");
                loadAllDataAsync();
            }
        });
    }

    // =========================================================================
    // TAB 3: STATION REBALANCING
    // =========================================================================
    private VBox createRebalanceTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 0, 0, 0));

        VBox titleBox = new VBox(2);
        Label heading = new Label("Campus Hub Inventory & Rebalancing");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("Monitor station capacity and redistribute surplus bicycles to high-demand docks");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        // Station Inventory Cards
        HBox stationCards = new HBox(12);
        stationCards.setAlignment(Pos.CENTER);
        for (String hub : CampusHubs.names()) {
            VBox card = new VBox(6);
            card.getStyleClass().add("bento-card");
            card.setPadding(new Insets(14, 16, 14, 16));
            card.setPrefWidth(200);

            Label name = new Label(hub);
            name.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
            name.setWrapText(true);

            Label count = new Label("0 Available");
            count.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");
            hubCountLabels.put(hub, count);

            card.getChildren().addAll(name, count);
            stationCards.getChildren().add(card);
        }

        // Rebalancing Form
        VBox formCard = new VBox(14);
        formCard.getStyleClass().add("bento-card");
        formCard.setPadding(new Insets(20));

        Label formTitle = new Label("Redistribute Fleet Units");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 750;");

        HBox form = new HBox(16);
        form.setAlignment(Pos.CENTER_LEFT);

        VBox fromCol = new VBox(4);
        Label fromLbl = new Label("SOURCE HUB");
        fromLbl.getStyleClass().add("metric-label");
        ComboBox<String> fromHub = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        fromHub.setValue(CampusHubs.names().get(0));
        fromHub.setPrefWidth(220);
        fromCol.getChildren().addAll(fromLbl, fromHub);

        VBox toCol = new VBox(4);
        Label toLbl = new Label("TARGET HUB");
        toLbl.getStyleClass().add("metric-label");
        ComboBox<String> toHub = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        toHub.setValue(CampusHubs.names().get(3));
        toHub.setPrefWidth(220);
        toCol.getChildren().addAll(toLbl, toHub);

        VBox countCol = new VBox(4);
        Label countLbl = new Label("TRANSFER UNITS");
        countLbl.getStyleClass().add("metric-label");
        Spinner<Integer> spinner = new Spinner<>(1, 10, 2);
        spinner.setPrefWidth(120);
        countCol.getChildren().addAll(countLbl, spinner);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button dispatchBtn = new Button("Transfer Cycles");
        dispatchBtn.getStyleClass().add("primary-button");

        Label feedbackLbl = new Label();
        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        feedbackLbl.setVisible(false);

        dispatchBtn.setOnAction(e -> {
            String src = fromHub.getValue();
            String dst = toHub.getValue();
            int qty = spinner.getValue();

            if (src.equals(dst)) {
                feedbackLbl.setText("Source and target stations must be different.");
                feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
                feedbackLbl.setVisible(true);
                return;
            }

            dispatchBtn.setDisable(true);
            AppExecutor.asyncThenFx(
                    () -> {
                        repo.rebalanceHub(src, dst, qty);
                        return true;
                    },
                    ok -> {
                        dispatchBtn.setDisable(false);
                        feedbackLbl.setText("Transferred " + qty + " cycle(s) from " + src + " to " + dst);
                        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");
                        feedbackLbl.setVisible(true);
                        loadAllDataAsync();
                    },
                    err -> {
                        dispatchBtn.setDisable(false);
                        feedbackLbl.setText("Transfer failed. Please retry.");
                        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
                        feedbackLbl.setVisible(true);
                    }
            );
        });

        form.getChildren().addAll(fromCol, toCol, countCol, sp, dispatchBtn);
        formCard.getChildren().addAll(formTitle, form, feedbackLbl);

        content.getChildren().addAll(titleBox, stationCards, formCard);
        return content;
    }

    // =========================================================================
    // TAB 4: SYSTEM AUDIT EXPORT
    // =========================================================================
    private VBox createAuditExportTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 0, 0, 0));

        VBox titleBox = new VBox(2);
        Label heading = new Label("System Audit & Compliance Export");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("Multi-threaded CSV export running via ReportService with complete transaction and maintenance history");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        VBox exportCard = new VBox(16);
        exportCard.getStyleClass().add("bento-card");
        exportCard.setPadding(new Insets(24));

        Label cardTitle = new Label("Campus Mobility Data Ledger");
        cardTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 750;");

        Label desc = new Label("Generates a comprehensive CSV archive including:\n" +
                "• All registered campus cycles and hub dock locations\n" +
                "• Complete maintenance tickets, repair notes, and parts expenses\n" +
                "• Student commute rental logs with fare calculations\n" +
                "• Campus Pay wallet transactions and deposits");
        desc.setStyle("-fx-font-size: 12.5px; -fx-line-spacing: 4px; -fx-opacity: 0.8;");

        ProgressBar progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(16);
        progressBar.setVisible(false);

        Label statusIndicator = new Label("Status: Ready to export");
        statusIndicator.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-opacity: 0.85;");

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Button exportBtn = new Button("Start CSV Export");
        exportBtn.getStyleClass().add("primary-button");

        Label resultLocation = new Label();
        resultLocation.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");
        resultLocation.setVisible(false);

        exportBtn.setOnAction(e -> {
            exportBtn.setDisable(true);
            progressBar.setVisible(true);
            resultLocation.setVisible(false);

            Task<File> exportTask = ReportService.getInstance().createExportTask();
            progressBar.progressProperty().bind(exportTask.progressProperty());
            statusIndicator.textProperty().bind(exportTask.messageProperty());

            exportTask.setOnSucceeded(evt -> {
                exportBtn.setDisable(false);
                File exported = exportTask.getValue();
                statusIndicator.textProperty().unbind();
                statusIndicator.setText("Status: Export completed successfully!");
                resultLocation.setText("Saved to: " + exported.getAbsolutePath());
                resultLocation.setVisible(true);
            });

            exportTask.setOnFailed(evt -> {
                exportBtn.setDisable(false);
                statusIndicator.textProperty().unbind();
                statusIndicator.setText("Status: Export failed. " + exportTask.getException().getMessage());
            });

            AppExecutor.runAsync(exportTask);
        });

        btnRow.getChildren().addAll(exportBtn, resultLocation);
        exportCard.getChildren().addAll(cardTitle, desc, progressBar, statusIndicator, btnRow);

        content.getChildren().addAll(titleBox, exportCard);
        return content;
    }

    // =========================================================================
    // DATA LOADING
    // =========================================================================
    private void loadAllDataAsync() {
        AppExecutor.asyncThenFx(
                () -> {
                    List<CycleItem> all = repo.allCycles(admin);
                    List<MaintenanceTicket> tickets = MaintenanceService.getInstance().getAllTickets();
                    return new AdminData(all, tickets);
                },
                data -> {
                    fleetList.setAll(data.cycles);
                    ticketList.setAll(data.tickets);

                    int total = data.cycles.size();
                    long avail = data.cycles.stream().filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE).count();
                    long maint = data.tickets.stream().filter(MaintenanceTicket::isOpen).count();

                    totalFleetVal.setText(total + " Units");
                    availableVal.setText(avail + " Units");
                    inMaintenanceVal.setText(maint + (maint == 1 ? " Ticket" : " Tickets"));

                    // Update station counts
                    for (String hub : CampusHubs.names()) {
                        long count = data.cycles.stream()
                                .filter(c -> hub.equalsIgnoreCase(c.pickupPoint()))
                                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                                .count();
                        Label lbl = hubCountLabels.get(hub);
                        if (lbl != null) {
                            lbl.setText(count + " Available");
                        }
                    }
                },
                err -> {
                    totalFleetVal.setText("Offline");
                    availableVal.setText("—");
                    inMaintenanceVal.setText("—");
                }
        );
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private record AdminData(List<CycleItem> cycles, List<MaintenanceTicket> tickets) {}
}
