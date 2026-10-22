// Java port of justext/core.py
/*
 * Copyright (c) 2011 Jan Pomikalek
 *
 * This software is licensed as described in the file LICENSE.rst.
 */
package io.github.danielonet.justext.justext;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.XmlDeclaration;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

public final class Core {

    public static final double MAX_LINK_DENSITY_DEFAULT = 0.2;
    public static final int LENGTH_LOW_DEFAULT = 70;
    public static final int LENGTH_HIGH_DEFAULT = 200;
    public static final double STOPWORDS_LOW_DEFAULT = 0.30;
    public static final double STOPWORDS_HIGH_DEFAULT = 0.32;
    public static final boolean NO_HEADINGS_DEFAULT = false;
    // Short and near-good headings within MAX_HEADING_DISTANCE characters before
    // a good paragraph are classified as good unless --no-headings is specified.
    public static final int MAX_HEADING_DISTANCE_DEFAULT = 200;
    public static final Set<String> PARAGRAPH_TAGS = Set.of(
            "body", "blockquote", "caption", "center", "col", "colgroup", "dd", "div", "dl", "dt",
            "fieldset", "form", "legend", "optgroup", "option", "p", "pre", "table", "td", "textarea",
            "tfoot", "th", "thead", "tr", "ul", "li", "h1", "h2", "h3", "h4", "h5", "h6");
    public static final String DEFAULT_ENCODING = "utf8";
    public static final String DEFAULT_ENC_ERRORS = "replace";
    public static final Pattern CHARSET_META_TAG_PATTERN =
            Pattern.compile("<meta[^>]+charset=[\"']?([^'\"/>\\s]+)", Pattern.CASE_INSENSITIVE);
    public static final Set<String> GOOD_OR_BAD = Set.of("good", "bad");

    private Core() {
    }

    /** Base class for jusText exceptions. */
    public static class JustextError extends RuntimeException {
        public JustextError(String message) {
            super(message);
        }

