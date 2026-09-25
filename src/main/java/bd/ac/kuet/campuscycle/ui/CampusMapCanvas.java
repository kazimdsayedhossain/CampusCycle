package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * High-precision interactive vector campus map of KUET.
 * Computes Euclidean distances to 5 official hubs, highlights the nearest hub,
 * draws dashed navigation paths, and provides real-time walking time estimates.
 */
public class CampusMapCanvas extends StackPane {

    public record HubLocation(String id, String name, double x, double y, String latLon, int capacity) {}

    public static final List<HubLocation> HUBS = List.of(
            new HubLocation("hub-1", "KUET Main Gate", 170, 370, "22.8987° N, 89.4981° E", 24),
            new HubLocation("hub-2", "KUET Central Library", 490, 210, "22.9009° N, 89.5016° E", 32),
            new HubLocation("hub-3", "Student Welfare Centre", 350, 180, "22.9017° N, 89.5030° E", 28),
            new HubLocation("hub-4", "Hall Gate", 740, 140, "22.9045° N, 89.5060° E", 18),
            new HubLocation("hub-5", "Academic Building", 310, 310, "22.9015° N, 89.5010° E", 20)
    );

    private final Canvas canvas = new Canvas(920, 440);
    private final List<CycleItem> cycles;
    private final Consumer<String> onHubSelected;
    private final Consumer<String> onLocationChanged;

    private double userPinX = 420;
    private double userPinY = 260;
    private String userLocationName = "Central Field";
    private HubLocation nearestHub = HUBS.get(1);
    private double nearestDistanceMeters = 180;
    private double pulsePhase = 0;
    private final Timeline pulseTimeline;
    private final javafx.beans.value.ChangeListener<ThemeManager.Theme> themeListener;

    private final Label nearestLabel = new Label();
    private final Button exploreHubBtn = new Button("View Cycles Here");

    public CampusMapCanvas(List<CycleItem> cycles,
                           Consumer<String> onHubSelected,
                           Consumer<String> onLocationChanged) {
        this.cycles = cycles;
        this.onHubSelected = onHubSelected;
        this.onLocationChanged = onLocationChanged;

        setMaxWidth(960);
        setPrefSize(920, 440);
        getStyleClass().add("bento-card");

        setupCanvasInteractivity();

        pulseTimeline = new Timeline(new KeyFrame(Duration.millis(800), e -> {
            pulsePhase = (pulsePhase + 0.5) % (Math.PI * 2);
            renderMap();
        }));
        pulseTimeline.setCycleCount(Animation.INDEFINITE);
        pulseTimeline.play();

        recalculateNearestHub();

        VBox overlay = createMapOverlay();
        getChildren().addAll(canvas, overlay);

        themeListener = (obs, o, n) -> renderMap();
        ThemeManager.themeProperty().addListener(themeListener);
    }

    /** Stop background redraws; call when view is removed. */
    public void dispose() {
        try {
            pulseTimeline.stop();
            ThemeManager.themeProperty().removeListener(themeListener);
        } catch (Exception ignored) {
        }
    }

    private void setupCanvasInteractivity() {
        canvas.setOnMouseClicked(this::handleMapClick);
        canvas.setOnMouseDragged(this::handleMapClick);
    }

    private void handleMapClick(MouseEvent e) {
        userPinX = Math.max(40, Math.min(canvas.getWidth() - 40, e.getX()));
        userPinY = Math.max(40, Math.min(canvas.getHeight() - 40, e.getY()));
        userLocationName = String.format("KUET (%.0f, %.0f)", userPinX, userPinY);

        for (HubLocation hub : HUBS) {
            double dist = Math.hypot(e.getX() - hub.x, e.getY() - hub.y);
            if (dist < 28) {
                userPinX = hub.x;
                userPinY = hub.y;
                userLocationName = hub.name;
                if (onHubSelected != null) {
                    onHubSelected.accept(hub.name);
                }
                break;
            }
        }

        recalculateNearestHub();
        renderMap();

        if (onLocationChanged != null) {
            onLocationChanged.accept(userLocationName);
        }
    }

    public void setPresetLocation(String locationName, double x, double y) {
        this.userLocationName = locationName;
        this.userPinX = x;
        this.userPinY = y;
        recalculateNearestHub();
        renderMap();
        if (onLocationChanged != null) {
            onLocationChanged.accept(locationName);
        }
    }

