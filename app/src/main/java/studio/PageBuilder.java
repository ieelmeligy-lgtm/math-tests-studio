package studio;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Writes a test page in the portal's format, in English or Arabic. The page has no answer key: grading happens in the Apps Script. */
public final class PageBuilder {
    private PageBuilder() {}

    private static final Map<String, String> EN = new LinkedHashMap<>();
    private static final Map<String, String> AR = new LinkedHashMap<>();
    static {
        EN.put("myTests", "My tests");
        EN.put("notice", "You are viewing this test in preview mode. Open it online and sign in to submit your answers and see your score.");
        EN.put("print", "Print / save result");
        EN.put("again", "Try again");
        EN.put("submit", "Submit my answers");
        EN.put("footer", "Choose the correct answer for each question, then press Submit.");
        EN.put("answered", "{n} of {N} answered");
        EN.put("left1", "You still have 1 question to answer.");
        EN.put("leftN", "You still have {n} questions to answer.");
        EN.put("checking", "Checking your answers…");
        EN.put("expired", "Your sign-in has expired. Please sign in again.");
        EN.put("failed", "Your answers could not be checked. Check your internet and press Submit again.");
        EN.put("correct", "Correct. ");
        EN.put("correctAnswer", "Correct answer: {L}. ");
        EN.put("saved", "Your result is saved.");
        EN.put("signedIn", "Signed in as {name}");
        EN.put("note100", "Excellent! Full marks.");
        EN.put("note80", "Very good work. Check the questions marked in red.");
        EN.put("note50", "Good try. Read the explanations and try again.");
        EN.put("note0", "Keep practising. Read each explanation below, then try again.");

        AR.put("myTests", "اختباراتي");
        AR.put("notice", "أنت تشاهد الاختبار في وضع المعاينة. افتحه عبر الإنترنت وسجّل الدخول لإرسال إجاباتك ومعرفة نتيجتك.");
        AR.put("print", "طباعة / حفظ النتيجة");
        AR.put("again", "حاول مرة أخرى");
        AR.put("submit", "أرسل إجاباتي");
        AR.put("footer", "اختر الإجابة الصحيحة لكل سؤال، ثم اضغط «أرسل إجاباتي».");
        AR.put("answered", "تمت الإجابة عن {n} من {N}");
        AR.put("left1", "بقي سؤال واحد بدون إجابة.");
        AR.put("leftN", "بقي {n} من الأسئلة بدون إجابة.");
        AR.put("checking", "جارٍ تصحيح إجاباتك…");
        AR.put("expired", "انتهت مدة تسجيل الدخول. يرجى تسجيل الدخول مرة أخرى.");
        AR.put("failed", "تعذّر تصحيح إجاباتك. تحقق من الإنترنت واضغط «أرسل إجاباتي» مرة أخرى.");
        AR.put("correct", "إجابة صحيحة. ");
        AR.put("correctAnswer", "الإجابة الصحيحة: {L}. ");
        AR.put("saved", "تم حفظ نتيجتك.");
        AR.put("signedIn", "تم تسجيل الدخول باسم {name}");
        AR.put("note100", "ممتاز! الدرجة كاملة.");
        AR.put("note80", "عمل جيد جدًا. راجع الأسئلة المحددة باللون الأحمر.");
        AR.put("note50", "محاولة جيدة. اقرأ الشرح وحاول مرة أخرى.");
        AR.put("note0", "استمر في التدريب. اقرأ شرح كل سؤال ثم حاول مرة أخرى.");
    }
    private static final List<String> LETTERS_EN = List.of("A", "B", "C", "D");
    private static final List<String> LETTERS_AR = List.of("أ", "ب", "ج", "د");
    private static final Map<String, String> AR_GRADES = Map.of("1", "الأول", "2", "الثاني", "3", "الثالث", "4", "الرابع",
            "5", "الخامس", "6", "السادس", "7", "السابع", "8", "الثامن", "9", "التاسع");

