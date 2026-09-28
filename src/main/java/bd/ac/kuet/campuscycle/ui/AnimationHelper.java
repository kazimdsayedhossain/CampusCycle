package bd.ac.kuet.campuscycle.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Centralized animation utilities for CampusCycle.
 * Provides toast notifications, page transitions, ambient effects,
 * and staggered entrance animations.
 */
public final class AnimationHelper {

    private AnimationHelper() {}

    // ---------- Toast / Snackbar System ----------

    private static final ConcurrentLinkedQueue<Toast> toastQueue = new ConcurrentLinkedQueue<>();
    private static VBox toastContainer;
    /** Host currently holding the toast container (roots are replaced on workspace rebuild). */
    private static StackPane toastHost;

    public record Toast(String message, ToastType type, Duration duration) {
        public Toast(String message, ToastType type) {
            this(message, type, Duration.seconds(4));
        }
    }

    public enum ToastType {
        INFO, SUCCESS, WARNING, ERROR
    }

    /**
     * Attaches the toast container to the given scene root. Safe to call on
     * every workspace rebuild — the single container is moved, never duplicated.
     */
    public static void initToastContainer(StackPane root) {
        if (root == null) return;
        if (toastContainer == null) {
            toastContainer = new VBox(8);
            toastContainer.setAlignment(Pos.BOTTOM_RIGHT);
            toastContainer.setPadding(new javafx.geometry.Insets(16));
            toastContainer.setPickOnBounds(false);
            toastContainer.setMouseTransparent(true);
        } else if (toastContainer.getParent() instanceof Pane oldParent) {
            oldParent.getChildren().remove(toastContainer);
        }
        if (!root.getChildren().contains(toastContainer)) {
            StackPane.setAlignment(toastContainer, Pos.BOTTOM_RIGHT);
            root.getChildren().add(toastContainer);
        }
        toastHost = root;
        // Keep toasts above page content but below modal dialogs (added later).
        toastContainer.toFront();
    }

    /** Shows a toast notification with slide-in animation. */
    public static void showToast(String message, ToastType type) {
        showToast(message, type, Duration.seconds(4));
    }

