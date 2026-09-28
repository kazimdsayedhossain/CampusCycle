package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.MaintenanceTicket;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.domain.ReleaseDecision;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.TicketStatus;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.MaintenanceService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Technician maintenance workspace.
 *
 * <p>Visible to TECHNICIAN and ADMIN. Technicians triage (OPEN → IN_PROGRESS)
 * and resolve tickets with an explicit {@link ReleaseDecision}; only ADMIN may
 * choose RETIRE. Students never reach this view (see navigateTo guard).
 */
public class TechnicianView extends VBox {

    private final CampusUser tech;
    private final CampusRepository repo;
    private final Runnable onRefresh;

    private final Label openVal = new Label("—");
    private final Label inProgressVal = new Label("—");
    private final Label resolvedVal = new Label("—");

    private final ObservableList<MaintenanceTicket> ticketList = FXCollections.observableArrayList();
    private final TableView<MaintenanceTicket> ticketTableView = new TableView<>();

    private final List<MaintenanceTicket> allTicketsMaster = new ArrayList<>();
    private TicketFilter ticketFilter = TicketFilter.OPEN;
    private final Map<String, String> cycleLabelById = new HashMap<>();

    private volatile boolean disposed = false;
    private boolean refreshing = false;

    private enum TicketFilter { OPEN, IN_PROGRESS, RESOLVED, ALL }

    public TechnicianView(CampusUser tech, CampusRepository repo, Runnable onRefresh) {
        if (tech == null || (tech.role() != Role.TECHNICIAN && tech.role() != Role.ADMIN)) {
            throw new SecurityException("TechnicianView requires Role.TECHNICIAN or Role.ADMIN");
        }
        ThemeManager.install(this);
        this.tech = tech;
        this.repo = repo;
        this.onRefresh = onRefresh;

        setSpacing(24);
        setPadding(new Insets(6, 16, 24, 16));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(createHeader(), createBento(), createQueueCard());
        ThemeManager.applyFadeIn(this);

        loadTicketsAsync();
    }

    /** Release async callbacks after navigate-away. */
    public void dispose() {
        disposed = true;
    }

