package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class SettingsModal extends StackPane {

    private final CampusUser user;
    private final Runnable onClose;
    private final Runnable onSignOut;

    public SettingsModal(CampusUser user, Runnable onClose, Runnable onSignOut) {
        ThemeManager.install(this);
        this.user = user;
        this.onClose = onClose;
        this.onSignOut = onSignOut;

        getStyleClass().add("modal-overlay");
        setAlignment(Pos.CENTER);

        VBox sheet = new VBox(22);
        sheet.getStyleClass().add("modal-sheet");
        sheet.setMaxWidth(500);
        sheet.setPadding(new Insets(28));

        HBox header = createHeader();
        VBox hubPref = createHubPreferenceSection();
        VBox notifications = createNotificationsSection();
        VBox account = createAccountSection();

        sheet.getChildren().addAll(header, hubPref, notifications, account);
        getChildren().add(sheet);

        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_SETTINGS, 18, Color.web("#35BFAE")));
        icon.setPrefSize(36, 36);
        icon.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(2);
        Label title = new Label("CampusCycle Preferences");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        Label sub = new Label("Personalize your transit experience and appearance");
        sub.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = ThemeManager.createIconButton(ThemeManager.ICON_CLOSE, 14, "action-icon-btn", onClose);

        row.getChildren().addAll(icon, titleCol, spacer, closeBtn);
        return row;
    }


    private VBox createHubPreferenceSection() {
        VBox box = new VBox(8);
        Label label = new Label("FAVORITE CAMPUS DOCK");
        label.getStyleClass().add("metric-label");

        ComboBox<String> hubBox = new ComboBox<>();
        hubBox.getItems().addAll(bd.ac.kuet.campuscycle.domain.CampusHubs.names());
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(SettingsModal.class);
        String saved = prefs.get("favoriteHub", bd.ac.kuet.campuscycle.domain.CampusHubs.names().get(0));
        hubBox.setValue(saved);
        hubBox.setOnAction(e -> prefs.put("favoriteHub", hubBox.getValue()));
        hubBox.setMaxWidth(Double.MAX_VALUE);
        hubBox.getStyleClass().add("filter-chip");

        box.getChildren().addAll(label, hubBox);
        return box;
    }

    private VBox createNotificationsSection() {
        VBox box = new VBox(10);
        Label label = new Label("SMART ALERTS & TELEMETRY");
        label.getStyleClass().add("metric-label");

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(SettingsModal.class);
        CheckBox returnAlert = new CheckBox("Notify me 10 minutes before rental expiration");
        returnAlert.setSelected(prefs.getBoolean("notifyReturn", true));
        returnAlert.setOnAction(e -> prefs.putBoolean("notifyReturn", returnAlert.isSelected()));
        returnAlert.setStyle("-fx-font-size: 12px;");

        CheckBox lowBatteryAlert = new CheckBox("Show real-time station availability updates");
        lowBatteryAlert.setSelected(prefs.getBoolean("notifyStations", true));
        lowBatteryAlert.setOnAction(e -> prefs.putBoolean("notifyStations", lowBatteryAlert.isSelected()));
        lowBatteryAlert.setStyle("-fx-font-size: 12px;");

        box.getChildren().addAll(label, returnAlert, lowBatteryAlert);
        return box;
    }

    private VBox createAccountSection() {
        VBox box = new VBox(12);
        box.getStyleClass().add("sub-panel");
        box.setPadding(new Insets(14, 16, 14, 16));

        HBox userRow = new HBox(12);
        userRow.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_USER, 16, Color.web("#35BFAE")));
        avatar.setPrefSize(34, 34);
        avatar.getStyleClass().add("action-icon-btn");

        VBox meta = new VBox(2);
        Label name = new Label(user.displayName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: 800;");

        Label email = new Label(user.email());
        email.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        meta.getChildren().addAll(name, email);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button signOutBtn = new Button("Sign Out");
        signOutBtn.getStyleClass().add("danger-button");
        signOutBtn.setOnAction(e -> {
            bd.ac.kuet.campuscycle.data.SessionStore.clear();
            onClose.run();
            if (onSignOut != null) onSignOut.run();
        });

        userRow.getChildren().addAll(avatar, meta, spacer, signOutBtn);
        box.getChildren().add(userRow);
        return box;
    }
}
