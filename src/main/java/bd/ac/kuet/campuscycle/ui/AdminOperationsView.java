package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.*;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.MaintenanceService;
import bd.ac.kuet.campuscycle.service.ReportService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Production Admin Operations Console:
 * 1. Fleet Management: Master cycle inventory table, Add/Edit/Delete, and pending listings review
 * 2. Maintenance Tickets: Student damage reports and technician re-commissioning modal
 * 3. Station Rebalancing: Real-time hub inventory and cycle transfer dispatch
 * 4. System Audit Export: Multi-threaded background CSV generation with live ProgressBar
 * 5. Student Registrations: Campus office student identity verification and access approval
 */
public class AdminOperationsView extends VBox {

    private final CampusUser admin;
    private final CampusRepository repo;
    private final Runnable onRefresh;

    // Bento metric labels
    private final Label totalFleetVal = new Label("—");
    private final Label availableVal = new Label("—");
    private final Label inMaintenanceVal = new Label("—");
    private final Label activeHubsVal = new Label("—");
    private final Label platformEarnVal = new Label("—");
    private final Label platformEarnSub = new Label("5% per settled ride");

    // Fleet Management Tab
    private final ObservableList<CycleItem> fleetList = FXCollections.observableArrayList();
    private final TableView<CycleItem> fleetTableView = new TableView<>();

    // Maintenance Tickets Tab
    private final ObservableList<MaintenanceTicket> ticketList = FXCollections.observableArrayList();
    private final TableView<MaintenanceTicket> ticketTableView = new TableView<>();

    // Student Registrations Tab
    private final ObservableList<UserRegistration> registrationList = FXCollections.observableArrayList();
    private final TableView<UserRegistration> regTableView = new TableView<>();

    // Disputes Tab
    private final ObservableList<DisputeItem> disputeList = FXCollections.observableArrayList();
    private final TableView<DisputeItem> disputeTableView = new TableView<>();

    // Dues Tab (persistent ride debt)
    private final ObservableList<RentalDue> duesList = FXCollections.observableArrayList();
    private final TableView<RentalDue> duesTableView = new TableView<>();
    private final Label duesTotalLabel = new Label("—");

    // Station Rebalancing Tab
    private final Map<String, Label> hubCountLabels = new LinkedHashMap<>();
    private ComboBox<String> fromHubBox;
    private ComboBox<String> toHubBox;
    private Spinner<Integer> transferSpinner;

    // Ticket queue: master copy + active filter (chips default to OPEN, P-142)
    private final List<MaintenanceTicket> allTicketsMaster = new ArrayList<>();
    private TicketFilter ticketFilter = TicketFilter.OPEN;
    // Cycle id -> label snapshot rebuilt once per load (P-142: no per-cell stream)
    private final Map<String, String> cycleLabelById = new HashMap<>();
    // Review reasons entered inline in this console (no domain field exists yet, P-123/P-141)
    private final Map<String, String> registrationDecisions = new HashMap<>();

    // Re-entrancy guard: refresh button, tab refresh and post-mutation reloads share it (P-118)
    private boolean refreshing = false;

    private enum TicketFilter { OPEN, IN_PROGRESS, RESOLVED, ALL }

    public AdminOperationsView(CampusUser admin, CampusRepository repo, Runnable onRefresh) {
        if (admin == null || admin.role() != Role.ADMIN) throw new SecurityException("AdminOperationsView requires Role.ADMIN");
        ThemeManager.install(this);
        this.admin = admin;
        this.repo = repo;
        this.onRefresh = onRefresh;

        setSpacing(24);
        setPadding(new Insets(6, 16, 24, 16));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-background-color: transparent;");

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

        Label sub = new Label("Hub operations, fleet status, and system records");
        sub.getStyleClass().add("view-subtitle");
        sub.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: -fx-ink-700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Data");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_REFRESH, 13, Color.web("#10B981")));
        refreshBtn.setOnAction(e -> loadAllDataAsync());

        row.getChildren().addAll(sub, spacer, refreshBtn);
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
        VBox c5 = createBentoCard("PLATFORM EARNINGS", platformEarnVal, platformEarnSub, ThemeManager.ICON_WALLET, "#8B5CF6");

        grid.add(c1, 0, 0);
        grid.add(c2, 1, 0);
        grid.add(c3, 2, 0);
        grid.add(c4, 3, 0);
        grid.add(c5, 4, 0);

        for (int i = 0; i < 5; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(20.0);
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
        iconBadge.getStyleClass().add("icon-badge");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        valLbl.getStyleClass().add("metric-number");
        subLbl.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, valLbl, subLbl);
        return card;
    }

    private TabPane createOperationalTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab fleetTab = new Tab("Fleet Management", createFleetManagementTab());
        Tab maintenanceTab = new Tab("Maintenance Tickets", createMaintenanceTab());
        Tab rebalanceTab = new Tab("Station Rebalancing", createRebalanceTab());
        Tab auditTab = new Tab("System Audit Export", createAuditExportTab());
        Tab registrationsTab = new Tab("Student Registrations", createRegistrationsTab());
        Tab disputeTab = new Tab("Disputes Queue", createDisputesTab());
        Tab duesTab = new Tab("Ride Dues", createDuesTab());