        public JustextError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class JustextInvalidOptions extends JustextError {
        public JustextInvalidOptions(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ DOM

    /** Converts an already decoded HTML string to DOM. */
    public static Document htmlToDom(String html) {
        if (Utils.strip(html).isEmpty()) {
            // lxml refuses empty documents; keep that behaviour
            throw new JustextError("Document is empty");
        }
        Document dom = Jsoup.parse(html);
        dom.outputSettings().prettyPrint(false);
        return dom;
    }

    /** Converts raw HTML bytes to DOM, guessing the encoding if needed. */
    public static Document htmlToDom(byte[] html, String defaultEncoding, String encoding, String errors) {
        return htmlToDom(decodeHtml(html, defaultEncoding, encoding, errors));
    }

    public static Document htmlToDom(byte[] html) {
        return htmlToDom(html, DEFAULT_ENCODING, null, DEFAULT_ENC_ERRORS);
    }

    /** Text input is already decoded; returned as is. */
    public static String decodeHtml(String html) {
        return html;
    }

    public static String decodeHtml(byte[] html) {
        return decodeHtml(html, DEFAULT_ENCODING, null, DEFAULT_ENC_ERRORS);
    }

    /**
     * Converts bytes containing an HTML page into a string.
     * Tries to guess character encoding from meta tag.
     *
     * @param errors one of {@code strict}, {@code ignore}, {@code replace}
     */
    public static String decodeHtml(byte[] html, String defaultEncoding, String encoding, String errors) {
        if (encoding != null && !encoding.isEmpty()) {
            try {
                return decode(html, encoding, errors);
            } catch (CharacterCodingException e) {
                throw new JustextError("Unable to decode the HTML to Unicode: " + e, e);
            }
        }

        // bytes -> ISO-8859-1 maps each byte to one char, so the regex sees the raw bytes
        Matcher match = CHARSET_META_TAG_PATTERN.matcher(new String(html, StandardCharsets.ISO_8859_1));
        if (match.find()) {
            String declaredEncoding = match.group(1);
            if (isKnownCharset(declaredEncoding)) {
                try {
                    return decode(html, declaredEncoding, errors);
                } catch (CharacterCodingException e) {
                    throw new JustextError("Unable to decode the HTML to Unicode: " + e, e);
                }
            }
            // proceed unknown encoding as if it wasn't found at all
        }

        // unknown encoding
        try {
            // try UTF-8 first
            return decode(html, "utf8", "strict");
        } catch (CharacterCodingException ignored) {
            // try lucky with default encoding
            try {
                return decode(html, defaultEncoding, errors);
            } catch (CharacterCodingException | IllegalArgumentException e) {
                throw new JustextError("Unable to decode the HTML to Unicode: " + e, e);
            }
        }
    }

    private static boolean isKnownCharset(String name) {
        try {
            return Charset.isSupported(name);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String decode(byte[] bytes, String charsetName, String errors)
            throws CharacterCodingException {
        CodingErrorAction action = switch (errors == null ? "strict" : errors.toLowerCase(Locale.ROOT)) {
            case "ignore" -> CodingErrorAction.IGNORE;
            case "replace" -> CodingErrorAction.REPLACE;
            default -> CodingErrorAction.REPORT;
        };
        CharsetDecoder decoder = Charset.forName(charsetName).newDecoder()
                .onMalformedInput(action)
                .onUnmappableCharacter(action);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    /** Tags removed together with their content. */
    private static final Set<String> KILL_TAGS = Set.of(
            "head", "script", "style", "applet", "button", "input", "select", "textarea");
    /** Tags removed but whose content is kept. */
    private static final Set<String> REMOVE_TAGS = Set.of(
            "iframe", "embed", "layer", "object", "param", "form");

    /**
     * Removes unwanted parts of DOM: comments, scripts, styles, embedded objects,
     * forms and the {@code <head>}. Mirrors the lxml {@code Cleaner} setup used by
     * the Python version. Returns a cleaned copy; the input is left untouched.
     */
    public static Document preprocessor(Document dom) {
        Document cleaned = dom.clone();
        List<Node> toKill = new ArrayList<>();
        List<Element> toUnwrap = new ArrayList<>();

        NodeTraversor.traverse((node, depth) -> {
            if (node instanceof Comment || node instanceof XmlDeclaration) {
                toKill.add(node);
            } else if (node instanceof Element el) {
                String tag = el.normalName();
                if (KILL_TAGS.contains(tag)) {
                    toKill.add(el);
                } else if (REMOVE_TAGS.contains(tag)) {
                    toUnwrap.add(el);
                }
                el.removeAttr("style"); // inline styles
            }
        }, cleaned);

        for (Node node : toKill) {
            if (node.parent() != null) {
                node.remove();
            }
        }
        // innermost first so nested unwraps don't lose their parent
        for (int i = toUnwrap.size() - 1; i >= 0; i--) {
            Element el = toUnwrap.get(i);
            if (el.parent() != null) {
                el.unwrap();
            }
        }
        return cleaned;
    }

    // ------------------------------------------------------------ paragraphs

    /**
     * Converts an HTML page represented as a DOM object into a list
     * of paragraphs. Walks the tree the same way lxml's SAX events do.
     */
    public static class ParagraphMaker implements NodeVisitor {
        private final PathInfo path = new PathInfo();
        private final List<Paragraph> paragraphs = new ArrayList<>();
        private Paragraph paragraph = null;
        private boolean link = false;
        private boolean br = false;

        /** Converts DOM into paragraphs. */
        public static List<Paragraph> makeParagraphs(Node root) {
            if (root instanceof Document doc) {
                Element html = doc.selectFirst("html");
                root = html != null ? html : doc;
            }
            ParagraphMaker handler = new ParagraphMaker();
            NodeTraversor.traverse(handler, root);
            handler.endDocument();
            return handler.paragraphs;
        }

        public ParagraphMaker() {
            startNewParagraph();
        }

        private void startNewParagraph() {
            if (paragraph != null && paragraph.containsText()) {
                paragraphs.add(paragraph);
            }
            paragraph = new Paragraph(path);
        }

        @Override
        public void head(Node node, int depth) {
            if (node instanceof Element el) {
                startElement(el.normalName());
            } else if (node instanceof TextNode text) {
                characters(text.getWholeText());
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (node instanceof Element el) {
                endElement(el.normalName());
            }
        }

        public void startElement(String name) {
            path.append(name);

            if (PARAGRAPH_TAGS.contains(name) || ("br".equals(name) && br)) {
                if ("br".equals(name)) {
                    // the <br><br> is a paragraph separator and should
                    // not be included in the number of tags within the
                    // paragraph
                    paragraph.tagsCount -= 1;
                }
                startNewParagraph();
            } else {
                br = "br".equals(name);
                if (br) {
                    paragraph.appendText(" ");
                } else if ("a".equals(name)) {
                    link = true;
                }
                paragraph.tagsCount += 1;
            }
        }

        public void endElement(String name) {
            path.pop();

            if (PARAGRAPH_TAGS.contains(name)) {
                startNewParagraph();
            }
            if ("a".equals(name)) {
                link = false;
            }
        }

        public void endDocument() {
            startNewParagraph();
        }

        public void characters(String content) {
            if (Utils.isBlank(content)) {
                return;
            }

            String text = paragraph.appendText(content);

            if (link) {
                paragraph.charsCountInLinks += text.length();
            }
            br = false;
        }
    }

    public static class PathInfo {
        /** Tag name, its order among same-named siblings, and counters for its children. */
        private record PathElement(String tagName, int order, Map<String, Integer> children) {
        }

        private final List<PathElement> elements = new ArrayList<>();

        public String dom() {
            return elements.stream().map(PathElement::tagName).collect(Collectors.joining("."));
        }

        public String xpath() {
            return "/" + elements.stream()
                    .map(e -> e.tagName() + "[" + e.order() + "]")
                    .collect(Collectors.joining("/"));
        }

        public PathInfo append(String tagName) {
            Map<String, Integer> children = getChildren();
            int order = children.getOrDefault(tagName, 0) + 1;
            children.put(tagName, order);
            elements.add(new PathElement(tagName, order, new HashMap<>()));
            return this;
        }

        private Map<String, Integer> getChildren() {
            if (elements.isEmpty()) {
                return new HashMap<>();
            }
            return elements.get(elements.size() - 1).children();
        }

        /** @throws IndexOutOfBoundsException when the path is empty */
        public PathInfo pop() {
            if (elements.isEmpty()) {
                throw new IndexOutOfBoundsException("pop from empty path");
            }
            elements.remove(elements.size() - 1);
            return this;
        }
    }

    // -------------------------------------------------------- classification

    /** Lower-case all words in stoplist. */
    public static Set<String> defineStoplist(Iterable<String> stoplist) {
        Set<String> result = new java.util.HashSet<>();
        for (String w : stoplist) {
            result.add(w.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    /** Context-free paragraph classification with default thresholds. */
    public static void classifyParagraphs(List<Paragraph> paragraphs, Iterable<String> stoplist) {
        classifyParagraphs(paragraphs, stoplist, LENGTH_LOW_DEFAULT, LENGTH_HIGH_DEFAULT,
                STOPWORDS_LOW_DEFAULT, STOPWORDS_HIGH_DEFAULT, MAX_LINK_DENSITY_DEFAULT, NO_HEADINGS_DEFAULT);
    }

    /** Context-free paragraph classification. */
    public static void classifyParagraphs(
            List<Paragraph> paragraphs,
            Iterable<String> stoplist,
            int lengthLow,
            int lengthHigh,
            double stopwordsLow,
            double stopwordsHigh,
            double maxLinkDensity,
            boolean noHeadings) {
        Set<String> stopwords = defineStoplist(stoplist);
        for (Paragraph paragraph : paragraphs) {
            int length = paragraph.length();
            double stopwordDensity = paragraph.stopwordsDensity(stopwords);
            double linkDensity = paragraph.linksDensity();
            paragraph.heading = !noHeadings && paragraph.isHeading();

            if (linkDensity > maxLinkDensity) {
                paragraph.cfClass = "bad";
            } else if (paragraph.text().contains("©") || paragraph.text().contains("&copy")) {
                paragraph.cfClass = "bad";
            } else if (paragraph.domPath.contains("select")) {
                paragraph.cfClass = "bad";
            } else if (length < lengthLow) {
                paragraph.cfClass = paragraph.charsCountInLinks > 0 ? "bad" : "short";
            } else if (stopwordDensity >= stopwordsHigh) {
                paragraph.cfClass = length > lengthHigh ? "good" : "neargood";
            } else if (stopwordDensity >= stopwordsLow) {
                paragraph.cfClass = "neargood";
            } else {
                paragraph.cfClass = "bad";
            }
        }
    }

    private static String getNeighbour(
            int i, List<Paragraph> paragraphs, boolean ignoreNeargood, int inc, int boundary) {
        while (i + inc != boundary) {
            i += inc;
            String c = paragraphs.get(i).classType;
            if (GOOD_OR_BAD.contains(c)) {
                return c;
            }
            if ("neargood".equals(c) && !ignoreNeargood) {
                return c;
            }
        }
        return "bad";
    }

    /**
     * Return the class of the paragraph at the top end of the short/neargood
     * paragraphs block. If ignoreNeargood is true, than only 'bad' or 'good'
     * can be returned, otherwise 'neargood' can be returned, too.
     */
    public static String getPrevNeighbour(int i, List<Paragraph> paragraphs, boolean ignoreNeargood) {
        return getNeighbour(i, paragraphs, ignoreNeargood, -1, -1);
    }

    /**
     * Return the class of the paragraph at the bottom end of the short/neargood
     * paragraphs block. If ignoreNeargood is true, than only 'bad' or 'good'
     * can be returned, otherwise 'neargood' can be returned, too.
     */
    public static String getNextNeighbour(int i, List<Paragraph> paragraphs, boolean ignoreNeargood) {
        return getNeighbour(i, paragraphs, ignoreNeargood, 1, paragraphs.size());
    }

    /**
     * Context-sensitive paragraph classification. Assumes that classifyParagraphs
     * has already been called.
     */
    public static void reviseParagraphClassification(List<Paragraph> paragraphs, int maxHeadingDistance) {
        // good headings
        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph paragraph = paragraphs.get(i);
            // copy classes
            paragraph.classType = paragraph.cfClass;
            if (!(paragraph.heading && "short".equals(paragraph.classType))) {
                continue;
            }
            int j = i + 1;
            int distance = 0;
            while (j < paragraphs.size() && distance <= maxHeadingDistance) {
                if ("good".equals(paragraphs.get(j).classType)) {
                    paragraph.classType = "neargood";
                    break;
                }
                distance += paragraphs.get(j).length();
                j += 1;
            }
        }

        // classify short
        Map<Integer, String> newClasses = new HashMap<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (!"short".equals(paragraphs.get(i).classType)) {
                continue;
            }
            String prevNeighbour = getPrevNeighbour(i, paragraphs, true);
            String nextNeighbour = getNextNeighbour(i, paragraphs, true);
            if ("good".equals(prevNeighbour) && "good".equals(nextNeighbour)) {
                newClasses.put(i, "good");
            } else if ("bad".equals(prevNeighbour) && "bad".equals(nextNeighbour)) {
                newClasses.put(i, "bad");
            // it must be set(['good', 'bad'])
            } else if (("bad".equals(prevNeighbour)
                            && "neargood".equals(getPrevNeighbour(i, paragraphs, false)))
                    || ("bad".equals(nextNeighbour)
                            && "neargood".equals(getNextNeighbour(i, paragraphs, false)))) {
                newClasses.put(i, "good");
            } else {
                newClasses.put(i, "bad");
            }
        }

        for (Map.Entry<Integer, String> entry : newClasses.entrySet()) {
            paragraphs.get(entry.getKey()).classType = entry.getValue();
        }

        // revise neargood
        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph paragraph = paragraphs.get(i);
            if (!"neargood".equals(paragraph.classType)) {
                continue;
            }
            String prevNeighbour = getPrevNeighbour(i, paragraphs, true);
            String nextNeighbour = getNextNeighbour(i, paragraphs, true);
            if ("bad".equals(prevNeighbour) && "bad".equals(nextNeighbour)) {
                paragraph.classType = "bad";
            } else {
                paragraph.classType = "good";
            }
        }

        // more good headings
        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph paragraph = paragraphs.get(i);
            if (!(paragraph.heading && "bad".equals(paragraph.classType) && !"bad".equals(paragraph.cfClass))) {
                continue;
            }
            int j = i + 1;
            int distance = 0;
            while (j < paragraphs.size() && distance <= maxHeadingDistance) {
                if ("good".equals(paragraphs.get(j).classType)) {
                    paragraph.classType = "good";
                    break;
                }
                distance += paragraphs.get(j).length();
                j += 1;
            }
        }
    }

    // ------------------------------------------------------------- entry point

    /** Converts an HTML page into a list of classified paragraphs using default settings. */
    public static List<Paragraph> justext(String htmlText, Iterable<String> stoplist) {
        return justext(Core.htmlToDom(htmlText), stoplist, LENGTH_LOW_DEFAULT, LENGTH_HIGH_DEFAULT,
                STOPWORDS_LOW_DEFAULT, STOPWORDS_HIGH_DEFAULT, MAX_LINK_DENSITY_DEFAULT,
                MAX_HEADING_DISTANCE_DEFAULT, NO_HEADINGS_DEFAULT, Core::preprocessor);
    }

    /** Converts raw HTML bytes into a list of classified paragraphs using default settings. */
    public static List<Paragraph> justext(byte[] htmlBytes, Iterable<String> stoplist) {
        return justext(htmlBytes, stoplist, LENGTH_LOW_DEFAULT, LENGTH_HIGH_DEFAULT,
                STOPWORDS_LOW_DEFAULT, STOPWORDS_HIGH_DEFAULT, MAX_LINK_DENSITY_DEFAULT,
                MAX_HEADING_DISTANCE_DEFAULT, NO_HEADINGS_DEFAULT, null, DEFAULT_ENCODING,
                DEFAULT_ENC_ERRORS);
    }

    /**
     * Converts raw HTML bytes into a list of classified paragraphs. Each paragraph
     * is represented as an instance of {@link Paragraph}.
     *
     * @param encoding force this encoding, or {@code null} to detect it
     */
    public static List<Paragraph> justext(
            byte[] htmlBytes,
            Iterable<String> stoplist,
            int lengthLow,
            int lengthHigh,
            double stopwordsLow,
            double stopwordsHigh,
            double maxLinkDensity,
            int maxHeadingDistance,
            boolean noHeadings,
            String encoding,
            String defaultEncoding,
            String encErrors) {
        Document dom = htmlToDom(htmlBytes, defaultEncoding, encoding, encErrors);
        return justext(dom, stoplist, lengthLow, lengthHigh, stopwordsLow, stopwordsHigh,
                maxLinkDensity, maxHeadingDistance, noHeadings, Core::preprocessor);
    }

    /** Classifies paragraphs of an already parsed document. */
    public static List<Paragraph> justext(
            Document dom,
            Iterable<String> stoplist,
            int lengthLow,
            int lengthHigh,
            double stopwordsLow,
            double stopwordsHigh,
            double maxLinkDensity,
            int maxHeadingDistance,
            boolean noHeadings,
            UnaryOperator<Document> preprocessor) {
        Document cleaned = preprocessor.apply(dom);

        List<Paragraph> paragraphs = ParagraphMaker.makeParagraphs(cleaned);

        classifyParagraphs(paragraphs, stoplist, lengthLow, lengthHigh, stopwordsLow, stopwordsHigh,
                maxLinkDensity, noHeadings);
        reviseParagraphClassification(paragraphs, maxHeadingDistance);

        return paragraphs;
    }
}
