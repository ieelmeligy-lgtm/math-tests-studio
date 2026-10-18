package studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Talks to the Apps Script web app that owns the Google Sheet (same backend the website uses). */
public final class Backend {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final String url;
    private String token;

    public Backend(String url) { this.url = url; }

    private static final Pattern API_LINE = Pattern.compile("PORTAL_API\\s*=\\s*'([^']*)'");

    static Path configJs(Path repo) { return repo.resolve("docs").resolve("config.js"); }

    /** Reads the backend address from the site's docs/config.js, so the app and the site always use the same one. */
    public static String urlFromConfig(Path repo) {
        try {
            Matcher m = API_LINE.matcher(Files.readString(configJs(repo), StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Writes the backend address into docs/config.js. Returns true when the file changed. */
    public static boolean writeUrlToConfig(Path repo, String url) throws IOException {
        if (!url.matches("https://script\\.google\\.com/macros/s/[\\w-]+/exec")) throw new IOException("That is not an Apps Script web app address ending in /exec.");
        Path f = configJs(repo);
        String js = Files.readString(f, StandardCharsets.UTF_8);
        String next = API_LINE.matcher(js).replaceFirst(Matcher.quoteReplacement("PORTAL_API = '" + url + "'"));
        if (next.equals(js)) return false;
        Files.writeString(f, next, StandardCharsets.UTF_8);
        return true;
    }

    JsonNode call(ObjectNode body) throws IOException {
        // text/plain, like the website, so Apps Script reads it from e.postData.contents
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/plain;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IOException("Backend answered HTTP " + res.statusCode());
            return JSON.readTree(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException("Backend did not answer with JSON. Is the Studio update deployed?", e);
        }
    }

    /** Health check: backend reachable and the Sheet tabs exist. Returns the raw answer. */
    public JsonNode ping() throws IOException {
        ObjectNode b = JSON.createObjectNode();
        b.put("action", "ping");
        return call(b);
    }

    /** Signs in as staff (Teacher or Admin) to get a token for addTest. */
    public void loginStaff(String name, String password) throws IOException {
        ObjectNode b = JSON.createObjectNode();
        b.put("action", "login");
        b.put("grade", "staff");
        b.put("name", name);
        b.put("password", password);
        JsonNode r = call(b);
        if (!r.path("ok").asBoolean()) throw new IOException("Staff sign-in failed: " + r.path("error").asText("unknown"));
        String role = r.path("role").asText("").toLowerCase();
        if (!role.equals("admin") && !role.equals("teacher")) throw new IOException("This account is not a Teacher or Admin.");
        token = r.path("token").asText();
    }

    /** Writes the test row (Tests tab) and its answer key (Questions tab). Replaces any earlier rows with the same Test ID. */
    public void addTest(String grade, String testId, String title, String file, TestDraft d) throws IOException {
        if (token == null) throw new IOException("Not signed in to the backend.");
        ObjectNode b = JSON.createObjectNode();
        b.put("action", "addTest");
        b.put("token", token);
        b.put("grade", grade);
        b.put("testId", testId);
        b.put("title", title);
        b.put("file", file);
        ArrayNode qs = b.putArray("questions");
        for (int i = 0; i < d.questions.size(); i++) {
            TestDraft.Question q = d.questions.get(i);
            ObjectNode o = qs.addObject();
            o.put("no", i + 1);
            o.put("correct", String.valueOf((char) ('A' + q.correctIndex)));
            o.put("explanation", Html.sanitize(q.explanationHtml));
        }
        JsonNode r = call(b);
        if (!r.path("ok").asBoolean()) throw new IOException("The Sheet did not accept the test: " + r.path("error").asText("unknown"));
    }
}
