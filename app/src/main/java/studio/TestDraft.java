package studio;

import java.util.ArrayList;
import java.util.List;

/** A test read from an HTML file made in Claude. Also the editable draft in the review window. */
public class TestDraft {

    public static class Question {
        /** Question number as printed in the file. */
        public int number;
        /** Heading of the part this question belongs to, e.g. "Page 2 · Choose the correct answer". May be empty. */
        public String sectionHtml = "";
        public String stemHtml = "";
        /** 2 to 4 options, in order A, B, C, D. */
        public List<String> optionsHtml = new ArrayList<>();
        /** 0 = A, 1 = B, 2 = C, 3 = D */
        public int correctIndex;
        public String explanationHtml = "";
        /** True when the file says the options were written by Claude, not the book. */
        public boolean optionsWrittenByClaude;
        /** Empty, or a note the teacher should check (misprint fixed, answer missing in the file, ...). */
        public String misprintNote = "";
    }

    /** "en" or "ar". Sets the page direction, the labels and the option letters. */
    public String lang = "en";
    public String suggestedTitle = "";
    public List<Question> questions = new ArrayList<>();
    /** Questions left out, each with the reason. */
    public List<String> skipped = new ArrayList<>();
    /** Anything else the teacher should check. */
    public List<String> notes = new ArrayList<>();

    public boolean arabic() { return "ar".equals(lang); }

    /** Answer key as stored in the Sheet: always A to D, e.g. "1-B, 2-A". */
    public String answerKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(i + 1).append('-').append((char) ('A' + questions.get(i).correctIndex));
        }
        return sb.toString();
    }
}
