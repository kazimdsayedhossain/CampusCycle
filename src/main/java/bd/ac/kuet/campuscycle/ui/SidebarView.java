package bd.ac.kuet.campuscycle.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Left sidebar navigation matching the CampusCycle dashboard design:
 * brand header, nav pills (Dashboard, Bikes, Map, My Ride, Central Mosque,
 * Payments, Messages, Settings) and the "Ride Greener" promo card.
 * All icons are hand-authored SVG paths; no emoji.
 */
public class SidebarView extends VBox {

    public static final String PAGE_DASHBOARD = "Dashboard";
    public static final String PAGE_BIKES = "Fleet Catalog";
    public static final String PAGE_MAP = "Campus Map";
    public static final String PAGE_MY_RIDE = "Active Journey";
    public static final String PAGE_PAYMENTS = "Passbook";
    public static final String PAGE_MESSAGES = "Support";
    public static final String PAGE_SETTINGS = "Settings";
    public static final String PAGE_MAINTENANCE = "Maintenance";

    private final List<Button> navButtons = new ArrayList<>();
    private final List<String> navPages = new ArrayList<>();
    private String activePage = PAGE_DASHBOARD;
    private Button myRideButton;
    private javafx.scene.Node myRideBaseGraphic;

    public SidebarView(String activePage, boolean isAdmin, Consumer<String> onNavigate, Runnable onOpenSettings) {
        this(activePage,
                isAdmin ? bd.ac.kuet.campuscycle.domain.Role.ADMIN : bd.ac.kuet.campuscycle.domain.Role.STUDENT,
                onNavigate, onOpenSettings);
    }

    public SidebarView(String activePage, bd.ac.kuet.campuscycle.domain.Role role,
                       Consumer<String> onNavigate, Runnable onOpenSettings) {
        this.activePage = activePage;
        setSpacing(8);
        setPadding(new Insets(20, 14, 20, 14));
        setPrefWidth(208);
        setMinWidth(208);
        setMaxWidth(208);
        getStyleClass().add("sidebar");

        VBox brand = createBrand();
        VBox nav = createNav(onNavigate, onOpenSettings, role);
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox promo = createPromoCard();

        getChildren().addAll(brand, nav, spacer, promo);
    }

    private VBox createBrand() {
        return CampusVectorArt.createBrandHeader(176, 56);
    }

    private VBox createNav(Consumer<String> onNavigate, Runnable onOpenSettings, boolean isAdmin) {
        return createNav(onNavigate, onOpenSettings,
                isAdmin ? bd.ac.kuet.campuscycle.domain.Role.ADMIN : bd.ac.kuet.campuscycle.domain.Role.STUDENT);
    }

    private VBox createNav(Consumer<String> onNavigate, Runnable onOpenSettings,
                           bd.ac.kuet.campuscycle.domain.Role role) {
        VBox nav = new VBox(4);
        addNavItem(nav, "Dashboard", ThemeManager.ICON_HOME, PAGE_DASHBOARD, onNavigate, onOpenSettings);
        addNavItem(nav, "Bikes", ThemeManager.ICON_BIKE, PAGE_BIKES, onNavigate, onOpenSettings);
        addNavItem(nav, "Map", ThemeManager.ICON_MAP, PAGE_MAP, onNavigate, onOpenSettings);
        addNavItem(nav, "My Ride", ThemeManager.ICON_CLOCK, PAGE_MY_RIDE, onNavigate, onOpenSettings);
        addNavItem(nav, "Payments", ThemeManager.ICON_WALLET, PAGE_PAYMENTS, onNavigate, onOpenSettings);
        addNavItem(nav, "Messages", ThemeManager.ICON_CHAT, PAGE_MESSAGES, onNavigate, onOpenSettings);
        addNavItem(nav, "Settings", ThemeManager.ICON_SETTINGS, PAGE_SETTINGS, onNavigate, onOpenSettings);
        if (role == bd.ac.kuet.campuscycle.domain.Role.TECHNICIAN
                || role == bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            addNavItem(nav, "Maintenance", ThemeManager.ICON_SHIELD, PAGE_MAINTENANCE, onNavigate, onOpenSettings);
        }
        if (role == bd.ac.kuet.campuscycle.domain.Role.ADMIN) {
            addNavItem(nav, "Admin", ThemeManager.ICON_SHIELD, "Admin Operations", onNavigate, onOpenSettings);
        }
        refreshActive();
        return nav;
    }

