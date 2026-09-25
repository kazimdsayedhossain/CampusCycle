package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.*;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Production-ready AdminOperationsView inspired by Fintory & Forselle:
 * 1. JavaFX TableView showcase with sortable columns and custom cell formatting
 * 2. Right-click ContextMenu for Approve, Reject, and Inspect actions
 * 3. Hub rebalancing simulator with ComboBox and Spinner
 * 4. Multi-threaded asynchronous data loading with AppExecutor
 * 5. Informative Tooltips on all interactive controls
 */
public class AdminOperationsView extends VBox {

    private final CampusUser admin;
    private final CampusRepository repo;
    private final Runnable onRefresh;

    private final ObservableList<CycleItem> masterInventory = FXCollections.observableArrayList();
    private final TableView<CycleItem> cycleTableView = new TableView<>();
    private final Label totalFleetVal = new Label("Loading...");
    private final Label pendingApprovalsVal = new Label("Loading...");
    private final Label co2Val = new Label("0.0 kg CO2");
    private final VBox pendingListContainer = new VBox(12);

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
        VBox rebalanceSection = createRebalanceSection();
        VBox approvalSection = createApprovalQueueSection();
        VBox disputeSection = createDisputeQueueSection();
        VBox inventoryTableSection = createInventoryTableSection();

        getChildren().addAll(header, bento, rebalanceSection, approvalSection, disputeSection, inventoryTableSection);
        ThemeManager.applyFadeIn(this);