    private void recalculateNearestHub() {
        HubLocation best = HUBS.get(0);
        double minDistance = Double.MAX_VALUE;

        for (HubLocation hub : HUBS) {
            double d = Math.hypot((userPinX - hub.x) * 1.8, (userPinY - hub.y) * 1.8);
            if (d < minDistance) {
                minDistance = d;
                best = hub;
            }
        }

        nearestHub = best;
        nearestDistanceMeters = Math.max(25, Math.round(minDistance));

        int walkMin = Math.max(1, (int) Math.round(nearestDistanceMeters / 75.0));
        long count = cycles.stream()
                .filter(c -> c.pickupPoint().equalsIgnoreCase(nearestHub.name) && c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .count();

        nearestLabel.setText(String.format("Nearest Station: %s (%.0fm away, ~%d min walk) - %d cycles ready",
                nearestHub.name, nearestDistanceMeters, walkMin, count));
    }

    private VBox createMapOverlay() {
        VBox root = new VBox();
        root.setPickOnBounds(false);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.TOP_LEFT);

        // Top Chip Bar
        HBox topChips = new HBox(8);
        topChips.setAlignment(Pos.CENTER_LEFT);

        Label quickLbl = new Label("QUICK LOCATE:");
        quickLbl.getStyleClass().add("metric-label");
        quickLbl.setStyle("-fx-font-size: 10px; -fx-padding: 0 4px 0 0;");

        Button bCentral = createPresetChip("Central Field", 420, 260);
        Button bMinar = createPresetChip("Shahid Minar", 260, 240);
        Button bAuditorium = createPresetChip("Auditorium", 540, 290);
        Button bMech = createPresetChip("Mechanical Dept", 320, 320);

        topChips.getChildren().addAll(quickLbl, bCentral, bMinar, bAuditorium, bMech);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Bottom HUD Card
        HBox hud = new HBox(12);
        hud.getStyleClass().add("sub-panel");
        hud.setPadding(new Insets(10, 16, 10, 16));
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setMaxWidth(Double.MAX_VALUE);

        nearestLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 750;");
        HBox.setHgrow(nearestLabel, Priority.ALWAYS);

        exploreHubBtn.getStyleClass().add("primary-button");
        exploreHubBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 6px 14px;");
        exploreHubBtn.setOnAction(e -> {
            if (onHubSelected != null) {
                onHubSelected.accept(nearestHub.name);
            }
        });

        hud.getChildren().addAll(nearestLabel, exploreHubBtn);

        root.getChildren().addAll(topChips, spacer, hud);
        return root;
    }

    private Button createPresetChip(String name, double x, double y) {
        Button btn = new Button(name);
        btn.getStyleClass().add("filter-chip");
        btn.setStyle("-fx-font-size: 10.5px; -fx-padding: 4px 10px;");
        btn.setOnAction(e -> setPresetLocation(name, x, y));
        return btn;
    }

    private void renderMap() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        boolean dark = ThemeManager.isDark();

        // 1. Background
        gc.setFill(dark ? Color.web("#0A0F1D") : Color.web("#F4F6F9"));
        gc.fillRect(0, 0, w, h);

        // Grid dots
        gc.setFill(dark ? Color.web("#1E293B", 0.5) : Color.web("#CBD5E1", 0.6));
        for (double x = 20; x < w; x += 32) {
            for (double y = 20; y < h; y += 32) {
                gc.fillOval(x - 1, y - 1, 2, 2);
            }
        }

        // 2. Campus Roads & Avenues
        gc.setStroke(dark ? Color.web("#1E293B") : Color.web("#E2E8F0"));
        gc.setLineWidth(14);
        gc.strokeLine(120, 390, 800, 120); // Main Avenue
        gc.strokeLine(160, 370, 320, 160); // West Avenue
        gc.strokeLine(300, 380, 520, 200); // Library Way
        gc.strokeLine(480, 210, 750, 150); // North Link
        gc.strokeLine(310, 310, 480, 210); // Academic Cross

        gc.setStroke(dark ? Color.web("#334155") : Color.web("#FFFFFF"));
        gc.setLineWidth(8);
        gc.strokeLine(120, 390, 800, 120);
        gc.strokeLine(160, 370, 320, 160);
        gc.strokeLine(300, 380, 520, 200);
        gc.strokeLine(480, 210, 750, 150);
        gc.strokeLine(310, 310, 480, 210);

