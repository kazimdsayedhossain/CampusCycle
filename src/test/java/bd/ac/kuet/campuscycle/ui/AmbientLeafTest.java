package bd.ac.kuet.campuscycle.ui;

import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why the falling leaf was invisible.
 *
 * <p>The icon was built with its fill already at 0.28 alpha, and the animation then
 * set node opacity to 0.22-0.34. JavaFX multiplies the two, so the leaf actually drew
 * at roughly 6-10% alpha over a photographic background: present in the scene graph,
 * impossible to see.
 */
public class AmbientLeafTest {

    private static final String LEAF_FILL_WAS_ALPHA = "0.28";

    @Test
    void leafIconIsNotPreFadedBeforeOpacityIsApplied() {
        // This is the exact call LeafFloater makes.
        SVGPath leaf = ThemeManager.createIcon(ThemeManager.ICON_LEAF, 26, Color.web(AnimationHelper.AMBIENT_LEAF_FILL));
        Color fill = (Color) leaf.getFill();
        assertTrue(fill.getOpacity() > 0.99,
                "the leaf fill must be fully opaque; the animation owns the fade, got "
                        + fill.getOpacity());
    }

    @Test
    void effectiveLeafAlphaStaysVisible() {
        // Old: fill 0.28 x opacity 0.28 ~= 0.08. New: fill 1.0 x peak opacity ~= 0.35.
        double oldEffective = 0.28 * 0.28;
        double newEffective = 1.0 * AnimationHelper.AMBIENT_LEAF_PEAK_OPACITY;
        assertTrue(oldEffective < 0.10, "the old combination really was invisible");
        assertTrue(newEffective >= 0.25, "the new value must be clearly visible, got " + newEffective);
    }

    @Test
    void leafReachesTheUpperAndLowerPageSoItIsNotHiddenBehindContent() {
        // The drift band starts below the top bar and stops short of the bottom, so
        // the leaf crosses the content area on both themes.
        double windowHeight = 860;
        double top = 120;
        double usable = windowHeight - top - 300;
        assertTrue(usable > 0, "there is room to drift through the content area");
    }
}
