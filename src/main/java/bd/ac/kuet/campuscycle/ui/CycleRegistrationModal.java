package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.*;
import bd.ac.kuet.campuscycle.domain.event.CycleStatusChangedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.UUID;

/**
 * Peer-to-Peer Cycle Listing Registration Modal.
 * Demonstrates:
 * 1. CRUD Create operation (creates new cycle listing in SQLite and Supabase)
 * 2. JavaFX UI controls (TextField, ComboBox, StackPane, Button, Label)
 * 3. Asynchronous background execution (AppExecutor)
 * 4. EventBus pub-sub notification
 */
public class CycleRegistrationModal extends StackPane {

    private final CampusUser user;
    private final Runnable onClose;
    private final Runnable onCycleCreated;

    private final TextField labelField = new TextField();
    private final ComboBox<CycleType> typeCombo = new ComboBox<>();
    private final RadioButton rbExcellent = new RadioButton("Brand New / Excellent");
    private final RadioButton rbGood = new RadioButton("Good Condition");
    private final RadioButton rbFair = new RadioButton("Fair / Minor Scratches");
    private final ToggleGroup conditionGroup = new ToggleGroup();
    private final ComboBox<String> pickupCombo = new ComboBox<>();
    private final TextField phoneField = new TextField();
    private final TextField descField = new TextField();
    private final Label errorLbl = new Label();
    private final Button submitBtn = new Button("Submit Cycle Listing");

    public CycleRegistrationModal(CampusUser user, Runnable onClose, Runnable onCycleCreated) {
        ThemeManager.install(this);
        this.user = user;
        this.onClose = onClose;
        this.onCycleCreated = onCycleCreated;

        setStyle("-fx-background-color: rgba(15, 23, 42, 0.65);");
        setAlignment(Pos.CENTER);

        VBox card = createCard();
        getChildren().add(card);
        ThemeManager.applyFadeIn(card);
    }

    private VBox createCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("bento-card");
        card.setStyle(card.getStyle() + "; -fx-background-radius: 20px; -fx-border-radius: 20px;");
        card.setPadding(new Insets(28, 32, 28, 32));
        card.setMaxWidth(520);
        card.setAlignment(Pos.TOP_LEFT);

