package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusTime;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.IssueCategory;
import bd.ac.kuet.campuscycle.domain.MaintenanceTicket;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.RentalStatus;
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
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 *
 * <p>Return-pipeline contract (P-018, P-019, P-027, P-035, P-104, P-173): the
 * {@code returnRental} state transition is never swallowed — on failure nothing
 * is charged and no receipt or event follows. Every charge result is checked; a
 * failed payment shows an explicit outstanding-balance state instead of a
 * success receipt. Billing uses started blocks; overdue grace/rate come from
 * {@link TariffService}.
 */
public class ActiveJourneyView extends VBox {

    private static final Logger LOGGER = Logger.getLogger(ActiveJourneyView.class.getName());

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<String> onNavigate;
    private final Runnable onRideFinished;

    private final Label timerLabel = new Label("00:00:00");
    private final Label fareLabel = new Label("৳ 20.00");
    private final Label fareSubLabel = new Label("Base 15m @ ৳20 + ৳10/15m");
    private final Label pickupStationLabel = new Label("KUET Central Mosque");

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
        setPadding(new Insets(6, 16, 24, 16));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-background-color: transparent;");

        Label loading = new Label("Loading active journey session...");
        loading.setStyle("-fx-font-size: 14px; -fx-opacity: 0.75;");
        getChildren().add(loading);

