package studio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Step 2: pick a test file made in Claude. The app lists the new files it finds in docs/GradeN/,
 * or the teacher can browse to a file anywhere (for example Downloads).
 */
public final class ImportDialog extends JDialog {
    /** What the rest of the app needs to publish an imported test. */
    public record Imported(Path source, String grade, String fileName, TestDraft draft) {
        /** Path inside docs/, e.g. Grade6/comparing-rational-numbers.html (as listed in the Tests tab). */
        public String rel() { return "Grade" + grade + "/" + fileName; }
    }

    static final String CLAUDE_PROMPT = """
            Use the math-tests-portal skill. Make a self-grading multiple-choice test page from the attached photos \
            for Grade <N>, in <English / Arabic>. Keep the book's wording, mark the correct option of each question \
            with data-c, and write a short explanation in each .why. On a question card where you fixed a misprint \
            add data-note="what you changed", and where you wrote the options yourself add data-options="claude". \
            Give me the HTML file to download, and the answer key.""";

    private record Row(Path file, String grade, TestImporter.Kind kind, String lang, int questions) {}

    private final Path docs;
    private final List<Row> rows = new ArrayList<>();
    private final RowModel model = new RowModel();
    private final JTable table = new JTable(model);
    private final ButtonGroup gradeGroup = new ButtonGroup();
    private final List<JToggleButton> gradeButtons = new ArrayList<>();
    private final JLabel chosen = new JLabel("Choose a file from the list, or browse for one.");
    private Path picked;
    private String grade;
    private Imported result;

