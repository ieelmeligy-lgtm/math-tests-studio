package studio;

import com.fasterxml.jackson.databind.JsonNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Six connection lights, checked at start-up and every minute. */
public final class StatusPanel extends JPanel {
    public enum State { CHECKING, OK, PROBLEM }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

    private final Map<String, Light> lights = new LinkedHashMap<>();
    private final Supplier<Settings> settings;
    private final Consumer<Boolean> onAllOk;
    private final JLabel checked = new JLabel(" ");
    private volatile boolean running;

    public StatusPanel(Supplier<Settings> settings, Consumer<Boolean> onAllOk) {
        this.settings = settings;
        this.onAllOk = onAllOk;
        setOpaque(false);
        setLayout(new BorderLayout(0, 8));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Theme.sectionLabel("Connections"), BorderLayout.WEST);
        JButton again = Theme.softButton("Check now", "refresh");
        again.addActionListener(e -> checkAll());
        top.add(again, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 3, 10, 10));
        grid.setOpaque(false);
        for (String name : new String[]{"Internet", "Test files", "GitHub repo", "Backend (Apps Script)", "Google Sheet", "Live website"}) {
            Light l = new Light(name);
            lights.put(name, l);
            grid.add(l);
        }
        add(grid, BorderLayout.CENTER);
        checked.setFont(Theme.font(Font.PLAIN, 12f));
        checked.setForeground(Theme.COFFEE_MID);
        add(checked, BorderLayout.SOUTH);

        new Timer(60_000, e -> checkAll()).start();
    }

    public void checkAll() {
        if (running) return;
        running = true;
        lights.values().forEach(l -> l.set(State.CHECKING, "Checking…"));
        Thread t = new Thread(() -> {
            Settings s = settings.get();
            boolean net = urlOk("https://www.gstatic.com/generate_204");
            set("Internet", net, net ? "Connected to the internet" : "No internet connection");

            String repo = s.repoFolder();
            boolean files = !repo.isEmpty() && java.nio.file.Files.isDirectory(Path.of(repo, "docs"));
            String filesMsg = "Pick the site folder (step 1)";
            if (files) {
                int n = ImportDialog.waiting(Path.of(repo)).size();
                filesMsg = n == 0 ? "Grade folders found in docs – no new test files" : n + " new test file(s) waiting to import";
            }
            set("Test files", files, filesMsg);

            boolean gh = false;
            String ghMsg = "Pick the site folder (step 1)", siteUrl = "";
            if (!repo.isEmpty()) {
                GitOps git = new GitOps(Path.of(repo));
                if (!git.isPortalRepo()) ghMsg = "This folder is not a clone of " + Settings.REPO_NAME + " with a docs/ site";
                else if (!git.remoteReachable()) ghMsg = "Cannot reach GitHub";
                else { gh = true; ghMsg = git.ownerRepo() + ", branch main"; siteUrl = git.siteUrl(); }
            }
            set("GitHub repo", gh, ghMsg);

            String api = repo.isEmpty() ? "" : Backend.urlFromConfig(Path.of(repo));
            boolean be = false, sheet = false;
            String beMsg = api.isEmpty() ? "Add the Apps Script web app address in Settings" : "No answer";
            String sheetMsg = "Waiting for the backend";
            if (!api.isEmpty() && net) {
                try {
                    JsonNode r = new Backend(api).ping();
                    be = r.path("ok").asBoolean();
                    beMsg = be ? "Web app answering" : "Backend answered: " + r.path("error").asText("unknown")
                            + (r.path("error").asText().equals("unknown_action") ? " – deploy the latest backend/Code.gs" : "");
                    if (be) {
                        StringBuilder missing = new StringBuilder();
                        for (JsonNode tab : r.path("tabs")) if (!tab.path("ok").asBoolean()) missing.append(' ').append(tab.path("name").asText());
                        sheet = missing.length() == 0;
                        sheetMsg = sheet ? "Users, Tests, Questions and Results tabs found" : "Missing tabs:" + missing + " (run setup() in Apps Script)";
                    }
                } catch (Exception ex) {
                    beMsg = ex.getMessage();
                }
            }
            set("Backend (Apps Script)", be, beMsg);
            set("Google Sheet", sheet, sheetMsg);

            boolean site = net && !siteUrl.isEmpty() && urlOk(siteUrl);
            set("Live website", site, site ? siteUrl : siteUrl.isEmpty() ? "Known once the GitHub repo is connected"
                    : "Not live yet – turn on GitHub Pages (branch main, folder /docs)");

            boolean all = net && files && gh && be && sheet && site;
            SwingUtilities.invokeLater(() -> {
                checked.setText("Last checked " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + " · hover a light for details");
                onAllOk.accept(all);
            });
            running = false;
        }, "status-check");
        t.setDaemon(true);
        t.start();
    }

    private void set(String name, boolean ok, String detail) {
        SwingUtilities.invokeLater(() -> lights.get(name).set(ok ? State.OK : State.PROBLEM, detail));
    }

    static boolean urlOk(String url) {
        try {
            HttpResponse<Void> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    /** A rounded pill with a coloured dot. */
    static final class Light extends JPanel {
        private final JLabel text;
        private Color dot = Theme.GREY;

        Light(String name) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(10, 34, 10, 12));
            text = new JLabel(name);
            text.setFont(Theme.font(Font.BOLD, 14f));
            text.setForeground(Theme.COFFEE);
            add(text);
            setPreferredSize(new Dimension(200, 44));
        }

        void set(State s, String detail) {
            dot = switch (s) { case OK -> Theme.GREEN; case PROBLEM -> Theme.RED; default -> Theme.GOLD; };
            setToolTipText(detail);
            text.setToolTipText(detail);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.TURQ_SOFT);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            int d = 16, y = (getHeight() - d) / 2;
            g2.setColor(new Color(dot.getRed(), dot.getGreen(), dot.getBlue(), 60));
            g2.fillOval(8, y - 3, d + 6, d + 6);
            g2.setColor(dot);
            g2.fillOval(11, y, d, d);
            g2.dispose();
        }
    }
}
