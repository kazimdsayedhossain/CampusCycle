package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Production-ready BingMapView:
 * Pure Microsoft Bing Maps Road imagery rendered inside JavaFX WebView.
 * Default centered directly on KUET campus with smooth mouse wheel zooming,
 * standard zoom controls, offline-bundled Leaflet core, and bi-directional JavaScript reflection bridge.
 */
public class BingMapView extends StackPane {

    private static final Logger LOGGER = Logger.getLogger(BingMapView.class.getName());

    private final WebView webView;
    private final WebEngine webEngine;
    private final Button locateBtn;

    private final Consumer<String> onHubSelected;
    private final Consumer<String> onLocationChanged;
    private boolean isLoaded = false;
    private volatile boolean disposed = false;

    private final javafx.beans.value.ChangeListener<ThemeManager.Theme> themeListener;
    private final javafx.beans.value.ChangeListener<Worker.State> loadListener;
    private final javafx.beans.value.ChangeListener<Number> widthListener;
    private final javafx.beans.value.ChangeListener<Number> heightListener;

    public BingMapView(List<CycleItem> availableCycles,
                       Consumer<String> onHubSelected,
                       Consumer<String> onLocationChanged) {
        ThemeManager.install(this);
        this.onHubSelected = onHubSelected;
        this.onLocationChanged = onLocationChanged;

        setPrefSize(600, 520);
        setMinWidth(0);
        setMinHeight(460);
        getStyleClass().add("map-canvas-card");

        // Microsoft Bing Maps WebView
        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 CampusCycle/1.0");
        webEngine.setOnError(event -> LOGGER.log(Level.WARNING, "[BingMapView Error] {0}", event.getMessage()));
        webEngine.setOnAlert(event -> LOGGER.log(Level.FINE, "[BingMapView Alert] {0}", event.getData()));
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
        Label loadingLabel = new Label("Loading OpenStreetMap...");
        loadingLabel.setStyle("-fx-text-fill: #10B981; -fx-font-weight: 700; -fx-font-size: 13px;");
        loadingOverlay.getChildren().addAll(
                ThemeManager.createIcon(ThemeManager.ICON_PIN, 28, Color.web("#10B981")),
                loadingLabel
        );

        // Locate Me floating action button (Google Maps style bottom right)
        locateBtn = new Button();
        updateLocateBtnStyle();
        locateBtn.setTooltip(new Tooltip("Locate my position on campus"));
        locateBtn.setOnAction(e -> locateMe());
        StackPane.setAlignment(locateBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(locateBtn, new Insets(0, 20, 24, 0));

        getChildren().addAll(webView, loadingOverlay, locateBtn);

        // Invalidate map size on layout changes (refs kept so dispose() can remove them)
        widthListener = (obs, oldVal, newVal) -> triggerInvalidateSize();
        heightListener = (obs, oldVal, newVal) -> triggerInvalidateSize();
        widthProperty().addListener(widthListener);
        heightProperty().addListener(heightListener);

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
                    LOGGER.log(Level.WARNING, "Map bridge registration failed", e);
                }
            } else if (newState == Worker.State.FAILED) {
                LOGGER.log(Level.WARNING, "Map tile network failed: {0}", webEngine.getLoadWorker().getMessage());
                loadingOverlay.setVisible(false);
                loadingLabel.setText("OpenStreetMap could not reach tile network.");
                loadingLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: 700;");
            }
        };
        webEngine.getLoadWorker().stateProperty().addListener(loadListener);

        // React to application theme changes
        themeListener = (obs, o, n) -> {
            Platform.runLater(() -> {
                updateLocateBtnStyle();
                if (isLoaded) {
                    updateMapTheme();
                }
            });
        };
        ThemeManager.themeProperty().addListener(themeListener);
    }

    private void updateLocateBtnStyle() {
        if (locateBtn == null) return;
        boolean isDark = ThemeManager.isDark();
        Color iconColor = isDark ? Color.web("#34D399") : Color.web("#0A3D36");
        locateBtn.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_GPS, 20, iconColor));
        String baseStyle = (isDark
                ? "-fx-background-color: rgba(30, 41, 59, 0.95); -fx-border-color: rgba(255, 255, 255, 0.16); "
                : "-fx-background-color: rgba(255, 255, 255, 0.96); -fx-border-color: rgba(0, 0, 0, 0.08); ")
                + "-fx-border-radius: 999px; -fx-background-radius: 999px; -fx-border-width: 1px; "
                + "-fx-pref-width: 44px; -fx-pref-height: 44px; -fx-min-width: 44px; -fx-min-height: 44px; "
                + "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.25), 14, 0, 0, 3);";
        locateBtn.setStyle(baseStyle);

        locateBtn.setOnMouseEntered(e -> {
            locateBtn.setStyle((isDark
                    ? "-fx-background-color: #1E293B; -fx-border-color: #10B981; "
                    : "-fx-background-color: #ECFDF5; -fx-border-color: #10B981; ")
                    + "-fx-border-radius: 999px; -fx-background-radius: 999px; -fx-border-width: 1.5px; "
                    + "-fx-pref-width: 44px; -fx-pref-height: 44px; -fx-min-width: 44px; -fx-min-height: 44px; "
                    + "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(16, 185, 129, 0.35), 16, 0, 0, 4);");
        });
        locateBtn.setOnMouseExited(e -> locateBtn.setStyle(baseStyle));
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
        if (disposed || !isLoaded || cycles == null) return;
        try {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < cycles.size(); i++) {
                CycleItem c = cycles.get(i);
                if (i > 0) json.append(",");
                json.append(String.format(Locale.US, "{\"id\":\"%s\",\"label\":\"%s\",\"lat\":%f,\"lng\":%f,\"hub\":\"%s\"}",
                        c.id().replace("\"", ""), c.label().replace("\"", "'"),
                        c.latitude(), c.longitude(), c.pickupPoint().replace("\"", "'")));
            }
            json.append("]");
            String payload = json.toString().replace("'", "\\'");
            webEngine.executeScript("if (window.CampusMap && window.CampusMap.setCycles) window.CampusMap.setCycles('" + payload + "');");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to push cycles to map", e);
        }
    }

    public void focusLocation(double lat, double lng) {
        if (disposed || !isLoaded) return;
        try {
            webEngine.executeScript(String.format(Locale.US, "if (window.CampusMap && window.CampusMap.focusLocation) window.CampusMap.focusLocation(%f, %f);", lat, lng));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Map focusLocation failed", e);
        }
    }

    public void centerKuet() {
        if (disposed || !isLoaded) return;
        try {
            webEngine.executeScript("if (window.CampusMap && window.CampusMap.centerKuet) window.CampusMap.centerKuet();");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Map centerKuet failed", e);
        }
    }

    public void zoomIn() {
        if (disposed || !isLoaded) return;
        try {
            webEngine.executeScript("if (window.CampusMap && window.CampusMap.zoomIn) window.CampusMap.zoomIn();");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Map zoomIn failed", e);
        }
    }

    public void zoomOut() {
        if (disposed || !isLoaded) return;
        try {
            webEngine.executeScript("if (window.CampusMap && window.CampusMap.zoomOut) window.CampusMap.zoomOut();");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Map zoomOut failed", e);
        }
    }

    public void locateMe() {
        if (disposed || !isLoaded) return;

        // Visual click feedback bounce
        if (locateBtn != null) {
            locateBtn.setScaleX(0.85);
            locateBtn.setScaleY(0.85);
            var st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(180), locateBtn);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        }

        // Fetch live user coordinates via IP or fallback to KUET campus center
        AppExecutor.runAsync(() -> {
            double[] coords = new double[]{22.9009, 89.5016};
            try {
                var client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofMillis(1500))
                        .build();
                var req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://ip-api.com/json"))
                        .timeout(java.time.Duration.ofMillis(1500))
                        .GET()
                        .build();
                var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String b = resp.body();
                    double lat = extractJsonDouble(b, "lat");
                    double lon = extractJsonDouble(b, "lon");
                    if (lat != 0.0 && lon != 0.0) {
                        coords = new double[]{lat, lon};
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "IP geolocation failed, using KUET campus center", e);
            }
            final double[] finalCoords = coords;
            Platform.runLater(() -> {
                if (disposed) return;
                try {
                    webEngine.executeScript(String.format(
                            Locale.US,
                            "if (window.CampusMap && window.CampusMap.locateMe) window.CampusMap.locateMe(%f, %f);",
                            finalCoords[0], finalCoords[1]
                    ));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Map locateMe failed", e);
                    try {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.WARNING,
                                "Could not update your position on the map. Please try again.",
                                javafx.scene.control.ButtonType.OK);
                        alert.setHeaderText("Location unavailable");
                        alert.showAndWait();
                    } catch (IllegalStateException | UnsupportedOperationException ex) {
                        LOGGER.log(Level.WARNING, "Could not show locate-failure alert", ex);
                    }
                }
            });
        });
    }

    private static double extractJsonDouble(String json, String key) {
        try {
            String pattern = "\"" + key + "\":";
            int idx = json.indexOf(pattern);
            if (idx == -1) return 0.0;
            int start = idx + pattern.length();
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
            return Double.parseDouble(json.substring(start, end));
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void setFocusLocation(double lat, double lng) {
        if (disposed || !isLoaded) return;
        try {
            webEngine.executeScript(String.format(Locale.US, "if (window.CampusMap && window.CampusMap.setUserLocation) window.CampusMap.setUserLocation(%f, %f);", lat, lng));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Map setUserLocation failed", e);
        }
    }

    private void updateMapTheme() {
        if (disposed || !isLoaded) return;
        try {
            String themeMode = ThemeManager.isDark() ? "DARK" : "LIGHT";
            webEngine.executeScript("if (window.CampusMap) window.CampusMap.setTheme('" + themeMode + "');");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Map setTheme failed", e);
        }
    }

    /** Release WebView + listeners to prevent leaks on navigation. */
    public void dispose() {
        disposed = true;
        try {
            webEngine.getLoadWorker().stateProperty().removeListener(loadListener);
            ThemeManager.themeProperty().removeListener(themeListener);
            widthProperty().removeListener(widthListener);
            heightProperty().removeListener(heightListener);
            try {
                webEngine.executeScript("window.campusBridge = null;");
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Clearing campusBridge failed", e);
            }
            webEngine.load(null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Map dispose failed", e);
        }
    }

    /**
     * Bridge class exposed to JavaScript in WebView
     */
    public class CampusBridge {

        public void onLocationSelected(double lat, double lng, String nearestHub, int distanceMeters) {
            Platform.runLater(() -> {
                String desc = String.format(Locale.US, "%s (%dm away)", nearestHub, distanceMeters);
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