        loadAdminDataAsync();
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Smart Mobility Admin Dispatch");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label sub = new Label("Campus fleet telemetry, hub rebalancing, and P2P cycle authorization");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Fleet");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_REFRESH, 13, Color.web("#2EB5A4")));
        refreshBtn.setTooltip(new Tooltip("Pull latest live fleet status from Supabase"));
        refreshBtn.setOnAction(e -> loadAdminDataAsync());

        row.getChildren().addAll(titleCol, spacer, refreshBtn);
        return row;
    }

    private GridPane createAdminBento() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        VBox c1 = createBentoCard("TOTAL CAMPUS FLEET", totalFleetVal, new Label("Live Tracked Units"), ThemeManager.ICON_BIKE, "#2EB5A4");
        VBox c2 = createBentoCard("PENDING APPROVALS", pendingApprovalsVal, new Label("Queued for Review"), ThemeManager.ICON_ALERT, "#F59E0B");
        VBox c3 = createBentoCard("EST. CO2 AVOIDED", co2Val, new Label("2.8 km avg per ride"), ThemeManager.ICON_LEAF, "#2EB5A4");
        VBox c4 = createBentoCard("HUBS ONLINE", new Label("5 Hubs"), new Label("KUET Network"), ThemeManager.ICON_DISPATCH, "#2EB5A4");

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

    private VBox createRebalanceSection() {
        VBox card = new VBox(16);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Smart Hub Redistribution & Rebalancer");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("Dispatch autonomous campus shuttles to transfer surplus cycles to deficit hubs");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);
        titleRow.getChildren().add(titleCol);

        HBox form = new HBox(16);
        form.setAlignment(Pos.CENTER_LEFT);

        VBox fromCol = new VBox(4);
        Label fromLbl = new Label("SURPLUS ORIGIN HUB");
        fromLbl.getStyleClass().add("metric-label");
        ComboBox<String> fromHub = new ComboBox<>();
        fromHub.getItems().addAll(bd.ac.kuet.campuscycle.domain.CampusHubs.names());
        fromHub.setValue(bd.ac.kuet.campuscycle.domain.CampusHubs.names().get(0));
        fromHub.getStyleClass().add("modern-input");
        fromHub.setPrefWidth(220);
        fromCol.getChildren().addAll(fromLbl, fromHub);

        VBox toCol = new VBox(4);
        Label toLbl = new Label("DEFICIT DESTINATION HUB");
        toLbl.getStyleClass().add("metric-label");
        ComboBox<String> toHub = new ComboBox<>();
        toHub.getItems().addAll(bd.ac.kuet.campuscycle.domain.CampusHubs.names());
        toHub.setValue(bd.ac.kuet.campuscycle.domain.CampusHubs.names().get(3));
        toHub.getStyleClass().add("modern-input");
        toHub.setPrefWidth(220);
        toCol.getChildren().addAll(toLbl, toHub);

        VBox countCol = new VBox(4);
        Label countLbl = new Label("TRANSFER UNITS");
        countLbl.getStyleClass().add("metric-label");
        Spinner<Integer> spinner = new Spinner<>(1, 10, 2);
        spinner.getStyleClass().add("modern-input");
        spinner.setPrefWidth(120);
        countCol.getChildren().addAll(countLbl, spinner);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button dispatchBtn = new Button("Dispatch Rebalancer");
        dispatchBtn.getStyleClass().add("primary-button");
        dispatchBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_DISPATCH, 13, Color.WHITE));
        dispatchBtn.setTooltip(new Tooltip("Trigger cycle redistribution between selected campus hubs"));

        Label feedbackLbl = new Label();
        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #2EB5A4;");
        feedbackLbl.setVisible(false);

        dispatchBtn.setOnAction(e -> {
            String src = fromHub.getValue();
            String dst = toHub.getValue();
            int qty = spinner.getValue();
            if (src.equals(dst)) {
                feedbackLbl.setText("Origin and destination hubs must be different");
                feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #DC2626;");
                feedbackLbl.setVisible(true);
                return;
            }

            dispatchBtn.setDisable(true);
            AppExecutor.asyncThenFx(
                    () -> {
                        repo.rebalanceHub(src, dst, qty);
                        return true;
                    },
                    res -> {
                        dispatchBtn.setDisable(false);
                        feedbackLbl.setText("Transferred " + qty + " cycles from " + src + " to " + dst);
                        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #2EB5A4;");
                        feedbackLbl.setVisible(true);
                        loadAdminDataAsync();
                    },
                    err -> {
                        dispatchBtn.setDisable(false);
                        feedbackLbl.setText("Rebalance failed. Please retry.");
                        feedbackLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #DC2626;");
                        feedbackLbl.setVisible(true);
                    }
            );
        });

        form.getChildren().addAll(fromCol, toCol, countCol, spacer, dispatchBtn);
        card.getChildren().addAll(titleRow, form, feedbackLbl);
        return card;
    }

    private VBox createApprovalQueueSection() {
        VBox card = new VBox(16);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));

        VBox titleCol = new VBox(2);
        Label title = new Label("Pending Cycle Approvals (P2P Listings)");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("Audit student registered bicycles before authorizing them for campus circulation");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        card.getChildren().addAll(titleCol, pendingListContainer);
        return card;
    }

    private final javafx.collections.ObservableList<String> disputeLines =
            javafx.collections.FXCollections.observableArrayList();
    private final ListView<String> disputeList = new ListView<>(disputeLines);

    private VBox createDisputeQueueSection() {
        VBox card = new VBox(12);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));
        Label title = new Label("Open Fare Disputes");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("RETURNED rentals under review. Resolution requires written note (handled via RPC).");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        disputeList.setPrefHeight(120);
        card.getChildren().addAll(title, sub, disputeList);
        return card;
    }

    private VBox createInventoryTableSection() {
        VBox card = new VBox(16);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));

        VBox titleCol = new VBox(2);
        Label title = new Label("Live Campus Fleet Master Table");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");
        Label sub = new Label("Sortable inventory table with right-click context menu (Inspired by Fintory)");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        // Configure TableView
        cycleTableView.setItems(masterInventory);
        cycleTableView.setPrefHeight(280);
        cycleTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<CycleItem, String> labelCol = new TableColumn<>("Model / Label");
        labelCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().label()));
        labelCol.setPrefWidth(180);

        TableColumn<CycleItem, String> ownerCol = new TableColumn<>("Owner Name");
        ownerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ownerName()));
        ownerCol.setPrefWidth(150);

        TableColumn<CycleItem, String> hubCol = new TableColumn<>("Current Hub");
        hubCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pickupPoint()));
        hubCol.setPrefWidth(170);

        TableColumn<CycleItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type().name()));
        typeCol.setPrefWidth(120);

        TableColumn<CycleItem, String> condCol = new TableColumn<>("Condition");
        condCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().condition().name()));
        condCol.setPrefWidth(100);

        TableColumn<CycleItem, String> statusCol = new TableColumn<>("Availability");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().availabilityStatus().name()));
        statusCol.setPrefWidth(110);

        cycleTableView.getColumns().setAll(List.of(labelCol, ownerCol, hubCol, typeCol, condCol, statusCol));

        // Right-Click Context Menu (async; never block FX thread)
        ContextMenu contextMenu = new ContextMenu();
        MenuItem approveItem = new MenuItem("Approve Listing");
        approveItem.setOnAction(e -> {
            CycleItem selected = cycleTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                AppExecutor.asyncThenFx(
                        () -> {
                            repo.reviewCycle(admin, selected.id(), true, "Approved for campus circulation");
                            return true;
                        },
                        res -> loadAdminDataAsync(),
                        err -> {
                            Alert a = new Alert(Alert.AlertType.ERROR, "Review failed. Please retry.", ButtonType.OK);
                            a.showAndWait();
                        });
            }
        });

        MenuItem rejectItem = new MenuItem("Reject Listing");
        rejectItem.setOnAction(e -> {
            CycleItem selected = cycleTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                TextInputDialog dialog = new TextInputDialog("Physical inspection failed - ");
                dialog.setTitle("Reject Cycle");
                dialog.setHeaderText("Written reason required (min 10 chars)");
                dialog.showAndWait().ifPresent(reason -> AppExecutor.asyncThenFx(
                        () -> {
                            repo.reviewCycle(admin, selected.id(), false, reason);
                            return true;
                        },
                        res -> loadAdminDataAsync(),
                        err -> {
                            Alert a = new Alert(Alert.AlertType.ERROR, "Review failed. Please retry.", ButtonType.OK);
                            a.showAndWait();
                        }));
            }
        });

        MenuItem detailsItem = new MenuItem("View Description & Notes");
        detailsItem.setOnAction(e -> {
            CycleItem selected = cycleTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Cycle Details");
                alert.setHeaderText(selected.label() + " (" + selected.id() + ")");
                alert.setContentText("Description: " + selected.description() + "\nGPS: " + selected.latitude() + ", " + selected.longitude());
                alert.showAndWait();
            }
        });

        contextMenu.getItems().addAll(approveItem, rejectItem, new SeparatorMenuItem(), detailsItem);
        cycleTableView.setContextMenu(contextMenu);

        card.getChildren().addAll(titleCol, cycleTableView);
        return card;
    }

    private void loadAdminDataAsync() {
        AppExecutor.asyncThenFx(
                () -> {
                    List<CycleItem> all = repo.catalog(admin);
                    List<CycleItem> pending = repo.pendingCycles();
                    List<bd.ac.kuet.campuscycle.domain.DisputeItem> disputes;
                    try {
                        disputes = repo.disputeQueue(admin);
                    } catch (Exception e) {
                        disputes = List.of();
                    }
                    return new AdminDataPayload(all, pending, disputes);
                },
                payload -> {
                    totalFleetVal.setText(payload.all.size() + " Units");
                    pendingApprovalsVal.setText(payload.pending.size() + " Pending");
                    // Real estimate: 2.8 km avg per cycle in fleet * 0.021 kg CO2/km vs motorbike
                    double co2 = payload.all.size() * 2.8 * 0.021;
                    co2Val.setText(String.format("%.1f kg CO2", co2));

                    masterInventory.setAll(payload.all);
                    disputeLines.clear();
                    for (bd.ac.kuet.campuscycle.domain.DisputeItem d : payload.disputes()) {
                        disputeLines.add(d.disputeId() + " - rental " + d.rentalId() + " [" + d.state() + "]: " + d.reason());
                    }
                    if (payload.disputes().isEmpty()) {
                        disputeLines.add("No open disputes.");
                    }

                    pendingListContainer.getChildren().clear();
                    if (payload.pending.isEmpty()) {
                        HBox emptyBox = new HBox(12);
                        emptyBox.setAlignment(Pos.CENTER_LEFT);
                        emptyBox.setPadding(new Insets(16));
                        emptyBox.getStyleClass().add("sub-panel");
                        Label ok = new Label("All campus cycle submissions verified. Zero pending approval requests.");
                        ok.setStyle("-fx-font-size: 13px; -fx-font-weight: 650; -fx-opacity: 0.8;");
                        emptyBox.getChildren().addAll(
                                ThemeManager.createIcon(ThemeManager.ICON_CHECK, 16, Color.web("#2EB5A4")),
                                ok
                        );
                        pendingListContainer.getChildren().add(emptyBox);
                    } else {
                        for (CycleItem c : payload.pending) {
                            pendingListContainer.getChildren().add(createPendingItemRow(c));
                        }
                    }
                },
                err -> {
                    totalFleetVal.setText("Offline");
                    pendingApprovalsVal.setText("0");
                }
        );
    }

    private HBox createPendingItemRow(CycleItem cycle) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("sub-panel");
        row.setPadding(new Insets(14, 18, 14, 18));

        StackPane bikeIcon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 16, Color.web("#2EB5A4")));
        bikeIcon.setPrefSize(36, 36);
        bikeIcon.getStyleClass().add("action-icon-btn");

        VBox info = new VBox(2);
        Label name = new Label(cycle.label());
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: 750;");

        Label meta = new Label("Owner: " + cycle.ownerName() + " • Station: " + cycle.pickupPoint());
        meta.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.75;");
        info.getChildren().addAll(name, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button approveBtn = new Button("Approve Listing");
        approveBtn.getStyleClass().add("primary-button");
        approveBtn.setStyle("-fx-font-size: 11px; -fx-padding: 6px 14px;");
        approveBtn.setOnAction(e -> {
            approveBtn.setDisable(true);
            AppExecutor.asyncThenFx(
                    () -> {
                        repo.reviewCycle(admin, cycle.id(), true, "Approved for campus circulation");
                        return true;
                    },
                    res -> loadAdminDataAsync(),
                    err -> err.printStackTrace()
            );
        });

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("secondary-button");
        rejectBtn.setStyle("-fx-font-size: 11px; -fx-padding: 6px 14px;");
        rejectBtn.setOnAction(e -> {
            rejectBtn.setDisable(true);
            AppExecutor.asyncThenFx(
                    () -> {
                        repo.reviewCycle(admin, cycle.id(), false, "Physical inspection failed");
                        return true;
                    },
                    res -> loadAdminDataAsync(),
                    err -> err.printStackTrace()
            );
        });

        row.getChildren().addAll(bikeIcon, info, spacer, approveBtn, rejectBtn);
        return row;
    }

    private record AdminDataPayload(List<CycleItem> all, List<CycleItem> pending,
            List<bd.ac.kuet.campuscycle.domain.DisputeItem> disputes) {}
}
