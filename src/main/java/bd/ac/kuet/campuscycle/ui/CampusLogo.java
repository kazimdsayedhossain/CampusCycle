package bd.ac.kuet.campuscycle.ui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The CampusCycle brand.
 *
 * <p>The logo is the supplied artwork ({@code brand-bike.png}), shown exactly as
 * authored — no tile, no plate, no background of any kind, so it sits on whatever
 * surface the app puts it against. A hand-drawn fallback mark is kept in code purely
 * as insurance: if the asset is ever missing from a packaged JAR the app still shows
 * a deliberate mark rather than an empty space. The fallback is an open ring that is
 * both a bicycle wheel and the letter C, with a leaf riding forward through the gap.
 *
 * <p>Repainting on theme change never leaks: the property holds a
 * {@link WeakChangeListener} and the listener holds a weak reference to its canvas
 * (the P-108 rule).
 */
public final class CampusLogo {

    private static final String ASSET_DIR = "/bd/ac/kuet/campuscycle/assets/";
    private static final String ARTWORK = ASSET_DIR + "brand-bike.png";
    /** The full lockup (logo + wordmark), used by the splash. */
    public static final String LOCKUP = ASSET_DIR + "brand-lockup.png";

    // ── Unit-square geometry of the fallback mark ──────────────────────────────
    private static final double RING_R = 0.40;   // centreline radius of the wheel
    private static final double RING_T = 0.135;  // ring thickness
    /** Total opening in the ring, centred on 3 o'clock, so it reads as a "C". */
    private static final double GAP_DEG = 66.0;
    private static final double ARC_START = GAP_DEG / 2.0;
    private static final double ARC_EXTENT = 360.0 - GAP_DEG;
    /** Leaf axis in degrees; negative leans up and to the right (forward motion). */
    private static final double LEAF_ANGLE_DEG = -42.0;
    private static final double LEAF_HALF_LEN = 0.250;
    private static final double LEAF_HALF_W = 0.150;
    private static final double LEAF_BOW = 1.18;
    /** Nudges the leaf toward the ring's opening, so it reads as riding out of it. */
    private static final double LEAF_OFFSET = 0.045;
    private static final double VEIN_W = 0.016;
    /**
     * Under this the leaf is dropped and the ring carries the mark by itself. Kept
     * low: the leaf is the distinctive half of the mark, and it stays readable well
     * below the sizes used in app chrome.
     */
    private static final double LEAF_MIN_SIZE = 20.0;
    private static final double VEIN_MIN_SIZE = 44.0;

    private CampusLogo() {}

    /**
     * Colours for the fallback mark, per theme. The vein is always a real split of
     * the leaf, never a translucent overlay — a wash reads as a scratch, not a midrib.
     */
    public record Palette(String ring, String leaf, String vein) {}

    public static Palette palette(boolean dark) {
        return dark
                ? new Palette("#34D399", "#2DD4BF", "#0F766E")
                : new Palette("#047857", "#0D9488", "#CCFBF1");
    }

    // ── Public builders ─────────────────────────────────────────────────────────

    /**
     * The logo, on transparency, sized square. The artwork is much wider than it is
     * tall, so it is fitted to the width and centred vertically — fitting to the
     * smaller side would shrink it to a sliver.
     */
    public static Canvas logo(double size) {
        return build(size, size, true);
    }

    /**
     * Window/taskbar icons, largest first. Transparent, exactly like the artwork, so
     * the desktop can composite it over whatever it likes. Read from the classpath so
     * a packaged JAR shows the logo too.
     */
    public static List<Image> windowIcons(boolean dark) {
        String tone = dark ? "dark" : "light";
        List<Image> icons = new ArrayList<>(3);
        for (String size : new String[]{"512", "256"}) {
            Image image = load(ASSET_DIR + "logo-window-" + tone + "-" + size + ".png");
            if (image == null || image.isError()) {
                image = load(ASSET_DIR + "logo-window-light-" + size + ".png");
            }
            if (image != null && !image.isError()) {
                icons.add(image);
            }
        }
        if (icons.isEmpty()) {
            // No generated tile: hand back the artwork itself.
            Image art = load(ARTWORK);
            if (art != null && !art.isError()) {
                icons.add(art);
            }
        }
        return icons;
    }

