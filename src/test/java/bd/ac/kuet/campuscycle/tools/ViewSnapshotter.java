package bd.ac.kuet.campuscycle.tools;

import bd.ac.kuet.campuscycle.ui.CampusVectorArt;
import bd.ac.kuet.campuscycle.ui.LoginView;
import bd.ac.kuet.campuscycle.ui.SplashScreen;
import bd.ac.kuet.campuscycle.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Development tool: screenshots a real view without clicking through the app, so
 * layout and branding changes can be reviewed directly. Not part of the test suite.
 *
 * <pre>
 * mvn -q test-compile
 * mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" \
 *      bd.ac.kuet.campuscycle.tools.ViewSnapshotter login-light
 * </pre>
 *
 * <p>Targets: {@code login-light}, {@code login-dark}, {@code brand-light},
 * {@code brand-dark}, {@code splash-light}. Output lands in {@code target/}.
 * Windows: replace {@code $(cat target/cp.txt)} with
 * {@code (Get-Content target\cp.txt -Raw)}.
 */
public final class ViewSnapshotter {

    public static void main(String[] args) throws Exception {
        String which = args.length > 0 ? args[0] : "login-light";
        boolean dark = which.endsWith("dark");
        if (dark) {
            ThemeManager.setTheme(ThemeManager.Theme.DARK);
        }

        CountDownLatch done = new CountDownLatch(1);
        final Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                StackPane root = new StackPane();
                Scene scene = new Scene(root, widthFor(which), heightFor(which));
                var css = ThemeManager.resolve(ThemeManager.getTheme().cssFile());
                if (css != null) {
                    scene.getStylesheets().add(css.toExternalForm());
                }
                if (which.startsWith("brand")) {
                    // The sidebar lockup at its real width, on a sidebar-like panel.
                    VBox panel = new VBox(CampusVectorArt.createBrandHeader(176, 56));
                    panel.setPadding(new Insets(10, 16, 10, 16));
                    panel.setStyle(ThemeManager.isDark()
                            ? "-fx-background-color: #112920;"
                            : "-fx-background-color: #F2F7F4;");
                    root.getChildren().add(panel);
                    StackPane.setAlignment(panel, Pos.TOP_CENTER);
                } else if (which.startsWith("splash")) {
                    root.getChildren().add(new SplashScreen().root());
                } else {
                    root.getChildren().add(new LoginView(user -> {}));
                }
                stage.setScene(scene);
                stage.show();

                // Do NOT sleep here: this runs on the FX thread, so blocking it would
                // freeze layout and any fade-in. Defer the capture instead.
                PauseTransition settle = new PauseTransition(Duration.seconds(2.5));
                settle.setOnFinished(e -> {
                    try {
                        WritableImage image = scene.snapshot(null);
                        File out = new File("target/branding-" + which + ".png");
                        writePng(image, out);
                        System.out.println("wrote " + out.getAbsolutePath());
                    } catch (Throwable t) {
                        failure[0] = t;
                    } finally {
                        done.countDown();
                    }
                });
                settle.play();
            } catch (Throwable t) {
                failure[0] = t;
                done.countDown();
            }
        });

        if (!done.await(180, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Toolkit did not start");
        }
        Platform.exit();
        if (failure[0] != null) {
            throw new IllegalStateException("snapshot failed", failure[0]);
        }
    }

    /** Snapshots to PNG without pulling in the JavaFX-Swing bridge module. */
    private static void writePng(WritableImage image, File out) throws Exception {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(img, "png", out);
    }

    /** Sidebar is narrow, the splash is a fixed 620x420 window, login fills the app. */
    private static double widthFor(String target) {
        if (target.startsWith("brand")) return 208;
        if (target.startsWith("splash")) return 620;
        return 1180;
    }

    private static double heightFor(String target) {
        if (target.startsWith("brand")) return 120;
        if (target.startsWith("splash")) return 420;
        return 760;
    }

    private ViewSnapshotter() {}
}
