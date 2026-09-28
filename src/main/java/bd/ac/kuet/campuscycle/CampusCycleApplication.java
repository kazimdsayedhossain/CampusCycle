package bd.ac.kuet.campuscycle;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.DatabaseConnection;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.data.SessionStore;
import bd.ac.kuet.campuscycle.data.SupabaseCampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.event.RentalReturnedEvent;
import bd.ac.kuet.campuscycle.domain.event.RentalStartedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.ui.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class CampusCycleApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(CampusCycleApplication.class.getName());

    /** Known navigation targets. Anything else gets a visible warning and keeps the current page (P-179). */
    private static final Set<String> KNOWN_ROUTES = Set.of(
            "Dashboard", "Fleet Catalog", "Campus Map", "Active Journey",
            "Passbook", "Support", "Maintenance", "Admin Operations", "Settings");

    private CampusRepository repository;
    private BingMapView activeMapView;
    private DashboardView activeDashboard;
    private FleetCatalogView activeFleet;
    private ActiveJourneyView activeJourney;
    private CampusMapView activeCampusMapView;
    private TechnicianView activeTechnicianView;

    /** Guards late async callbacks after navigate-away / sign-out (P-109). */
    private final AtomicInteger navGeneration = new AtomicInteger();

    private CampusUser currentUser;
    private SidebarView sidebar;

    /** EventBus listener refs so sign-out can unsubscribe instead of leaking (P-154). */
    private final Consumer<RentalStartedEvent> rentalStartedListener =
            event -> Platform.runLater(() -> {
                if (sidebar != null && currentUser != null && currentUser.id().equals(event.rental().renterId())) {
                    sidebar.setHasActiveRide(true);
                }
            });
    private final Consumer<RentalReturnedEvent> rentalReturnedListener =
            event -> Platform.runLater(() -> {
                if (sidebar != null && currentUser != null && currentUser.id().equals(event.user().id())) {
                    sidebar.setHasActiveRide(false);
                }
            });

    private void disposeActiveViews() {
        if (topBar != null) {
            topBar.dispose();
            topBar = null;
        }
        if (activeMapView != null) {
            activeMapView.dispose();
            activeMapView = null;
        }
        if (activeCampusMapView != null) {
            activeCampusMapView.dispose();
            activeCampusMapView = null;
        }
        if (activeDashboard != null) {
            activeDashboard.dispose();
            activeDashboard = null;
        }
        if (activeFleet != null) {
            activeFleet.dispose();
            activeFleet = null;
        }
        if (activeJourney != null) {
            activeJourney.dispose();
            activeJourney = null;
        }
        if (activeTechnicianView != null) {
            activeTechnicianView.dispose();
            activeTechnicianView = null;
        }
    }

    private Stage stage;
    private Scene scene;
    private HBox shellLayout;
    private VBox mainColumn;
    private StackPane rootStack;
    private StackPane contentHost; // for page transition animations
    private TopBarView topBar;
    private String pendingFleetQuery;
    private javafx.scene.image.ImageView stageBgView;
    private Region stageScrim;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("CampusCycle | KUET");
        // Single app-lifetime listener: keeps the Scene stylesheet in sync on toggle.
        // (Per-view listeners are the P-106/P-108 leak — those live in ThemeManager.install, not here.)
        ThemeManager.themeProperty().addListener((obs, o, n) -> applyActiveTheme());
        applyWindowIcon();

        // Observer Pattern: subscribe to domain events to update UI reactively.
        // Listener refs are fields so showLogin() can unsubscribe (P-154).
        EventBus.getInstance().subscribe(RentalStartedEvent.class, rentalStartedListener);

        EventBus.getInstance().subscribe(RentalReturnedEvent.class, rentalReturnedListener);

        showLogin();

        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setMaximized(true);
        stage.show();

        // Toast container only — no ambient leaf on the login screen.
        Platform.runLater(() -> {
            if (rootStack != null) {
                AnimationHelper.initToastContainer(rootStack);
            }
        });

        // Photoshop-style splash: shown once the window is up, then dismissed when the
        // shell has painted. Showing it last means the splash never covers a
        // half-built scene, and finishing it is a no-op if the 6s guard beat it.
        SplashScreen splash = new SplashScreen();
        splash.setStatus("Warming up the cycle hubs…");
        splash.show(stage);
        Platform.runLater(() -> Platform.runLater(splash::finish));
    }

    private void showLogin() {
        // Cancel any in-flight workspace/catalog callbacks, then release everything (P-107, P-109, P-154).
        navGeneration.incrementAndGet();
        EventBus.getInstance().unsubscribe(RentalStartedEvent.class, rentalStartedListener);
        EventBus.getInstance().unsubscribe(RentalReturnedEvent.class, rentalReturnedListener);
        AnimationHelper.stopAmbientLeaf();
        disposeActiveViews();
        this.currentUser = null;
        this.repository = null;
        this.sidebar = null;
        this.mainColumn = null;
        this.shellLayout = null;
        this.pendingFleetQuery = null;
        if (stageBgView != null) {
            stageBgView.fitWidthProperty().unbind();
            stageBgView.fitHeightProperty().unbind();
            stageBgView = null;
        }
        if (stageScrim != null) {
            stageScrim.prefWidthProperty().unbind();
            stageScrim.prefHeightProperty().unbind();
            stageScrim = null;
        }
        SessionStore.clear();
        LoginView loginView = new LoginView(this::openWorkspace);
        rootStack = loginView;
        setupScene(rootStack, 1360, 860);
        if (stage != null) {
            stage.setMaximized(true);
        }
    }

    private void openWorkspace(CampusUser user) {
        // Tear down the previous workspace first so orphaned Timelines/WebViews cannot survive (P-107).
        disposeActiveViews();
        this.currentUser = user;
        final int gen = navGeneration.incrementAndGet();

        // Repository construction + availability probe run off the FX thread (P-056).
        VBox loadingBox = new VBox(16,
                new ProgressIndicator(),
                new Label("Connecting to campus services..."));
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(40));
        setupScene(new StackPane(loadingBox), 1360, 860);

        AppExecutor.asyncThenFx(
                () -> {
                    boolean available = DatabaseConnection.isAvailable();
                    CampusRepository repo = available ? new SupabaseCampusRepository() : null;
                    return new Object[]{available, repo};
                },
                result -> {
                    if (navGeneration.get() != gen) {
                        return; // user navigated away or signed out while probing
                    }
                    boolean available = (Boolean) result[0];
                    CampusRepository repo = (CampusRepository) result[1];
                    if (!available || repo == null) {
                        showDatabaseError();
                        return;
                    }
                    buildWorkspace(repo, gen);
                },
                err -> {
                    if (navGeneration.get() != gen) {
                        return;
                    }
                    LOGGER.warning("Workspace init failed: " + err.getMessage());
                    showDatabaseError();
                });
    }

    /** Builds the shell on the FX thread once the repository is ready. */
    private void buildWorkspace(CampusRepository repo, int gen) {
        this.repository = repo;

        Role role = currentUser != null && currentUser.role() != null ? currentUser.role() : Role.STUDENT;
        sidebar = new SidebarView("Dashboard", role, this::navigateTo, this::openSettings);
        topBar = new TopBarView(
                currentUser,
                query -> {
                    pendingFleetQuery = query;
                    navigateTo("Fleet Catalog");
                },
                () -> navigateTo("Support"),
                this::openSettings
        );

        mainColumn = new VBox();
        mainColumn.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(mainColumn, Priority.ALWAYS);

        shellLayout = new HBox();
        shellLayout.setStyle("-fx-background-color: transparent;");

        // Unbind before rebinding: the Stage outlives every workspace, so stale
        // bindings would pin each discarded background/scrim forever (P-107).
        if (stageBgView != null) {
            stageBgView.fitWidthProperty().unbind();
            stageBgView.fitHeightProperty().unbind();
        }
        if (stageScrim != null) {
            stageScrim.prefWidthProperty().unbind();
            stageScrim.prefHeightProperty().unbind();
        }
        stageBgView = new javafx.scene.image.ImageView();
        stageBgView.setPreserveRatio(false);
        stageBgView.setSmooth(true);
        if (stage != null) {
            stageBgView.fitWidthProperty().bind(stage.widthProperty());
            stageBgView.fitHeightProperty().bind(stage.heightProperty());
        }

        stageScrim = new Region();
        updateScrimStyle();
        if (stage != null) {
            stageScrim.prefWidthProperty().bind(stage.widthProperty());
            stageScrim.prefHeightProperty().bind(stage.heightProperty());
        }

        // Content host for page transition animations
        contentHost = new StackPane();
        contentHost.setAlignment(Pos.TOP_CENTER);
        contentHost.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(contentHost);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.getStyleClass().add("scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        mainColumn.getChildren().addAll(topBar, scrollPane);

        shellLayout.getChildren().addAll(sidebar, mainColumn);

        StackPane workspaceRoot = new StackPane();
        // Dedicated ambient layer sits behind the shell: the leaf drifts behind
        // content and can never cover modals, toasts, or dialogs.
        Pane ambientLayer = new Pane();
        ambientLayer.setMouseTransparent(true);
        ambientLayer.setPickOnBounds(false);
        workspaceRoot.getChildren().addAll(stageBgView, stageScrim, ambientLayer, shellLayout);

        rootStack = workspaceRoot;
        setupScene(rootStack, 1360, 860);

        // Toast container re-attaches to the new root; leaf runs on the background layer.
        AnimationHelper.initToastContainer(rootStack);
        AnimationHelper.startAmbientLeaf(ambientLayer);

        navigateTo("Dashboard");

        // Resolve active ride off the FX thread to update sidebar reactively
        AppExecutor.asyncThenFx(
                () -> {
                    try {
                        return repository.activeRental(currentUser) != null;
                    } catch (Exception e) {
                        return false;
                    }
                },
                hasActive -> {
                    if (navGeneration.get() != gen) {
                        return;
                    }
                    if (sidebar != null) sidebar.setHasActiveRide(hasActive);
                },
                err -> {
                }
        );
    }

    private void showDatabaseError() {
        VBox errorLayout = new VBox(20);
        errorLayout.setAlignment(Pos.CENTER);
        errorLayout.setPadding(new Insets(40));
        errorLayout.setStyle("-fx-background-color: white;");

        Label title = new Label("Connection Error");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");

        Label message = new Label("Unable to connect to the campus cycle server.\nPlease check your internet connection and try again.");
        message.setWrapText(true);
        message.setAlignment(Pos.CENTER);
        message.setStyle("-fx-font-size: 16px; -fx-text-fill: #4b5563;");

        javafx.scene.control.Button retryBtn = new javafx.scene.control.Button("Retry Connection");
        retryBtn.setOnAction(e -> showLogin());
        retryBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-padding: 10 20;");

        errorLayout.getChildren().addAll(title, message, retryBtn);
        setupScene(new StackPane(errorLayout), 1360, 860);
    }

    private String canonicalPage(String page) {
        return switch (page) {
            case "Bikes" -> "Fleet Catalog";
            case "Map" -> "Campus Map";
            case "My Ride" -> "Active Journey";
            case "Payments" -> "Passbook";
            case "Messages" -> "Support";
            case "Admin" -> "Admin Operations";
            default -> page;
        };
    }

    public void navigateTo(String page) {
        String canonical = canonicalPage(page);

        // "Settings" is a real route that opens the modal (P-179).
        if ("Settings".equals(canonical)) {
            openSettings();
            return;
        }

        // Hiding the nav item is not a gate: block non-admins here (P-048).
        if ("Admin Operations".equals(canonical)
                && (currentUser == null || currentUser.role() != Role.ADMIN)) {
            LOGGER.warning("Blocked non-admin navigation to Admin Operations.");
            canonical = "Dashboard";
        }
        // Maintenance console: technicians and admins only; students land on Dashboard.
        if ("Maintenance".equals(canonical)
                && (currentUser == null
                    || (currentUser.role() != Role.TECHNICIAN && currentUser.role() != Role.ADMIN))) {
            LOGGER.warning("Blocked non-technician navigation to Maintenance.");
            canonical = "Dashboard";
        }

        // Unknown routes stay put with a visible message instead of silently
        // dropping the user on the Dashboard (P-179).
        if (!KNOWN_ROUTES.contains(canonical)) {
            LOGGER.warning("Unknown navigation target requested: " + canonical + ". Staying on current page.");
            Alert unknown = new Alert(Alert.AlertType.WARNING,
                    "Unknown destination: " + canonical, ButtonType.OK);
            unknown.setTitle("Navigation");
            unknown.setHeaderText(null);
            unknown.showAndWait();
            return;
        }

        final int gen = navGeneration.incrementAndGet();
        if (sidebar != null) {
            sidebar.setActivePage(canonical);
        }
        updateStageBackground(canonical);
        disposeActiveViews();

        Node newContent = buildPageContent(canonical, gen);

        // Animated page transition only. Views run their own entrance
        // (ThemeManager.applyFadeIn in constructors) — no second pass here,
        // which used to re-hide every child and fight tables/charts mid-load.
        AnimationHelper.transitionContent(contentHost, newContent, null);
    }

    /** Builds the page content node for a given route. */
    private Node buildPageContent(String canonical, int gen) {
        switch (canonical) {
            case "Fleet Catalog" -> {
                FleetCatalogView fleet = new FleetCatalogView(
                        currentUser,
                        repository,
                        this::openReservationModal,
                        locationName -> {
                        },
                        this::openCycleRegistrationModal,
                        () -> navigateTo("Active Journey")
                );
                activeFleet = fleet;
                if (pendingFleetQuery != null && !pendingFleetQuery.isBlank()) {
                    fleet.setSearchQuery(pendingFleetQuery);
                }
                pendingFleetQuery = null;
                return fleet;
            }
            case "Campus Map" -> {
                VBox mapWrap = new VBox(16);
                mapWrap.setStyle("-fx-background-color: transparent;");
                mapWrap.setAlignment(Pos.TOP_CENTER);
                mapWrap.setPadding(new Insets(6, 20, 24, 20));

                Label mapLoading = new Label("Loading campus map...");
                mapLoading.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");
                mapWrap.getChildren().add(mapLoading);

                CampusUser mapUser = currentUser;
                AppExecutor.asyncThenFx(
                        () -> {
                            try {
                                return repository.catalog(mapUser);
                            } catch (Exception e) {
                                return java.util.List.<CycleItem>of();
                            }
                        },
                        cycles -> {
                            if (navGeneration.get() != gen) {
                                return;
                            }
                            CampusMapView campusMap = new CampusMapView(
                                    mapUser,
                                    cycles,
                                    this::navigateTo,
                                    locationName -> {
                                    }
                            );
                            activeCampusMapView = campusMap;
                            mapWrap.getChildren().setAll(campusMap);
                        },
                        err -> {
                            if (navGeneration.get() != gen) return;
                            // Error handled by map view
                        }
                );
                return mapWrap;
            }
            case "Active Journey" -> {
                ActiveJourneyView journey = new ActiveJourneyView(
                        currentUser,
                        repository,
                        this::navigateTo,
                        () -> openWorkspace(currentUser)
                );
                activeJourney = journey;
                return journey;
            }
            case "Passbook" -> {
                return new PassbookView(currentUser, repository);
            }
            case "Support" -> {
                return new SupportView(currentUser, repository);
            }
            case "Maintenance" -> {
                TechnicianView techView = new TechnicianView(
                        currentUser,
                        repository,
                        () -> navigateTo("Maintenance")
                );
                activeTechnicianView = techView;
                return techView;
            }
            case "Admin Operations" -> {
                AdminOperationsView admin = new AdminOperationsView(
                        currentUser,
                        repository,
                        () -> navigateTo("Admin Operations")
                );
                return admin;
            }
            case "Dashboard" -> {
                DashboardView dash = new DashboardView(
                        currentUser,
                        repository,
                        this::navigateTo,
                        this::openReservationModal,
                        locationName -> {
                        }
                );
                activeDashboard = dash;
                return dash;
            }
            default -> throw new IllegalStateException("Unhandled route (guard above should reject): " + canonical);
        }
    }

    private void openReservationModal(CycleItem cycle) {
        ReservationModal modal = new ReservationModal(
                cycle,
                currentUser,
                repository,
                () -> rootStack.getChildren().removeIf(n -> n instanceof ReservationModal),
                () -> {
                    rootStack.getChildren().removeIf(n -> n instanceof ReservationModal);
                    navigateTo("Active Journey");
                }
        );
        rootStack.getChildren().add(modal);
    }

    private void openCycleRegistrationModal() {
        CycleRegistrationModal modal = new CycleRegistrationModal(
                currentUser,
                () -> rootStack.getChildren().removeIf(n -> n instanceof CycleRegistrationModal),
                () -> {
                    rootStack.getChildren().removeIf(n -> n instanceof CycleRegistrationModal);
                    navigateTo("Fleet Catalog");
                }
        );
        rootStack.getChildren().add(modal);
    }

    private void openSettings() {
        if (rootStack == null) {
            return;
        }
        SettingsModal settings = new SettingsModal(
                currentUser,
                repository,
                () -> rootStack.getChildren().removeIf(n -> n instanceof SettingsModal),
                this::showLogin
        );
        rootStack.getChildren().add(settings);
    }

    private void setupScene(StackPane content, double width, double height) {
        rootStack = content;
        scene = new Scene(rootStack, width, height);
        applyActiveTheme();
        // The "Ctrl K" pill in the top bar is real: focus the search field (P-097/H-3).
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
                () -> {
                    if (topBar != null) {
                        topBar.focusSearch();
                    }
                });
        stage.setScene(scene);
    }

    private void applyActiveTheme() {
        updateScrimStyle();
        applyWindowIcon();
        if (scene == null) return;
        scene.getStylesheets().clear();
        URL css = ThemeManager.resolve(ThemeManager.getTheme().cssFile());
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    /**
     * Puts the brand mark on the window and taskbar, following the active theme.
     * Driven from the single app-lifetime theme listener, so no extra subscription
     * is created here.
     */
    private void applyWindowIcon() {
        if (stage == null) return;
        try {
            stage.getIcons().setAll(CampusLogo.windowIcons(ThemeManager.isDark()));
        } catch (Exception e) {
            // A missing or unreadable icon must never stop the app from starting.
            java.util.logging.Logger.getLogger(CampusCycleApplication.class.getName())
                    .log(java.util.logging.Level.FINE, "Window icon unavailable", e);
        }
    }

    private void updateScrimStyle() {
        if (stageScrim == null) return;
        // Keep the wash light so the per-page photo stays visible; cards carry
        // their own solid surfaces, the scrim only tints the gutters.
        boolean isDark = ThemeManager.getTheme() == ThemeManager.Theme.DARK;
        stageScrim.setStyle(isDark
                ? "-fx-background-color: rgba(15, 23, 42, 0.55);"
                : "-fx-background-color: rgba(248, 250, 252, 0.32);");
    }

    private void updateStageBackground(String page) {
        if (stageBgView == null) return;
        String bgFile = switch (page) {
            case "Fleet Catalog", "Bikes" -> "bg-promenade-racks.jpg";
            case "Campus Map", "Map" -> "bg-lake-bridge.jpg";
            case "Active Journey", "My Ride" -> "bg-bike-dock.jpg";
            case "Passbook", "Payments" -> "bg-lake-reflection.jpg";
            case "Support", "Messages" -> "bg-lake-bridge.jpg";
            case "Admin Operations", "Admin" -> "bg-monument-plaza.jpg";
            default -> "bg-monument-plaza.jpg"; // Dashboard
        };
        javafx.scene.image.Image img = BikeArt.backgroundImage(bgFile);
        if (img != null && !img.isError()) {
            if (stageBgView.getImage() == null) {
                stageBgView.setImage(img);
            } else if (stageBgView.getImage() != img) {
                javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(160), stageBgView);
                fade.setFromValue(stageBgView.getOpacity());
                fade.setToValue(0.25);
                fade.setOnFinished(e -> {
                    stageBgView.setImage(img);
                    javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), stageBgView);
                    fadeIn.setFromValue(0.25);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                });
                fade.play();
            }
        }
    }

    @Override
    public void stop() {
        AppExecutor.shutdown();
        DatabaseConnection.closeAllPhysical();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
