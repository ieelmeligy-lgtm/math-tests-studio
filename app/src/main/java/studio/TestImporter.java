package studio;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a test page made in Claude with the math-tests-portal skill (English or Arabic):
 * question cards with the correct option marked data-c and an explanation in .why.
 * Pictures come along: those already inside the file (data: URIs) and PNG/JPEG/GIF/WebP files next to it,
 * which are put inside the page so the site needs no separate image files. Figure blocks placed between a
 * question and its options (e.g. div.figwrap) are kept too.
 * The answers stay in the app and go to the Sheet; the public page is rebuilt without them.
 */
public final class TestImporter {
    /** Written into every page the app publishes, so a published page is never read as a new test. */
    public static final String GENERATOR = "Math Tests Studio";

    private static final Pattern GRADE_DIR = Pattern.compile("(?i)^grade(\\d+)$");
    private static final Pattern REQUIRE = Pattern.compile("requireLogin\\(\\s*'(\\w+)'");

    public enum Kind { NEW, PUBLISHED, UNKNOWN }

    /** Ends the note on a question whose correct answer the teacher still has to choose. */
    public static final String UNSURE = " – tick the correct option.";

    /** What a file in a grade folder is, without reading it fully. */
    public static Kind kind(Path file) {
        try {
            String html = Files.readString(file, StandardCharsets.UTF_8);
            if (html.contains("<meta name=\"generator\" content=\"" + GENERATOR + "\"")) return Kind.PUBLISHED;
            return AnswerGuard.hasAnswers(html) ? Kind.NEW : Kind.UNKNOWN;
        } catch (IOException e) {
            return Kind.UNKNOWN;
        }
    }

