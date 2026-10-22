// Java port of tests/test_classify_paragraphs.py
package io.github.danielonet.justext.tests;

import static io.github.danielonet.justext.justext.Core.LENGTH_HIGH_DEFAULT;
import static io.github.danielonet.justext.justext.Core.LENGTH_LOW_DEFAULT;
import static io.github.danielonet.justext.justext.Core.NO_HEADINGS_DEFAULT;
import static io.github.danielonet.justext.justext.Core.STOPWORDS_HIGH_DEFAULT;
import static io.github.danielonet.justext.justext.Core.STOPWORDS_LOW_DEFAULT;
import static io.github.danielonet.justext.justext.Core.classifyParagraphs;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.danielonet.justext.justext.Core.PathInfo;
import io.github.danielonet.justext.justext.Paragraph;

class TestClassifyParagraphs {

    private static Paragraph paragraph(String text) {
        return paragraph(text, 0);
    }

    private static Paragraph paragraph(String text, int charsCountInLinks) {
        Paragraph paragraph = new Paragraph(new PathInfo().append("body").append("p"));
        paragraph.appendText(text);
        paragraph.charsCountInLinks = charsCountInLinks;
        return paragraph;
    }

    @Test
    void maxLinkDensity() {
        List<Paragraph> paragraphs = List.of(
                paragraph("0123456789".repeat(2), 0),
                paragraph("0123456789".repeat(2), 20),
                paragraph("0123456789".repeat(8), 40),
                paragraph("0123456789".repeat(8), 39),
                paragraph("0123456789".repeat(8), 41));

        classifyParagraphs(paragraphs, List.of(), LENGTH_LOW_DEFAULT, LENGTH_HIGH_DEFAULT,
                STOPWORDS_LOW_DEFAULT, STOPWORDS_HIGH_DEFAULT, 0.5, NO_HEADINGS_DEFAULT);

        assertEquals("short", paragraphs.get(0).cfClass);
        assertEquals("bad", paragraphs.get(1).cfClass);
        assertEquals("bad", paragraphs.get(2).cfClass);
        assertEquals("bad", paragraphs.get(3).cfClass);
        assertEquals("bad", paragraphs.get(4).cfClass);
    }

    @Test
    void lengthLow() {
        List<Paragraph> paragraphs = List.of(
                paragraph("0 1 2 3 4 5 6 7 8 9".repeat(2), 0),
                paragraph("0 1 2 3 4 5 6 7 8 9".repeat(2), 20));

        classifyParagraphs(paragraphs, List.of(), 1000, LENGTH_HIGH_DEFAULT,
                STOPWORDS_LOW_DEFAULT, STOPWORDS_HIGH_DEFAULT, 1, NO_HEADINGS_DEFAULT);

        assertEquals("short", paragraphs.get(0).cfClass);
        assertEquals("bad", paragraphs.get(1).cfClass);
    }

    @Test
    void stopwordsHigh() {
        List<Paragraph> paragraphs = List.of(
                paragraph("0 1 2 3 4 5 6 7 8 9"),
                paragraph("0 1 2 3 4 5 6 7 8 9".repeat(2)));

        classifyParagraphs(paragraphs, List.of("0"), 0, 20,
                STOPWORDS_LOW_DEFAULT, 0, 1, NO_HEADINGS_DEFAULT);

        assertEquals("neargood", paragraphs.get(0).cfClass);
        assertEquals("good", paragraphs.get(1).cfClass);
    }

    @Test
    void stopwordsLow() {
        List<Paragraph> paragraphs = List.of(
                paragraph("0 0 0 0 1 2 3 4 5 6 7 8 9"),
                paragraph("0 1 2 3 4 5 6 7 8 9"),
                paragraph("1 2 3 4 5 6 7 8 9"));

        classifyParagraphs(paragraphs, List.of("0", "1"), 0, LENGTH_HIGH_DEFAULT,
                0.2, 1000, 1, NO_HEADINGS_DEFAULT);

        assertEquals("neargood", paragraphs.get(0).cfClass);
        assertEquals("neargood", paragraphs.get(1).cfClass);
        assertEquals("bad", paragraphs.get(2).cfClass);
    }
}
