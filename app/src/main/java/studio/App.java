package studio;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/** Math Tests Studio – courtesy of Miss Sarah Awad, Math Teacher. */
public final class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("--import-check")) {
            System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
            importCheck(args[1], args[2], args[3]);
            return;
        }
        FlatLightLaf.setup();
        UIManager.put("Button.arc", 16);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Component.focusColor", Theme.TURQ);
        UIManager.put("ProgressBar.foreground", Theme.TURQ);
        UIManager.put("ToggleButton.selectedBackground", Theme.TURQ_DEEP);
        UIManager.put("ToggleButton.selectedForeground", Color.WHITE);
        UIManager.put("defaultFont", Theme.font(Font.PLAIN, 14f));
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }

    /** Developer check: imports a test file made in Claude and writes the public page, without the GUI. */
    static void importCheck(String in, String grade, String out) throws Exception {
        java.nio.file.Path src = java.nio.file.Path.of(in);
        TestDraft d = TestImporter.read(src);
        String name = PageBuilder.safeFileName(src.getFileName().toString());
        String html = PageBuilder.build(d, grade, d.suggestedTitle, PageBuilder.testId(grade, name));
        java.nio.file.Files.writeString(java.nio.file.Path.of(out), html, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("kind=" + TestImporter.kind(src) + " lang=" + d.lang + " questions=" + d.questions.size()
                + " skipped=" + d.skipped.size() + " notes=" + d.notes.size() + " answersInPage=" + AnswerGuard.hasAnswers(html)
                + " file=" + name + " id=" + PageBuilder.testId(grade, name));
        System.out.println("title=" + d.suggestedTitle);
        System.out.println("key=" + d.answerKey());
        for (String s : d.skipped) System.out.println("skipped: " + s);
        for (String s : d.notes) System.out.println("note: " + s);
        for (TestDraft.Question q : d.questions) if (!q.misprintNote.isEmpty()) System.out.println("flag Q" + q.number + ": " + q.misprintNote);
    }
}
