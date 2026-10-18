package studio;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keeps only the maths markup the test pages use (English and Arabic), so nothing else can reach the public site:
 * no scripts, styles, links, images or event handlers.
 */
public final class Html {
    private static final String[] SVG = {"svg", "g", "circle", "ellipse", "line", "rect", "path", "polygon", "polyline", "text", "tspan"};
    private static final String[] SVG_ATTRS = {"class", "cx", "cy", "r", "rx", "ry", "x", "y", "x1", "y1", "x2", "y2", "dx", "dy",
            "width", "height", "d", "points", "fill", "stroke", "stroke-width", "stroke-dasharray", "transform",
            "text-anchor", "font-size", "font-weight", "viewbox", "role", "aria-label"};

    private static final Safelist SAFE = new Safelist()
            .addTags("b", "i", "em", "strong", "u", "sup", "sub", "small", "br", "span")
            .addAttributes("span", "class", "dir")
            .addAttributes("sup", "class")
            .addAttributes("sub", "class")
            .addTags(SVG);
    static {
        for (String t : SVG) SAFE.addAttributes(t, SVG_ATTRS);
    }

    /** Class names the page template has styles for. Anything else is dropped. */
    private static final Set<String> CLASSES = Set.of("frac", "blank", "circle", "rep", "ov", "nw", "m", "mb", "seq",
            "rad", "rt", "ri", "venn", "vl", "vm", "vr", "venn-svg", "lab");

    private Html() {}

    public static String sanitize(String in) {
        if (in == null || in.isBlank()) return "";
        Document clean = new Cleaner(SAFE).clean(Jsoup.parseBodyFragment(in));
        for (Element e : clean.body().getAllElements()) {
            if (e.hasAttr("class")) {
                List<String> keep = new ArrayList<>();
                for (String c : e.classNames()) if (CLASSES.contains(c)) keep.add(c);
                if (keep.isEmpty()) e.removeAttr("class");
                else e.attr("class", String.join(" ", keep));
            }
            if (e.hasAttr("dir") && !e.attr("dir").matches("ltr|rtl")) e.removeAttr("dir");
            for (String a : List.of("fill", "stroke", "transform")) {
                // keep plain colours and transforms only, never url(...) references
                String v = e.attr(a);
                if (e.hasAttr(a) && (!v.matches("[#\\w\\s.,()\\-%]*") || v.toLowerCase().contains("url"))) e.removeAttr(a);
            }
        }
        clean.outputSettings().prettyPrint(false);
        return clean.body().html().trim();
    }

    public static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Turns HTML maths into plain readable text for the review list. */
    public static String toPlain(String html) {
        if (html == null) return "";
        String s = html.replaceAll("<span class=\"frac\"><b>(.*?)</b><b>(.*?)</b></span>", "($1/$2)")
                .replaceAll("<sup[^>]*>(.*?)</sup>", "^$1")
                .replaceAll("<span class=\"blank\"></span>", " ____ ")
                .replaceAll("<span class=\"circle\"></span>", " ◯ ")
                .replaceAll("<svg.*?</svg>", " [diagram] ");
        return Jsoup.parseBodyFragment(s).text();
    }
}