    // ------------------------------------------------------------------ header

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label heading = new Label("Maintenance Work Orders");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");
        Label sub = new Label("Triage damage reports, repair cycles, and release them back to service");
        sub.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.7;");
        sub.setWrapText(true);
        titleBox.getChildren().addAll(heading, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_REFRESH, 13, Color.web("#10B981")));
        refreshBtn.setOnAction(e -> loadTicketsAsync());
        AnimationHelper.addPressAnimation(refreshBtn);

        row.getChildren().addAll(titleBox, spacer, refreshBtn);
        return row;
    }

    private GridPane createBento() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        grid.add(metricCard("OPEN", openVal, "Awaiting triage", ThemeManager.ICON_ALERT, "#F59E0B"), 0, 0);
        grid.add(metricCard("IN PROGRESS", inProgressVal, "Under repair", ThemeManager.ICON_CLOCK, "#3B82F6"), 1, 0);
        grid.add(metricCard("RESOLVED", resolvedVal, "Released bikes", ThemeManager.ICON_CHECK, "#10B981"), 2, 0);

        for (int i = 0; i < 3; i++) {
            javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
            col.setPercentWidth(100.0 / 3);
            grid.getColumnConstraints().add(col);
        }
        return grid;
    }

    private VBox metricCard(String title, Label valLbl, String sub, String svgIcon, String accentHex) {
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
        Label subLbl = new Label(sub);
        subLbl.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, valLbl, subLbl);
        AnimationHelper.addCardHoverLift(card);
        return card;
    }

    // ------------------------------------------------------------------ queue

    private VBox createQueueCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(20, 22, 20, 22));
        card.setMaxWidth(Double.MAX_VALUE);

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label("Work Order Queue");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button startBtn = new Button("Start Work");
        startBtn.getStyleClass().add("secondary-button");
        startBtn.setOnAction(e -> {
            MaintenanceTicket sel = ticketTableView.getSelectionModel().getSelectedItem();
            if (sel != null && sel.status() == TicketStatus.OPEN) startWorkOnTicket(sel);
        });
        startBtn.disableProperty().bind(ticketTableView.getSelectionModel().selectedItemProperty().isNull());
        AnimationHelper.addPressAnimation(startBtn);

        Button resolveBtn = new Button("Resolve Ticket");
        resolveBtn.getStyleClass().add("primary-button");
        resolveBtn.setOnAction(e -> {
            MaintenanceTicket sel = ticketTableView.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (sel.isResolved()) {
                showAlert(Alert.AlertType.INFORMATION, "Already Resolved", "This ticket is already resolved.");
                return;
            }
            showResolveModal(sel);
        });
        resolveBtn.disableProperty().bind(ticketTableView.getSelectionModel().selectedItemProperty().isNull());
        AnimationHelper.addPressAnimation(resolveBtn);

        toolbar.getChildren().addAll(heading, spacer, startBtn, resolveBtn);

        FlowPane chipRow = new FlowPane(8, 8);
        chipRow.setAlignment(Pos.CENTER_LEFT);
        chipRow.setPrefWrapLength(800);
        ToggleGroup group = new ToggleGroup();
        for (TicketFilter f : TicketFilter.values()) {
            ToggleButton chip = new ToggleButton(chipLabel(f));
            chip.setToggleGroup(group);
            chip.getStyleClass().add("filter-chip");
            chip.setUserData(f);
            if (f == ticketFilter) chip.setSelected(true);
            chip.setOnAction(evt -> {
                ticketFilter = (TicketFilter) chip.getUserData();
                applyTicketFilter();
            });
            chipRow.getChildren().add(chip);
        }

        configureTable();
        card.getChildren().addAll(toolbar, chipRow, ticketTableView);
        return card;
    }

    private void configureTable() {
        ticketTableView.setItems(ticketList);
        ticketTableView.setPrefHeight(380);
        ticketTableView.setMinHeight(240);
        VBox.setVgrow(ticketTableView, Priority.ALWAYS);
        ticketTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<MaintenanceTicket, String> idCol = new TableColumn<>("Ticket");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(shortId(d.getValue().id())));
        idCol.setPrefWidth(80);
        idCol.setCellFactory(col -> tooltipCell(MaintenanceTicket::id));

        TableColumn<MaintenanceTicket, String> cycleCol = new TableColumn<>("Cycle");
        cycleCol.setCellValueFactory(d -> new SimpleStringProperty(
                cycleLabelById.getOrDefault(d.getValue().cycleId(), shortId(d.getValue().cycleId()))));
        cycleCol.setPrefWidth(140);
        cycleCol.setCellFactory(col -> tooltipCell(MaintenanceTicket::cycleId));

        TableColumn<MaintenanceTicket, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().issueCategory().name().replace("_", " ")));
        catCol.setPrefWidth(120);

        TableColumn<MaintenanceTicket, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().description()));
        descCol.setPrefWidth(220);
        descCol.setCellFactory(col -> tooltipCell(MaintenanceTicket::description));

        TableColumn<MaintenanceTicket, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status().name()));
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
                    if ("RESOLVED".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: #10B981;");
                    } else if ("IN_PROGRESS".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(59, 130, 246, 0.15); -fx-text-fill: #3B82F6;");
                    } else {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #B45309;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<MaintenanceTicket, String> dateCol = new TableColumn<>("Reported");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().reportedDate()));
        dateCol.setPrefWidth(130);

        TableColumn<MaintenanceTicket, String> costCol = new TableColumn<>("Repair Cost");
        costCol.setCellValueFactory(d -> new SimpleStringProperty(
                Money.formatTaka(d.getValue().repairCostPoisha())));
        costCol.setPrefWidth(100);

        TableColumn<MaintenanceTicket, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(150);
        actionsCol.setCellFactory(col -> new TableCell<>() {
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
                AnimationHelper.addPressAnimation(rowStart);
                AnimationHelper.addPressAnimation(rowResolve);
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

        ticketTableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(MaintenanceTicket item, boolean empty) {
                super.updateItem(item, empty);
                setOpacity(empty || item == null || !item.isResolved() ? 1.0 : 0.55);
            }
        });

        ticketTableView.setPlaceholder(tablePlaceholder());
        ticketTableView.getColumns().setAll(
                List.of(idCol, cycleCol, catCol, descCol, statusCol, dateCol, costCol, actionsCol));
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

    private javafx.scene.Node tablePlaceholder() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        Label msg = new Label();
        msg.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> ticketFilter == TicketFilter.OPEN
                        ? "No open work orders. The fleet is healthy."
                        : "No tickets match this filter.",
                ticketTableView.itemsProperty()));
        msg.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        msg.setWrapText(true);
        Button retry = new Button("Retry");
        retry.getStyleClass().add("secondary-button");
        retry.setOnAction(e -> loadTicketsAsync());
        box.getChildren().addAll(msg, retry);
        return box;
    }

    private static String chipLabel(TicketFilter f) {
        return switch (f) {
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

    private static String shortId(String id) {
        if (id == null) return "—";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    // ------------------------------------------------------------------ actions

    private void startWorkOnTicket(MaintenanceTicket ticket) {
        AppExecutor.asyncThenFx(
                () -> MaintenanceService.getInstance().startWork(ticket.id(), tech),
                ok -> {
                    if (disposed) return;
                    if (Boolean.TRUE.equals(ok)) {
                        AnimationHelper.showToast("Work started on " + shortId(ticket.id()),
                                AnimationHelper.ToastType.INFO);
                        loadTicketsAsync();
                        if (onRefresh != null) onRefresh.run();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Could Not Start Work",
                                "Ticket is no longer OPEN. No changes were made.");
                    }
                },
                err -> {
                    if (disposed) return;
                    showAlert(Alert.AlertType.ERROR, "Could Not Start Work",
                            requestErrorMessage(err) + " No changes were made.");
                });
    }

    private void showResolveModal(MaintenanceTicket ticket) {
        Dialog<ResolveRequest> dialog = new Dialog<>();
        dialog.setTitle("Resolve Work Order");
        dialog.setHeaderText("Resolve ticket " + shortId(ticket.id())
                + " — " + ticket.issueCategory().name().replace("_", " "));
        ThemeManager.install(dialog.getDialogPane());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        Label issueLbl = new Label(ticket.description());
        issueLbl.setWrapText(true);
        issueLbl.setStyle("-fx-font-weight: 600; -fx-opacity: 0.85;");

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Repair notes (e.g. replaced tube, calibrated brakes)...");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);

        TextField costField = new TextField("0.00");
        costField.setPromptText("Repair cost in ৳ (e.g. 150.00)");
        Label costError = new Label();
        costError.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
        costError.setVisible(false);
        costError.setManaged(false);

        // RETIRE is ADMIN-only — technicians choose between service and quarantine.
        List<ReleaseDecision> options = tech.role() == Role.ADMIN
                ? List.of(ReleaseDecision.values())
                : List.of(ReleaseDecision.RETURN_TO_SERVICE, ReleaseDecision.QUARANTINE);
        ComboBox<ReleaseDecision> decisionCombo =
                new ComboBox<>(FXCollections.observableArrayList(options));
        decisionCombo.setValue(ReleaseDecision.RETURN_TO_SERVICE);
        decisionCombo.setTooltip(new Tooltip(tech.role() == Role.ADMIN
                ? "RETIRE permanently withdraws the cycle."
                : "RETIRE requires an admin — ask the cycle office."));

        ComboBox<String> hubCombo = new ComboBox<>(FXCollections.observableArrayList(CampusHubs.names()));
        hubCombo.setPromptText("Keep current location");
        hubCombo.setValue(null);

        grid.add(new Label("Reported Defect:"), 0, 0);
        grid.add(issueLbl, 1, 0);
        grid.add(new Label("Repair Notes:"), 0, 1);
        grid.add(notesArea, 1, 1);
        grid.add(new Label("Repair Cost (৳):"), 0, 2);
        grid.add(costField, 1, 2);
        grid.add(costError, 1, 3);
        grid.add(new Label("Release Decision:"), 0, 4);
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
                return new ResolveRequest(ticket.id(), notes,
                        costField.getText() == null ? "" : costField.getText().trim(),
                        decisionCombo.getValue() != null ? decisionCombo.getValue()
                                : ReleaseDecision.RETURN_TO_SERVICE,
                        hubCombo.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(req -> {
            final int costPoisha;
            try {
                costPoisha = Money.parseBdtToPoisha(req.costText());
            } catch (IllegalArgumentException ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid Repair Cost",
                        ex.getMessage() + " No changes were made.");
                return;
            }
            AppExecutor.asyncThenFx(
                    () -> MaintenanceService.getInstance().resolveTicket(
                            req.ticketId(), req.notes(), costPoisha, req.decision(), req.hub(), tech, repo),
                    ok -> {
                        if (disposed) return;
                        if (Boolean.TRUE.equals(ok)) {
                            AnimationHelper.showToast("Ticket " + shortId(req.ticketId()) + " resolved — "
                                            + req.decision().name().replace("_", " "),
                                    AnimationHelper.ToastType.SUCCESS);
                            loadTicketsAsync();
                            if (onRefresh != null) onRefresh.run();
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Resolution Failed",
                                    "Ticket is not open (it may already be resolved). No changes were made.");
                        }
                    },
                    err -> {
                        if (disposed) return;
                        showAlert(Alert.AlertType.ERROR, "Resolution Failed",
                                requestErrorMessage(err) + " No changes were made.");
                    });
        });
    }

    // ------------------------------------------------------------------ data

    private void loadTicketsAsync() {
        if (refreshing || disposed) return;
        refreshing = true;
        AppExecutor.asyncThenFx(
                () -> {
                    List<MaintenanceTicket> tickets = MaintenanceService.getInstance().getAllTickets();
                    List<CycleItem> cycles;
                    try {
                        cycles = repo.allCycles(tech);
                    } catch (Exception e) {
                        cycles = List.of();
                    }
                    return new TechData(tickets, cycles);
                },
                data -> {
                    try {
                        if (disposed) return;
                        allTicketsMaster.clear();
                        allTicketsMaster.addAll(data.tickets());
                        applyTicketFilter();

                        cycleLabelById.clear();
                        for (CycleItem c : data.cycles()) {
                            cycleLabelById.put(c.id(), c.label());
                        }
                        ticketTableView.refresh();

                        long open = data.tickets().stream()
                                .filter(t -> t.status() == TicketStatus.OPEN).count();
                        long prog = data.tickets().stream()
                                .filter(t -> t.status() == TicketStatus.IN_PROGRESS).count();
                        long res = data.tickets().stream().filter(MaintenanceTicket::isResolved).count();
                        openVal.setText(open + (open == 1 ? " Ticket" : " Tickets"));
                        inProgressVal.setText(prog + (prog == 1 ? " Ticket" : " Tickets"));
                        resolvedVal.setText(res + (res == 1 ? " Ticket" : " Tickets"));
                    } finally {
                        refreshing = false;
                    }
                },
                err -> {
                    try {
                        if (disposed) return;
                        openVal.setText("—");
                        inProgressVal.setText("—");
                        resolvedVal.setText("—");
                        showAlert(Alert.AlertType.ERROR, "Could Not Load Tickets",
                                requestErrorMessage(err));
                    } finally {
                        refreshing = false;
                    }
                });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        if (disposed) return;
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            ThemeManager.install(alert.getDialogPane());
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private static String requestErrorMessage(Throwable err) {
        Throwable cause = (err != null && err.getCause() != null) ? err.getCause() : err;
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return "Unexpected error.";
    }

    private record ResolveRequest(String ticketId, String notes, String costText,
                                  ReleaseDecision decision, String hub) {}

    private record TechData(List<MaintenanceTicket> tickets, List<CycleItem> cycles) {}
}
