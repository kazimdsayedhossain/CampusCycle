package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.service.WalletService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern compact Top-Up modal for CampusCycle Prepaid Wallet.
 * Supports quick-add presets (৳50, ৳100, ৳200, ৳500),
 * payment channels (bKash, Nagad, Student ID), and custom amount entry.
 */
public class TopUpModal extends StackPane {

    private final CampusUser user;
    private final Runnable onClose;
    private final Runnable onSuccess;

    private double selectedAmount = 100.0;
    private String selectedMethod = "bKash";

    private final List<Button> presetButtons = new ArrayList<>();
    private final List<Button> methodButtons = new ArrayList<>();

    private final Label currentBalLabel = new Label();
    private final Label newBalLabel = new Label();
    private final Button confirmBtn = new Button();
    private final TextField customAmountField = new TextField();

    public static void open(Node anchor, CampusUser user, Runnable onComplete) {
        if (anchor == null) return;
        Scene scene = anchor.getScene();
        if (scene == null) return;

        if (scene.getRoot() instanceof StackPane rootStack) {
            TopUpModal modal = new TopUpModal(
                    user,
                    () -> rootStack.getChildren().removeIf(n -> n instanceof TopUpModal),
                    () -> {
                        rootStack.getChildren().removeIf(n -> n instanceof TopUpModal);
                        if (onComplete != null) onComplete.run();
                    }
            );
            rootStack.getChildren().add(modal);
        }
    }

