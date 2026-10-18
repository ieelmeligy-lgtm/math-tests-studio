package studio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** School colours (turquoise shirt, coffee trousers, cream page) and shared widgets. */
public final class Theme {
    public static final Color TURQ = new Color(0x17b3b0);
    public static final Color TURQ_DEEP = new Color(0x0c7f7c);
    public static final Color TURQ_SOFT = new Color(0xe2f5f4);
    public static final Color COFFEE = new Color(0x4e3426);
    public static final Color COFFEE_MID = new Color(0x7a5a47);
    public static final Color CREAM = new Color(0xf7f2eb);
    public static final Color CARD = new Color(0xfffdfa);
    public static final Color LINE = new Color(0xe6dccf);
    public static final Color GOLD = new Color(0xe0a526);
    public static final Color RED = new Color(0xb8392e);
    public static final Color GREEN = new Color(0x2e9d5b);
    public static final Color GREY = new Color(0xb9ab9a);

    /** One colour per step button, so the four steps are easy to tell apart. */
    public static final Color[] STEP_COLORS = {
            new Color(0x17b3b0), new Color(0xe0a526), new Color(0x8e5bd6), new Color(0xe26d4f)
    };

    public static final String CREDIT = "Courtesy of Miss Sarah Awad – Math Teacher";

    private Theme() {}

    public static Font font(int style, float size) {
        return new Font("Segoe UI", style, 12).deriveFont(size);
    }

    /** Big rounded colourful step button with its icon on a white badge. */
    public static JButton bigButton(String text, Color color, String icon) {
        JButton b = bigButton(text, color);
        b.setIcon(Icons.badge(icon, 40));
        b.setDisabledIcon(Icons.badge(icon, 40));
        b.setIconTextGap(14);
        return b;
    }

    public static JButton softButton(String text, String icon) {
        JButton b = softButton(text);
        b.setIcon(Icons.get(icon, 20));
        b.setIconTextGap(8);
        return b;
    }

    public static JButton primaryButton(String text, String icon) {
        JButton b = primaryButton(text);
        b.setIcon(Icons.badge(icon, 26));
        b.setIconTextGap(8);
        return b;
    }

    /** Header with the window's icon on a white badge before the title. */
    public static JPanel header(String icon, String title, String subtitle) {
        JPanel h = header(title, subtitle);
        JLabel badge = new JLabel(Icons.badge(icon, 52));
        badge.setBorder(new EmptyBorder(0, 0, 0, 14));
        h.add(badge, BorderLayout.WEST);
        return h;
    }

    /** Big rounded colourful button. */
    public static JButton bigButton(String text, Color color) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = isEnabled() ? color : GREY;
                if (getModel().isPressed()) base = base.darker();
                else if (getModel().isRollover()) base = brighter(base);
                g2.setPaint(new GradientPaint(0, 0, brighter(base), 0, getHeight(), base));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(Color.WHITE);
        b.setFont(font(Font.BOLD, 17f));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(14, 20, 14, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(320, 64));
        return b;
    }

    public static JButton softButton(String text) {
        JButton b = new JButton(text);
        b.setFont(font(Font.BOLD, 14f));
        b.setForeground(TURQ_DEEP);
        b.setBackground(TURQ_SOFT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("JButton.buttonType", "roundRect");
        return b;
    }

    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(font(Font.BOLD, 14f));
        b.setForeground(Color.WHITE);
        b.setBackground(TURQ_DEEP);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("JButton.buttonType", "roundRect");
        return b;
    }

    /** White rounded card on the cream page. */
    public static JPanel card() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(78, 52, 38, 18));
                g2.fill(new RoundRectangle2D.Float(2, 4, getWidth() - 4, getHeight() - 4, 22, 22));
                g2.setColor(CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 3, getHeight() - 5, 22, 22));
                g2.setColor(LINE);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 3, getHeight() - 5, 22, 22));
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(16, 18, 18, 18));
        return p;
    }

    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(font(Font.BOLD, 12f));
        l.setForeground(COFFEE_MID);
        return l;
    }

    /** Turquoise woven-cloth header with a coffee band, used by the main window and every pop-up. */
    public static JPanel header(String title, String subtitle) {
        JPanel h = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth(), hh = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x1cc2be), w, hh, TURQ_DEEP));
                g2.fillRect(0, 0, w, hh);
                g2.setColor(new Color(255, 255, 255, 16));
                for (int x = -hh; x < w; x += 6) g2.drawLine(x, hh, x + hh, 0);
                g2.setColor(new Color(0, 0, 0, 12));
                for (int x = 0; x < w + hh; x += 6) g2.drawLine(x, hh, x - hh, 0);
                g2.setColor(COFFEE);
                g2.fillRect(0, hh - 6, w, 6);
                g2.dispose();
            }
        };
        h.setBorder(new EmptyBorder(16, 22, 20, 22));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(font(Font.BOLD, 24f));
        t.setForeground(Color.WHITE);
        text.add(t);
        if (subtitle != null) {
            JLabel s = new JLabel(subtitle);
            s.setFont(font(Font.PLAIN, 14f));
            s.setForeground(new Color(255, 255, 255, 230));
            text.add(s);
        }
        h.add(text, BorderLayout.CENTER);
        return h;
    }

    public static JLabel credit() {
        JLabel c = new JLabel(CREDIT, SwingConstants.CENTER);
        c.setFont(font(Font.ITALIC, 13f));
        c.setForeground(COFFEE_MID);
        c.setBorder(new EmptyBorder(6, 10, 10, 10));
        return c;
    }

    static Color brighter(Color c) {
        return new Color(Math.min(255, c.getRed() + 28), Math.min(255, c.getGreen() + 28), Math.min(255, c.getBlue() + 28));
    }
}
