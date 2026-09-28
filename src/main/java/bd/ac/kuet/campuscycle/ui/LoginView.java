package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.SessionStore;
import bd.ac.kuet.campuscycle.data.SupabaseAuthService;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.function.Consumer;

/**
 * Modern translucent frosted-glass login screen matching the KUET CampusCycle concept design.
 * Features:
 * - Fullscreen KUET campus background
 * - Frosted glass card with large corner radius and soft decorative organic corner ribbons
 * - Centered brand header: mint rounded icon with green cycle, CampusCycle title, K U E T subtitle & accent line
 * - Capsule/pill inputs for University Email/Student ID and Password with leading icons and interactive eye toggle
 * - Remember checkbox and "Forgot?" link
 * - Dark forest green "Sign In ->" primary pill button
 * - Clean "or" divider
 * - Frosted outline "Register" pill button launching the verified registration modal
 * - Dynamic light/dark theme toggle
 */
public class LoginView extends StackPane {

    private final Consumer<CampusUser> onLogin;

    public LoginView(Consumer<CampusUser> onLogin) {
        ThemeManager.install(this);
        this.onLogin = onLogin;

        setAlignment(Pos.CENTER);
        setPadding(Insets.EMPTY);
        setStyle("-fx-background-color: transparent;");

        // Background image with cover
        String bgUrl = null;
        for (String candidate : new String[]{"assets/login-kuet-campus.jpg", "assets/backgrounds/login-kuet-campus.jpg", "assets/backgrounds/bg-promenade-racks.jpg"}) {
            java.net.URL url = BikeArt.class.getResource("/bd/ac/kuet/campuscycle/" + candidate);
            if (url != null) {
                bgUrl = url.toExternalForm();
                break;
            }
        }

        if (bgUrl != null) {
            Region bgRegion = new Region();
            bgRegion.prefWidthProperty().bind(widthProperty());
            bgRegion.prefHeightProperty().bind(heightProperty());
            bgRegion.setStyle(
                "-fx-background-image: url('" + bgUrl + "');" +
                "-fx-background-size: cover;" +
                "-fx-background-position: center center;" +
                "-fx-background-repeat: no-repeat;"
            );

            Region overlay = new Region();
            Runnable updateOverlay = () -> {
                boolean isDark = ThemeManager.getTheme() == ThemeManager.Theme.DARK;
                overlay.setStyle(isDark
                        ? "-fx-background-color: rgba(15, 23, 42, 0.45);"
                        : "-fx-background-color: rgba(255, 255, 255, 0.15);");
            };
            updateOverlay.run();
            ThemeManager.themeProperty().addListener((obs, o, n) -> updateOverlay.run());

            overlay.prefWidthProperty().bind(widthProperty());
            overlay.prefHeightProperty().bind(heightProperty());

            getChildren().addAll(bgRegion, overlay);
        }

        StackPane cardWrapper = createLoginCard();
        getChildren().add(cardWrapper);

        ThemeManager.applyFadeIn(cardWrapper);
    }

    private StackPane createLoginCard() {
        StackPane cardStack = new StackPane();
        cardStack.setPrefWidth(350);
        cardStack.setMaxWidth(360);
        cardStack.setMinWidth(330);
        cardStack.setMaxHeight(Region.USE_PREF_SIZE);
        cardStack.setAlignment(Pos.CENTER);
        StackPane.setAlignment(cardStack, Pos.CENTER);

        // Corner organic ribbon accents matching mockup
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

        VBox contentBox = new VBox(8);
        contentBox.setPadding(new Insets(14, 26, 18, 26));
        contentBox.setAlignment(Pos.CENTER);
        contentBox.setMaxWidth(360);

        Runnable applyCardStyle = () -> {
            boolean isDark = ThemeManager.getTheme() == ThemeManager.Theme.DARK;
            cardStack.setStyle(
                    (isDark
                        ? "-fx-background-color: rgba(15, 23, 42, 0.65); -fx-border-color: rgba(255, 255, 255, 0.22); "
                        : "-fx-background-color: rgba(255, 255, 255, 0.52); -fx-border-color: rgba(255, 255, 255, 0.80); ") +
                    "-fx-background-radius: 28px; " +
                    "-fx-border-radius: 28px; " +
                    "-fx-border-width: 1.5px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.16), 40, 0, 0, 16);"
            );
        };
        applyCardStyle.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> applyCardStyle.run());

