package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.domain.CampusTime;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.TariffService;
import bd.ac.kuet.campuscycle.domain.event.RentalStartedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.WalletService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Clean Reservation Modal:
 * 1. Title: "Reserve Cycle"
 * 2. Duration selector pills: 15 mins, 30 mins, 1 hour, 2 hours
 * 3. Pricing breakdown: Base Fare, Student Discount, Total Due
 * 4. Payment Selector: Prepaid Campus Pay vs Station Dock / bKash
 * 5. Insufficient balance validation with header top-up guidance
 * 6. Clean "Confirm & Unlock Cycle" action
 *
 * <p>Money-integrity contract (P-017, P-113, P-114, P-180): the booking commits
 * FIRST and the wallet is charged second, so a failed booking can never leave a
 * stray debit. A charge failure after a successful booking triggers the guarded
 * {@code refundFare} reversal before the error surfaces.
 */
public class ReservationModal extends StackPane {

    private static final Logger LOGGER = Logger.getLogger(ReservationModal.class.getName());

    private final CycleItem cycle;
    private final CampusUser user;
    private final CampusRepository repo;
    private final Runnable onClose;
    private final Runnable onSuccess;

    private int selectedMinutes = 30;
    private final List<Button> durationPills = new ArrayList<>();

    private final Label returnLabel = new Label();
    private final Label baseFareLabel = new Label();
    private final Label discountLabel = new Label();
    private final Label totalDueLabel = new Label();
    private HBox discountRow;
    private final Label campusPayBalanceLabel = new Label();

    private final RadioButton rbCampusPay = new RadioButton("Prepaid Campus Pay");
    private final RadioButton rbDockPay = new RadioButton("Pay at Station Dock / bKash");
    private final ToggleGroup paymentGroup = new ToggleGroup();
    private final Label warningLabel = new Label();
    private final Button confirmBtn = new Button("Confirm & Unlock Cycle");
    private int cachedBalancePoisha = 0;
    private Timeline dueTicker;

