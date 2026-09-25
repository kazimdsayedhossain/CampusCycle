package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.DatabaseConnection;
import bd.ac.kuet.campuscycle.data.SessionStore;
import bd.ac.kuet.campuscycle.data.SupabaseAuthService;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.function.Consumer;

/**
 * Modern login screen inspired by Forselle and clean mobility portals.
 * Features rounded pill inputs, leading SVG glyphs, interactive password eye toggle,
 * instant theme switcher, and production-grade database-backed authentication.
 */
public class LoginView extends StackPane {

    private final Consumer<CampusUser> onLogin;

    public LoginView(Consumer<CampusUser> onLogin) {
        this.onLogin = onLogin;

        setAlignment(Pos.CENTER);
        setPadding(new Insets(32));

        HBox card = createLoginCard();
        getChildren().add(card);

        ThemeManager.applyFadeIn(card);
    }

    private HBox createLoginCard() {
        HBox card = new HBox();
        card.getStyleClass().add("bento-card");
        card.setMaxWidth(920);
        card.setPrefWidth(920);
        card.setMinHeight(560);
        card.setAlignment(Pos.CENTER);

        VBox leftBrand = createBrandShowcase();
        VBox rightForm = createLoginForm();

        HBox.setHgrow(leftBrand, Priority.ALWAYS);
        HBox.setHgrow(rightForm, Priority.ALWAYS);

        card.getChildren().addAll(leftBrand, rightForm);
        return card;
    }

