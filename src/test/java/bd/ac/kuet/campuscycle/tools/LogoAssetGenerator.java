package bd.ac.kuet.campuscycle.tools;

import bd.ac.kuet.campuscycle.ui.CampusLogo;
import bd.ac.kuet.campuscycle.ui.ThemeManager;
import javafx.application.Platform;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Development tool: renders the window and taskbar icons from the same code the app
 * draws the logo with, so the bitmaps and the in-app mark can never drift apart.
 *
 * <p>Run it after changing {@link CampusLogo}, then commit the regenerated PNGs:
 *
 * <pre>
 * mvn -q test-compile
 * mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" \
 *      bd.ac.kuet.campuscycle.tools.LogoAssetGenerator
 * </pre>
 *
 * <p>Windows: replace {@code $(cat target/cp.txt)} with
 * {@code (Get-Content target\cp.txt -Raw)}.
 */
public final class LogoAssetGenerator {

    private static final Path ASSET_DIR =
            Path.of("src", "main", "resources", "bd", "ac", "kuet", "campuscycle", "assets");

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        final Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                Files.createDirectories(ASSET_DIR);
                for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
                    ThemeManager.setTheme(theme);
                    // Window/taskbar icon: the logo exactly as authored, on
                    // transparency, so the desktop can composite it freely.
                    write(CampusLogo.logo(512), ASSET_DIR.resolve(
                            "logo-window-" + theme.name().toLowerCase() + "-512.png"));
                    write(CampusLogo.logo(256), ASSET_DIR.resolve(
                            "logo-window-" + theme.name().toLowerCase() + "-256.png"));
                }
                ThemeManager.setTheme(ThemeManager.Theme.LIGHT);
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                done.countDown();
            }
        });

        if (!done.await(120, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX toolkit did not start.");
        }
        Platform.exit();
        if (failure[0] != null) {
            throw new IllegalStateException("Logo generation failed", failure[0]);
        }
        System.out.println("Logo assets written to " + ASSET_DIR.toAbsolutePath());
    }

    /**
     * Snapshots the canvas to a PNG. Pixels are copied straight into a BufferedImage
     * rather than via SwingFXUtils, which would drag in a module the app does not
     * otherwise need.
     */
    private static void write(javafx.scene.canvas.Canvas canvas, Path target) throws Exception {
        int w = (int) canvas.getWidth();
        int h = (int) canvas.getHeight();
        WritableImage image = new WritableImage(w, h);
        // A default snapshot fills with the scene colour, which would bake a white
        // plate behind a logo that is meant to stay transparent.
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(javafx.scene.paint.Color.TRANSPARENT);
        canvas.snapshot(params, image);

        PixelReader reader = image.getPixelReader();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        File file = target.toFile();
        ImageIO.write(out, "png", file);
        System.out.println("  " + file.getName() + " (" + file.length() / 1024 + " KB)");
    }

    private LogoAssetGenerator() {}
}
