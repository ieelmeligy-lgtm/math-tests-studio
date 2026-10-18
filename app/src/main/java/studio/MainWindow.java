package studio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Main window: connection lights, the four steps, and an activity log. */
public final class MainWindow extends JFrame {
    private final Settings settings = new Settings();
    private final JTextArea log = new JTextArea(6, 40);
    private final JProgressBar progress = new JProgressBar();
    /** Rounded ONLINE / NOT READY pill. */
    private final JLabel online = new JLabel("  CHECKING…  ") {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    };
    private final JLabel[] stepInfo = new JLabel[4];
    private final JButton[] steps = new JButton[4];
    private StatusPanel status;

    private ImportDialog.Imported imported;
    private String title;
    private boolean approved;

    public MainWindow() {
        super("Math Tests Studio");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.CREAM);

        // Header with ONLINE pill and settings
        setIconImages(Icons.windowImages());
        JPanel header = Theme.header("app", "Math Tests Studio", "Test files from Claude → live website");
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        right.setOpaque(false);
        online.setOpaque(false);
        online.setFont(Theme.font(Font.BOLD, 14f));
        online.setForeground(Color.WHITE);
        online.setBackground(Theme.GOLD);
        online.setBorder(new EmptyBorder(6, 10, 6, 10));
        online.putClientProperty("FlatLaf.style", "arc: 999");
        JButton gear = Theme.softButton("Settings", "settings");
        gear.addActionListener(e -> { new SettingsDialog(this, settings).setVisible(true); status.checkAll(); });
        right.add(online);
        right.add(gear);
        header.add(right, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(16, 18, 6, 18));

        JPanel statusCard = Theme.card();
        statusCard.setLayout(new BorderLayout());
        status = new StatusPanel(() -> settings, this::setOnline);
        statusCard.add(status);
        center.add(statusCard);
        center.add(Box.createVerticalStrut(14));

        JPanel stepsCard = Theme.card();
        stepsCard.setLayout(new GridLayout(2, 2, 14, 14));
        String[] names = {"1  ·  Site folder", "2  ·  Import test", "3  ·  Review test", "4  ·  Publish & deploy"};
        String[] stepIcons = {"folder", "import", "review", "publish"};
        Runnable[] actions = {this::stepRepo, this::stepImport, this::review, this::stepPublish};
        for (int i = 0; i < 4; i++) {
            JPanel cell = new JPanel(new BorderLayout(0, 4));
            cell.setOpaque(false);
            steps[i] = Theme.bigButton(names[i], Theme.STEP_COLORS[i], stepIcons[i]);
            int k = i;
            steps[i].addActionListener(e -> actions[k].run());
            stepInfo[i] = new JLabel(" ");
            stepInfo[i].setFont(Theme.font(Font.PLAIN, 12f));
            stepInfo[i].setForeground(Theme.COFFEE_MID);
            cell.add(steps[i], BorderLayout.CENTER);
            cell.add(stepInfo[i], BorderLayout.SOUTH);
            stepsCard.add(cell);
        }
        center.add(stepsCard);
        center.add(Box.createVerticalStrut(14));

        JPanel logCard = Theme.card();
        logCard.setLayout(new BorderLayout(0, 8));
        JPanel logTop = new JPanel(new BorderLayout(10, 0));
        logTop.setOpaque(false);
        logTop.add(Theme.sectionLabel("Activity"), BorderLayout.WEST);
        progress.setPreferredSize(new Dimension(220, 10));
        logTop.add(progress, BorderLayout.EAST);
        logCard.add(logTop, BorderLayout.NORTH);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setBackground(Theme.CARD);
        logCard.add(new JScrollPane(log));
        center.add(logCard);

        root.add(center, BorderLayout.CENTER);
        root.add(Theme.credit(), BorderLayout.SOUTH);
        setContentPane(root);
        setSize(980, 820);
        setMinimumSize(new Dimension(820, 700));
        setLocationRelativeTo(null);

