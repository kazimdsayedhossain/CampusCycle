package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.data.CampusRepository;
import bd.ac.kuet.campuscycle.data.EventBus;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusTime;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.CycleType;
import bd.ac.kuet.campuscycle.domain.Money;
import bd.ac.kuet.campuscycle.domain.OwnerEarnings;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.ReviewStatus;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.TariffService;
import bd.ac.kuet.campuscycle.domain.WalletTransaction;
import bd.ac.kuet.campuscycle.domain.event.RentalReturnedEvent;
import bd.ac.kuet.campuscycle.service.AppExecutor;
import bd.ac.kuet.campuscycle.service.WalletService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Dashboard matching the CampusCycle green reference design.
 * Every number, card, chart point and action is backed by live state:
 * repository catalog, active rental, wallet ledger, rental history and hubs.
 */
public class DashboardView extends VBox {

    private static final Color GREEN = Color.web("#0E7A5F");
    private static final Color GREEN_BRIGHT = Color.web("#10B981");
    private static final Color INK = Color.web("#0B2E23");
    private static final Color MUTED = Color.web("#7A8B83");

    private final CampusUser user;
    private final CampusRepository repo;
    private final Consumer<String> onNavigate;
    private final Consumer<CycleItem> onReserve;
    private final Consumer<String> onLocationChanged;

    private final Label dateLabel = new Label();
    private final Label timeLabel = new Label();
    private Timeline clock;
    private Timeline elapsedTimeline;

    private final Label availableValue = new Label("—");
    private final Label availableDelta = new Label("");
    private final Label commuteTitle = new Label("No active ride");
    private final Label commuteSub = new Label("Start your next journey");
    private final Button commuteBtn = new Button("Find a Bike");
    private final Label hubsValue = new Label("—");
    private final Label payBalance = new Label("—");
    private final Button topUpButton = new Button("+ Top Up");
    private final Canvas paySpark = new Canvas(60, 48);

    private final Canvas chart = new Canvas(1, 1);
    private final Label chartTip = new Label();
    private int hoverIndex = -1;
    private int chartRange = 7;
    private final Button range7Btn = new Button("7 Days");
    private final Button range30Btn = new Button("30 Days");
    private final Label legRidesLabel = new Label("Rides (—)");
    private final Label legYesterdayLabel = new Label("Yesterday (—)");
    private final Label statTotalValue = new Label("—");
    private final Label statBestValue = new Label("—");
    private final Label statFleetValue = new Label("—");
    private int[] dayRides = new int[30];
    private LocalDate[] dayDates = new LocalDate[30];
    private List<RentalRecord> personalHistory = new ArrayList<>();
    private PauseTransition chartCoalescer;

    private final VBox earningsCard = new VBox(12);
    private final Label earnTotalValue = new Label("—");
    private final Label earnRidesValue = new Label("—");
    private final VBox earnBikeRows = new VBox(6);
    private final HBox duesBanner = new HBox(12);
    private final Label duesBannerLabel = new Label();

    private final HBox bikeRow = new HBox(14);
    private final Button viewAllBtn = new Button("View All Cycles");
    private ProgressIndicator bikeSpinner;
    private ProgressIndicator mapSpinner;
    private final StackPane mapWrapper = new StackPane();
    private BingMapView dashMap;

    private volatile boolean disposed = false;
    private final List<ChangeListener<ThemeManager.Theme>> themeListeners = new ArrayList<>();
    private ChangeListener<Number> chartWidthListener;
    private ChangeListener<Number> chartHeightListener;
    private java.util.function.Consumer<RentalReturnedEvent> rentalReturnedConsumer;
    private WalletService.WalletListener walletListener;
    private List<WalletTransaction> lastPayTxs = new ArrayList<>();

    private List<CycleItem> cycles = new ArrayList<>();
    private RentalRecord active;
    private final Preferences prefs = Preferences.userNodeForPackage(DashboardView.class);

    public DashboardView(CampusUser user,
                         CampusRepository repo,
                         Consumer<String> onNavigate,
                         Consumer<CycleItem> onReserve,
                         Consumer<String> onLocationChanged) {
        this.user = user;
        this.repo = repo;
        this.onNavigate = onNavigate;
        this.onReserve = onReserve;
        this.onLocationChanged = onLocationChanged;

        setSpacing(16);
        setPadding(new Insets(6, 16, 24, 16));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Double.MAX_VALUE);

        configureEarningsCard();
        earningsCard.setVisible(false);
        earningsCard.setManaged(false);
        configureDuesBanner();
        duesBanner.setVisible(false);
        duesBanner.setManaged(false);
        getChildren().addAll(
                createWelcomeRow(),
                duesBanner,
                createKpiRow(),
                earningsCard,
                createMidRow(),
                createBottomRow()
        );
        ThemeManager.applyFadeIn(this);
        startClock();
        subscribeToLiveUpdates();
        loadDataAsync();

