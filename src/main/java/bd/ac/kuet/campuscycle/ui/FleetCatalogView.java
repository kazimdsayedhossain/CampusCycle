package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.CycleType;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.TariffService;
import bd.ac.kuet.campuscycle.domain.event.CycleStatusChangedEvent;
import bd.ac.kuet.campuscycle.domain.event.RentalReturnedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Clean, centered Fleet Catalog:
 * 1. Filter chips: All, City Bike, Road Bike, Electric Bike
 * 2. Instant real-time search
 * 3. Register Your Bike action
 * 4. Cycle cards with photo/icon, label, hub dock, type & condition badges,
 *    and the TariffService 15-min rate with the student subsidy badge (students only)
 * 5. Center-aligned, responsive layout without left-pinning void
 */
public class FleetCatalogView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<CycleItem> onReserve;
    private final Consumer<String> onLocationChanged;
    private final Runnable onOpenRegister;
    private final Runnable onGoToRide;
    /** Cycle id of the viewer's active rental — pinned visible even when RENTED. */
    private volatile String activeCycleId;

    private final TextField searchField = new TextField();
    private final CheckBox availableOnlyCheck = new CheckBox("Available Only");
    private final FlowPane cardsGrid = new FlowPane(20, 20);
    private String selectedTypeFilter = "ALL";
    private final List<Button> chipButtons = new ArrayList<>();
    private final VBox mapContainer = new VBox(12);
    private boolean isMapVisible = false;

    private List<CycleItem> cachedCycles = new ArrayList<>();
    private boolean isLoading = false;
    private boolean refreshQueued = false;
    private boolean disposed = false;
    private final Set<String> favorites = new HashSet<>();
    private final Label catalogStatus = new Label();
    private Button registerBtn;
    private Button toggleMapBtn;
    private BingMapView stationMap;

    /** Catalog + viewer's active rental cycle, loaded together per refresh. */
    private record CatalogPayload(List<CycleItem> cycles, String activeCycleId) {}

    private final Consumer<RentalReturnedEvent> onRentalReturned = event -> {
        if (!disposed) {
            Platform.runLater(this::refreshCatalog);
        }
    };
    private final Consumer<CycleStatusChangedEvent> onCycleStatusChanged = event -> {
        if (!disposed) {
            Platform.runLater(this::refreshCatalog);
        }
    };

    public FleetCatalogView(CampusUser user,
                            CampusRepository repo,
                            Consumer<CycleItem> onReserve,
                            Consumer<String> onLocationChanged,
                            Runnable onOpenRegister,
                            Runnable onGoToRide) {
        ThemeManager.install(this);
        this.user = user;
        this.repo = repo;
        this.onReserve = onReserve;
        this.onLocationChanged = onLocationChanged;
        this.onOpenRegister = onOpenRegister;
        this.onGoToRide = onGoToRide != null ? onGoToRide : () -> {};

        setSpacing(24);
        setPadding(new Insets(6, 16, 24, 16));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-background-color: transparent;");

        HBox header = createHeader();
        VBox controls = createControlBar();
        setupMapContainer();

        cardsGrid.setAlignment(Pos.TOP_CENTER);
        cardsGrid.setPrefWrapLength(1140);
        cardsGrid.setMaxWidth(Double.MAX_VALUE);

        VBox gridCenterContainer = new VBox(cardsGrid);
        gridCenterContainer.setAlignment(Pos.TOP_CENTER);
        gridCenterContainer.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(header, controls, mapContainer, gridCenterContainer);

        EventBus.getInstance().subscribe(RentalReturnedEvent.class, onRentalReturned);
        EventBus.getInstance().subscribe(CycleStatusChangedEvent.class, onCycleStatusChanged);
        refreshCatalog();
        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        Label sub = new Label("Find and unlock available cycles on campus");
        sub.getStyleClass().add("view-subtitle");
        sub.setStyle("-fx-font-size: 15px; -fx-font-weight: 600; -fx-text-fill: -fx-ink-700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        catalogStatus.setStyle("-fx-font-size: 12px; -fx-opacity: 0.6; -fx-font-weight: 600;");
        catalogStatus.setVisible(false);
        catalogStatus.setManaged(false);

        registerBtn = new Button("Register Your Bike");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 14, Color.WHITE));
        if (onOpenRegister != null) {
            registerBtn.setOnAction(e -> onOpenRegister.run());
        }

        toggleMapBtn = new Button("Station Map");
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

        // Add press animations
        AnimationHelper.addPressAnimation(registerBtn);
        AnimationHelper.addPressAnimation(toggleMapBtn);

        row.getChildren().addAll(sub, spacer, catalogStatus, registerBtn, toggleMapBtn);
        return row;
    }

    private void setupMapContainer() {
        mapContainer.setVisible(false);
        mapContainer.setManaged(false);
    }

    private void rebuildStationMap() {
        if (disposed) return;
        if (stationMap != null) {
            // Cheap refresh: push new cycles into the live WebView (P-127).
            stationMap.pushCycles(cachedCycles);
            return;
        }
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
        disposed = true;
        try {
            EventBus.getInstance().unsubscribe(RentalReturnedEvent.class, onRentalReturned);
        } catch (Exception ignored) {}
        try {
            EventBus.getInstance().unsubscribe(CycleStatusChangedEvent.class, onCycleStatusChanged);
        } catch (Exception ignored) {}
        if (stationMap != null) {
            stationMap.dispose();
            stationMap = null;
        }
    }

    /** Preset the search box (e.g. from sidebar or top-bar search). */
    public void setSearchQuery(String query) {
        searchField.setText(query == null ? "" : query);
        applyFilter();
    }

    private VBox createControlBar() {
        VBox bar = new VBox(14);
        bar.setAlignment(Pos.CENTER);
        bar.setMaxWidth(Double.MAX_VALUE);

        // Full width search input box matching mockup
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("input-pill-box");
        searchBox.setMaxWidth(Double.MAX_VALUE);
        searchBox.setPadding(new Insets(10, 16, 10, 16));
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        SVGPath searchIcon = ThemeManager.createIcon(ThemeManager.ICON_SEARCH, 15, Color.web("#9CA3AF"));
        searchField.setPromptText("Search cycles by model, station, or owner...");
        searchField.getStyleClass().add("bare-input");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Instant in-memory real-time filtering on keystroke
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        searchBox.getChildren().addAll(searchIcon, searchField);

        // Filter chips row with Available Only checkbox on right
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        HBox chips = new HBox(10);
        chips.setAlignment(Pos.CENTER_LEFT);

        addChip(chips, "All", "ALL");
        addChip(chips, "City Bike", "CITY_BIKE");
        addChip(chips, "Road Bike", "ROAD_BIKE");
        addChip(chips, "Electric Bike", "ELECTRIC_BIKE");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        availableOnlyCheck.setSelected(true);
        availableOnlyCheck.selectedProperty().addListener((obs, o, n) -> applyFilter());

        filterRow.getChildren().addAll(chips, spacer, availableOnlyCheck);

        bar.getChildren().addAll(searchBox, filterRow);
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
        if (disposed) return;
        if (isLoading) {
            // Never silently drop a refresh (P-118): queue it and show a subtle state.
            refreshQueued = true;
            catalogStatus.setText("Refreshing…");
            catalogStatus.setVisible(true);
            catalogStatus.setManaged(true);
            return;
        }
        isLoading = true;
        refreshQueued = false;
        catalogStatus.setText("Refreshing…");
        catalogStatus.setVisible(true);
        catalogStatus.setManaged(true);

        cardsGrid.getChildren().clear();
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(36, 36);
        VBox loadingBox = new VBox(12, pi, new Label("Loading available campus cycles..."));
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefWidth(900);
        loadingBox.setPadding(new Insets(50));
        cardsGrid.getChildren().add(loadingBox);

        AppExecutor.asyncThenFx(
                () -> {
                    List<CycleItem> cycles = repo.catalog(user);
                    String mine = null;
                    try {
                        RentalRecord act = repo.activeRental(user);
                        if (act != null) mine = act.cycleId();
                    } catch (Exception ignored) {}
                    return new CatalogPayload(cycles, mine);
                },
                payload -> {
                    if (disposed) return;
                    isLoading = false;
                    catalogStatus.setVisible(false);
                    catalogStatus.setManaged(false);
                    this.cachedCycles = payload.cycles() == null ? new ArrayList<>() : new ArrayList<>(payload.cycles());
                    this.activeCycleId = payload.activeCycleId();
                    if (isMapVisible) {
                        rebuildStationMap();
                    }
                    applyFilter();
                    if (refreshQueued && !disposed) {
                        refreshQueued = false;
                        refreshCatalog();
                    }
                },
                throwable -> {
                    if (disposed) return;
                    isLoading = false;
                    catalogStatus.setVisible(false);
                    catalogStatus.setManaged(false);
                    cardsGrid.getChildren().clear();
                    VBox failure = new VBox(10);
                    failure.setAlignment(Pos.CENTER);
                    failure.setPadding(new Insets(50));
                    failure.setPrefWidth(900);
                    Label msg = new Label("Could not load the fleet catalog. Check your connection and try again.");
                    msg.setWrapText(true);
                    Button retry = new Button("Retry");
                    retry.getStyleClass().add("secondary-button");
                    retry.setOnAction(e -> refreshCatalog());
                    failure.getChildren().addAll(msg, retry);
                    cardsGrid.getChildren().add(failure);
                    if (refreshQueued && !disposed) {
                        refreshQueued = false;
                        refreshCatalog();
                    }
                }
        );
    }

    private void applyFilter() {
        cardsGrid.getChildren().clear();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        boolean availableOnly = availableOnlyCheck.isSelected();

        String mine = activeCycleId;
        List<CycleItem> items = cachedCycles.stream()
                .filter(c -> {
                    // The viewer's own rented bike is never filtered out — it stays
                    // pinned visible for the whole ride (it is rendered first below).
                    if (mine != null && mine.equals(c.id())) return true;
                    if (availableOnly && c.availabilityStatus() != AvailabilityStatus.AVAILABLE) return false;
                    if (!selectedTypeFilter.equals("ALL") && !c.type().name().equalsIgnoreCase(selectedTypeFilter)) return false;
                    if (query.isEmpty()) return true;
                    return c.label().toLowerCase().contains(query)
                            || c.pickupPoint().toLowerCase().contains(query)
                            || c.ownerName().toLowerCase().contains(query);
                })
                .sorted((a, b) -> {
                    // Pinned ride first, everything else keeps catalog order.
                    boolean aMine = mine != null && mine.equals(a.id());
                    boolean bMine = mine != null && mine.equals(b.id());
                    return Boolean.compare(bMine, aMine);
                })
                .toList();

        if (items.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(50));
            empty.setPrefWidth(900);

            javafx.scene.image.ImageView dockArt = BikeArt.bannerView("campus-dock.png", 460, 150);
            if (dockArt != null) {
                StackPane artWrap = new StackPane(dockArt);
                artWrap.setAlignment(Pos.CENTER);
                empty.getChildren().add(artWrap);
            }

            boolean fleetEmpty = cachedCycles.isEmpty();
            Label noMsg = new Label(fleetEmpty
                    ? "No cycles in the fleet right now."
                    : "No cycles match your filters.");
            noMsg.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-opacity: 0.75;");

            Label resetHint = new Label(fleetEmpty
                    ? "Check back later or register your own bike to get rolling."
                    : "Try a different search, another bike type, or uncheck 'Available Only'.");
            resetHint.setStyle("-fx-font-size: 12px; -fx-opacity: 0.55;");
            resetHint.setWrapText(true);

            empty.getChildren().addAll(noMsg, resetHint);
            cardsGrid.getChildren().add(empty);
            return;
        }

        for (CycleItem c : items) {
            cardsGrid.getChildren().add(createCycleCard(c));
        }
    }

    private VBox createCycleCard(CycleItem cycle) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setPrefWidth(325);
        card.setMaxWidth(335);
        card.getStyleClass().add("cycle-card");

        // 1. Top Row: Type Pill on Left, Favorite Heart Outline on Right
        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(cycle.type() == CycleType.ELECTRIC_BIKE ? "ELECTRIC BIKE" : (cycle.type() == CycleType.ROAD_BIKE ? "ROAD BIKE" : "CITY BIKE"));
        if (cycle.type() == CycleType.ELECTRIC_BIKE) {
            typeBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px; -fx-background-radius: 6px; -fx-background-color: #D1FAE5; -fx-text-fill: #059669;");
        } else if (cycle.type() == CycleType.ROAD_BIKE) {
            typeBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px; -fx-background-radius: 6px; -fx-background-color: #F1F5F9; -fx-text-fill: #475569;");
        } else {
            typeBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 3px 8px; -fx-background-radius: 6px; -fx-background-color: #E0F2FE; -fx-text-fill: #0284C7;");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button heartBtn = new Button();
        heartBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_HEART, 14,
                favorites.contains(cycle.id()) ? Color.web("#DC2626") : Color.web("#94A3B8")));
        heartBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2px 4px;");
        heartBtn.setAccessibleText(favorites.contains(cycle.id()) ? "Remove from favorites" : "Save to favorites");
        heartBtn.setTooltip(new Tooltip(favorites.contains(cycle.id()) ? "Remove from favorites" : "Save to favorites"));
        heartBtn.setOnAction(e -> {
            if (favorites.contains(cycle.id())) {
                favorites.remove(cycle.id());
            } else {
                favorites.add(cycle.id());
            }
            boolean fav = favorites.contains(cycle.id());
            heartBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_HEART, 14,
                    fav ? Color.web("#DC2626") : Color.web("#94A3B8")));
            heartBtn.setAccessibleText(fav ? "Remove from favorites" : "Save to favorites");
            heartBtn.setTooltip(new Tooltip(fav ? "Remove from favorites" : "Save to favorites"));
        });

        top.getChildren().addAll(typeBadge, spacer, heartBtn);

        // 2. Bike artwork: clean cutout image on transparent card canvas
        StackPane bikeArt = new StackPane();
        bikeArt.setAlignment(Pos.CENTER);
        bikeArt.setPrefHeight(125);
        bikeArt.setStyle("-fx-background-color: transparent;");

        javafx.scene.image.Image bikeImg = BikeArt.imageFor(cycle.type(), cycle.label());
        if (bikeImg != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(bikeImg);
            iv.setFitWidth(240);
            iv.setFitHeight(120);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            bikeArt.getChildren().add(iv);
        }

        // 3. Name, Location & Distance, Description
        VBox metaSection = new VBox(4);
        Label name = new Label(cycle.label());
        name.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        HBox locRow = new HBox(6);
        locRow.setAlignment(Pos.CENTER_LEFT);
        Label loc = new Label("📍 " + cycle.pickupPoint());
        loc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #475569; -fx-font-weight: 600;");
        Region spLoc = new Region();
        HBox.setHgrow(spLoc, Priority.ALWAYS);
        Label dist = new Label(formatDistanceFromHome(cycle));
        dist.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        locRow.getChildren().addAll(loc, spLoc, dist);

        Label desc = new Label(cycle.description().isBlank() ? "Reliable campus commuter with comfortable ride." : cycle.description());
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        desc.setWrapText(true);
        desc.setMaxHeight(32);

        metaSection.getChildren().addAll(name, locRow, desc);

        // 4. Rate, student-perk badge, and Reserve button
        HBox bottom = new HBox(8);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox priceCol = new VBox(2);
        HBox rateRow = new HBox(3);
        rateRow.setAlignment(Pos.BASELINE_LEFT);
        Label rate = new Label(Money.formatBdt(TariffService.quotePoisha(TariffService.BASE_MINUTES)));
        rate.setStyle("-fx-font-size: 15.5px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");
        Label unit = new Label("/ " + TariffService.BASE_MINUTES + " min");
        unit.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        rateRow.getChildren().addAll(rate, unit);

        Label perk = new Label((int) Math.round(TariffService.STUDENT_SUBSIDY_RATE * 100) + "% Student Perk");
        perk.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 700; -fx-text-fill: #065F46; -fx-background-color: #D1FAE5; -fx-padding: 2px 6px; -fx-background-radius: 6px;");
        boolean showPerk = user != null && user.role() == Role.STUDENT;
        perk.setVisible(showPerk);
        perk.setManaged(showPerk);
        priceCol.getChildren().addAll(rateRow, perk);

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);

        Button reserveBtn = new Button("Reserve");
        reserveBtn.setStyle(
                "-fx-background-color: #064E3B; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-font-size: 12.5px; " +
                "-fx-font-weight: 700; " +
                "-fx-padding: 8px 20px; " +
                "-fx-background-radius: 8px; " +
                "-fx-cursor: hand;"
        );

        boolean isMyRide = activeCycleId != null && activeCycleId.equals(cycle.id());
        boolean canBook = cycle.canBeBookedBy(user.id());
        if (isMyRide) {
            reserveBtn.setDisable(false);
            reserveBtn.setText("Your Ride  →");
            reserveBtn.setTooltip(new Tooltip("Open your active ride"));
            reserveBtn.setOnAction(e -> onGoToRide.run());
            AnimationHelper.addPressAnimation(reserveBtn);
        } else if (!canBook) {
            reserveBtn.setDisable(true);
            if (cycle.availabilityStatus() == AvailabilityStatus.MAINTENANCE
                    || cycle.availabilityStatus() == AvailabilityStatus.QUARANTINE) {
                reserveBtn.setText("Under Maintenance");
            } else if (cycle.availabilityStatus() == AvailabilityStatus.RENTED) {
                reserveBtn.setText("Rented");
            } else {
                reserveBtn.setText("Own Cycle");
            }
        } else {
            reserveBtn.setOnAction(e -> onReserve.accept(cycle));
            AnimationHelper.addPressAnimation(reserveBtn);
        }

        bottom.getChildren().addAll(priceCol, sp2, reserveBtn);

        card.getChildren().addAll(top, bikeArt, metaSection, bottom);
        ThemeManager.applySpringHover(card);
        return card;
    }

    /** User's hub preference — same source DashboardView uses (P-072 keeps the source, fixes the math). */
    private CampusHubs.Hub homeHub() {
        try {
            String fav = Preferences.userNodeForPackage(SettingsModal.class)
                    .get("favoriteHub", CampusHubs.names().get(0));
            return CampusHubs.byName(fav);
        } catch (Exception e) {
            return CampusHubs.ALL.get(0);
        }
    }

    private String formatDistanceFromHome(CycleItem cycle) {
        try {
            CampusHubs.Hub home = homeHub();
            double metres = CampusHubs.distanceMetres(home.lat(), home.lng(), cycle.latitude(), cycle.longitude());
            long rounded = Math.round(metres);
            if (rounded < 1000) {
                return rounded + " m";
            }
            return String.format(java.util.Locale.US, "%.1f km", rounded / 1000.0);
        } catch (Exception e) {
            return "";
        }
    }
}
