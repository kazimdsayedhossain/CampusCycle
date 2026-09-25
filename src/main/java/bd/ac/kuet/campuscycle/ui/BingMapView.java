package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CycleItem;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

/**
 * Production-ready BingMapView:
 * Pure Microsoft Bing Maps Virtual Earth Satellite (Hybrid) & Road imagery
 * rendered inside JavaFX WebView with automatic sizing synchronization,
 * offline-bundled Leaflet core, and bi-directional JavaScript reflection bridge.
 */
public class BingMapView extends StackPane {

    private final WebView webView;
    private final WebEngine webEngine;

    private final Consumer<String> onHubSelected;
    private final Consumer<String> onLocationChanged;
    private boolean isLoaded = false;

    private final javafx.beans.value.ChangeListener<ThemeManager.Theme> themeListener;
    private final javafx.beans.value.ChangeListener<Worker.State> loadListener;

    private final Button btnBingSatellite = new Button("🛰️ Bing Satellite");
    private final Button btnBingRoad = new Button("🗺️ Bing Road");
    private final Button btnCenterKuet = new Button("📍 KUET");
    private final Button btnCenterKhulna = new Button("🏙️ Khulna");

    public BingMapView(List<CycleItem> availableCycles,
                       Consumer<String> onHubSelected,
                       Consumer<String> onLocationChanged) {
        this.onHubSelected = onHubSelected;
        this.onLocationChanged = onLocationChanged;

        setPrefSize(980, 520);
        setMinHeight(460);
        getStyleClass().add("map-canvas-card");

        // Microsoft Bing Maps WebView
        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 CampusCycle/1.0");
        webView.setStyle("-fx-background-color: transparent;");

        // Responsive sizing
        webView.prefWidthProperty().bind(widthProperty());
        webView.prefHeightProperty().bind(heightProperty());

        // Loading overlay
        VBox loadingOverlay = new VBox(10);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(10, 15, 29, 0.85); -fx-background-radius: 14px;");
        Label loadingLabel = new Label("Initializing Microsoft Bing Satellite Telemetry...");
        loadingLabel.setStyle("-fx-text-fill: #0EA5E9; -fx-font-weight: 700; -fx-font-size: 13px;");
        loadingOverlay.getChildren().addAll(
                ThemeManager.createIcon(ThemeManager.ICON_PIN, 28, Color.web("#0EA5E9")),
                loadingLabel
        );

        // Top-right layer pills
        HBox layerPills = createLayerPills();
        StackPane.setAlignment(layerPills, Pos.TOP_RIGHT);
        StackPane.setMargin(layerPills, new Insets(14, 14, 0, 0));

        getChildren().addAll(webView, loadingOverlay, layerPills);

        // Invalidate map size on layout changes
        widthProperty().addListener((obs, oldVal, newVal) -> triggerInvalidateSize());
        heightProperty().addListener((obs, oldVal, newVal) -> triggerInvalidateSize());

        // Load the HTML
        URL mapUrl = getClass().getResource("/bd/ac/kuet/campuscycle/bing-map.html");
        if (mapUrl != null) {
            webEngine.load(mapUrl.toExternalForm());
        }

        loadListener = (obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                isLoaded = true;
                loadingOverlay.setVisible(false);

                // Register Bridge
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("campusBridge", new CampusBridge());

                    // Apply current theme + push live cycle count
                    updateMapTheme();
                    pushCycles(availableCycles);
                    triggerInvalidateSize();
                } catch (Exception e) {
                    System.err.println("Note: Map bridge registration note: " + e.getMessage());
                }
            } else if (newState == Worker.State.FAILED) {
                loadingOverlay.setVisible(false);
                loadingLabel.setText("Bing Map could not reach tile network.");
                loadingLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: 700;");
            }
        };
        webEngine.getLoadWorker().stateProperty().addListener(loadListener);

        // React to application theme changes
        themeListener = (obs, o, n) -> {
            if (isLoaded) {
                Platform.runLater(this::updateMapTheme);
            }
        };
        ThemeManager.themeProperty().addListener(themeListener);
    }

    private HBox createLayerPills() {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-background-radius: 20px; -fx-padding: 4px 10px; -fx-border-color: rgba(255,255,255,0.12); -fx-border-radius: 20px;");

        btnBingSatellite.getStyleClass().add("filter-chip-active");
        btnBingSatellite.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        btnBingSatellite.setTooltip(new Tooltip("High-Resolution Microsoft Bing Hybrid Satellite Imagery"));
        btnBingSatellite.setOnAction(e -> {
            setSatelliteActive(true);
            if (isLoaded) webEngine.executeScript("switchLayer('satellite')");
        });

        btnBingRoad.getStyleClass().add("filter-chip");
        btnBingRoad.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        btnBingRoad.setTooltip(new Tooltip("Microsoft Bing Road & Street Navigation Map"));
        btnBingRoad.setOnAction(e -> {
            setSatelliteActive(false);
            if (isLoaded) webEngine.executeScript("switchLayer('road')");
        });

        btnCenterKuet.getStyleClass().add("filter-chip");
        btnCenterKuet.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        btnCenterKuet.setTooltip(new Tooltip("Center view on KUET campus"));
        btnCenterKuet.setOnAction(e -> {
            if (isLoaded) webEngine.executeScript("centerKuet()");
        });

        btnCenterKhulna.getStyleClass().add("filter-chip");
        btnCenterKhulna.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        btnCenterKhulna.setTooltip(new Tooltip("View Khulna metropolitan roaming area"));
        btnCenterKhulna.setOnAction(e -> {
            if (isLoaded) webEngine.executeScript("centerKhulna()");
        });

        box.getChildren().addAll(btnBingSatellite, btnBingRoad, btnCenterKuet, btnCenterKhulna);
        return box;
    }

    private void setSatelliteActive(boolean satellite) {
        if (satellite) {
            btnBingSatellite.getStyleClass().remove("filter-chip");
            btnBingSatellite.getStyleClass().add("filter-chip-active");
            btnBingRoad.getStyleClass().remove("filter-chip-active");
            btnBingRoad.getStyleClass().add("filter-chip");
        } else {
            btnBingRoad.getStyleClass().remove("filter-chip");
            btnBingRoad.getStyleClass().add("filter-chip-active");
            btnBingSatellite.getStyleClass().remove("filter-chip-active");
            btnBingSatellite.getStyleClass().add("filter-chip");
        }
    }

    private void triggerInvalidateSize() {
        if (!isLoaded) return;
        Platform.runLater(() -> {
            try {
                webEngine.executeScript("if (window.CampusMap && window.CampusMap.invalidateSize) window.CampusMap.invalidateSize();");
            } catch (Exception ignored) {
            }
        });
    }

    /** Push live approved/available cycles to JS. */
    public void pushCycles(List<CycleItem> cycles) {
        if (!isLoaded || cycles == null) return;
        try {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < cycles.size(); i++) {
                CycleItem c = cycles.get(i);
                if (i > 0) json.append(",");
                json.append(String.format("{\"id\":\"%s\",\"label\":\"%s\",\"lat\":%f,\"lng\":%f,\"hub\":\"%s\"}",
                        c.id().replace("\"", ""), c.label().replace("\"", "'"),
                        c.latitude(), c.longitude(), c.pickupPoint().replace("\"", "'")));
            }
            json.append("]");
            String payload = json.toString().replace("'", "\\'");
            webEngine.executeScript("if (window.CampusMap && window.CampusMap.setCycles) window.CampusMap.setCycles('" + payload + "');");
        } catch (Exception ignored) {
        }
    }

    public void focusLocation(double lat, double lng) {
        if (isLoaded) {
            try {
                webEngine.executeScript(String.format("if (window.CampusMap && window.CampusMap.focusLocation) window.CampusMap.focusLocation(%f, %f);", lat, lng));
            } catch (Exception ignored) {}
        }
    }

    public void setFocusLocation(double lat, double lng) {
        if (!isLoaded) return;
        try {
            webEngine.executeScript(String.format("if (window.CampusMap && window.CampusMap.setUserLocation) window.CampusMap.setUserLocation(%f, %f);", lat, lng));
        } catch (Exception ignored) {}
    }

    private void updateMapTheme() {
        if (!isLoaded) return;
        try {
            String themeMode = ThemeManager.isDark() ? "DARK" : "LIGHT";
            webEngine.executeScript("if (window.CampusMap) window.CampusMap.setTheme('" + themeMode + "');");
        } catch (Exception ignored) {
        }
    }

    /** Release WebView + listeners to prevent leaks on navigation. */
    public void dispose() {
        try {
            webEngine.getLoadWorker().stateProperty().removeListener(loadListener);
            ThemeManager.themeProperty().removeListener(themeListener);
            webEngine.load(null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Bridge class exposed to JavaScript in WebView
     */
    public class CampusBridge {

        public void onLocationSelected(double lat, double lng, String nearestHub, int distanceMeters) {
            Platform.runLater(() -> {
                String desc = String.format("%s (%dm away)", nearestHub, distanceMeters);
                if (onLocationChanged != null) {
                    onLocationChanged.accept(desc);
                }
            });
        }

        public void onHubSelected(String hubName) {
            Platform.runLater(() -> {
                if (onHubSelected != null) {
                    onHubSelected.accept(hubName);
                }
            });
        }
    }
}