    private void addNavItem(VBox nav, String label, String icon, String page,
                            Consumer<String> onNavigate, Runnable onOpenSettings) {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.getStyleClass().add("sidebar-nav");
        btn.setGraphic(ThemeManager.createIcon(icon, 16, Color.web("#0B2E23")));
        btn.setOnAction(e -> {
            if (PAGE_SETTINGS.equals(page)) {
                if (onOpenSettings != null) onOpenSettings.run();
                return;
            }
            setActivePage(page);
            onNavigate.accept(page);
        });
        navButtons.add(btn);
        navPages.add(page);
        if (PAGE_MY_RIDE.equals(page)) {
            myRideButton = btn;
            myRideBaseGraphic = btn.getGraphic();
        }
        nav.getChildren().add(btn);
    }

    public void setActivePage(String page) {
        this.activePage = page;
        refreshActive();
    }

    /**
     * Shows a live badge dot on the My Ride item while a rental is active.
     * Swaps the Graphic instead of mutating the label so the 208px rail never reflows (P-184).
     */
    public void setHasActiveRide(boolean hasActive) {
        if (myRideButton == null) {
            return;
        }
        if (hasActive) {
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4, Color.web("#22C55E"));
            StackPane badge = new StackPane(myRideBaseGraphic, dot);
            StackPane.setAlignment(dot, Pos.TOP_RIGHT);
            badge.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
            myRideButton.setGraphic(badge);
            myRideButton.setAccessibleText("My Ride, active ride in progress");
        } else {
            myRideButton.setGraphic(myRideBaseGraphic);
            myRideButton.setAccessibleText("My Ride");
        }
    }

    private void refreshActive() {
        for (int i = 0; i < navButtons.size(); i++) {
            Button btn = navButtons.get(i);
            btn.getStyleClass().remove("sidebar-nav-active");
            if (navPages.get(i).equals(activePage)) {
                if (!btn.getStyleClass().contains("sidebar-nav-active")) {
                    btn.getStyleClass().add("sidebar-nav-active");
                }
            }
        }
    }

    private VBox createPromoCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("sidebar-promo");
        card.setPadding(new Insets(12, 10, 12, 10));

        // Canvas width must fit inside the promo card content area:
        //   sidebar width (208) - sidebar padding (14+14) - card padding (10+10) = 160px
        // We use 160px wide × 80px tall so it never overflows horizontally.
        double artW = 160;
        double artH = 80;
        javafx.scene.canvas.Canvas monumentArt = CampusVectorArt.createMonumentCard(artW, artH);

        // Clip the canvas to its own bounds so nothing can bleed outside
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(artW, artH);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        monumentArt.setClip(clip);

        // Center in a full-width container
        StackPane artWrap = new StackPane(monumentArt);
        artWrap.setAlignment(Pos.CENTER);
        artWrap.setMaxWidth(Double.MAX_VALUE);

        // Thin green separator line
        Region sep = new Region();
        sep.setStyle("-fx-background-color: #B6DDCC; -fx-min-height: 1px; -fx-max-height: 1px;");
        sep.setMaxWidth(Double.MAX_VALUE);

        HBox promoContent = new HBox(10);
        promoContent.setAlignment(Pos.CENTER_LEFT);

        // Leaf icon using the proper ICON_LEAF path from ThemeManager
        SVGPath leaf = ThemeManager.createIcon(ThemeManager.ICON_LEAF, 20, Color.web("#10B981"));
        StackPane leafWrap = new StackPane(leaf);
        leafWrap.setAlignment(Pos.CENTER);
        leafWrap.setPrefSize(32, 32);
        leafWrap.setMinSize(32, 32);
        leafWrap.setMaxSize(32, 32);
        leafWrap.setStyle("-fx-background-color: #D1FAE5; -fx-background-radius: 999px;");

        VBox textCol = new VBox(2);
        Label title = new Label("Ride Greener\nCleaner Tomorrow");
        title.getStyleClass().add("sidebar-promo-title");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0F172A; -fx-line-spacing: -1px;");
        title.setWrapText(true);

        Label body = new Label("Take a cycle, reduce emissions, keep KUET green.");
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 9px; -fx-text-fill: #5A6E65; -fx-line-spacing: 1px;");

        textCol.getChildren().addAll(title, body);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        promoContent.getChildren().addAll(leafWrap, textCol);
        card.getChildren().addAll(artWrap, sep, promoContent);
        return card;
    }
}
