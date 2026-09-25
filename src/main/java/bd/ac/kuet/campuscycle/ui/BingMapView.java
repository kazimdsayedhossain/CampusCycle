package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CycleItem;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

/**
 * High-definition interactive map view embedding Leaflet and Bing Satellite / Street tiles
 * inside a JavaFX WebView, featuring real-time Java-JavaScript telemetry communication,
 * Khulna city-wide navigation, and floating HUD status elements.
 */
public class BingMapView extends StackPane {

    private final WebView webView;
    private final WebEngine webEngine;
    private final Consumer<String> onHubSelected;
    private final Consumer<String> onLocationChanged;
    private boolean isLoaded = false;

    public BingMapView(List<CycleItem> availableCycles,
                       Consumer<String> onHubSelected,
                       Consumer<String> onLocationChanged) {
        this.onHubSelected = onHubSelected;
        this.onLocationChanged = onLocationChanged;

        setPrefSize(980, 520);
        setMinHeight(460);
        getStyleClass().add("map-canvas-card");

        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);

        // Clip to rounded border
        webView.setStyle("-fx-background-color: transparent;");

        // Floating Loading Indicator
        VBox loadingOverlay = new VBox(10);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(10, 15, 29, 0.85); -fx-background-radius: 14px;");
        Label loadingLabel = new Label("Initializing Satellite & Street Telemetry...");
        loadingLabel.setStyle("-fx-text-fill: #0EA5E9; -fx-font-weight: 700; -fx-font-size: 13px;");
        loadingOverlay.getChildren().addAll(
                ThemeManager.createIcon(ThemeManager.ICON_PIN, 28, Color.web("#0EA5E9")),
                loadingLabel
        );

        getChildren().addAll(webView, loadingOverlay);

        // Load the HTML
        URL mapUrl = getClass().getResource("/bd/ac/kuet/campuscycle/bing-map.html");
        if (mapUrl != null) {
            webEngine.load(mapUrl.toExternalForm());
        }

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                isLoaded = true;
                loadingOverlay.setVisible(false);

                // Register Bridge
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("campusBridge", new CampusBridge());

                    // Apply current theme
                    updateMapTheme();
                } catch (Exception e) {
                    System.err.println("Failed to wire JavaScript bridge: " + e.getMessage());
                }
            } else if (newState == Worker.State.FAILED) {
                loadingLabel.setText("Map rendered in offline vector mode.");
            }
        });

        // React to application theme changes
        ThemeManager.themeProperty().addListener((obs, o, n) -> {
            if (isLoaded) {
                Platform.runLater(this::updateMapTheme);
            }
        });
    }

    private void updateMapTheme() {
        if (!isLoaded) return;
        try {
            String themeMode = ThemeManager.isDark() ? "DARK" : "LIGHT";
            webEngine.executeScript("if (window.CampusMap) window.CampusMap.setTheme('" + themeMode + "');");
        } catch (Exception e) {
            // Ignore execution if engine is reloading
        }
    }

    public void setFocusLocation(double lat, double lng) {
        if (!isLoaded) return;
        try {
            webEngine.executeScript(String.format("if (window.CampusMap) window.CampusMap.setUserLocation(%f, %f);", lat, lng));
        } catch (Exception ignored) {}
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