        AppExecutor.asyncThenFx(
                () -> {
                    RentalRecord active = null;
                    try {
                        active = repo.activeRental(user);
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "Active-rental lookup failed; showing no-ride view.", e);
                    }

                    String pickupStation = "KUET Central Mosque";
                    if (active != null) {
                        try {
                            Optional<CycleItem> cycleOpt = repo.getCycleById(active.cycleId());
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
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Pickup-station resolution failed; using default.", e);
                        }
                    }

                    // Pre-check open tickets so a bike with existing work orders
                    // can never slip back to AVAILABLE silently (P-035).
                    boolean hasOpenTicket = false;
                    CampusRepository.PaymentMethod method = CampusRepository.PaymentMethod.CAMPUS_PAY;
                    if (active != null) {
                        try {
                            String cycleId = active.cycleId();
                            hasOpenTicket = MaintenanceService.getInstance().getOpenTickets().stream()
                                    .anyMatch(t -> t.cycleId().equals(cycleId));
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Open-ticket pre-check failed; defaulting to no open ticket.", e);
                        }
                        try {
                            method = repo.rentalPaymentMethod(active.id());
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Payment-method lookup failed; assuming Campus Pay.", e);
                        }
                    }

                    return new ActiveSessionData(active, pickupStation, hasOpenTicket, method);
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
                                createReturnCard(active, sessionData.pickupStation, sessionData.hasOpenTicket,
                                        sessionData.method)
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

    private record ActiveSessionData(RentalRecord record, String pickupStation, boolean hasOpenTicket,
                                     CampusRepository.PaymentMethod method) {}

    /** Dispose running timeline and animations when navigating away. */
    public void dispose() {
        disposed = true;
        if (ticker != null) ticker.stop();
        if (pulseTransition != null) pulseTransition.stop();
    }

    private static int subsidyPercent() {
        return (int) Math.round(TariffService.STUDENT_SUBSIDY_RATE * 100);
    }

    private javafx.scene.Node createNoActiveRideView() {
        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(42, 54, 46, 54));
        box.setMaxWidth(660);
        box.getStyleClass().add("bento-card");

        // Procedural vector empty ride illustration (skyline, trees, signpost, bicycle)
        javafx.scene.canvas.Canvas emptyArt = CampusVectorArt.createNoActiveRideIllustration(440, 185);

        Label sub = new Label("You don't have an ongoing bike rental. Browse the campus fleet to unlock a bicycle from any of our 5 KUET stations.");
        sub.setWrapText(true);
        sub.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: -fx-ink-700; -fx-text-alignment: center; -fx-line-spacing: 3px;");
        sub.setMaxWidth(480);

        Button browseBtn = new Button("Browse Available Cycles  →");
        browseBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 15, Color.WHITE));
        browseBtn.getStyleClass().add("primary-button");
        browseBtn.setOnAction(e -> onNavigate.accept("Fleet Catalog"));

        box.getChildren().addAll(emptyArt, sub, browseBtn);

        StackPane centerWrapper = new StackPane(box);
        centerWrapper.setAlignment(Pos.CENTER);
        centerWrapper.setPadding(new Insets(50, 20, 60, 20));
        return centerWrapper;
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

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a", Locale.US);
        Label startedLabel = new Label("Commute started at " + active.startedAt().format(dtf));
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
        iconBadge.getStyleClass().add("icon-badge");

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
        iconBadge.getStyleClass().add("icon-badge");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        valueLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: 800; -fx-text-fill: #10B981;");

        subLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8; -fx-font-weight: 600;");

        card.getChildren().addAll(top, valueLabel, subLabel);
        return card;
    }

    private VBox createReturnCard(RentalRecord active, String pickupStation, boolean hasOpenTicket,
                                  CampusRepository.PaymentMethod method) {
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
        dropLbl.setLabelFor(hubCombo);
        hubCombo.setAccessibleText("Destination return hub");

        Label trustNote = new Label("ℹ️ Hub return operates on KUET honor system. Please ensure the bicycle is physically docked within the selected hub's perimeter.");
        trustNote.setStyle("-fx-font-size: 11px; -fx-opacity: 0.65; -fx-wrap-text: true;");

        hubGroup.getChildren().addAll(dropLbl, hubCombo, trustNote);

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

        ComboBox<IssueCategory> issueCategoryCombo = new ComboBox<>();
        issueCategoryCombo.getItems().setAll(IssueCategory.values());
        issueCategoryCombo.setValue(IssueCategory.BRAKE_ISSUE);
        issueCategoryCombo.setMaxWidth(Double.MAX_VALUE);
        issueCategoryCombo.getStyleClass().add("modern-input");
        issueCategoryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(IssueCategory category) {
                return category == null ? "" : toDisplayName(category);
            }

            @Override
            public IssueCategory fromString(String text) {
                if (text == null) return IssueCategory.ROUTINE_CHECKUP;
                for (IssueCategory c : IssueCategory.values()) {
                    if (toDisplayName(c).equalsIgnoreCase(text.trim()) || c.name().equalsIgnoreCase(text.trim())) {
                        return c;
                    }
                }
                return IssueCategory.ROUTINE_CHECKUP;
            }
        });
        categoryLabel.setLabelFor(issueCategoryCombo);
        issueCategoryCombo.setAccessibleText("Issue category");

        Label notesLabel = new Label("SHORT NOTES / FAULT DESCRIPTION");
        notesLabel.getStyleClass().add("metric-label");

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Describe the defect (e.g. rear brake loose, chain slipping, tire puncture)...");
        notesArea.setPrefRowCount(3);
        notesArea.setWrapText(true);
        notesArea.getStyleClass().add("modern-input");
        notesLabel.setLabelFor(notesArea);
        notesArea.setAccessibleText("Fault description");

        issueDetailsBox.getChildren().addAll(categoryLabel, issueCategoryCombo, notesLabel, notesArea);
        damageSubform.getChildren().addAll(damageCheckbox, issueDetailsBox);

        // Cash-at-hub collection: only shown for dock rides, and only the hub
        // attendant can say how much cash actually came in. Campus Pay rides never
        // see it — their money comes from the wallet.
        VBox cashGroup = new VBox(8);
        Label cashLabel = new Label("CASH RECEIVED AT HUB");
        cashLabel.getStyleClass().add("metric-label");

        TextField cashField = new TextField();
        cashField.setPromptText("0.00");
        cashField.getStyleClass().add("modern-input");
        cashField.setAccessibleText("Cash received at hub in taka");
        cashField.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));

        Label cashHint = new Label("The owner is paid from the cash confirmed here. Any gap becomes an unpaid due on the rider's account.");
        cashHint.setWrapText(true);
        cashHint.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

        cashGroup.getChildren().addAll(cashLabel, cashField, cashHint);
        cashGroup.setVisible(false);
        cashGroup.setManaged(false);
        if (method == CampusRepository.PaymentMethod.DOCK_PAY) {
            cashGroup.setVisible(true);
            cashGroup.setManaged(true);
        }

        // An existing open ticket forces a maintenance return (P-035).
        if (hasOpenTicket) {
            damageCheckbox.setSelected(true);
            damageCheckbox.setDisable(true);
            Label openTicketNote = new Label("An open maintenance ticket already exists for this cycle — it will return to maintenance and this cannot be unchecked.");
            openTicketNote.setWrapText(true);
            openTicketNote.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #D97706;");
            damageSubform.getChildren().add(openTicketNote);
        }

        // Action Return Button
        HBox bottom = new HBox(16);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        Button returnBtn = new Button("Lock & Return Cycle");
        returnBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_LOCK, 15, Color.WHITE));
        returnBtn.setStyle("-fx-background-color: linear-gradient(to right, #10B981, #0EA5E9); -fx-text-fill: white; -fx-font-size: 13.5px; -fx-font-weight: 800; -fx-padding: 12px 28px; -fx-background-radius: 999px; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(16, 185, 129, 0.35), 16, 0, 0, 4);");

        returnBtn.setOnAction(e -> {
            // Snapshot FX state on the FX thread BEFORE dispatch, and freeze the clock (P-104).
            final String durationSnapshot = timerLabel.getText();
            if (ticker != null) ticker.stop();
            returnBtn.setDisable(true);
            returnBtn.setText("Processing Return & Fare Settlement...");

            final String selectedHub = hubCombo.getValue();
            final boolean hasDamage = damageCheckbox.isSelected();
            final IssueCategory selectedCategory = issueCategoryCombo.getValue();
            final String issueNotes = notesArea.getText() != null ? notesArea.getText().trim() : "";
            final int finalFare = calculatedFarePoisha;
            final boolean cashRide = method == CampusRepository.PaymentMethod.DOCK_PAY;
            final int cashEntered = parseTakaToPoisha(cashField.getText());

            AppExecutor.asyncThenFx(
                    () -> {
                        int prepaid = active.quotedAmountPoisha();
                        int overtime = prepaid > 0 ? Math.max(0, finalFare - prepaid) : finalFare;

                        // Overdue fine from TariffService constants, uncapped: the
                        // deterrent stays unbounded; whatever cannot be collected
                        // becomes a booked due.
                        int finePoisha = 0;
                        if (active.dueAt() != null) {
                            long overdueSeconds = java.time.Duration.between(active.dueAt(), CampusTime.now()).getSeconds();
                            if (overdueSeconds > TariffService.OVERDUE_GRACE_SECONDS) {
                                long over = overdueSeconds - TariffService.OVERDUE_GRACE_SECONDS;
                                int blocks = (int) ((over + TariffService.OVERDUE_FINE_BLOCK_SECONDS - 1)
                                        / TariffService.OVERDUE_FINE_BLOCK_SECONDS);
                                finePoisha = blocks * TariffService.OVERDUE_BLOCK_POISHA;
                            }
                        }

                        // 1. SETTLE. One transaction does all of it: takes the money
                        //    (wallet for Campus Pay, hub cash for Dock Pay), settles the
                        //    ride, and pays the owner min(settled payout, collected).
                        //    All or nothing — a failure charges nothing and leaves the
                        //    ride active (P-018).
                        AvailabilityStatus returnStatus =
                                (hasDamage || hasOpenTicket) ? AvailabilityStatus.MAINTENANCE : AvailabilityStatus.AVAILABLE;
                        // The fare and the fine are the database's to work out from the
                        // real elapsed time; the only figure the client owns is the cash
                        // the hub attendant counted.
                        CampusRepository.ReturnCharge charge =
                                new CampusRepository.ReturnCharge(cashRide ? cashEntered : 0);
                        CampusRepository.SettlementOutcome outcome = repo.settleReturn(
                                user, active.id(), active.cycleId(), finalFare, selectedHub, returnStatus,
                                method, charge);

                        // The wallet was moved by the settlement itself, not through
                        // WalletService, so announce the new balance: every open
                        // dashboard and passbook listens for this and would otherwise
                        // keep showing the pre-return figure.
                        WalletService.getInstance().refreshAndNotifyBalance(user.id());

                        // 2. Whatever the platform could not collect was booked as a due
                        //    inside that same transaction, so it can never be lost. All
                        //    that is left here is to report it honestly.
                        int shortfall = outcome.shortfallPoisha();
                        boolean paymentFailed = shortfall > 0;
                        int outstandingPoisha = shortfall;
                        String duesError = outcome.dueId() == null && shortfall > 0
                                ? "the unpaid balance could not be saved — please settle it at the cycle office"
                                : null;
                        // The fine shown is the one the database recomputed from the real
                        // elapsed time, not what this client asked for.
                        int effectiveFine = outcome.fineBilledPoisha();

                        // 3. Damage report: best-effort after the return. A failure is
                        //    surfaced on the receipt, never disguised as a failed return.
                        String ticketId = null;
                        String damageError = null;
                        if (hasDamage) {
                            try {
                                MaintenanceTicket ticket = MaintenanceService.getInstance().reportDamage(
                                        active.cycleId(), user, selectedCategory.name(), issueNotes);
                                ticketId = ticket.id();
                            } catch (Exception damageFailure) {
                                damageError = damageFailure.getMessage();
                                LOGGER.log(Level.WARNING, "Damage report failed for cycle " + active.cycleId(), damageFailure);
                            }
                        }

                        // 4. Publish, and build the settled record for fare+fine=total.
                        EventBus.getInstance().publish(new RentalReturnedEvent(user, active.id(), Instant.now()));
                        RentalRecord settled = new RentalRecord(
                                active.id(), active.cycleId(), active.cycleLabel(), active.renterId(),
                                active.requestedMinutes(), active.quotedAmountPoisha(),
                                outcome.finalAmountPoisha(), effectiveFine, selectedHub, RentalStatus.RETURNED,
                                active.startedAt(), active.dueAt(), CampusTime.now(),
                                outcome.platformFeePoisha(),
                                outcome.ownerPayoutCreditedPoisha());

                        return new ReturnResult(true, selectedHub, outcome.finalAmountPoisha(), prepaid, effectiveFine,
                                paymentFailed, outstandingPoisha, durationSnapshot, ticketId, damageError,
                                duesError, settled, cashRide, cashEntered);
                    },
                    result -> {
                        if (ticker != null) ticker.stop();
                        if (pulseTransition != null) pulseTransition.stop();
                        if (result.paymentFailed()) {
                            showPaymentFailedPopup(active, pickupStation, result);
                        } else {
                            showCompletionReceiptPopup(active, pickupStation, result);
                        }
                    },
                    error -> {
                        returnBtn.setDisable(false);
                        returnBtn.setText("Lock & Return Cycle");
                        Alert alert = new Alert(Alert.AlertType.ERROR,
                                "Return failed — nothing was charged. Please retry: " + error.getMessage(), ButtonType.OK);
                        alert.showAndWait();
                    }
            );
        });

        bottom.getChildren().add(returnBtn);
        card.getChildren().addAll(titleBox, hubGroup, cashGroup, damageSubform, bottom);
        return card;
    }

    private static String toDisplayName(IssueCategory category) {
        String[] parts = category.name().toLowerCase(Locale.US).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private record ReturnResult(boolean success, String returnHub, int farePoisha, int prepaidPoisha,
                                int finePoisha, boolean paymentFailed, int outstandingPoisha,
                                String durationFormatted, String ticketId, String damageError,
                                String duesError, RentalRecord settled, boolean cashRide,
                                int cashEnteredPoisha) {}

    /** Parses a taka amount ("12.50", "12") into poisha; anything odd reads as zero. */
    private static int parseTakaToPoisha(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            double taka = Double.parseDouble(text.trim());
            if (taka <= 0 || Double.isNaN(taka) || Double.isInfinite(taka)) {
                return 0;
            }
            return (int) Math.round(taka * 100.0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showPaymentFailedPopup(RentalRecord active, String pickupHub, ReturnResult result) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("KUET CampusCycle — Payment Incomplete");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        ThemeManager.install(pane);
        pane.getStyleClass().add("modal-sheet");
        pane.setMinWidth(480);

        VBox content = new VBox(16);
        content.setPadding(new Insets(10, 8, 10, 8));

        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);

        StackPane warnCircle = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_CLOCK, 20, Color.web("#D97706")));
        warnCircle.setPrefSize(42, 42);
        warnCircle.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-background-radius: 999px;");

        VBox headText = new VBox(2);
        Label t1 = new Label("Returned with an unpaid balance of " + Money.formatTaka(result.outstandingPoisha));
        t1.setStyle("-fx-font-size: 17px; -fx-font-weight: 800;");
        t1.setWrapText(true);
        Label t2 = new Label("Cycle returned to " + result.returnHub + ". The shortfall is recorded on your account"
                + (result.cashRide ? " from the cash taken at the hub." : " and blocks your next Campus Pay ride until cleared."));
        t2.setWrapText(true);
        t2.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        headText.getChildren().addAll(t1, t2);
        head.getChildren().addAll(warnCircle, headText);

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
        grid.add(createReceiptRow("TOTAL FARE", Money.formatTaka(result.farePoisha)), 1, 2);
        grid.add(createReceiptRow("PLATFORM FEE",
                Money.formatTaka(TariffService.platformFeePoisha(result.farePoisha))
                        + " (5%, capped — included above)"), 0, 3, 2, 1);
        if (result.finePoisha > 0) {
            grid.add(createReceiptRow("OVERDUE FINE", Money.formatTaka(result.finePoisha)), 0, 4);
        }
        grid.add(createReceiptRow("COLLECTED",
                Money.formatTaka(result.farePoisha + result.finePoisha - result.outstandingPoisha)), 1, 4);
        grid.add(createReceiptRow("UNPAID BALANCE",
                Money.formatTaka(result.outstandingPoisha)
                        + (result.duesError == null
                        ? " — recorded as an unpaid due on your account."
                        : " — could NOT be recorded (" + result.duesError
                          + "). Please settle at the cycle office; it blocks your next ride.")), 0, 5, 2, 1);

        receiptCard.getChildren().add(grid);
        content.getChildren().addAll(head, receiptCard);

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
        grid.add(createReceiptRow("TOTAL FARE", Money.formatTaka(result.farePoisha)), 1, 2);
        int payRow = 4;
        grid.add(createReceiptRow("PLATFORM FEE",
                Money.formatTaka(TariffService.platformFeePoisha(result.farePoisha))
                        + " (5%, capped — included above)"), 0, 3, 2, 1);
        if (result.finePoisha > 0) {
            grid.add(createReceiptRow("OVERDUE FINE", Money.formatTaka(result.finePoisha)), 0, 4);
            grid.add(createReceiptRow("TOTAL PAID", Money.formatTaka(result.settled.totalPaidPoisha())), 1, 4);
            payRow = 5;
        }

        String payText;
        String fineSuffix = result.finePoisha > 0 ? " + Fine " + Money.formatTaka(result.finePoisha) : "";
        if (result.cashRide) {
            payText = "Cash at hub (" + Money.formatTaka(result.cashEnteredPoisha()) + " collected)" + fineSuffix;
        } else if (result.prepaidPoisha > 0) {
            if (result.farePoisha > result.prepaidPoisha) {
                payText = "Campus Wallet (Pre-paid " + Money.formatTaka(result.prepaidPoisha)
                        + " + Overtime " + Money.formatTaka(result.farePoisha - result.prepaidPoisha) + fineSuffix + ")";
            } else {
                payText = "Campus Wallet (Pre-paid in full: " + Money.formatTaka(result.prepaidPoisha) + fineSuffix + ")";
            }
        } else {
            payText = "Campus Wallet (Settled " + Money.formatTaka(result.farePoisha) + fineSuffix + ")";
        }
        grid.add(createReceiptRow("PAYMENT", payText), 0, payRow, 2, 1);

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
        if (result.damageError != null) {
            HBox damageNotice = new HBox(8);
            damageNotice.setAlignment(Pos.CENTER_LEFT);
            damageNotice.setStyle("-fx-background-color: rgba(239, 68, 68, 0.10); -fx-background-radius: 10px; -fx-padding: 10px 14px; -fx-border-color: #EF4444; -fx-border-radius: 10px;");
            Label warnIcon = new Label("⚠️");
            Label damageText = new Label("Damage report could not be saved (" + result.damageError
                    + "). The return itself succeeded — please report the issue via Support.");
            damageText.setWrapText(true);
            damageText.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #B91C1C;");
            damageNotice.getChildren().addAll(warnIcon, damageText);
            content.getChildren().add(damageNotice);
        }
        if (result.duesError != null) {
            HBox duesNotice = new HBox(8);
            duesNotice.setAlignment(Pos.CENTER_LEFT);
            duesNotice.setStyle("-fx-background-color: rgba(239, 68, 68, 0.10); -fx-background-radius: 10px; -fx-padding: 10px 14px; -fx-border-color: #EF4444; -fx-border-radius: 10px;");
            Label warnIcon = new Label("⚠️");
            Label duesText = new Label("Outstanding balance could not be recorded (" + result.duesError
                    + "). Please screenshot this receipt and contact the cycle office.");
            duesText.setWrapText(true);
            duesText.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #B91C1C;");
            duesNotice.getChildren().addAll(warnIcon, duesText);
            content.getChildren().add(duesNotice);
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
        v.setWrapText(true);
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

            // Started-blocks billing (P-027): a block is charged only once it starts.
            int blocks = (int) Math.max(0,
                    (elapsed - (long) TariffService.BASE_MINUTES * 60)
                            / ((long) TariffService.EXTRA_BLOCK_MINUTES * 60));
            int basePoisha = TariffService.BASE_CHARGE_POISHA + blocks * TariffService.EXTRA_BLOCK_CHARGE_POISHA;

            calculatedFarePoisha = basePoisha - TariffService.subsidyPoisha(basePoisha, isStudent);

            fareLabel.setText(Money.formatTaka(calculatedFarePoisha));

            boolean isOvertime = active.dueAt() != null && CampusTime.now().isAfter(active.dueAt());
            if (isOvertime) {
                timerLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #EF4444;");
                if (isStudent) {
                    fareSubLabel.setText("⚠️ Overdue • " + Money.formatTaka(basePoisha) + " base (" + subsidyPercent() + "% Subsidy)");
                } else {
                    fareSubLabel.setText("⚠️ Overdue • Base 15m @ ৳20 + ৳10/15m block");
                }
            } else {
                timerLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");
                if (isStudent) {
                    fareSubLabel.setText(Money.formatTaka(basePoisha) + " base (" + subsidyPercent() + "% KUET Subsidy Applied)");
                } else {
                    fareSubLabel.setText("Base 15m @ ৳20 + ৳10/15m block");
                }
            }
        }));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }
}
