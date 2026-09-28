package bd.ac.kuet.campuscycle.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Startup splash, in the spirit of the Photoshop one: the brand lockup holds centre
 * stage while the app boots behind it, then it gets out of the way.
 *
 * <p>Shown on its own stage before the main window so the first thing a user sees is
 * the logo rather than a half-built shell. It closes on {@link #finish()}, or on its
 * own once {@link #MAX_VISIBLE_MILLIS} elapses, so a slow or failing boot can never
 * leave someone staring at a splash forever.
 */
public class SplashScreen {

    /** Hard ceiling on splash time; it fades out even if boot never reports back. */
    private static final long MAX_VISIBLE_MILLIS = 6000L;
    /**
     * Floor on splash time. Boot finishes in milliseconds, and a splash that appears
     * and vanishes instantly reads as a glitch rather than as branding — so a fast
     * boot waits this out.
     */
    private static final long MIN_VISIBLE_MILLIS = 1200L;

    private final StackPane root = new StackPane();
    private final Label status = new Label("Starting CampusCycle…");
    private final ProgressBar bar = new ProgressBar(0);
    private final Region brand = new Region();

    private javafx.stage.Stage stage;
    private Animation spinner;
    private boolean finished;
    private boolean closing;
    private long shownAt;

    public SplashScreen() {
        build();
    }

    private void build() {
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #F6FBF8, #E4F3EA);");

        StackPane art = new StackPane();
        art.getChildren().addAll(watermark(), brandImage());

        VBox column = new VBox(0);
        column.setAlignment(Pos.CENTER);
        column.getChildren().add(art);

        status.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #4B6B60;");
        bar.setPrefWidth(240);
        bar.setMaxWidth(240);
        bar.setPrefHeight(4);
        // Indeterminate: honest about not knowing how long boot takes.
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setStyle("-fx-accent: #10B981;");

        VBox footer = new VBox(8);
        footer.setAlignment(Pos.CENTER);
        footer.getChildren().addAll(status, bar);

        VBox layout = new VBox(18);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(24));
        layout.getChildren().addAll(column, footer);

        root.getChildren().add(layout);
        StackPane.setAlignment(layout, Pos.CENTER);

        // Gentle entrance so the splash feels like part of the app, not a dialog.
        FadeTransition fade = new FadeTransition(Duration.millis(420), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition grow = new ScaleTransition(Duration.millis(520), art);
        grow.setFromX(0.92);
        grow.setToX(1.0);
        grow.setFromY(0.92);
        grow.setToY(1.0);
        new ParallelTransition(fade, grow).play();
    }

    /** The full lockup (logo + wordmark) as authored, on transparency. */
    private Node brandImage() {
        Image image = load(CampusLogo.LOCKUP);
        if (image == null || image.isError()) {
            return CampusLogo.logo(150);
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        // The source art is wide; bound the width and let the height follow.
        double maxWidth = 360;
        double scale = Math.min(1.0, maxWidth / image.getWidth());
        view.setFitWidth(image.getWidth() * scale);
        view.setFitHeight(image.getHeight() * scale);
        return view;
    }

    /**
     * A soft halo behind the art, built from concentric ovals. Deliberately not a
     * paint gradient: a CSS radial-gradient renders here as a hard-edged box, and the
     * gradient constructors are easy to get wrong, whereas overlapping translucent
     * ovals are smooth and cannot fail.
     */
    private Node watermark() {
        double w = 420;
        double h = 280;
        Canvas halo = new Canvas(w, h);
        GraphicsContext gc = halo.getGraphicsContext2D();
        int rings = 22;
        for (int i = rings; i >= 1; i--) {
            double t = i / (double) rings;          // 1 = outermost
            double rw = w * t;
            double rh = h * t;
            gc.setFill(Color.web("#10B981", 0.020));
            gc.fillOval((w - rw) / 2.0, (h - rh) / 2.0, rw, rh);
        }
        halo.setMouseTransparent(true);
        return halo;
    }
    /**
     * Loads an image fully into memory before handing it to JavaFX. The artwork is read
     * lazily from a stream, so passing a stream that is about to be closed leaves a
     * half-loaded image and the splash shows a broken-image placeholder.
     */
    private static Image load(String resource) {
        try (java.io.InputStream in = SplashScreen.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return new Image(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    /** The splash node, for callers that want to host it instead of its own stage. */
    public StackPane root() {
        return root;
    }

    public void setStatus(String text) {
        status.setText(text);
    }

    /**
     * Shows the splash on its own undecorated stage. The user can dismiss it early
     * with Escape or a click, the way a splash is meant to behave.
     */
    public void show(javafx.stage.Stage owner) {
        this.stage = new javafx.stage.Stage();
        stage.initOwner(owner);
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        stage.setScene(new javafx.scene.Scene(root, 620, 420));
        stage.setResizable(false);
        stage.setTitle("CampusCycle");

        root.setOnMouseClicked(e -> finish());
        root.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                finish();
            }
        });
        root.setFocusTraversable(true);
        root.requestFocus();

        stage.show();
        root.requestFocus();
        startSpinner();
        shownAt = System.currentTimeMillis();

        // Never hold the UI hostage to a boot that never completes.
        Timeline failsafe = new Timeline(new KeyFrame(
                Duration.millis(MAX_VISIBLE_MILLIS), e -> finish()));
        failsafe.play();
    }

    /** A slow travelling sheen, so "working" never looks like a frozen bar. */
    private void startSpinner() {
        double travel = 260;
        spinner = new Transition(1400) {
            @Override
            protected void interpolate(double fraction) {
                bar.setTranslateX(-travel / 2 + travel * fraction);
            }
        };
        spinner.setCycleCount(Animation.INDEFINITE);
        spinner.play();
    }

    /**
     * Fades the splash out and closes it. Safe to call more than once. A call that
     * arrives before {@link #MIN_VISIBLE_MILLIS} is deferred, so the branding is
     * actually seen.
     */
    public void finish() {
        if (finished || closing) {
            return;
        }
        long elapsed = System.currentTimeMillis() - shownAt;
        if (shownAt > 0 && elapsed < MIN_VISIBLE_MILLIS) {
            closing = true;
            PauseTransition defer = new PauseTransition(
                    Duration.millis(MIN_VISIBLE_MILLIS - elapsed));
            defer.setOnFinished(e -> {
                closing = false;
                finish();
            });
            defer.play();
            return;
        }
        finished = true;
        if (spinner != null) {
            spinner.stop();
        }
        FadeTransition out = new FadeTransition(Duration.millis(320), root);
        out.setFromValue(1);
        out.setToValue(0);
        out.setOnFinished(e -> {
            if (stage != null) {
                stage.close();
            }
        });
        if (stage != null) {
            out.play();
        } else {
            root.setOpacity(0);
        }
    }
}