        log("Welcome! Make the test in Claude, download the HTML file into docs\\GradeN, then import it here.");
        if (!settings.repoFolder().isEmpty()) {
            GitOps git = new GitOps(Path.of(settings.repoFolder()));
            git.ensureHook();
            stepInfo[0].setText(settings.repoFolder());
            announceWaiting();
        } else {
            log("Start with step 1: pick the folder of your " + Settings.REPO_NAME + " repo.");
        }
        if (settings.adminName().isEmpty()) log("Open Settings to add the backend address and the Teacher/Admin account.");
        refreshSteps();
        status.checkAll();
    }

    private void setOnline(boolean ok) {
        online.setText(ok ? "  ● ONLINE  " : "  ● NOT READY  ");
        online.setBackground(ok ? Theme.GREEN : Theme.RED);
        online.setToolTipText(ok ? "Everything is connected" : "Hover the red lights below to see what needs attention");
    }

    private void refreshSteps() {
        steps[1].setEnabled(!settings.repoFolder().isEmpty());
        steps[2].setEnabled(imported != null);
        steps[3].setEnabled(imported != null && approved);
    }

    void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            log.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + msg + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void announceWaiting() {
        // A folder per grade, so there is always a place to download the files from Claude into
        for (String g : grades()) {
            try { Files.createDirectories(Path.of(settings.repoFolder(), "docs", "Grade" + g)); } catch (Exception ignored) {}
        }
        List<Path> w = ImportDialog.waiting(Path.of(settings.repoFolder()));
        if (w.isEmpty()) { stepInfo[1].setText("No new test files in the grade folders"); return; }
        stepInfo[1].setText(w.size() + " new test file(s) waiting to import");
        for (Path p : w) log("Waiting to import: " + Path.of(settings.repoFolder(), "docs").relativize(p));
    }

    private void stepRepo() {
        JFileChooser fc = new JFileChooser(settings.repoFolder().isEmpty() ? null : new File(settings.repoFolder()));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Folder of your " + Settings.REPO_NAME + " repo (it has a docs folder)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path p = fc.getSelectedFile().toPath();
        GitOps git = new GitOps(p);
        if (!git.isPortalRepo()) {
            JOptionPane.showMessageDialog(this, "This folder is not a clone of " + Settings.REPO_NAME
                    + " with a docs/ site.\nPick the folder that contains app, docs and backend.", "Not the site folder", JOptionPane.WARNING_MESSAGE);
            return;
        }
        git.ensureHook();
        settings.set("repo.folder", p.toString());
        settings.save();
        stepInfo[0].setText(p + "  →  " + git.siteUrl());
        log("Site folder: " + p + " (" + git.ownerRepo() + ")");
        announceWaiting();
        refreshSteps();
        status.checkAll();
    }

    private void stepImport() {
        Path repo = Path.of(settings.repoFolder());
        ImportDialog d = new ImportDialog(this, repo, grades());
        d.setVisible(true);
        if (d.result() == null) return;
        imported = d.result();
        approved = false;
        title = imported.draft().suggestedTitle;
        TestDraft draft = imported.draft();
        stepInfo[1].setText("Grade " + imported.grade() + " · " + imported.fileName() + " · " + (draft.arabic() ? "Arabic" : "English")
                + " · " + draft.questions.size() + " questions");
        stepInfo[2].setText(" ");
        stepInfo[3].setText(" ");
        stepInfo[2].setIcon(null);
        stepInfo[3].setIcon(null);
        log("Imported " + imported.source().getFileName() + ": " + draft.questions.size() + " question(s), "
                + (draft.arabic() ? "Arabic" : "English") + ". Answer key: " + draft.answerKey());
        if (!draft.skipped.isEmpty()) log("Left out: " + String.join(" | ", draft.skipped));
        refreshSteps();
        review();
    }

    private void review() {
        if (imported == null) return;
        TestDraft draft = imported.draft();
        ReviewDialog r = new ReviewDialog(this, draft, imported.grade(), title == null || title.isEmpty() ? draft.suggestedTitle : title);
        r.setVisible(true);
        if (r.approved()) {
            approved = true;
            title = r.title();
            stepInfo[2].setText("Approved: " + title + " · " + draft.questions.size() + " questions");
            stepInfo[2].setIcon(Icons.get("ok", 16));
            log("Approved \"" + title + "\". Answer key: " + draft.answerKey());
        } else if (!approved) {
            stepInfo[2].setText("Not approved yet (press 3 to review)");
            stepInfo[2].setIcon(Icons.get("warning", 16));
        }
        refreshSteps();
    }

    private void stepPublish() {
        if (imported == null || !approved) return;
        Path page = Path.of(settings.repoFolder(), "docs").resolve(imported.rel());
        boolean inPlace = page.toAbsolutePath().normalize().equals(imported.source().toAbsolutePath().normalize());
        if (!inPlace && Files.exists(page) && JOptionPane.showConfirmDialog(this, "A page named " + page.getFileName()
                + " already exists for Grade " + imported.grade() + ". Replace it?", "Replace?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        PublishDialog p = new PublishDialog(this, settings, imported, title, this::log);
        p.setVisible(true);
        if (p.published()) {
            stepInfo[3].setText("Live: " + p.liveUrl());
            stepInfo[3].setIcon(Icons.get("ok", 16));
            imported = null;
            approved = false;
            announceWaiting();
            refreshSteps();
        } else {
            stepInfo[3].setText("Stopped – see the activity log, fix it and press 4 again");
            stepInfo[3].setIcon(Icons.get("fail", 16));
        }
        status.checkAll();
    }

    /** Grades come from the site's config.js so the app and the sign-in page match. */
    private List<String> grades() {
        List<String> out = new ArrayList<>();
        try {
            String js = Files.readString(Path.of(settings.repoFolder(), "docs", "config.js"));
            Matcher m = Pattern.compile("PORTAL_GRADES\\s*=\\s*\\[([^\\]]*)\\]").matcher(js);
            if (m.find()) for (Matcher g = Pattern.compile("'(\\w+)'").matcher(m.group(1)); g.find(); ) out.add(g.group(1));
        } catch (Exception ignored) {}
        if (out.isEmpty()) out.addAll(List.of("5", "6", "7", "8", "9"));
        return out;
    }
}
