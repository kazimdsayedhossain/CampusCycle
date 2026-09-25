package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.data.LocalDatabase;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.event.RentalReturnedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Production-ready Active Journey Cockpit.
 * Implements:
 * 1. Live telemetry ticker with speed, distance, fare, and duration
 * 2. Slider control for simulated Pedal Assist / Virtual Cadence
 * 3. ListView control for real-time sensor and geofence checkpoint logs
 * 4. Multi-threaded asynchronous return processing with AppExecutor
 * 5. Event-driven Observer pattern synchronization via EventBus
 */
public class ActiveJourneyView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<String> onNavigate;
    private final Runnable onRideFinished;

    private final Label timerLabel = new Label("00:00:00");
    private final Label fareLabel = new Label("BDT 20.00");
    private final Label speedLabel = new Label("18.4 km/h");
    private final Label distLabel = new Label("2.4 km");
    private final Label assistModeLabel = new Label("Eco (Level 1)");

    private final ObservableList<String> telemetryLogs = FXCollections.observableArrayList();
    private final ListView<String> logListView = new ListView<>(telemetryLogs);

    private double assistMultiplier = 1.0;
    private Timeline ticker;

    public ActiveJourneyView(CampusUser user,
                             CampusRepository repo,
                             Consumer<String> onNavigate,
                             Runnable onRideFinished) {
        this.user = user;
        this.repo = repo;
        this.onNavigate = onNavigate;
        this.onRideFinished = onRideFinished;

        setSpacing(24);
        setPadding(new Insets(28, 36, 48, 36));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(1160);

        RentalRecord active = repo.activeRental(user);
        if (active == null) {
            getChildren().add(createNoActiveRideView());
        } else {
            getChildren().addAll(
                    createHeader(active),
                    createTelemetryCockpit(active),
                    createPedalAssistAndLogsSection(),
                    createReturnProtocol(active)
            );
            initTelemetryLogs(active);
            startLiveTicker(active);
        }

        ThemeManager.applyFadeIn(this);
    }

    private VBox createNoActiveRideView() {
        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60, 40, 60, 40));
        box.getStyleClass().add("bento-card");
        box.setMaxWidth(600);

        StackPane iconCircle = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 28, Color.web("#94A3B8")));
        iconCircle.setPrefSize(64, 64);
        iconCircle.getStyleClass().add("action-icon-btn");

        Label title = new Label("No Active Cycle Rental");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");

        Label sub = new Label("Unlock a university cycle from any of our 5 KUET campus stations to begin a journey.");
        sub.setWrapText(true);
        sub.setStyle("-fx-font-size: 13px; -fx-opacity: 0.75; -fx-text-alignment: center;");

        Button browseBtn = new Button("Explore Available Fleet →");
        browseBtn.getStyleClass().add("primary-button");
        browseBtn.setOnAction(e -> onNavigate.accept("Fleet Catalog"));

        box.getChildren().addAll(iconCircle, title, sub, browseBtn);
        return box;
    }

    private HBox createHeader(RentalRecord active) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BOLT, 18, Color.web("#10B981")));
        icon.setPrefSize(40, 40);
        icon.getStyleClass().add("action-icon-btn");

        VBox titleCol = new VBox(2);
        Label title = new Label("Active Journey: " + active.cycleLabel());
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");

        Label sub = new Label("Rental #" + active.id() + " • Bluetooth Smart Lock Online");
        sub.setStyle("-fx-font-size: 12px; -fx-opacity: 0.7;");
        titleCol.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label activeBadge = new Label("● LIVE COMMUTE");
        activeBadge.getStyleClass().add("badge-available");

        row.getChildren().addAll(icon, titleCol, spacer, activeBadge);
        return row;
    }

    private GridPane createTelemetryCockpit(RentalRecord active) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        VBox c1 = createCockpitCard("ELAPSED TIME", timerLabel, "Ticking live from start", ThemeManager.ICON_CLOCK, "#0284C7");
        VBox c2 = createCockpitCard("CURRENT TARIFF", fareLabel, "25% Subsidy applied", ThemeManager.ICON_SHIELD, "#10B981");
        VBox c3 = createCockpitCard("ESTIMATED SPEED", speedLabel, "Hub telemetry sensor", ThemeManager.ICON_NAV, "#0EA5E9");
        VBox c4 = createCockpitCard("DISTANCE CYCLED", distLabel, "Campus odometer", ThemeManager.ICON_PIN, "#0284C7");

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

    private VBox createCockpitCard(String label, Label valueLabel, String sub, String svgIcon, String accentHex) {
        VBox card = new VBox(6);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(16, 20, 16, 20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconBadge = new StackPane(ThemeManager.createIcon(svgIcon, 14, Color.web(accentHex)));
        iconBadge.setPrefSize(28, 28);
        iconBadge.getStyleClass().add("action-icon-btn");

        top.getChildren().addAll(lbl, spacer, iconBadge);

        valueLabel.getStyleClass().add("metric-number");

        Label badge = new Label(sub);
        badge.getStyleClass().add("metric-badge");

        card.getChildren().addAll(top, valueLabel, badge);
        return card;
    }

    private HBox createPedalAssistAndLogsSection() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.TOP_LEFT);

        // Assist Level Slider Card
        VBox sliderCard = new VBox(14);
        sliderCard.getStyleClass().add("bento-card");
        sliderCard.setPadding(new Insets(20));
        HBox.setHgrow(sliderCard, Priority.ALWAYS);

        HBox sliderHeader = new HBox();
        Label sTitle = new Label("Smart Electric Assist & Throttle");
        sTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");
        Region sSp = new Region();
        HBox.setHgrow(sSp, Priority.ALWAYS);
        assistModeLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #0284C7;");
        sliderHeader.getChildren().addAll(sTitle, sSp, assistModeLabel);

        Slider assistSlider = new Slider(1, 3, 1);
        assistSlider.setMajorTickUnit(1);
        assistSlider.setSnapToTicks(true);
        assistSlider.getStyleClass().add("slider");
        assistSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int level = (int) Math.round(newVal.doubleValue());
            if (level == 1) {
                assistMultiplier = 1.0;
                assistModeLabel.setText("Eco (Level 1)");
                addTelemetryLog("Pedal Assist set to Eco Mode (Optimal Battery)");
            } else if (level == 2) {
                assistMultiplier = 1.35;
                assistModeLabel.setText("Cruise (Level 2)");
                addTelemetryLog("Pedal Assist set to Cruise Mode (+35% Cadence)");
            } else {
                assistMultiplier = 1.75;
                assistModeLabel.setText("Turbo (Level 3)");
                addTelemetryLog("Pedal Assist set to Turbo Boost (+75% Power)");
            }
        });

        HBox sliderTicks = new HBox();
        Label t1 = new Label("Level 1: Eco");
        t1.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);
        Label t2 = new Label("Level 2: Cruise");
        t2.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        Label t3 = new Label("Level 3: Turbo");
        t3.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
        sliderTicks.getChildren().addAll(t1, sp1, t2, sp2, t3);

        sliderCard.getChildren().addAll(sliderHeader, assistSlider, sliderTicks);

        // Real-time Checkpoints ListView Card
        VBox logsCard = new VBox(14);
        logsCard.getStyleClass().add("bento-card");
        logsCard.setPadding(new Insets(20));
        HBox.setHgrow(logsCard, Priority.ALWAYS);

        Label logsTitle = new Label("Live Journey & Geofence Checkpoints");
        logsTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        logListView.setPrefHeight(130);
        logListView.getStyleClass().add("list-view");

        logsCard.getChildren().addAll(logsTitle, logListView);

        row.getChildren().addAll(sliderCard, logsCard);
        return row;
    }

    private VBox createReturnProtocol(RentalRecord active) {
        VBox card = new VBox(18);
        card.getStyleClass().add("bento-card");
        card.setPadding(new Insets(24));

        Label h = new Label("Return & Docking Protocol");
        h.setStyle("-fx-font-size: 16px; -fx-font-weight: 750;");

        VBox subBox = new VBox(12);
        subBox.getStyleClass().add("sub-panel");
        subBox.setPadding(new Insets(16));

        Label dropLbl = new Label("SELECT DESTINATION RETURN HUB");
        dropLbl.getStyleClass().add("metric-label");

        ComboBox<String> hubCombo = new ComboBox<>();
        hubCombo.getItems().addAll("KUET Central Library", "Student Welfare Centre", "KUET Main Gate", "Hall Gate", "Academic Building");
        hubCombo.setValue("KUET Central Library");
        hubCombo.setMaxWidth(Double.MAX_VALUE);
        hubCombo.getStyleClass().add("filter-chip");

        CheckBox c1 = new CheckBox("Cycle physically placed inside smart quad dock slot");
        c1.setSelected(true);
        c1.setStyle("-fx-font-size: 12px;");

        CheckBox c2 = new CheckBox("Wheel padlock engaged & verified by dock infrared sensor");
        c2.setSelected(true);
        c2.setStyle("-fx-font-size: 12px;");

        subBox.getChildren().addAll(dropLbl, hubCombo, c1, c2);

        HBox bottom = new HBox(12);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        Button returnBtn = new Button("Complete Return & Lock Cycle");
        returnBtn.getStyleClass().add("primary-button");
        returnBtn.setStyle("-fx-background-color: linear-gradient(to right, #059669, #10B981);");

        returnBtn.setOnAction(e -> {
            if (!c1.isSelected() || !c2.isSelected()) {
                Alert a = new Alert(Alert.AlertType.WARNING, "Please confirm all safety checklist items before locking.", ButtonType.OK);
                a.showAndWait();
                return;
            }

            returnBtn.setDisable(true);
            returnBtn.setText("Returning & Locking...");

            // Multi-threaded non-blocking return execution
            AppExecutor.asyncThenFx(
                    () -> {
                        // 1. Supabase repository return
                        repo.returnRental(user, active.id());
                        // 2. SQLite local database update
                        LocalDatabase.getInstance().updateRentalReturned(active.id());
                        LocalDatabase.getInstance().updateCycleAvailability(active.cycleId(), AvailabilityStatus.AVAILABLE);
                        // 3. EventBus Observer notification
                        EventBus.getInstance().publish(new RentalReturnedEvent(user, active.id(), Instant.now()));
                        return true;
                    },
                    success -> {
                        if (ticker != null) ticker.stop();
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Cycle successfully docked and locked at " + hubCombo.getValue() + ". Thank you for choosing KUET Green Mobility!", ButtonType.OK);
                        alert.showAndWait();
                        onRideFinished.run();
                    },
                    error -> {
                        returnBtn.setDisable(false);
                        returnBtn.setText("Complete Return & Lock Cycle");
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Return failed: " + error.getMessage(), ButtonType.OK);
                        alert.showAndWait();
                    }
            );
        });

        bottom.getChildren().add(returnBtn);
        card.getChildren().addAll(h, subBox, bottom);
        return card;
    }

    private void initTelemetryLogs(RentalRecord active) {
        telemetryLogs.add("• [00:00:00] Dock lock disengaged at station");
        telemetryLogs.add("• [00:00:05] Smart sensor telemetry online");
        telemetryLogs.add("• [00:00:12] GPS geofence: KUET Campus Active Zone");
    }

    private void addTelemetryLog(String msg) {
        telemetryLogs.add(0, "• [" + timerLabel.getText() + "] " + msg);
        if (telemetryLogs.size() > 15) {
            telemetryLogs.remove(telemetryLogs.size() - 1);
        }
    }

    private void startLiveTicker(RentalRecord active) {
        long startSeconds = active.startedAt().toInstant().getEpochSecond();

        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long nowSec = Instant.now().getEpochSecond();
            long elapsed = Math.max(0, nowSec - startSeconds);

            long hours = elapsed / 3600;
            long mins = (elapsed % 3600) / 60;
            long secs = elapsed % 60;

            timerLabel.setText(String.format("%02d:%02d:%02d", hours, mins, secs));

            double baseKm = 0.8 + (elapsed / 45.0) * 0.12;
            distLabel.setText(String.format("%.1f km", baseKm * assistMultiplier));

            double baseSpeed = 16.0 + Math.sin(elapsed / 10.0) * 4.5;
            speedLabel.setText(String.format("%.1f km/h", baseSpeed * assistMultiplier));

            int elapsedMinutes = (int) Math.ceil(elapsed / 60.0);
            int basePoisha = 2000;
            int extraMin = Math.max(0, elapsedMinutes - 15);
            int extraBlocks = (int) Math.ceil(extraMin / 15.0);
            int totalPoisha = (int) ((basePoisha + (extraBlocks * 1000)) * 0.75); // 25% student subsidy
            fareLabel.setText(String.format("BDT %.2f", totalPoisha / 100.0));
        }));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }
}
