package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.service.WalletService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Modern consumer mobility top navigation bar (Uber/Lime/CitiBike standard).
 * Features:
 * 1. Clean brand emblem with bicycle icon and KUET subtitle
 * 2. Navigation capsule pills: [ Dashboard ] [ Bikes ] [ Map ] [ My Ride ] [ More ▾ ]
 *    with live glowing green dot on My Ride when a rental is active
 * 3. Crisp, high-contrast "More" menu items (never dark-on-dark)
 * 4. Right cluster: Prepaid Wallet Pill (💳 ৳ XX.XX with instant + Top Up modal),
 *    Location Chip (📍 Central Library), User Profile Chip (avatar, name, verified badge),
 *    Theme Toggle, and Settings button.
 */
public class AppHeader extends HBox {

    private final CampusUser user;
    private final Consumer<String> onNavigate;
    private final Runnable onOpenSettings;
    private final Runnable onSelectLocation;

    private final HBox navCapsule = new HBox(4);
    private final List<Button> navButtons = new ArrayList<>();
    private final Map<String, Button> pageButtonMap = new HashMap<>();

    private String activePage = "Dashboard";
    private boolean hasActiveRide = false;

    private final Button locationBtn = new Button("📍 Central Library");
    private final MenuButton moreMenu = new MenuButton("More");
    private final Label walletBalanceLabel = new Label("💳 ৳ 150.00");
    private final Button themeToggleBtn = new Button();

