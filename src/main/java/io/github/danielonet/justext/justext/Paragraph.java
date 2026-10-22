// Java port of justext/paragraph.py
package io.github.danielonet.justext.justext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.danielonet.justext.justext.Core.PathInfo;

/** Object representing one block of text in HTML. */
public class Paragraph {

    public static final Pattern HEADINGS_PATTERN = Pattern.compile("\\bh\\d\\b");

    public final String domPath;
    public final String xpath;
    public final List<String> textNodes = new ArrayList<>();
    public int charsCountInLinks = 0;
    public int tagsCount = 0;
    /** Final class: short | neargood | good | bad. */
    public String classType = "";
    /** Context-free class assigned by {@link Core#classifyParagraphs}. */
    public String cfClass = "";
    /** Whether this paragraph is treated as a heading during classification. */
    public boolean heading = false;

    public Paragraph(PathInfo path) {
        this.domPath = path.dom();
        this.xpath = path.xpath();
    }

    public boolean isHeading() {
        return HEADINGS_PATTERN.matcher(domPath).find();
    }

    public boolean isBoilerplate() {
        return !"good".equals(classType);
    }

    public String text() {
        return Utils.normalizeWhitespace(Utils.strip(String.join("", textNodes)));
    }

    /** Length of the text (Python's {@code len(paragraph)}). */
    public int length() {
        return text().length();
    }

    public int wordsCount() {
        return Utils.split(text()).length;
    }

    public boolean containsText() {
        return !textNodes.isEmpty();
    }

    public String appendText(String text) {
        String normalized = Utils.normalizeWhitespace(text);
        textNodes.add(normalized);
        return normalized;
    }

    public int stopwordsCount(Set<String> stopwords) {
        int count = 0;
        for (String word : Utils.split(text())) {
            if (stopwords.contains(word.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    public double stopwordsDensity(Set<String> stopwords) {
        int words = wordsCount();
        if (words == 0) {
            return 0;
        }
        return (double) stopwordsCount(stopwords) / words;
    }

    public double linksDensity() {
        int textLength = length();
        if (textLength == 0) {
            return 0;
        }
        return (double) charsCountInLinks / textLength;
    }

    @Override
    public String toString() {
        return "Paragraph[" + classType + ", " + xpath + "] " + text();
    }
}
