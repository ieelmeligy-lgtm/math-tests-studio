package studio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The answer key must never reach the public site. A file made in Claude marks the correct option with data-c
 * and fills the .why explanations; a page built by the app has neither. The same check runs as a git pre-commit
 * hook (.githooks/pre-commit), so a commit made outside the app is stopped too.
 */
public final class AnswerGuard {
    private static final Pattern MARKED = Pattern.compile("(?i)<input\\b[^>]*\\bdata-c\\b");
    private static final Pattern EXPLAINED = Pattern.compile("class=\"why\"\\s*>\\s*(?!</div>)\\S");

    private AnswerGuard() {}

    public static boolean hasAnswers(String html) {
        return MARKED.matcher(html).find() || EXPLAINED.matcher(html).find();
    }

    public static boolean hasAnswers(Path file) throws IOException {
        return hasAnswers(Files.readString(file, StandardCharsets.UTF_8));
    }
}
