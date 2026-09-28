package bd.ac.kuet.campuscycle.ui;

import bd.ac.kuet.campuscycle.domain.CycleType;
import javafx.scene.image.Image;

/**
 * Resolves the FXML-package bike artwork to a cycle, with graceful
 * fallback to SVG glyphs when an asset is missing.
 */
public final class BikeArt {

    private BikeArt() {
    }

    /**
     * Returns a showroom cycle photo for the given type, used verbatim —
     * backgrounds are never stripped or altered. Falls back through legacy
     * art, then null (callers show an SVG glyph).
     */
    public static Image imageFor(CycleType type, String label) {
        String lower = label != null ? label.toLowerCase() : "";
        String[] candidates;
        if (lower.contains("blue") || lower.contains("commuter 01")) {
            candidates = new String[]{"cycle-blue-commuter.png", "bike-blue.png"};
        } else if (lower.contains("glide") || lower.contains("silver")) {
            candidates = new String[]{"cycle-silver-commuter.png", "cycle-electric-silver.png"};
        } else if (lower.contains("eco") || lower.contains("assisted") || type == CycleType.ELECTRIC_BIKE) {
            candidates = new String[]{"cycle-eco-ebike.png", "cycle-electric-black.png", "bike-ebike.png"};
        } else if (lower.contains("basket") || type == CycleType.CARGO_BIKE) {
            candidates = new String[]{"cycle-city-basket.png", "cycle-city-cream.png"};
        } else if (type == CycleType.ROAD_BIKE) {
            candidates = new String[]{"cycle-silver-commuter.png", "cycle-blue-commuter.png"};
        } else {
            candidates = new String[]{"cycle-blue-commuter.png", "cycle-silver-commuter.png", "cycle-city-cream.png", "cycle-city-basket.png"};
        }
        for (String file : candidates) {
            try {
                var url = BikeArt.class.getResource(
                        "/bd/ac/kuet/campuscycle/assets/" + file);
                if (url == null) continue;
                Image img = new Image(url.toExternalForm(), true);
                if (!img.isError()) return img;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Returns a scenic campus stage background photo. */
    public static Image backgroundImage(String file) {
        try {
            var url = BikeArt.class.getResource(
                    "/bd/ac/kuet/campuscycle/assets/backgrounds/" + file);
            if (url == null) return null;
            return new Image(url.toExternalForm(), false);
        } catch (Exception e) {
            return null;
        }
    }

    public static javafx.scene.image.ImageView bannerView(String file, double fitW, double fitH) {
        try {
            var url = BikeArt.class.getResource(
                    "/bd/ac/kuet/campuscycle/assets/" + file);
            if (url == null) return null;
            Image img = new Image(url.toExternalForm(), false);
            if (img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) return null;
            double iw = img.getWidth(), ih = img.getHeight();
            double scale = Math.max(fitW / iw, fitH / ih);
            double vw = Math.min(iw, fitW / scale), vh = Math.min(ih, fitH / scale);
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
            iv.setViewport(new javafx.geometry.Rectangle2D((iw - vw) / 2, (ih - vh) / 2, vw, vh));
            iv.setFitWidth(fitW);
            iv.setFitHeight(fitH);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            double radius = Math.min(14, Math.min(fitW, fitH) / 4);
            javafx.scene.shape.Rectangle clip =
                    new javafx.scene.shape.Rectangle(fitW, fitH);
            clip.setArcWidth(radius * 2);
            clip.setArcHeight(radius * 2);
            iv.setClip(clip);
            return iv;
        } catch (Exception e) {
            return null;
        }
    }
}
