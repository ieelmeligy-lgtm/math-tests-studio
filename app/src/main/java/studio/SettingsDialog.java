package studio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** The backend address and the staff account the app uses to write tests to the Sheet. The password is saved encrypted on this PC. */
public final class SettingsDialog extends JDialog {
    public SettingsDialog(Frame owner, Settings s) {
        super(owner, "Settings", true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.CREAM);
        root.add(Theme.header("settings", "Settings","Saved only on this PC, encrypted for your Windows account"), BorderLayout.NORTH);

        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField name = new JTextField(s.adminName(), 30);
        JPasswordField pw = new JPasswordField(s.adminPassword(), 30);
        pw.putClientProperty("JPasswordField.showRevealButton", true);
        String repo = s.repoFolder();
        JTextField backend = new JTextField(repo.isEmpty() ? "" : Backend.urlFromConfig(java.nio.file.Path.of(repo)), 30);
        backend.setEnabled(!repo.isEmpty());

        int row = 0;
        row = addRow(card, c, row, "Backend web app URL", backend, repo.isEmpty() ? "Pick the site folder (step 1) first"
                : "Apps Script → Deploy → Web app URL ending in /exec (saved into docs/config.js)");
        row = addRow(card, c, row, "Teacher / Admin name", name, "A Teacher or Admin row in the Users tab");
        addRow(card, c, row, "Teacher / Admin password", pw, "Used only to sign in to the backend");

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 16, 8, 16));
        body.add(card);
        root.add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setOpaque(false);
        JButton cancel = Theme.softButton("Cancel");
        JButton save = Theme.primaryButton("Save");
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> {
            String url = backend.getText().trim();
            if (!repo.isEmpty() && !url.isEmpty()) {
                try {
                    if (Backend.writeUrlToConfig(java.nio.file.Path.of(repo), url))
                        JOptionPane.showMessageDialog(this, "Saved into docs/config.js. It goes online with the next Publish.", "Backend address", JOptionPane.INFORMATION_MESSAGE);
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Backend address", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            s.set("admin.name", name.getText());
            s.setSecret("admin.password", new String(pw.getPassword()));
            s.save();
            dispose();
        });
        buttons.add(cancel);
        buttons.add(save);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(Theme.credit(), BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private static int addRow(JPanel p, GridBagConstraints c, int row, String label, JComponent field, String help) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(Font.BOLD, 14f));
        l.setForeground(Theme.COFFEE);
        c.gridx = 0; c.gridy = row * 2; c.weightx = 0;
        p.add(l, c);
        c.gridx = 1; c.weightx = 1;
        p.add(field, c);
        JLabel h = new JLabel(help);
        h.setFont(Theme.font(Font.PLAIN, 12f));
        h.setForeground(Theme.COFFEE_MID);
        c.gridx = 1; c.gridy = row * 2 + 1;
        c.insets = new Insets(0, 4, 8, 4);
        p.add(h, c);
        c.insets = new Insets(6, 4, 6, 4);
        return row + 1;
    }
}
