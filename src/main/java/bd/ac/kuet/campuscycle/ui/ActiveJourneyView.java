package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.Instant;
import java.util.function.Consumer;

public class ActiveJourneyView extends VBox {

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<String> onNavigate;
    private final Runnable onRideFinished;

    private final Label timerLabel = new Label("00:00:00");
    private final Label fareLabel = new Label("BDT 20.00");
    private final Label speedLabel = new Label("18.4 km/h");
    private final Label distLabel = new Label("2.4 km");
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
                    createReturnProtocol(active)
            );
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
        VBox c2 = createCockpitCard("CURRENT TARIFF", fareLabel, "Subsidy applied", ThemeManager.ICON_SHIELD, "#10B981");
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

        HBox bottom = new HBox();
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
            if (ticker != null) ticker.stop();
            repo.returnRental(user, active.id());
            onRideFinished.run();
        });

        bottom.getChildren().add(returnBtn);
        card.getChildren().addAll(h, subBox, bottom);
        return card;
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

            double km = 0.8 + (elapsed / 45.0) * 0.12;
            distLabel.setText(String.format("%.1f km", km));

            double speed = 16.0 + Math.sin(elapsed / 10.0) * 4.5;
            speedLabel.setText(String.format("%.1f km/h", speed));

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
