package bd.ac.kuet.campuscycle.tools;

import bd.ac.kuet.campuscycle.ui.AnimationHelper;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Development tool: captures the falling leaf against a representative page so the
 * ambient effect can be checked without logging in. Not part of the test suite.
 *
 * <pre>
 * mvn -q test-compile
 * mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" \
 *      bd.ac.kuet.campuscycle.tools.AmbientLeafSnapshot
 * </pre>
 */
public final class AmbientLeafSnapshot {

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        final Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                StackPane root = new StackPane();

                // Stand-in for the dashboard cards: translucent, like the real page.
                Pane cards = new Pane();
                cards.setStyle("-fx-background-color: rgba(255,255,255,0.78); "
                        + "-fx-background-radius: 18px; -fx-border-color: #DDE8E1; -fx-border-radius: 18px;");
                cards.setPrefSize(900, 520);
                StackPane.setAlignment(cards, javafx.geometry.Pos.CENTER);
                root.getChildren().add(cards);

                Pane ambientLayer = new Pane();
                ambientLayer.setMouseTransparent(true);
                ambientLayer.setPickOnBounds(false);
                root.getChildren().add(ambientLayer);

                Scene scene = new Scene(root, 1180, 700);
                stage.setScene(scene);
                stage.show();

                AnimationHelper.startAmbientLeaf(ambientLayer);

                // Sample twice: the leaf fades in and out over its drift, so one frame
                // can catch it near zero opacity.
                captureAfter(scene, 3.0, new File("target/leaf-3s.png"), failure, done);
                javafx.animation.PauseTransition second = new javafx.animation.PauseTransition(Duration.seconds(3.0));
                second.setOnFinished(e -> captureAfter(scene, 0.2, new File("target/leaf-6s.png"), failure, done));
                second.play();
            } catch (Throwable t) {
                failure[0] = t;
                done.countDown();
            }
        });

        if (!done.await(120, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Toolkit did not start");
        }
        Platform.exit();
        if (failure[0] != null) {
            throw new IllegalStateException("snapshot failed", failure[0]);
        }
    }

    private static void captureAfter(Scene scene, double seconds, File out,
                                     Throwable[] failure, CountDownLatch done) {
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(Duration.seconds(seconds));
        wait.setOnFinished(e -> {
            try {
                writePng(scene.snapshot(null), out);
                System.out.println("wrote " + out.getAbsolutePath());
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                done.countDown();
            }
        });
        wait.play();
    }

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

    private AmbientLeafSnapshot() {}
}
