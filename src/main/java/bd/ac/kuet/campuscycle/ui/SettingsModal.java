package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.SessionStore;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.prefs.Preferences;

/**
 * Modern translucent frosted-glass settings modal matching the LoginView design language.
 * Features:
 * - Frosted glass card with large corner radius and soft decorative organic corner ribbons
 * - Compact layout wrapped to content size to eliminate empty void space
 * - Centered brand header: mint rounded icon badge, bold title, and emerald accent line
 * - Top row with live theme toggle and close button
 * - Capsule/pill dropdown for favorite campus dock
 * - Frosted sub-panel for notification toggles with persistent preferences
 * - User profile card with sleek red-accented Sign Out pill button
 * - Dark forest green primary "Done" pill button matching LoginView's primary action
 */
public class SettingsModal extends StackPane {

    private final CampusUser user;
    private final bd.ac.kuet.campuscycle.data.CampusRepository repo;
    private final Runnable onClose;
    private final Runnable onSignOut;

    public SettingsModal(CampusUser user, Runnable onClose, Runnable onSignOut) {
        this(user, null, onClose, onSignOut);
    }

    public SettingsModal(CampusUser user, bd.ac.kuet.campuscycle.data.CampusRepository repo, Runnable onClose, Runnable onSignOut) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = (repo != null) ? repo : (bd.ac.kuet.campuscycle.data.DatabaseConnection.isAvailable()
                ? new bd.ac.kuet.campuscycle.data.SupabaseCampusRepository()
                : new bd.ac.kuet.campuscycle.data.InMemoryCampusRepository());
        this.onClose = onClose;
        this.onSignOut = onSignOut;

        setAlignment(Pos.CENTER);
        setPadding(new Insets(20));