    private VBox createBrandShowcase() {
        VBox box = new VBox(20);
        box.getStyleClass().add("sub-panel");
        box.setPadding(new Insets(40));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(420);

        // Logo Island
        HBox logoRow = new HBox(14);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        StackPane iconCircle = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 22, Color.web("#1D4ED8")));
        iconCircle.setPrefSize(48, 48);
        iconCircle.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(2);
        Label title = new Label("CampusCycle");
        title.getStyleClass().add("brand-title");
        title.setStyle("-fx-font-size: 20px;");

        Label sub = new Label("KUET SMART MOBILITY");
        sub.getStyleClass().add("brand-sub");

        titleCol.getChildren().addAll(title, sub);
        logoRow.getChildren().addAll(iconCircle, titleCol);

        Label desc = new Label(
                "Autonomous smart bicycle transit connecting university academic departments, student halls, and campus gates with zero carbon emissions."
        );
        desc.setWrapText(true);
        desc.getStyleClass().add("metric-label");
        desc.setStyle("-fx-font-size: 13px; -fx-line-spacing: 4px; -fx-opacity: 0.85;");

        // Feature Bullets
        VBox features = new VBox(12);
        features.getChildren().addAll(
                createFeatureItem(ThemeManager.ICON_LEAF, "100% Zero-Emission Commute", "Zero fuel, pure electric & pedal fleet"),
                createFeatureItem(ThemeManager.ICON_PIN, "5 Smart Campus Hubs", "Automated quad-docks at Library, SWC, and Gates"),
                createFeatureItem(ThemeManager.ICON_SHIELD, "Khulna Metropolitan Roaming", "Extended travel permitted throughout Khulna city")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("Khulna University of Engineering & Technology");
        footer.getStyleClass().add("metric-label");
        footer.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6; -fx-font-weight: 600;");

        box.getChildren().addAll(logoRow, desc, features, spacer, footer);
        return box;
    }

    private HBox createFeatureItem(String svg, String headline, String detail) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBox = new StackPane(ThemeManager.createIcon(svg, 14, Color.web("#1D4ED8")));
        iconBox.setPrefSize(32, 32);
        iconBox.getStyleClass().add("action-icon-btn");

        VBox textCol = new VBox(2);
        Label h = new Label(headline);
        h.getStyleClass().add("card-title");
        h.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700;");

        Label d = new Label(detail);
        d.getStyleClass().add("metric-label");
        d.setStyle("-fx-opacity: 0.7;");

        textCol.getChildren().addAll(h, d);
        row.getChildren().addAll(iconBox, textCol);
        return row;
    }

    private VBox createLoginForm() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(40));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(500);

        // Header with Theme Toggle
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);

        Button themeBtn = ThemeManager.createIconButton(
                ThemeManager.isDark() ? ThemeManager.ICON_SUN : ThemeManager.ICON_MOON,
                15,
                "action-icon-btn",
                () -> ThemeManager.toggleTheme()
        );
        ThemeManager.themeProperty().addListener((obs, o, n) -> {
            themeBtn.setGraphic(ThemeManager.createIcon(
                    ThemeManager.isDark() ? ThemeManager.ICON_SUN : ThemeManager.ICON_MOON,
                    15,
                    Color.web("#64748B")
            ));
        });
        topRow.getChildren().add(themeBtn);

        VBox titleCol = new VBox(4);
        Label welcome = new Label("Welcome Back");
        welcome.getStyleClass().add("card-title");
        welcome.setStyle("-fx-font-size: 24px; -fx-font-weight: 800;");

        Label sub = new Label("Sign in to unlock bikes, track active rides, and manage rentals.");
        sub.getStyleClass().add("metric-label");
        sub.setStyle("-fx-opacity: 0.75;");
        titleCol.getChildren().addAll(welcome, sub);

        Label configNote = new Label();
        if (DatabaseConnection.isAvailable()) {
            configNote.setText("⚡ KUET Mobility Network • Live PostgreSQL Connected");
            configNote.setStyle("-fx-text-fill: #10B981; -fx-font-weight: 700; -fx-font-size: 11.5px;");
        } else {
            configNote.setText("⚡ KUET Smart Mobility • Local Persistence Store");
            configNote.setStyle("-fx-text-fill: #0EA5E9; -fx-font-weight: 700; -fx-font-size: 11.5px;");
        }
        configNote.getStyleClass().add("metric-label");

        // Email / Student Roll Input
        Label emailLbl = new Label("UNIVERSITY EMAIL / STUDENT ROLL");
        emailLbl.getStyleClass().add("metric-label");

        HBox emailBox = new HBox(10);
        emailBox.setAlignment(Pos.CENTER_LEFT);
        emailBox.getStyleClass().add("input-pill-box");

        SVGPath mailIcon = ThemeManager.createIcon(ThemeManager.ICON_MAIL, 15, Color.web("#9CA3AF"));
        TextField emailField = new TextField();
        emailField.setPromptText("e.g. arafat@kuet.ac.bd or 1907001");
        emailField.getStyleClass().add("bare-input");
        HBox.setHgrow(emailField, Priority.ALWAYS);

        emailBox.getChildren().addAll(mailIcon, emailField);

        // Password Input with Interactive Eye Toggle
        Label passLbl = new Label("PASSWORD");
        passLbl.getStyleClass().add("metric-label");

        HBox passBox = new HBox(10);
        passBox.setAlignment(Pos.CENTER_LEFT);
        passBox.getStyleClass().add("input-pill-box");

        SVGPath lockIcon = ThemeManager.createIcon(ThemeManager.ICON_LOCK, 15, Color.web("#9CA3AF"));

        PasswordField maskedField = new PasswordField();
        maskedField.setPromptText("Enter your password");
        maskedField.getStyleClass().add("bare-input");

        TextField visibleField = new TextField();
        visibleField.setPromptText("Enter your password");
        visibleField.getStyleClass().add("bare-input");
        visibleField.setVisible(false);
        visibleField.setManaged(false);

        // Sync text between masked and visible fields
        maskedField.textProperty().bindBidirectional(visibleField.textProperty());

        StackPane fieldStack = new StackPane(maskedField, visibleField);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);

        BooleanProperty isPasswordVisible = new SimpleBooleanProperty(false);
        Button eyeBtn = ThemeManager.createIconButton(ThemeManager.ICON_EYE, 14, "action-icon-btn", null);
        eyeBtn.setOnAction(e -> {
            boolean show = !isPasswordVisible.get();
            isPasswordVisible.set(show);
            maskedField.setVisible(!show);
            maskedField.setManaged(!show);
            visibleField.setVisible(show);
            visibleField.setManaged(show);
            eyeBtn.setGraphic(ThemeManager.createIcon(show ? ThemeManager.ICON_EYE_OFF : ThemeManager.ICON_EYE, 14, Color.web("#6B7280")));
        });
        eyeBtn.setStyle("-fx-padding: 4px 8px; -fx-background-radius: 12px;");

        passBox.getChildren().addAll(lockIcon, fieldStack, eyeBtn);

        // Error message label
        Label errorLbl = new Label();
        errorLbl.getStyleClass().add("metric-label");
        errorLbl.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 11.5px; -fx-font-weight: 700;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        // Submit Button
        Button signInBtn = new Button("Sign In to CampusCycle");
        signInBtn.getStyleClass().add("primary-button");
        signInBtn.setMaxWidth(Double.MAX_VALUE);
        signInBtn.setOnAction(e -> {
            String email = emailField.getText();
            String pass = maskedField.getText();
            try {
                SupabaseAuthService.validate(
                        email == null ? "" : email.trim().toLowerCase(), pass == null ? "" : pass);
            } catch (IllegalArgumentException ex) {
                errorLbl.setText(ex.getMessage());
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }

            errorLbl.setVisible(false);
            errorLbl.setManaged(false);
            signInBtn.setDisable(true);
            signInBtn.setText("Authenticating with KUET Network...");

            String trimmed = email.trim().toLowerCase();
            String password = pass;

            SupabaseAuthService.signIn(trimmed, password)
                    .thenAccept(session -> javafx.application.Platform.runLater(() -> {
                        SessionStore.set(session.accessToken(), session.user().id());
                        onLogin.accept(session.user());
                    }))
                    .exceptionally(err -> {
                        javafx.application.Platform.runLater(() -> {
                            signInBtn.setDisable(false);
                            signInBtn.setText("Sign In to CampusCycle");
                            errorLbl.setText("Sign-in error: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage()));
                            errorLbl.setVisible(true);
                            errorLbl.setManaged(true);
                        });
                        return null;
                    });
        });

        // Helpful credentials guideline
        VBox hintBox = new VBox(4);
        hintBox.setStyle("-fx-padding: 10px 14px; -fx-background-color: rgba(2, 132, 199, 0.08); -fx-background-radius: 10px; -fx-border-color: rgba(2, 132, 199, 0.25); -fx-border-radius: 10px;");
        Label hintHeader = new Label("KUET Identity Access");
        hintHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: 750; -fx-text-fill: #0284C7;");
        Label hintBody = new Label("Student Account: arafat@kuet.ac.bd (or your student roll)\nCycle Office Admin: cycleoffice@kuet.ac.bd (password: any 6+ chars)");
        hintBody.setStyle("-fx-font-size: 10.5px; -fx-opacity: 0.8; -fx-line-spacing: 2px;");
        hintBox.getChildren().addAll(hintHeader, hintBody);

        box.getChildren().addAll(
                topRow,
                titleCol,
                configNote,
                emailLbl, emailBox,
                passLbl, passBox,
                errorLbl,
                signInBtn,
                hintBox
        );

        return box;
    }
}