    public static void showToast(String message, ToastType type, Duration duration) {
        if (toastContainer == null || toastHost == null
                || toastHost.getScene() == null) return;

        Platform.runLater(() -> {
            VBox toast = createToast(message, type);
            toastContainer.getChildren().add(0, toast);

            // Slide in from right
            toast.setTranslateX(400);
            toast.setOpacity(0);
            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), toast);
            slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
            fadeIn.setToValue(1.0);

            ParallelTransition enter = new ParallelTransition(slideIn, fadeIn);
            enter.setOnFinished(e -> {
                PauseTransition pause = new PauseTransition(duration);
                pause.setOnFinished(ev -> hideToast(toast));
                pause.play();
            });
            enter.play();
        });
    }

    private static VBox createToast(String message, ToastType type) {
        VBox toast = new VBox(6);
        toast.getStyleClass().add("toast");
        toast.setPadding(new javafx.geometry.Insets(14, 18, 14, 18));
        toast.setMaxWidth(380);
        toast.setMouseTransparent(false);

        String bgColor, textColor, iconPath;
        switch (type) {
            case SUCCESS -> { bgColor = "#ECFDF5"; textColor = "#059669"; iconPath = ThemeManager.ICON_CHECK; }
            case WARNING -> { bgColor = "#FFFBEB"; textColor = "#D97706"; iconPath = ThemeManager.ICON_ALERT; }
            case ERROR -> { bgColor = "#FEF2F2"; textColor = "#DC2626"; iconPath = ThemeManager.ICON_CLOSE; }
            default -> { bgColor = "#EFF6FF"; textColor = "#2563EB"; iconPath = ThemeManager.ICON_INFO; }
        }

        toast.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 12px; " +
                "-fx-border-color: " + textColor + "; -fx-border-radius: 12px; -fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.15), 24, 0, 0, 8);");

        HBox content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);

        SVGPath icon = ThemeManager.createIcon(iconPath, 18, Color.web(textColor));
        Label msg = new Label(message);
        msg.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: " + textColor + ";");
        msg.setWrapText(true);
        msg.setMaxWidth(300);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label closeBtn = new Label("✕");
        closeBtn.setStyle("-fx-font-size: 14px; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-opacity: 0.7;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-font-size: 14px; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-opacity: 1;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-font-size: 14px; -fx-text-fill: " + textColor + "; -fx-cursor: hand; -fx-opacity: 0.7;"));
        closeBtn.setOnMouseClicked(e -> hideToast(toast));

        content.getChildren().addAll(icon, msg, spacer, closeBtn);
        toast.getChildren().add(content);
        return toast;
    }

    private static void hideToast(VBox toast) {
        if (toast == null || toast.getParent() == null) return;
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(250), toast);
        slideOut.setToX(400);
        slideOut.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), toast);
        fadeOut.setToValue(0);
        ParallelTransition exit = new ParallelTransition(slideOut, fadeOut);
        exit.setOnFinished(e -> Platform.runLater(() -> toastContainer.getChildren().remove(toast)));
        exit.play();
    }

    // ---------- Page Transition Animations ----------

    /** Ongoing transition per host — a new navigation cancels the previous one. */
    private static final java.util.Map<StackPane, Transition> activeTransitions =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Fades out old content, fades in new content with slide. */
    public static void transitionContent(StackPane contentHost, Node newContent, Runnable onComplete) {
        if (contentHost == null || newContent == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        // Cancel any in-flight transition so rapid clicks can't leave stale callbacks fighting.
        Transition stale = activeTransitions.remove(contentHost);
        if (stale != null) stale.stop();

        Node oldContent = contentHost.getChildren().isEmpty() ? null : contentHost.getChildren().get(0);

        if (oldContent == null) {
            newContent.setOpacity(0);
            newContent.setTranslateY(20);
            contentHost.getChildren().setAll(newContent);
            FadeTransition ft = new FadeTransition(Duration.millis(200), newContent);
            ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(300), newContent);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition pt = new ParallelTransition(ft, tt);
            activeTransitions.put(contentHost, pt);
            pt.setOnFinished(e -> {
                activeTransitions.remove(contentHost, pt);
                // Reset transform state so a cancelled-then-replayed node never sticks offset.
                newContent.setTranslateY(0);
                if (onComplete != null) onComplete.run();
            });
            pt.play();
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), oldContent);
        fadeOut.setToValue(0);
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(200), oldContent);
        slideOut.setToX(-30);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition exit = new ParallelTransition(fadeOut, slideOut);
        activeTransitions.put(contentHost, exit);
        exit.setOnFinished(e -> {
            newContent.setOpacity(0);
            newContent.setTranslateY(20);
            contentHost.getChildren().setAll(newContent);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), newContent);
            fadeIn.setToValue(1);
            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), newContent);
            slideIn.setToY(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition enter = new ParallelTransition(fadeIn, slideIn);
            activeTransitions.put(contentHost, enter);
            enter.setOnFinished(ev -> {
                activeTransitions.remove(contentHost, enter);
                newContent.setTranslateX(0);
                newContent.setTranslateY(0);
                if (onComplete != null) onComplete.run();
            });
            enter.play();
        });
        exit.play();
    }

    // ---------- Staggered Entrance Animations ----------

    /**
     * Animates children of a container with staggered fade+slide entrance.
     * Call after the container is added to the scene.
     */
    public static void staggerEntrance(VBox container, Duration baseDelay, Duration stagger) {
        staggerEntrance(container.getChildren(), baseDelay, stagger);
    }

    public static void staggerEntrance(javafx.collections.ObservableList<Node> children,
                                       Duration baseDelay, Duration stagger) {
        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            child.setOpacity(0);
            child.setTranslateY(30);
            int index = i;
            PauseTransition delay = new PauseTransition(baseDelay.add(stagger.multiply(index)));
            delay.setOnFinished(e -> {
                FadeTransition ft = new FadeTransition(Duration.millis(400), child);
                ft.setToValue(1);
                TranslateTransition tt = new TranslateTransition(Duration.millis(500), child);
                tt.setToY(0);
                tt.setInterpolator(Interpolator.EASE_OUT);
                ParallelTransition pt = new ParallelTransition(ft, tt);
                pt.play();
            });
            delay.play();
        }
    }

    // ---------- Ambient Floating Leaf ----------

    /**
     * Fill colour for the falling leaf, fully opaque on purpose. The fade is owned
     * entirely by {@link #AMBIENT_LEAF_PEAK_OPACITY}; baking alpha into the fill as
     * well multiplied the two and left the leaf at roughly 8% alpha, which is why it
     * never appeared.
     */
    public static final String AMBIENT_LEAF_FILL = "#10B981";

    /** Strongest alpha the leaf reaches mid-drift. Faint, but actually visible. */
    public static final double AMBIENT_LEAF_PEAK_OPACITY = 0.32;

    private static LeafFloater leafFloater;

    /** Starts a subtle leaf floating across the window. */
    public static void startAmbientLeaf(Pane overlayLayer) {
        stopAmbientLeaf();
        leafFloater = new LeafFloater(overlayLayer);
        leafFloater.start();
    }

    public static void stopAmbientLeaf() {
        if (leafFloater != null) {
            leafFloater.stop();
            leafFloater = null;
        }
    }

    private static class LeafFloater {
        private final Pane layer;
        private final SVGPath leaf;
        private AnimationTimer timer;
        private long startNs;
        private double startX, baseY, endX, amplitude, wavelength, durationS, peakOpacity, spin;

        LeafFloater(Pane layer) {
            this.layer = layer;
            this.leaf = ThemeManager.createIcon(ThemeManager.ICON_LEAF, 26, Color.web(AMBIENT_LEAF_FILL));
            this.leaf.setMouseTransparent(true);
            this.leaf.setOpacity(0);
        }

        void start() {
            if (!layer.getChildren().contains(leaf)) {
                layer.getChildren().add(leaf);
            }
            // Defer first cycle until the layer actually has size.
            Platform.runLater(this::runCycle);
        }

        private void runCycle() {
            double w = layer.getWidth();
            double h = layer.getHeight();
            if (w <= 50 || h <= 50) {
                // Layout not ready — retry shortly instead of animating a zero-size path.
                PauseTransition retry = new PauseTransition(Duration.millis(500));
                retry.setOnFinished(e -> {
                    if (leaf.getParent() != null) runCycle();
                });
                retry.play();
                return;
            }
            startX = -60;
            endX = w + 60;
            // Drift through the content area, not just the margins: the page is
            // translucent so the leaf reads across cards and the map, and the top
            // band is kept clear of the header controls.
            baseY = 120 + Math.random() * Math.max(60, h - 300);
            amplitude = 14 + Math.random() * 18;
            wavelength = 150 + Math.random() * 140;
            durationS = 11 + Math.random() * 7; // slow, ambient — never distracting
            peakOpacity = AMBIENT_LEAF_PEAK_OPACITY + Math.random() * 0.08;
            spin = (Math.random() < 0.5 ? -1 : 1) * (20 + Math.random() * 25);
            double startRot = -15 + Math.random() * 30;

            leaf.setRotate(startRot);
            startNs = System.nanoTime();
            if (timer != null) timer.stop();
            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    double t = (now - startNs) / 1_000_000_000.0;
                    double p = t / durationS;
                    if (p >= 1.0) {
                        stop();
                        if (leaf.getParent() != null) runCycle();
                        return;
                    }
                    double x = startX + (endX - startX) * p;
                    // True sine drift around the base line.
                    double y = baseY + amplitude * Math.sin(2 * Math.PI * (x - startX) / wavelength);
                    leaf.setTranslateX(x);
                    leaf.setTranslateY(y);
                    leaf.setRotate(startRot + spin * p);
                    // Fade in first 8%, hold, fade out last 12%.
                    double o;
                    if (p < 0.08) o = peakOpacity * (p / 0.08);
                    else if (p > 0.88) o = peakOpacity * ((1.0 - p) / 0.12);
                    else o = peakOpacity;
                    leaf.setOpacity(Math.max(0, Math.min(peakOpacity, o)));
                }
            };
            timer.start();
        }

        void stop() {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            layer.getChildren().remove(leaf);
        }
    }

    // ---------- Button Press Feedback ----------

    /**
     * Adds a subtle scale-down/up press animation to a button.
     * Uses {@code addEventHandler} so existing press/release handlers are kept.
     */
    public static void addPressAnimation(Button button) {
        if (button == null) return;
        if (Boolean.TRUE.equals(button.getProperties().get("pressAnimInstalled"))) return;
        button.getProperties().put("pressAnimInstalled", Boolean.TRUE);
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(80), button);
            st.setToX(0.96);
            st.setToY(0.96);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(140), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
    }

    // ---------- Card Hover Lift ----------

    /**
     * Adds a smooth lift on hover. The node's pre-existing effect (if any) is
     * saved and restored — never clobbered. Do NOT call on whole-page roots.
     */
    public static void addCardHoverLift(Node card) {
        if (card == null) return;
        if (Boolean.TRUE.equals(card.getProperties().get("hoverLiftInstalled"))) return;
        card.getProperties().put("hoverLiftInstalled", Boolean.TRUE);
        final javafx.scene.effect.Effect[] original = new javafx.scene.effect.Effect[1];
        final boolean[] captured = new boolean[1];
        card.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            if (!captured[0]) {
                original[0] = card.getEffect();
                captured[0] = true;
            }
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), card);
            tt.setToY(-4);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
            card.setEffect(new javafx.scene.effect.DropShadow(24, Color.web("#000000", 0.12)));
        });
        card.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(300), card);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.play();
            card.setEffect(original[0]);
        });
    }

    // ---------- Progress Ring Pulse ----------

    /** Creates a pulsing progress indicator for loading states. */
    public static ProgressIndicator createPulseProgress(double size) {
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(size, size);
        pi.setStyle("-fx-progress-color: -fx-teal;");

        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pi.opacityProperty(), 1.0)),
                new KeyFrame(Duration.seconds(1), new KeyValue(pi.opacityProperty(), 0.5)),
                new KeyFrame(Duration.seconds(2), new KeyValue(pi.opacityProperty(), 1.0))
        );
        pulse.setCycleCount(Animation.INDEFINITE);
        pi.setOnMouseEntered(e -> pulse.pause());
        pi.setOnMouseExited(e -> pulse.play());
        pulse.play();
        return pi;
    }

    // ---------- Number Count-Up Animation ----------

    /** Animates a label counting from 0 to target value. */
    public static void animateCountUp(Label label, int target, Duration duration) {
        animateCountUp(label, 0, target, duration);
    }

    public static void animateCountUp(Label label, int from, int to, Duration duration) {
        long start = System.nanoTime();
        long durationNs = (long) (duration.toMillis() * 1_000_000);
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            long elapsed = System.nanoTime() - start;
            double progress = Math.min(1.0, (double) elapsed / durationNs);
            double eased = easeOutCubic(progress);
            int value = (int) Math.round(from + (to - from) * eased);
            label.setText(String.valueOf(value));
            if (progress >= 1.0) {
                label.setText(String.valueOf(to));
                ((Timeline) e.getSource()).stop();
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private static double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    // ---------- Shimmer Loading Placeholder ----------

    /** Adds a shimmer effect to a node while content loads. */
    public static void addShimmer(Node node) {
        javafx.scene.paint.LinearGradient shimmer = new javafx.scene.paint.LinearGradient(
                0, 0, 1, 0, true, CycleMethod.REFLECT,
                new Stop(0, Color.web("#E2E8F0")),
                new Stop(0.5, Color.web("#F1F5F9")),
                new Stop(1, Color.web("#E2E8F0"))
        );
        Rectangle clip = new Rectangle();
        if (node instanceof Region region) {
            clip.widthProperty().bind(region.widthProperty());
            clip.heightProperty().bind(region.heightProperty());
            clip.arcWidthProperty().bind(region.widthProperty().multiply(0.1));
            clip.arcHeightProperty().bind(region.heightProperty().multiply(0.1));
        } else {
            clip.setWidth(node.getBoundsInLocal().getWidth());
            clip.setHeight(node.getBoundsInLocal().getHeight());
        }
        clip.arcWidthProperty().bind(clip.widthProperty().multiply(0.1));
        clip.arcHeightProperty().bind(clip.heightProperty().multiply(0.1));

        double startX = -(node instanceof Region ? ((Region) node).getWidth() : node.getBoundsInLocal().getWidth());
        double endX = (node instanceof Region ? ((Region) node).getWidth() : node.getBoundsInLocal().getWidth()) * 2;

        Timeline shimmerAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(clip.translateXProperty(), startX)),
                new KeyFrame(Duration.seconds(1.5), new KeyValue(clip.translateXProperty(), endX))
        );
        shimmerAnim.setCycleCount(Animation.INDEFINITE);
        shimmerAnim.play();

        // Store original background to restore later
        Object originalBg = node.getProperties().get("originalBackground");
        if (originalBg == null) {
            node.getProperties().put("originalBackground", node.getStyle());
        }

        // This is a simplified version - in practice you'd use a custom skin or overlay
    }

    public static void removeShimmer(Node node) {
        Object originalBg = node.getProperties().get("originalBackground");
        if (originalBg != null) {
            node.setStyle((String) originalBg);
        }
    }
}