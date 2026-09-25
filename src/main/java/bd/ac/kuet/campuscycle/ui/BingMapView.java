package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CycleItem;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

/**
 * Production-ready BingMapView:
 * Pure Microsoft Bing Maps Road imagery rendered inside JavaFX WebView.
 * Default centered directly on KUET campus with smooth mouse wheel zooming,
 * standard zoom controls, offline-bundled Leaflet core, and bi-directional JavaScript reflection bridge.
 */
public class BingMapView extends StackPane {

    private final WebView webView;
    private final WebEngine webEngine;

    private final Consumer<String> onHubSelected;
    private final Consumer<String> onLocationChanged;
    private boolean isLoaded = false;

    private final javafx.beans.value.ChangeListener<ThemeManager.Theme> themeListener;
    private final javafx.beans.value.ChangeListener<Worker.State> loadListener;

    public BingMapView(List<CycleItem> availableCycles,
                       Consumer<String> onHubSelected,
                       Consumer<String> onLocationChanged) {
        ThemeManager.install(this);
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
        webEngine.setOnError(event -> System.err.println("[BingMapView Error] " + event.getMessage()));
        webEngine.setOnAlert(event -> System.out.println("[BingMapView Alert] " + event.getData()));
        webView.setStyle("-fx-background-color: #F0EEEA;");

        // Responsive sizing
        webView.prefWidthProperty().bind(widthProperty());
        webView.prefHeightProperty().bind(heightProperty());

        // Mouse wheel scroll handler: Natural zoom in and zoom out of KUET
        webView.setOnScroll(event -> {
            if (event.getDeltaY() > 0) {
                webEngine.executeScript("if (window.map) window.map.zoomIn();");
            } else if (event.getDeltaY() < 0) {
                webEngine.executeScript("if (window.map) window.map.zoomOut();");
            }
            event.consume();
        });

        // Loading overlay
        VBox loadingOverlay = new VBox(10);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(10, 15, 29, 0.85); -fx-background-radius: 14px;");
        Label loadingLabel = new Label("Loading Microsoft Bing Road Map...");
        loadingLabel.setStyle("-fx-text-fill: #35BFAE; -fx-font-weight: 700; -fx-font-size: 13px;");
        loadingOverlay.getChildren().addAll(
                ThemeManager.createIcon(ThemeManager.ICON_PIN, 28, Color.web("#35BFAE")),
                loadingLabel
        );

        getChildren().addAll(webView, loadingOverlay);

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

    public void centerKuet() {
        if (isLoaded) {
            try {
                webEngine.executeScript("if (window.CampusMap && window.CampusMap.centerKuet) window.CampusMap.centerKuet();");
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
