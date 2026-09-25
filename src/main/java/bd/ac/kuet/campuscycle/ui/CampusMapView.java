package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dedicated CampusMapView implementing the Wheat Field UI reference:
 * Split navigation layout with a station hub directory on the left and
 * an interactive Microsoft Bing Satellite / Road radar on the right.
 */
public class CampusMapView extends VBox {

    public record StationZone(
            String id,
            String name,
            double lat,
            double lng,
            String category, // "CAMPUS" or "KHULNA"
            String description,
            String accentHex,
            int defaultCapacity
    ) {}

    public static final List<StationZone> ALL_ZONES = List.of(
            // 5 Campus Hubs
            new StationZone("hub-1", "KUET Central Library", 22.9009, 89.5016, "CAMPUS", "Main library hub with automated quad-dock", "#2EB5A4", 32),
            new StationZone("hub-2", "Student Welfare Centre", 22.9017, 89.5030, "CAMPUS", "SWC cafeteria & student plaza dock", "#2EB5A4", 28),
            new StationZone("hub-3", "KUET Main Gate", 22.8987, 89.4981, "CAMPUS", "Fulbarigate entrance connector", "#F59E0B", 24),
            new StationZone("hub-4", "Hall Gate", 22.9045, 89.5060, "CAMPUS", "Residential halls & sports ground gateway", "#8B5CF6", 20),
            new StationZone("hub-5", "Academic Building", 22.9015, 89.5010, "CAMPUS", "CSE, EEE & Mechanical complex", "#EC4899", 20),

            // 7 Khulna City Roaming Checkpoints
            new StationZone("zone-1", "Fulbarigate Transit Hub", 22.8950, 89.5040, "KHULNA", "Direct KUET link & market connection", "#35BFAE", 15),
            new StationZone("zone-2", "Daulatpur Terminal", 22.8700, 89.5200, "KHULNA", "Midway Khulna transit corridor", "#3B82F6", 12),
            new StationZone("zone-3", "Khalishpur Commercial", 22.8550, 89.5300, "KHULNA", "Residential & shopping corridor", "#6366F1", 10),
            new StationZone("zone-4", "Boyra Civic Center", 22.8400, 89.5350, "KHULNA", "Civic center & hospital intersection", "#14B8A6", 10),
            new StationZone("zone-5", "Sonadanga Bus Terminal", 22.8180, 89.5530, "KHULNA", "Inter-district bus access terminal", "#F97316", 15),
            new StationZone("zone-6", "Shibbari Circle (City Core)", 22.8150, 89.5580, "KHULNA", "Downtown commercial center", "#EF4444", 15),
            new StationZone("zone-7", "Royal Mor / Rupsha Riverfront", 22.8100, 89.5640, "KHULNA", "Khulna riverfront & cultural district", "#A855F7", 10)
    );

    private final CampusUser user;
    private final List<CycleItem> cycles;
    private final Consumer<String> onNavigate;
    private final Consumer<String> onLocationChanged;

    private BingMapView bingMapView;
    private final VBox cardsContainer = new VBox(10);
    private final TextField searchField = new TextField();
    private String activeCategory = "ALL";

    private final Button btnAll = new Button("All Zones (12)");
    private final Button btnCampus = new Button("Campus Hubs (5)");
    private final Button btnKhulna = new Button("Khulna City (7)");

    public CampusMapView(CampusUser user,
                         List<CycleItem> cycles,
                         Consumer<String> onNavigate,
                         Consumer<String> onLocationChanged) {
        ThemeManager.install(this);
        this.user = user;
        this.cycles = cycles != null ? cycles : new ArrayList<>();
        this.onNavigate = onNavigate;
        this.onLocationChanged = onLocationChanged;

        setSpacing(20);
        setPadding(new Insets(24, 32, 36, 32));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1200);

        HBox header = createHeader();
        HBox splitLayout = createSplitLayout();

        getChildren().addAll(header, splitLayout);
        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("KUET & Khulna City Navigation Radar");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label sub = new Label("Live satellite & street telemetry covering KUET campus quad-docks and Khulna metropolitan free-roaming zones");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button catalogBtn = new Button("Explore Fleet Catalog →");
        catalogBtn.getStyleClass().add("primary-button");
        catalogBtn.setOnAction(e -> onNavigate.accept("Fleet Catalog"));

