package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.TariffService;
import bd.ac.kuet.campuscycle.domain.event.RentalReturnedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.MaintenanceService;
import bd.ac.kuet.campuscycle.service.WalletService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Consumer-grade Active Journey Cockpit.
 * Features:
 * 1. Live session status banner with pulsing active indicator and cycle identification
 * 2. High-precision elapsed time counter (HH:MM:SS) updating live every second
 * 3. Dynamic fare calculation (base + duration blocks + student subsidy)
 * 4. Dropdown campus return hub selection
 * 5. Built-in damage and mechanical defect reporting subform
 * 6. Non-blocking checkout with automated wallet fare deduction and maintenance ticketing
 * 7. Digital transit receipt popup upon return confirmation
 */
public class ActiveJourneyView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<String> onNavigate;
    private final Runnable onRideFinished;

    private final Label timerLabel = new Label("00:00:00");
    private final Label fareLabel = new Label("৳ 20.00");
    private final Label fareSubLabel = new Label("Base 15m @ ৳20 + ৳10/15m");
    private final Label pickupStationLabel = new Label("KUET Central Library");

    private Timeline ticker;
    private ScaleTransition pulseTransition;
    private volatile boolean disposed = false;
    private int calculatedFarePoisha = TariffService.BASE_CHARGE_POISHA;

    public ActiveJourneyView(CampusUser user,
                             CampusRepository repo,
                             Consumer<String> onNavigate,
                             Runnable onRideFinished) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = repo;
        this.onNavigate = onNavigate;
        this.onRideFinished = onRideFinished;

        setSpacing(24);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        Label loading = new Label("Loading active journey session...");
        loading.setStyle("-fx-font-size: 14px; -fx-opacity: 0.75;");
        getChildren().add(loading);

        AppExecutor.asyncThenFx(
                () -> {
                    RentalRecord active = null;
                    try {
                        active = repo.activeRental(user);
                    } catch (Exception ignored) {}

                    if (active == null) {
                        try {
                            active = LocalDatabase.getInstance().getActiveRental(user.id()).orElse(null);
                        } catch (Exception ignored) {}
                    }

                    String pickupStation = "KUET Central Library";
                    if (active != null) {
                        try {
                            Optional<CycleItem> cycleOpt = LocalDatabase.getInstance().getCycleById(active.cycleId());
                            if (cycleOpt.isPresent() && cycleOpt.get().pickupPoint() != null) {
                                pickupStation = cycleOpt.get().pickupPoint();
                            } else {
                                for (CycleItem ci : repo.catalog(user)) {
                                    if (ci.id().equals(active.cycleId())) {
                                        pickupStation = ci.pickupPoint();
                                        break;
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    return new ActiveSessionData(active, pickupStation);
                },
                sessionData -> {
                    getChildren().clear();
                    if (disposed) return;

                    RentalRecord active = sessionData.record;
                    if (active == null) {
                        getChildren().add(createNoActiveRideView());
                    } else {
                        pickupStationLabel.setText(sessionData.pickupStation);
                        getChildren().addAll(
                                createStatusBanner(active),
                                createMetricsRow(active),
                                createReturnCard(active, sessionData.pickupStation)
                        );
                        startLiveTicker(active);
                    }
                },
                err -> {
                    getChildren().clear();
                    if (!disposed) {
                        getChildren().add(createNoActiveRideView());
                    }
                }
        );

        ThemeManager.applyFadeIn(this);
    }

    private record ActiveSessionData(RentalRecord record, String pickupStation) {}

    /** Dispose running timeline and animations when navigating away. */
    public void dispose() {
        disposed = true;
        if (ticker != null) ticker.stop();
        if (pulseTransition != null) pulseTransition.stop();
    }

    private VBox createNoActiveRideView() {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(64, 44, 64, 44));
        box.getStyleClass().add("bento-card");
        box.setMaxWidth(620);

        StackPane iconCircle = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 32, Color.web("#10B981")));
        iconCircle.setPrefSize(72, 72);
        iconCircle.setStyle("-fx-background-color: -fx-teal-soft; -fx-background-radius: 999px;");

        Label title = new Label("No Active Ride");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label sub = new Label("You don't have an ongoing bike rental. Browse the campus fleet to unlock a bicycle from any of our 5 KUET stations.");
        sub.setWrapText(true);
        sub.setStyle("-fx-font-size: 13.5px; -fx-opacity: 0.75; -fx-text-alignment: center;");
        sub.setMaxWidth(440);

        Button browseBtn = new Button("Browse Available Cycles");
        browseBtn.getStyleClass().add("primary-button");
        browseBtn.setStyle("-fx-font-size: 13px; -fx-padding: 11px 26px;");
        browseBtn.setOnAction(e -> onNavigate.accept("Fleet Catalog"));

        box.getChildren().addAll(iconCircle, title, sub, browseBtn);
        return box;
    }

    private VBox createStatusBanner(RentalRecord active) {
        VBox banner = new VBox(12);
        banner.getStyleClass().add("bento-card");
        banner.setPadding(new Insets(20, 24, 20, 24));
        banner.setMaxWidth(Double.MAX_VALUE);

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Animated pulsing green status dot
        Circle pulseDot = new Circle(6, Color.web("#10B981"));
        pulseTransition = new ScaleTransition(Duration.millis(900), pulseDot);
        pulseTransition.setFromX(0.8);
        pulseTransition.setFromY(0.8);
        pulseTransition.setToX(1.35);
        pulseTransition.setToY(1.35);
        pulseTransition.setAutoReverse(true);
        pulseTransition.setCycleCount(Animation.INDEFINITE);
        pulseTransition.play();

        Label pulseText = new Label("ACTIVE RIDE SESSION");
        pulseText.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #10B981; -fx-letter-spacing: 0.06em;");

        HBox pulseBadge = new HBox(8, pulseDot, pulseText);
        pulseBadge.setAlignment(Pos.CENTER_LEFT);
        pulseBadge.setStyle("-fx-background-color: -fx-teal-soft; -fx-background-radius: 999px; -fx-padding: 4px 14px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String shortId = active.id().length() > 8 ? active.id().substring(0, 8) : active.id();
        Label rentalIdBadge = new Label("Rental #" + shortId);
        rentalIdBadge.getStyleClass().add("filter-chip");

        topRow.getChildren().addAll(pulseBadge, spacer, rentalIdBadge);

        HBox bottomRow = new HBox(16);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        StackPane bikeIconBox = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 22, Color.web("#10B981")));
        bikeIconBox.setPrefSize(44, 44);
        bikeIconBox.setStyle("-fx-background-color: -fx-surface-alt; -fx-background-radius: 12px; -fx-border-color: -fx-border-c; -fx-border-radius: 12px;");

        VBox titleCol = new VBox(3);
        Label cycleTitle = new Label(active.cycleLabel());
        cycleTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a");
        Label startedLabel = new Label("Commute started at " + active.startedAt().format(dtf) + " • Smart Lock Engaged");
        startedLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");

        titleCol.getChildren().addAll(cycleTitle, startedLabel);
        bottomRow.getChildren().addAll(bikeIconBox, titleCol);

        banner.getChildren().addAll(topRow, bottomRow);
        return banner;
    }

    private GridPane createMetricsRow(RentalRecord active) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMaxWidth(Double.MAX_VALUE);

        // 1. Elapsed Time Card
        VBox timeCard = createMetricCard("ELAPSED TIME", timerLabel, "Live Session Clock", ThemeManager.ICON_CLOCK, "#10B981", true);

        // 2. Dynamic Current Fare Card
        VBox fareCard = createFareMetricCard("CURRENT FARE", fareLabel, fareSubLabel, ThemeManager.ICON_SHIELD, "#0EA5E9");

        // 3. Pickup Hub Card
        pickupStationLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");
        VBox pickupCard = createMetricCard("PICKUP HUB", pickupStationLabel, "Commute Origin Station", ThemeManager.ICON_PIN, "#F59E0B", false);

        grid.add(timeCard, 0, 0);
        grid.add(fareCard, 1, 0);
        grid.add(pickupCard, 2, 0);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(33.33);
            grid.getColumnConstraints().add(col);
        }

        return grid;
    }

    private VBox createMetricCard(String label, Label valueLabel, String sub, String svgIcon, String accentHex, boolean isMonoTimer) {
        VBox card = new VBox(8);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(18, 22, 18, 22));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(svgIcon, 15, Color.web(accentHex)));
        iconBadge.setPrefSize(30, 30);
        iconBadge.getStyleClass().add("action-icon-btn");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        if (isMonoTimer) {
            valueLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: 800; -fx-font-family: 'Consolas', 'JetBrains Mono', 'Segoe UI', monospace; -fx-text-fill: -fx-ink-900;");
        } else {
            valueLabel.getStyleClass().add("metric-number");
        }

        Label badge = new Label(sub);
        badge.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, valueLabel, badge);
        return card;
    }

    private VBox createFareMetricCard(String label, Label valueLabel, Label subLabel, String svgIcon, String accentHex) {
        VBox card = new VBox(8);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(18, 22, 18, 22));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(svgIcon, 15, Color.web(accentHex)));
        iconBadge.setPrefSize(30, 30);
        iconBadge.getStyleClass().add("action-icon-btn");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        valueLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: 800; -fx-text-fill: #10B981;");

        subLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8; -fx-font-weight: 600;");

        card.getChildren().addAll(top, valueLabel, subLabel);
        return card;
    }

    private VBox createReturnCard(RentalRecord active, String pickupStation) {
        VBox card = new VBox(20);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24, 28, 26, 28));
        card.setMaxWidth(Double.MAX_VALUE);

        // Header
        VBox titleBox = new VBox(3);
        Label headerTitle = new Label("Return & Checkout");
        headerTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: 800;");

        Label headerSub = new Label("Confirm dock destination and inspect cycle before engaging physical lock.");
        headerSub.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.7;");
        titleBox.getChildren().addAll(headerTitle, headerSub);

        // Destination Hub Selection
        VBox hubGroup = new VBox(8);
        Label dropLbl = new Label("SELECT DESTINATION RETURN HUB / DOCK");
        dropLbl.getStyleClass().add("metric-label");

        ComboBox<String> hubCombo = new ComboBox<>();
        hubCombo.getItems().addAll(CampusHubs.names());
        hubCombo.setValue(CampusHubs.names().contains("Student Welfare Centre") ? "Student Welfare Centre" : CampusHubs.names().get(0));
        hubCombo.setMaxWidth(Double.MAX_VALUE);
        hubCombo.getStyleClass().add("modern-input");

        hubGroup.getChildren().addAll(dropLbl, hubCombo);

        // Damage / Issue Report subform
        VBox damageSubform = new VBox(12);
        damageSubform.getStyleClass().add("sub-panel");
        damageSubform.setPadding(new Insets(16, 18, 16, 18));

        CheckBox damageCheckbox = new CheckBox("Report a mechanical issue or damage");
        damageCheckbox.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");

        VBox issueDetailsBox = new VBox(10);
        issueDetailsBox.managedProperty().bind(damageCheckbox.selectedProperty());
        issueDetailsBox.visibleProperty().bind(damageCheckbox.selectedProperty());

        Label categoryLabel = new Label("ISSUE CATEGORY");
        categoryLabel.getStyleClass().add("metric-label");

        ComboBox<String> issueCategoryCombo = new ComboBox<>();
        issueCategoryCombo.getItems().addAll(
                "Flat Tire",
                "Brake Issue",
                "Chain / Gear",
                "Battery / Electrical",
                "Frame Damage"
        );
        issueCategoryCombo.setValue("Brake Issue");
        issueCategoryCombo.setMaxWidth(Double.MAX_VALUE);
        issueCategoryCombo.getStyleClass().add("modern-input");

        Label notesLabel = new Label("SHORT NOTES / FAULT DESCRIPTION");
        notesLabel.getStyleClass().add("metric-label");

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Describe the defect (e.g. rear brake loose, chain slipping, tire puncture)...");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);
        notesArea.getStyleClass().add("modern-input");

        issueDetailsBox.getChildren().addAll(categoryLabel, issueCategoryCombo, notesLabel, notesArea);
        damageSubform.getChildren().addAll(damageCheckbox, issueDetailsBox);

        // Action Return Button
        HBox bottom = new HBox(16);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        Button returnBtn = new Button("Lock & Return Cycle");
        returnBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_LOCK, 15, Color.WHITE));
        returnBtn.setStyle("-fx-background-color: linear-gradient(to right, #10B981, #0EA5E9); -fx-text-fill: white; -fx-font-size: 13.5px; -fx-font-weight: 800; -fx-padding: 12px 28px; -fx-background-radius: 999px; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(16, 185, 129, 0.35), 16, 0, 0, 4);");

        returnBtn.setOnAction(e -> {
            returnBtn.setDisable(true);
            returnBtn.setText("Processing Return & Fare Settlement...");

            final String selectedHub = hubCombo.getValue();
            final boolean hasDamage = damageCheckbox.isSelected();
            final String issueCategory = issueCategoryCombo.getValue();
            final String issueNotes = notesArea.getText() != null ? notesArea.getText().trim() : "";
            final int finalFare = calculatedFarePoisha;

            AppExecutor.asyncThenFx(
                    () -> {
                        // 1. Supabase repository return
                        try {
                            repo.returnRental(user, active.id());
                        } catch (Exception ignored) {}

                        // 2. SQLite local database update
                        LocalDatabase.getInstance().updateRentalReturned(active.id());
                        LocalDatabase.getInstance().updateCycleAvailability(active.cycleId(), AvailabilityStatus.AVAILABLE);

                        // 3. Deduct fare from Campus Wallet
                        WalletService.getInstance().deductFare(
                                user,
                                finalFare,
                                active.id(),
                                "Transit Fare: " + active.cycleLabel() + " (" + timerLabel.getText() + ")"
                        );

                        // 4. If damage reported: flag maintenance ticket & set cycle to MAINTENANCE
                        String ticketId = null;
                        if (hasDamage) {
                            var ticket = MaintenanceService.getInstance().reportDamage(
                                    active.cycleId(),
                                    user,
                                    issueCategory,
                                    issueNotes
                            );
                            ticketId = ticket.id();
                        }

                        // 5. Publish EventBus notification
                        EventBus.getInstance().publish(new RentalReturnedEvent(user, active.id(), Instant.now()));

                        return new ReturnResult(true, selectedHub, finalFare, timerLabel.getText(), ticketId);
                    },
                    result -> {
                        if (ticker != null) ticker.stop();
                        if (pulseTransition != null) pulseTransition.stop();
                        showCompletionReceiptPopup(active, pickupStation, result);
                    },
                    error -> {
                        returnBtn.setDisable(false);
                        returnBtn.setText("Lock & Return Cycle");
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Return failed: " + error.getMessage(), ButtonType.OK);
                        alert.showAndWait();
                    }
            );
        });

        bottom.getChildren().add(returnBtn);
        card.getChildren().addAll(titleBox, hubGroup, damageSubform, bottom);
        return card;
    }

    private record ReturnResult(boolean success, String returnHub, int farePoisha, String durationFormatted, String ticketId) {}

    private void showCompletionReceiptPopup(RentalRecord active, String pickupHub, ReturnResult result) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("KUET CampusCycle — Transit Receipt");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        ThemeManager.install(pane);
        pane.getStyleClass().add("modal-sheet");
        pane.setMinWidth(480);

        VBox content = new VBox(16);
        content.setPadding(new Insets(10, 8, 10, 8));

        // Header check
        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);

        StackPane checkCircle = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_CHECK, 20, Color.web("#10B981")));
        checkCircle.setPrefSize(42, 42);
        checkCircle.setStyle("-fx-background-color: -fx-teal-soft; -fx-background-radius: 999px;");

        VBox headText = new VBox(2);
        Label t1 = new Label("Ride Completed Successfully!");
        t1.setStyle("-fx-font-size: 17px; -fx-font-weight: 800;");
        Label t2 = new Label("Cycle locked & docked at " + result.returnHub);
        t2.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        headText.getChildren().addAll(t1, t2);
        head.getChildren().addAll(checkCircle, headText);

        // Receipt Summary Box
        VBox receiptCard = new VBox(10);
        receiptCard.getStyleClass().add("sub-panel");
        receiptCard.setPadding(new Insets(16));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);

        grid.add(createReceiptRow("RIDER", user.displayName()), 0, 0);
        grid.add(createReceiptRow("CYCLE MODEL", active.cycleLabel()), 1, 0);
        grid.add(createReceiptRow("DEPARTURE HUB", pickupHub), 0, 1);
        grid.add(createReceiptRow("ARRIVAL HUB", result.returnHub), 1, 1);
        grid.add(createReceiptRow("COMMUTE TIME", result.durationFormatted), 0, 2);
        grid.add(createReceiptRow("TOTAL FARE", String.format("৳ %.2f", result.farePoisha / 100.0)), 1, 2);
        grid.add(createReceiptRow("PAYMENT", "Campus Digital Wallet (Deducted)"), 0, 3, 2, 1);

        receiptCard.getChildren().add(grid);
        content.getChildren().addAll(head, receiptCard);

        if (result.ticketId != null) {
            HBox ticketNotice = new HBox(8);
            ticketNotice.setAlignment(Pos.CENTER_LEFT);
            ticketNotice.setStyle("-fx-background-color: rgba(245, 158, 11, 0.12); -fx-background-radius: 10px; -fx-padding: 10px 14px; -fx-border-color: #F59E0B; -fx-border-radius: 10px;");
            Label warnIcon = new Label("⚠️");
            Label ticketText = new Label("Maintenance ticket #" + result.ticketId + " logged. Cycle flagged for technical inspection.");
            ticketText.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #D97706;");
            ticketNotice.getChildren().addAll(warnIcon, ticketText);
            content.getChildren().add(ticketNotice);
        }

        pane.setContent(content);

        ButtonType viewPassbookBtn = new ButtonType("View Passbook", ButtonBar.ButtonData.OK_DONE);
        ButtonType dashboardBtn = new ButtonType("Back to Dashboard", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(viewPassbookBtn, dashboardBtn);

        Optional<ButtonType> chosen = dialog.showAndWait();
        if (chosen.isPresent() && chosen.get() == viewPassbookBtn) {
            onNavigate.accept("Passbook");
        } else {
            onRideFinished.run();
        }
    }

    private VBox createReceiptRow(String label, String value) {
        VBox b = new VBox(2);
        Label l = new Label(label);
        l.getStyleClass().add("metric-label");
        l.setStyle("-fx-font-size: 9px;");

        Label v = new Label(value);
        v.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700;");
        b.getChildren().addAll(l, v);
        return b;
    }

    private void startLiveTicker(RentalRecord active) {
        long startSeconds = active.startedAt().toInstant().getEpochSecond();
        boolean isStudent = user.role() == Role.STUDENT;

        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (disposed) {
                ticker.stop();
                return;
            }
            long nowSec = Instant.now().getEpochSecond();
            long elapsed = Math.max(0, nowSec - startSeconds);

            long hours = elapsed / 3600;
            long mins = (elapsed % 3600) / 60;
            long secs = elapsed % 60;

            timerLabel.setText(String.format("%02d:%02d:%02d", hours, mins, secs));

            // Dynamic tariff: base 15 min + extra blocks of 15 min
            int elapsedMinutes = (int) Math.max(1, Math.ceil(elapsed / 60.0));
            int blocks = elapsedMinutes <= TariffService.BASE_MINUTES ? 0 :
                    (int) Math.ceil((elapsedMinutes - TariffService.BASE_MINUTES) / (double) TariffService.EXTRA_BLOCK_MINUTES);
            int basePoisha = TariffService.BASE_CHARGE_POISHA + blocks * TariffService.EXTRA_BLOCK_CHARGE_POISHA;

            calculatedFarePoisha = isStudent ?
                    (int) Math.round(basePoisha * (1.0 - TariffService.STUDENT_SUBSIDY_RATE)) : basePoisha;

            fareLabel.setText(String.format("৳ %.2f", calculatedFarePoisha / 100.0));
            if (isStudent) {
                fareSubLabel.setText("৳ " + String.format("%.2f", basePoisha / 100.0) + " base (25% KUET Subsidy Applied)");
            } else {
                fareSubLabel.setText("Base 15m @ ৳20 + ৳10/15m block");
            }
        }));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }
}