    public TopUpModal(CampusUser user, Runnable onClose, Runnable onSuccess) {
        ThemeManager.install(this);
        this.user = user;
        this.onClose = onClose;
        this.onSuccess = onSuccess;

        getStyleClass().add("modal-overlay");
        setAlignment(Pos.CENTER);

        VBox sheet = new VBox(20);
        sheet.getStyleClass().add("modal-sheet");
        sheet.setMaxWidth(460);
        sheet.setPadding(new Insets(26, 30, 28, 30));

        HBox header = createHeader();
        VBox currentBalBox = createBalanceBanner();
        VBox methodSection = createMethodSection();
        VBox amountSection = createAmountSection();
        VBox summarySection = createSummaryAndAction();

        sheet.getChildren().addAll(header, currentBalBox, methodSection, amountSection, summarySection);
        getChildren().add(sheet);

        updateCalculations();
        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BOLT, 18, Color.web("#10B981")));
        icon.setPrefSize(38, 38);
        icon.getStyleClass().add("action-icon-btn");
        icon.setStyle("-fx-background-color: rgba(16, 185, 129, 0.12); -fx-background-radius: 999px;");

        VBox titleCol = new VBox(2);
        Label title = new Label("Top Up Transit Wallet");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800;");

        Label sub = new Label("Instant recharge • Zero transaction fees");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = ThemeManager.createIconButton(ThemeManager.ICON_CLOSE, 14, "action-icon-btn", onClose);

        row.getChildren().addAll(icon, titleCol, spacer, closeBtn);
        return row;
    }

    private VBox createBalanceBanner() {
        VBox box = new VBox(4);
        box.getStyleClass().add("sub-panel");
        box.setPadding(new Insets(12, 16, 12, 16));

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label("Current Available Balance");
        lbl.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        double balance = WalletService.getInstance().getBalance(user);
        currentBalLabel.setText(String.format("৳ %.2f", balance));
        currentBalLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");

        row.getChildren().addAll(lbl, spacer, currentBalLabel);
        box.getChildren().add(row);
        return box;
    }

    private VBox createMethodSection() {
        VBox box = new VBox(8);
        Label lbl = new Label("PAYMENT METHOD");
        lbl.getStyleClass().add("metric-label");

        HBox methods = new HBox(10);
        methods.setAlignment(Pos.CENTER_LEFT);

        String[] channelNames = {"bKash", "Nagad", "Student ID"};
        for (String m : channelNames) {
            Button btn = new Button(m);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.getStyleClass().add("secondary-button");
            btn.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-padding: 8px 12px;");

            if (m.equals(selectedMethod)) {
                applyMethodActiveStyle(btn, m);
            }

            btn.setOnAction(e -> {
                selectedMethod = m;
                for (Button b : methodButtons) {
                    b.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-padding: 8px 12px;");
                }
                applyMethodActiveStyle(btn, m);
                updateCalculations();
            });

            methodButtons.add(btn);
            methods.getChildren().add(btn);
        }

        box.getChildren().addAll(lbl, methods);
        return box;
    }

    private void applyMethodActiveStyle(Button btn, String method) {
        String color = switch (method) {
            case "bKash" -> "#E2136E";
            case "Nagad" -> "#F7941D";
            default -> "#10B981";
        };
        btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 800; -fx-padding: 8px 12px; -fx-background-radius: 8px;",
                color
        ));
    }

    private VBox createAmountSection() {
        VBox box = new VBox(10);
        Label lbl = new Label("QUICK ADD AMOUNT");
        lbl.getStyleClass().add("metric-label");

        HBox presets = new HBox(8);
        presets.setAlignment(Pos.CENTER_LEFT);

        double[] amounts = {50.0, 100.0, 200.0, 500.0};
        for (double amt : amounts) {
            Button btn = new Button(String.format("৳ %.0f", amt));
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.getStyleClass().add("secondary-button");
            btn.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-padding: 10px 10px;");

            if (amt == selectedAmount) {
                applyPresetActiveStyle(btn);
            }

            btn.setOnAction(e -> {
                selectedAmount = amt;
                customAmountField.setText("");
                for (Button b : presetButtons) {
                    b.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-padding: 10px 10px;");
                }
                applyPresetActiveStyle(btn);
                updateCalculations();
            });

            presetButtons.add(btn);
            presets.getChildren().add(btn);
        }

        customAmountField.setPromptText("Or type custom amount (e.g. 150)");
        customAmountField.setStyle("-fx-font-size: 12px; -fx-padding: 8px 12px; -fx-background-radius: 8px;");
        customAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                return;
            }
            try {
                double parsed = Double.parseDouble(newVal.replaceAll("[^0-9.]", ""));
                if (parsed > 0) {
                    selectedAmount = parsed;
                    for (Button b : presetButtons) {
                        b.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-padding: 10px 10px;");
                    }
                    updateCalculations();
                }
            } catch (NumberFormatException ignored) {}
        });

        box.getChildren().addAll(lbl, presets, customAmountField);
        return box;
    }

    private void applyPresetActiveStyle(Button btn) {
        btn.setStyle("-fx-background-color: -fx-navy; -fx-text-fill: white; -fx-font-size: 12.5px; -fx-font-weight: 800; -fx-padding: 10px 10px; -fx-background-radius: 8px;");
    }

    private VBox createSummaryAndAction() {
        VBox box = new VBox(14);

        HBox summaryRow = new HBox(8);
        summaryRow.setAlignment(Pos.CENTER_LEFT);

        Label subLbl = new Label("Balance after recharge:");
        subLbl.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.8;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        newBalLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");

        summaryRow.getChildren().addAll(subLbl, spacer, newBalLabel);

        confirmBtn.setMaxWidth(Double.MAX_VALUE);
        confirmBtn.getStyleClass().add("primary-button");
        confirmBtn.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 800; -fx-padding: 11px 20px; -fx-background-color: #10B981; -fx-text-fill: white;");
        confirmBtn.setOnAction(e -> handleDeposit());

        box.getChildren().addAll(summaryRow, confirmBtn);
        return box;
    }

    private void updateCalculations() {
        double current = WalletService.getInstance().getBalance(user);
        double next = current + selectedAmount;
        newBalLabel.setText(String.format("৳ %.2f", next));
        confirmBtn.setText(String.format("Top Up ৳ %.0f via %s", selectedAmount, selectedMethod));
    }

    private void handleDeposit() {
        if (selectedAmount <= 0) return;
        confirmBtn.setDisable(true);
        confirmBtn.setText("Processing recharge...");

        WalletService.getInstance().deposit(user, selectedAmount, selectedMethod);

        if (onSuccess != null) {
            onSuccess.run();
        }
    }
}
