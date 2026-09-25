package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.TariffService;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Clean, centered Fleet Catalog:
 * 1. Filter chips: All, City Bike, Road Bike, Electric Bike
 * 2. Instant real-time search
 * 3. Register Your Bike action
 * 4. Cycle cards with photo/icon, label, hub dock, type & condition badges,
 *    and hourly rate with 20% student discount applied
 * 5. Center-aligned, responsive layout without left-pinning void
 */
public class FleetCatalogView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<CycleItem> onReserve;
    private final Consumer<String> onLocationChanged;
    private final Runnable onOpenRegister;

    private final TextField searchField = new TextField();
    private final CheckBox availableOnlyCheck = new CheckBox("Available Only");
    private final FlowPane cardsGrid = new FlowPane(20, 20);
    private String selectedTypeFilter = "ALL";
    private final List<Button> chipButtons = new ArrayList<>();
    private final VBox mapContainer = new VBox(12);
    private boolean isMapVisible = false;

    private List<CycleItem> cachedCycles = new ArrayList<>();
    private boolean isLoading = false;
    private BingMapView stationMap;

    public FleetCatalogView(CampusUser user,
                            CampusRepository repo,
                            Consumer<CycleItem> onReserve,
                            Consumer<String> onLocationChanged,
                            Runnable onOpenRegister) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = repo;
        this.onReserve = onReserve;
        this.onLocationChanged = onLocationChanged;
        this.onOpenRegister = onOpenRegister;

        setSpacing(24);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        HBox header = createHeader();
        VBox controls = createControlBar();
        setupMapContainer();

        cardsGrid.setAlignment(Pos.TOP_CENTER);
        cardsGrid.setPrefWrapLength(1060);
        cardsGrid.setMaxWidth(1060);

        VBox gridCenterContainer = new VBox(cardsGrid);
        gridCenterContainer.setAlignment(Pos.TOP_CENTER);
        gridCenterContainer.setMaxWidth(1080);

        getChildren().addAll(header, controls, mapContainer, gridCenterContainer);

        refreshCatalog();
        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(3);
        Label title = new Label("Campus Cycles");
        title.getStyleClass().add("view-title");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: 800; -fx-text-fill: -fx-ink-900;");

        Label sub = new Label("Find and unlock available cycles on campus");
        sub.getStyleClass().add("view-subtitle");
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75; -fx-text-fill: -fx-ink-700;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button registerBtn = new Button("Register Your Bike");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 14, Color.WHITE));
        if (onOpenRegister != null) {
            registerBtn.setOnAction(e -> onOpenRegister.run());
        }

        Button toggleMapBtn = new Button("Station Map");
        toggleMapBtn.getStyleClass().add("secondary-button");
        toggleMapBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_PIN, 13, Color.web("#10B981")));
        toggleMapBtn.setOnAction(e -> {
            isMapVisible = !isMapVisible;
            mapContainer.setVisible(isMapVisible);
            mapContainer.setManaged(isMapVisible);
            if (isMapVisible && stationMap == null) {
                rebuildStationMap();
            }
        });

        row.getChildren().addAll(titleCol, spacer, registerBtn, toggleMapBtn);
        return row;
    }

    private void setupMapContainer() {
        mapContainer.setVisible(false);
        mapContainer.setManaged(false);
    }

    private void rebuildStationMap() {
        if (stationMap != null) stationMap.dispose();
        BingMapView map = new BingMapView(
                cachedCycles,
                hubName -> searchField.setText(hubName),
                onLocationChanged
        );
        stationMap = map;
        map.setPrefHeight(380);
        mapContainer.getChildren().setAll(map);
    }

    public void dispose() {
        if (stationMap != null) {
            stationMap.dispose();
            stationMap = null;
        }
    }

    private VBox createControlBar() {
        VBox bar = new VBox(14);
        bar.setAlignment(Pos.CENTER);
        bar.setMaxWidth(1060);

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("input-pill-box");
        searchBox.setPrefWidth(420);

        SVGPath searchIcon = ThemeManager.createIcon(ThemeManager.ICON_SEARCH, 14, Color.web("#9CA3AF"));
        searchField.setPromptText("Search cycles by model, station, or owner...");
        searchField.getStyleClass().add("bare-input");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Instant in-memory real-time filtering on keystroke
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        searchBox.getChildren().addAll(searchIcon, searchField);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        availableOnlyCheck.setSelected(true);
        availableOnlyCheck.setStyle("-fx-font-size: 12px; -fx-font-weight: 650;");
        availableOnlyCheck.selectedProperty().addListener((obs, o, n) -> applyFilter());

        topRow.getChildren().addAll(searchBox, spacer, availableOnlyCheck);

        // Quick Filter Chips: All, City Bike, Road Bike, Electric Bike
        HBox chips = new HBox(10);
        chips.setAlignment(Pos.CENTER_LEFT);

        addChip(chips, "All", "ALL");
        addChip(chips, "City Bike", "CITY_BIKE");
        addChip(chips, "Road Bike", "ROAD_BIKE");
        addChip(chips, "Electric Bike", "ELECTRIC_BIKE");

        bar.getChildren().addAll(topRow, chips);
        return bar;
    }

    private void addChip(HBox container, String title, String typeKey) {
        Button chip = new Button(title);
        chip.getStyleClass().add("filter-chip");
        if (typeKey.equals("ALL")) chip.getStyleClass().add("filter-chip-active");

        chip.setOnAction(e -> {
            for (Button b : chipButtons) {
                b.getStyleClass().remove("filter-chip-active");
            }
            chip.getStyleClass().add("filter-chip-active");
            selectedTypeFilter = typeKey;
            applyFilter();
        });

        chipButtons.add(chip);
        container.getChildren().add(chip);
    }

    public void refreshCatalog() {
        if (isLoading) return;
        isLoading = true;

        cardsGrid.getChildren().clear();
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(36, 36);
        VBox loadingBox = new VBox(12, pi, new Label("Loading available campus cycles..."));
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefWidth(900);
        loadingBox.setPadding(new Insets(50));
        cardsGrid.getChildren().add(loadingBox);

        AppExecutor.asyncThenFx(
                () -> repo.catalog(user),
                cycles -> {
                    isLoading = false;
                    this.cachedCycles = cycles;
                    if (isMapVisible) {
                        rebuildStationMap();
                    }
                    applyFilter();
                },
                throwable -> {
                    isLoading = false;
                    cardsGrid.getChildren().clear();
                    cardsGrid.getChildren().add(new Label("Catalog is currently unavailable. Please retry."));
                }
        );
    }

    private void applyFilter() {
        cardsGrid.getChildren().clear();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        boolean availableOnly = availableOnlyCheck.isSelected();

        List<CycleItem> items = cachedCycles.stream()
                .filter(c -> {
                    if (availableOnly && c.availabilityStatus() != AvailabilityStatus.AVAILABLE) return false;
                    if (!selectedTypeFilter.equals("ALL") && !c.type().name().equalsIgnoreCase(selectedTypeFilter)) return false;
                    if (query.isEmpty()) return true;
                    return c.label().toLowerCase().contains(query)
                            || c.pickupPoint().toLowerCase().contains(query)
                            || c.ownerName().toLowerCase().contains(query);
                })
                .toList();

        if (items.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(50));
            empty.setPrefWidth(900);

            Label noMsg = new Label("No cycles match your filter criteria.");
            noMsg.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-opacity: 0.75;");

            Label resetHint = new Label("Try searching a different station or select 'All'.");
            resetHint.setStyle("-fx-font-size: 12px; -fx-opacity: 0.55;");

            empty.getChildren().addAll(noMsg, resetHint);
            cardsGrid.getChildren().add(empty);
            return;
        }

        for (CycleItem c : items) {
            cardsGrid.getChildren().add(createCycleCard(c));
        }
    }

    private VBox createCycleCard(CycleItem cycle) {
        VBox card = new VBox(14);
        card.getStyleClass().add("cycle-card");
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setPrefWidth(330);
        card.setMaxWidth(340);

        String accentColor = switch (cycle.type()) {
            case ELECTRIC_BIKE -> "#10B981";
            case ROAD_BIKE -> "#3B82F6";
            case CARGO_BIKE -> "#F59E0B";
            case CITY_BIKE -> "#10B981";
            default -> "#10B981";
        };
        card.setStyle(card.getStyle() + String.format("; -fx-border-color: transparent transparent transparent %s; -fx-border-width: 0 0 0 4px;", accentColor));

        // 1. Top Row: Icon, Title & Owner, Type & Condition Badges
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        StackPane iconStage = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 18, Color.web(accentColor)));
        iconStage.setPrefSize(38, 38);
        iconStage.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(2);
        Label name = new Label(cycle.label());
        name.getStyleClass().add("card-title");
        name.setStyle("-fx-font-size: 14.5px; -fx-font-weight: 750;");

        Label owner = new Label("Owner: " + cycle.ownerName());
        owner.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(name, owner);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Badges: Type + Condition
        VBox badgesCol = new VBox(4);
        badgesCol.setAlignment(Pos.CENTER_RIGHT);

        Label typeBadge = new Label(cycle.type().name().replace("_", " "));
        typeBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 750; -fx-padding: 2px 7px; -fx-background-radius: 999px; -fx-background-color: rgba(16, 185, 129, 0.12); -fx-text-fill: -fx-teal;");

        Label condBadge = new Label(cycle.condition().name());
        condBadge.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 700; -fx-padding: 2px 6px; -fx-background-radius: 999px; -fx-background-color: rgba(59, 130, 246, 0.10); -fx-text-fill: #3B82F6;");

        badgesCol.getChildren().addAll(typeBadge, condBadge);
        top.getChildren().addAll(iconStage, titleCol, spacer, badgesCol);

        // 2. Hub Dock & Description
        VBox metaSection = new VBox(5);
        Label station = new Label("📍 Dock: " + cycle.pickupPoint());
        station.setStyle("-fx-font-size: 12px; -fx-font-weight: 650; -fx-opacity: 0.85;");

        Label desc = new Label(cycle.description().isEmpty() ? "Verified KUET campus commuter bicycle." : cycle.description());
        desc.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.65;");
        desc.setWrapText(true);
        desc.setMaxHeight(36);

        metaSection.getChildren().addAll(station, desc);

        // 3. Pricing Breakdown (Hourly rate with 20% student discount applied)
        // 1 hour base fare from TariffService = 5000 poisha (৳ 50.00)
        // With 20% discount = ৳ 40.00
        int baseHourlyPoisha = TariffService.quotePoisha(60);
        int discountPoisha = (int) Math.round(baseHourlyPoisha * 0.20);
        int discountedPoisha = baseHourlyPoisha - discountPoisha;

        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox priceCol = new VBox(1);
        Label rate = new Label(String.format("৳ %.2f / hr", discountedPoisha / 100.0));
        rate.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: -fx-teal;");

        Label discountNotice = new Label(String.format("৳ %.2f (-20%% student perk)", baseHourlyPoisha / 100.0));
        discountNotice.setStyle("-fx-font-size: 10.5px; -fx-opacity: 0.6;");
        priceCol.getChildren().addAll(rate, discountNotice);

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);

        Button reserveBtn = new Button("Reserve");
        reserveBtn.getStyleClass().add("primary-button");
        reserveBtn.setStyle("-fx-font-size: 12px; -fx-padding: 7px 18px;");

        boolean canBook = cycle.canBeBookedBy(user.id());
        if (!canBook) {
            reserveBtn.setDisable(true);
            reserveBtn.setText(cycle.availabilityStatus() != AvailabilityStatus.AVAILABLE ? "Rented" : "Own Cycle");
        } else {
            reserveBtn.setOnAction(e -> onReserve.accept(cycle));
        }

        bottom.getChildren().addAll(priceCol, sp2, reserveBtn);

        card.getChildren().addAll(top, metaSection, bottom);
        return card;
    }
}
