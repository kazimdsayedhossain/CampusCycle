package bd.ac.kuet.campuscycle;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.InMemoryCampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.ui.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public final class CampusCycleApplication extends Application {

    private CampusRepository repository = new bd.ac.kuet.campuscycle.data.InMemoryCampusRepository();
    private BingMapView activeMapView;
    private DashboardView activeDashboard;
    private FleetCatalogView activeFleet;
    private ActiveJourneyView activeJourney;
    private CampusMapView activeCampusMapView;

    private void disposeActiveViews() {
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
    }

    private Stage stage;
    private Scene scene;
    private CampusUser currentUser;
    private BorderPane mainLayout;
    private StackPane rootStack;
    private AppHeader appHeader;
    private String currentPage = "Dashboard";

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("CampusCycle | KUET Smart Mobility");
        ThemeManager.setTheme(ThemeManager.Theme.LIGHT);

        // Observer Pattern: subscribe to domain events to update UI reactively
        bd.ac.kuet.campuscycle.data.EventBus.getInstance().subscribe(
                bd.ac.kuet.campuscycle.domain.event.RentalStartedEvent.class,
                event -> javafx.application.Platform.runLater(() -> {
                    if (appHeader != null && currentUser != null && currentUser.id().equals(event.rental().renterId())) {
                        appHeader.setHasActiveRide(true);
                    }
                })
        );

        bd.ac.kuet.campuscycle.data.EventBus.getInstance().subscribe(
                bd.ac.kuet.campuscycle.domain.event.RentalReturnedEvent.class,
                event -> javafx.application.Platform.runLater(() -> {
                    if (appHeader != null && currentUser != null && currentUser.id().equals(event.user().id())) {
                        appHeader.setHasActiveRide(false);
                    }
                })
        );

        showLogin();

        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        stage.show();
    }

    private void showLogin() {
        disposeActiveViews();
        bd.ac.kuet.campuscycle.data.SessionStore.clear();
        if (activeMapView != null) {
            activeMapView.dispose();
            activeMapView = null;
        }
        LoginView loginView = new LoginView(this::openWorkspace);

        rootStack = new StackPane(loginView);
        setupScene(rootStack, 1100, 740);
    }

    private void openWorkspace(CampusUser user) {
        this.currentUser = user;
        if (bd.ac.kuet.campuscycle.data.DatabaseConnection.isAvailable()) {
            repository = new bd.ac.kuet.campuscycle.data.SupabaseCampusRepository();
        } else {
            repository = new bd.ac.kuet.campuscycle.data.InMemoryCampusRepository();
        }

        mainLayout = new BorderPane();

        appHeader = new AppHeader(
                currentUser,
                false,
                this::navigateTo,
                this::openSettings,
                this::openLocationPicker
        );

        mainLayout.setTop(appHeader);
        BorderPane.setMargin(appHeader, new Insets(16, 24, 0, 24));

        rootStack = new StackPane(mainLayout);
        setupScene(rootStack, 1240, 820);
        navigateTo("Dashboard");
        // Resolve active ride off the FX thread to avoid startup freeze.
        bd.ac.kuet.campuscycle.service.AppExecutor.asyncThenFx(
                () -> {
                    try {
                        return repository.activeRental(currentUser) != null;
                    } catch (Exception e) {
                        return false;
                    }
                },
                hasActive -> {
                    if (appHeader != null) appHeader.setHasActiveRide(hasActive);
                },
                err -> {
                });
    }

    public void navigateTo(String page) {
        this.currentPage = page;
        if (appHeader != null) {
            appHeader.setActivePage(page);
        }
        disposeActiveViews();

        Node content;
        switch (page) {
            case "Fleet Catalog" -> {
                FleetCatalogView fleet = new FleetCatalogView(
                        currentUser,
                        repository,
                        this::openReservationModal,
                        locationName -> {
                            if (appHeader != null) appHeader.setLocationDisplay(locationName);
                        },
                        this::openCycleRegistrationModal
                );
                activeFleet = fleet;
                content = fleet;
            }
            case "Campus Map" -> {
                VBox mapWrap = new VBox(16);
                mapWrap.setAlignment(Pos.TOP_CENTER);
                mapWrap.setPadding(new Insets(16, 24, 24, 24));
                mapWrap.setMaxWidth(1220);

                Label mapLoading = new Label("Initializing KUET & Khulna Navigation Radar...");
                mapLoading.setStyle("-fx-font-size: 13px; -fx-opacity: 0.7;");
                mapWrap.getChildren().add(mapLoading);
                content = mapWrap;

                CampusUser mapUser = currentUser;
                bd.ac.kuet.campuscycle.service.AppExecutor.asyncThenFx(
                        () -> {
                            try {
                                return repository.catalog(mapUser);
                            } catch (Exception e) {
                                return java.util.List.<CycleItem>of();
                            }
                        },
                        cycles -> {
                            CampusMapView campusMap = new CampusMapView(
                                    mapUser,
                                    cycles,
                                    this::navigateTo,
                                    locationName -> {
                                        if (appHeader != null) appHeader.setLocationDisplay(locationName);
                                    }
                            );
                            activeCampusMapView = campusMap;
                            mapWrap.getChildren().setAll(campusMap);
                        },
                        err -> {
                            CampusMapView fallback = new CampusMapView(
                                    mapUser,
                                    java.util.List.of(),
                                    this::navigateTo,
                                    locationName -> {}
                            );
                            activeCampusMapView = fallback;
                            mapWrap.getChildren().setAll(fallback);
                        }
                );
            }
            case "Active Journey" -> {
                ActiveJourneyView journey = new ActiveJourneyView(
                        currentUser,
                        repository,
                        this::navigateTo,
                        () -> openWorkspace(currentUser)
                );
                activeJourney = journey;
                content = journey;
            }
            case "Passbook" -> {
                content = new PassbookView(currentUser, repository);
            }
            case "Support" -> {
                content = new SupportView(currentUser, repository);
            }
            case "Admin Operations" -> {
                content = new AdminOperationsView(
                        currentUser,
                        repository,
                        () -> navigateTo("Admin Operations")
                );
            }
            default -> { // "Dashboard"
                DashboardView dash = new DashboardView(
                        currentUser,
                        repository,
                        this::navigateTo,
                        this::openReservationModal,
                        locationName -> {
                            if (appHeader != null) appHeader.setLocationDisplay(locationName);
                        }
                );
                activeDashboard = dash;
                content = dash;
            }
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        mainLayout.setCenter(scroll);
    }

    private void openReservationModal(CycleItem cycle) {
        ReservationModal modal = new ReservationModal(
                cycle,
                currentUser,
                repository,
                () -> rootStack.getChildren().removeIf(n -> n instanceof ReservationModal),
                () -> {
                    rootStack.getChildren().removeIf(n -> n instanceof ReservationModal);
                    openWorkspace(currentUser);
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
        SettingsModal settings = new SettingsModal(
                currentUser,
                () -> rootStack.getChildren().removeIf(n -> n instanceof SettingsModal),
                this::showLogin
        );
        rootStack.getChildren().add(settings);
    }

    private void openLocationPicker() {
        navigateTo("Campus Map");
    }

    private void setupScene(StackPane content, double width, double height) {
        rootStack = content;
        scene = new Scene(rootStack, width, height);
        applyActiveTheme();
        stage.setScene(scene);
    }

    private void applyActiveTheme() {
        if (scene == null) return;
        scene.getStylesheets().clear();
        String cssPath = getClass().getResource("/bd/ac/kuet/campuscycle/" + ThemeManager.getTheme().cssFile()).toExternalForm();
        scene.getStylesheets().add(cssPath);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
