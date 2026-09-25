package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.event.RentalStartedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ReservationModal extends StackPane {

    private final CycleItem cycle;
    private final CampusUser user;
    private final CampusRepository repo;
    private final Runnable onClose;
    private final Runnable onSuccess;

    private int selectedMinutes = 30;
    private final Label timeLabel = new Label("30 mins");
    private final Label returnLabel = new Label();
    private final Label tariffLabel = new Label();
    private final Label discountLabel = new Label();
    private final Label totalLabel = new Label();

    private final RadioButton rbSubsidy = new RadioButton("KUET Campus Subsidy (25% Auto-Deduction)");
    private final RadioButton rbBkash = new RadioButton("bKash / Nagad Instant Mobile Banking");
    private final RadioButton rbSmartCard = new RadioButton("KUET Smart ID NFC Card Balance");

    public ReservationModal(CycleItem cycle,
                            CampusUser user,
                            CampusRepository repo,
                            Runnable onClose,
                            Runnable onSuccess) {
        this.cycle = cycle;
        this.user = user;
        this.repo = repo;
        this.onClose = onClose;
        this.onSuccess = onSuccess;

        getStyleClass().add("modal-overlay");
        setAlignment(Pos.CENTER);

        VBox sheet = new VBox(18);
        sheet.getStyleClass().add("modal-sheet");
        sheet.setMaxWidth(500);
        sheet.setPadding(new Insets(28));

        HBox header = createHeader();
        VBox cycleInfo = createCycleInfoCard();
        VBox sliderSection = createSliderSection();
        VBox paymentMethodSection = createPaymentMethodSection();
        VBox summarySection = createSummarySection();
        HBox actions = createActionButtons();

        sheet.getChildren().addAll(header, cycleInfo, sliderSection, paymentMethodSection, summarySection, actions);
        getChildren().add(sheet);

        updateTariffCalculation();
        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 18, Color.web("#0284C7")));
        icon.setPrefSize(36, 36);
        icon.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(2);
        Label title = new Label("Unlock & Reserve Cycle");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        Label sub = new Label("Instant Bluetooth smart lock release at station");
        sub.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.7;");

        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = ThemeManager.createIconButton(ThemeManager.ICON_CLOSE, 14, "action-icon-btn", onClose);

        row.getChildren().addAll(icon, titleCol, spacer, closeBtn);
        return row;
    }

    private VBox createCycleInfoCard() {
        VBox box = new VBox(8);
        box.getStyleClass().add("sub-panel");
        box.setPadding(new Insets(14, 16, 14, 16));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(cycle.label());
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: 750;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label typeBadge = new Label(cycle.type().name().replace("_", " "));
        typeBadge.getStyleClass().add("badge-electric");

        top.getChildren().addAll(name, spacer, typeBadge);

        Label station = new Label("📍 Dock: " + cycle.pickupPoint() + " • Verified KUET Hardware");
        station.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.75;");

        box.getChildren().addAll(top, station);
        return box;
    }

    private VBox createSliderSection() {
        VBox box = new VBox(12);

        HBox labelRow = new HBox();
        Label durHeading = new Label("ESTIMATED COMMUTE DURATION");
        durHeading.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        timeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #0284C7;");
        labelRow.getChildren().addAll(durHeading, spacer, timeLabel);

        Slider slider = new Slider(15, 180, 30);
        slider.setMajorTickUnit(15);
        slider.setSnapToTicks(true);
        slider.getStyleClass().add("slider");

        slider.valueProperty().addListener((obs, o, n) -> {
            selectedMinutes = (int) Math.round(n.doubleValue() / 15.0) * 15;
            selectedMinutes = Math.max(15, Math.min(180, selectedMinutes));
            timeLabel.setText(selectedMinutes + " mins");
            updateTariffCalculation();
        });

        HBox bounds = new HBox();
        Label minLbl = new Label("15 mins (Min)");
        minLbl.setStyle("-fx-font-size: 10.5px; -fx-opacity: 0.6;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label maxLbl = new Label("180 mins (Max)");
        maxLbl.setStyle("-fx-font-size: 10.5px; -fx-opacity: 0.6;");
        bounds.getChildren().addAll(minLbl, sp, maxLbl);

        box.getChildren().addAll(labelRow, slider, bounds);
        return box;
    }

    private VBox createPaymentMethodSection() {
        VBox box = new VBox(8);

        Label label = new Label("PAYMENT & SUBSIDY CHANNEL");
        label.getStyleClass().add("metric-label");

        ToggleGroup tg = new ToggleGroup();
        rbSubsidy.setToggleGroup(tg);
        rbBkash.setToggleGroup(tg);
        rbSmartCard.setToggleGroup(tg);
        rbSubsidy.setSelected(true);

        rbSubsidy.getStyleClass().add("radio-button");
        rbBkash.getStyleClass().add("radio-button");
        rbSmartCard.getStyleClass().add("radio-button");

        tg.selectedToggleProperty().addListener((obs, oldVal, newVal) -> updateTariffCalculation());

        VBox radioBox = new VBox(6, rbSubsidy, rbBkash, rbSmartCard);
        radioBox.getStyleClass().add("sub-panel");
        radioBox.setPadding(new Insets(10, 14, 10, 14));

        box.getChildren().addAll(label, radioBox);
        return box;
    }

    private VBox createSummarySection() {
        VBox box = new VBox(8);
        box.getStyleClass().add("sub-panel");
        box.setPadding(new Insets(14, 16, 14, 16));

        HBox r1 = createRow("Estimated Return Time", returnLabel);
        HBox r2 = createRow("Standard Campus Tariff", tariffLabel);
        HBox r3 = createRow("Student ID Subsidy (25%)", discountLabel);
        discountLabel.setStyle("-fx-text-fill: #10B981; -fx-font-weight: 700;");

        Separator sep = new Separator();

        HBox r4 = createRow("Net Payable at Return", totalLabel);
        totalLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0284C7;");

        box.getChildren().addAll(r1, r2, r3, sep, r4);
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

    private void updateTariffCalculation() {
        LocalTime due = LocalTime.now().plusMinutes(selectedMinutes);
        returnLabel.setText("Due by " + due.format(DateTimeFormatter.ofPattern("hh:mm a")));

        int basePoisha = 2000; // 20 BDT
        int extraMinutes = Math.max(0, selectedMinutes - 15);
        int extraBlocks = (int) Math.ceil(extraMinutes / 15.0);
        int subtotalPoisha = basePoisha + (extraBlocks * 1000);

        boolean applySubsidy = rbSubsidy.isSelected() && (user.role() == Role.STUDENT);
        int discountPoisha = applySubsidy ? (int) (subtotalPoisha * 0.25) : 0;
        int netPoisha = subtotalPoisha - discountPoisha;

        tariffLabel.setText(String.format("BDT %.2f", subtotalPoisha / 100.0));
        discountLabel.setText(applySubsidy ? String.format("- BDT %.2f", discountPoisha / 100.0) : "BDT 0.00");
        totalLabel.setText(String.format("BDT %.2f", netPoisha / 100.0));
    }

    private HBox createActionButtons() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> onClose.run());

        Button confirmBtn = new Button("Confirm & Unlock Cycle");
        confirmBtn.getStyleClass().add("primary-button");
        confirmBtn.setOnAction(e -> {
            confirmBtn.setDisable(true);
            confirmBtn.setText("Unlocking & Connecting...");

            AppExecutor.asyncThenFx(
                    () -> {
                        RentalRecord record = repo.book(user, cycle.id(), selectedMinutes);
                        EventBus.getInstance().publish(new RentalStartedEvent(record, Instant.now()));
                        return record;
                    },
                    record -> onSuccess.run(),
                    error -> {
                        confirmBtn.setDisable(false);
                        confirmBtn.setText("Confirm & Unlock Cycle");
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Reservation error: " + error.getMessage(), ButtonType.OK);
                        alert.showAndWait();
                    }
            );
        });

        row.getChildren().addAll(cancelBtn, confirmBtn);
        return row;
    }
}
