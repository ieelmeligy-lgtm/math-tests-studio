package studio;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Draws the app's icons and saves them as 128 px PNG files (developer command: --make-icons <folder>).
 * The PNGs are bundled in src/main/resources/icons/; any of them can be replaced by another 128 px PNG with the same name.
 */
final class IconPainter {
    private static final int S = 128;
    private static final Color TURQ = new Color(0x17b3b0), TURQ_DEEP = new Color(0x0c7f7c), COFFEE = new Color(0x4e3426),
            GOLD = new Color(0xf0b93a), GOLD_DEEP = new Color(0xd1921a), PURPLE = new Color(0x8e5bd6), RED = new Color(0xe0533d),
            GREEN = new Color(0x2e9d5b), BLUE = new Color(0x3d8bd9), GREY = new Color(0xb9ab9a), WHITE = Color.WHITE;

    private IconPainter() {}

    static void makeAll(Path dir) throws IOException {
        Files.createDirectories(dir);
        String[] names = {"app", "settings", "folder", "import", "review", "publish", "refresh", "file", "clipboard",
                "globe", "trash", "eye", "ok", "fail", "busy", "idle", "warning", "pencil"};
        for (String n : names) {
            BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            paint(n, g);
            g.dispose();
            ImageIO.write(img, "png", dir.resolve(n + ".png").toFile());
        }
    }