        // 3. KUET Central Pond / Water Body
        gc.setFill(dark ? Color.web("#0369A1", 0.25) : Color.web("#BAE6FD", 0.45));
        gc.fillRoundRect(560, 240, 140, 80, 30, 30);
        gc.setFill(dark ? Color.web("#38BDF8") : Color.web("#0284C7"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        gc.fillText("KUET Lake", 605, 285);

        // 4. Central Playground / Field
        gc.setFill(dark ? Color.web("#064E3B", 0.25) : Color.web("#D1FAE5", 0.45));
        gc.fillRoundRect(380, 220, 130, 85, 20, 20);
        gc.setFill(dark ? Color.web("#34D399") : Color.web("#059669"));
        gc.fillText("Central Field", 415, 265);

        // 5. Buildings
        renderBuilding(gc, 230, 220, 70, 45, "Auditorium", dark);
        renderBuilding(gc, 270, 290, 80, 50, "Academic Complex", dark);
        renderBuilding(gc, 450, 160, 90, 40, "Central Library", dark);
        renderBuilding(gc, 320, 140, 75, 35, "SWC", dark);
        renderBuilding(gc, 700, 100, 85, 40, "Halls Area", dark);

        // 6. Navigation Dashed Guide Line from User Pin to Nearest Hub
        gc.setStroke(dark ? Color.web("#38BDF8") : Color.web("#0284C7"));
        gc.setLineWidth(2.5);
        gc.setLineDashes(6, 6);
        gc.strokeLine(userPinX, userPinY, nearestHub.x, nearestHub.y);
        gc.setLineDashes(null);

        // 7. Render 5 Official Hubs
        for (HubLocation hub : HUBS) {
            boolean isNearest = (hub == nearestHub);
            long availableCycles = cycles.stream()
                    .filter(c -> c.pickupPoint().equalsIgnoreCase(hub.name) && c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                    .count();

            // Pulsing highlight ring on nearest hub
            if (isNearest) {
                double pulseRadius = 24 + Math.sin(pulsePhase) * 6;
                gc.setFill(dark ? Color.web("#38BDF8", 0.2) : Color.web("#0284C7", 0.2));
                gc.fillOval(hub.x - pulseRadius, hub.y - pulseRadius, pulseRadius * 2, pulseRadius * 2);
            }

            // Hub Outer Circle
            gc.setFill(dark ? Color.web("#1E293B") : Color.web("#FFFFFF"));
            gc.fillOval(hub.x - 16, hub.y - 16, 32, 32);

            // Hub Inner Accent
            Color hubAccent = availableCycles > 0
                    ? (dark ? Color.web("#0EA5E9") : Color.web("#0284C7"))
                    : (dark ? Color.web("#64748B") : Color.web("#94A3B8"));
            gc.setFill(hubAccent);
            gc.fillOval(hub.x - 11, hub.y - 11, 22, 22);

            // Cycle count badge
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 10));
            String countStr = String.valueOf(availableCycles);
            gc.fillText(countStr, hub.x - (countStr.length() * 3), hub.y + 3.5);

            // Hub Label
            gc.setFill(dark ? Color.web("#F1F5F9") : Color.web("#0F172A"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            gc.fillText(hub.name, hub.x - 30, hub.y + 26);
        }

        // 8. User "You Are Here" Pin
        double pinR = 9;
        // Outer glow
        gc.setFill(Color.web("#2563EB", 0.35));
        gc.fillOval(userPinX - 16, userPinY - 16, 32, 32);

        // Core Pin
        gc.setFill(Color.web("#2563EB"));
        gc.fillOval(userPinX - pinR, userPinY - pinR, pinR * 2, pinR * 2);
        gc.setFill(Color.WHITE);
        gc.fillOval(userPinX - 3.5, userPinY - 3.5, 7, 7);

        // Tooltip callout pill over user pin
        gc.setFill(dark ? Color.web("#1E293B") : Color.web("#0F172A"));
        gc.fillRoundRect(userPinX - 44, userPinY - 34, 88, 20, 10, 10);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        gc.fillText("YOU ARE HERE", userPinX - 34, userPinY - 20);
    }

    private void renderBuilding(GraphicsContext gc, double x, double y, double w, double h, String label, boolean dark) {
        gc.setFill(dark ? Color.web("#162032") : Color.web("#E2E8F0"));
        gc.fillRoundRect(x, y, w, h, 6, 6);
        gc.setStroke(dark ? Color.web("#1F2937") : Color.web("#CBD5E1"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 6, 6);

        gc.setFill(dark ? Color.web("#94A3B8") : Color.web("#64748B"));
        gc.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 9.5));
        gc.fillText(label, x + 6, y + 18);
    }
}