    /** Grade from the folder name (docs/Grade6/x.html gives 6), or "" when the folder is not a grade folder. */
    public static String gradeFromFolder(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) return "";
        Matcher m = GRADE_DIR.matcher(parent.getFileName().toString());
        return m.matches() ? m.group(1) : "";
    }

    public static TestDraft read(Path file) throws IOException {
        Document doc = Jsoup.parse(file.toFile(), "UTF-8");
        TestDraft d = new TestDraft();
        d.lang = detectLang(doc);

        Element h1 = doc.selectFirst("header h1, h1");
        d.suggestedTitle = h1 != null ? h1.text().trim() : doc.title().trim();
        collectImages(doc, file, d);

        Element quiz = doc.getElementById("quiz");
        Element scope = quiz != null ? quiz : doc.body();
        String section = "";
        int pos = 0;
        for (Element el : scope.select("h2, h3, div.card")) {
            if (!el.tagName().equals("div")) {
                section = Html.sanitize(el.html());
                continue;
            }
            Element stem = el.selectFirst(".stem");
            if (stem == null) continue;
            pos++;
            Element num = el.selectFirst(".num");
            String label = num != null && !num.text().isBlank() ? num.text().trim() : String.valueOf(pos);
            String plain = Html.toPlain(stem.html());
            String shortText = plain.length() > 50 ? plain.substring(0, 50) + "…" : plain;

            Elements inputs = el.select("input[type=radio]");
            if (inputs.isEmpty()) {
                d.skipped.add("Question " + label + " has no answer options (not multiple choice): " + shortText);
                continue;
            }
            TestDraft.Question q = new TestDraft.Question();
            q.number = Integer.parseInt(label.replaceAll("\\D", "").isEmpty() ? String.valueOf(pos) : label.replaceAll("\\D", ""));
            q.sectionHtml = section;
            q.stemHtml = Html.sanitize(stem.html());
            for (Element part : el.children()) {
                if (part.is(".q-head, .opts, .why, input, script, style") || part.selectFirst(".stem, input") != null) continue;
                String fig = Html.sanitize(part.is("img, svg") ? part.outerHtml() : part.html());
                if (!fig.isEmpty()) q.figuresHtml.add(fig);
            }

            int marked = 0, correct = -1;
            for (int k = 0; k < inputs.size(); k++) {
                Element in = inputs.get(k);
                Element opt = optionOf(in);
                if (opt == null) continue;
                Element copy = opt.clone();
                copy.select(".let").remove();
                // The page puts each option in its own <span>: keep only what is inside it
                Element only = copy.children().size() == 1 && copy.ownText().isBlank() ? copy.child(0) : null;
                if (only != null && only.tagName().equals("span") && only.attributes().isEmpty()) copy = only;
                q.optionsHtml.add(Html.sanitize(copy.html()));
                if (in.hasAttr("data-c")) { marked++; correct = q.optionsHtml.size() - 1; }
            }
            if (q.optionsHtml.size() < 2) {
                d.skipped.add("Question " + label + ": fewer than two options could be read: " + shortText);
                continue;
            }
            if (q.optionsHtml.size() > 4) {
                d.notes.add("Question " + label + " had " + q.optionsHtml.size() + " options; only A to D were kept.");
                if (correct > 3) { correct = -1; marked = 0; }
                q.optionsHtml = q.optionsHtml.subList(0, 4);
            }
            if (marked == 1) {
                q.correctIndex = correct;
            } else {
                q.correctIndex = Math.max(0, correct);
                q.misprintNote = (marked == 0 ? "No correct answer was marked in the file" : "More than one option was marked correct in the file")
                        + UNSURE;
            }

            Element why = el.selectFirst(".why");
            if (why != null) {
                Element w = why.clone();
                w.select("strong.pre").remove();
                Element first = w.children().first();
                if (first != null && first.tagName().equals("strong")
                        && first.text().matches("(?s)^\\s*(Correct|الإجابة|إجابة|الاجابة).*")) first.remove();
                q.explanationHtml = Html.sanitize(w.html());
            }
            if (q.explanationHtml.isBlank()) d.notes.add("Question " + label + " has no explanation.");

            String note = el.attr("data-note").trim();
            if (!note.isEmpty()) q.misprintNote = q.misprintNote.isEmpty() ? note : q.misprintNote + " " + note;
            q.optionsWrittenByClaude = el.attr("data-options").trim().equalsIgnoreCase("claude");
            d.questions.add(q);
        }
        return d;
    }

    /**
     * Moves every picture into the draft and points its img at it with src="img:N", so the editor stays readable.
     * A picture given as a file (e.g. src="q5.png" next to the HTML) is read and put inside the page.
     */
    private static void collectImages(Document doc, Path file, TestDraft d) {
        Map<String, String> seen = new HashMap<>();
        Path dir = file.toAbsolutePath().getParent();
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src").trim();
            String uri = Html.DATA_IMAGE.matcher(src).matches() ? src.replaceAll("\\s", "") : fromFile(dir, src);
            if (uri == null) {
                d.notes.add("A picture could not be read and was left out: " + (src.length() > 60 ? src.substring(0, 60) + "…" : src));
                continue;
            }
            String ref = seen.get(uri);
            if (ref == null) {
                ref = "img:" + (d.images.size() + 1);
                seen.put(uri, ref);
                d.images.put(ref, uri);
            }
            img.attr("src", ref);
        }
    }

    /** A picture file next to the test file as a data: URI, or null when it is not a local PNG/JPEG/GIF/WebP. */
    private static String fromFile(Path dir, String src) {
        if (dir == null || src.isEmpty() || src.matches("(?i)^[a-z][a-z0-9+.-]*:.*") || src.startsWith("//")) return null;
        String name = src.replaceAll("[?#].*$", "").replace("%20", " ");
        Matcher ext = Pattern.compile("(?i)\\.(png|jpe?g|gif|webp)$").matcher(name);
        if (!ext.find()) return null;
        Path p = dir.resolve(name).normalize();
        try {
            if (!Files.isRegularFile(p)) return null;
            String type = ext.group(1).toLowerCase(Locale.ROOT).replace("jpg", "jpeg");
            return "data:image/" + type + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(p));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The visible option next to a radio input: the .opt span in the same label. */
    private static Element optionOf(Element input) {
        Element label = input.closest("label");
        if (label != null) {
            Element opt = label.selectFirst(".opt");
            return opt != null ? opt : label;
        }
        Element next = input.nextElementSibling();
        return next;
    }

    private static String detectLang(Document doc) {
        Element html = doc.selectFirst("html");
        if (html != null) {
            if (html.attr("lang").toLowerCase().startsWith("ar") || html.attr("dir").equalsIgnoreCase("rtl")) return "ar";
            if (html.attr("lang").toLowerCase().startsWith("en")) return "en";
        }
        String text = doc.body().text();
        long arabic = text.codePoints().filter(c -> c >= 0x0600 && c <= 0x06FF).count();
        long latin = text.codePoints().filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).count();
        return arabic > latin ? "ar" : "en";
    }

    /** Grade written in the page's own sign-in line, e.g. Portal.requireLogin('6'), or "". */
    public static String gradeFromPage(Path file) {
        try {
            Matcher m = REQUIRE.matcher(Files.readString(file, StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