    public AppHeader(CampusUser user,
                     boolean hasActiveRide,
                     Consumer<String> onNavigate,
                     Runnable onOpenSettings,
                     Runnable onSelectLocation) {
        ThemeManager.install(this);
        this.user = user;
        this.hasActiveRide = hasActiveRide;
        this.onNavigate = onNavigate;
        this.onOpenSettings = onOpenSettings;
        this.onSelectLocation = onSelectLocation;

        getStyleClass().add("app-header");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 20, 10, 20));
        setSpacing(14);

        // 1. Brand Emblem
        HBox brand = createBrandIsland();

        // 2. Navigation Capsule (Center)
        setupNavCapsule();

        Region leftSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);

        Region rightSpacer = new Region();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        // 3. Right Control Cluster
        HBox controls = createRightControls();

        getChildren().addAll(brand, leftSpacer, navCapsule, rightSpacer, controls);

        // Register wallet updates
        updateWalletDisplay();
        WalletService.getInstance().addListener((uid, bal) -> {
            Platform.runLater(this::updateWalletDisplay);
        });
    }

    private HBox createBrandIsland() {
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setCursor(javafx.scene.Cursor.HAND);
        brand.setOnMouseClicked(e -> {
            setActivePage("Dashboard");
            onNavigate.accept("Dashboard");
        });

        StackPane iconStage = new StackPane();
        iconStage.setPrefSize(36, 36);
        iconStage.getStyleClass().add("action-icon-btn");
        iconStage.setStyle("-fx-background-color: rgba(16, 185, 129, 0.12); -fx-background-radius: 10px;");
        iconStage.getChildren().add(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 18, Color.web("#10B981")));

        Label title = new Label("CampusCycle");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");

        Label sub = new Label("KUET");
        sub.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: -fx-teal; -fx-opacity: 0.9; -fx-letter-spacing: 1px;");

        VBox textBox = new VBox(0, title, sub);
        textBox.setAlignment(Pos.CENTER_LEFT);

        brand.getChildren().addAll(iconStage, textBox);
        ThemeManager.applySpringHover(brand);
        return brand;
    }

    private void setupNavCapsule() {
        navCapsule.getStyleClass().add("nav-capsule");
        navCapsule.setAlignment(Pos.CENTER);
        navCapsule.getChildren().clear();
        navButtons.clear();
        pageButtonMap.clear();

        // 1. Dashboard
        addNavPill("Dashboard", "Dashboard", null, "Dashboard");

        // 2. Bikes
        addNavPill("Fleet Catalog", "Bikes", null, "Fleet Catalog");

        // 3. Map
        addNavPill("Campus Map", "Map", null, "Campus Map");

        // 4. My Ride (with live glowing green dot if active)
        Node myRideGraphic = null;
        if (hasActiveRide) {
            HBox graphic = new HBox(6);
            graphic.setAlignment(Pos.CENTER);

            Circle dot = new Circle(4.5, Color.web("#10B981"));
            dot.setStyle("-fx-effect: dropshadow(gaussian, #10B981, 8, 0.6, 0, 0);");

            Label txt = new Label("My Ride");
            txt.setStyle("-fx-text-fill: inherit; -fx-font-weight: inherit;");
            graphic.getChildren().addAll(dot, txt);
            myRideGraphic = graphic;
        }
        addNavPill("Active Journey", "My Ride", myRideGraphic, "Active Journey");

        // 5. More ▾ Dropdown
        setupMoreDropdown();
    }

    private void addNavPill(String pageName, String buttonLabel, Node customGraphic, String targetPage) {
        Button btn = new Button(buttonLabel);
        if (customGraphic != null) {
            btn.setText("");
            btn.setGraphic(customGraphic);
        }
        btn.getStyleClass().add("nav-pill");
        btn.setOnAction(e -> {
            setActivePage(pageName);
            onNavigate.accept(targetPage);
        });
        ThemeManager.applySpringHover(btn);

        navButtons.add(btn);
        pageButtonMap.put(pageName, btn);
        navCapsule.getChildren().add(btn);
    }

    private void setupMoreDropdown() {
        moreMenu.setText("More ▾");
        moreMenu.getStyleClass().setAll("menu-button", "nav-menu");
        moreMenu.getItems().clear();

        addMenuItem("Passbook", "Passbook");
        addMenuItem("Support", "Support");
        if (user.role() == Role.ADMIN) {
            addMenuItem("Admin Operations", "Admin Operations");
        }

        ThemeManager.applySpringHover(moreMenu);
        navCapsule.getChildren().add(moreMenu);
    }

    private void addMenuItem(String title, String pageTarget) {
        MenuItem item = new MenuItem(title);
        item.setOnAction(e -> {
            setActivePage(pageTarget);
            onNavigate.accept(pageTarget);
        });
        moreMenu.getItems().add(item);
    }

    public void setActivePage(String pageName) {
        this.activePage = pageName;

        String canonical = switch (pageName) {
            case "Bikes" -> "Fleet Catalog";
            case "Map" -> "Campus Map";
            case "My Ride" -> "Active Journey";
            default -> pageName;
        };

        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-pill-active");
        }

        Button activeBtn = pageButtonMap.get(canonical);
        if (activeBtn != null) {
            activeBtn.getStyleClass().add("nav-pill-active");
        }

        boolean isMorePage = List.of("Passbook", "Support", "Admin Operations").contains(pageName);
        moreMenu.getStyleClass().remove("nav-menu-active");
        if (isMorePage) {
            moreMenu.getStyleClass().add("nav-menu-active");
        }
    }

    private HBox createRightControls() {
        HBox right = new HBox(10);
        right.setAlignment(Pos.CENTER_RIGHT);

        // 1. Prepaid Wallet Pill
        HBox walletPill = createWalletPill();

        // 2. Location Chip
        locationBtn.getStyleClass().add("location-pill");
        locationBtn.setTooltip(new Tooltip("Click to open Campus Map"));
        locationBtn.setOnAction(e -> {
            if (onSelectLocation != null) onSelectLocation.run();
        });
        ThemeManager.applySpringHover(locationBtn);

        // 3. User Profile Chip
        HBox userChip = createUserProfileChip();

        // 4. Theme Toggle Button
        setupThemeToggle();

        // 5. Settings Button
        Button settingsBtn = ThemeManager.createIconButton(
                ThemeManager.ICON_SETTINGS,
                16,
                "action-icon-btn",
                onOpenSettings
        );
        settingsBtn.setTooltip(new Tooltip("Preferences & Settings"));

        right.getChildren().addAll(walletPill, locationBtn, userChip, themeToggleBtn, settingsBtn);
        return right;
    }

    private HBox createWalletPill() {
        HBox pill = new HBox(8);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.getStyleClass().add("wallet-pill");

        walletBalanceLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 750; -fx-text-fill: -fx-ink-900;");

        Button topUpBtn = new Button("+ Top Up");
        topUpBtn.getStyleClass().add("wallet-topup-btn");
        topUpBtn.setTooltip(new Tooltip("Instant Recharge via bKash / Nagad / Student ID"));
        topUpBtn.setOnAction(e -> {
            TopUpModal.open(this, user, this::updateWalletDisplay);
        });

        pill.getChildren().addAll(walletBalanceLabel, topUpBtn);
        ThemeManager.applySpringHover(pill);
        return pill;
    }

    private HBox createUserProfileChip() {
        HBox userChip = new HBox(8);
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.getStyleClass().add("user-chip");

        StackPane userAvatar = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_USER, 13, Color.web("#10B981")));
        userAvatar.setPrefSize(26, 26);
        userAvatar.setStyle("-fx-background-color: rgba(16, 185, 129, 0.12); -fx-background-radius: 999px;");

        VBox userMeta = new VBox(0);
        userMeta.setAlignment(Pos.CENTER_LEFT);

        Label userName = new Label(user.displayName());
        userName.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 750;");

        String badgeText = user.role() == Role.ADMIN ? "KUET Admin" : "✓ Verified Student";
        Label userRole = new Label(badgeText);
        userRole.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 700; -fx-text-fill: -fx-teal;");

        userMeta.getChildren().addAll(userName, userRole);
        userChip.getChildren().addAll(userAvatar, userMeta);
        ThemeManager.applySpringHover(userChip);
        return userChip;
    }

    private void setupThemeToggle() {
        themeToggleBtn.getStyleClass().add("action-icon-btn");
        updateThemeToggleIcon();
        themeToggleBtn.setOnAction(e -> {
            ThemeManager.toggleTheme();
            updateThemeToggleIcon();
        });
        ThemeManager.themeProperty().addListener((obs, oldTheme, newTheme) -> {
            updateThemeToggleIcon();
        });
    }

    private void updateThemeToggleIcon() {
        boolean dark = ThemeManager.isDark();
        SVGPath icon = ThemeManager.createIcon(
                dark ? ThemeManager.ICON_SUN : ThemeManager.ICON_MOON,
                15,
                Color.web(dark ? "#F59E0B" : "#64748B")
        );
        themeToggleBtn.setGraphic(icon);
        themeToggleBtn.setTooltip(new Tooltip(dark ? "Switch to Light Mode" : "Switch to Dark Mode"));
    }

    public void updateWalletDisplay() {
        double balance = WalletService.getInstance().getBalance(user);
        walletBalanceLabel.setText(String.format("💳 ৳ %.2f", balance));
    }

    public void setLocationDisplay(String locationName) {
        if (locationName == null || locationName.isBlank()) {
            locationBtn.setText("📍 Central Library");
        } else {
            String clean = locationName.startsWith("📍") ? locationName.substring(1).trim() : locationName.trim();
            locationBtn.setText("📍 " + clean);
        }
    }

    public void setHasActiveRide(boolean hasActiveRide) {
        this.hasActiveRide = hasActiveRide;
        setupNavCapsule();
        setActivePage(activePage);
    }
}
