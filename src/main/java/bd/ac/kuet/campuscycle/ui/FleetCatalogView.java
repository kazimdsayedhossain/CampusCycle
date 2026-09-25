package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
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
 * Production-ready FleetCatalogView:
 * 1. Asynchronous multi-threaded data fetching
 * 2. Instant in-memory client-side search filtering (no query flooding)
 * 3. Peer cycle registration action button
 * 4. Embedded interactive BingMapView with station jump
 */
public class FleetCatalogView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<CycleItem> onReserve;
    private final Consumer<String> onLocationChanged;
    private final Runnable onOpenRegister;

    private final TextField searchField = new TextField();
    private final CheckBox availableOnlyCheck = new CheckBox("Available Only");
    private final FlowPane cardsGrid = new FlowPane(16, 16);
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

        cardsGrid.setAlignment(Pos.TOP_LEFT);
        cardsGrid.setPrefWrapLength(1080);

        getChildren().addAll(header, controls, mapContainer, cardsGrid);

        refreshCatalog();
        ThemeManager.applyFadeIn(this);
    }

    private HBox createHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("KUET Fleet Catalog");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

        Label sub = new Label("Discover and instantly reserve verified bicycles across all 5 campus hubs");
        sub.getStyleClass().add("metric-label");
        sub.setStyle("-fx-opacity: 0.75;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button registerBtn = new Button("Register My Cycle (+)");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 14, Color.WHITE));
        if (onOpenRegister != null) {
            registerBtn.setOnAction(e -> onOpenRegister.run());
        }

        Button toggleMapBtn = new Button("Toggle Station Map");
        toggleMapBtn.getStyleClass().add("secondary-button");
        toggleMapBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_PIN, 13, Color.web("#0F172A")));
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
        // Lazy: WebView created only when user toggles map visible.
    }

    private void rebuildStationMap() {
        if (stationMap != null) stationMap.dispose();
        BingMapView map = new BingMapView(
                cachedCycles,
                hubName -> searchField.setText(hubName),
                onLocationChanged
        );
        stationMap = map;
        map.setPrefHeight(400);
        mapContainer.getChildren().setAll(map);
    }

    /** Release embedded map. */
    public void dispose() {
        if (stationMap != null) {
            stationMap.dispose();
            stationMap = null;
        }
    }

    private VBox createControlBar() {
        VBox bar = new VBox(12);

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        HBox searchBox = new HBox(8);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("input-pill-box");
        searchBox.setPrefWidth(380);

        SVGPath searchIcon = ThemeManager.createIcon(ThemeManager.ICON_SEARCH, 14, Color.web("#9CA3AF"));
        searchField.setPromptText("Search cycles by model, station, or owner...");
        searchField.getStyleClass().add("bare-input");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Instant in-memory filtering on keystroke (no network calls)
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        searchBox.getChildren().addAll(searchIcon, searchField);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        availableOnlyCheck.setSelected(true);
        availableOnlyCheck.getStyleClass().add("metric-label");
        availableOnlyCheck.selectedProperty().addListener((obs, o, n) -> applyFilter());

        topRow.getChildren().addAll(searchBox, spacer, availableOnlyCheck);

        // Filter Chips Row
        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER_LEFT);

        addChip(chips, "All Models", "ALL");
        addChip(chips, "City Commuters", "CITY_BIKE");
        addChip(chips, "Road Racers", "ROAD_BIKE");
        addChip(chips, "Electric Assisted", "ELECTRIC_BIKE");
        addChip(chips, "Cargo Utility", "CARGO_BIKE");

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
        pi.setMaxSize(40, 40);
        VBox loadingBox = new VBox(12, pi, new Label("Loading campus fleet..."));
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefWidth(1000);
        loadingBox.setPadding(new Insets(50));
        cardsGrid.getChildren().add(loadingBox);

        AppExecutor.asyncThenFx(
                () -> repo.catalog(user),
                cycles -> {
                    isLoading = false;
                    this.cachedCycles = cycles;
                    // Update map only if visible (lazy, dispose previous)
                    if (isMapVisible) {
                        rebuildStationMap();
                    }

                    applyFilter();
                },
                throwable -> {
                    isLoading = false;
                    cardsGrid.getChildren().clear();
                    cardsGrid.getChildren().add(new Label("Catalog is offline. Please retry."));
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
                    if (!selectedTypeFilter.equals("ALL") && !c.type().name().equals(selectedTypeFilter)) return false;
                    if (query.isEmpty()) return true;
                    return c.label().toLowerCase().contains(query)
                            || c.pickupPoint().toLowerCase().contains(query)
                            || c.ownerName().toLowerCase().contains(query);
                })
                .toList();

        if (items.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(40));
            empty.setPrefWidth(1000);

            Label noMsg = new Label("No bicycles match your filter criteria.");
            noMsg.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-opacity: 0.7;");

            Label resetHint = new Label("Try changing your station search or select 'All Models'.");
            resetHint.setStyle("-fx-font-size: 12px; -fx-opacity: 0.5;");

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
        card.setPadding(new Insets(20));
        card.setPrefWidth(340);
        card.setMaxWidth(340);

        String accentColor = switch (cycle.type()) {
            case ELECTRIC_BIKE -> "#2EB5A4";
            case ROAD_BIKE -> "#8B5CF6";
            case CARGO_BIKE -> "#F59E0B";
            case CITY_BIKE -> "#2EB5A4";
            default -> "#35BFAE";
        };
        card.setStyle(card.getStyle() + String.format("; -fx-border-color: transparent transparent transparent %s; -fx-border-width: 0 0 0 4px;", accentColor));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        StackPane iconStage = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 18, Color.web(accentColor)));
        iconStage.setPrefSize(36, 36);
        iconStage.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(2);
        Label name = new Label(cycle.label());
        name.getStyleClass().add("card-title");

        Label owner = new Label("Verified Owner: " + cycle.ownerName());
        owner.getStyleClass().add("metric-label");
        owner.setStyle("-fx-opacity: 0.7;");
        titleCol.getChildren().addAll(name, owner);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label typeBadge = new Label(cycle.type().name().replace("_", " "));
        typeBadge.getStyleClass().add("badge-electric");

        top.getChildren().addAll(iconStage, titleCol, spacer, typeBadge);

        VBox metaSection = new VBox(6);
        Label station = new Label("Dock: " + cycle.pickupPoint());
        station.getStyleClass().add("metric-label");
        station.setStyle("-fx-font-weight: 650;");

        Label desc = new Label(cycle.description().isEmpty() ? "Standard KUET campus commuter bicycle." : cycle.description());
        desc.getStyleClass().add("metric-label");
        desc.setStyle("-fx-opacity: 0.65;");
        desc.setWrapText(true);

        metaSection.getChildren().addAll(station, desc);

        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox priceCol = new VBox(1);
        Label rate = new Label(bd.ac.kuet.campuscycle.domain.TariffService.formatBdt(bd.ac.kuet.campuscycle.domain.TariffService.BASE_CHARGE_POISHA));
        rate.getStyleClass().add("metric-number");
        rate.setStyle("-fx-text-fill: #0F172A;");

        Label sub = new Label("First 15m • +10/15m");
        sub.getStyleClass().add("metric-label");
        sub.setStyle("-fx-opacity: 0.6;");
        priceCol.getChildren().addAll(rate, sub);

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