        // Header
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Register Campus Cycle (P2P)");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800;");
        Label sub = new Label("List your bicycle for verified KUET students to rent and share");
        sub.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = ThemeManager.createIconButton(ThemeManager.ICON_CLOSE, 14, "action-icon-btn", onClose);
        top.getChildren().addAll(titleCol, spacer, closeBtn);

        // Form Fields
        Label l1 = new Label("CYCLE MODEL / LABEL");
        l1.getStyleClass().add("metric-label");
        labelField.setPromptText("e.g. Phoenix Alloy Commuter");
        labelField.getStyleClass().add("modern-input");

        Label l2 = new Label("CYCLE TYPE");
        l2.getStyleClass().add("metric-label");
        typeCombo.getItems().setAll(CycleType.values());
        typeCombo.setValue(CycleType.CITY_BIKE);
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.getStyleClass().add("modern-input");

        Label l3 = new Label("PHYSICAL CONDITION");
        l3.getStyleClass().add("metric-label");
        rbExcellent.setToggleGroup(conditionGroup);
        rbGood.setToggleGroup(conditionGroup);
        rbFair.setToggleGroup(conditionGroup);
        rbExcellent.setSelected(true);
        rbExcellent.getStyleClass().add("radio-button");
        rbGood.getStyleClass().add("radio-button");
        rbFair.getStyleClass().add("radio-button");
        HBox conditionBox = new HBox(12, rbExcellent, rbGood, rbFair);
        conditionBox.setPadding(new Insets(4, 0, 4, 0));

        Label l4 = new Label("PICKUP / RETURN HUB");
        l4.getStyleClass().add("metric-label");
        pickupCombo.getItems().setAll(bd.ac.kuet.campuscycle.domain.CampusHubs.names());
        pickupCombo.setValue(bd.ac.kuet.campuscycle.domain.CampusHubs.names().get(0));
        pickupCombo.setMaxWidth(Double.MAX_VALUE);
        pickupCombo.getStyleClass().add("modern-input");

        Label l5 = new Label("EMERGENCY / OWNER CONTACT PHONE");
        l5.getStyleClass().add("metric-label");
        phoneField.setPromptText("+8801700000000");
        phoneField.getStyleClass().add("modern-input");

        Label l6 = new Label("DESCRIPTION / LOCK NOTES");
        l6.getStyleClass().add("metric-label");
        descField.setPromptText("e.g. 21-speed gears, front basket, combination lock 1234");
        descField.getStyleClass().add("modern-input");

        errorLbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #DC2626;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        // Buttons
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> onClose.run());

        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setOnAction(e -> handleRegistration());

        btnRow.getChildren().addAll(cancelBtn, submitBtn);

        card.getChildren().addAll(
                top,
                l1, labelField,
                l2, typeCombo,
                l3, conditionBox,
                l4, pickupCombo,
                l5, phoneField,
                l6, descField,
                errorLbl,
                btnRow
        );

        return card;
    }

    private void handleRegistration() {
        String label = labelField.getText();
        if (label == null || label.trim().length() < 2 || label.trim().length() > 100) {
            showError("Cycle label must be 2-100 characters.");
            return;
        }

        String phone = phoneField.getText();
        if (phone == null || !phone.trim().matches("^\\+?8801[3-9][0-9]{8}$")) {
            showError("Enter a valid BD mobile (e.g. +8801712345678).");
            return;
        }
        String desc = descField.getText() == null ? "" : descField.getText().trim();
        if (desc.length() > 2000) {
            showError("Description must be under 2000 characters.");
            return;
        }

        CycleCondition selectedCondition = CycleCondition.EXCELLENT;
        if (rbGood.isSelected()) {
            selectedCondition = CycleCondition.GOOD;
        } else if (rbFair.isSelected()) {
            selectedCondition = CycleCondition.FAIR;
        }

        submitBtn.setDisable(true);
        submitBtn.setText("Registering Cycle...");

        String cycleIdStr = UUID.randomUUID().toString();
        String hub = pickupCombo.getValue();
        bd.ac.kuet.campuscycle.domain.CampusHubs.Hub hubRef =
                bd.ac.kuet.campuscycle.domain.CampusHubs.byName(hub);
        double lat = hubRef.lat();
        double lng = hubRef.lng();

        final double finalLat = lat;
        final double finalLng = lng;
        final CycleCondition finalCondition = selectedCondition;

        CycleItem newCycle = new CycleItem(
                cycleIdStr,
                user.id(),
                user.displayName(),
                label.trim(),
                typeCombo.getValue(),
                finalCondition,
                hub,
                finalLat,
                finalLng,
                descField.getText() != null ? descField.getText().trim() : "",
                ReviewStatus.PENDING_REVIEW,
                AvailabilityStatus.AVAILABLE
        );

        // Run background database persistence (local only; server insert goes via register_cycle RPC)
        AppExecutor.asyncThenFx(
                () -> {
                    // 1. Save in local SQLite database
                    LocalDatabase.getInstance().saveCycle(newCycle);
                    return newCycle;
                },
                created -> {
                    // Notify observers
                    EventBus.getInstance().publish(new CycleStatusChangedEvent(created.id(), AvailabilityStatus.AVAILABLE));
                    onCycleCreated.run();
                    onClose.run();
                },
                throwable -> {
                    submitBtn.setDisable(false);
                    submitBtn.setText("Submit Cycle Listing");
                    showError("Registration failed: " + throwable.getMessage());
                }
        );
    }

    private void showError(String msg) {
        errorLbl.setText(msg);
        errorLbl.setVisible(true);
        errorLbl.setManaged(true);
    }
}
