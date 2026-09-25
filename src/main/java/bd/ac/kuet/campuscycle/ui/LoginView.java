package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Role;
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

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modern login screen inspired by Forselle and clean mobility portals.
 * Features rounded pill inputs, leading SVG glyphs, interactive password eye toggle,
 * instant theme switcher, and one-click Student/Admin demo launchers.
 */
public class LoginView extends StackPane {

    private final Consumer<CampusUser> onLogin;
    private final Runnable onStudentDemo;
    private final Runnable onAdminDemo;

    public LoginView(Consumer<CampusUser> onLogin, Runnable onStudentDemo, Runnable onAdminDemo) {
        this.onLogin = onLogin;
        this.onStudentDemo = onStudentDemo;
        this.onAdminDemo = onAdminDemo;

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

        StackPane iconCircle = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 22, Color.web("#0284C7")));
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
        desc.setStyle("-fx-font-size: 13px; -fx-line-spacing: 4px; -fx-opacity: 0.85;");

        // Feature Bullets
        VBox features = new VBox(12);
        features.getChildren().addAll(
                createFeatureItem(ThemeManager.ICON_LEAF, "100% Zero-Emission Commute", "Zero fuel, pure electric & pedal fleet"),
                createFeatureItem(ThemeManager.ICON_PIN, "5 Smart Campus Hubs", "Automated docks at Library, SWC, and Gates"),
                createFeatureItem(ThemeManager.ICON_SHIELD, "Student Subsidy Program", "25% instant discount on verified KUET IDs")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("Khulna University of Engineering & Technology");
        footer.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6; -fx-font-weight: 600;");

        box.getChildren().addAll(logoRow, desc, features, spacer, footer);
        return box;
    }

    private HBox createFeatureItem(String svg, String headline, String detail) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBox = new StackPane(ThemeManager.createIcon(svg, 14, Color.web("#0284C7")));
        iconBox.setPrefSize(32, 32);
        iconBox.getStyleClass().add("action-icon-btn");

        VBox textCol = new VBox(2);
        Label h = new Label(headline);
        h.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700;");

        Label d = new Label(detail);
        d.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

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
        welcome.setStyle("-fx-font-size: 24px; -fx-font-weight: 800;");

        Label sub = new Label("Sign in to unlock bikes, track active rides, and manage rentals.");
        sub.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.75;");
        titleCol.getChildren().addAll(welcome, sub);

        // Email / Student Roll Input
        Label emailLbl = new Label("UNIVERSITY EMAIL / STUDENT ROLL");
        emailLbl.getStyleClass().add("metric-label");

        HBox emailBox = new HBox(10);
        emailBox.setAlignment(Pos.CENTER_LEFT);
        emailBox.getStyleClass().add("input-pill-box");

        SVGPath mailIcon = ThemeManager.createIcon(ThemeManager.ICON_MAIL, 15, Color.web("#94A3B8"));
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

        SVGPath lockIcon = ThemeManager.createIcon(ThemeManager.ICON_LOCK, 15, Color.web("#94A3B8"));

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
            eyeBtn.setGraphic(ThemeManager.createIcon(show ? ThemeManager.ICON_EYE_OFF : ThemeManager.ICON_EYE, 14, Color.web("#64748B")));
        });
        eyeBtn.setStyle("-fx-padding: 4px 8px; -fx-background-radius: 12px;");

        passBox.getChildren().addAll(lockIcon, fieldStack, eyeBtn);

        // Error message label
        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #DC2626;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        // Submit Button
        Button signInBtn = new Button("Sign In to CampusCycle");
        signInBtn.getStyleClass().add("primary-button");
        signInBtn.setMaxWidth(Double.MAX_VALUE);
        signInBtn.setOnAction(e -> {
            String email = emailField.getText();
            String pass = maskedField.getText();
            if (email == null || email.trim().isEmpty()) {
                errorLbl.setText("Please enter your university email or student roll.");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }
            if (pass == null || pass.trim().isEmpty()) {
                errorLbl.setText("Please enter your password.");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }

            errorLbl.setVisible(false);
            errorLbl.setManaged(false);

            String trimmed = email.trim();
            if (trimmed.toLowerCase().contains("admin") || trimmed.toLowerCase().contains("office")) {
                CampusUser adminUser = new CampusUser("56d6f9dc-0ca7-4b49-9f9e-3c48a1b2089a", "KUET Cycle Office", trimmed, Role.ADMIN);
                LocalDatabase.getInstance().saveProfile(adminUser);
                onLogin.accept(adminUser);
            } else {
                String name = trimmed.contains("@") ? trimmed.split("@")[0] : "Student " + trimmed;
                String userUuid = UUID.nameUUIDFromBytes(trimmed.getBytes()).toString();
                CampusUser studentUser = new CampusUser(userUuid, name, trimmed, Role.STUDENT);
                LocalDatabase.getInstance().saveProfile(studentUser);
                onLogin.accept(studentUser);
            }
        });

        // Quick Demo Launcher Section
        Label demoHeader = new Label("OR EXPLORE INSTANT DEMO");
        demoHeader.getStyleClass().add("metric-label");
        demoHeader.setAlignment(Pos.CENTER);
        demoHeader.setMaxWidth(Double.MAX_VALUE);
        demoHeader.setStyle("-fx-alignment: center; -fx-padding: 8px 0 0 0;");

        HBox demoRow = new HBox(12);
        demoRow.setAlignment(Pos.CENTER);

        Button studentDemoBtn = new Button("Student Demo");
        studentDemoBtn.getStyleClass().add("secondary-button");
        studentDemoBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(studentDemoBtn, Priority.ALWAYS);
        studentDemoBtn.setOnAction(e -> onStudentDemo.run());

        Button adminDemoBtn = new Button("Admin Demo");
        adminDemoBtn.getStyleClass().add("secondary-button");
        adminDemoBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(adminDemoBtn, Priority.ALWAYS);
        adminDemoBtn.setOnAction(e -> onAdminDemo.run());

        demoRow.getChildren().addAll(studentDemoBtn, adminDemoBtn);

        box.getChildren().addAll(
                topRow,
                titleCol,
                emailLbl, emailBox,
                passLbl, passBox,
                errorLbl,
                signInBtn,
                demoHeader,
                demoRow
        );

        return box;
    }
}
