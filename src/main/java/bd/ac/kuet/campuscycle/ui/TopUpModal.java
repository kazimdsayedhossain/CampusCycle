package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.WalletService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Modern compact Top-Up modal for CampusCycle Prepaid Wallet.
 * Supports quick-add presets (৳50, ৳100, ৳200, ৳500),
 * payment channels (bKash, Nagad, Student ID), and custom amount entry.
 *
 * <p>Money contract (P-023): amounts are integer poisha end to end.
 * Custom input is parsed with {@link Money#parseBdtToPoisha} and failures are
 * shown inline — never silently swallowed. The service cap is displayed, not
 * discovered via an error alert.
 */
public class TopUpModal extends StackPane {

    private final CampusUser user;
    private final Runnable onClose;
    private final Runnable onSuccess;

    private int selectedAmountPoisha = 10_000;
    private String selectedMethod = "bKash";
    private int cachedBalancePoisha = 0;
    /** Stable per-modal idempotency key so a retried tap cannot double-charge. */
    private final String topupRef = "TOPUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    private final List<Button> presetButtons = new ArrayList<>();
    private final List<Button> methodButtons = new ArrayList<>();

    private final Label currentBalLabel = new Label();
    private final Label newBalLabel = new Label();
    private final Label amountErrorLabel = new Label();
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

        setAccessibleRole(AccessibleRole.DIALOG);

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

        // ESC closes the modal; initial focus lands on the custom amount field.
        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                onClose.run();
            }
        });
        Platform.runLater(() -> customAmountField.requestFocus());
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BOLT, 18, Color.web("#10B981")));
        icon.setPrefSize(38, 38);
        icon.getStyleClass().add("icon-badge");
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

        cachedBalancePoisha = user != null ? WalletService.getInstance().getBalancePoisha(user.id()) : 0;
        currentBalLabel.setText(Money.formatTaka(cachedBalancePoisha));
        currentBalLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");

        // Background refresh to guarantee accurate balance without blocking modal rendering
        AppExecutor.asyncThenFx(
                () -> user != null ? WalletService.getInstance().getBalancePoisha(user.id()) : 0,
                bal -> {
                    cachedBalancePoisha = bal;
                    currentBalLabel.setText(Money.formatTaka(bal));
                    updateCalculations();
                },
                err -> {}
        );

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
            btn.getStyleClass().add("filter-chip");

            if (m.equals(selectedMethod)) {
                btn.getStyleClass().add("filter-chip-active");
            }

            btn.setOnAction(e -> {
                selectedMethod = m;
                for (Button b : methodButtons) {
                    b.getStyleClass().remove("filter-chip-active");
                }
                btn.getStyleClass().add("filter-chip-active");
                updateCalculations();
            });

            methodButtons.add(btn);
            methods.getChildren().add(btn);
        }

        box.getChildren().addAll(lbl, methods);
        return box;
    }

    private VBox createAmountSection() {
        VBox box = new VBox(10);
        Label lbl = new Label("QUICK ADD AMOUNT");
        lbl.getStyleClass().add("metric-label");

        HBox presets = new HBox(8);
        presets.setAlignment(Pos.CENTER_LEFT);

        int[] amountsPoisha = {5_000, 10_000, 20_000, 50_000};
        for (int amt : amountsPoisha) {
            Button btn = new Button("৳" + amt / 100);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.getStyleClass().add("filter-chip");

            if (amt == selectedAmountPoisha) {
                btn.getStyleClass().add("filter-chip-active");
            }
            btn.setUserData(amt);

            btn.setOnAction(e -> {
                selectedAmountPoisha = (int) btn.getUserData();
                customAmountField.setText("");
                clearParseError();
                for (Button b : presetButtons) {
                    b.getStyleClass().remove("filter-chip-active");
                }
                btn.getStyleClass().add("filter-chip-active");
                updateCalculations();
            });

            presetButtons.add(btn);
            presets.getChildren().add(btn);
        }

        customAmountField.setPromptText("Or type custom amount (e.g. 150)");
        customAmountField.getStyleClass().add("modern-input");
        customAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                clearParseError();
                return;
            }
            try {
                int parsed = Money.parseBdtToPoisha(newVal);
                if (parsed <= 0) {
                    showParseError("Amount must be greater than zero.");
                    return;
                }
                if (parsed > WalletService.MAX_SINGLE_DEPOSIT_POISHA) {
                    selectedAmountPoisha = WalletService.MAX_SINGLE_DEPOSIT_POISHA;
                    clearPresetSelection();
                    showParseError("Capped at the max single top-up of "
                            + Money.formatTaka(WalletService.MAX_SINGLE_DEPOSIT_POISHA) + ".");
                    updateCalculations();
                    return;
                }
                clearParseError();
                selectedAmountPoisha = parsed;
                clearPresetSelection();
                updateCalculations();
            } catch (IllegalArgumentException ex) {
                showParseError(ex.getMessage());
            }
        });

        amountErrorLabel.setStyle("-fx-text-fill: -fx-danger; -fx-font-size: 11.5px; -fx-font-weight: 700;");
        amountErrorLabel.setWrapText(true);
        amountErrorLabel.setVisible(false);
        amountErrorLabel.setManaged(false);

        Label maxHint = new Label("Max single top-up: " + Money.formatTaka(WalletService.MAX_SINGLE_DEPOSIT_POISHA));
        maxHint.setStyle("-fx-font-size: 11px; -fx-opacity: 0.65;");

        box.getChildren().addAll(lbl, presets, customAmountField, amountErrorLabel, maxHint);
        return box;
    }

    private void clearPresetSelection() {
        for (Button b : presetButtons) {
            b.getStyleClass().remove("filter-chip-active");
        }
    }

    private void showParseError(String message) {
        amountErrorLabel.setText(message);
        amountErrorLabel.setVisible(true);
        amountErrorLabel.setManaged(true);
    }

    private void clearParseError() {
        amountErrorLabel.setVisible(false);
        amountErrorLabel.setManaged(false);
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

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> onClose.run());

        confirmBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(confirmBtn, Priority.ALWAYS);
        confirmBtn.getStyleClass().add("primary-button");
        confirmBtn.setOnAction(e -> handleDeposit());

        actions.getChildren().addAll(cancelBtn, confirmBtn);

        box.getChildren().addAll(summaryRow, actions);
        return box;
    }

    private void updateCalculations() {
        int current = cachedBalancePoisha;
        int next = current + selectedAmountPoisha;
        newBalLabel.setText(Money.formatTaka(next));
        confirmBtn.setText("Top Up " + Money.formatTaka(selectedAmountPoisha) + " via " + selectedMethod);
        confirmBtn.setDisable(selectedAmountPoisha <= 0);
    }

    private void handleDeposit() {
        if (selectedAmountPoisha <= 0 || user == null) return;
        int amount = selectedAmountPoisha;
        confirmBtn.setDisable(true);
        confirmBtn.setText("Processing recharge...");

        AppExecutor.asyncThenFx(
                () -> WalletService.getInstance().depositPoisha(user.id(), amount, selectedMethod, topupRef),
                success -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                },
                error -> {
                    confirmBtn.setDisable(false);
                    updateCalculations();
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Recharge failed: " + error.getMessage(), ButtonType.OK);
                    ThemeManager.install(alert.getDialogPane());
                    alert.showAndWait();
                }
        );
    }
}
