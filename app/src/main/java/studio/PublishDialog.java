package studio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Step 4: save the page, commit, push to GitHub, write the answer key to the Sheet, wait for the live page. */
public final class PublishDialog extends JDialog {
    private static final String[] STEPS = {
            "Get the latest site from GitHub",
            "Save the test page",
            "Commit the change",
            "Push to GitHub (a sign-in window may open the first time)",
            "Write the test and answers to the Google Sheet",
            "Wait for the live website to update"
    };
    private final JLabel[] icons = new JLabel[STEPS.length];
    private final JLabel[] details = new JLabel[STEPS.length];
    private final JButton close = Theme.primaryButton("Close");
    private final JButton open = Theme.softButton("🌐  Open the live test");
    private final JButton copy = Theme.softButton("📋  Copy answer key");
    private final JLabel result = new JLabel(" ");
    private String liveUrl;

    public PublishDialog(Frame owner, Settings s, ImportDialog.Imported test, String title, java.util.function.Consumer<String> log) {
        super(owner, "Step 4 – Publish & deploy", true);
        TestDraft draft = test.draft();
        String grade = test.grade();
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.CREAM);
        root.add(Theme.header("🚀  Publish & deploy", title + " · Grade " + grade), BorderLayout.NORTH);

        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 4, 5, 10);
        for (int i = 0; i < STEPS.length; i++) {
            icons[i] = new JLabel("○");
            icons[i].setFont(Theme.font(Font.BOLD, 20f));
            icons[i].setForeground(Theme.GREY);
            JLabel t = new JLabel(STEPS[i]);
            t.setFont(Theme.font(Font.BOLD, 14f));
            t.setForeground(Theme.COFFEE);
            details[i] = new JLabel(" ");
            details[i].setFont(Theme.font(Font.PLAIN, 12f));
            details[i].setForeground(Theme.COFFEE_MID);
            JPanel txt = new JPanel(new GridLayout(2, 1));
            txt.setOpaque(false);
            txt.add(t);
            txt.add(details[i]);
            c.gridy = i; c.gridx = 0; c.weightx = 0;
            card.add(icons[i], c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            card.add(txt, c);
            c.fill = GridBagConstraints.NONE;
        }
        result.setFont(Theme.font(Font.BOLD, 15f));
        result.setForeground(Theme.TURQ_DEEP);
        c.gridy = STEPS.length; c.gridx = 0; c.gridwidth = 2; c.insets = new Insets(14, 4, 4, 4);
        card.add(result, c);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 16, 8, 16));
        body.add(card);
        root.add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setOpaque(false);
        open.setEnabled(false);
        close.setEnabled(false);
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(draft.answerKey()), null));
        close.addActionListener(e -> dispose());
        buttons.add(copy);
        buttons.add(open);
        buttons.add(close);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(Theme.credit(), BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        setContentPane(root);
        setSize(720, 620);
        setLocationRelativeTo(owner);

        Path repo = Path.of(s.repoFolder());
        GitOps git = new GitOps(repo);
        String rel = test.rel();                                    // Grade6/x.html, as listed in the Tests tab
        String repoRel = "docs/" + rel;                             // path inside the repo
        String testId = PageBuilder.testId(grade, test.fileName());

        new SwingWorker<String, Object[]>() {
            int step;

            void mark(int i, String state, String detail) { publish(new Object[]{i, state, detail}); }

            void fail(String detail) throws Exception {
                mark(step, "fail", detail);
                throw new Exception(detail);
            }

            @Override
            protected String doInBackground() throws Exception {
                step = 0; mark(0, "run", "git pull");
                GitOps.Result r = git.pull();
                if (!r.ok()) fail(last(r.out()));
                mark(0, "ok", "Up to date");

                step = 1; mark(1, "run", repoRel);
                // Keep the file from Claude (with the answers) privately on this PC, outside the repo
                Path source = test.source().toAbsolutePath().normalize();
                Path backup = Settings.originalsDir().resolve("Grade" + grade).resolve(test.fileName()).toAbsolutePath().normalize();
                // Only a file that still has the answers is kept: on a second Publish the source is already the public page
                if (!source.equals(backup) && AnswerGuard.hasAnswers(source)) {
                    Files.createDirectories(backup.getParent());
                    Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
                }
                Path page = repo.resolve(repoRel).toAbsolutePath().normalize();
                Files.createDirectories(page.getParent());
                String html = PageBuilder.build(draft, grade, title, testId);
                if (AnswerGuard.hasAnswers(html)) fail("The page still contained answers, so it was not saved.");
                Files.writeString(page, html, StandardCharsets.UTF_8);
                // A downloaded file with a name the site cannot use was saved under a new name: remove the one with the answers
                if (!source.equals(page) && source.startsWith(repo.resolve("docs").toAbsolutePath().normalize())) Files.deleteIfExists(source);
                mark(1, "ok", repoRel + " · Test ID " + testId + " · answers removed from the page");

                step = 2; mark(2, "run", "git commit");
                List<String> files = new ArrayList<>(List.of(repoRel));
                if (!git.run(15, "status", "--porcelain", "--", "docs/config.js").out().isBlank()) files.add("docs/config.js");
                for (String f : files) {
                    if (f.endsWith(".html") && AnswerGuard.hasAnswers(repo.resolve(f))) fail(f + " contains answers – not committed.");
                }
                git.ensureHook();
                // Publishing again with the same content: the page is already on GitHub, nothing to commit
                files.removeIf(f -> { try { return git.run(15, "status", "--porcelain", "--", f).out().isBlank(); } catch (Exception e) { return false; } });
                if (files.isEmpty()) {
                    mark(2, "ok", "No change – the page is already on GitHub");
                } else {
                    r = git.commitFiles(files, "Add test: Grade " + grade + " – " + title);
                    if (!r.ok() && !r.out().contains("nothing to commit") && !r.out().contains("no changes added to commit")) fail(last(r.out()));
                    mark(2, "ok", files.size() == 1 ? "1 file" : "Test page and backend address");
                }

                step = 3; mark(3, "run", "Pushing…");
                r = git.push();
                if (!r.ok()) fail(last(r.out()));
                mark(3, "ok", "Pushed to " + git.ownerRepo());

                step = 4; mark(4, "run", "Signing in as " + s.adminName());
                String api = Backend.urlFromConfig(repo);
                if (api.isEmpty()) fail("No backend address. Add it in Settings.");
                if (s.adminName().isEmpty() || s.adminPassword().isEmpty()) fail("Add the Teacher/Admin name and password in Settings.");
                Backend be = new Backend(api);
                be.loginStaff(s.adminName(), s.adminPassword());
                be.addTest(grade, testId, title, rel, draft);
                mark(4, "ok", "Tests row + " + draft.questions.size() + " answers saved");

                step = 5;
                String url = git.siteUrl() + rel;
                mark(5, "run", "Waiting for " + url);
                long until = System.currentTimeMillis() + 6 * 60_000;
                while (System.currentTimeMillis() < until) {
                    if (StatusPanel.urlOk(url)) { mark(5, "ok", url); return url; }
                    Thread.sleep(10_000);
                }
                fail("Not live after 6 minutes. Check that GitHub Pages is on (branch main, folder /docs).");
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] ch : chunks) {
                    int i = (Integer) ch[0];
                    String st = (String) ch[1];
                    icons[i].setText(switch (st) { case "ok" -> "✔"; case "fail" -> "✖"; default -> "⏳"; });
                    icons[i].setForeground(switch (st) { case "ok" -> Theme.GREEN; case "fail" -> Theme.RED; default -> Theme.GOLD; });
                    details[i].setText((String) ch[2]);
                    log.accept(STEPS[i] + ": " + ch[2]);
                }
            }

            @Override
            protected void done() {
                close.setEnabled(true);
                setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                try {
                    String url = get();
                    liveUrl = url;
                    result.setText("<html>🎉 Published! Answer key: " + draft.answerKey() + "</html>");
                    open.setEnabled(true);
                    open.addActionListener(e -> { try { Desktop.getDesktop().browse(URI.create(url)); } catch (Exception ignored) {} });
                    log.accept("Published " + url + " · answer key " + draft.answerKey());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    result.setForeground(Theme.RED);
                    result.setText("<html>Stopped: " + Html.escape(cause.getMessage()) + "</html>");
                }
            }
        }.execute();
    }

    public boolean published() { return liveUrl != null; }
    public String liveUrl() { return liveUrl; }

    private static String last(String out) {
        String[] lines = out.strip().split("\\R");
        return lines.length == 0 ? "failed" : lines[lines.length - 1];
    }
}