    public ReservationModal(CycleItem cycle,
                            CampusUser user,
                            CampusRepository repo,
                            Runnable onClose,
                            Runnable onSuccess) {
        ThemeManager.install(this);
        this.cycle = cycle;
        this.user = user;
        this.repo = repo;
        this.onClose = onClose;
        this.onSuccess = onSuccess;

        setAccessibleRole(AccessibleRole.DIALOG);

        // Seed with memory-cached balance immediately
        this.cachedBalancePoisha = user != null ? WalletService.getInstance().getBalancePoisha(user.id()) : 0;

        getStyleClass().add("modal-overlay");
        setAlignment(Pos.CENTER);

        VBox sheet = new VBox(18);
        sheet.getStyleClass().add("modal-sheet");
        sheet.setMaxWidth(480);
        sheet.setPadding(new Insets(26));

        HBox header = createHeader();
        VBox cycleInfo = createCycleInfoCard();
        VBox durationSection = createDurationPillsSection();
        VBox pricingSection = createPricingSection();
        VBox paymentSection = createPaymentSelectorSection();
        HBox actions = createActionButtons();

        sheet.getChildren().addAll(header, cycleInfo, durationSection, pricingSection, paymentSection, actions);
        getChildren().add(sheet);

        updateCalculations();
        ThemeManager.applyFadeIn(this);
        startDueTicker();
        parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent == null) {
                stopDueTicker();
            }
        });

        // ESC closes the modal; initial focus lands on the primary action.
        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
            }
        });
        Platform.runLater(() -> confirmBtn.requestFocus());

        // Background refresh to guarantee fresh live balance without UI blocking
        AppExecutor.asyncThenFx(
                () -> user != null ? WalletService.getInstance().getBalancePoisha(user.id()) : 0,
                bal -> {
                    this.cachedBalancePoisha = bal;
                    updateCalculations();
                },
                err -> {}
        );
    }

    private void close() {
        stopDueTicker();
        onClose.run();
    }

    private void startDueTicker() {
        stopDueTicker();
        dueTicker = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshDueBy()));
        dueTicker.setCycleCount(Animation.INDEFINITE);
        dueTicker.play();
    }

    private void stopDueTicker() {
        if (dueTicker != null) {
            dueTicker.stop();
            dueTicker = null;
        }
    }

    private void refreshDueBy() {
        ZonedDateTime due = CampusTime.now().plusMinutes(selectedMinutes);
        returnLabel.setText("Due by " + due.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US)));
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 18, Color.web("#10B981")));
        icon.setPrefSize(38, 38);
        icon.getStyleClass().add("icon-badge");

        VBox titleCol = new VBox(2);
        Label title = new Label("Reserve Cycle");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");

        Label sub = new Label("Instant unlock at station dock");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75; -fx-text-fill: -fx-ink-700;");

        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = ThemeManager.createIconButton(ThemeManager.ICON_CLOSE, 14, "action-icon-btn", this::close);

        row.getChildren().addAll(icon, titleCol, spacer, closeBtn);
        return row;
    }

    private VBox createCycleInfoCard() {
        VBox box = new VBox(6);
        box.getStyleClass().add("sub-panel");
        box.setPadding(new Insets(12, 16, 12, 16));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(cycle.label());
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: 800;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label typeBadge = new Label(cycle.type().name().replace("_", " "));
        typeBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-padding: 2px 7px; -fx-background-radius: 999px; -fx-background-color: rgba(16, 185, 129, 0.12); -fx-text-fill: -fx-teal;");

        top.getChildren().addAll(name, spacer, typeBadge);

        Label station = new Label("📍 Station Dock: " + cycle.pickupPoint());
        station.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.75;");

        box.getChildren().addAll(top, station);
        return box;
    }

    private VBox createDurationPillsSection() {
        VBox box = new VBox(8);

        HBox labelRow = new HBox();
        Label heading = new Label("COMMUTE DURATION");
        heading.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        returnLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");
        labelRow.getChildren().addAll(heading, spacer, returnLabel);

        // Duration selector pills: 15 mins, 30 mins, 1 hour, 2 hours
        HBox pillsRow = new HBox(8);
        pillsRow.setAlignment(Pos.CENTER);

        addDurationPill(pillsRow, "15 mins", 15);
        addDurationPill(pillsRow, "30 mins", 30);
        addDurationPill(pillsRow, "1 hour", 60);
        addDurationPill(pillsRow, "2 hours", 120);

        box.getChildren().addAll(labelRow, pillsRow);
        return box;
    }

    private void addDurationPill(HBox container, String label, int minutes) {
        Button pill = new Button(label);
        pill.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pill, Priority.ALWAYS);
        pill.getStyleClass().add("filter-chip");

        if (minutes == selectedMinutes) {
            pill.getStyleClass().add("filter-chip-active");
        }

        pill.setOnAction(e -> {
            for (Button b : durationPills) {
                b.getStyleClass().remove("filter-chip-active");
            }
            pill.getStyleClass().add("filter-chip-active");
            selectedMinutes = minutes;
            updateCalculations();
        });

        durationPills.add(pill);
        container.getChildren().add(pill);
    }

    private VBox createPricingSection() {
        VBox box = new VBox(7);
        box.getStyleClass().add("sub-panel");
        box.setPadding(new Insets(12, 16, 12, 16));

        HBox r1 = createRow("Base Fare", baseFareLabel);
        discountRow = createRow("Student Discount", discountLabel);
        discountLabel.setStyle("-fx-text-fill: -fx-teal; -fx-font-weight: 700;");

        Separator sep = new Separator();

        HBox r3 = createRow("Total Due", totalDueLabel);
        totalDueLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");

        box.getChildren().addAll(r1, discountRow, sep, r3);
        return box;
    }

    private HBox createRow(String label, Label valueLabel) {
        HBox row = new HBox();
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 12px; -fx-opacity: 0.8;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        valueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
        row.getChildren().addAll(l, spacer, valueLabel);
        return row;
    }

    private VBox createPaymentSelectorSection() {
        VBox box = new VBox(8);

        Label label = new Label("PAYMENT METHOD");
        label.getStyleClass().add("metric-label");

        rbCampusPay.setToggleGroup(paymentGroup);
        rbDockPay.setToggleGroup(paymentGroup);
        rbCampusPay.setSelected(true);

        paymentGroup.selectedToggleProperty().addListener((obs, o, n) -> updateCalculations());

        // Static radio label + live balance readout kept separate (P-180).
        campusPayBalanceLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");
        HBox campusPayRow = new HBox(8, rbCampusPay, campusPayBalanceLabel);
        campusPayRow.setAlignment(Pos.CENTER_LEFT);

        VBox payOptionsBox = new VBox(8, campusPayRow, rbDockPay);
        payOptionsBox.getStyleClass().add("sub-panel");
        payOptionsBox.setPadding(new Insets(10, 14, 10, 14));

        warningLabel.setStyle("-fx-text-fill: -fx-danger; -fx-font-size: 11.5px; -fx-font-weight: 700;");
        warningLabel.setWrapText(true);
        warningLabel.setVisible(false);
        warningLabel.setManaged(false);

        box.getChildren().addAll(label, payOptionsBox, warningLabel);
        return box;
    }

    private void updateCalculations() {
        refreshDueBy();

        boolean isStudent = user != null && user.role() == Role.STUDENT;

        // Base fare from TariffService; discount only for students (P-113).
        int basePoisha = TariffService.quotePoisha(selectedMinutes);
        int discountPoisha = TariffService.subsidyPoisha(basePoisha, isStudent);
        int totalDuePoisha = basePoisha - discountPoisha;

        baseFareLabel.setText(Money.formatTaka(basePoisha));
        if (isStudent) {
            int pct = (int) Math.round(TariffService.STUDENT_SUBSIDY_RATE * 100);
            discountLabel.setText("-" + pct + "% Applied (KUET Student Perk) • -" + Money.formatTaka(discountPoisha));
        }
        discountRow.setVisible(isStudent);
        discountRow.setManaged(isStudent);
        totalDueLabel.setText(Money.formatTaka(totalDuePoisha));

        // Wallet Balance check from cached balance (0ms UI latency)
        int balancePoisha = this.cachedBalancePoisha;
        campusPayBalanceLabel.setText("(Balance: " + Money.formatTaka(balancePoisha) + ")");

        if (rbCampusPay.isSelected()) {
            if (balancePoisha < totalDuePoisha) {
                warningLabel.setText("Insufficient balance (Need " + Money.formatTaka(totalDuePoisha) + "). Top up in header.");
                warningLabel.setVisible(true);
                warningLabel.setManaged(true);
                confirmBtn.setDisable(true);
            } else {
                warningLabel.setVisible(false);
                warningLabel.setManaged(false);
                confirmBtn.setDisable(false);
            }
        } else {
            // Pay at Station Dock / bKash
            warningLabel.setVisible(false);
            warningLabel.setManaged(false);
            confirmBtn.setDisable(false);
        }
    }

    private HBox createActionButtons() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> close());

        confirmBtn.getStyleClass().add("primary-button");
        confirmBtn.setOnAction(e -> handleConfirmUnlock());

        row.getChildren().addAll(cancelBtn, confirmBtn);
        return row;
    }

    private void handleConfirmUnlock() {
        confirmBtn.setDisable(true);
        confirmBtn.setText("Unlocking Cycle...");

        boolean payWithCampusPay = rbCampusPay.isSelected();
        CampusRepository.PaymentMethod method = payWithCampusPay
                ? CampusRepository.PaymentMethod.CAMPUS_PAY
                : CampusRepository.PaymentMethod.DOCK_PAY;

        AppExecutor.asyncThenFx(
                () -> {
                    // Book with atomic wallet charge for Campus Pay (P-017, P-026).
                    // The wallet is debited inside the same transaction as the booking,
                    // so a failed booking can never leave a stray debit.
                    RentalRecord record = repo.book(user, cycle.id(), selectedMinutes, method);

                    // Campus Pay debited the wallet inside the booking transaction, so
                    // refresh every screen that shows a balance.
                    if (method == CampusRepository.PaymentMethod.CAMPUS_PAY) {
                        WalletService.getInstance().refreshAndNotifyBalance(user.id());
                    }

                    // Notify EventBus
                    EventBus.getInstance().publish(new RentalStartedEvent(record, Instant.now()));
                    return record;
                },
                record -> {
                    stopDueTicker();
                    onSuccess.run();
                },
                error -> {
                    confirmBtn.setDisable(false);
                    confirmBtn.setText("Confirm & Unlock Cycle");
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to reserve cycle: " + error.getMessage(), ButtonType.OK);
                    alert.showAndWait();
                }
        );
    }

    }