        row.getChildren().addAll(titleCol, spacer, catalogBtn);
        return row;
    }

    private HBox createSplitLayout() {
        HBox split = new HBox(18);
        split.setAlignment(Pos.TOP_LEFT);

        // Left Panel (Wheat Field Directory)
        VBox leftPanel = createDirectoryPanel();
        leftPanel.setPrefWidth(380);
        leftPanel.setMinWidth(360);
        leftPanel.setMaxWidth(400);

        // Right Panel (Bing Map)
        VBox rightPanel = new VBox(10);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        bingMapView = new BingMapView(
                cycles,
                hubName -> onNavigate.accept("Fleet Catalog"),
                desc -> {
                    if (onLocationChanged != null) onLocationChanged.accept(desc);
                }
        );
        bingMapView.setPrefHeight(620);
        HBox.setHgrow(bingMapView, Priority.ALWAYS);

        rightPanel.getChildren().add(bingMapView);
        split.getChildren().addAll(leftPanel, rightPanel);

        return split;
    }

    private VBox createDirectoryPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("bento-card");
        panel.setPadding(new Insets(18));

        Label dirTitle = new Label("Stations & Roaming Hubs");
        dirTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        // Search Input
        searchField.setPromptText("Filter by station name...");
        searchField.getStyleClass().add("modern-input");
        searchField.textProperty().addListener((obs, oldV, newV) -> renderStationCards());

        // Category Pills
        HBox pillRow = new HBox(6);
        setupPill(btnAll, "ALL");
        setupPill(btnCampus, "CAMPUS");
        setupPill(btnKhulna, "KHULNA");
        btnAll.getStyleClass().add("filter-chip-active");
        pillRow.getChildren().addAll(btnAll, btnCampus, btnKhulna);

        // Scrollable Card List
        ScrollPane scroll = new ScrollPane(cardsContainer);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(460);
        scroll.getStyleClass().add("scroll-pane");

        panel.getChildren().addAll(dirTitle, searchField, pillRow, scroll);
        renderStationCards();

        return panel;
    }

    private void setupPill(Button btn, String category) {
        btn.getStyleClass().add("filter-chip");
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        btn.setOnAction(e -> {
            btnAll.getStyleClass().remove("filter-chip-active");
            btnCampus.getStyleClass().remove("filter-chip-active");
            btnKhulna.getStyleClass().remove("filter-chip-active");
            btn.getStyleClass().add("filter-chip-active");
            activeCategory = category;
            renderStationCards();
        });
    }

    private void renderStationCards() {
        cardsContainer.getChildren().clear();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();

        List<StationZone> filtered = ALL_ZONES.stream()
                .filter(z -> {
                    if (!"ALL".equals(activeCategory) && !z.category().equals(activeCategory)) return false;
                    if (query.isEmpty()) return true;
                    return z.name().toLowerCase().contains(query) || z.description().toLowerCase().contains(query);
                })
                .toList();

        for (StationZone zone : filtered) {
            cardsContainer.getChildren().add(createStationCard(zone));
        }
    }

    private VBox createStationCard(StationZone zone) {
        VBox card = new VBox(6);
        card.getStyleClass().add("sub-panel");
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle(card.getStyle() + String.format("; -fx-border-color: transparent transparent transparent %s; -fx-border-width: 0 0 0 4px; -fx-cursor: hand;", zone.accentHex()));

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(zone.name());
        name.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 750;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        // Count live cycles at this station
        long liveCount = cycles.stream()
                .filter(c -> c.pickupPoint().equalsIgnoreCase(zone.name()) && c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .count();

        Label badge = new Label(liveCount > 0 ? liveCount + " Cycles" : (zone.category().equals("CAMPUS") ? "Dock Hub" : "Free Roam"));
        badge.getStyleClass().add(liveCount > 0 ? "badge-available" : "filter-chip");
        badge.setStyle("-fx-font-size: 10px; -fx-padding: 2px 7px;");

        top.getChildren().addAll(name, sp, badge);

        Label desc = new Label(zone.description());
        desc.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        desc.setWrapText(true);

        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Button focusBtn = new Button("Focus Map");
        focusBtn.getStyleClass().add("secondary-button");
        focusBtn.setStyle("-fx-font-size: 10.5px; -fx-padding: 3px 8px;");
        focusBtn.setOnAction(e -> {
            if (bingMapView != null) {
                bingMapView.focusLocation(zone.lat(), zone.lng());
            }
        });

        actionRow.getChildren().add(focusBtn);

        card.getChildren().addAll(top, desc, actionRow);

        card.setOnMouseClicked(e -> {
            if (bingMapView != null) {
                bingMapView.focusLocation(zone.lat(), zone.lng());
            }
        });

        return card;
    }

    public void dispose() {
        if (bingMapView != null) {
            bingMapView.dispose();
            bingMapView = null;
        }
    }
}
