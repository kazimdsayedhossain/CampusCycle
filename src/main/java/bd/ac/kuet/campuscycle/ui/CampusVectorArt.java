package bd.ac.kuet.campuscycle.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

/**
 * Pure JavaFX vector artwork and procedural graphics generator.
 * Creates brand emblems, empty-state ride illustrations, architectural
 * campus landmarks, and UI badges with zero raster image cropping.
 */
public final class CampusVectorArt {

    private CampusVectorArt() {}

    /**
     * Renders the CampusCycle brand lockup: the logo badge beside the wordmark,
     * campus tag line and strap line. Used by the sidebar and any other chrome that
     * needs to state who the app is.
     */
    public static VBox createBrandHeader(double width, double height) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 10, 0));

        double badge = Math.min(height, width * 0.26);
        row.getChildren().add(CampusLogo.logo(badge));

        VBox text = new VBox(1);
        text.setAlignment(Pos.CENTER_LEFT);
        text.setPadding(new Insets(0, 0, 0, 0));

        // Colours come from .sidebar-brand-* in the theme stylesheets, not from
        // inline styles: hardcoding them here made the wordmark unreadable in dark
        // mode, and a per-instance theme listener is the P-108 leak.
        Label title = new Label("CampusCycle");
        title.getStyleClass().add("sidebar-brand-title");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800;");

        Label sub = new Label("KUET");
        sub.getStyleClass().add("sidebar-brand-sub");
        sub.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-letter-spacing: 0.08em;");

        Label tag = new Label("CLEANER CAMPUS RIDES");
        tag.getStyleClass().add("sidebar-brand-tag");
        tag.setWrapText(true);
        tag.setMaxWidth(width - badge - 18);
        tag.setStyle("-fx-font-size: 7.5px; -fx-font-weight: 700; -fx-letter-spacing: 0.05em;");

        text.getChildren().addAll(title, sub, tag);
        row.getChildren().add(text);

        VBox box = new VBox(row);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Renders the complete vector illustration for the "No Active Ride" empty state:
     * Campus skyline, tree canopies, signpost with directional arrows, and bicycle.
     */
    public static Canvas createNoActiveRideIllustration(double width, double height) {
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        double baseY = height * 0.72;

        // 1. Distant campus skyline in soft mint tints
        gc.setFill(Color.web("#D1EBE2", 0.5));
        // Distant high-rise towers
        gc.fillRoundRect(width * 0.20, baseY - 65, 34, 70, 6, 6);
        gc.fillRoundRect(width * 0.28, baseY - 85, 32, 90, 8, 8);
        gc.fillRoundRect(width * 0.58, baseY - 95, 36, 100, 8, 8);
        gc.fillRoundRect(width * 0.68, baseY - 70, 30, 75, 6, 6);

        // 2. Soft rolling foliage
        gc.setFill(Color.web("#BBE3D5", 0.7));
        gc.fillOval(width * 0.12, baseY - 48, 48, 48);
        gc.fillOval(width * 0.22, baseY - 54, 52, 52);
        gc.fillOval(width * 0.65, baseY - 50, 56, 56);
        gc.fillOval(width * 0.76, baseY - 42, 44, 44);

        // 3. Foreground richer trees
        gc.setFill(Color.web("#4EAF93", 0.85));
        gc.fillOval(width * 0.16, baseY - 38, 36, 36);
        gc.fillOval(width * 0.70, baseY - 36, 40, 40);

        // Tree trunks
        gc.setStroke(Color.web("#1E6955"));
        gc.setLineWidth(2.2);
        gc.strokeLine(width * 0.18, baseY - 18, width * 0.18, baseY + 6);
        gc.strokeLine(width * 0.72, baseY - 16, width * 0.72, baseY + 6);

        // 4. Ground baseline
        gc.setStroke(Color.web("#58B69C"));
        gc.setLineWidth(2.0);
        gc.beginPath();
        gc.moveTo(width * 0.08, baseY + 6);
        gc.quadraticCurveTo(width * 0.5, baseY - 3, width * 0.92, baseY + 6);
        gc.stroke();

        // 5. Signpost with directional arrows (on right)
        double spX = width * 0.68;
        gc.setStroke(Color.web("#184D3F"));
        gc.setLineWidth(3.0);
        gc.strokeLine(spX, baseY - 80, spX, baseY + 6);

        // Top arrow (pointing right)
        double arrW = 46, arrH = 14;
        gc.setFill(Color.web("#2D947B"));
        gc.fillRoundRect(spX - 22, baseY - 76, arrW, arrH, 4, 4);

        // Bottom arrow (pointing left)
        gc.setFill(Color.web("#38A88E"));
        gc.fillRoundRect(spX - 24, baseY - 58, arrW, arrH, 4, 4);

        // 6. Vector Bicycle in Foreground
        drawBicycle(gc, width * 0.38, baseY - 12, 110, 72);

        return canvas;
    }

    private static void drawBicycle(GraphicsContext gc, double cx, double cy, double w, double h) {
        double r = h * 0.34; // wheel radius
        double leftAxleX = cx - w * 0.36;
        double rightAxleX = cx + w * 0.36;
        double axleY = cy + h * 0.22;

        // Wheels: Tires & Rims
        gc.setStroke(Color.web("#0A3D36"));
        gc.setLineWidth(3.2);
        gc.strokeOval(leftAxleX - r, axleY - r, r * 2, r * 2);
        gc.strokeOval(rightAxleX - r, axleY - r, r * 2, r * 2);

        // Inner spokes
        gc.setStroke(Color.web("#34A286"));
        gc.setLineWidth(1.0);
        gc.strokeOval(leftAxleX - r * 0.85, axleY - r * 0.85, r * 1.7, r * 1.7);
        gc.strokeOval(rightAxleX - r * 0.85, axleY - r * 0.85, r * 1.7, r * 1.7);
        for (int a = 0; a < 360; a += 45) {
            double rad = Math.toRadians(a);
            gc.strokeLine(leftAxleX, axleY, leftAxleX + Math.cos(rad) * r, axleY + Math.sin(rad) * r);
            gc.strokeLine(rightAxleX, axleY, rightAxleX + Math.cos(rad) * r, axleY + Math.sin(rad) * r);
        }

        // Frame Nodes
        double bottomBracketX = cx - w * 0.04;
        double bottomBracketY = axleY + 2;
        double seatPostNodeX = cx - w * 0.12;
        double seatPostNodeY = cy - h * 0.20;
        double headTubeX = cx + w * 0.24;
        double headTubeY = cy - h * 0.32;

        // Bike Frame Tubing
        gc.setStroke(Color.web("#148A6C"));
        gc.setLineWidth(3.4);

        // Chainstay (rear axle to bottom bracket)
        gc.strokeLine(leftAxleX, axleY, bottomBracketX, bottomBracketY);
        // Seatstay (rear axle to seat post node)
        gc.strokeLine(leftAxleX, axleY, seatPostNodeX, seatPostNodeY);
        // Seat tube (bottom bracket to seat post node)
        gc.strokeLine(bottomBracketX, bottomBracketY, seatPostNodeX, seatPostNodeY);
        // Down tube (bottom bracket to head tube)
        gc.strokeLine(bottomBracketX, bottomBracketY, headTubeX, headTubeY);
        // Top tube (seat post node to head tube)
        gc.strokeLine(seatPostNodeX, seatPostNodeY, headTubeX, headTubeY);
        // Front Fork (head tube to front axle)
        gc.strokeLine(headTubeX, headTubeY, rightAxleX, axleY);

        // Saddle / Seat
        gc.setStroke(Color.web("#062B23"));
        gc.setLineWidth(2.4);
        gc.strokeLine(seatPostNodeX, seatPostNodeY, seatPostNodeX, seatPostNodeY - 8);
        gc.setFill(Color.web("#062B23"));
        gc.fillRoundRect(seatPostNodeX - 12, seatPostNodeY - 14, 22, 6, 4, 4);

        // Handlebars & Stem
        gc.strokeLine(headTubeX, headTubeY, headTubeX + 2, headTubeY - 14);
        gc.strokeLine(headTubeX - 4, headTubeY - 14, headTubeX + 8, headTubeY - 14);
        gc.strokeLine(headTubeX + 8, headTubeY - 14, headTubeX + 11, headTubeY - 8);

        // Chain ring & Pedals
        gc.setFill(Color.web("#083E32"));
        gc.fillOval(bottomBracketX - 6, bottomBracketY - 6, 12, 12);
        gc.setStroke(Color.web("#062B23"));
        gc.setLineWidth(2.0);
        gc.strokeLine(bottomBracketX - 4, bottomBracketY - 7, bottomBracketX + 4, bottomBracketY + 7);

        // Rear Cargo Rack
        gc.setStroke(Color.web("#2D947B"));
        gc.setLineWidth(1.8);
        gc.strokeLine(seatPostNodeX - 3, seatPostNodeY - 2, leftAxleX - 8, seatPostNodeY - 2);
        gc.strokeLine(leftAxleX - 8, seatPostNodeY - 2, leftAxleX, axleY);
    }

    /**
     * Creates the KUET Shahid Minar monument architecture card for the sidebar promo.
     */
    public static Canvas createMonumentCard(double width, double height) {
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Canvas is intentionally transparent — blends with sidebar card's own background.
        // All coordinates below are expressed as fractions of width/height so the art
        // looks correct at any canvas size (e.g. 160×80 in sidebar, 310×110 in login page).

        double cx   = width * 0.50;
        double baseY = height * 0.85;

        // Scale factor relative to reference size 186×92
        double sx = width / 186.0;
        double sy = height / 92.0;

        // ── Flanking trees ──────────────────────────────────────────────────────────
        double treeR  = 21 * sx;          // outer circle radius (was 42px wide → r=21)
        double treeR2 = 18 * sx;          // inner/darker circle radius (was 36px → r=18)
        double treeY  = baseY - 38 * sy;  // vertical center of trees
        double treeOffX = 44 * sx;        // horizontal distance from cx to tree center

        gc.setFill(Color.web("#3DA487", 0.75));
        gc.fillOval(cx - treeOffX - treeR,  treeY - treeR,  treeR * 2, treeR * 2);
        gc.fillOval(cx + treeOffX - treeR,  treeY - treeR,  treeR * 2, treeR * 2);
        gc.setFill(Color.web("#2D856C", 0.85));
        gc.fillOval(cx - treeOffX - treeR2, treeY - treeR2, treeR2 * 2, treeR2 * 2);
        gc.fillOval(cx + treeOffX - treeR2, treeY - treeR2, treeR2 * 2, treeR2 * 2);

        // ── Tree trunks ──────────────────────────────────────────────────────────────
        double trunkX = treeOffX;
        gc.setStroke(Color.web("#1A5745"));
        gc.setLineWidth(1.8 * sx);
        gc.strokeLine(cx - trunkX, baseY - 12 * sy, cx - trunkX, baseY);
        gc.strokeLine(cx + trunkX, baseY - 12 * sy, cx + trunkX, baseY);

        // ── Base steps ───────────────────────────────────────────────────────────────
        gc.setStroke(Color.web("#6FA898"));
        gc.setLineWidth(1.4 * sx);
        gc.strokeLine(cx - 44 * sx, baseY - 2 * sy, cx + 44 * sx, baseY - 2 * sy);
        gc.strokeLine(cx - 34 * sx, baseY - 5 * sy, cx + 34 * sx, baseY - 5 * sy);
        gc.strokeLine(cx - 24 * sx, baseY - 8 * sy, cx + 24 * sx, baseY - 8 * sy);

        // ── Shahid Minar pillars (scaled) ────────────────────────────────────────────
        double pillarH = 70 * sy;   // total pillar height above base
        double pillarTop = baseY - pillarH;

        gc.setFill(Color.web("#E2EFE9"));
        gc.setStroke(Color.web("#1E6955"));
        gc.setLineWidth(1.5 * sx);

        // Left pillar (curved inward)
        gc.beginPath();
        gc.moveTo(cx - 18 * sx, baseY - 8 * sy);
        gc.quadraticCurveTo(cx - 12 * sx, baseY - 40 * sy, cx - 5 * sx, pillarTop);
        gc.lineTo(cx - 2 * sx, pillarTop);
        gc.quadraticCurveTo(cx - 7 * sx, baseY - 40 * sy, cx - 9 * sx, baseY - 8 * sy);
        gc.closePath();
        gc.fill();
        gc.stroke();

        // Right pillar (curved inward, mirrored)
        gc.beginPath();
        gc.moveTo(cx + 18 * sx, baseY - 8 * sy);
        gc.quadraticCurveTo(cx + 12 * sx, baseY - 40 * sy, cx + 5 * sx, pillarTop);
        gc.lineTo(cx + 2 * sx, pillarTop);
        gc.quadraticCurveTo(cx + 7 * sx, baseY - 40 * sy, cx + 9 * sx, baseY - 8 * sy);
        gc.closePath();
        gc.fill();
        gc.stroke();

        // Center pillar (straight, narrower)
        gc.beginPath();
        gc.moveTo(cx - 4 * sx, baseY - 8 * sy);
        gc.lineTo(cx - 2.5 * sx, pillarTop - 6 * sy);
        gc.lineTo(cx + 2.5 * sx, pillarTop - 6 * sy);
        gc.lineTo(cx + 4 * sx, baseY - 8 * sy);
        gc.closePath();
        gc.fill();
        gc.stroke();

        // Center decorative sphere at mid-height
        double sphereY = baseY - 46 * sy;
        gc.setFill(Color.web("#10B981"));
        gc.fillOval(cx - 4 * sx, sphereY - 4 * sy, 8 * sx, 8 * sy);

        return canvas;
    }

    /**
     * Vector Leaf Icon matching the green sidebar promotion glyph.
     */
    public static SVGPath createPromoLeaf(double size, Color color) {
        SVGPath leaf = new SVGPath();
        leaf.setContent(
                "M12 2C6.48 2 2 6.48 2 12c0 3.31 1.61 6.24 4.09 8.04L6 22l1.96-.09C10.04 22.95 11 23 12 23c6.08 0 11-4.92 11-11 0-5.52-4.48-10-11-10zm0 18.5c-4.69 0-8.5-3.81-8.5-8.5 0-2.35.95-4.47 2.49-6.01l.01.01c.21 2.39 1.13 4.56 2.57 6.27 1.83 2.18 4.38 3.65 7.27 4.07-.94 2.47-3.08 4.16-5.84 4.16z"
        );
        leaf.setFill(color);
        double scale = size / 24.0;
        leaf.setScaleX(scale);
        leaf.setScaleY(scale);
        return leaf;
    }

    /**
     * Creates a stylized user profile chip avatar with status ring.
     */
    public static StackPane createUserAvatar(String name, double size) {
        StackPane root = new StackPane();
        root.setPrefSize(size, size);
        root.setMinSize(size, size);
        root.setMaxSize(size, size);

        // Circular background
        Circle circle = new Circle(size / 2.0);
        circle.setFill(Color.web("#0A3D36"));

        // User initials or profile glyph
        String initials = "?";
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+");
            if (parts.length >= 2) {
                initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
            } else if (parts.length == 1 && !parts[0].isEmpty()) {
                initials = ("" + parts[0].charAt(0)).toUpperCase();
            }
        }
        Label text = new Label(initials);
        text.setStyle(String.format("-fx-font-size: %.1fpx; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;", size * 0.38));

        root.getChildren().addAll(circle, text);
        return root;
    }
}