    public static String slug(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.isEmpty()) s = "test-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        return s.length() > 50 ? s.substring(0, 50).replaceAll("-$", "") : s;
    }

    /** A safe file name for the site, keeping the teacher's own name when it is already safe. */
    public static String safeFileName(String fileName) {
        String base = fileName.replaceAll("(?i)\\.html?$", "");
        return (base.matches("[\\w-]+") ? base : slug(base)) + ".html";
    }

    /** Stable Test ID from the grade and file name, e.g. g6-comparing-rational-numbers, so re-publishing updates the same test. */
    public static String testId(String grade, String fileName) {
        return "g" + grade + "-" + fileName.replaceAll("(?i)\\.html$", "").toLowerCase(Locale.ROOT);
    }

    public static String build(TestDraft d, String grade, String title, String testId) throws IOException {
        String tpl;
        try (InputStream in = PageBuilder.class.getResourceAsStream("/test-template.html")) {
            if (in == null) throw new IOException("Page template missing from the app.");
            tpl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean ar = d.arabic();
        Map<String, String> t = ar ? AR : EN;
        List<String> letters = ar ? LETTERS_AR : LETTERS_EN;
        int n = d.questions.size();

        StringBuilder qs = new StringBuilder();
        String section = "";
        for (int i = 0; i < n; i++) {
            TestDraft.Question q = d.questions.get(i);
            String sec = Html.sanitize(q.sectionHtml);
            if (!sec.isEmpty() && !sec.equals(section)) qs.append("  <h2 class=\"sec\">").append(sec).append("</h2>\n");
            section = sec;
            qs.append("  <div class=\"card\" id=\"q").append(i).append("\"><div class=\"q-head\"><div class=\"num\">")
              .append(i + 1).append("</div><div class=\"stem\">").append(Html.sanitize(q.stemHtml)).append("</div></div>\n");
            qs.append("   <div class=\"opts\" role=\"radiogroup\" aria-label=\"").append(ar ? "السؤال " : "Question ").append(i + 1).append("\">\n");
            for (int k = 0; k < q.optionsHtml.size(); k++) {
                qs.append("    <label class=\"ch\"><input type=\"radio\" name=\"q").append(i).append("\" value=\"").append(k)
                  .append("\"><span class=\"opt\"><span class=\"let\">").append(letters.get(k)).append("</span><span>")
                  .append(Html.sanitize(q.optionsHtml.get(k))).append("</span></span></label>\n");
            }
            qs.append("   </div>\n   <div class=\"why\"></div></div>\n");
        }

        String gradeWord = ar ? AR_GRADES.getOrDefault(grade, grade) : grade;
        String pageTitle = ar ? "اختبار رياضيات الصف " + gradeWord + " – " + title : "Grade " + grade + " Math Test – " + title;
        String subtitle = ar ? "رياضيات الصف " + gradeWord + " · عدد الأسئلة: " + n : "Grade " + grade + " Math · " + n + " multiple choice questions";
        String testTitle = (ar ? "الصف " + gradeWord : "Grade " + grade) + " - " + title;

        Map<String, Object> js = new LinkedHashMap<>(t);
        js.put("letters", letters);
        String json;
        try {
            json = Backend.JSON.writeValueAsString(js).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            throw new IOException(e);
        }
        return tpl.replace("{{LANG}}", ar ? "ar" : "en")
                .replace("{{DIR}}", ar ? "rtl" : "ltr")
                .replace("{{PAGE_TITLE}}", Html.escape(pageTitle))
                .replace("{{HEADER_TITLE}}", Html.escape(title))
                .replace("{{SUBTITLE}}", Html.escape(subtitle))
                .replace("{{T_MY_TESTS}}", Html.escape(t.get("myTests")))
                .replace("{{T_NOTICE}}", Html.escape(t.get("notice")))
                .replace("{{T_PRINT}}", Html.escape(t.get("print")))
                .replace("{{T_AGAIN}}", Html.escape(t.get("again")))
                .replace("{{T_SUBMIT}}", Html.escape(t.get("submit")))
                .replace("{{T_FOOTER}}", Html.escape(t.get("footer")))
                .replace("{{T_ANSWERED_0}}", Html.escape(t.get("answered").replace("{n}", "0").replace("{N}", String.valueOf(n))))
                .replace("{{TEST_TITLE_JS}}", testTitle.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\x3c"))
                .replace("{{TEST_ID}}", testId)
                .replace("{{GRADE}}", grade)
                .replace("{{T_JSON}}", json)
                .replace("{{QUESTIONS}}", qs.toString());
    }
}
