package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.WeatherService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Production-ready DashboardView with:
 * 1. Asynchronous multi-threaded data loading (AppExecutor)
 * 2. Live Khulna weather integration & cycling safety advice (WeatherService)
 * 3. Responsive layout bindings relative to container width
 * 4. Zero blocking on the JavaFX UI Application Thread
 */
public class DashboardView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<String> onNavigate;
    private final Consumer<CycleItem> onReserve;
    private final Consumer<String> onLocationChanged;

    private final Label availableFleetValue = new Label("Loading...");
    private final Label availableFleetSub = new Label("Querying campus database");
    private final Label viewAllBtnLabel = new Label("View All Cycles →");
    private final HBox featuredCardRow = new HBox(16);
    private final StackPane mapWrapper = new StackPane();
    private final Label weatherBadge = new Label("🌤 Khulna Weather: Connecting...");

    public DashboardView(CampusUser user,
                         CampusRepository repo,
                         Consumer<String> onNavigate,
                         Consumer<CycleItem> onReserve,
                         Consumer<String> onLocationChanged) {
        this.user = user;
        this.repo = repo;
        this.onNavigate = onNavigate;
        this.onReserve = onReserve;
        this.onLocationChanged = onLocationChanged;

        setSpacing(24);
        setPadding(new Insets(24, 32, 40, 32));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        HBox welcomeBar = createWelcomeBar();
        GridPane bento = createTelemetryBento();
        VBox mapSection = createMapSection();
        VBox featuredSection = createFeaturedSection();

        getChildren().addAll(welcomeBar, bento, mapSection, featuredSection);
        ThemeManager.applyFadeIn(this);

        // Asynchronously load live data off the UI thread
        loadDashboardDataAsync();
    }

    private HBox createWelcomeBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 0, 4, 0));

        VBox userCol = new VBox(2);
        Label greet = new Label("Welcome, " + user.displayName());
        greet.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");

        Label emailLbl = new Label("KUET Identity: " + user.email() + " • Role: " + user.role());
        emailLbl.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.7;");
        userCol.getChildren().addAll(greet, emailLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        weatherBadge.getStyleClass().add("location-pill");
        weatherBadge.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-padding: 8px 16px;");

        bar.getChildren().addAll(userCol, spacer, weatherBadge);
        return bar;
    }

    private GridPane createTelemetryBento() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        VBox c1 = createBentoCard("AVAILABLE FLEET", availableFleetValue, availableFleetSub, ThemeManager.ICON_BIKE, "#0284C7");
        VBox c2 = createBentoCard("CAMPUS STATIONS", new Label("5 Hubs Online"), new Label("100% Operational"), ThemeManager.ICON_PIN, "#10B981");
        VBox c3 = createBentoCard("STUDENT SUBSIDY TIER", new Label("25% Off"), new Label("Auto-applied to ID"), ThemeManager.ICON_SHIELD, "#0284C7");
        VBox c4 = createBentoCard("CAMPUS AIR QUALITY", new Label("0.0g Carbon"), new Label("Zero Emissions Network"), ThemeManager.ICON_LEAF, "#10B981");

        grid.add(c1, 0, 0);
        grid.add(c2, 1, 0);
        grid.add(c3, 2, 0);
        grid.add(c4, 3, 0);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25.0);
            grid.getColumnConstraints().add(col);
        }

        return grid;
    }

    private VBox createBentoCard(String title, Label valLbl, Label subLbl, String svgIcon, String accentHex) {
        VBox card = new VBox(6);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(title);
        lbl.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(svgIcon, 14, Color.web(accentHex)));
        iconBadge.setPrefSize(28, 28);
        iconBadge.getStyleClass().add("action-icon-btn");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        valLbl.getStyleClass().add("metric-number");
        subLbl.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, valLbl, subLbl);
        return card;
    }

    private VBox createMapSection() {
        VBox box = new VBox(12);

        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Live Campus Telemetry & Stations");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800;");

        Label sub = new Label("Click anywhere on campus to calculate walking distance and discover nearest available cycles");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button fullMapBtn = new Button("Open Full Map →");
        fullMapBtn.getStyleClass().add("secondary-button");
        fullMapBtn.setOnAction(e -> onNavigate.accept("Campus Map"));

        head.getChildren().addAll(titleCol, spacer, fullMapBtn);

        // Placeholder with progress indicator while map/catalog loads
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(36, 36);
        mapWrapper.getChildren().add(pi);
        mapWrapper.setPrefHeight(440);

        box.getChildren().addAll(head, mapWrapper);
        return box;
    }

    private VBox createFeaturedSection() {
        VBox box = new VBox(14);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(2);
        Label title = new Label("Ready for Instant Checkout");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800;");

        Label sub = new Label("Verified cycles unlocked with student ID and Bluetooth authentication");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button viewAllBtn = new Button();
        viewAllBtn.setGraphic(viewAllBtnLabel);
        viewAllBtn.getStyleClass().add("secondary-button");
        viewAllBtn.setOnAction(e -> onNavigate.accept("Fleet Catalog"));

        header.getChildren().addAll(titleCol, spacer, viewAllBtn);

        // Placeholder cards while loading
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(30, 30);
        featuredCardRow.setAlignment(Pos.CENTER);
        featuredCardRow.setPadding(new Insets(20));
        featuredCardRow.getChildren().add(pi);

        box.getChildren().addAll(header, featuredCardRow);
        return box;
    }

    private void loadDashboardDataAsync() {
        // 1. Fetch Khulna weather asynchronously
        new WeatherService().fetchCurrentWeatherAsync().thenAccept(weather -> {
            Platform.runLater(() -> {
                weatherBadge.setText(String.format("🌤 Khulna: %.1f°C • %s",
                        weather.temperatureCelsius(),
                        weather.advice()));
            });
        });

        // 2. Fetch cycle catalog asynchronously via worker pool
        AppExecutor.asyncThenFx(
                () -> repo.catalog(user),
                cycles -> {
                    long available = cycles.stream()
                            .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                            .count();

                    availableFleetValue.setText(available + " Cycles");
                    availableFleetSub.setText(available > 0 ? "Ready for Checkout" : "All Cycles Checked Out");
                    viewAllBtnLabel.setText("View All Cycles (" + cycles.size() + ") →");

                    // Populate featured cycle cards
                    featuredCardRow.getChildren().clear();
                    featuredCardRow.setAlignment(Pos.CENTER_LEFT);
                    List<CycleItem> featured = cycles.stream()
                            .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                            .limit(3)
                            .toList();

                    if (featured.isEmpty()) {
                        Label emptyLbl = new Label("No available cycles found at this moment.");
                        emptyLbl.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");
                        featuredCardRow.getChildren().add(emptyLbl);
                    } else {
                        for (CycleItem c : featured) {
                            VBox card = createCycleCard(c);
                            HBox.setHgrow(card, Priority.ALWAYS);
                            featuredCardRow.getChildren().add(card);
                        }
                    }

                    // Populate BingMapView
                    BingMapView mapCanvas = new BingMapView(
                            cycles,
                            hubName -> onNavigate.accept("Fleet Catalog"),
                            onLocationChanged
                    );
                    mapCanvas.setPrefHeight(440);
                    mapWrapper.getChildren().setAll(mapCanvas);
                },
                throwable -> {
                    availableFleetValue.setText("0 Cycles");
                    availableFleetSub.setText("Offline Mode");
                    featuredCardRow.getChildren().clear();
                    featuredCardRow.getChildren().add(new Label("Failed to load live fleet: " + throwable.getMessage()));
                }
        );
    }

    private VBox createCycleCard(CycleItem cycle) {
        VBox card = new VBox(12);
        card.getStyleClass().add("cycle-card");
        card.setPadding(new Insets(18));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        StackPane iconStage = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 18, Color.web("#0284C7")));
        iconStage.setPrefSize(34, 34);
        iconStage.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(1);
        Label name = new Label(cycle.label());
        name.getStyleClass().add("card-title");

        Label owner = new Label("Owner: " + cycle.ownerName());
        owner.setStyle("-fx-font-size: 10.5px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(name, owner);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label typeBadge = new Label(cycle.type().name().replace("_", " "));
        typeBadge.getStyleClass().add("badge-electric");

        top.getChildren().addAll(iconStage, titleCol, spacer, typeBadge);

        Label station = new Label("📍 Dock: " + cycle.pickupPoint());
        station.setStyle("-fx-font-size: 11.5px; -fx-opacity: 0.8;");

        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);

        Label rate = new Label("BDT 20 / 15m");
        rate.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 800; -fx-text-fill: #0284C7;");

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);

        Button reserveBtn = new Button("Reserve →");
        reserveBtn.getStyleClass().add("primary-button");
        reserveBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 6px 14px;");
        reserveBtn.setOnAction(e -> onReserve.accept(cycle));

        bottom.getChildren().addAll(rate, sp2, reserveBtn);

        card.getChildren().addAll(top, station, bottom);
        return card;
    }
}