    public ImportDialog(Frame owner, Path repo, List<String> grades) {
        super(owner, "Step 2 – Import a test", true);
        this.docs = repo.resolve("docs");
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.CREAM);
        root.add(Theme.header("import", "Import a test","HTML files you downloaded from Claude into docs\\GradeN – English or Arabic"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 16, 8, 16));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        JButton refresh = Theme.softButton("Look again", "refresh");
        JButton openFolder = Theme.softButton("Open grade folders", "folder");
        JButton browse = Theme.primaryButton("Browse for a file…", "file");
        JButton prompt = Theme.softButton("Copy the request for Claude", "clipboard");
        refresh.addActionListener(e -> scan());
        openFolder.addActionListener(e -> { try { Desktop.getDesktop().open(docs.toFile()); } catch (Exception ignored) {} });
        browse.addActionListener(e -> browse());
        prompt.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(CLAUDE_PROMPT), null);
            JOptionPane.showMessageDialog(this, "Copied. Paste it in Claude with your photos, and fill in the grade and language.",
                    "Request for Claude", JOptionPane.INFORMATION_MESSAGE);
        });
        tools.add(browse);
        tools.add(refresh);
        tools.add(openFolder);
        tools.add(prompt);
        body.add(tools, BorderLayout.NORTH);

        table.setRowHeight(32);
        table.setFont(Theme.font(Font.PLAIN, 14f));
        table.getTableHeader().setFont(Theme.font(Font.BOLD, 13f));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                JLabel comp = (JLabel) super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                TestImporter.Kind k = rows.get(r).kind();
                if (!sel) comp.setForeground(k == TestImporter.Kind.NEW ? Theme.COFFEE : Theme.GREY);
                comp.setIcon(c != 4 ? null : Icons.get(switch (k) { case NEW -> "import"; case PUBLISHED -> "ok"; case UNKNOWN -> "idle"; }, 18));
                return comp;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
            Row r = rows.get(table.getSelectedRow());
            choose(r.file(), r.grade());
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) next(); }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(820, 300));
        sp.setBorder(new LineBorder(Theme.LINE, 1, true));
        body.add(sp, BorderLayout.CENTER);

        JPanel details = Theme.card();
        details.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        details.add(Theme.sectionLabel("File"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        chosen.setFont(Theme.font(Font.PLAIN, 13f));
        chosen.setForeground(Theme.COFFEE_MID);
        details.add(chosen, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        details.add(Theme.sectionLabel("Grade"), c);
        JPanel gradeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        gradeRow.setOpaque(false);
        for (String g : grades) {
            JToggleButton b = new JToggleButton(g);
            b.setFont(Theme.font(Font.BOLD, 18f));
            b.setPreferredSize(new Dimension(56, 48));
            b.addActionListener(e -> grade = g);
            gradeGroup.add(b);
            gradeButtons.add(b);
            gradeRow.add(b);
        }
        c.gridx = 1;
        details.add(gradeRow, c);
        body.add(details, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.setOpaque(false);
        JButton back = Theme.softButton("← Back");
        JButton next = Theme.primaryButton("Import this test →");
        back.addActionListener(e -> dispose());
        next.addActionListener(e -> next());
        buttons.add(back);
        buttons.add(next);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(Theme.credit(), BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
        scan();
    }

    /** New test files waiting in docs/GradeN (made in Claude, not yet published by the app). */
    public static List<Path> waiting(Path repo) {
        List<Path> out = new ArrayList<>();
        for (Path f : gradeFiles(repo.resolve("docs"))) if (TestImporter.kind(f) == TestImporter.Kind.NEW) out.add(f);
        return out;
    }

    private static List<Path> gradeFiles(Path docs) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(docs)) return out;
        try (Stream<Path> dirs = Files.list(docs)) {
            for (Path d : dirs.filter(Files::isDirectory).filter(p -> p.getFileName().toString().matches("(?i)grade\\d+")).sorted().toList()) {
                try (Stream<Path> fs = Files.list(d)) {
                    fs.filter(p -> p.getFileName().toString().toLowerCase().matches(".*\\.html?$")).sorted().forEach(out::add);
                }
            }
        } catch (IOException ignored) {}
        return out;
    }

    private void scan() {
        rows.clear();
        for (Path f : gradeFiles(docs)) {
            TestImporter.Kind k = TestImporter.kind(f);
            String lang = "", g = TestImporter.gradeFromFolder(f);
            int n = 0;
            if (k == TestImporter.Kind.NEW) {
                try {
                    TestDraft d = TestImporter.read(f);
                    lang = d.arabic() ? "Arabic" : "English";
                    n = d.questions.size();
                } catch (IOException ex) {
                    k = TestImporter.Kind.UNKNOWN;
                }
            }
            rows.add(new Row(f, g, k, lang, n));
        }
        // New files first, so the one to import is at the top
        rows.sort((a, b) -> Boolean.compare(b.kind() == TestImporter.Kind.NEW, a.kind() == TestImporter.Kind.NEW));
        model.fireTableDataChanged();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == TestImporter.Kind.NEW) { table.setRowSelectionInterval(i, i); break; }
        }
    }

    private void browse() {
        JFileChooser fc = new JFileChooser(docs.toFile());
        fc.setDialogTitle("Test file made in Claude (.html)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Test pages (*.html)", "html", "htm"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path f = fc.getSelectedFile().toPath();
        String g = TestImporter.gradeFromFolder(f);
        if (g.isEmpty()) g = TestImporter.gradeFromPage(f);
        table.clearSelection();
        choose(f, g);
    }

    private void choose(Path f, String g) {
        picked = f;
        chosen.setText(f.toString());
        grade = null;
        gradeGroup.clearSelection();
        for (JToggleButton b : gradeButtons) if (b.getText().equals(g)) { b.setSelected(true); grade = g; }
    }

    private void next() {
        if (picked == null) { warn("Choose a test file first."); return; }
        if (grade == null) { warn("Choose the grade."); return; }
        TestImporter.Kind kind = TestImporter.kind(picked);
        Path source = picked;
        if (kind == TestImporter.Kind.PUBLISHED) {
            // Already published: the page has no answers left, but the app kept the original file.
            Path backup = Settings.originalsDir().resolve("Grade" + grade).resolve(picked.getFileName().toString());
            if (!Files.exists(backup)) {
                warn("This page is already published and has no answers inside.\nTo change it, download the file from Claude again into the grade folder.");
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "This test is already published.\nOpen the copy with the answers that the app kept, to edit and publish it again?",
                    "Already published", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            source = backup;
        } else if (kind == TestImporter.Kind.UNKNOWN) {
            warn("This file is not a test page from Claude: no correct answers are marked in it (data-c).\n"
                    + "Ask Claude again with the request from \"Copy the request for Claude\".");
            return;
        }
        TestDraft d;
        try {
            d = TestImporter.read(source);
        } catch (IOException ex) {
            warn("Could not read the file: " + ex.getMessage());
            return;
        }
        if (d.questions.isEmpty()) {
            warn("No multiple-choice questions were found in this file.\n" + String.join("\n", d.skipped));
            return;
        }
        String name = PageBuilder.safeFileName(picked.getFileName().toString());
        if (!name.equals(picked.getFileName().toString()))
            JOptionPane.showMessageDialog(this, "The file name has spaces or letters a web address cannot use.\nIt will be published as " + name + ".",
                    "File name", JOptionPane.INFORMATION_MESSAGE);
        result = new Imported(source, grade, name, d);
        dispose();
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Import", JOptionPane.INFORMATION_MESSAGE);
    }

    public Imported result() { return result; }

    private final class RowModel extends AbstractTableModel {
        private final String[] cols = {"Grade", "File", "Language", "Questions", "Status"};
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.grade();
                case 1 -> row.file().getFileName().toString();
                case 2 -> row.lang();
                case 3 -> row.questions() > 0 ? String.valueOf(row.questions()) : "";
                default -> switch (row.kind()) {
                    case NEW -> "New – ready to import";
                    case PUBLISHED -> "Published";
                    case UNKNOWN -> "Not a test from Claude";
                };
            };
        }
    }
}