        tabPane.getTabs().addAll(fleetTab, maintenanceTab, rebalanceTab, auditTab, registrationsTab, disputeTab, duesTab);
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
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
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
            }
        });
        editBtn.disableProperty().bind(fleetTableView.getSelectionModel().selectedItemProperty().isNull());

        Button reviewBtn = new Button("Review Pending Listings");
        reviewBtn.getStyleClass().add("secondary-button");
        reviewBtn.setOnAction(e -> showPendingReviewModal());

        Region toolbarDivider = new Region();
        toolbarDivider.setPrefWidth(1);
        toolbarDivider.setMaxWidth(1);
        toolbarDivider.setMinHeight(24);
        toolbarDivider.setStyle("-fx-background-color: -fx-mid-grey;");

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> {
            CycleItem selected = fleetTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                confirmAndDeleteCycle(selected);
            }
        });
        deleteBtn.disableProperty().bind(fleetTableView.getSelectionModel().selectedItemProperty().isNull());

        Button createUserBtn = new Button("Create Staff User");
        createUserBtn.getStyleClass().add("primary-button");
        createUserBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_USER, 13, Color.WHITE));
        createUserBtn.setOnAction(e -> showCreateStaffUserModal());

        toolbar.getChildren().addAll(titleBox, spacer, addBtn, editBtn, reviewBtn, toolbarDivider, deleteBtn, createUserBtn);

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
                        case "QUARANTINE" -> badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(245, 158, 11, 0.18); -fx-text-fill: #B45309;");
                        case "RETIRED" -> badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(100, 116, 139, 0.25); -fx-text-fill: #475569;");
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
        descCol.setCellFactory(col -> new TableCell<>() {
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
        });

        TableColumn<CycleItem, Void> fleetActionsCol = new TableColumn<>("Actions");
        fleetActionsCol.setPrefWidth(130);
        fleetActionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button rowEdit = new Button("Edit");
            private final Button rowDelete = new Button("Delete");
            {
                rowEdit.getStyleClass().add("secondary-button");
                rowEdit.setStyle("-fx-font-size: 11px; -fx-padding: 3px 8px;");
                rowEdit.setOnAction(e -> {
                    CycleItem item = getTableRow() == null ? null : getTableRow().getItem();
                    if (item != null) showEditCycleModal(item);
                });
                rowDelete.getStyleClass().add("danger-button");
                rowDelete.setStyle("-fx-font-size: 11px; -fx-padding: 3px 8px;");
                rowDelete.setOnAction(e -> {
                    CycleItem item = getTableRow() == null ? null : getTableRow().getItem();
                    if (item != null) confirmAndDeleteCycle(item);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(6, rowEdit, rowDelete);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        });

        fleetTableView.setPlaceholder(tablePlaceholder("No cycles in the fleet.", "Fleet data could not be loaded."));
        fleetTableView.getColumns().setAll(List.of(labelCol, ownerCol, hubCol, typeCol, condCol, statusCol, descCol, fleetActionsCol));

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

        ThemeManager.install(dialog.getDialogPane());
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
            AppExecutor.asyncThenFx(
                    () -> {
                        repo.addCycle(admin, cycle);
                        return null;
                    },
                    res -> loadAllDataAsync(),
                    err -> showAlert(Alert.AlertType.ERROR, "Failed to Add Cycle", err.getMessage())
            );
        });
    }

    private void showCreateStaffUserModal() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Create Staff User");
        dialog.setHeaderText("Create a new technician or admin user with the correct role");
        ThemeManager.install(dialog.getDialogPane());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField emailField = new TextField();
        emailField.setPromptText("admin@kuet.ac.bd");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Initial password (min 6 chars)");

        TextField nameField = new TextField();
        nameField.setPromptText("Display name (optional)");

        ComboBox<Role> roleCombo = new ComboBox<>(FXCollections.observableArrayList(Role.TECHNICIAN, Role.ADMIN));
        roleCombo.setValue(Role.TECHNICIAN);

        grid.add(new Label("Email:"), 0, 0);
        grid.add(emailField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Display Name:"), 0, 2);
        grid.add(nameField, 1, 2);
        grid.add(new Label("Role:"), 0, 3);
        grid.add(roleCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String email = emailField.getText() == null ? "" : emailField.getText().trim().toLowerCase();
                String password = passwordField.getText() == null ? "" : passwordField.getText();
                String name = nameField.getText() == null ? "" : nameField.getText().trim();
                Role role = roleCombo.getValue();

                if (email.isBlank() || password.length() < 6) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", "Email is required and password must be at least 6 characters.");
                    return null;
                }
                if (!email.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Email", "Enter a valid email address.");
                    return null;
                }

                AppExecutor.asyncThenFx(
                        () -> repo.createUserWithRole(admin, email, password, name.isEmpty() ? null : name, role),
                        uid -> {
                            showAlert(Alert.AlertType.INFORMATION, "User Created",
                                    "Staff user created successfully!\nEmail: " + email + "\nRole: " + role.name() + "\nUser ID: " + uid);
                            loadAllDataAsync();
                        },
                        err -> showAlert(Alert.AlertType.ERROR, "Creation Failed", err.getMessage())
                );
            }
            return null;
        });

        dialog.showAndWait();
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
        if (cycle.availabilityStatus() == AvailabilityStatus.RENTED) {
            statusCombo.setDisable(true);
            statusCombo.setTooltip(new Tooltip("Cannot change status mid-ride"));
        }

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

        ThemeManager.install(dialog.getDialogPane());
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                CampusHubs.Hub hRef = CampusHubs.byName(hubCombo.getValue());
                // Status is routed through updateCycleState on save (P-061), so the
                // updateCycle payload always carries the current status — never a new one.
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
                        cycle.availabilityStatus()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            AvailabilityStatus next = statusCombo.getValue();
            String nextHub = hubCombo.getValue();
            boolean statusChanged = !statusCombo.isDisabled() && next != cycle.availabilityStatus();
            boolean hubChanged = nextHub != null && !nextHub.equals(cycle.pickupPoint());
            AppExecutor.asyncThenFx(
                    () -> {
                        repo.updateCycle(admin, updated);
                        if (statusChanged) {
                            repo.updateCycleState(cycle.id(), cycle.availabilityStatus(), next, admin);
                        }
                        if (hubChanged) {
                            repo.updateCycleLocation(admin, cycle.id(), nextHub);
                        }
                        return null;
                    },
                    res -> loadAllDataAsync(),
                    err -> showAlert(Alert.AlertType.ERROR, "Failed to Update Cycle", requestErrorMessage(err))
            );
        });
    }

    private void confirmAndDeleteCycle(CycleItem cycle) {
        if (cycle.availabilityStatus() == AvailabilityStatus.RENTED) {
            showAlert(Alert.AlertType.ERROR, "Cannot Delete Active Cycle",
                    "Cycle '" + cycle.label() + "' is currently RENTED out. It must be returned before it can be removed from the fleet.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        ThemeManager.install(alert.getDialogPane());
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete cycle: " + cycle.label() + " (" + cycle.id() + ")");
        alert.setContentText("Are you sure you want to permanently remove this bicycle from the campus fleet?");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                AppExecutor.asyncThenFx(
                        () -> {
                            repo.deleteCycle(admin, cycle.id());
                            return null;
                        },
                        res -> loadAllDataAsync(),
                        err -> showAlert(Alert.AlertType.ERROR, "Failed to Delete Cycle", err.getMessage())
                );
            }
        });
    }

    private void showPendingReviewModal() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Review Pending Cycle Listings");
        dialog.setHeaderText("Student bicycle submissions queued for inspection and authorization");
        ThemeManager.install(dialog.getDialogPane());

        VBox box = new VBox(12);
        box.setPrefWidth(550);
        box.setPadding(new Insets(16));

        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(30, 30);
        VBox loading = new VBox(10, pi, new Label("Loading pending submissions..."));
        loading.setAlignment(Pos.CENTER);
        loading.setPadding(new Insets(20));

        Label reasonLbl = new Label("Rejection reason (used when rejecting a listing):");
        reasonLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
        reasonLbl.setVisible(false);
        reasonLbl.setManaged(false);
        TextField reasonField = new TextField("Inspection criteria not met");
        reasonField.setPromptText("Reason recorded when a submission is rejected...");
        reasonField.setVisible(false);
        reasonField.setManaged(false);

        VBox rowsBox = new VBox(10);
        ScrollPane scroll = new ScrollPane(rowsBox);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(420);
        scroll.setPrefViewportHeight(300);
        scroll.setVisible(false);
        scroll.setManaged(false);

        box.getChildren().addAll(loading, reasonLbl, reasonField, scroll);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        AppExecutor.asyncThenFx(
                () -> repo.pendingCycles(),
                pending -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    loading.setVisible(false);
                    loading.setManaged(false);
                    rowsBox.getChildren().clear();
                    if (pending.isEmpty()) {
                        javafx.scene.image.ImageView plazaArt = BikeArt.bannerView("campus-plaza.png", 500, 110);
                        if (plazaArt != null) {
                            rowsBox.getChildren().add(plazaArt);
                        }
                        Label noPending = new Label("No pending cycle submissions waiting for review.");
                        noPending.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");
                        rowsBox.getChildren().add(noPending);
                    } else {
                        reasonLbl.setVisible(true);
                        reasonLbl.setManaged(true);
                        reasonField.setVisible(true);
                        reasonField.setManaged(true);
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
                                dialog.close();
                                AppExecutor.asyncThenFx(
                                        () -> {
                                            repo.reviewCycle(admin, c.id(), true, "Approved for campus circulation");
                                            return null;
                                        },
                                        res -> loadAllDataAsync(),
                                        reviewErr -> showAlert(Alert.AlertType.ERROR, "Review Failed", requestErrorMessage(reviewErr))
                                );
                            });

                            Button reject = new Button("Reject");
                            reject.getStyleClass().add("danger-button");
                            reject.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
                            reject.setOnAction(e -> {
                                String reason = reasonField.getText() == null || reasonField.getText().isBlank()
                                        ? "Inspection criteria not met" : reasonField.getText().trim();
                                dialog.close();
                                AppExecutor.asyncThenFx(
                                        () -> {
                                            repo.reviewCycle(admin, c.id(), false, reason);
                                            return null;
                                        },
                                        res -> loadAllDataAsync(),
                                        reviewErr -> showAlert(Alert.AlertType.ERROR, "Review Failed", requestErrorMessage(reviewErr))
                                );
                            });

                            row.getChildren().addAll(details, sp, approve, reject);
                            rowsBox.getChildren().add(row);
                        }
                    }
                    scroll.setVisible(true);
                    scroll.setManaged(true);
                },
                err -> {
                    if (!dialog.isShowing()) {
                        return;
                    }
                    loading.setVisible(false);
                    loading.setManaged(false);
                    rowsBox.getChildren().clear();
                    rowsBox.getChildren().add(new Label("Failed to load pending submissions: " + requestErrorMessage(err)));
                    scroll.setVisible(true);
                    scroll.setManaged(true);
                }
        );

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
        Label heading = new Label("Maintenance Tickets");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Label sub = new Label("Track damage reports and cycle repairs");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resolveBtn = new Button("Resolve Ticket");
        resolveBtn.getStyleClass().add("primary-button");
        resolveBtn.setOnAction(e -> {
            MaintenanceTicket selected = ticketTableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (selected.isResolved()) {
                showAlert(Alert.AlertType.INFORMATION, "Already Resolved", "This maintenance ticket has already been resolved.");
                return;
            }
            showResolveModal(selected);
        });
        resolveBtn.disableProperty().bind(ticketTableView.getSelectionModel().selectedItemProperty().isNull());

        Button startWorkBtn = new Button("Start Work");
        startWorkBtn.getStyleClass().add("secondary-button");
        startWorkBtn.setOnAction(e -> {
            MaintenanceTicket selected = ticketTableView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.status() == TicketStatus.OPEN) {
                startWorkOnTicket(selected);
            }
        });
        startWorkBtn.disableProperty().bind(ticketTableView.getSelectionModel().selectedItemProperty().isNull());

        toolbar.getChildren().addAll(titleBox, spacer, startWorkBtn, resolveBtn);

        // Filter chips: Open / In Progress / Resolved / All (default Open, P-142)
        FlowPane chipRow = new FlowPane(8, 8);
        chipRow.setAlignment(Pos.CENTER_LEFT);
        chipRow.setPrefWrapLength(800);
        ToggleGroup ticketToggle = new ToggleGroup();
        for (TicketFilter f : TicketFilter.values()) {
            ToggleButton chip = new ToggleButton(chipLabel(f));
            chip.setToggleGroup(ticketToggle);
            chip.getStyleClass().add("filter-chip");
            chip.setUserData(f);
            if (f == ticketFilter) {
                chip.setSelected(true);
            }
            chip.setOnAction(evt -> {
                ticketFilter = (TicketFilter) chip.getUserData();
                applyTicketFilter();
            });
            chipRow.getChildren().add(chip);
        }

        // Configure Ticket TableView
        ticketTableView.setItems(ticketList);
        ticketTableView.setPrefHeight(340);
        ticketTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<MaintenanceTicket, String> idCol = new TableColumn<>("Ticket ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().id()));
        idCol.setPrefWidth(90);
        idCol.setCellFactory(col -> shortIdCell(MaintenanceTicket::id));

        TableColumn<MaintenanceTicket, String> cycleCol = new TableColumn<>("Cycle");
        cycleCol.setCellValueFactory(data -> {
            String cid = data.getValue().cycleId();
            return new SimpleStringProperty(cycleLabelById.getOrDefault(cid, shortId(cid)));
        });
        cycleCol.setPrefWidth(130);
        cycleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    MaintenanceTicket t = getTableRow() == null ? null : getTableRow().getItem();
                    setTooltip(t == null ? null : new Tooltip("Cycle: " + t.cycleId()));
                }
            }
        });

        TableColumn<MaintenanceTicket, String> userCol = new TableColumn<>("Reported By");
        userCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reportedBy()));
        userCol.setPrefWidth(120);
        userCol.setCellFactory(col -> shortIdCell(MaintenanceTicket::reportedBy));

        TableColumn<MaintenanceTicket, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category().replace("_", " ")));
        catCol.setPrefWidth(120);

        TableColumn<MaintenanceTicket, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        descCol.setPrefWidth(220);
        descCol.setCellFactory(col -> new TableCell<>() {
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
        });

        TableColumn<MaintenanceTicket, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status().name()));
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
                    } else if ("IN_PROGRESS".equalsIgnoreCase(item) || "IN PROGRESS".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(59, 130, 246, 0.15); -fx-text-fill: #3B82F6;");
                    } else {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #F59E0B;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<MaintenanceTicket, String> dateCol = new TableColumn<>("Reported");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reportedDate()));
        dateCol.setPrefWidth(120);

        TableColumn<MaintenanceTicket, String> costCol = new TableColumn<>("Cost");
        costCol.setCellValueFactory(data -> new SimpleStringProperty(Money.formatTaka(data.getValue().repairCostPoisha())));
        costCol.setPrefWidth(90);

        TableColumn<MaintenanceTicket, String> resolvedCol = new TableColumn<>("Resolved");
        resolvedCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().resolvedDate()));
        resolvedCol.setPrefWidth(120);

        TableColumn<MaintenanceTicket, Void> ticketActionsCol = new TableColumn<>("Actions");
        ticketActionsCol.setPrefWidth(140);
        ticketActionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button rowStart = new Button("Start");
            private final Button rowResolve = new Button("Resolve");
            {
                rowStart.getStyleClass().add("secondary-button");
                rowStart.setStyle("-fx-font-size: 11px; -fx-padding: 3px 8px;");
                rowStart.setOnAction(e -> {
                    MaintenanceTicket item = getTableRow() == null ? null : getTableRow().getItem();
                    if (item != null && item.status() == TicketStatus.OPEN) startWorkOnTicket(item);
                });
                rowResolve.getStyleClass().add("primary-button");
                rowResolve.setStyle("-fx-font-size: 11px; -fx-padding: 3px 8px;");
                rowResolve.setOnAction(e -> {
                    MaintenanceTicket item = getTableRow() == null ? null : getTableRow().getItem();
                    if (item != null && !item.isResolved()) showResolveModal(item);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                MaintenanceTicket t = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || t == null) {
                    setGraphic(null);
                } else {
                    rowStart.setVisible(t.status() == TicketStatus.OPEN);
                    rowStart.setManaged(t.status() == TicketStatus.OPEN);
                    rowResolve.setDisable(t.isResolved());
                    HBox box = new HBox(6, rowStart, rowResolve);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        });

        ticketTableView.setRowFactory(tv -> {
            TableRow<MaintenanceTicket> row = new TableRow<>() {
                @Override
                protected void updateItem(MaintenanceTicket item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setOpacity(1.0);
                        setStyle("");
                    } else if (item.isResolved()) {
                        setOpacity(0.55);
                        setStyle("");
                    } else {
                        setOpacity(1.0);
                        setStyle("");
                    }
                }
            };
            row.setStyle("-fx-cursor: hand;");
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    MaintenanceTicket item = row.getItem();
                    if (item != null && !item.isResolved()) {
                        showResolveModal(item);
                    }
                }
            });
            return row;
        });
        ticketTableView.setPlaceholder(tablePlaceholder("No maintenance tickets found.", ""));
        ticketTableView.getColumns().setAll(List.of(idCol, cycleCol, userCol, catCol, descCol, statusCol, dateCol, costCol, resolvedCol, ticketActionsCol));

        content.getChildren().addAll(toolbar, chipRow, ticketTableView);
        return content;
    }

    private void showResolveModal(MaintenanceTicket ticket) {
        Dialog<ResolveRequest> dialog = new Dialog<>();
        dialog.setTitle("Resolve Ticket");
        String cycleName = cycleLabelById.getOrDefault(ticket.cycleId(), ticket.cycleId());
        dialog.setHeaderText("Resolve Ticket #" + shortId(ticket.id()) + " · " + cycleName);
        ThemeManager.install(dialog.getDialogPane());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label issueLbl = new Label(ticket.category().replace("_", " ") + ": " + ticket.description());
        issueLbl.setStyle("-fx-font-weight: 600; -fx-opacity: 0.85;");
        issueLbl.setWrapText(true);

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Enter repair notes...");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        TextField costField = new TextField("0.00");
        costField.setPromptText("0.00");
        Label costError = new Label();
        costError.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
        costError.setVisible(false);
        costError.setManaged(false);

        ComboBox<ReleaseDecision> decisionCombo = new ComboBox<>(FXCollections.observableArrayList(ReleaseDecision.values()));
        decisionCombo.setValue(ReleaseDecision.RETURN_TO_SERVICE);
        decisionCombo.setTooltip(new Tooltip("RETIRE permanently withdraws the cycle and requires Role.ADMIN."));

        ComboBox<String> hubCombo = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        hubCombo.setPromptText("Keep current hub");
        hubCombo.setValue(null);

        grid.add(new Label("Defect:"), 0, 0);
        grid.add(issueLbl, 1, 0);
        grid.add(new Label("Repair Notes:"), 0, 1);
        grid.add(notesArea, 1, 1);
        grid.add(new Label("Repair Cost (৳):"), 0, 2);
        grid.add(costField, 1, 2);
        grid.add(costError, 1, 3);
        grid.add(new Label("Decision:"), 0, 4);
        grid.add(decisionCombo, 1, 4);
        grid.add(new Label("Redeploy Hub:"), 0, 5);
        grid.add(hubCombo, 1, 5);

        dialog.getDialogPane().setContent(grid);
        ButtonType confirmType = new ButtonType("Resolve Ticket", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(confirmType).addEventFilter(ActionEvent.ACTION, evt -> {
            try {
                Money.parseBdtToPoisha(costField.getText());
                costError.setVisible(false);
                costError.setManaged(false);
            } catch (IllegalArgumentException ex) {
                costError.setText(ex.getMessage());
                costError.setVisible(true);
                costError.setManaged(true);
                evt.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == confirmType) {
                String notes = notesArea.getText() == null || notesArea.getText().isBlank()
                        ? "Repairs verified and completed." : notesArea.getText().trim();
                ReleaseDecision decision = decisionCombo.getValue() != null
                        ? decisionCombo.getValue() : ReleaseDecision.RETURN_TO_SERVICE;
                return new ResolveRequest(ticket.id(), notes,
                        costField.getText() == null ? "" : costField.getText().trim(),
                        decision, hubCombo.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(req -> {
            final int costPoisha;
            try {
                costPoisha = Money.parseBdtToPoisha(req.costText());
            } catch (IllegalArgumentException ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid Repair Cost", ex.getMessage() + " No changes were made.");
                return;
            }
            AppExecutor.asyncThenFx(
                    () -> MaintenanceService.getInstance().resolveTicket(
                            req.ticketId(), req.notes(), costPoisha, req.decision(), req.hub(), admin, repo),
                    ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            showAlert(Alert.AlertType.INFORMATION, "Ticket Resolved",
                                    "Ticket " + shortId(req.ticketId()) + " marked RESOLVED.\nRelease decision: "
                                            + req.decision().name().replace("_", " ") + ".");
                            loadAllDataAsync();
                            if (onRefresh != null) onRefresh.run();
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Resolution Failed",
                                    "Ticket is not open (it may already be resolved). No changes were made.");
                        }
                    },
                    err -> showAlert(Alert.AlertType.ERROR, "Resolution Failed",
                            requestErrorMessage(err) + " No changes were made.")
            );
        });
    }

    private void startWorkOnTicket(MaintenanceTicket ticket) {
        AppExecutor.asyncThenFx(
                () -> MaintenanceService.getInstance().startWork(ticket.id(), admin),
                ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        loadAllDataAsync();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Could Not Start Work",
                                "Ticket is no longer OPEN. No changes were made.");
                    }
                },
                err -> showAlert(Alert.AlertType.ERROR, "Could Not Start Work",
                        requestErrorMessage(err) + " No changes were made.")
        );
    }

    // =========================================================================
    // TAB 3: STATION REBALANCING
    // =========================================================================
    private VBox createRebalanceTab() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 0, 0, 0));

        VBox titleBox = new VBox(2);
        Label heading = new Label("Campus Hub Inventory & Rebalancing");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Label sub = new Label("Monitor station capacity and redistribute surplus bicycles to high-demand docks");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        // Station Inventory Cards
        FlowPane stationCards = new FlowPane(12, 12);
        stationCards.setAlignment(Pos.CENTER);
        stationCards.setPrefWrapLength(900);
        for (String hub : CampusHubs.names()) {
            VBox card = new VBox(6);
            card.getStyleClass().add("bento-card");
            card.setPadding(new Insets(14, 16, 14, 16));
            card.setMinWidth(180);
            card.setMaxWidth(220);
            card.setPrefWidth(200);

            Label name = new Label(hub);
            name.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
            name.setWrapText(true);

            Label count = new Label("—");
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
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        FlowPane form = new FlowPane(16, 12);
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPrefWrapLength(800);

        VBox fromCol = new VBox(4);
        Label fromLbl = new Label("SOURCE HUB");
        fromLbl.getStyleClass().add("metric-label");
        ComboBox<String> fromHub = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        fromHub.setValue(CampusHubs.names().get(0));
        fromHub.setPrefWidth(220);
        fromCol.getChildren().addAll(fromLbl, fromHub);
        fromHubBox = fromHub;

        VBox toCol = new VBox(4);
        Label toLbl = new Label("TARGET HUB");
        toLbl.getStyleClass().add("metric-label");
        ComboBox<String> toHub = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        toHub.setValue(CampusHubs.names().get(3));
        toHub.setPrefWidth(220);
        toCol.getChildren().addAll(toLbl, toHub);
        toHubBox = toHub;

        VBox countCol = new VBox(4);
        Label countLbl = new Label("TRANSFER UNITS");
        countLbl.getStyleClass().add("metric-label");
        Spinner<Integer> spinner = new Spinner<>(1, 10, 2);
        spinner.setPrefWidth(120);
        countCol.getChildren().addAll(countLbl, spinner);
        transferSpinner = spinner;
        fromHub.getSelectionModel().selectedItemProperty().addListener((obs, oldHub, newHub) -> updateTransferCap());
        updateTransferCap();

        Button dispatchBtn = new Button("Transfer Cycles");
        dispatchBtn.getStyleClass().add("primary-button");

        Label feedbackLbl = new Label();
        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        feedbackLbl.setVisible(false);
        feedbackLbl.setManaged(false);

        dispatchBtn.setOnAction(e -> {
            String src = fromHub.getValue();
            String dst = toHub.getValue();
            int qty = spinner.getValue();

            if (src.equals(dst)) {
                feedbackLbl.setText("Source and target stations must be different.");
                feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
                feedbackLbl.setVisible(true);
                feedbackLbl.setManaged(true);
                return;
            }

            dispatchBtn.setDisable(true);
            fromHub.setDisable(true);
            toHub.setDisable(true);
            spinner.setDisable(true);
            AppExecutor.asyncThenFx(
                    () -> repo.rebalanceHub(admin, src, dst, qty),
                    moved -> {
                        dispatchBtn.setDisable(false);
                        fromHub.setDisable(false);
                        toHub.setDisable(false);
                        spinner.setDisable(false);
                        if (moved <= 0) {
                            feedbackLbl.setText("No cycles available at " + src + " — nothing transferred (requested " + qty + ").");
                            feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
                        } else if (moved < qty) {
                            feedbackLbl.setText("Transferred " + moved + " cycle(s) from " + src + " to " + dst + " (only " + moved + " of " + qty + " requested were available).");
                            feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");
                        } else {
                            feedbackLbl.setText("Transferred " + moved + " cycle(s) from " + src + " to " + dst + ".");
                            feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");
                        }
                        feedbackLbl.setVisible(true);
                        feedbackLbl.setManaged(true);
                        loadAllDataAsync();
                    },
                    err -> {
                        dispatchBtn.setDisable(false);
                        fromHub.setDisable(false);
                        toHub.setDisable(false);
                        spinner.setDisable(false);
                        feedbackLbl.setText("Transfer failed: " + requestErrorMessage(err));
                        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
                        feedbackLbl.setVisible(true);
                        feedbackLbl.setManaged(true);
                    }
            );
        });

        form.getChildren().addAll(fromCol, toCol, countCol, dispatchBtn);
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
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Label sub = new Label("Multi-threaded CSV export running via ReportService with complete transaction and maintenance history");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        VBox exportCard = new VBox(16);
        exportCard.getStyleClass().add("bento-card");
        exportCard.setPadding(new Insets(24));

        Label cardTitle = new Label("Campus Mobility Data Ledger");
        cardTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        Label desc = new Label("Generates a comprehensive CSV archive including:\n" +
                "• All registered campus cycles and hub dock locations\n" +
                "• Complete maintenance tickets, repair notes, and parts expenses\n" +
                "• Student commute rental logs with fare calculations\n" +
                "• Campus Pay wallet transactions and deposits");
        desc.setStyle("-fx-font-size: 12.5px; -fx-line-spacing: 4px; -fx-opacity: 0.8;");

        File defaultTarget = ReportService.getDefaultReportFile(admin);
        Label pathNote = new Label("Destination: " + defaultTarget.getParent()
                + " (Desktop when present, otherwise your home folder). "
                + "Each run creates a new timestamped file and never overwrites an existing report.");
        pathNote.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.7;");
        pathNote.setWrapText(true);

        ProgressBar progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(16);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        Label statusIndicator = new Label("Status: Ready to export");
        statusIndicator.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-opacity: 0.85;");

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Button exportBtn = new Button("Start CSV Export");
        exportBtn.getStyleClass().add("primary-button");

        Label resultLocation = new Label();
        resultLocation.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");
        resultLocation.setVisible(false);
        resultLocation.setManaged(false);

        exportBtn.setOnAction(e -> {
            exportBtn.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            resultLocation.setVisible(false);
            resultLocation.setManaged(false);

            Task<File> exportTask = ReportService.getInstance().createExportTask(ReportService.getDefaultReportFile(admin), admin);
            progressBar.progressProperty().unbind();
            statusIndicator.textProperty().unbind();
            progressBar.progressProperty().bind(exportTask.progressProperty());
            statusIndicator.textProperty().bind(exportTask.messageProperty());

            exportTask.setOnSucceeded(evt -> {
                exportBtn.setDisable(false);
                progressBar.progressProperty().unbind();
                statusIndicator.textProperty().unbind();
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                File exported = null;
                try {
                    exported = exportTask.getValue();
                } catch (Exception ignored) {}
                if (exported != null) {
                    statusIndicator.setText("Status: Export completed successfully!");
                    resultLocation.setText("Saved to: " + exported.getAbsolutePath());
                } else {
                    statusIndicator.setText("Status: Export finished but produced no file.");
                    resultLocation.setText("No file was produced. Please retry.");
                }
                resultLocation.setVisible(true);
                resultLocation.setManaged(true);
            });

            exportTask.setOnFailed(evt -> {
                exportBtn.setDisable(false);
                progressBar.progressProperty().unbind();
                statusIndicator.textProperty().unbind();
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                Throwable failure = exportTask.getException();
                String msg = (failure != null && failure.getMessage() != null && !failure.getMessage().isBlank())
                        ? failure.getMessage() : "Unknown error";
                statusIndicator.setText("Status: Export failed. " + msg);
            });

            exportTask.setOnCancelled(evt -> {
                exportBtn.setDisable(false);
                progressBar.progressProperty().unbind();
                statusIndicator.textProperty().unbind();
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                statusIndicator.setText("Status: Export cancelled.");
            });

            AppExecutor.runAsync(exportTask);
        });

        btnRow.getChildren().addAll(exportBtn, resultLocation);
        exportCard.getChildren().addAll(cardTitle, desc, pathNote, progressBar, statusIndicator, btnRow);

        content.getChildren().addAll(titleBox, exportCard);
        return content;
    }

    // =========================================================================
    // TAB 5: STUDENT REGISTRATIONS & VERIFICATION
    // =========================================================================
    private VBox createRegistrationsTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 0, 0, 0));

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label heading = new Label("Student Registrations");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Label sub = new Label("Review student identity and verify account access");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Click any student row to view details & approve or reject");
        hint.setStyle("-fx-font-size: 12px; -fx-opacity: 0.65; -fx-font-style: italic;");

        toolbar.getChildren().addAll(titleBox, spacer, hint);

        regTableView.setItems(registrationList);
        regTableView.setPrefHeight(340);
        regTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<UserRegistration, String> nameCol = new TableColumn<>("Student Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fullName()));
        nameCol.setPrefWidth(150);

        TableColumn<UserRegistration, String> rollCol = new TableColumn<>("Roll");
        rollCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().studentRoll()));
        rollCol.setPrefWidth(90);

        TableColumn<UserRegistration, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().department()));
        deptCol.setPrefWidth(150);

        TableColumn<UserRegistration, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().email()));
        emailCol.setPrefWidth(180);

        TableColumn<UserRegistration, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().phone()));
        phoneCol.setPrefWidth(110);

        TableColumn<UserRegistration, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().verificationStatus()));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.replace("_", " "));
                    badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px; -fx-background-radius: 999px;");
                    if ("APPROVED".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #ECFDF5; -fx-text-fill: #059669;");
                    } else if ("REJECTED".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #FEF2F2; -fx-text-fill: #DC2626;");
                    } else {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #FFFBEB; -fx-text-fill: #D97706;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<UserRegistration, String> dateCol = new TableColumn<>("Submitted");
        dateCol.setCellValueFactory(data -> {
            try {
                return new SimpleStringProperty(data.getValue().createdAt().format(DateTimeFormatter.ofPattern("dd/MM/yy hh:mm a")));
            } catch (Exception e) {
                return new SimpleStringProperty("—");
            }
        });
        dateCol.setPrefWidth(130);

        regTableView.setRowFactory(tv -> {
            TableRow<UserRegistration> row = new TableRow<>();
            row.setStyle("-fx-cursor: hand;");
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    showStudentDetailsModal(row.getItem());
                }
            });
            return row;
        });

        regTableView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                UserRegistration selected = regTableView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showStudentDetailsModal(selected);
                }
            }
        });

        regTableView.setPlaceholder(tablePlaceholder("No student registrations found.", ""));
        regTableView.getColumns().setAll(List.of(nameCol, rollCol, deptCol, emailCol, phoneCol, statusCol, dateCol));

        content.getChildren().addAll(toolbar, regTableView);
        return content;
    }

    private void showStudentDetailsModal(UserRegistration reg) {
        if (reg == null) return;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Student Details");
        dialog.setHeaderText(null);
        ThemeManager.install(dialog.getDialogPane());

        VBox root = new VBox(16);
        root.setPrefWidth(480);
        root.setPadding(new Insets(20));

        // Header: Name and Status Badge
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox nameBox = new VBox(2);
        Label nameLbl = new Label(reg.fullName());
        nameLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");
        Label rollSub = new Label("Roll: " + reg.studentRoll() + " • " + reg.department());
        rollSub.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-opacity: 0.7;");
        nameBox.getChildren().addAll(nameLbl, rollSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(reg.verificationStatus().replace("_", " "));
        statusBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-padding: 4px 10px; -fx-background-radius: 999px;");
        if (reg.isApproved()) {
            statusBadge.setStyle(statusBadge.getStyle() + "-fx-background-color: #ECFDF5; -fx-text-fill: #059669;");
        } else if (reg.isRejected()) {
            statusBadge.setStyle(statusBadge.getStyle() + "-fx-background-color: #FEF2F2; -fx-text-fill: #DC2626;");
        } else {
            statusBadge.setStyle(statusBadge.getStyle() + "-fx-background-color: #FFFBEB; -fx-text-fill: #D97706;");
        }

        headerBox.getChildren().addAll(nameBox, spacer, statusBadge);

        // Details Grid Card
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setStyle("-fx-background-color: -fx-surface-card; -fx-background-radius: 10px; -fx-padding: 16px; -fx-border-color: -fx-border; -fx-border-radius: 10px;");

        int r = 0;
        addDetailRow(grid, r++, "Full Name", reg.fullName());
        addDetailRow(grid, r++, "Student Roll", reg.studentRoll());
        addDetailRow(grid, r++, "Department", reg.department());
        addDetailRow(grid, r++, "University Email", reg.email());
        addDetailRow(grid, r++, "Phone Number", reg.phone());
        String dateStr = "—";
        try {
            dateStr = reg.createdAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
        } catch (Exception ignored) {}
        addDetailRow(grid, r++, "Submitted On", dateStr);
        addDetailRow(grid, r++, "Registration ID", reg.id());

        // Review note field
        VBox noteBox = new VBox(6);
        Label noteLbl = new Label("Review Note / Rejection Reason:");
        noteLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-opacity: 0.8;");
        TextField noteField = new TextField(registrationDecisions.getOrDefault(reg.id(), ""));
        noteField.setPromptText("Enter note for approval, or reason for rejection...");
        noteField.getStyleClass().add("modern-input");
        noteBox.getChildren().addAll(noteLbl, noteField);

        // Action Buttons Row
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.setPadding(new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button("Close");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> dialog.close());

        Button rejectBtn = new Button("Reject Registration");
        rejectBtn.getStyleClass().add("danger-button");
        rejectBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_CLOSE, 12, Color.WHITE));
        rejectBtn.setDisable(reg.isRejected());
        rejectBtn.setOnAction(e -> {
            String note = noteField.getText() == null || noteField.getText().isBlank()
                    ? "Verification criteria not met" : noteField.getText().trim();
            dialog.close();
            executeRejectRegistration(reg, note);
        });

        Button approveBtn = new Button("Approve Registration");
        approveBtn.getStyleClass().add("primary-button");
        approveBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_CHECK, 12, Color.WHITE));
        approveBtn.setDisable(reg.isApproved());
        approveBtn.setOnAction(e -> {
            String note = noteField.getText() == null || noteField.getText().isBlank()
                    ? "Identity verified" : noteField.getText().trim();
            dialog.close();
            executeApproveRegistration(reg, note);
        });

        actionRow.getChildren().addAll(cancelBtn, rejectBtn, approveBtn);

        root.getChildren().addAll(headerBox, grid, noteBox, actionRow);

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node closeBtnNode = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (closeBtnNode != null) {
            closeBtnNode.setVisible(false);
            closeBtnNode.setManaged(false);
        }

        dialog.showAndWait();
    }

    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label + ":");
        lbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600; -fx-opacity: 0.65;");
        Label val = new Label(value != null && !value.isBlank() ? value : "—");
        val.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: -fx-ink-800;");
        val.setWrapText(true);
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    private void executeApproveRegistration(UserRegistration selected, String note) {
        AppExecutor.asyncThenFx(
                () -> {
                    repo.approveUserRegistration(selected.id());
                    bd.ac.kuet.campuscycle.service.NotificationService.getInstance()
                            .sendApprovalEmail(selected.email(), selected.fullName());
                    return null;
                },
                res -> {
                    registrationDecisions.put(selected.id(), note);
                    showAlert(Alert.AlertType.INFORMATION, "Registration Approved",
                            "Student " + selected.fullName() + " (Roll " + selected.studentRoll() + ") has been approved.");
                    loadAllDataAsync();
                },
                err -> showAlert(Alert.AlertType.ERROR, "Approval Failed", requestErrorMessage(err))
        );
    }

    private void executeRejectRegistration(UserRegistration selected, String note) {
        AppExecutor.asyncThenFx(
                () -> {
                    repo.rejectUserRegistration(selected.id(), note);
                    bd.ac.kuet.campuscycle.service.NotificationService.getInstance()
                            .sendRejectionEmail(selected.email(), note);
                    return null;
                },
                res -> {
                    registrationDecisions.put(selected.id(), note);
                    showAlert(Alert.AlertType.INFORMATION, "Registration Rejected",
                            "Registration for " + selected.fullName() + " (Roll " + selected.studentRoll() + ") has been rejected.");
                    loadAllDataAsync();
                },
                err -> showAlert(Alert.AlertType.ERROR, "Rejection Failed", requestErrorMessage(err))
        );
    }

    // =========================================================================
    // TAB 6: DISPUTES QUEUE
    // =========================================================================
    private VBox createDisputesTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 0, 0, 0));

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label heading = new Label("Student Fare & Commute Disputes");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Label sub = new Label("Review disputed trips, rider claims, and reconcile fare charges");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(heading, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resolveBtn = new Button("Resolve Dispute");
        resolveBtn.getStyleClass().add("primary-button");
        resolveBtn.setOnAction(e -> {
            DisputeItem sel = disputeTableView.getSelectionModel().getSelectedItem();
            if (sel == null) {
                return;
            }
            showResolveDisputeDialog(sel);
        });
        resolveBtn.disableProperty().bind(disputeTableView.getSelectionModel().selectedItemProperty().isNull());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> loadAllDataAsync());

        toolbar.getChildren().addAll(titleBox, spacer, resolveBtn, refreshBtn);

        disputeTableView.setItems(disputeList);
        disputeTableView.setPrefHeight(340);
        disputeTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DisputeItem, String> idCol = new TableColumn<>("Dispute ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().disputeId()));
        idCol.setPrefWidth(100);

        TableColumn<DisputeItem, String> rentalCol = new TableColumn<>("Rental ID");
        rentalCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().rentalId()));
        rentalCol.setPrefWidth(120);

        TableColumn<DisputeItem, String> reasonCol = new TableColumn<>("Dispute Reason");
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reason()));
        reasonCol.setPrefWidth(280);

        TableColumn<DisputeItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().state()));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("filter-chip");
                    if ("RESOLVED".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-background-color: -fx-teal-soft; -fx-text-fill: -fx-teal-dark; -fx-font-weight: 700;");
                    } else {
                        badge.setStyle("-fx-background-color: -fx-amber-soft; -fx-text-fill: #B45309; -fx-font-weight: 700;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        disputeTableView.setPlaceholder(tablePlaceholder("No disputes in the queue.", "Disputes could not be loaded."));
        disputeTableView.getColumns().setAll(List.of(idCol, rentalCol, reasonCol, statusCol));
        content.getChildren().addAll(toolbar, disputeTableView);
        return content;
    }

    private void showResolveDisputeDialog(DisputeItem dispute) {
        TextInputDialog dialog = new TextInputDialog("Refund/Adjustment approved.");
        dialog.setTitle("Resolve Commute Dispute");
        dialog.setHeaderText("Resolve Dispute #" + (dispute.disputeId().length() > 8 ? dispute.disputeId().substring(0, 8) : dispute.disputeId())
                + " (Rental #" + (dispute.rentalId().length() > 8 ? dispute.rentalId().substring(0, 8) : dispute.rentalId()) + ")");
        dialog.setContentText("Enter official administrative resolution notes:");

        DialogPane pane = dialog.getDialogPane();
        ThemeManager.install(pane);
        pane.getStyleClass().add("modal-sheet");

        dialog.showAndWait().ifPresent(notes -> {
            AppExecutor.asyncThenFx(
                    () -> {
                        repo.resolveDispute(admin, dispute.disputeId(), notes != null ? notes.trim() : "Resolved");
                        return true;
                    },
                    ok -> {
                        showAlert(Alert.AlertType.INFORMATION, "Dispute Resolved", "Dispute marked RESOLVED.");
                        loadAllDataAsync();
                    },
                    err -> showAlert(Alert.AlertType.ERROR, "Resolution Failed", requestErrorMessage(err))
            );
        });
    }

    // =========================================================================
    // TAB 7: RIDE DUES (persistent overdue/overtime debt)
    // =========================================================================
    private VBox createDuesTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 0, 0, 0));

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label heading = new Label("Unpaid Ride Dues");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Label sub = new Label("Overdue and overtime amounts wallets could not cover — booking stays blocked until cleared or waived");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        sub.setWrapText(true);
        titleBox.getChildren().addAll(heading, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        duesTotalLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: -fx-teal-dark;");

        Button waiveBtn = new Button("Waive Selected");
        waiveBtn.getStyleClass().add("secondary-button");
        waiveBtn.setOnAction(e -> {
            RentalDue sel = duesTableView.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (!sel.isOwed()) {
                showAlert(Alert.AlertType.INFORMATION, "Already Settled", "This due is already settled.");
                return;
            }
            showWaiveDueDialog(sel);
        });
        waiveBtn.disableProperty().bind(duesTableView.getSelectionModel().selectedItemProperty().isNull());
        AnimationHelper.addPressAnimation(waiveBtn);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> loadAllDataAsync());
        AnimationHelper.addPressAnimation(refreshBtn);

        toolbar.getChildren().addAll(titleBox, spacer, duesTotalLabel, waiveBtn, refreshBtn);

        duesTableView.setItems(duesList);
        duesTableView.setPrefHeight(340);
        duesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<RentalDue, String> userCol = new TableColumn<>("Rider");
        userCol.setCellValueFactory(d -> new SimpleStringProperty(shortId(d.getValue().userId())));
        userCol.setPrefWidth(110);
        userCol.setCellFactory(col -> tooltipCell(RentalDue::userId));

        TableColumn<RentalDue, String> rentalCol = new TableColumn<>("Rental");
        rentalCol.setCellValueFactory(d -> new SimpleStringProperty(shortId(d.getValue().rentalId())));
        rentalCol.setPrefWidth(110);
        rentalCol.setCellFactory(col -> tooltipCell(RentalDue::rentalId));

        TableColumn<RentalDue, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(d -> new SimpleStringProperty(Money.formatTaka(d.getValue().amountPoisha())));
        amountCol.setPrefWidth(100);

        TableColumn<RentalDue, String> paidCol = new TableColumn<>("Paid");
        paidCol.setCellValueFactory(d -> new SimpleStringProperty(Money.formatTaka(d.getValue().paidPoisha())));
        paidCol.setPrefWidth(100);

        TableColumn<RentalDue, String> owedCol = new TableColumn<>("Outstanding");
        owedCol.setCellValueFactory(d -> new SimpleStringProperty(Money.formatTaka(d.getValue().outstandingPoisha())));
        owedCol.setPrefWidth(110);

        TableColumn<RentalDue, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().state().name()));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.replace("_", " "));
                    badge.setStyle("-fx-padding: 3px 8px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: 700;");
                    if ("PAID".equalsIgnoreCase(item) || "WAIVED".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: #10B981;");
                    } else if ("PARTIAL".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(59, 130, 246, 0.15); -fx-text-fill: #3B82F6;");
                    } else {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #B45309;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<RentalDue, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().reason()));
        reasonCol.setPrefWidth(220);
        reasonCol.setCellFactory(col -> tooltipCell(RentalDue::reason));

        duesTableView.setPlaceholder(tablePlaceholder("No ride dues on the books.", "Dues could not be loaded."));
        duesTableView.getColumns().setAll(List.of(userCol, rentalCol, amountCol, paidCol, owedCol, statusCol, reasonCol));
        content.getChildren().addAll(toolbar, duesTableView);
        return content;
    }

    private void showWaiveDueDialog(RentalDue due) {
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Waive Ride Due");
        dialog.setHeaderText("Waive " + Money.formatTaka(due.outstandingPoisha())
                + " outstanding on due #" + shortId(due.id()) + "?");
        dialog.setContentText("Waive reason (min 3 characters, recorded in audit):");

        DialogPane pane = dialog.getDialogPane();
        ThemeManager.install(pane);
        pane.getStyleClass().add("modal-sheet");

        dialog.showAndWait().ifPresent(reason -> {
            String note = reason == null ? "" : reason.trim();
            if (note.length() < 3) {
                showAlert(Alert.AlertType.ERROR, "Reason Required",
                        "A waive reason of at least 3 characters is required. No changes were made.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            ThemeManager.install(confirm.getDialogPane());
            confirm.setTitle("Confirm Waive");
            confirm.setHeaderText("Waive " + Money.formatTaka(due.outstandingPoisha()) + "?");
            confirm.setContentText("Reason: " + note);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    AppExecutor.asyncThenFx(
                            () -> {
                                repo.waiveDue(admin, due.id(), note);
                                return true;
                            },
                            ok -> {
                                showAlert(Alert.AlertType.INFORMATION, "Due Waived",
                                        "Due marked WAIVED. The rider can book again.");
                                loadAllDataAsync();
                            },
                            err -> showAlert(Alert.AlertType.ERROR, "Waive Failed", requestErrorMessage(err))
                    );
                }
            });
        });
    }

    // =========================================================================
    // DATA LOADING
    // =========================================================================
    private void loadAllDataAsync() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        AppExecutor.asyncThenFx(
                () -> {
                    List<CycleItem> all = repo.allCycles(admin);
                    List<MaintenanceTicket> tickets = MaintenanceService.getInstance().getAllTickets();
                    List<UserRegistration> registrations = repo.getAllUserRegistrations(admin);
                    List<DisputeItem> disputes = List.of();
                    try {
                        disputes = repo.disputeQueue(admin);
                    } catch (Exception ignored) {}
                    PlatformEarnings earnings = null;
                    try {
                        earnings = repo.platformEarnings(admin);
                    } catch (Exception ignored) {}
                    List<RentalDue> dues = List.of();
                    try {
                        dues = repo.allDues(admin);
                    } catch (Exception ignored) {}
                    return new AdminData(all, tickets, registrations, disputes, earnings, dues);
                },
                data -> {
                    try {
                        fleetList.setAll(data.cycles);
                        allTicketsMaster.clear();
                        allTicketsMaster.addAll(data.tickets);
                        applyTicketFilter();
                        registrationList.setAll(data.registrations);
                        disputeList.setAll(data.disputes);
                        duesList.setAll(data.dues);
                        int duesOwed = data.dues.stream()
                                .filter(RentalDue::isOwed)
                                .mapToInt(RentalDue::outstandingPoisha)
                                .sum();
                        long duesOpen = data.dues.stream().filter(RentalDue::isOwed).count();
                        duesTotalLabel.setText(duesOpen == 0 ? "All clear"
                                : duesOpen + " open · " + Money.formatTaka(duesOwed) + " owed");

                        cycleLabelById.clear();
                        for (CycleItem c : data.cycles) {
                            cycleLabelById.put(c.id(), c.label());
                        }
                        ticketTableView.refresh();

                        int total = data.cycles.size();
                        long avail = data.cycles.stream().filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE).count();
                        long maint = data.tickets.stream().filter(MaintenanceTicket::isOpen).count();

                        totalFleetVal.setText(total + " Units");
                        availableVal.setText(avail + " Units");
                        inMaintenanceVal.setText(maint + (maint == 1 ? " Ticket" : " Tickets"));
                        activeHubsVal.setText(CampusHubs.names().size() + " Hubs");
                        if (data.earnings() != null) {
                            platformEarnVal.setText(Money.formatTaka(data.earnings().feesPoisha()));
                            platformEarnSub.setText(data.earnings().settledRides()
                                    + (data.earnings().settledRides() == 1 ? " settled ride" : " settled rides"));
                        } else {
                            platformEarnVal.setText("—");
                            platformEarnSub.setText("5% per settled ride");
                        }

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
                        updateTransferCap();
                    } finally {
                        refreshing = false;
                    }
                },
                err -> {
                    try {
                        totalFleetVal.setText("—");
                        availableVal.setText("—");
                        inMaintenanceVal.setText("—");
                        activeHubsVal.setText("—");
                        platformEarnVal.setText("—");
                        platformEarnSub.setText("5% per settled ride");
                        duesList.clear();
                        duesTotalLabel.setText("—");
                        for (Label lbl : hubCountLabels.values()) {
                            lbl.setText("Unavailable");
                        }
                    } finally {
                        refreshing = false;
                    }
                }
        );
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        ThemeManager.install(alert.getDialogPane());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private Node tablePlaceholder(String emptyMessage, String errorHint) {
        String display = (emptyMessage != null && !emptyMessage.isBlank()) ? emptyMessage : "No items found.";
        Label msg = new Label(display);
        msg.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");
        msg.setWrapText(true);
        Button retry = new Button("Refresh");
        retry.getStyleClass().add("secondary-button");
        retry.setOnAction(e -> loadAllDataAsync());
        VBox box = new VBox(8, msg, retry);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        return box;
    }

    private static String shortId(String id) {
        if (id == null) {
            return "—";
        }
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private static <T> TableCell<T, String> shortIdCell(java.util.function.Function<T, String> fullId) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(shortId(item));
                    T row = getTableRow() == null ? null : getTableRow().getItem();
                    setTooltip(new Tooltip(row == null ? item : fullId.apply(row)));
                }
            }
        };
    }

    private <T> TableCell<T, String> tooltipCell(java.util.function.Function<T, String> fullText) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    T row = getTableRow() == null ? null : getTableRow().getItem();
                    setTooltip(new Tooltip(row == null ? item : fullText.apply(row)));
                }
            }
        };
    }

    private static String chipLabel(TicketFilter filter) {
        return switch (filter) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In Progress";
            case RESOLVED -> "Resolved";
            case ALL -> "All";
        };
    }

    private void applyTicketFilter() {
        List<MaintenanceTicket> filtered = switch (ticketFilter) {
            case OPEN -> allTicketsMaster.stream().filter(t -> t.status() == TicketStatus.OPEN).toList();
            case IN_PROGRESS -> allTicketsMaster.stream().filter(t -> t.status() == TicketStatus.IN_PROGRESS).toList();
            case RESOLVED -> allTicketsMaster.stream().filter(MaintenanceTicket::isResolved).toList();
            case ALL -> List.copyOf(allTicketsMaster);
        };
        ticketList.setAll(filtered);
    }

    private void updateTransferCap() {
        if (fromHubBox == null || transferSpinner == null) {
            return;
        }
        String src = fromHubBox.getValue();
        if (src == null) {
            return;
        }
        long stock = fleetList.stream()
                .filter(c -> src.equalsIgnoreCase(c.pickupPoint()))
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .count();
        if (transferSpinner.getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory factory) {
            factory.setMax((int) Math.max(1, stock));
            if (transferSpinner.getValue() != null && transferSpinner.getValue() > factory.getMax()) {
                transferSpinner.getValueFactory().setValue(factory.getMax());
            }
        }
    }

    private static String requestErrorMessage(Throwable err) {
        Throwable cause = (err != null && err.getCause() != null) ? err.getCause() : err;
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return "Unexpected error. No changes were made.";
    }

    private record ResolveRequest(String ticketId, String notes, String costText, ReleaseDecision decision, String hub) {}

    private record AdminData(List<CycleItem> cycles, List<MaintenanceTicket> tickets, List<UserRegistration> registrations, List<DisputeItem> disputes, PlatformEarnings earnings, List<RentalDue> dues) {}
}
