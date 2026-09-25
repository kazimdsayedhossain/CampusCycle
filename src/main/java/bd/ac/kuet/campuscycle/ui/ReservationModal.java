package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.TariffService;
import bd.ac.kuet.campuscycle.domain.event.RentalStartedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.WalletService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean Reservation Modal:
 * 1. Title: "Reserve Cycle"
 * 2. Duration selector pills: 15 mins, 30 mins, 1 hour, 2 hours
 * 3. Pricing breakdown: Base Fare, Student Discount (-20% Applied), Total Due
 * 4. Payment Selector: Prepaid Campus Pay vs Station Dock / bKash
 * 5. Insufficient balance validation with header top-up guidance
 * 6. Clean "Confirm & Unlock Cycle" action
 */
public class ReservationModal extends StackPane {

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

    private final RadioButton rbCampusPay = new RadioButton();
    private final RadioButton rbDockPay = new RadioButton("Pay at Station Dock / bKash");
    private final ToggleGroup paymentGroup = new ToggleGroup();
    private final Label warningLabel = new Label();
    private final Button confirmBtn = new Button("Confirm & Unlock Cycle");

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
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 18, Color.web("#10B981")));
        icon.setPrefSize(38, 38);
        icon.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(2);
        Label title = new Label("Reserve Cycle");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");

        Label sub = new Label("Instant unlock at station dock");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75; -fx-text-fill: -fx-ink-700;");

        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = ThemeManager.createIconButton(ThemeManager.ICON_CLOSE, 14, "action-icon-btn", onClose);

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
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: 750;");

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

        returnLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 650; -fx-text-fill: -fx-teal;");
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
        HBox r2 = createRow("Student Discount", discountLabel);
        discountLabel.setStyle("-fx-text-fill: -fx-teal; -fx-font-weight: 700;");

        Separator sep = new Separator();

        HBox r3 = createRow("Total Due", totalDueLabel);
        totalDueLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");

        box.getChildren().addAll(r1, r2, sep, r3);
        return box;
    }

    private HBox createRow(String label, Label valueLabel) {
        HBox row = new HBox();
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 12px; -fx-opacity: 0.8;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        valueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 650;");
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

        VBox payOptionsBox = new VBox(8, rbCampusPay, rbDockPay);
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
        LocalTime due = LocalTime.now().plusMinutes(selectedMinutes);
        returnLabel.setText("Due by " + due.format(DateTimeFormatter.ofPattern("hh:mm a")));

        // Base fare from TariffService
        int basePoisha = TariffService.quotePoisha(selectedMinutes);
        double baseBdt = basePoisha / 100.0;

        // 20% Student Discount Applied (KUET Student Perk)
        double discountBdt = baseBdt * 0.20;
        double totalDueBdt = baseBdt - discountBdt;

        baseFareLabel.setText(String.format("৳ %.2f", baseBdt));
        discountLabel.setText(String.format("-20%% Applied (KUET Student Perk) • -৳ %.2f", discountBdt));
        totalDueLabel.setText(String.format("৳ %.2f", totalDueBdt));

        // Wallet Balance check
        double balance = WalletService.getInstance().getBalance(user);
        rbCampusPay.setText(String.format("Prepaid Campus Pay (Balance: ৳ %.2f)", balance));

        if (rbCampusPay.isSelected()) {
            if (balance < totalDueBdt) {
                warningLabel.setText(String.format("Insufficient balance (Need ৳%.2f). Top up in header.", totalDueBdt));
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
        cancelBtn.setOnAction(e -> onClose.run());

        confirmBtn.getStyleClass().add("primary-button");
        confirmBtn.setOnAction(e -> handleConfirmUnlock());

        row.getChildren().addAll(cancelBtn, confirmBtn);
        return row;
    }

    private void handleConfirmUnlock() {
        confirmBtn.setDisable(true);
        confirmBtn.setText("Unlocking Cycle...");

        int basePoisha = TariffService.quotePoisha(selectedMinutes);
        int discountPoisha = (int) Math.round(basePoisha * 0.20);
        int netDuePoisha = basePoisha - discountPoisha;

        AppExecutor.asyncThenFx(
                () -> {
                    // If Campus Pay selected, deduct fare from wallet
                    if (rbCampusPay.isSelected()) {
                        WalletService.getInstance().deductFare(
                                user,
                                netDuePoisha,
                                cycle.id(),
                                "Reservation: " + cycle.label() + " (" + selectedMinutes + "m)"
                        );
                    }

                    // Book rental in repository
                    RentalRecord record = repo.book(user, cycle.id(), selectedMinutes);

                    // Update SQLite local database
                    LocalDatabase.getInstance().saveRental(record);
                    LocalDatabase.getInstance().updateCycleAvailability(cycle.id(), AvailabilityStatus.RENTED);

                    // Notify EventBus
                    EventBus.getInstance().publish(new RentalStartedEvent(record, Instant.now()));
                    return record;
                },
                record -> onSuccess.run(),
                error -> {
                    confirmBtn.setDisable(false);
                    confirmBtn.setText("Confirm & Unlock Cycle");
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to reserve cycle: " + error.getMessage(), ButtonType.OK);
                    alert.showAndWait();
                }
        );
    }
}