        // 1. Top row with Moon/Sun Toggle
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);
        topRow.setMaxWidth(Double.MAX_VALUE);

        Button themeBtn = new Button();
        themeBtn.setAccessibleText("Toggle dark mode");
        themeBtn.setTooltip(new Tooltip("Toggle dark mode"));
        Runnable updateThemeIcon = () -> {
            boolean isDark = ThemeManager.isDark();
            themeBtn.setGraphic(ThemeManager.createIcon(
                    isDark ? ThemeManager.ICON_SUN : ThemeManager.ICON_MOON,
                    13,
                    isDark ? Color.web("#F59E0B") : Color.web("#334155")
            ));
            themeBtn.setStyle(
                    (isDark ? "-fx-background-color: rgba(30, 41, 59, 0.85); " : "-fx-background-color: rgba(255, 255, 255, 0.65); ") +
                    "-fx-background-radius: 999px; -fx-padding: 5px 7px; -fx-cursor: hand;"
            );
        };
        updateThemeIcon.run();
        themeBtn.setOnAction(e -> {
            ThemeManager.toggleTheme();
            updateThemeIcon.run();
        });
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateThemeIcon.run());
        topRow.getChildren().add(themeBtn);

        // 2. Centered Branding Header: CampusCycle badge + wordmark + accent line
        VBox branding = new VBox(4);
        branding.setAlignment(Pos.CENTER);

        Canvas bikeLogo = CampusLogo.logo(62);

        Label titleLbl = new Label("CampusCycle");
        Runnable updateBrandingText = () -> {
            boolean isDark = ThemeManager.isDark();
            titleLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: " + (isDark ? "#F8FAFC;" : "#0F172A;"));
        };
        updateBrandingText.run();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateBrandingText.run());

        Region accentLine = new Region();
        accentLine.setPrefSize(26, 2.5);
        accentLine.setMaxSize(26, 2.5);
        accentLine.setStyle("-fx-background-color: #10B981; -fx-background-radius: 2px;");

        branding.getChildren().addAll(bikeLogo, titleLbl, accentLine);

        // 3. Email / Student ID Pill Input Box
        HBox emailBox = new HBox(10);
        emailBox.setAlignment(Pos.CENTER_LEFT);
        emailBox.setPrefHeight(42);
        emailBox.setMaxHeight(42);
        emailBox.setPadding(new Insets(0, 14, 0, 14));
        emailBox.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.72); " +
                "-fx-background-radius: 999px; " +
                "-fx-border-color: rgba(255, 255, 255, 0.88); " +
                "-fx-border-radius: 999px; " +
                "-fx-border-width: 1px;"
        );

        SVGPath mailIcon = ThemeManager.createIcon(ThemeManager.ICON_MAIL, 14, Color.web("#64748B"));
        TextField emailField = new TextField();
        emailField.setPromptText("University email / Student ID");
        emailField.setAccessibleText("University email or student ID");
        emailField.setTooltip(new Tooltip("University email or student ID"));
        emailField.setStyle("-fx-background-color: transparent; -fx-text-fill: #0F172A; -fx-font-size: 12.5px; -fx-font-weight: 600; -fx-prompt-text-fill: #94A3B8;");
        HBox.setHgrow(emailField, Priority.ALWAYS);
        emailBox.getChildren().addAll(mailIcon, emailField);

        // 4. Password Pill Input Box with Eye Toggle
        HBox passBox = new HBox(10);
        passBox.setAlignment(Pos.CENTER_LEFT);
        passBox.setPrefHeight(42);
        passBox.setMaxHeight(42);
        passBox.setPadding(new Insets(0, 14, 0, 14));
        passBox.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.72); " +
                "-fx-background-radius: 999px; " +
                "-fx-border-color: rgba(255, 255, 255, 0.88); " +
                "-fx-border-radius: 999px; " +
                "-fx-border-width: 1px;"
        );

        SVGPath lockIcon = ThemeManager.createIcon(ThemeManager.ICON_LOCK, 14, Color.web("#64748B"));

        PasswordField maskedField = new PasswordField();
        maskedField.setPromptText("Password");
        maskedField.setAccessibleText("Password");
        maskedField.setTooltip(new Tooltip("Password"));
        maskedField.setStyle("-fx-background-color: transparent; -fx-text-fill: #0F172A; -fx-font-size: 12.5px; -fx-font-weight: 600; -fx-prompt-text-fill: #94A3B8;");

        TextField visibleField = new TextField();
        visibleField.setPromptText("Password");
        visibleField.setAccessibleText("Password, visible");
        visibleField.setTooltip(new Tooltip("Password"));
        visibleField.setStyle("-fx-background-color: transparent; -fx-text-fill: #0F172A; -fx-font-size: 12.5px; -fx-font-weight: 600; -fx-prompt-text-fill: #94A3B8;");
        visibleField.setVisible(false);
        visibleField.setManaged(false);

        maskedField.textProperty().bindBidirectional(visibleField.textProperty());

        StackPane fieldStack = new StackPane(maskedField, visibleField);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);

        BooleanProperty isPasswordVisible = new SimpleBooleanProperty(false);
        Button eyeBtn = new Button();
        eyeBtn.setAccessibleText("Show password");
        eyeBtn.setTooltip(new Tooltip("Show password"));
        eyeBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_EYE, 14, Color.web("#64748B")));
        eyeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-cursor: hand;");
        eyeBtn.setOnAction(e -> {
            boolean show = !isPasswordVisible.get();
            isPasswordVisible.set(show);
            maskedField.setVisible(!show);
            maskedField.setManaged(!show);
            visibleField.setVisible(show);
            visibleField.setManaged(show);
            eyeBtn.setGraphic(ThemeManager.createIcon(show ? ThemeManager.ICON_EYE_OFF : ThemeManager.ICON_EYE, 14, Color.web("#64748B")));
            eyeBtn.setAccessibleText(show ? "Hide password" : "Show password");
            eyeBtn.setTooltip(new Tooltip(show ? "Hide password" : "Show password"));
        });

        passBox.getChildren().addAll(lockIcon, fieldStack, eyeBtn);

        // Keyboard navigation
        emailField.setOnAction(e -> maskedField.requestFocus());

        // 5. Options Row: "Forgot?" hyperlink, right-aligned.
        // (The old "remember me" checkbox was inert — never read, in-memory-only
        // session — so it was deleted rather than left looking interactive, P-097.)
        HBox optionsRow = new HBox();
        optionsRow.setAlignment(Pos.CENTER_RIGHT);
        optionsRow.setPadding(new Insets(2, 6, 2, 6));

        Hyperlink forgotLink = new Hyperlink("Forgot?");
        forgotLink.setAccessibleText("Forgot password: contact the Campus Cycle Office");
        forgotLink.setTooltip(new Tooltip("Contact the Campus Cycle Office to reset your password"));
        forgotLink.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #059669; -fx-cursor: hand; -fx-focus-color: #059669;");

        // Error Label
        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 11.5px; -fx-font-weight: 700;");
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        forgotLink.setOnAction(e -> {
            errorLbl.setText("Please contact Campus Cycle Office: cycleoffice@kuet.ac.bd");
            errorLbl.setStyle("-fx-text-fill: #059669; -fx-font-size: 11.5px; -fx-font-weight: 700;");
            errorLbl.setVisible(true);
            errorLbl.setManaged(true);
        });

        optionsRow.getChildren().addAll(forgotLink);

        // 6. Sign In Pill Button (mnemonic Alt+S, P-134)
        Button signInBtn = new Button("_Sign In  →");
        signInBtn.setMnemonicParsing(true);
        signInBtn.setAccessibleText("Sign in");
        signInBtn.setTooltip(new Tooltip("Sign in with your university credentials (Alt+S)"));
        signInBtn.setMaxWidth(Double.MAX_VALUE);
        signInBtn.setPrefHeight(42);
        signInBtn.setStyle(
                "-fx-background-color: #064E3B; " +
                "-fx-background-radius: 999px; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 13.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-cursor: hand;"
        );
        signInBtn.setOnMouseEntered(e -> signInBtn.setStyle(
                "-fx-background-color: #047857; -fx-background-radius: 999px; -fx-text-fill: #FFFFFF; -fx-font-size: 13.5px; -fx-font-weight: 700; -fx-cursor: hand;"
        ));
        signInBtn.setOnMouseExited(e -> signInBtn.setStyle(
                "-fx-background-color: #064E3B; -fx-background-radius: 999px; -fx-text-fill: #FFFFFF; -fx-font-size: 13.5px; -fx-font-weight: 700; -fx-cursor: hand;"
        ));

        // Connect enter key on inputs to trigger sign in
        maskedField.setOnAction(e -> signInBtn.fire());
        visibleField.setOnAction(e -> signInBtn.fire());

        signInBtn.setOnAction(e -> {
            String email = emailField.getText();
            String pass = maskedField.getText();
            try {
                SupabaseAuthService.validate(
                        email == null ? "" : email.trim().toLowerCase(), pass == null ? "" : pass);
            } catch (IllegalArgumentException ex) {
                errorLbl.setText(ex.getMessage());
                errorLbl.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 11.5px; -fx-font-weight: 700;");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }

            errorLbl.setVisible(false);
            errorLbl.setManaged(false);
            signInBtn.setDisable(true);
            signInBtn.setText("Signing in...");

            String trimmed = email.trim().toLowerCase();
            String password = pass;

            SupabaseAuthService.signIn(trimmed, password)
                    .thenAccept(session -> javafx.application.Platform.runLater(() -> {
                        SessionStore.set(session.accessToken(), session.refreshToken(), session.user().id());
                        onLogin.accept(session.user());
                    }))
                    .exceptionally(err -> {
                        javafx.application.Platform.runLater(() -> {
                            signInBtn.setDisable(false);
                            signInBtn.setText("_Sign In  →");
                            String fullMsg = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
                            // Full text to console (never truncated) + wrapped label + tooltip.
                            System.err.println("[CampusCycle] Sign-in failed: " + fullMsg);
                            errorLbl.setText("Sign-in error: " + fullMsg);
                            errorLbl.setWrapText(true);
                            errorLbl.setMaxWidth(420);
                            errorLbl.setTooltip(new Tooltip(fullMsg));
                            errorLbl.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 11.5px; -fx-font-weight: 700;");
                            errorLbl.setVisible(true);
                            errorLbl.setManaged(true);
                        });
                        return null;
                    });
        });

        // 7. Divider: ——— or ———
        HBox divider = new HBox(8);
        divider.setAlignment(Pos.CENTER);
        divider.setPadding(new Insets(1, 0, 1, 0));

        Region lineL = new Region();
        lineL.setStyle("-fx-background-color: rgba(148, 163, 184, 0.45); -fx-min-height: 1px; -fx-max-height: 1px;");
        HBox.setHgrow(lineL, Priority.ALWAYS);

        Label orLbl = new Label("or");
        orLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94A3B8;");

        Region lineR = new Region();
        lineR.setStyle("-fx-background-color: rgba(148, 163, 184, 0.45); -fx-min-height: 1px; -fx-max-height: 1px;");
        HBox.setHgrow(lineR, Priority.ALWAYS);

        divider.getChildren().addAll(lineL, orLbl, lineR);

        // 8. Register Button: Frosted outline pill button with Register (mnemonic Alt+R, P-134)
        Button registerBtn = new Button("_Register");
        registerBtn.setMnemonicParsing(true);
        registerBtn.setAccessibleText("Register a new account");
        registerBtn.setTooltip(new Tooltip("Register a new account (Alt+R)"));
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setPrefHeight(40);
        registerBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_USER, 13, Color.web("#059669")));
        registerBtn.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.35); " +
                "-fx-border-color: #059669; " +
                "-fx-border-width: 1.3px; " +
                "-fx-border-radius: 999px; " +
                "-fx-background-radius: 999px; " +
                "-fx-text-fill: #059669; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: 700; " +
                "-fx-cursor: hand;"
        );
        registerBtn.setOnMouseEntered(e -> registerBtn.setStyle(
                "-fx-background-color: rgba(16, 185, 129, 0.15); -fx-border-color: #047857; -fx-border-width: 1.3px; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-text-fill: #047857; -fx-font-size: 13px; -fx-font-weight: 700; -fx-cursor: hand;"
        ));
        registerBtn.setOnMouseExited(e -> registerBtn.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.35); -fx-border-color: #059669; -fx-border-width: 1.3px; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-text-fill: #059669; -fx-font-size: 13px; -fx-font-weight: 700; -fx-cursor: hand;"
        ));
        registerBtn.setOnAction(e -> openRegistrationModal());

        contentBox.getChildren().addAll(
                topRow,
                branding,
                emailBox,
                passBox,
                optionsRow,
                errorLbl,
                signInBtn,
                divider,
                registerBtn
        );

        cardStack.getChildren().addAll(topLeftWave, bottomRightWave, contentBox);
        return cardStack;
    }

    private void openRegistrationModal() {
        UserRegistrationModal modal = new UserRegistrationModal(() -> {
            getChildren().removeIf(n -> n instanceof UserRegistrationModal);
        });
        getChildren().add(modal);
    }
}
