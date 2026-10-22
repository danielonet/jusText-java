// Java port of tests/test_sax.py
package io.github.danielonet.justext.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.danielonet.justext.justext.Core;
import io.github.danielonet.justext.justext.Core.ParagraphMaker;
import io.github.danielonet.justext.justext.Paragraph;

class TestSax {

    private static void assertParagraph(Paragraph p, String text, int wordsCount, int tagsCount) {
        assertEquals(text, p.text(), "text");
        assertEquals(wordsCount, p.wordsCount(), "words_count");
        assertEquals(tagsCount, p.tagsCount, "tags_count");
    }

    private static List<Paragraph> paragraphs(String html) {
        return ParagraphMaker.makeParagraphs(Core.htmlToDom(html));
    }

    @Test
    void noParagraphs() {
        assertEquals(0, paragraphs("<html><body></body></html>").size());
    }

    @Test
    void basic() {
        List<Paragraph> paragraphs = paragraphs("<html><body>" + "<h1>Header</h1>"
                + "<p>text and some <em>other</em> words <span class=\"class\">that I</span> have in my head now</p>"
                + "<p>footer</p>" + "</body></html>");
        assertEquals(3, paragraphs.size());

        assertParagraph(paragraphs.get(0), "Header", 1, 0);
        assertParagraph(paragraphs.get(1), "text and some other words that I have in my head now", 12, 2);
        assertParagraph(paragraphs.get(2), "footer", 1, 0);
    }

    @Test
    void whitespaceHandling() {
        List<Paragraph> paragraphs = paragraphs("<html><body>"
                + "<p>pre<em>in</em>post \t pre  <span class=\"class\"> in </span>  post</p>"
                + "<div>pre<em> in </em>post</div>" + "<pre>pre<em>in </em>post</pre>"
                + "<blockquote>pre<em> in</em>post</blockquote>" + "</body></html>");
        assertEquals(4, paragraphs.size());

        assertParagraph(paragraphs.get(0), "preinpost pre in post", 4, 2);
        assertParagraph(paragraphs.get(1), "pre in post", 3, 1);
        assertParagraph(paragraphs.get(2), "prein post", 2, 1);
        assertParagraph(paragraphs.get(3), "pre inpost", 2, 1);
    }

    @Test
    void multipleLineBreak() {
        List<Paragraph> paragraphs = paragraphs(
                "<html><body>" + "  normal text   <br><br> another   text  " + "</body></html>");
        assertEquals(2, paragraphs.size());

        assertParagraph(paragraphs.get(0), "normal text", 2, 0);
        assertParagraph(paragraphs.get(1), "another text", 2, 0);
    }

    /** Inline text should be treated as separate paragraph. */
    @Test
    void inlineTextInBody() {
        List<Paragraph> paragraphs = paragraphs("<html><body>"
                + "<sup>I am <strong>top</strong>-inline\n\n\n\n and I am happy \n</sup>"
                + "<p>normal text</p>" + "<code>\nvar i = -INFINITY;\n</code>"
                + "<div>after text with variable <var>N</var> </div>"
                + "   I am inline\n\n\n\n and I am happy \n" + "</body></html>");
        assertEquals(5, paragraphs.size());

        assertParagraph(paragraphs.get(0), "I am top-inline\nand I am happy", 7, 2);
        assertParagraph(paragraphs.get(1), "normal text", 2, 0);
        assertParagraph(paragraphs.get(2), "var i = -INFINITY;", 4, 1);
        assertParagraph(paragraphs.get(3), "after text with variable N", 5, 1);
        assertParagraph(paragraphs.get(4), "I am inline\nand I am happy", 7, 0);
    }

    @Test
    void links() {
        List<Paragraph> paragraphs = paragraphs("<html><body>"
                + "<a>I am <strong>top</strong>-inline\n\n\n\n and I am happy \n</a>"
                + "<p>normal text</p>" + "<code>\nvar i = -INFINITY;\n</code>"
                + "<div>after <a>text</a> with variable <var>N</var> </div>"
                + "   I am inline\n\n\n\n and I am happy \n" + "</body></html>");
        assertEquals(5, paragraphs.size());

        assertParagraph(paragraphs.get(0), "I am top-inline\nand I am happy", 7, 2);
        assertEquals(31, paragraphs.get(0).charsCountInLinks);
        assertParagraph(paragraphs.get(1), "normal text", 2, 0);
        assertParagraph(paragraphs.get(2), "var i = -INFINITY;", 4, 1);
        assertParagraph(paragraphs.get(3), "after text with variable N", 5, 2);
        assertEquals(4, paragraphs.get(3).charsCountInLinks);
        assertParagraph(paragraphs.get(4), "I am inline\nand I am happy", 7, 0);
    }
}