        // Translucent backdrop overlay that closes the modal on click outside
        Runnable updateOverlay = () -> {
            boolean isDark = ThemeManager.isDark();
            setStyle(isDark
                    ? "-fx-background-color: rgba(15, 23, 42, 0.65);"
                    : "-fx-background-color: rgba(15, 23, 42, 0.45);");
        };
        updateOverlay.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateOverlay.run());

        setOnMouseClicked(e -> {
            if (e.getTarget() == this) {
                onClose.run();
            }
        });

        StackPane card = createSettingsCard();
        getChildren().add(card);

        ThemeManager.applyFadeIn(this);
    }

    private StackPane createSettingsCard() {
        StackPane cardStack = new StackPane();
        cardStack.setPrefWidth(360);
        cardStack.setMaxWidth(380);
        cardStack.setMinWidth(330);
        cardStack.setMaxHeight(Region.USE_PREF_SIZE);
        cardStack.setAlignment(Pos.CENTER);
        StackPane.setAlignment(cardStack, Pos.CENTER);
        cardStack.setOnMouseClicked(javafx.event.Event::consume);

        // Corner organic ribbon accents matching LoginView
        Region topLeftWave = new Region();
        topLeftWave.setMaxSize(90, 80);
        topLeftWave.setStyle(
                "-fx-background-color: radial-gradient(focus-angle 45deg, focus-distance 20%, center 20% 20%, radius 80%, rgba(16, 185, 129, 0.18), transparent 80%);" +
                "-fx-background-radius: 28px 0 50px 0;"
        );
        StackPane.setAlignment(topLeftWave, Pos.TOP_LEFT);

        Region bottomRightWave = new Region();
        bottomRightWave.setMaxSize(100, 90);
        bottomRightWave.setStyle(
                "-fx-background-color: radial-gradient(focus-angle 225deg, focus-distance 20%, center 80% 80%, radius 80%, rgba(16, 185, 129, 0.20), transparent 80%);" +
                "-fx-background-radius: 50px 0 28px 0;"
        );
        StackPane.setAlignment(bottomRightWave, Pos.BOTTOM_RIGHT);

        VBox contentBox = new VBox(11);
        contentBox.setPadding(new Insets(14, 24, 18, 24));
        contentBox.setAlignment(Pos.CENTER);
        contentBox.setMaxWidth(380);

        Runnable applyCardStyle = () -> {
            boolean isDark = ThemeManager.isDark();
            cardStack.setStyle(
                    (isDark
                        ? "-fx-background-color: rgba(15, 23, 42, 0.90); -fx-border-color: rgba(255, 255, 255, 0.20); "
                        : "-fx-background-color: rgba(255, 255, 255, 0.92); -fx-border-color: rgba(255, 255, 255, 0.90); ") +
                    "-fx-background-radius: 28px; " +
                    "-fx-border-radius: 28px; " +
                    "-fx-border-width: 1.5px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.22), 40, 0, 0, 16);"
            );
        };
        applyCardStyle.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> applyCardStyle.run());

        // 1. Top row: Theme toggle on left, Close button on right
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);
        topRow.setMaxWidth(Double.MAX_VALUE);

        Button themeBtn = new Button();
        Runnable updateThemeIcon = () -> {
            boolean isDark = ThemeManager.isDark();
            themeBtn.setGraphic(ThemeManager.createIcon(
                    isDark ? ThemeManager.ICON_SUN : ThemeManager.ICON_MOON,
                    13,
                    isDark ? Color.web("#F59E0B") : Color.web("#334155")
            ));
            themeBtn.setStyle(
                    (isDark ? "-fx-background-color: rgba(30, 41, 59, 0.85); " : "-fx-background-color: rgba(241, 245, 249, 0.85); ") +
                    "-fx-background-radius: 999px; -fx-padding: 5px 8px; -fx-cursor: hand;"
            );
        };
        updateThemeIcon.run();
        themeBtn.setOnAction(e -> {
            ThemeManager.toggleTheme();
            updateThemeIcon.run();
        });
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateThemeIcon.run());

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Button closeBtn = new Button();
        Runnable updateCloseStyle = () -> {
            boolean isDark = ThemeManager.isDark();
            closeBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_CLOSE, 12, isDark ? Color.web("#94A3B8") : Color.web("#64748B")));
            closeBtn.setStyle(
                    (isDark ? "-fx-background-color: rgba(30, 41, 59, 0.85); " : "-fx-background-color: rgba(241, 245, 249, 0.85); ") +
                    "-fx-background-radius: 999px; -fx-padding: 5px 8px; -fx-cursor: hand;"
            );
        };
        updateCloseStyle.run();
        closeBtn.setOnAction(e -> onClose.run());
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateCloseStyle.run());

        topRow.getChildren().addAll(themeBtn, topSpacer, closeBtn);

        // 2. Centered Branding / Header
        VBox branding = new VBox(3);
        branding.setAlignment(Pos.CENTER);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_SETTINGS, 20, Color.web("#059669")));
        iconBadge.setPrefSize(44, 44);
        iconBadge.setMaxSize(44, 44);
        iconBadge.setStyle(
                "-fx-background-color: rgba(16, 185, 129, 0.14); " +
                "-fx-background-radius: 999px; " +
                "-fx-border-color: rgba(16, 185, 129, 0.28); " +
                "-fx-border-radius: 999px; " +
                "-fx-border-width: 1px;"
        );

        Label titleLbl = new Label("Preferences");
        Runnable updateTitleText = () -> {
            boolean isDark = ThemeManager.isDark();
            titleLbl.setStyle("-fx-font-size: 19px; -fx-font-weight: 800; -fx-text-fill: " + (isDark ? "#F8FAFC;" : "#0F172A;"));
        };
        updateTitleText.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateTitleText.run());

        Label subtitleLbl = new Label("Personalize your transit experience & account");
        subtitleLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: #94A3B8;");

        Region accentLine = new Region();
        accentLine.setPrefSize(26, 2.5);
        accentLine.setMaxSize(26, 2.5);
        accentLine.setStyle("-fx-background-color: #10B981; -fx-background-radius: 2px;");

        branding.getChildren().addAll(iconBadge, titleLbl, subtitleLbl, accentLine);

        // 3. Favorite Campus Dock Section
        VBox dockSection = new VBox(5);
        dockSection.setAlignment(Pos.CENTER_LEFT);
        dockSection.setMaxWidth(Double.MAX_VALUE);

        Label dockLabel = new Label("FAVORITE CAMPUS DOCK");
        dockLabel.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 800; -fx-text-fill: #059669; -fx-letter-spacing: 0.5px;");

        HBox dockPill = new HBox(10);
        dockPill.setAlignment(Pos.CENTER_LEFT);
        dockPill.setPrefHeight(40);
        dockPill.setMaxHeight(40);
        dockPill.setPadding(new Insets(0, 12, 0, 14));

        Runnable updateDockPillStyle = () -> {
            boolean isDark = ThemeManager.isDark();
            dockPill.setStyle(
                    (isDark
                        ? "-fx-background-color: rgba(30, 41, 59, 0.70); -fx-border-color: rgba(255, 255, 255, 0.15); "
                        : "-fx-background-color: rgba(255, 255, 255, 0.80); -fx-border-color: rgba(226, 232, 240, 0.90); ") +
                    "-fx-background-radius: 999px; -fx-border-radius: 999px; -fx-border-width: 1px;"
            );
        };
        updateDockPillStyle.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateDockPillStyle.run());

        SVGPath pinIcon = ThemeManager.createIcon(ThemeManager.ICON_PIN, 14, Color.web("#059669"));

        ComboBox<String> hubBox = new ComboBox<>();
        hubBox.getItems().addAll(CampusHubs.names());
        Preferences prefs = Preferences.userNodeForPackage(SettingsModal.class);
        String savedHub = prefs.get("favoriteHub", CampusHubs.names().get(0));
        hubBox.setValue(savedHub);
        hubBox.setOnAction(e -> {
            if (hubBox.getValue() != null) {
                prefs.put("favoriteHub", hubBox.getValue());
            }
        });
        hubBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(hubBox, Priority.ALWAYS);
        hubBox.setStyle("-fx-background-color: transparent; -fx-font-size: 12px; -fx-font-weight: 600; -fx-cursor: hand;");

        dockPill.getChildren().addAll(pinIcon, hubBox);
        dockSection.getChildren().addAll(dockLabel, dockPill);

        // (The "Notifications & Alerts" toggles were deleted: both preference keys
        // were persisted here and never read by any code, P-097/P-187. The favorite
        // dock above stays — DashboardView reads "favoriteHub" for suggestions.)

        // 5. Account & Session Card
        VBox accountSection = new VBox(5);
        accountSection.setAlignment(Pos.CENTER_LEFT);
        accountSection.setMaxWidth(Double.MAX_VALUE);

        Label accountLabel = new Label("ACCOUNT & SESSION");
        accountLabel.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 800; -fx-text-fill: #059669; -fx-letter-spacing: 0.5px;");

        HBox accountCard = new HBox(10);
        accountCard.setAlignment(Pos.CENTER_LEFT);
        accountCard.setPadding(new Insets(8, 12, 8, 12));
        Runnable updateAccountCardStyle = () -> {
            boolean isDark = ThemeManager.isDark();
            accountCard.setStyle(
                    (isDark
                        ? "-fx-background-color: rgba(30, 41, 59, 0.60); -fx-border-color: rgba(255, 255, 255, 0.10); "
                        : "-fx-background-color: rgba(248, 250, 252, 0.85); -fx-border-color: rgba(226, 232, 240, 0.70); ") +
                    "-fx-background-radius: 16px; -fx-border-radius: 16px; -fx-border-width: 1px;"
            );
        };
        updateAccountCardStyle.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateAccountCardStyle.run());

        StackPane userAvatar = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_USER, 15, Color.web("#059669")));
        userAvatar.setPrefSize(34, 34);
        userAvatar.setMinSize(34, 34);
        userAvatar.setStyle("-fx-background-color: rgba(16, 185, 129, 0.15); -fx-background-radius: 999px;");

        VBox userMeta = new VBox(1);
        HBox.setHgrow(userMeta, Priority.ALWAYS);
        Label nameLbl = new Label(user != null && user.displayName() != null ? user.displayName() : "KUET Cyclist");
        // A long name must ellipsize inside the 380px card, not shove the buttons out (P-069).
        nameLbl.setMaxWidth(170);
        nameLbl.setEllipsisString("...");
        nameLbl.setTooltip(new Tooltip(nameLbl.getText()));
        Runnable updateUserNameStyle = () -> {
            boolean isDark = ThemeManager.isDark();
            nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: " + (isDark ? "#F8FAFC;" : "#0F172A;"));
        };
        updateUserNameStyle.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateUserNameStyle.run());

        Label emailLbl = new Label(user != null && user.email() != null ? user.email() : "student@kuet.ac.bd");
        emailLbl.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #94A3B8;");
        emailLbl.setMaxWidth(170);
        emailLbl.setEllipsisString("...");
        emailLbl.setTooltip(new Tooltip(emailLbl.getText()));
        userMeta.getChildren().addAll(nameLbl, emailLbl);

        Region accSpacer = new Region();
        HBox.setHgrow(accSpacer, Priority.ALWAYS);

        Button changePwdBtn = new Button("Password");
        changePwdBtn.setStyle(
                "-fx-background-color: rgba(59, 130, 246, 0.12); " +
                "-fx-text-fill: #2563EB; " +
                "-fx-border-color: rgba(59, 130, 246, 0.35); " +
                "-fx-border-radius: 999px; " +
                "-fx-background-radius: 999px; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: 700; " +
                "-fx-padding: 4px 10px; " +
                "-fx-cursor: hand;"
        );
        changePwdBtn.setOnMouseEntered(e -> changePwdBtn.setStyle(
                "-fx-background-color: #2563EB; -fx-text-fill: white; -fx-border-color: #2563EB; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 4px 10px; -fx-cursor: hand;"
        ));
        changePwdBtn.setOnMouseExited(e -> changePwdBtn.setStyle(
                "-fx-background-color: rgba(59, 130, 246, 0.12); -fx-text-fill: #2563EB; -fx-border-color: rgba(59, 130, 246, 0.35); -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 4px 10px; -fx-cursor: hand;"
        ));
        changePwdBtn.setOnAction(e -> showChangePasswordDialog());

        accountCard.getChildren().addAll(userAvatar, userMeta, accSpacer, changePwdBtn);
        accountSection.getChildren().addAll(accountLabel, accountCard);

        // 6. Danger Zone: Sign Out sits alone, full-width, below a separator and
        // behind a confirmation — never one click beside a routine button (P-069).
        VBox dangerSection = new VBox(6);
        dangerSection.setAlignment(Pos.CENTER_LEFT);
        dangerSection.setMaxWidth(Double.MAX_VALUE);

        Label dangerLabel = new Label("DANGER ZONE");
        dangerLabel.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 800; -fx-text-fill: #DC2626; -fx-letter-spacing: 0.5px;");

        Separator dangerSep = new Separator();

        Button signOutBtn = new Button("Sign Out");
        signOutBtn.setMaxWidth(Double.MAX_VALUE);
        signOutBtn.getStyleClass().add("danger-button");
        signOutBtn.setAccessibleText("Sign out of CampusCycle");
        signOutBtn.setTooltip(new Tooltip("End the current session and return to the login screen"));
        signOutBtn.setStyle(
                "-fx-background-color: rgba(239, 68, 68, 0.12); " +
                "-fx-text-fill: #EF4444; " +
                "-fx-border-color: rgba(239, 68, 68, 0.35); " +
                "-fx-border-radius: 999px; " +
                "-fx-background-radius: 999px; " +
                "-fx-font-size: 12px; " +
                "-fx-font-weight: 700; " +
                "-fx-padding: 8px 10px; " +
                "-fx-cursor: hand;"
        );
        signOutBtn.setOnMouseEntered(e -> signOutBtn.setStyle(
                "-fx-background-color: #EF4444; -fx-text-fill: white; -fx-border-color: #EF4444; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: 700; -fx-padding: 8px 10px; -fx-cursor: hand;"
        ));
        signOutBtn.setOnMouseExited(e -> signOutBtn.setStyle(
                "-fx-background-color: rgba(239, 68, 68, 0.12); -fx-text-fill: #EF4444; -fx-border-color: rgba(239, 68, 68, 0.35); -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: 700; -fx-padding: 8px 10px; -fx-cursor: hand;"
        ));
        signOutBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Sign out of CampusCycle? Any unsaved progress on this screen will be lost.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Sign Out");
            confirm.setHeaderText(null);
            ThemeManager.install(confirm.getDialogPane());
            if (confirm.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
                SessionStore.clear();
                onClose.run();
                if (onSignOut != null) onSignOut.run();
            }
        });

        dangerSection.getChildren().addAll(dangerLabel, dangerSep, signOutBtn);

        // 7. Primary "Done" Pill Button (matches LoginView's primary button)
        Button doneBtn = new Button("Done  ✓");
        doneBtn.setMaxWidth(Double.MAX_VALUE);
        doneBtn.setPrefHeight(40);
        doneBtn.setStyle(
                "-fx-background-color: #064E3B; " +
                "-fx-background-radius: 999px; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-cursor: hand;"
        );
        doneBtn.setOnMouseEntered(e -> doneBtn.setStyle(
                "-fx-background-color: #047857; -fx-background-radius: 999px; -fx-text-fill: #FFFFFF; -fx-font-size: 13.5px; -fx-font-weight: 700; -fx-cursor: hand;"
        ));
        doneBtn.setOnMouseExited(e -> doneBtn.setStyle(
                "-fx-background-color: #064E3B; -fx-background-radius: 999px; -fx-text-fill: #FFFFFF; -fx-font-size: 13.5px; -fx-font-weight: 700; -fx-cursor: hand;"
        ));
        doneBtn.setOnAction(e -> onClose.run());

        contentBox.getChildren().addAll(
                topRow,
                branding,
                dockSection,
                accountSection,
                dangerSection,
                doneBtn
        );

        cardStack.getChildren().addAll(topLeftWave, bottomRightWave, contentBox);
        return cardStack;
    }

    private void showChangePasswordDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        ThemeManager.install(pane);
        pane.getStyleClass().add("modal-sheet");
        pane.setMinWidth(380);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        Label title = new Label("Update Account Password");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");

        Label curLbl = new Label("Current Password");
        curLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
        PasswordField curPwd = new PasswordField();
        curPwd.setPromptText("Enter current password");
        curPwd.setAccessibleText("Current password");
        curLbl.setLabelFor(curPwd);
        curPwd.getStyleClass().add("modern-input");

        Label newLbl = new Label("New Password (min 6 characters)");
        newLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
        PasswordField newPwd = new PasswordField();
        newPwd.setPromptText("Enter new password");
        newPwd.setAccessibleText("New password, at least 6 characters");
        newLbl.setLabelFor(newPwd);
        newPwd.getStyleClass().add("modern-input");

        Label confLbl = new Label("Confirm New Password");
        confLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
        PasswordField confPwd = new PasswordField();
        confPwd.setPromptText("Re-type new password");
        confPwd.setAccessibleText("Confirm new password");
        confLbl.setLabelFor(confPwd);
        confPwd.getStyleClass().add("modern-input");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #EF4444; -fx-wrap-text: true;");
        errorLbl.setVisible(false);

        box.getChildren().addAll(title, curLbl, curPwd, newLbl, newPwd, confLbl, confPwd, errorLbl);
        pane.setContent(box);

        ButtonType updateBtnType = new ButtonType("Update Password", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(updateBtnType, ButtonType.CANCEL);

        Button updateBtn = (Button) pane.lookupButton(updateBtnType);
        updateBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String cur = curPwd.getText();
            String np = newPwd.getText();
            String cp = confPwd.getText();

            if (cur == null || cur.isBlank()) {
                errorLbl.setText("Current password is required.");
                errorLbl.setVisible(true);
                evt.consume();
                return;
            }
            if (np == null || np.length() < 6) {
                errorLbl.setText("New password must be at least 6 characters.");
                errorLbl.setVisible(true);
                evt.consume();
                return;
            }
            if (!np.equals(cp)) {
                errorLbl.setText("Passwords do not match.");
                errorLbl.setVisible(true);
                evt.consume();
                return;
            }

            try {
                boolean ok = false;
                if (user != null) {
                    ok = repo.changePassword(user.id(), cur, np);
                    if (!ok && user.email() != null) {
                        ok = repo.changePassword(user.email(), cur, np);
                    }
                }
                if (ok) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION, "Password changed successfully!", ButtonType.OK);
                    ThemeManager.install(successAlert.getDialogPane());
                    successAlert.showAndWait();
                } else {
                    errorLbl.setText("Account registration record not found to update.");
                    errorLbl.setVisible(true);
                    evt.consume();
                }
            } catch (Exception ex) {
                errorLbl.setText(ex.getMessage());
                errorLbl.setVisible(true);
                evt.consume();
            }
        });

        dialog.showAndWait();
    }
}
