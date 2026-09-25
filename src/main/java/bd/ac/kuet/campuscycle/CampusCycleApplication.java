package bd.ac.kuet.campuscycle;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.InMemoryCampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.ui.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public final class CampusCycleApplication extends Application {

    private CampusRepository repository = new bd.ac.kuet.campuscycle.data.SupabaseCampusRepository();
    private final CampusUser student = new CampusUser("3d1e3d69-ffc6-494f-a42c-26eeb258b581", "Arafat Rahman", "arafat@kuet.ac.bd", Role.STUDENT);
    private final CampusUser admin = new CampusUser("56d6f9dc-0ca7-4b49-9f9e-3c48a1b2089a", "KUET Cycle Office", "cycleoffice@kuet.ac.bd", Role.ADMIN);

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
        LoginView loginView = new LoginView(
                this::openWorkspace,
                () -> {
                    repository = new bd.ac.kuet.campuscycle.data.SupabaseCampusRepository();
                    openWorkspace(student);
                },
                () -> {
                    repository = new bd.ac.kuet.campuscycle.data.SupabaseCampusRepository();
                    openWorkspace(admin);
                }
        );

        rootStack = new StackPane(loginView);
        setupScene(rootStack, 1100, 740);
    }

    private void openWorkspace(CampusUser user) {
        this.currentUser = user;

        mainLayout = new BorderPane();

        boolean hasActive = repository.activeRental(currentUser) != null;

        appHeader = new AppHeader(
                currentUser,
                hasActive,
                this::navigateTo,
                this::openSettings,
                () -> openWorkspace(currentUser.role() == Role.ADMIN ? student : admin),
                this::openLocationPicker
        );

        mainLayout.setTop(appHeader);
        BorderPane.setMargin(appHeader, new Insets(16, 24, 0, 24));

        rootStack = new StackPane(mainLayout);
        setupScene(rootStack, 1240, 820);
        navigateTo("Dashboard");
    }

    public void navigateTo(String page) {
        this.currentPage = page;
        if (appHeader != null) {
            appHeader.setActivePage(page);
        }

        Node content;
        switch (page) {
            case "Fleet Catalog" -> {
                content = new FleetCatalogView(
                        currentUser,
                        repository,
                        this::openReservationModal,
                        locationName -> {
                            if (appHeader != null) appHeader.setLocationDisplay(locationName);
                        },
                        this::openCycleRegistrationModal
                );
            }
            case "Campus Map" -> {
                VBox mapWrap = new VBox(16);
                mapWrap.setAlignment(Pos.TOP_CENTER);
                mapWrap.setPadding(new Insets(24, 36, 36, 36));

                HBox mapHeader = new HBox(16);
                mapHeader.setAlignment(Pos.CENTER_LEFT);
                mapHeader.setMaxWidth(1160);

                VBox titleCol = new VBox(3);
                Label title = new Label("KUET & Khulna City Navigation Radar");
                title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");
                Label sub = new Label("Live satellite & street telemetry covering KUET campus quad-docks and Khulna metropolitan free-roaming zones");
                sub.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.75;");
                titleCol.getChildren().addAll(title, sub);

                mapHeader.getChildren().add(titleCol);

                BingMapView fullMap = new BingMapView(
                        repository.catalog(currentUser),
                        hubName -> navigateTo("Fleet Catalog"),
                        locationName -> {
                            if (appHeader != null) appHeader.setLocationDisplay(locationName);
                        }
                );
                fullMap.setPrefHeight(620);
                fullMap.setMaxWidth(1160);

                mapWrap.getChildren().addAll(mapHeader, fullMap);
                content = mapWrap;
            }
            case "Active Journey" -> {
                content = new ActiveJourneyView(
                        currentUser,
                        repository,
                        this::navigateTo,
                        () -> openWorkspace(currentUser)
                );
            }
            case "Passbook" -> {
                content = new PassbookView(currentUser, repository);
            }
            case "Admin Operations" -> {
                content = new AdminOperationsView(
                        currentUser,
                        repository,
                        () -> navigateTo("Admin Operations")
                );
            }
            default -> { // "Dashboard"
                content = new DashboardView(
                        currentUser,
                        repository,
                        this::navigateTo,
                        this::openReservationModal,
                        locationName -> {
                            if (appHeader != null) appHeader.setLocationDisplay(locationName);
                        }
                );
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

    private void setupScene(StackPane root, double width, double height) {
        scene = new Scene(root, width, height);
        applyActiveTheme();
        ThemeManager.themeProperty().addListener((obs, o, n) -> applyActiveTheme());
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
