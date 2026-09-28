package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CampusTime;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.service.WeatherService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Top bar matching the dashboard design: search pill with Ctrl+K hint on the left,
 * with weather chip, notification bell, time chip, and user profile chip right-aligned.
 */
public class TopBarView extends HBox {

    /** Below this bar width the weather chip is hidden to protect the search + user chips (P-077). */
    private static final double WEATHER_HIDE_BELOW = 900;

    private final TextField searchField = new TextField();
    private final Label weatherLabel = new Label("--°C");
    private final Label weatherSub = new Label("Khulna, KUET");
    private final Label userNameLabel = new Label();
    private final Circle notifDot = new Circle(4, Color.web("#EF4444"));
    private HBox weatherChip;
    private Timeline clockTimeline;

    public TopBarView(CampusUser user,
                      Consumer<String> onSearch,
                      Runnable onOpenNotifications,
                      Runnable onOpenProfile) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(8, 16, 8, 16));
        setMaxWidth(Double.MAX_VALUE);

        HBox searchBox = createSearchBox(onSearch);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        weatherChip = createWeatherChip();
        Button bellBtn = createBellButton(onOpenNotifications);
        HBox timeChip = createTimeChip();
        HBox userChip = createUserChip(user, onOpenProfile);

        // The dot must not announce phantom notifications on first paint (P-097).
        notifDot.setVisible(false);
        notifDot.setManaged(false);

        // Width budget: collapse the weather chip before the search or user chip clips (P-077).
        widthProperty().addListener((obs, oldW, newW) -> {
            boolean showWeather = newW.doubleValue() >= WEATHER_HIDE_BELOW;
            weatherChip.setVisible(showWeather);
            weatherChip.setManaged(showWeather);
        });

        loadWeather();

        // Search on left, spacer expands, then weather, notification bell, time, and user profile right-aligned
        getChildren().addAll(searchBox, spacer, weatherChip, bellBtn, timeChip, userChip);
    }

    private HBox createSearchBox(Consumer<String> onSearch) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("topbar-search");
        box.setMinWidth(0);
        box.setMaxWidth(380);

        SVGPath icon = ThemeManager.createIcon(ThemeManager.ICON_SEARCH, 15, Color.web("#6B7280"));
        searchField.setPromptText("Search bikes, locations, hubs...");
        searchField.setAccessibleText("Search bikes, locations, hubs. Shortcut: Control K.");
        searchField.getStyleClass().add("bare-input");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setPrefWidth(320);
        searchField.setMaxWidth(380);
        searchField.setMinWidth(0);
        searchField.setOnAction(e -> {
            if (onSearch != null) onSearch.accept(searchField.getText());
        });

        Label ctrlK = new Label("Ctrl K");
        ctrlK.getStyleClass().add("topbar-ctrlk");

        box.getChildren().addAll(icon, searchField, ctrlK);
        return box;
    }

    private HBox createWeatherChip() {
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("topbar-chip");
        chip.setPadding(new Insets(8, 14, 8, 14));
        chip.setMinWidth(0);

        StackPane sun = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_SUN, 20, Color.web("#F59E0B")));

        VBox text = new VBox(0);
        weatherLabel.getStyleClass().add("topbar-weather-temp");
        weatherSub.getStyleClass().add("topbar-weather-sub");
        text.getChildren().addAll(weatherLabel, weatherSub);

        chip.getChildren().addAll(sun, text);
        return chip;
    }

    private Button createBellButton(Runnable onOpenNotifications) {
        Button bell = new Button();
        bell.getStyleClass().add("topbar-chip");
        bell.setAccessibleText("Notifications");
        bell.setTooltip(new Tooltip("Notifications"));
        bell.setPrefSize(38, 38);
        bell.setMinSize(38, 38);
        bell.setMaxSize(38, 38);
        bell.setStyle("-fx-padding: 0; -fx-background-radius: 999px; -fx-border-radius: 999px;");
        StackPane wrap = new StackPane(
                ThemeManager.createIcon(ThemeManager.ICON_BELL, 16, Color.web("#4B5563")));
        wrap.setPrefSize(22, 22);
        notifDot.setTranslateX(7);
        notifDot.setTranslateY(-7);
        wrap.getChildren().add(notifDot);
        bell.setGraphic(wrap);
        bell.setOnAction(e -> {
            notifDot.setVisible(false);
            notifDot.setManaged(false);
            if (onOpenNotifications != null) onOpenNotifications.run();
        });
        return bell;
    }

    private HBox createTimeChip() {
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("topbar-chip");
        chip.setPadding(new Insets(8, 14, 8, 14));
        chip.setMinWidth(0);

        StackPane clockIcon = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_CLOCK, 16, Color.web("#10B981")));

        VBox text = new VBox(0);
        Label timeLabel = new Label();
        timeLabel.getStyleClass().add("topbar-weather-temp");
        Label dateLabel = new Label();
        dateLabel.getStyleClass().add("topbar-weather-sub");
        text.getChildren().addAll(timeLabel, dateLabel);

        Runnable tick = () -> {
            java.time.ZonedDateTime now = CampusTime.now();
            timeLabel.setText(now.format(DateTimeFormatter.ofPattern("hh:mm a")));
            dateLabel.setText(now.format(DateTimeFormatter.ofPattern("EEE, dd MMM")));
        };
        tick.run();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> tick.run()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();

        chip.getChildren().addAll(clockIcon, text);
        return chip;
    }

    private HBox createUserChip(CampusUser user, Runnable onOpenProfile) {
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("topbar-user");
        chip.setPadding(new Insets(6, 14, 6, 10)); // more right padding so chevron is inside pill
        chip.setMinWidth(0);
        // Mouse-only HBox is unreachable by keyboard/screen reader (P-131): expose as a button.
        chip.setFocusTraversable(true);
        chip.setAccessibleRole(AccessibleRole.BUTTON);
        chip.setAccessibleText("Open profile and settings for " + user.displayName());
        Tooltip.install(chip, new Tooltip("Open profile and settings"));

        StackPane avatar = CampusVectorArt.createUserAvatar(user.displayName(), 34);

        StackPane online = new StackPane();
        online.setPrefSize(10, 10);
        online.getStyleClass().add("topbar-online");

        StackPane avatarWrap = new StackPane(avatar, online);
        StackPane.setAlignment(online, Pos.BOTTOM_RIGHT);

        VBox meta = new VBox(1);
        userNameLabel.setText(user.displayName());
        userNameLabel.getStyleClass().add("topbar-username");
        // Width budget: a long display name must ellipsize, not push the cluster off-screen (P-077).
        userNameLabel.setMaxWidth(120);
        userNameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        userNameLabel.setTooltip(new Tooltip(user.displayName()));
        Label role = new Label(user.role() == bd.ac.kuet.campuscycle.domain.Role.ADMIN ? "Cycle Office Admin" : "Verified Student");
        role.getStyleClass().add("topbar-userrole");
        meta.getChildren().addAll(userNameLabel, role);

        SVGPath chevron = ThemeManager.createIcon(ThemeManager.ICON_NAV, 12, Color.web("#6B7280"));
        chevron.setRotate(180);

        chip.getChildren().addAll(avatarWrap, meta, chevron);
        chip.setOnMouseClicked(e -> {
            if (onOpenProfile != null) onOpenProfile.run();
        });
        chip.setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)
                    && onOpenProfile != null) {
                onOpenProfile.run();
                e.consume();
            }
        });
        chip.setCursor(javafx.scene.Cursor.HAND);
        return chip;
    }

    private void loadWeather() {
        // KhulnaWeather currently carries no liveness flag (P-062 belongs to another
        // agent): null or failure renders as unavailable, never as fabricated sun.
        new WeatherService().fetchCurrentWeatherAsync()
                .thenAccept(w -> Platform.runLater(() -> {
                    if (w == null) {
                        renderWeatherUnavailable();
                    } else {
                        weatherLabel.setText(String.format(Locale.US, "%.0f°C", w.temperatureCelsius()));
                        String condition = w.conditionDescription();
                        weatherSub.setText(condition != null && !condition.isBlank() ? condition : "Khulna, KUET");
                    }
                }))
                .exceptionally(err -> {
                    Platform.runLater(this::renderWeatherUnavailable);
                    return null;
                });
    }

    private void renderWeatherUnavailable() {
        weatherLabel.setText("--°C");
        weatherSub.setText("unavailable");
    }

    public void focusSearch() {
        searchField.requestFocus();
    }

    public void setHasNotifications(boolean has) {
        notifDot.setVisible(has);
        notifDot.setManaged(has);
    }

    public void dispose() {
        if (clockTimeline != null) {
            clockTimeline.stop();
            clockTimeline = null;
        }
    }
}
