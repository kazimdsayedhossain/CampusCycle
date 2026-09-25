package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Role;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Top navigation bar inspired by Forselle & Fintory.
 * Floating center capsule with capsule navigation pills, brand emblem,
 * location chip, instant theme toggle, and user profile pill.
 * Eliminates all sidebar components.
 */
public class AppHeader extends HBox {

    private final CampusUser user;
    private final Consumer<String> onNavigate;
    private final Runnable onOpenSettings;
    private final Runnable onToggleRole;
    private final Runnable onSelectLocation;

    private final HBox navCapsule = new HBox(4);
    private final List<Button> navButtons = new ArrayList<>();
    private String activePage = "Dashboard";
    private final Button locationBtn = new Button("Central Field");
    private final Button themeToggleBtn = new Button();
    private final Circle activeRideDot = new Circle(4, Color.web("#10B981"));

    public AppHeader(CampusUser user,
                     boolean hasActiveRide,
                     Consumer<String> onNavigate,
                     Runnable onOpenSettings,
                     Runnable onToggleRole,
                     Runnable onSelectLocation) {
        this.user = user;
        this.onNavigate = onNavigate;
        this.onOpenSettings = onOpenSettings;
        this.onToggleRole = onToggleRole;
        this.onSelectLocation = onSelectLocation;

        getStyleClass().add("app-header");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 20, 10, 20));
        setSpacing(16);

        // 1. Brand Island
        HBox brand = createBrandIsland();

        // 2. Navigation Capsule (Center)
        setupNavCapsule(hasActiveRide);

        Region leftSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);

        Region rightSpacer = new Region();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        // 3. Right Control Cluster
        HBox controls = createRightControls();

        getChildren().addAll(brand, leftSpacer, navCapsule, rightSpacer, controls);

        updateThemeIcon();
        ThemeManager.themeProperty().addListener((obs, o, n) -> updateThemeIcon());
    }

    private HBox createBrandIsland() {
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);

        StackPane iconStage = new StackPane();
        iconStage.setPrefSize(38, 38);
        iconStage.getStyleClass().add("action-icon-btn");
        iconStage.getChildren().add(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 20, Color.web("#0284C7")));

        Label title = new Label("CampusCycle");
        title.getStyleClass().add("brand-title");

        Label sub = new Label("KUET SMART MOBILITY");
        sub.getStyleClass().add("brand-sub");

        VBox textBox = new VBox(1, title, sub);
        brand.getChildren().addAll(iconStage, textBox);
        return brand;
    }

    private void setupNavCapsule(boolean hasActiveRide) {
        navCapsule.getStyleClass().add("nav-capsule");
        navCapsule.setAlignment(Pos.CENTER);

        addNavPill("Dashboard", null);
        addNavPill("Fleet Catalog", null);
        addNavPill("Campus Map", null);

        HBox activeJourneyGraphic = new HBox(6);
        activeJourneyGraphic.setAlignment(Pos.CENTER);
        Label ajLabel = new Label("Active Journey");
        if (hasActiveRide) {
            activeJourneyGraphic.getChildren().addAll(activeRideDot, ajLabel);
            addNavPill("Active Journey", activeJourneyGraphic);
        } else {
            addNavPill("Active Journey", null);
        }

        addNavPill("Passbook", null);
        addNavPill("Support", null);

        if (user.role() == Role.ADMIN) {
            addNavPill("Admin Operations", null);
        }
    }

    private void addNavPill(String pageName, javafx.scene.Node customGraphic) {
        Button btn = new Button(pageName);
        if (customGraphic != null) {
            btn.setText("");
            btn.setGraphic(customGraphic);
        }
        btn.getStyleClass().add("nav-pill");
        if (pageName.equals(activePage)) {
            btn.getStyleClass().add("nav-pill-active");
        }
        btn.setOnAction(e -> {
            setActivePage(pageName);
            onNavigate.accept(pageName);
        });
        ThemeManager.applySpringHover(btn);
        navButtons.add(btn);
        navCapsule.getChildren().add(btn);
    }

    public void setActivePage(String pageName) {
        this.activePage = pageName;
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-pill-active");
            String text = btn.getText();
            if (text.isEmpty() && btn.getGraphic() instanceof HBox hbox) {
                for (javafx.scene.Node child : hbox.getChildren()) {
                    if (child instanceof Label l && l.getText().equals(pageName)) {
                        btn.getStyleClass().add("nav-pill-active");
                        break;
                    }
                }
            } else if (text.equals(pageName)) {
                btn.getStyleClass().add("nav-pill-active");
            }
        }
    }

    private HBox createRightControls() {
        HBox right = new HBox(10);
        right.setAlignment(Pos.CENTER_RIGHT);

        // Location Chip
        locationBtn.getStyleClass().add("location-pill");
        locationBtn.setOnAction(e -> {
            if (onSelectLocation != null) onSelectLocation.run();
        });
        ThemeManager.applySpringHover(locationBtn);

        // Theme Switcher Button
        themeToggleBtn.getStyleClass().add("action-icon-btn");
        themeToggleBtn.setOnAction(e -> ThemeManager.toggleTheme());
        ThemeManager.applySpringHover(themeToggleBtn);

        // Settings Button
        Button settingsBtn = ThemeManager.createIconButton(ThemeManager.ICON_SETTINGS, 16, "action-icon-btn", onOpenSettings);

        // User Profile Chip (with 1-click test role switcher)
        HBox userChip = new HBox(8);
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.getStyleClass().add("user-chip");

        StackPane userAvatar = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_USER, 14, Color.web("#0284C7")));
        userAvatar.setPrefSize(24, 24);

        VBox userMeta = new VBox(0);
        Label userName = new Label(user.displayName());
        userName.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 750;");

        Label userRole = new Label(user.role() == Role.ADMIN ? "KUET Admin" : "Student ID Verified");
        userRole.setStyle("-fx-font-size: 9.5px; -fx-opacity: 0.7;");
        userMeta.getChildren().addAll(userName, userRole);

        userChip.getChildren().addAll(userAvatar, userMeta);
        userChip.setOnMouseClicked(null);
        ThemeManager.applySpringHover(userChip);

        right.getChildren().addAll(locationBtn, themeToggleBtn, settingsBtn, userChip);
        return right;
    }

    private void updateThemeIcon() {
        boolean dark = ThemeManager.isDark();
        themeToggleBtn.setGraphic(ThemeManager.createIcon(
                dark ? ThemeManager.ICON_SUN : ThemeManager.ICON_MOON,
                15,
                Color.web(dark ? "#F59E0B" : "#0284C7")
        ));
    }

    public void setLocationDisplay(String locationName) {
        locationBtn.setText(locationName);
    }

    public void setHasActiveRide(boolean hasActiveRide) {
        navCapsule.getChildren().clear();
        navButtons.clear();
        setupNavCapsule(hasActiveRide);
        setActivePage(activePage);
    }
}