    /**
     * Loads an image from a classpath resource.
     *
     * <p>The bytes are buffered rather than handed straight to {@link Image}: JavaFX
     * reads an image stream lazily, so closing the stream right after construction —
     * which a try-with-resources does — leaves a half-loaded image and the window
     * shows a broken-image placeholder instead of the logo.
     */
    private static Image load(String resource) {
        try (InputStream in = CampusLogo.class.getResourceAsStream(resource)) {
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

    private static Canvas build(double w, double h, boolean useArtwork) {
        Canvas canvas = new Canvas(w, h);
        canvas.setAccessibleText("CampusCycle logo");
        RepaintListener listener = new RepaintListener(canvas, useArtwork);
        ThemeManager.themeProperty().addListener(listener.weak());
        listener.redraw();
        return canvas;
    }

    /**
     * Repaints on theme change without leaking. The property holds only a
     * {@link WeakChangeListener}, and the listener holds a weak reference to the
     * canvas, so a discarded view takes its subscription with it.
     */
    private static final class RepaintListener implements ChangeListener<ThemeManager.Theme> {
        private final WeakReference<Canvas> target;
        private final boolean useArtwork;
        private final WeakChangeListener<ThemeManager.Theme> weak = new WeakChangeListener<>(this);

        RepaintListener(Canvas canvas, boolean useArtwork) {
            this.target = new WeakReference<>(canvas);
            this.useArtwork = useArtwork;
        }

        WeakChangeListener<ThemeManager.Theme> weak() {
            return weak;
        }

        void redraw() {
            Canvas canvas = target.get();
            if (canvas != null) {
                render(canvas, useArtwork);
            }
        }

        @Override
        public void changed(ObservableValue<? extends ThemeManager.Theme> obs,
                            ThemeManager.Theme old, ThemeManager.Theme now) {
            Canvas canvas = target.get();
            if (canvas == null) {
                obs.removeListener(weak);
                return;
            }
            render(canvas, useArtwork);
        }
    }

    private static void render(Canvas canvas, boolean useArtwork) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        // Transparent by design: the artwork keeps its own alpha and sits on whatever
        // the app puts behind it.
        gc.clearRect(0, 0, w, h);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);

        if (useArtwork) {
            Image art = load(ARTWORK);
            if (art != null && !art.isError()) {
                drawArtwork(gc, w, h, art);
                return;
            }
        }
        drawMark(gc, w / 2.0, h / 2.0, Math.min(w, h) * 0.94, palette(ThemeManager.isDark()));
    }

    private static void drawArtwork(GraphicsContext gc, double w, double h, Image art) {
        double scale = (Math.min(w, h) * 0.96) / art.getWidth();
        double dw = art.getWidth() * scale;
        double dh = art.getHeight() * scale;
        gc.drawImage(art, (w - dw) / 2.0, (h - dh) / 2.0, dw, dh);
    }

    /**
     * The fallback mark, centred on {@code (cx, cy)} and drawn to fit a box of
     * {@code size}. The leaf degrades away on small sizes so the silhouette stays
     * readable rather than turning to mush.
     */
    private static void drawMark(GraphicsContext gc, double cx, double cy, double size, Palette p) {
        double s = size;

        // 1. The wheel: an open ring whose gap faces forward, doubling as the "C".
        gc.setStroke(Color.web(p.ring()));
        gc.setLineWidth(RING_T * s);
        gc.strokeArc(cx - RING_R * s, cy - RING_R * s, RING_R * 2 * s, RING_R * 2 * s,
                ARC_START, ARC_EXTENT, javafx.scene.shape.ArcType.OPEN);

        if (s < LEAF_MIN_SIZE) {
            return;
        }

        // 2. The leaf: a lens between two bowed quadratics, tilted forward and set
        //    slightly toward the gap.
        double rad = Math.toRadians(LEAF_ANGLE_DEG);
        double ux = Math.cos(rad);
        double uy = Math.sin(rad);
        double vx = -uy;   // perpendicular in screen space, y grows downward
        double vy = ux;

        double lx = cx + ux * LEAF_OFFSET * s;
        double ly = cy + uy * LEAF_OFFSET * s;

        double bx = lx - ux * LEAF_HALF_LEN * s;
        double by = ly - uy * LEAF_HALF_LEN * s;
        double tx = lx + ux * LEAF_HALF_LEN * s;
        double ty = ly + uy * LEAF_HALF_LEN * s;

        double bow = LEAF_HALF_W * LEAF_BOW * s;
        gc.setFill(Color.web(p.leaf()));
        gc.beginPath();
        gc.moveTo(bx, by);
        gc.quadraticCurveTo(lx + vx * bow, ly + vy * bow, tx, ty);
        gc.quadraticCurveTo(lx - vx * bow, ly - vy * bow, bx, by);
        gc.closePath();
        gc.fill();

        // 3. Vein: a faint crease, only where there are pixels to spend.
        if (s >= VEIN_MIN_SIZE) {
            gc.setStroke(Color.web(p.vein()));
            gc.setLineWidth(Math.max(1.0, VEIN_W * s));
            gc.beginPath();
            gc.moveTo(bx, by);
            gc.quadraticCurveTo(lx, ly, tx, ty);
            gc.stroke();
        }
    }
}
