package studio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Step 3b: the teacher checks every question, the answer key and Claude's warnings, edits, previews, approves. */
public final class ReviewDialog extends JDialog {
    private final TestDraft draft;
    private final String grade;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);
    private final JTextArea stem = area(3);
    private final JTextField[] opts = new JTextField[4];
    private final JRadioButton[] correct = new JRadioButton[4];
    private final ButtonGroup pickGroup = new ButtonGroup();
    private final JTextArea why = area(3);
    private final JLabel flag = new JLabel(" ");
    private final JLabel key = new JLabel();
    private final JTextField title = new JTextField(30);
    private int current = -1;
    private boolean approved;

    public ReviewDialog(Frame owner, TestDraft draft, String grade, String suggestedTitle) {
        super(owner, "Step 3 – Review the test", true);
        this.draft = draft;
        this.grade = grade;
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.CREAM);
        root.add(Theme.header("✨  Review the test", "Grade " + grade + " · " + (draft.arabic() ? "Arabic" : "English") + " · "
                + draft.questions.size() + " questions · nothing is published until you approve"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(14, 16, 6, 16));

        // Title, answer key and Claude's warnings
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.setOpaque(false);
        titleRow.add(Theme.sectionLabel("Test title"), BorderLayout.WEST);
        title.setText(suggestedTitle);
        title.setFont(Theme.font(Font.BOLD, 15f));
        titleRow.add(title, BorderLayout.CENTER);
        top.add(titleRow, BorderLayout.NORTH);
        key.setFont(Theme.font(Font.BOLD, 14f));
        key.setForeground(Theme.TURQ_DEEP);
        top.add(key, BorderLayout.CENTER);
        String warnings = warnings();
        if (!warnings.isEmpty()) {
            JTextArea w = new JTextArea(warnings);
            w.setEditable(false);
            w.setLineWrap(true);
            w.setWrapStyleWord(true);
            w.setFont(Theme.font(Font.PLAIN, 13f));
            w.setBackground(new Color(0xfff8e6));
            w.setForeground(Theme.COFFEE);
            w.setBorder(new EmptyBorder(8, 10, 8, 10));
            JScrollPane ws = new JScrollPane(w);
            ws.setBorder(new LineBorder(new Color(0xf2d58a), 1, true));
            ws.setPreferredSize(new Dimension(100, 90));
            top.add(ws, BorderLayout.SOUTH);
        }
        body.add(top, BorderLayout.NORTH);

        // Question list
        list.setFont(Theme.font(Font.PLAIN, 14f));
        list.setFixedCellHeight(34);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) show(list.getSelectedIndex()); });
        JScrollPane ls = new JScrollPane(list);
        ls.setPreferredSize(new Dimension(300, 400));
        ls.setBorder(new LineBorder(Theme.LINE, 1, true));
        body.add(ls, BorderLayout.WEST);

        // Editor
        JPanel ed = Theme.card();
        ed.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(3, 2, 3, 2);
        int y = 0;
        c.gridy = y++; ed.add(Theme.sectionLabel("Question (HTML maths allowed)"), c);
        c.gridy = y++; ed.add(new JScrollPane(stem), c);
        c.gridy = y++; ed.add(Theme.sectionLabel("Options – tick the correct one"), c);
        ButtonGroup g = pickGroup;
        String[] arLetters = {"أ", "ب", "ج", "د"};
        for (int i = 0; i < 4; i++) {
            correct[i] = new JRadioButton((char) ('A' + i) + (draft.arabic() ? "  " + arLetters[i] : ""));
            correct[i].setFont(Theme.font(Font.BOLD, 15f));
            correct[i].setOpaque(false);
            g.add(correct[i]);
            opts[i] = new JTextField();
            opts[i].setFont(Theme.font(Font.PLAIN, 14f));
            opts[i].putClientProperty("JTextField.placeholderText", i < 2 ? "required" : "optional");
            c.gridy = y; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
            ed.add(correct[i], c);
            c.gridx = 1; c.weightx = 1;
            ed.add(opts[i], c);
            y++;
        }
        c.gridx = 0; c.gridwidth = 2;
        c.gridy = y++; ed.add(Theme.sectionLabel("Explanation shown after submitting"), c);
        c.gridy = y++; ed.add(new JScrollPane(why), c);
        flag.setFont(Theme.font(Font.BOLD, 13f));
        flag.setForeground(new Color(0xa86b00));
        c.gridy = y++; ed.add(flag, c);
        JButton remove = Theme.softButton("🗑  Remove this question");
        remove.addActionListener(e -> removeCurrent());
        c.gridy = y; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.WEST; c.weighty = 1;
        ed.add(remove, c);
        body.add(ed, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        if (draft.arabic()) {
            ComponentOrientation rtl = ComponentOrientation.RIGHT_TO_LEFT;
            stem.setComponentOrientation(rtl);
            why.setComponentOrientation(rtl);
            title.setComponentOrientation(rtl);
            list.setComponentOrientation(rtl);
            for (JTextField o : opts) o.setComponentOrientation(rtl);
        }

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setOpaque(false);
        JButton preview = Theme.softButton("👁  Preview in browser");
        JButton back = Theme.softButton("← Back");
        JButton ok = Theme.primaryButton("Approve ✓");
        preview.addActionListener(e -> preview());
        back.addActionListener(e -> dispose());
        ok.addActionListener(e -> approve());
        buttons.add(preview);
        buttons.add(back);
        buttons.add(ok);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(Theme.credit(), BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        refreshList();
        if (!draft.questions.isEmpty()) list.setSelectedIndex(0);
        setContentPane(root);
        setSize(1100, 760);
        setLocationRelativeTo(owner);
    }

    private static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 40);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(Theme.font(Font.PLAIN, 14f));
        return a;
    }

    private String warnings() {
        StringBuilder sb = new StringBuilder();
        for (String s : draft.skipped) sb.append("⚠ Left out: ").append(s).append('\n');
        for (int i = 0; i < draft.questions.size(); i++) {
            TestDraft.Question q = draft.questions.get(i);
            if (!q.misprintNote.isBlank()) sb.append("⚠ Q").append(i + 1).append(" – check this: ").append(q.misprintNote).append('\n');
            if (q.optionsWrittenByClaude) sb.append("✎ Q").append(i + 1).append(": the options were written by Claude, not the book.\n");
        }
        for (String s : draft.notes) sb.append("ℹ ").append(s).append('\n');
        return sb.toString().trim();
    }

    private String label(int i) {
        TestDraft.Question q = draft.questions.get(i);
        String mark = !q.misprintNote.isBlank() ? "⚠ " : q.optionsWrittenByClaude ? "✎ " : "";
        String plain = Html.toPlain(q.stemHtml);
        return (i + 1) + ".  " + mark + (plain.length() > 40 ? plain.substring(0, 40) + "…" : plain)
                + "   [" + (char) ('A' + q.correctIndex) + "]";
    }

    private void refreshList() {
        updating = true;
        listModel.clear();
        for (int i = 0; i < draft.questions.size(); i++) listModel.addElement(label(i));
        updating = false;
        key.setText("Answer key:  " + draft.answerKey());
    }

    private boolean updating;

    private void show(int i) {
        if (updating) return;
        save();
        current = i;
        if (i < 0 || i >= draft.questions.size()) return;
        TestDraft.Question q = draft.questions.get(i);
        stem.setText(q.stemHtml);
        pickGroup.clearSelection();
        boolean unsure = q.misprintNote.endsWith(TestImporter.UNSURE);
        for (int k = 0; k < 4; k++) {
            opts[k].setText(k < q.optionsHtml.size() ? q.optionsHtml.get(k) : "");
            if (!unsure && k == q.correctIndex) correct[k].setSelected(true);
        }
        why.setText(q.explanationHtml);
        String f = (!q.misprintNote.isBlank() ? "⚠ " + q.misprintNote + "   " : "")
                + (q.optionsWrittenByClaude ? "✎ Options written by Claude   " : "")
                + (q.sectionHtml.isBlank() ? "" : "Part: " + Html.toPlain(q.sectionHtml));
        flag.setText(f.isEmpty() ? " " : f);
    }

    /** Copies the editor back into the draft. Returns an error message, or null. */
    private String save() {
        if (current < 0 || current >= draft.questions.size()) return null;
        TestDraft.Question q = draft.questions.get(current);
        q.stemHtml = Html.sanitize(stem.getText().trim());
        List<String> o = new ArrayList<>();
        int pick = -1;
        for (int k = 0; k < 4; k++) {
            String t = opts[k].getText().trim();
            if (t.isEmpty()) continue;
            if (correct[k].isSelected()) pick = o.size();
            o.add(Html.sanitize(t));
        }
        q.optionsHtml = o;
        if (pick >= 0) {
            q.correctIndex = pick;
            if (q.misprintNote.endsWith(TestImporter.UNSURE)) q.misprintNote = "";   // the teacher has chosen it now
        }
        q.explanationHtml = Html.sanitize(why.getText().trim());
        int idx = current;
        updating = true;
        listModel.set(idx, label(idx));
        updating = false;
        key.setText("Answer key:  " + draft.answerKey());
        if (q.stemHtml.isEmpty()) return "Question " + (idx + 1) + " has no text.";
        if (o.size() < 2) return "Question " + (idx + 1) + " needs at least two options.";
        if (pick < 0) return "Question " + (idx + 1) + ": tick the correct option.";
        return null;
    }

    private void removeCurrent() {
        if (current < 0) return;
        if (JOptionPane.showConfirmDialog(this, "Remove question " + (current + 1) + "?", "Remove", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        draft.questions.remove(current);
        current = -1;
        refreshList();
        if (!draft.questions.isEmpty()) list.setSelectedIndex(0);
    }

    private String validateAll() {
        String err = save();
        if (err != null) return err;
        if (title.getText().isBlank()) return "Give the test a title.";
        if (draft.questions.isEmpty()) return "The test has no questions.";
        for (int i = 0; i < draft.questions.size(); i++) {
            TestDraft.Question q = draft.questions.get(i);
            if (q.optionsHtml.size() < 2 || q.correctIndex >= q.optionsHtml.size()) return "Check the options of question " + (i + 1) + ".";
            if (q.misprintNote.endsWith(TestImporter.UNSURE)) return "Question " + (i + 1) + ": tick the correct option.";
        }
        return null;
    }

    private void preview() {
        String err = validateAll();
        if (err != null) { JOptionPane.showMessageDialog(this, err); return; }
        try {
            // Private preview on this PC: a fake session, and Submit grades here so the answers and explanations can be checked.
            // Links back to the site will not work.
            List<Integer> key = new ArrayList<>();
            List<String> expl = new ArrayList<>();
            for (TestDraft.Question q : draft.questions) { key.add(q.correctIndex); expl.add(Html.sanitize(q.explanationHtml)); }
            String data = Backend.JSON.writeValueAsString(List.of(key, expl)).replace("<", "\\u003c");
            String stub = "<script>var Portal={requireLogin:function(){return {name:'Preview'};},submitTest:function(d,cb){"
                    + "var k=" + data + ",c=k[0],s=0,i;for(i=0;i<c.length;i++){if(d.picks[i]===c[i])s++;}"
                    + "cb({ok:true,saved:false,score:s,total:c.length,percent:Math.round(s/c.length*100),correct:c,explanations:k[1]});}};</script>";
            String html = PageBuilder.build(draft, grade, title.getText().trim(), "preview")
                    .replace("<script src=\"../config.js\"></script>", "")
                    .replace("<script src=\"../portal.js\"></script>", stub);
            File f = File.createTempFile("math-test-preview-", ".html");
            f.deleteOnExit();
            Files.writeString(f.toPath(), html, StandardCharsets.UTF_8);
            Desktop.getDesktop().browse(f.toURI());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open the preview: " + ex.getMessage());
        }
    }

    private void approve() {
        String err = validateAll();
        if (err != null) { JOptionPane.showMessageDialog(this, err, "Please check", JOptionPane.WARNING_MESSAGE); return; }
        approved = true;
        dispose();
    }

    public boolean approved() { return approved; }
    public String title() { return title.getText().trim(); }
}