    private static Stroke line(float w) { return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); }

    private static void disc(Graphics2D g, Color c) {
        g.setPaint(new GradientPaint(0, 0, brighter(c), 0, S, c));
        g.fill(new Ellipse2D.Float(6, 6, S - 12, S - 12));
    }

    private static Color brighter(Color c) {
        return new Color(Math.min(255, c.getRed() + 40), Math.min(255, c.getGreen() + 40), Math.min(255, c.getBlue() + 40));
    }

    private static void paint(String n, Graphics2D g) {
        switch (n) {
            case "app" -> {          // turquoise tile with + − × =
                g.setPaint(new GradientPaint(0, 0, new Color(0x1cc2be), S, S, TURQ_DEEP));
                g.fill(new RoundRectangle2D.Float(6, 6, 116, 116, 30, 30));
                g.setColor(COFFEE);
                g.fill(new RoundRectangle2D.Float(6, 104, 116, 18, 18, 18));
                g.fill(new Rectangle2D.Float(6, 104, 116, 6));
                g.setColor(WHITE);
                g.setStroke(line(9));
                g.draw(new Line2D.Float(26, 38, 50, 38)); g.draw(new Line2D.Float(38, 26, 38, 50));          // +
                g.draw(new Line2D.Float(78, 38, 102, 38));                                                  // −
                g.draw(new Line2D.Float(28, 66, 48, 86)); g.draw(new Line2D.Float(48, 66, 28, 86));          // ×
                g.draw(new Line2D.Float(78, 70, 102, 70)); g.draw(new Line2D.Float(78, 84, 102, 84));        // =
            }
            case "settings" -> {     // gear
                g.setColor(new Color(0x7a5a47));
                Area gear = new Area(new Ellipse2D.Float(26, 26, 76, 76));
                for (int i = 0; i < 8; i++) {
                    Shape tooth = new RoundRectangle2D.Float(54, 8, 20, 28, 6, 6);
                    gear.add(new Area(AffineTransform.getRotateInstance(Math.toRadians(i * 45), 64, 64).createTransformedShape(tooth)));
                }
                gear.subtract(new Area(new Ellipse2D.Float(46, 46, 36, 36)));
                g.fill(gear);
                g.setColor(GOLD);
                g.fill(new Ellipse2D.Float(52, 52, 24, 24));
            }
            case "folder" -> {       // gold folder
                g.setColor(GOLD_DEEP);
                g.fill(new RoundRectangle2D.Float(10, 24, 48, 24, 12, 12));
                g.fill(new RoundRectangle2D.Float(10, 34, 108, 76, 14, 14));
                g.setPaint(new GradientPaint(0, 44, new Color(0xffd36b), 0, 112, GOLD));
                g.fill(new RoundRectangle2D.Float(10, 46, 108, 66, 14, 14));
            }
            case "import" -> {       // arrow down into a tray
                g.setColor(TURQ);
                g.setStroke(line(14));
                g.draw(new Line2D.Float(64, 14, 64, 66));
                Path2D head = new Path2D.Float();
                head.moveTo(36, 50); head.lineTo(64, 80); head.lineTo(92, 50); head.closePath();
                g.fill(head); g.setStroke(line(8)); g.draw(head);
                g.setColor(COFFEE);
                g.setStroke(line(12));
                Path2D tray = new Path2D.Float();
                tray.moveTo(16, 74); tray.lineTo(16, 110); tray.lineTo(112, 110); tray.lineTo(112, 74);
                g.draw(tray);
            }
            case "review" -> {       // sparkles
                g.setColor(PURPLE);
                g.fill(star(58, 66, 46, 12));
                g.setColor(GOLD);
                g.fill(star(100, 26, 20, 6));
                g.fill(star(100, 102, 14, 4));
            }
            case "publish" -> {      // rocket
                AffineTransform old = g.getTransform();
                g.rotate(Math.toRadians(45), 64, 64);
                g.setColor(new Color(0xff9a3c));
                Path2D flame = new Path2D.Float();
                flame.moveTo(52, 96); flame.quadTo(64, 128, 76, 96); flame.closePath();
                g.fill(flame);
                g.setColor(RED);
                Path2D finL = new Path2D.Float(); finL.moveTo(46, 70); finL.lineTo(30, 96); finL.lineTo(50, 92); finL.closePath(); g.fill(finL);
                Path2D finR = new Path2D.Float(); finR.moveTo(82, 70); finR.lineTo(98, 96); finR.lineTo(78, 92); finR.closePath(); g.fill(finR);
                g.setPaint(new GradientPaint(44, 0, WHITE, 84, 0, new Color(0xd9e4ec)));
                Path2D body = new Path2D.Float();
                body.moveTo(64, 6); body.curveTo(92, 26, 86, 70, 80, 96); body.lineTo(48, 96); body.curveTo(42, 70, 36, 26, 64, 6); body.closePath();
                g.fill(body);
                g.setColor(new Color(0x9fb0bd)); g.setStroke(line(3)); g.draw(body);
                g.setColor(BLUE);
                g.fill(new Ellipse2D.Float(52, 38, 24, 24));
                g.setColor(WHITE); g.setStroke(line(4)); g.draw(new Ellipse2D.Float(52, 38, 24, 24));
                g.setTransform(old);
            }
            case "refresh" -> {      // circular arrow
                g.setColor(TURQ_DEEP);
                g.setStroke(new BasicStroke(13, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Float(22, 22, 84, 84, 70, 290, Arc2D.OPEN));
                Path2D head = new Path2D.Float();
                head.moveTo(64, 6); head.lineTo(92, 26); head.lineTo(62, 44); head.closePath();
                g.fill(head);
            }
            case "file" -> {         // page with folded corner
                Path2D page = new Path2D.Float();
                page.moveTo(26, 8); page.lineTo(80, 8); page.lineTo(104, 32); page.lineTo(104, 120); page.lineTo(26, 120); page.closePath();
                g.setColor(WHITE); g.fill(page);
                g.setColor(TURQ_DEEP); g.setStroke(line(6)); g.draw(page);
                Path2D fold = new Path2D.Float(); fold.moveTo(80, 8); fold.lineTo(80, 32); fold.lineTo(104, 32);
                g.draw(fold);
                g.setColor(TURQ); g.setStroke(line(7));
                for (int y : new int[]{56, 76, 96}) g.draw(new Line2D.Float(42, y, y == 96 ? 70 : 88, y));
            }
            case "clipboard" -> {
                g.setColor(new Color(0x9a6b4f)); g.fill(new RoundRectangle2D.Float(18, 16, 92, 108, 16, 16));
                g.setColor(WHITE); g.fill(new RoundRectangle2D.Float(28, 30, 72, 86, 8, 8));
                g.setColor(GREY); g.fill(new RoundRectangle2D.Float(42, 6, 44, 24, 10, 10));
                g.setColor(TURQ); g.setStroke(line(6));
                for (int y : new int[]{52, 70, 88}) g.draw(new Line2D.Float(40, y, y == 88 ? 70 : 88, y));
            }
            case "globe" -> {
                g.setPaint(new GradientPaint(0, 0, new Color(0x6db8f2), 0, S, BLUE));
                g.fill(new Ellipse2D.Float(8, 8, 112, 112));
                g.setColor(WHITE); g.setStroke(line(5));
                g.draw(new Ellipse2D.Float(8, 8, 112, 112));
                g.draw(new Ellipse2D.Float(38, 8, 52, 112));
                g.draw(new Line2D.Float(64, 8, 64, 120));
                g.draw(new Line2D.Float(8, 64, 120, 64));
                g.draw(new Arc2D.Float(18, 30, 92, 20, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Float(18, 78, 92, 20, 180, 180, Arc2D.OPEN));
            }
            case "trash" -> {
                g.setColor(RED);
                g.fill(new RoundRectangle2D.Float(48, 8, 32, 16, 8, 8));
                g.fill(new RoundRectangle2D.Float(16, 20, 96, 16, 8, 8));
                Path2D bin = new Path2D.Float(); bin.moveTo(26, 42); bin.lineTo(102, 42); bin.lineTo(94, 120); bin.lineTo(34, 120); bin.closePath();
                g.fill(bin);
                g.setColor(WHITE); g.setStroke(line(6));
                for (int x : new int[]{48, 64, 80}) g.draw(new Line2D.Float(x, 56, x, 106));
            }
            case "eye" -> {
                Path2D eye = new Path2D.Float();
                eye.moveTo(6, 64); eye.quadTo(64, 0, 122, 64); eye.quadTo(64, 128, 6, 64); eye.closePath();
                g.setColor(WHITE); g.fill(eye);
                g.setColor(TURQ_DEEP); g.setStroke(line(7)); g.draw(eye);
                g.setColor(BLUE); g.fill(new Ellipse2D.Float(40, 40, 48, 48));
                g.setColor(COFFEE); g.fill(new Ellipse2D.Float(54, 54, 20, 20));
                g.setColor(WHITE); g.fill(new Ellipse2D.Float(70, 48, 10, 10));
            }
            case "ok" -> {
                disc(g, GREEN);
                g.setColor(WHITE); g.setStroke(line(14));
                Path2D c = new Path2D.Float(); c.moveTo(36, 66); c.lineTo(56, 86); c.lineTo(94, 44);
                g.draw(c);
            }
            case "fail" -> {
                disc(g, RED);
                g.setColor(WHITE); g.setStroke(line(14));
                g.draw(new Line2D.Float(42, 42, 86, 86)); g.draw(new Line2D.Float(86, 42, 42, 86));
            }
            case "busy" -> {         // clock
                disc(g, GOLD_DEEP);
                g.setColor(WHITE); g.setStroke(line(11));
                g.draw(new Line2D.Float(64, 64, 64, 32)); g.draw(new Line2D.Float(64, 64, 86, 76));
            }
            case "idle" -> {
                g.setColor(GREY); g.setStroke(new BasicStroke(10));
                g.draw(new Ellipse2D.Float(14, 14, 100, 100));
            }
            case "warning" -> {
                Path2D t = new Path2D.Float(); t.moveTo(64, 10); t.lineTo(122, 112); t.lineTo(6, 112); t.closePath();
                g.setColor(GOLD); g.fill(t);
                g.setColor(GOLD_DEEP); g.setStroke(line(8)); g.draw(t);
                g.setColor(COFFEE); g.setStroke(line(12));
                g.draw(new Line2D.Float(64, 44, 64, 76));
                g.fill(new Ellipse2D.Float(57, 88, 14, 14));
            }
            case "pencil" -> {
                AffineTransform old = g.getTransform();
                g.rotate(Math.toRadians(45), 64, 64);
                g.setColor(GOLD); g.fill(new Rectangle2D.Float(50, 20, 28, 68));
                g.setColor(new Color(0xff8fa3)); g.fill(new RoundRectangle2D.Float(50, 4, 28, 20, 8, 8));
                g.setColor(new Color(0xf3d9b1));
                Path2D tip = new Path2D.Float(); tip.moveTo(50, 88); tip.lineTo(78, 88); tip.lineTo(64, 120); tip.closePath(); g.fill(tip);
                g.setColor(COFFEE); g.fill(new Ellipse2D.Float(59, 108, 10, 12));
                g.setTransform(old);
            }
            default -> throw new IllegalArgumentException(n);
        }
    }

    /** Four-pointed sparkle centred on (cx, cy). */
    private static Shape star(float cx, float cy, float r, float in) {
        Path2D p = new Path2D.Float();
        p.moveTo(cx, cy - r);
        p.quadTo(cx + in * 0.3f, cy - in * 0.3f, cx + r, cy);
        p.quadTo(cx + in * 0.3f, cy + in * 0.3f, cx, cy + r);
        p.quadTo(cx - in * 0.3f, cy + in * 0.3f, cx - r, cy);
        p.quadTo(cx - in * 0.3f, cy - in * 0.3f, cx, cy - r);
        p.closePath();
        return p;
    }
}
