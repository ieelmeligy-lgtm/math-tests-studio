package studio;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The app's PNG icons (src/main/resources/icons/*.png, 128 px), drawn smoothly at any size and sharp on high-DPI screens. */
public final class Icons {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private Icons() {}

    static synchronized BufferedImage image(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = Icons.class.getResourceAsStream("/icons/" + n + ".png")) {
                return in == null ? null : ImageIO.read(in);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** An icon of the given size in logical pixels; empty if the PNG is missing, so the UI never breaks. */
    public static Icon get(String name, int size) {
        BufferedImage img = image(name);
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                if (img == null) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(img, x, y, size, size, null);
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /** The icon on a white round badge, so it stays visible on the coloured step buttons. */
    public static Icon badge(String name, int size) {
        Icon inner = get(name, size - 12);
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 235));
                g2.fillOval(x, y, size, size);
                g2.dispose();
                inner.paintIcon(c, g, x + 6, y + 6);
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /** Window and taskbar icon. */
    public static List<Image> windowImages() {
        BufferedImage img = image("app");
        if (img == null) return List.of();
        return List.of(img.getScaledInstance(16, 16, Image.SCALE_SMOOTH), img.getScaledInstance(32, 32, Image.SCALE_SMOOTH),
                img.getScaledInstance(48, 48, Image.SCALE_SMOOTH), img);
    }
}
