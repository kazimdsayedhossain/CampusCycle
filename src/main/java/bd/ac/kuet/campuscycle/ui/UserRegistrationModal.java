package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.DatabaseConnection;
import bd.ac.kuet.campuscycle.data.SupabaseCampusRepository;
import bd.ac.kuet.campuscycle.domain.AppError;
import bd.ac.kuet.campuscycle.domain.UserRegistration;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Modern student registration modal matching the LoginView & SettingsModal design language.
 * Features:
 * - Frosted glass card with rounded corners and subtle organic corner wave accents
 * - Responsive sizing matching the login card (380px wide)
 * - Full dark and light mode dynamic theme integration via ThemeManager
 * - Clean scrollable form with styled pill input fields and icons
 * - Click-outside to dismiss backdrop
 * - Primary dark emerald pill submission button matching LoginView
 */
public class UserRegistrationModal extends StackPane {

    public UserRegistrationModal(Runnable onClose) {
        ThemeManager.install(this);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(20));

        // Translucent backdrop overlay that closes the modal on click outside
        Runnable updateOverlay = () -> {
            boolean isDark = ThemeManager.isDark();
            setStyle(isDark
                    ? "-fx-background-color: rgba(15, 23, 42, 0.70);"
                    : "-fx-background-color: rgba(15, 23, 42, 0.45);");
        };
        updateOverlay.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateOverlay.run());

        setOnMouseClicked(e -> {
            if (e.getTarget() == this) {
                if (onClose != null) onClose.run();
            }
        });

        StackPane cardStack = createRegistrationCard(onClose);
        getChildren().add(cardStack);

        ThemeManager.applyFadeIn(this);
    }

    private StackPane createRegistrationCard(Runnable onClose) {
        StackPane cardStack = new StackPane();
        cardStack.setPrefWidth(380);
        cardStack.setMaxWidth(400);
        cardStack.setMinWidth(350);
        cardStack.setMaxHeight(Region.USE_PREF_SIZE);
        cardStack.setAlignment(Pos.CENTER);
        StackPane.setAlignment(cardStack, Pos.CENTER);
        cardStack.setOnMouseClicked(javafx.event.Event::consume);

        // Corner organic ribbon accents matching LoginView / SettingsModal
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

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(16, 24, 20, 24));
        contentBox.setAlignment(Pos.CENTER);
        contentBox.setMaxWidth(400);

        List<Runnable> themeUpdaters = new ArrayList<>();

        // Card styling listener
        Runnable applyCardStyle = () -> {
            boolean isDark = ThemeManager.isDark();
            cardStack.setStyle(
                    (isDark
                        ? "-fx-background-color: rgba(15, 23, 42, 0.92); -fx-border-color: rgba(255, 255, 255, 0.20); "
                        : "-fx-background-color: rgba(255, 255, 255, 0.94); -fx-border-color: rgba(255, 255, 255, 0.90); ") +
                    "-fx-background-radius: 28px; " +
                    "-fx-border-radius: 28px; " +
                    "-fx-border-width: 1.5px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.22), 40, 0, 0, 16);"
            );
        };
        applyCardStyle.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> applyCardStyle.run());

        // 1. Header: Shield Badge, Title & Close Button
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_SHIELD, 16, Color.web("#10B981")));
        iconBadge.setPrefSize(34, 34);
        iconBadge.setMinSize(34, 34);
        iconBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.15); -fx-background-radius: 10px;");

        VBox titleCol = new VBox(1);
        Label title = new Label("Student Registration");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");
        Label sub = new Label("Create your campus cycle account");
        sub.setStyle("-fx-font-size: 11px;");
        titleCol.getChildren().addAll(title, sub);
        HBox.setHgrow(titleCol, Priority.ALWAYS);

        Button closeBtn = ThemeManager.createIconButton(ThemeManager.ICON_CLOSE, 12, "action-icon-btn", onClose);

        Runnable updateHeaderColors = () -> {
            boolean isDark = ThemeManager.isDark();
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: " + (isDark ? "#F8FAFC;" : "#0F172A;"));
            sub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#94A3B8;" : "#64748B;"));
        };
        updateHeaderColors.run();
        themeUpdaters.add(updateHeaderColors);

        headerRow.getChildren().addAll(iconBadge, titleCol, closeBtn);

        // Fields
        TextField nameField = new TextField();
        nameField.setPromptText("Full Name (e.g. Arafat Rahman)");
        HBox nameBox = wrapPillField(ThemeManager.ICON_USER, nameField, themeUpdaters);

        TextField rollField = new TextField();
        rollField.setPromptText("7-digit roll (e.g. 1907001)");
        HBox rollBox = wrapPillField(ThemeManager.ICON_CHECK, rollField, themeUpdaters);

        ComboBox<String> deptBox = new ComboBox<>();
        deptBox.getItems().addAll(
                "Computer Science & Engineering (CSE)",
                "Electrical & Electronic Engineering (EEE)",
                "Mechanical Engineering (ME)",
                "Civil Engineering (CE)",
                "Electronics & Communication (ECE)",
                "Industrial Engineering (IEM)",
                "Biomedical Engineering (BME)",
                "Materials Science (MSE)",
                "Chemical Engineering (ChE)",
                "Urban & Regional Planning (URP)",
                "Other Department"
        );
        deptBox.setValue(deptBox.getItems().get(0));
        deptBox.setMaxWidth(Double.MAX_VALUE);
        Runnable updateDeptBoxStyle = () -> {
            boolean isDark = ThemeManager.isDark();
            deptBox.setStyle(
                    "-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.75);" : "rgba(255, 255, 255, 0.72);") +
                    "-fx-background-radius: 999px; " +
                    "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.15);" : "rgba(255, 255, 255, 0.88);") +
                    "-fx-border-radius: 999px; " +
                    "-fx-border-width: 1px; " +
                    "-fx-pref-height: 38px; -fx-max-height: 38px; -fx-font-size: 11.5px; -fx-padding: 0 10px;"
            );
        };
        updateDeptBoxStyle.run();
        themeUpdaters.add(updateDeptBoxStyle);

        TextField emailField = new TextField();
        emailField.setPromptText("Email (e.g. arafat@kuet.ac.bd)");
        HBox emailBox = wrapPillField(ThemeManager.ICON_MAIL, emailField, themeUpdaters);

        TextField phoneField = new TextField();
        phoneField.setPromptText("Phone (e.g. 01712345678)");
        HBox phoneBox = wrapPillField(ThemeManager.ICON_CARD, phoneField, themeUpdaters);

        PasswordField passField = new PasswordField();
        passField.setPromptText("Password (min 6 chars)");
        HBox passBox = wrapPillField(ThemeManager.ICON_LOCK, passField, themeUpdaters);

        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Confirm Password");
        HBox confirmPassBox = wrapPillField(ThemeManager.ICON_LOCK, confirmPassField, themeUpdaters);

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 11.5px; -fx-font-weight: 700;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        // Labels
        Label lblName = createFieldLabel("FULL NAME", themeUpdaters);
        Label lblRoll = createFieldLabel("STUDENT ROLL NUMBER", themeUpdaters);
        Label lblDept = createFieldLabel("ACADEMIC DEPARTMENT", themeUpdaters);
        Label lblEmail = createFieldLabel("UNIVERSITY EMAIL", themeUpdaters);
        Label lblPhone = createFieldLabel("PHONE NUMBER", themeUpdaters);
        Label lblPass = createFieldLabel("PASSWORD", themeUpdaters);
        Label lblConfirm = createFieldLabel("CONFIRM PASSWORD", themeUpdaters);

        VBox form = new VBox(6);
        form.setPadding(new Insets(2, 2, 6, 2));
        form.getChildren().addAll(
                lblName, nameBox,
                lblRoll, rollBox,
                lblDept, deptBox,
                lblEmail, emailBox,
                lblPhone, phoneBox,
                lblPass, passBox,
                lblConfirm, confirmPassBox
        );

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(310);
        scroll.setMaxHeight(330);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        // Submit Button
        Button submitBtn = new Button("Submit for Verification  →");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        Runnable updateBtnStyle = () -> {
            submitBtn.setStyle(
                    "-fx-background-color: #064E3B; " +
                    "-fx-background-radius: 999px; " +
                    "-fx-text-fill: #FFFFFF; " +
                    "-fx-font-size: 13.5px; " +
                    "-fx-font-weight: 700; " +
                    "-fx-padding: 10px 0; " +
                    "-fx-cursor: hand;"
            );
        };
        updateBtnStyle.run();
        submitBtn.setOnMouseEntered(e -> submitBtn.setStyle(
                "-fx-background-color: #047857; -fx-background-radius: 999px; -fx-text-fill: #FFFFFF; -fx-font-size: 13.5px; -fx-font-weight: 700; -fx-padding: 10px 0; -fx-cursor: hand;"
        ));
        submitBtn.setOnMouseExited(e -> updateBtnStyle.run());

        submitBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String roll = rollField.getText().trim();
            String dept = deptBox.getValue();
            String email = emailField.getText().trim().toLowerCase();
            String phone = phoneField.getText().trim();
            String pass = passField.getText();
            String confirm = confirmPassField.getText();

            if (name.isEmpty()) {
                showError(errorLbl, "Please enter your full name.");
                return;
            }
            if (!roll.matches("^\\d{7}$")) {
                showError(errorLbl, "Student roll must be a 7-digit number (e.g. 1907001).");
                return;
            }
            if (!email.contains("@") || email.length() < 5) {
                showError(errorLbl, "Enter a valid email address (e.g. student@kuet.ac.bd).");
                return;
            }
            if (phone.isEmpty()) {
                showError(errorLbl, "Please enter your contact phone number.");
                return;
            }
            if (pass.length() < 6) {
                showError(errorLbl, "Password must be at least 6 characters.");
                return;
            }
            if (!pass.equals(confirm)) {
                showError(errorLbl, "Passwords do not match. Please re-enter.");
                return;
            }

            errorLbl.setVisible(false);
            errorLbl.setManaged(false);
            submitBtn.setDisable(true);
            submitBtn.setText("Submitting Application...");

            AppExecutor.asyncThenFx(
                    () -> {
                        if (!DatabaseConnection.isAvailable()) {
                            throw new AppError("OFFLINE", "Database connection required for registration.");
                        }
                        CampusRepository repo = new SupabaseCampusRepository();
                        // The registration id is the Supabase auth user id: profiles.id
                        // references auth.users(id), so approval can only succeed when a
                        // real auth user exists. The repo provisions it.
                        UserRegistration reg = new UserRegistration(
                                "", name, roll, dept, email, phone,
                                bd.ac.kuet.campuscycle.domain.PasswordUtils.hash(pass),
                                "PENDING_APPROVAL", ZonedDateTime.now()
                        );
                        repo.submitUserRegistration(reg, pass);
                        return true;                    },
                    ok -> {
                        contentBox.getChildren().clear();
                        contentBox.getChildren().addAll(createSuccessView(name, roll, onClose));
                    },
                    err -> {
                        submitBtn.setDisable(false);
                        submitBtn.setText("Submit for Verification  →");
                        showError(errorLbl, "Registration failed: " + err.getMessage());
                    }
            );
        });

        // Theme change listener dispatch
        ThemeManager.themeProperty().addListener((obs, o, n) -> {
            for (Runnable r : themeUpdaters) {
                r.run();
            }
        });

        contentBox.getChildren().addAll(headerRow, errorLbl, scroll, submitBtn);
        cardStack.getChildren().addAll(topLeftWave, bottomRightWave, contentBox);
        return cardStack;
    }

    private static Label createFieldLabel(String text, List<Runnable> themeUpdaters) {
        Label l = new Label(text);
        Runnable updater = () -> {
            boolean isDark = ThemeManager.isDark();
            l.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 700; -fx-text-fill: " +
                    (isDark ? "#94A3B8;" : "#475569;") + " -fx-padding: 3px 0 0 4px;");
        };
        updater.run();
        themeUpdaters.add(updater);
        return l;
    }

    private static HBox wrapPillField(String iconSvg, TextField field, List<Runnable> themeUpdaters) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        SVGPath icon = ThemeManager.createIcon(iconSvg, 13, Color.web("#94A3B8"));
        HBox.setHgrow(field, Priority.ALWAYS);

        Runnable updater = () -> {
            boolean isDark = ThemeManager.isDark();
            box.setStyle(
                    "-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.75);" : "rgba(255, 255, 255, 0.72);") +
                    "-fx-background-radius: 999px; " +
                    "-fx-border-color: " + (isDark ? "rgba(255, 255, 255, 0.15);" : "rgba(255, 255, 255, 0.88);") +
                    "-fx-border-radius: 999px; " +
                    "-fx-border-width: 1px; " +
                    "-fx-padding: 0 12px; " +
                    "-fx-pref-height: 38px; -fx-max-height: 38px;"
            );
            field.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-text-fill: " + (isDark ? "#F8FAFC;" : "#0F172A;") +
                    "-fx-prompt-text-fill: " + (isDark ? "#64748B;" : "#94A3B8;") +
                    "-fx-font-size: 12px; -fx-font-weight: 600;"
            );
        };
        updater.run();
        themeUpdaters.add(updater);

        box.getChildren().addAll(icon, field);
        return box;
    }

    private static void showError(Label errorLbl, String message) {
        errorLbl.setText(message);
        errorLbl.setVisible(true);
        errorLbl.setManaged(true);
    }

    private static VBox createSuccessView(String name, String roll, Runnable onClose) {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(16, 8, 16, 8));

        StackPane checkBadge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_CHECK, 26, Color.web("#10B981")));
        checkBadge.setPrefSize(52, 52);
        checkBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.15); -fx-background-radius: 999px; -fx-border-color: rgba(16, 185, 129, 0.35); -fx-border-radius: 999px; -fx-border-width: 2px;");

        Label title = new Label("Application Submitted! 🎉");
        boolean isDark = ThemeManager.isDark();
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800; -fx-text-fill: " + (isDark ? "#F8FAFC;" : "#0F172A;"));

        Label desc = new Label(
                "Thank you, " + name + " (Roll " + roll + ")!\n\n" +
                "Your registration has been submitted to the KUET Campus Cycle Office.\n\n" +
                "Once the office verifies your student credentials, you can sign in directly with your roll number or university email."
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: " + (isDark ? "#94A3B8;" : "#475569;") + " -fx-text-alignment: center; -fx-line-spacing: 2px;");
        desc.setMaxWidth(340);

        Button doneBtn = new Button("Done & Return to Sign In");
        doneBtn.setMaxWidth(Double.MAX_VALUE);
        doneBtn.setStyle(
                "-fx-background-color: #064E3B; " +
                "-fx-background-radius: 999px; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: 700; " +
                "-fx-padding: 10px 0; " +
                "-fx-cursor: hand;"
        );
        doneBtn.setOnAction(e -> {
            if (onClose != null) onClose.run();
        });

        box.getChildren().addAll(checkBadge, title, desc, doneBtn);
        return box;
    }
}