        // Button press feedback only — never lift the whole page root.
        Platform.runLater(() -> {
            AnimationHelper.addPressAnimation(commuteBtn);
            AnimationHelper.addPressAnimation(topUpButton);
            AnimationHelper.addPressAnimation(viewAllBtn);
        });
    }

    /** Release clocks, listeners and the embedded map. */
    public void dispose() {
        disposed = true;
        if (clock != null) clock.stop();
        if (elapsedTimeline != null) elapsedTimeline.stop();
        if (chartCoalescer != null) chartCoalescer.stop();
        for (ChangeListener<ThemeManager.Theme> l : themeListeners) {
            ThemeManager.themeProperty().removeListener(l);
        }
        themeListeners.clear();
        if (rentalReturnedConsumer != null) {
            EventBus.getInstance().unsubscribe(RentalReturnedEvent.class, rentalReturnedConsumer);
            rentalReturnedConsumer = null;
        }
        if (walletListener != null) {
            WalletService.getInstance().removeListener(walletListener);
            walletListener = null;
        }
        if (dashMap != null) {
            dashMap.dispose();
            dashMap = null;
        }
    }

    private void subscribeToLiveUpdates() {
        rentalReturnedConsumer = evt -> {
            if (disposed) return;
            Platform.runLater(() -> {
                if (disposed) return;
                reloadKpis();
            });
        };
        EventBus.getInstance().subscribe(RentalReturnedEvent.class, rentalReturnedConsumer);
        walletListener = (changedUserId, newBalancePoisha) -> {
            if (disposed) return;
            if (user != null && user.id().equals(changedUserId)) {
                payBalance.setText(Money.formatTaka(newBalancePoisha));
            }
        };
        WalletService.getInstance().addListener(walletListener);
    }

    /** Re-reads catalog + active rental + personal history after a return. */
    private void reloadKpis() {
        if (disposed) return;
        AppExecutor.asyncThenFx(
                () -> {
                    List<CycleItem> catalog;
                    try {
                        catalog = repo.catalog(user);
                    } catch (Exception e) {
                        catalog = List.of();
                    }
                    RentalRecord act = null;
                    try {
                        act = repo.activeRental(user);
                    } catch (Exception ignored) {
                    }
                    List<RentalRecord> history;
                    try {
                        history = repo.rentals(user);
                    } catch (Exception e) {
                        history = List.of();
                    }
                    return new DashPayload(catalog, act, history);
                },
                payload -> {
                    if (disposed) return;
                    cycles = new ArrayList<>(payload.catalog());
                    active = payload.active();
                    personalHistory = new ArrayList<>(payload.history());
                    renderKpis(payload);
                    renderBikes();
                    renderEarnings();
                    renderDuesBanner();
                    computeChart();
                },
                err -> {
                    if (disposed) return;
                    renderUnavailableState();
                });
    }

    /** Registers a theme listener that is removed in {@link #dispose()}. */
    private void onThemeChange(Runnable apply) {
        apply.run();
        ChangeListener<ThemeManager.Theme> listener = (obs, o, n) -> {
            if (disposed) return;
            apply.run();
        };
        themeListeners.add(listener);
        ThemeManager.themeProperty().addListener(listener);
    }

    // ---------- header ----------

    private HBox createWelcomeRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label sub = new Label("Ready to ride across KUET? Here's what's happening on campus today.");
        sub.getStyleClass().add("dash-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        dateLabel.getStyleClass().add("dash-date");
        timeLabel.getStyleClass().add("dash-date");

        row.getChildren().addAll(sub, spacer, dateLabel, timeLabel);
        return row;
    }

    private void startClock() {
        Runnable tick = () -> {
            if (disposed) return;
            ZonedDateTime now = CampusTime.now();
            dateLabel.setText(now.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")));
            timeLabel.setText(now.format(DateTimeFormatter.ofPattern("hh:mm a")));
        };
        tick.run();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick.run()));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    /** Live "on ride for N min" counter; restarts whenever the active rental changes. */
    private void restartElapsedCounter() {
        if (elapsedTimeline != null) {
            elapsedTimeline.stop();
            elapsedTimeline = null;
        }
        if (active == null || disposed) return;
        Runnable tick = () -> {
            if (disposed || active == null) return;
            long mins = Math.max(0, java.time.Duration.between(active.startedAt(), CampusTime.now()).toMinutes());
            commuteSub.setText("On ride for " + mins + " min");
        };
        tick.run();
        elapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> tick.run()));
        elapsedTimeline.setCycleCount(Animation.INDEFINITE);
        elapsedTimeline.play();
    }

    // ---------- KPI row ----------

    private HBox createKpiRow() {
        HBox row = new HBox(14);

        VBox c1 = kpiCard(ThemeManager.ICON_BIKE, "Available Cycles", availableValue,
                new Label("Ready to ride across campus"), availableDelta, null, null);

        VBox c2 = kpiCard(ThemeManager.ICON_PLAY, "Active Commute", commuteTitle,
                commuteSub, null, commuteBtn, "kpi-outline-btn");
        commuteBtn.setText("Find a Bike  →");
        commuteBtn.setOnAction(e -> {
            if (active != null) onNavigate.accept("Active Journey");
            else onNavigate.accept("Fleet Catalog");
        });

        VBox c3 = kpiCard(ThemeManager.ICON_PIN, "Campus Hubs", hubsValue,
                new Label("Central Mosque, SWC & Gates"), null, actionButton("Explore Map  →",
                        () -> onNavigate.accept("Campus Map")), "kpi-outline-btn");

        VBox pay = createPayCard();

        for (VBox c : List.of(c1, c2, c3, pay)) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        row.getChildren().addAll(c1, c2, c3, pay);
        return row;
    }

    private Button actionButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("kpi-outline-btn");
        b.setOnAction(e -> action.run());
        return b;
    }

    private VBox kpiCard(String icon, String title, Label value, Label subLabel,
                         Label deltaOrNull, Button btnOrNull, String btnStyle) {
        VBox card = new VBox(8);
        card.getStyleClass().add("kpi-card");
        card.setPadding(new Insets(16, 18, 16, 18));

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane(ThemeManager.createIcon(icon, 18, Color.web("#0E7A5F")));
        badge.setPrefSize(42, 42);
        badge.getStyleClass().add("icon-badge");

        VBox titleCol = new VBox(2);
        HBox titleRow = new HBox(4);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label t = new Label(title);
        t.getStyleClass().add("kpi-title");
        Label chevronTitle = new Label(" >");
        chevronTitle.getStyleClass().add("kpi-title");
        titleRow.getChildren().addAll(t, chevronTitle);

        if (!value.getStyleClass().contains("kpi-value")) value.getStyleClass().add("kpi-value");
        titleCol.getChildren().addAll(titleRow, value);

        top.getChildren().addAll(badge, titleCol);
        card.getChildren().add(top);

        if (subLabel != null) {
            if (!subLabel.getStyleClass().contains("kpi-sub")) subLabel.getStyleClass().add("kpi-sub");
            subLabel.setWrapText(true);
            card.getChildren().add(subLabel);
        }
        if (deltaOrNull != null) {
            if (!deltaOrNull.getStyleClass().contains("kpi-delta")) deltaOrNull.getStyleClass().add("kpi-delta");
            HBox wrap = new HBox(deltaOrNull);
            wrap.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(wrap);
        }
        if (btnOrNull != null) {
            if (btnStyle != null && !btnOrNull.getStyleClass().contains(btnStyle)) {
                btnOrNull.getStyleClass().add(btnStyle);
            }
            HBox wrap = new HBox(btnOrNull);
            wrap.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(wrap);
        }
        return card;
    }

    private VBox createPayCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("pay-card");
        card.setPadding(new Insets(16, 18, 16, 18));

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);

        StackPane walletCircle = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_WALLET, 18, Color.WHITE));
        walletCircle.setPrefSize(40, 40);
        walletCircle.getStyleClass().add("icon-badge");

        VBox titleCol = new VBox(2);
        Label t = new Label("CAMPUS PAY");
        t.getStyleClass().add("pay-label");
        if (!payBalance.getStyleClass().contains("pay-balance")) payBalance.getStyleClass().add("pay-balance");
        titleCol.getChildren().addAll(t, payBalance);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane statBadge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_NAV, 15, Color.web("#99F6E4")));
        statBadge.setPrefSize(32, 32);
        statBadge.getStyleClass().add("icon-badge");

        top.getChildren().addAll(walletCircle, titleCol, spacer, statBadge);

        Label sub = new Label("Prepaid transit balance");
        sub.getStyleClass().add("pay-sub");

        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);
        topUpButton.getStyleClass().add("pay-topup");
        topUpButton.setOnAction(e -> openTopUpDialog());

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        paySpark.setHeight(48);
        paySpark.widthProperty().bind(card.widthProperty().multiply(0.22));
        paySpark.widthProperty().addListener((obs, o, n) -> drawPaySpark(lastPayTxs));
        bottom.getChildren().addAll(topUpButton, sp2, paySpark);

        card.getChildren().addAll(top, sub, bottom);
        return card;
    }

    private void openTopUpDialog() {
        TopUpModal.open(this, user, () -> {
            AppExecutor.asyncThenFx(
                    () -> {
                        WalletService ws = WalletService.getInstance();
                        int bal = ws.getBalancePoisha(user.id());
                        List<WalletTransaction> txs;
                        try {
                            txs = ws.getTransactions(user.id());
                        } catch (Exception e) {
                            txs = List.of();
                        }
                        return new WalletPayload(bal, txs);
                    },
                    wp -> {
                        if (disposed) return;
                        payBalance.setText(Money.formatTaka(wp.bal()));
                        lastPayTxs = new ArrayList<>(wp.txs());
                        drawPaySpark(lastPayTxs);
                    },
                    err -> {
                        if (disposed) return;
                        payBalance.setText("BDT --");
                        topUpButton.setDisable(true);
                    });
        });
    }

    // ---------- mid row: chart + quick actions ----------

    private HBox createMidRow() {
        HBox row = new HBox(14);
        row.setFillHeight(true);
        VBox chartCard = createChartCard();
        HBox.setHgrow(chartCard, Priority.ALWAYS);
        chartCard.setMaxWidth(Double.MAX_VALUE);
        VBox quick = createQuickActions();
        quick.setPrefWidth(360);
        quick.setMinWidth(340);
        quick.setMaxWidth(380);
        row.getChildren().addAll(chartCard, quick);
        return row;
    }

    private VBox createChartCard() {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14, 18, 12, 18));
        card.setPrefHeight(340);
        card.setMinHeight(340);
        card.setMaxHeight(340);

        onThemeChange(() -> {
            boolean isDark = ThemeManager.isDark();
            card.setStyle(
                    (isDark
                        ? "-fx-background-color: linear-gradient(to bottom, rgba(30, 41, 59, 0.88), rgba(15, 23, 42, 0.88)); " +
                          "-fx-border-color: rgba(255, 255, 255, 0.16); " +
                          "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.35), 32, 0, 0, 10);"
                        : "-fx-background-color: linear-gradient(to bottom, rgba(255, 255, 255, 0.92), rgba(240, 253, 248, 0.82)); " +
                          "-fx-border-color: rgba(255, 255, 255, 0.95); " +
                          "-fx-effect: dropshadow(gaussian, rgba(14, 122, 95, 0.12), 32, 0, 0, 10);") +
                    "-fx-background-radius: 28px; " +
                    "-fx-border-radius: 28px; " +
                    "-fx-border-width: 1.5px;"
            );
            drawChart();
        });

        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 17, GREEN));
        badge.setPrefSize(34, 34);
        badge.setMinSize(34, 34);
        badge.getStyleClass().add("icon-badge");

        VBox titleCol = new VBox(2);
        HBox.setHgrow(titleCol, Priority.ALWAYS);
        Label title = new Label("Your Rides — Last 7 Days");
        title.setWrapText(true);
        title.setMaxWidth(280);
        Label subtitle = new Label("Your personal commute history");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(280);
        onThemeChange(() -> {
            boolean isDark = ThemeManager.isDark();
            title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: " + (isDark ? "#F8FAFC;" : "#0F172A;"));
            subtitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 500; -fx-text-fill: " + (isDark ? "#94A3B8;" : "#64748B;"));
        });
        titleCol.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rangePills = new HBox(8);
        rangePills.setAlignment(Pos.CENTER_RIGHT);

        range7Btn.getStyleClass().add("filter-chip");
        range30Btn.getStyleClass().add("filter-chip");
        range7Btn.setTooltip(new Tooltip("Show your last 7 days of rides"));
        range30Btn.setTooltip(new Tooltip("Show your last 30 days of rides"));
        range7Btn.setOnAction(e -> setChartRange(7, title, subtitle));
        range30Btn.setOnAction(e -> setChartRange(30, title, subtitle));
        updateRangePills();

        rangePills.getChildren().addAll(range7Btn, range30Btn);
        head.getChildren().addAll(badge, titleCol, spacer, rangePills);

        StackPane chartWrap = new StackPane(chart, chartTip);
        chartWrap.setAlignment(Pos.CENTER);
        VBox.setVgrow(chartWrap, Priority.ALWAYS);

        chartTip.getStyleClass().add("chart-tooltip");
        chartTip.setPadding(new Insets(8, 12, 8, 12));
        chartTip.setVisible(false);
        chartTip.setManaged(false);
        chartTip.setMouseTransparent(true);
        StackPane.setAlignment(chartTip, Pos.TOP_LEFT);

        // Coalesced redraw on resize: one draw per 120ms burst, not two per event.
        chartCoalescer = new PauseTransition(Duration.millis(120));
        chartCoalescer.setOnFinished(e -> {
            if (!disposed) drawChart();
        });
        chartWidthListener = (obs, o, n) -> {
            if (disposed) return;
            if (n != null && n.doubleValue() > 100) {
                chart.setWidth(n.doubleValue());
                chartCoalescer.playFromStart();
            }
        };
        chartHeightListener = (obs, o, n) -> {
            if (disposed) return;
            if (n != null && n.doubleValue() > 80) {
                chart.setHeight(n.doubleValue());
                chartCoalescer.playFromStart();
            }
        };
        chartWrap.widthProperty().addListener(chartWidthListener);
        chartWrap.heightProperty().addListener(chartHeightListener);

        chart.setOnMouseMoved(e -> showChartTip(e.getX()));
        chart.setOnMouseExited(e -> {
            hoverIndex = -1;
            chartTip.setVisible(false);
            drawChart();
        });

        // Bottom insight bar: personal totals plus the chart legend (moved down from the header).
        HBox insightBar = new HBox(10);
        insightBar.setAlignment(Pos.CENTER_LEFT);
        insightBar.setPadding(new Insets(4, 6, 2, 6));

        HBox chipTotal = createInsightChip(ThemeManager.ICON_BIKE, "Total Rides", statTotalValue);
        HBox chipBest = createInsightChip(ThemeManager.ICON_CLOCK, "Best Day", statBestValue);

        Region insightSpacer = new Region();
        HBox.setHgrow(insightSpacer, Priority.ALWAYS);

        HBox legA = createLegendChip("#10B981", legRidesLabel);
        HBox legB = createLegendChip("#0A3D36", legYesterdayLabel);
        HBox chipFleet = createInsightChip(ThemeManager.ICON_PIN, "Fleet utilisation", statFleetValue);

        insightBar.getChildren().addAll(chipTotal, chipBest, insightSpacer, legA, legB, chipFleet);

        card.getChildren().addAll(head, chartWrap, insightBar);
        return card;
    }

    private void setChartRange(int days, Label title, Label subtitle) {
        chartRange = days;
        hoverIndex = -1;
        chartTip.setVisible(false);
        title.setText(days == 7 ? "Your Rides — Last 7 Days" : "Your Rides — Last 30 Days");
        subtitle.setText("Your personal commute history");
        updateRangePills();
        drawChart();
    }

    private void updateRangePills() {
        range7Btn.getStyleClass().remove("filter-chip-active");
        range30Btn.getStyleClass().remove("filter-chip-active");
        if (chartRange == 7) {
            if (!range7Btn.getStyleClass().contains("filter-chip-active")) {
                range7Btn.getStyleClass().add("filter-chip-active");
            }
        } else {
            if (!range30Btn.getStyleClass().contains("filter-chip-active")) {
                range30Btn.getStyleClass().add("filter-chip-active");
            }
        }
    }

    private HBox createLegendChip(String hex, Label valueLabel) {
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(4, 10, 4, 10));
        chip.setMinWidth(Region.USE_PREF_SIZE);

        Region dot = new Region();
        dot.setPrefSize(7, 7);
        dot.setMaxSize(7, 7);
        dot.setStyle("-fx-background-color: " + hex + "; -fx-background-radius: 999px;");

        valueLabel.setMinWidth(Region.USE_PREF_SIZE);

        HBox chipRef = chip;
        onThemeChange(() -> {
            boolean isDark = ThemeManager.isDark();
            chipRef.setStyle(
                    (isDark
                        ? "-fx-background-color: rgba(30, 41, 59, 0.70); -fx-border-color: rgba(255, 255, 255, 0.12); "
                        : "-fx-background-color: rgba(255, 255, 255, 0.85); -fx-border-color: rgba(0, 0, 0, 0.08); ") +
                    "-fx-border-radius: 999px; -fx-background-radius: 999px; -fx-border-width: 1px;"
            );
            valueLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: " + (isDark ? "#E2E8F0;" : "#334155;"));
        });

        chip.getChildren().addAll(dot, valueLabel);
        return chip;
    }

    private HBox createInsightChip(String icon, String labelText, Label valLabel) {
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(4, 12, 4, 12));
        chip.setMinWidth(Region.USE_PREF_SIZE);

        StackPane iconWrap = new StackPane(ThemeManager.createIcon(icon, 11, Color.web("#10B981")));
        iconWrap.setPrefSize(20, 20);
        iconWrap.setMaxSize(20, 20);
        iconWrap.setStyle("-fx-background-color: rgba(16, 185, 129, 0.16); -fx-background-radius: 999px;");

        Label label = new Label(labelText + ":");
        label.setMinWidth(Region.USE_PREF_SIZE);
        valLabel.setMinWidth(Region.USE_PREF_SIZE);

        HBox chipRef = chip;
        onThemeChange(() -> {
            boolean isDark = ThemeManager.isDark();
            chipRef.setStyle(
                    (isDark
                        ? "-fx-background-color: rgba(30, 41, 59, 0.80); -fx-border-color: rgba(16, 185, 129, 0.28); "
                        : "-fx-background-color: rgba(255, 255, 255, 0.92); -fx-border-color: rgba(16, 185, 129, 0.25); ") +
                    "-fx-background-radius: 999px; -fx-border-radius: 999px; -fx-border-width: 1px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.03), 8, 0, 0, 2);"
            );
            label.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: " + (isDark ? "#94A3B8;" : "#475569;"));
            valLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: " + (isDark ? "#34D399;" : "#0E7A5F;"));
        });

        chip.getChildren().addAll(iconWrap, label, valLabel);
        return chip;
    }

    private void showChartTip(double mouseX) {
        int n = chartRange;
        int start = dayRides.length - n;
        double w = chart.getWidth();
        double left = 36, right = w - 20;
        int rel = (int) Math.round((mouseX - left) / ((right - left) / (double) Math.max(1, n - 1)));
        rel = Math.max(0, Math.min(n - 1, rel));
        int idx = start + rel;
        if (idx != hoverIndex) {
            hoverIndex = idx;
            drawChart();
        }
        String dateStr = dayDates[idx] != null
                ? dayDates[idx].format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)) : "";
        int rides = dayRides[idx];
        chartTip.setText(dateStr + "\n" + rides + (rides == 1 ? " ride" : " rides"));
        chartTip.setVisible(true);
        // Position near the hovered point; translate is relative to the wrap's top-left.
        double plotW = Math.max(1, right - left);
        double hx = left + plotW * rel / (double) Math.max(1, n - 1);
        double h = chart.getHeight();
        double top = 18, bottom = h - 30;
        int max = chartMax();
        double hy = bottom - (bottom - top) * rides / (double) max;
        chartTip.setTranslateX(hx + 12);
        chartTip.setTranslateY(Math.max(0, hy - 20));
    }

    private void drawChart() {
        GraphicsContext g = chart.getGraphicsContext2D();
        double w = chart.getWidth(), h = chart.getHeight();
        if (w <= 50 || h <= 50) return;
        g.clearRect(0, 0, w, h);

        boolean isDark = ThemeManager.isDark();
        double left = 36, right = w - 20, top = 18, bottom = h - 30;

        int n = chartRange;
        int start = dayRides.length - n;
        int max = chartMax();

        // Horizontal dashed gridlines & Y-axis labels
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        int step = Math.max(1, max / 4);
        for (int v = 0; v <= max; v += step) {
            double y = bottom - (bottom - top) * v / (double) max;

            g.setStroke(isDark ? Color.web("#334155", 0.50) : Color.web("#E2E8F0", 0.85));
            g.setLineWidth(1.0);
            g.setLineDashes(4, 4);
            g.strokeLine(left, y, right, y);
            g.setLineDashes(null);

            g.setFill(isDark ? Color.web("#94A3B8") : Color.web("#64748B"));
            g.fillText(String.valueOf(v), 10, y + 3.5);
        }

        double[] xs = new double[n];
        double[] yr = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = left + (right - left) * (n == 1 ? 1 : (double) i / (n - 1));
            yr[i] = bottom - (bottom - top) * dayRides[start + i] / (double) max;
        }

        // Vertical subtle dotted lines for each day column
        for (int i = 0; i < n; i++) {
            g.setStroke(isDark ? Color.web("#334155", 0.25) : Color.web("#E2E8F0", 0.50));
            g.setLineWidth(0.8);
            g.setLineDashes(2, 4);
            g.strokeLine(xs[i], top, xs[i], bottom);
            g.setLineDashes(null);
        }

        // Vertical hover scrubber line
        if (hoverIndex >= start && hoverIndex < start + n) {
            double hx = xs[hoverIndex - start];
            g.setStroke(isDark ? Color.web("#34D399", 0.60) : Color.web("#10B981", 0.60));
            g.setLineWidth(1.5);
            g.setLineDashes(4, 3);
            g.strokeLine(hx, top, hx, bottom);
            g.setLineDashes(null);
        }

        // Single honest series: the student's own rides per day.
        Color rideColor = Color.web("#10B981");

        LinearGradient greenGrad = new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, rideColor.deriveColor(0, 1, 1, isDark ? 0.35 : 0.22)),
                new Stop(0.65, rideColor.deriveColor(0, 1, 1, isDark ? 0.10 : 0.06)),
                new Stop(1.0, rideColor.deriveColor(0, 1, 1, 0.0))
        );

        // Draw rides curve (Emerald)
        drawSmoothSpline(g, xs, yr, rideColor, 2.8, greenGrad, bottom);

        // Draw data points with glowing halo rings
        for (int i = 0; i < n; i++) {
            boolean isHovered = (start + i == hoverIndex);
            drawGlowDot(g, xs[i], yr[i], rideColor, isHovered);
        }

        // X-axis day & date labels (every label for 7 days, every 5th for 30)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US);
        for (int i = 0; i < n; i++) {
            int abs = start + i;
            String dayStr = dayDates[abs] != null ? dayDates[abs].format(fmt) : "";
            boolean isToday = (abs == dayDates.length - 1);
            String dayName = isToday ? "Today"
                    : (dayDates[abs] != null
                            ? dayDates[abs].getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US) : "");
            String label = dayName + " " + dayStr;

            boolean showLabel = (n <= 7) || isToday || (i % 5 == 0) || (start + i == hoverIndex);
            if (!showLabel) continue;
            g.setFont(Font.font("Segoe UI", (isToday || start + i == hoverIndex) ? FontWeight.BOLD : FontWeight.SEMI_BOLD, 10));
            if (isToday) {
                g.setFill(isDark ? Color.web("#34D399") : Color.web("#059669"));
            } else if (start + i == hoverIndex) {
                g.setFill(isDark ? Color.web("#6EE7B7") : Color.web("#0A3D36"));
            } else {
                g.setFill(isDark ? Color.web("#94A3B8") : Color.web("#64748B"));
            }

            double strWidth = label.length() * 5.2;
            g.fillText(label, xs[i] - strWidth / 2.0, h - 8);
        }
    }

    /** Y-axis ceiling for the visible window: max rides, at least 5, rounded up to 5s. */
    private int chartMax() {
        int n = chartRange;
        int start = dayRides.length - n;
        int max = 1;
        for (int i = start; i < start + n; i++) {
            max = Math.max(max, dayRides[i]);
        }
        return Math.max(5, (int) Math.ceil(max / 5.0) * 5);
    }

    private void drawSmoothSpline(GraphicsContext g, double[] xs, double[] ys,
                                  Color strokeColor, double strokeWidth,
                                  Paint fillPaint, double bottom) {
        int n = xs.length;
        if (n < 2) return;

        // 1. Fill path under spline
        if (fillPaint != null) {
            g.beginPath();
            g.moveTo(xs[0], bottom);
            g.lineTo(xs[0], ys[0]);
            for (int i = 0; i < n - 1; i++) {
                double p0x = i > 0 ? xs[i - 1] : xs[i];
                double p0y = i > 0 ? ys[i - 1] : ys[i];
                double p1x = xs[i];
                double p1y = ys[i];
                double p2x = xs[i + 1];
                double p2y = ys[i + 1];
                double p3x = i < n - 2 ? xs[i + 2] : p2x;
                double p3y = i < n - 2 ? ys[i + 2] : p2y;

                double cp1x = p1x + (p2x - p0x) / 6.0;
                double cp1y = p1y + (p2y - p0y) / 6.0;
                double cp2x = p2x - (p3x - p1x) / 6.0;
                double cp2y = p2y - (p3y - p1y) / 6.0;

                g.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y);
            }
            g.lineTo(xs[n - 1], bottom);
            g.closePath();
            g.setFill(fillPaint);
            g.fill();
        }

        // 2. Stroke curve through points
        g.beginPath();
        g.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            double p0x = i > 0 ? xs[i - 1] : xs[i];
            double p0y = i > 0 ? ys[i - 1] : ys[i];
            double p1x = xs[i];
            double p1y = ys[i];
            double p2x = xs[i + 1];
            double p2y = ys[i + 1];
            double p3x = i < n - 2 ? xs[i + 2] : p2x;
            double p3y = i < n - 2 ? ys[i + 2] : p2y;

            double cp1x = p1x + (p2x - p0x) / 6.0;
            double cp1y = p1y + (p2y - p0y) / 6.0;
            double cp2x = p2x - (p3x - p1x) / 6.0;
            double cp2y = p2y - (p3y - p1y) / 6.0;

            g.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y);
        }
        g.setStroke(strokeColor);
        g.setLineWidth(strokeWidth);
        g.stroke();
    }

    private void drawGlowDot(GraphicsContext g, double x, double y, Color color, boolean isHovered) {
        if (isHovered) {
            g.setFill(color.deriveColor(0, 1, 1, 0.25));
            g.fillOval(x - 8, y - 8, 16, 16);
            g.setFill(color.deriveColor(0, 1, 1, 0.50));
            g.fillOval(x - 5, y - 5, 10, 10);
            g.setFill(color);
            g.fillOval(x - 3.5, y - 3.5, 7, 7);
            g.setFill(Color.WHITE);
            g.fillOval(x - 1.5, y - 1.5, 3, 3);
        } else {
            g.setFill(color.deriveColor(0, 1, 1, 0.20));
            g.fillOval(x - 5, y - 5, 10, 10);
            g.setFill(color);
            g.fillOval(x - 3, y - 3, 6, 6);
            g.setFill(Color.WHITE);
            g.fillOval(x - 1.2, y - 1.2, 2.4, 2.4);
        }
    }

    private VBox createQuickActions() {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setPrefHeight(340);
        card.setMinHeight(340);
        card.setMaxHeight(340);

        onThemeChange(() -> {
            boolean isDark = ThemeManager.isDark();
            card.setStyle(
                    (isDark
                        ? "-fx-background-color: linear-gradient(to bottom, rgba(30, 41, 59, 0.88), rgba(15, 23, 42, 0.88)); " +
                          "-fx-border-color: rgba(255, 255, 255, 0.16); " +
                          "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.35), 32, 0, 0, 10);"
                        : "-fx-background-color: linear-gradient(to bottom, rgba(255, 255, 255, 0.92), rgba(240, 253, 248, 0.82)); " +
                          "-fx-border-color: rgba(255, 255, 255, 0.95); " +
                          "-fx-effect: dropshadow(gaussian, rgba(14, 122, 95, 0.12), 32, 0, 0, 10);") +
                    "-fx-background-radius: 28px; " +
                    "-fx-border-radius: 28px; " +
                    "-fx-border-width: 1.5px;"
            );
        });

        // Top icon row with flanking divider lines and centered lightning bolt
        HBox topIconRow = new HBox(10);
        topIconRow.setAlignment(Pos.CENTER);
        topIconRow.setPadding(new Insets(0, 4, 0, 4));

        Region leftLine = new Region();
        HBox.setHgrow(leftLine, Priority.ALWAYS);
        leftLine.setStyle("-fx-background-color: rgba(16, 185, 129, 0.25); -fx-min-height: 1px; -fx-max-height: 1px;");

        StackPane boltBadge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BOLT, 15, Color.web("#059669")));
        boltBadge.setPrefSize(30, 30);
        boltBadge.setMaxSize(30, 30);
        boltBadge.setStyle(
                "-fx-background-color: rgba(16, 185, 129, 0.16); " +
                "-fx-background-radius: 999px; " +
                "-fx-border-color: rgba(16, 185, 129, 0.30); " +
                "-fx-border-radius: 999px; " +
                "-fx-border-width: 1px;"
        );

        Region rightLine = new Region();
        HBox.setHgrow(rightLine, Priority.ALWAYS);
        rightLine.setStyle("-fx-background-color: rgba(16, 185, 129, 0.25); -fx-min-height: 1px; -fx-max-height: 1px;");

        topIconRow.getChildren().addAll(leftLine, boltBadge, rightLine);

        // 2x2 Grid with 50% / 50% columns and 50% / 50% rows
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);
        VBox.setVgrow(grid, Priority.ALWAYS);

        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setPercentWidth(50);
        col1.setHgrow(Priority.ALWAYS);
        javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
        col2.setPercentWidth(50);
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        javafx.scene.layout.RowConstraints row1 = new javafx.scene.layout.RowConstraints();
        row1.setPercentHeight(50);
        row1.setVgrow(Priority.ALWAYS);
        javafx.scene.layout.RowConstraints row2 = new javafx.scene.layout.RowConstraints();
        row2.setPercentHeight(50);
        row2.setVgrow(Priority.ALWAYS);
        grid.getRowConstraints().addAll(row1, row2);

        grid.add(quickTile(ThemeManager.ICON_BIKE, "Find a Bike", "Check available\ncycles nearby",
                () -> onNavigate.accept("Fleet Catalog")), 0, 0);
        grid.add(quickTile(ThemeManager.ICON_MAP, "View Map", "Explore campus hubs\nand locations",
                () -> onNavigate.accept("Campus Map")), 1, 0);
        grid.add(quickTile(ThemeManager.ICON_CLOCK, "My Ride History", "View your past\nrides and stats",
                () -> onNavigate.accept("Passbook")), 0, 1);
        grid.add(quickTile(ThemeManager.ICON_WALLET, "Top Up Balance", "Add money to your\nCampus Pay",
                this::openTopUpDialog), 1, 1);

        card.getChildren().addAll(topIconRow, grid);
        return card;
    }

    private Button quickTile(String icon, String title, String sub, Runnable action) {
        VBox graphic = new VBox(4);
        graphic.setAlignment(Pos.CENTER);
        graphic.setPadding(new Insets(8, 8, 8, 8));

        StackPane badge = new StackPane(ThemeManager.createIcon(icon, 15, Color.web("#059669")));
        badge.setPrefSize(30, 30);
        badge.setMaxSize(30, 30);
        badge.getStyleClass().add("icon-badge");

        Label t = new Label(title);
        t.getStyleClass().add("quick-title");
        t.setAlignment(Pos.CENTER);
        t.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        t.setWrapText(true);
        t.setMaxWidth(Double.MAX_VALUE);

        Label s = new Label(sub.replace("\n", " "));
        s.getStyleClass().add("quick-sub");
        s.setAlignment(Pos.CENTER);
        s.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        s.setWrapText(true);
        s.setMaxWidth(Double.MAX_VALUE);
        s.setMinHeight(Region.USE_PREF_SIZE);

        // Small circular mint pill with chevron right
        StackPane arrowPill = new StackPane();
        arrowPill.setPrefSize(18, 18);
        arrowPill.setMaxSize(18, 18);
        arrowPill.getStyleClass().add("icon-badge");
        SVGPath chevron = new SVGPath();
        chevron.setContent("M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z");
        chevron.setScaleX(0.60);
        chevron.setScaleY(0.60);
        chevron.setFill(Color.web("#059669"));
        arrowPill.getChildren().add(chevron);

        graphic.getChildren().addAll(badge, t, s, arrowPill);

        Button tile = new Button();
        tile.setGraphic(graphic);
        tile.getStyleClass().add("quick-action");
        tile.setAccessibleText(title + " — " + sub.replace("\n", " "));
        tile.setTooltip(new Tooltip(title + " — " + sub.replace("\n", " ")));
        tile.setMaxHeight(Double.MAX_VALUE);
        tile.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(tile, Priority.ALWAYS);
        javafx.scene.layout.GridPane.setHgrow(tile, Priority.ALWAYS);
        javafx.scene.layout.GridPane.setVgrow(tile, Priority.ALWAYS);
        tile.setOnAction(e -> action.run());
        return tile;
    }

    // ---------- bottom row ----------

    private HBox createBottomRow() {
        HBox row = new HBox(14);
        VBox bikesCard = createBikesCard();
        HBox.setHgrow(bikesCard, Priority.ALWAYS);
        bikesCard.setMaxWidth(Double.MAX_VALUE);
        VBox mapCard = createMiniMapCard();
        mapCard.setPrefWidth(340);
        mapCard.setMinWidth(340);
        row.getChildren().addAll(bikesCard, mapCard);
        return row;
    }

    /**
     * Owner earnings strip: visible only when the viewer owns at least one
     * cycle. Numbers come from settled rentals; the card stays hidden on any
     * load failure rather than showing invented figures.
     */
    private void configureEarningsCard() {
        earningsCard.getStyleClass().add("section-card");
        earningsCard.setPadding(new Insets(18));

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_WALLET, 16, GREEN));
        badge.setPrefSize(32, 32);
        badge.getStyleClass().add("kpi-icon");
        Label title = new Label("My Bikes Earnings");
        title.getStyleClass().add("section-title");
        Label hint = new Label("95% of settled fares · 5% platform fee capped at ৳1.50");
        hint.getStyleClass().add("bike-meta");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button withdrawBtn = new Button("Withdraw to bKash");
        withdrawBtn.getStyleClass().add("range-pill");
        withdrawBtn.setTooltip(new Tooltip("Move wallet money to your bKash account"));
        withdrawBtn.setOnAction(e -> openWithdrawFlow());
        AnimationHelper.addPressAnimation(withdrawBtn);
        head.getChildren().addAll(badge, title, spacer, hint, withdrawBtn);

        HBox totals = new HBox(24);
        totals.setAlignment(Pos.CENTER_LEFT);
        VBox totalCol = new VBox(2);
        Label totalLbl = new Label("TOTAL EARNED");
        totalLbl.getStyleClass().add("metric-label");
        earnTotalValue.getStyleClass().add("bike-fare");
        totalCol.getChildren().addAll(totalLbl, earnTotalValue);
        VBox ridesCol = new VBox(2);
        Label ridesLbl = new Label("SETTLED RIDES");
        ridesLbl.getStyleClass().add("metric-label");
        earnRidesValue.getStyleClass().add("bike-fare");
        ridesCol.getChildren().addAll(ridesLbl, earnRidesValue);
        totals.getChildren().addAll(totalCol, ridesCol);

        earningsCard.getChildren().addAll(head, totals, earnBikeRows);
    }

    private void renderEarnings() {
        if (disposed || user == null) return;
        boolean ownsAny = cycles.stream().anyMatch(c -> c.ownerId().equals(user.id()));
        if (!ownsAny) {
            earningsCard.setVisible(false);
            earningsCard.setManaged(false);
            return;
        }
        AppExecutor.asyncThenFx(
                () -> repo.ownerEarnings(user, user.id()),
                earnings -> {
                    if (disposed) return;
                    earnTotalValue.setText(Money.formatTaka(earnings.netPayoutPoisha()));
                    earnRidesValue.setText(String.valueOf(earnings.settledRides()));
                    earnBikeRows.getChildren().clear();
                    for (OwnerEarnings.BikeEarning b : earnings.bikes()) {
                        HBox row = new HBox(8);
                        row.setAlignment(Pos.CENTER_LEFT);
                        Label name = new Label(b.cycleLabel());
                        name.getStyleClass().add("bike-meta");
                        name.setMaxWidth(260);
                        Region sp = new Region();
                        HBox.setHgrow(sp, Priority.ALWAYS);
                        Label stat = new Label(b.settledRides() + (b.settledRides() == 1 ? " ride · " : " rides · ")
                                + Money.formatTaka(b.netPayoutPoisha()));
                        stat.getStyleClass().add("bike-meta");
                        row.getChildren().addAll(name, sp, stat);
                        earnBikeRows.getChildren().add(row);
                    }
                    earningsCard.setVisible(true);
                    earningsCard.setManaged(true);
                },
                err -> {
                    if (disposed) return;
                    earningsCard.setVisible(false);
                    earningsCard.setManaged(false);
                });
    }

    private VBox createBikesCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(18));

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 16, GREEN));
        badge.setPrefSize(32, 32);
        badge.getStyleClass().add("kpi-icon");
        Label title = new Label("Available Bikes Near You");
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        viewAllBtn.getStyleClass().add("range-pill");
        viewAllBtn.setOnAction(e -> onNavigate.accept("Fleet Catalog"));
        head.getChildren().addAll(badge, title, spacer, viewAllBtn);

        bikeSpinner = new ProgressIndicator();
        bikeSpinner.setMaxSize(28, 28);
        bikeRow.setAlignment(Pos.CENTER);
        bikeRow.getChildren().add(bikeSpinner);

        card.getChildren().addAll(head, bikeRow);
        return card;
    }

    private VBox createMiniMapCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(18));

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane(ThemeManager.createIcon(ThemeManager.ICON_PIN, 16, GREEN));
        badge.setPrefSize(32, 32);
        badge.setMinSize(32, 32);
        badge.getStyleClass().add("kpi-icon");
        Label title = new Label("Campus Hubs");
        title.getStyleClass().add("section-title");
        title.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button openFull = new Button("Full Map");
        openFull.getStyleClass().add("range-pill");
        openFull.setMinWidth(Region.USE_PREF_SIZE);
        openFull.setOnAction(e -> onNavigate.accept("Campus Map"));
        head.getChildren().addAll(badge, title, spacer, openFull);

        mapWrapper.setPrefHeight(300);
        mapSpinner = new ProgressIndicator();
        mapSpinner.setMaxSize(30, 30);
        mapWrapper.getChildren().add(mapSpinner);

        StackPane mapStack = new StackPane(mapWrapper);

        VBox zoomBox = new VBox(6);
        zoomBox.setAlignment(Pos.TOP_RIGHT);
        zoomBox.setPadding(new Insets(10));
        zoomBox.setPickOnBounds(false);
        Button zoomIn = new Button("+");
        zoomIn.getStyleClass().add("map-zoom-btn");
        zoomIn.setAccessibleText("Zoom in on campus map");
        zoomIn.setTooltip(new Tooltip("Zoom in"));
        zoomIn.setOnAction(e -> {
            if (dashMap != null) dashMap.zoomIn();
        });
        Button zoomOut = new Button("−");
        zoomOut.getStyleClass().add("map-zoom-btn");
        zoomOut.setAccessibleText("Zoom out of campus map");
        zoomOut.setTooltip(new Tooltip("Zoom out"));
        zoomOut.setOnAction(e -> {
            if (dashMap != null) dashMap.zoomOut();
        });
        zoomBox.getChildren().addAll(zoomIn, zoomOut);

        StackPane overlayStack = new StackPane(mapStack, zoomBox);
        StackPane.setAlignment(zoomBox, Pos.TOP_RIGHT);

        card.getChildren().addAll(head, overlayStack);
        return card;
    }

    // ---------- data ----------

    private void loadDataAsync() {
        AppExecutor.asyncThenFx(
                () -> {
                    List<CycleItem> catalog;
                    try {
                        catalog = repo.catalog(user);
                    } catch (Exception e) {
                        catalog = List.of();
                    }
                    RentalRecord act = null;
                    try {
                        act = repo.activeRental(user);
                    } catch (Exception ignored) {
                    }
                    List<RentalRecord> history;
                    try {
                        history = repo.rentals(user);
                    } catch (Exception e) {
                        history = List.of();
                    }
                    return new DashPayload(catalog, act, history);
                },
                payload -> {
                    if (disposed) return;
                    cycles = new ArrayList<>(payload.catalog());
                    active = payload.active();
                    personalHistory = new ArrayList<>(payload.history());
                    renderKpis(payload);
                    renderBikes();
                    renderEarnings();
                    renderDuesBanner();
                    renderMiniMap();
                    computeChart();
                },
                err -> {
                    if (disposed) return;
                    renderUnavailableState();
                });
        AppExecutor.asyncThenFx(
                () -> {
                    WalletService ws = WalletService.getInstance();
                    int bal = ws.getBalancePoisha(user.id());
                    List<WalletTransaction> txs;
                    try {
                        txs = ws.getTransactions(user.id());
                    } catch (Exception e) {
                        txs = List.of();
                    }
                    return new WalletPayload(bal, txs);
                },
                wp -> {
                    if (disposed) return;
                    payBalance.setText(Money.formatTaka(wp.bal()));
                    lastPayTxs = new ArrayList<>(wp.txs());
                    drawPaySpark(lastPayTxs);
                },
                err -> {
                    if (disposed) return;
                    payBalance.setText("BDT --");
                    topUpButton.setDisable(true);
                });
    }

    /** Single error state: stops spinners, blanks every metric, disables Top Up. */
    private void renderUnavailableState() {
        if (bikeSpinner != null) {
            bikeSpinner.setVisible(false);
            bikeSpinner.setManaged(false);
        }
        if (mapSpinner != null) {
            mapSpinner.setVisible(false);
            mapSpinner.setManaged(false);
        }
        availableValue.setText("—");
        availableDelta.setText("Unavailable");
        hubsValue.setText("—");
        commuteTitle.setText("Unavailable");
        commuteSub.setText("Connect to load your commute");
        commuteBtn.setText("Find a Bike");
        viewAllBtn.setText("View All Cycles");
        legRidesLabel.setText("Rides (—)");
        legYesterdayLabel.setText("Yesterday (—)");
        statTotalValue.setText("—");
        statBestValue.setText("—");
        statFleetValue.setText("—");
        payBalance.setText("BDT --");
        topUpButton.setDisable(true);
        bikeRow.getChildren().clear();
        bikeRow.setAlignment(Pos.CENTER_LEFT);
        Label empty = new Label("Couldn't load bikes — check your connection.");
        empty.getStyleClass().add("bike-meta");
        bikeRow.getChildren().add(empty);
        earningsCard.setVisible(false);
        earningsCard.setManaged(false);
        duesBanner.setVisible(false);
        duesBanner.setManaged(false);
    }

    private record DashPayload(List<CycleItem> catalog, RentalRecord active, List<RentalRecord> history) {
    }

    private record WalletPayload(int bal, List<WalletTransaction> txs) {
    }

    private void renderKpis(DashPayload payload) {
        long liveAvailable = payload.catalog().stream()
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE).count();
        availableValue.setText(String.valueOf(liveAvailable));
        hubsValue.setText(String.valueOf(CampusHubs.ALL.size()));
        viewAllBtn.setText("View All Cycles (" + payload.catalog().size() + ")");

        long yesterdayRides = ridesYesterday(payload.history());
        availableDelta.setText("+" + yesterdayRides + " rides yesterday");

        if (payload.active() != null) {
            commuteTitle.setText(payload.active().cycleLabel());
            long mins = Math.max(0, java.time.Duration.between(
                    payload.active().startedAt(), CampusTime.now()).toMinutes());
            commuteSub.setText("On ride for " + mins + " min");
            commuteBtn.setText("Go to Ride");
        } else {
            commuteTitle.setText("No active ride");
            commuteSub.setText("Start your next journey");
            commuteBtn.setText("Find a Bike");
        }
        restartElapsedCounter();
    }

    /** Personal rides started yesterday — the same ledger the chart draws from. */
    private long ridesYesterday(List<RentalRecord> history) {
        LocalDate yesterday = LocalDate.now(CampusTime.DHAKA).minusDays(1);
        Set<String> seen = new HashSet<>();
        long count = 0;
        for (RentalRecord r : history) {
            if (r.startedAt() == null || !seen.add(r.id())) continue;
            if (r.startedAt().withZoneSameInstant(CampusTime.DHAKA).toLocalDate().equals(yesterday)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Owner cash-out: amount + bKash number collected, then an honest
     * "coming soon" stop — no bKash API is connected, so no money moves
     * and no ledger row is written. Validation is real (positive amount
     * within wallet balance, 11-digit number) so the UI is correct on day one.
     */
    private void openWithdrawFlow() {
        if (disposed || user == null) return;
        AppExecutor.asyncThenFx(
                () -> WalletService.getInstance().getBalancePoisha(user.id()),
                balance -> {
                    if (disposed) return;
                    Dialog<Void> dialog = new Dialog<>();
                    dialog.setTitle("Withdraw to bKash");
                    dialog.setHeaderText("Move wallet money to bKash · available "
                            + Money.formatTaka(balance));
                    ThemeManager.install(dialog.getDialogPane());

                    GridPane grid = new GridPane();
                    grid.setHgap(12);
                    grid.setVgap(12);
                    grid.setPadding(new Insets(20));

                    TextField amountField = new TextField();
                    amountField.setPromptText("Amount in ৳ (e.g. 500.00)");
                    Label amountError = new Label();
                    amountError.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
                    amountError.setVisible(false);
                    amountError.setManaged(false);

                    TextField bkashField = new TextField();
                    bkashField.setPromptText("bKash number (01XXXXXXXXX)");
                    Label bkashError = new Label();
                    bkashError.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: -fx-danger;");
                    bkashError.setVisible(false);
                    bkashError.setManaged(false);

                    grid.add(new Label("Amount (৳):"), 0, 0);
                    grid.add(amountField, 1, 0);
                    grid.add(amountError, 1, 1);
                    grid.add(new Label("bKash Number:"), 0, 2);
                    grid.add(bkashField, 1, 2);
                    grid.add(bkashError, 1, 3);

                    dialog.getDialogPane().setContent(grid);
                    ButtonType confirmType = new ButtonType("Send Money", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

                    dialog.getDialogPane().lookupButton(confirmType).addEventFilter(ActionEvent.ACTION, evt -> {
                        boolean ok = true;
                        int amountPoisha = 0;
                        try {
                            amountPoisha = Money.parseBdtToPoisha(amountField.getText());
                            if (amountPoisha <= 0 || amountPoisha > balance) {
                                throw new IllegalArgumentException(
                                        "Amount must be between ৳1 and your balance of "
                                                + Money.formatTaka(balance) + ".");
                            }
                            amountError.setVisible(false);
                            amountError.setManaged(false);
                        } catch (IllegalArgumentException ex) {
                            amountError.setText(ex.getMessage());
                            amountError.setVisible(true);
                            amountError.setManaged(true);
                            ok = false;
                        }
                        String number = bkashField.getText() == null ? "" : bkashField.getText().trim();
                        if (!number.matches("^01[3-9]\\d{8}$")) {
                            bkashError.setText("Enter a valid 11-digit bKash number starting with 01.");
                            bkashError.setVisible(true);
                            bkashError.setManaged(true);
                            ok = false;
                        } else {
                            bkashError.setVisible(false);
                            bkashError.setManaged(false);
                        }
                        if (!ok) {
                            evt.consume();
                            return;
                        }
                        evt.consume();
                        int finalAmount = amountPoisha;
                        String finalNumber = number;
                        Platform.runLater(() -> showWithdrawComingSoon(finalAmount, finalNumber));
                    });

                    dialog.showAndWait();
                },
                err -> {
                    if (disposed) return;
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            "Could not load wallet balance. Please retry.", ButtonType.OK);
                    ThemeManager.install(alert.getDialogPane());
                    alert.showAndWait();
                });
    }

    private void showWithdrawComingSoon(int amountPoisha, String bkashNumber) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        ThemeManager.install(alert.getDialogPane());
        alert.setTitle("Withdraw to bKash");
        alert.setHeaderText("Feature coming soon");
        String masked = bkashNumber.length() >= 4
                ? "XXXXXXX" + bkashNumber.substring(bkashNumber.length() - 4)
                : bkashNumber;
        alert.setContentText("bKash payouts are not connected yet, so no money moved.\n\n"
                + "Your request (" + Money.formatTaka(amountPoisha) + " to " + masked + ") "
                + "is noted — your wallet balance is unchanged.");
        alert.showAndWait();
    }

    private void configureDuesBanner() {
        duesBanner.setAlignment(Pos.CENTER_LEFT);
        duesBanner.setPadding(new Insets(12, 18, 12, 18));
        duesBanner.setStyle("-fx-background-color: rgba(245, 158, 11, 0.12); -fx-background-radius: 12px; "
                + "-fx-border-color: #F59E0B; -fx-border-radius: 12px; -fx-border-width: 1px;");
        Label warnIcon = new Label("⚠️");
        duesBannerLabel.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #B45309;");
        duesBannerLabel.setWrapText(true);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button topUpNow = new Button("Top Up to Clear");
        topUpNow.getStyleClass().add("pay-topup");
        topUpNow.setOnAction(e -> openTopUpDialog());
        AnimationHelper.addPressAnimation(topUpNow);
        duesBanner.getChildren().addAll(warnIcon, duesBannerLabel, sp, topUpNow);
    }

    private void renderDuesBanner() {
        if (disposed || user == null) return;
        AppExecutor.asyncThenFx(
                () -> repo.unpaidDuesTotal(user, user.id()),
                owed -> {
                    if (disposed) return;
                    if (owed > 0) {
                        duesBannerLabel.setText("Outstanding ride dues of " + Money.formatTaka(owed)
                                + " — new bookings stay blocked until cleared. Top up and your next booking auto-settles oldest dues first.");
                        duesBanner.setVisible(true);
                        duesBanner.setManaged(true);
                    } else {
                        duesBanner.setVisible(false);
                        duesBanner.setManaged(false);
                    }
                },
                err -> {
                    if (disposed) return;
                    duesBanner.setVisible(false);
                    duesBanner.setManaged(false);
                });
    }

    private void renderBikes() {
        bikeRow.getChildren().clear();
        bikeRow.setAlignment(Pos.CENTER_LEFT);
        CampusHubs.Hub home = homeHub();
        String mine = active != null ? active.cycleId() : null;
        List<CycleItem> nearest = cycles.stream()
                .filter(c -> c.reviewStatus() == ReviewStatus.APPROVED)
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE
                        || (mine != null && mine.equals(c.id())))
                .sorted((a, b) -> {
                    boolean aMine = mine != null && mine.equals(a.id());
                    boolean bMine = mine != null && mine.equals(b.id());
                    if (aMine != bMine) return bMine ? 1 : -1;
                    return Double.compare(
                            CampusHubs.distanceMetres(home.lat(), home.lng(), a.latitude(), a.longitude()),
                            CampusHubs.distanceMetres(home.lat(), home.lng(), b.latitude(), b.longitude()));
                })
                .limit(3)
                .toList();
        if (nearest.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER_LEFT);
            Label msg = new Label("No bikes available right now.");
            msg.getStyleClass().add("bike-meta");
            Button browse = new Button("Browse Fleet Catalog");
            browse.getStyleClass().add("kpi-outline-btn");
            browse.setOnAction(e -> onNavigate.accept("Fleet Catalog"));
            empty.getChildren().addAll(msg, browse);
            bikeRow.getChildren().add(empty);
            return;
        }
        for (CycleItem c : nearest) {
            VBox card = bikeCard(c, home);
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
            bikeRow.getChildren().add(card);
        }
    }

    /** Single shared disabled-reason resolver so every surface agrees. */
    private String disabledReason(CycleItem cycle) {
        if (cycle.reviewStatus() != ReviewStatus.APPROVED) return "Awaiting review";
        if (cycle.availabilityStatus() == AvailabilityStatus.MAINTENANCE
                || cycle.availabilityStatus() == AvailabilityStatus.QUARANTINE
                || cycle.availabilityStatus() == AvailabilityStatus.RETIRED) {
            return "Under Maintenance";
        }
        if (cycle.availabilityStatus() == AvailabilityStatus.RENTED) return "Rented";
        if (cycle.ownerId().equals(user.id())) return "Own Cycle";
        return "Unavailable";
    }

    private VBox bikeCard(CycleItem cycle, CampusHubs.Hub home) {
        VBox card = new VBox(8);
        card.getStyleClass().add("bike-card");
        card.setPadding(new Insets(14));

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label type = new Label(cycle.type() == CycleType.ELECTRIC_BIKE ? "ELECTRIC BIKE" : (cycle.type() == CycleType.ROAD_BIKE ? "ROAD BIKE" : "CITY BIKE"));
        type.getStyleClass().add("bike-type");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button heart = new Button();
        heart.getStyleClass().add("map-zoom-btn");
        boolean fav = isFavorite(cycle.id());
        heart.setAccessibleText(fav ? "Remove from favourites" : "Add to favourites");
        heart.setTooltip(new Tooltip(fav ? "Remove from favourites" : "Add to favourites"));
        heart.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_HEART, 14,
                fav ? Color.web("#DC2626") : Color.web("#94A3B8")));
        heart.setOnAction(e -> {
            toggleFavorite(cycle.id());
            boolean nowFav = isFavorite(cycle.id());
            heart.setGraphic(ThemeManager.createIcon(ThemeManager.ICON_HEART, 14,
                    nowFav ? Color.web("#DC2626") : Color.web("#94A3B8")));
            heart.setAccessibleText(nowFav ? "Remove from favourites" : "Add to favourites");
            heart.setTooltip(new Tooltip(nowFav ? "Remove from favourites" : "Add to favourites"));
        });
        top.getChildren().addAll(type, spacer, heart);

        StackPane art = new StackPane();
        art.setPrefHeight(105);
        art.setAlignment(Pos.CENTER);
        javafx.scene.image.Image bikeImg = BikeArt.imageFor(cycle.type(), cycle.label());
        if (bikeImg != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(bikeImg);
            iv.setFitWidth(180);
            iv.setFitHeight(95);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            art.getChildren().add(iv);
        } else {
            art.getChildren().add(ThemeManager.createIcon(ThemeManager.ICON_BIKE, 56, GREEN));
        }

        Label name = new Label(cycle.label());
        name.getStyleClass().add("bike-name");

        HBox meta = new HBox(6);
        meta.setAlignment(Pos.CENTER_LEFT);
        SVGPath pin = ThemeManager.createIcon(ThemeManager.ICON_PIN, 12, Color.web("#64748B"));
        Label hub = new Label(cycle.pickupPoint());
        hub.getStyleClass().add("bike-meta");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        long meters = Math.round(CampusHubs.distanceMetres(
                home.lat(), home.lng(), cycle.latitude(), cycle.longitude()));
        Label dist = new Label(meters < 1000
                ? meters + " m"
                : String.format(Locale.US, "%.1f km", meters / 1000.0));
        dist.getStyleClass().add("bike-meta");
        meta.getChildren().addAll(pin, hub, sp2, dist);

        HBox fareRow = new HBox(4);
        fareRow.setAlignment(Pos.BASELINE_LEFT);
        Label fare = new Label(Money.formatTaka(netFarePoisha()));
        fare.getStyleClass().add("bike-fare");
        Label unit = new Label("/ 15m");
        unit.getStyleClass().add("bike-meta");
        fareRow.getChildren().addAll(fare, unit);

        HBox bottom = new HBox(8);
        bottom.setAlignment(Pos.CENTER_LEFT);
        boolean isStudent = user.role() == Role.STUDENT;
        Label perk = new Label(Math.round(TariffService.STUDENT_SUBSIDY_RATE * 100) + "% Student Perk");
        perk.getStyleClass().add("bike-perk");
        perk.setVisible(isStudent);
        perk.setManaged(isStudent);
        Region sp3 = new Region();
        HBox.setHgrow(sp3, Priority.ALWAYS);
        Button reserve = new Button("Reserve");
        reserve.getStyleClass().add("bike-reserve");
        boolean isMyRide = active != null && active.cycleId().equals(cycle.id());
        boolean canBook = cycle.canBeBookedBy(user.id());
        if (isMyRide) {
            reserve.setDisable(false);
            reserve.setText("Your Ride  →");
            reserve.setTooltip(new Tooltip("Open your active ride"));
            reserve.setOnAction(e -> onNavigate.accept("Active Journey"));
        } else if (!canBook) {
            String reason = disabledReason(cycle);
            reserve.setText(reason);
            reserve.setTooltip(new Tooltip(reason));
        } else {
            reserve.setOnAction(e -> onReserve.accept(cycle));
        }
        AnimationHelper.addPressAnimation(reserve);
        bottom.getChildren().addAll(perk, sp3, reserve);

        card.getChildren().addAll(top, art, name, meta, fareRow, bottom);
        return card;
    }

    private int netFarePoisha() {
        int quote = TariffService.quotePoisha(15);
        int discount = TariffService.subsidyPoisha(quote, user.role() == Role.STUDENT);
        return quote - discount;
    }

    private CampusHubs.Hub homeHub() {
        try {
            String fav = Preferences.userNodeForPackage(SettingsModal.class).get("favoriteHub", CampusHubs.names().get(0));
            return CampusHubs.findByName(fav).orElse(CampusHubs.ALL.get(0));
        } catch (Exception e) {
            return CampusHubs.ALL.get(0);
        }
    }

    private boolean isFavorite(String cycleId) {
        return favoriteSet().contains(cycleId);
    }

    private void toggleFavorite(String cycleId) {
        Set<String> set = favoriteSet();
        if (!set.remove(cycleId)) set.add(cycleId);
        prefs.put("favoriteCycles", String.join(",", set));
    }

    private Set<String> favoriteSet() {
        String raw = prefs.get("favoriteCycles", "");
        Set<String> set = new HashSet<>();
        for (String s : raw.split(",")) {
            if (!s.isBlank()) set.add(s.trim());
        }
        return set;
    }

    private void renderMiniMap() {
        if (disposed) return;
        if (dashMap != null) {
            // BingMapView has no setCycles method: push fresh cycles into the live view
            // instead of spinning up a new WebView on every refresh.
            dashMap.pushCycles(cycles);
        } else {
            BingMapView map = new BingMapView(cycles, hub -> onNavigate.accept("Campus Map"), onLocationChanged);
            if (disposed) {
                map.dispose();
                return;
            }
            dashMap = map;
            map.setPrefHeight(300);
            mapWrapper.getChildren().setAll(map);
        }
        if (mapSpinner != null) {
            mapSpinner.setVisible(false);
            mapSpinner.setManaged(false);
        }
        CampusHubs.Hub home = homeHub();
        CampusHubs.Hub near = CampusHubs.nearest(home.lat(), home.lng());
        if (onLocationChanged != null) onLocationChanged.accept(near.name());
    }

    /**
     * Personal commute history: distinct rentals the student started per day over the
     * last 30 days, capped at fleet size. Both the 7- and 30-day pills redraw from
     * these same arrays. No fleet-wide claims are derived from personal data.
     */
    private void computeChart() {
        LocalDate today = LocalDate.now(CampusTime.DHAKA);
        int fleet = Math.max(1, cycles.size());
        for (int i = 0; i < 30; i++) {
            LocalDate day = today.minusDays(29 - i);
            dayDates[i] = day;
            Set<String> seen = new HashSet<>();
            int rides = 0;
            for (RentalRecord r : personalHistory) {
                if (r.startedAt() == null || !seen.add(r.id())) continue;
                if (r.startedAt().withZoneSameInstant(CampusTime.DHAKA).toLocalDate().equals(day)) {
                    rides++;
                }
            }
            dayRides[i] = Math.min(rides, fleet);
        }

        int total = 0;
        int best = 0;
        int bestIdx = 29;
        for (int i = 0; i < 30; i++) {
            total += dayRides[i];
            if (dayRides[i] > best) {
                best = dayRides[i];
                bestIdx = i;
            }
        }
        long yesterdayRides = dayRides[28];
        legRidesLabel.setText("Rides (" + total + ")");
        legYesterdayLabel.setText("Yesterday (+" + yesterdayRides + ")");

        statTotalValue.setText(total + (total == 1 ? " ride" : " rides"));
        String bestDate = dayDates[bestIdx] != null
                ? dayDates[bestIdx].format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) : "—";
        statBestValue.setText(best > 0 ? bestDate + " (" + best + ")" : "—");

        long rented = cycles.stream()
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.RENTED).count();
        int fleetSize = Math.max(1, cycles.size());
        statFleetValue.setText(Math.round(rented * 100.0 / fleetSize) + "% in use");

        drawChart();
    }

    private void drawPaySpark(List<WalletTransaction> txs) {
        GraphicsContext g = paySpark.getGraphicsContext2D();
        double w = paySpark.getWidth(), h = paySpark.getHeight();
        g.clearRect(0, 0, w, h);
        List<Integer> points = new ArrayList<>();
        if (txs != null) {
            for (int i = txs.size() - 1; i >= 0 && points.size() < 12; i--) {
                points.add(txs.get(i).balanceAfterPoisha());
            }
        }
        if (points.isEmpty()) {
            // No ledger yet: leave the sparkline blank rather than inventing money.
            return;
        }
        int max = points.stream().mapToInt(Integer::intValue).max().orElse(1);
        int min = points.stream().mapToInt(Integer::intValue).min().orElse(0);
        int span = Math.max(1, max - min);
        double[] xs = new double[points.size()], ys = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = 4 + (w - 8) * (points.size() == 1 ? 1 : (double) i / (points.size() - 1));
            ys[i] = h - 6 - (h - 12) * (points.get(i) - min) / span;
        }
        g.setStroke(Color.web("#10B981", 0.9));
        g.setLineWidth(2);
        g.beginPath();
        g.moveTo(xs[0], ys[0]);
        for (int i = 0; i < xs.length - 1; i++) {
            double xc = (xs[i] + xs[i + 1]) / 2, yc = (ys[i] + ys[i + 1]) / 2;
            g.quadraticCurveTo(xs[i], ys[i], xc, yc);
        }
        if (xs.length > 0) g.lineTo(xs[xs.length - 1], ys[ys.length - 1]);
        g.stroke();
    }
}
