// Java port of tests/test_dom_utils.py
package io.github.danielonet.justext.tests;

import static io.github.danielonet.justext.justext.Core.htmlToDom;
import static io.github.danielonet.justext.justext.Core.preprocessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import io.github.danielonet.justext.justext.Core.JustextError;

class TestDomUtils {

    /** Serializes the {@code <html>} element without pretty printing. */
    private static String toString(Document dom) {
        return dom.selectFirst("html").outerHtml();
    }

    @Test
    void removeComments() {
        Document dom = htmlToDom("<html><!-- comment --><body>" + "<h1>Header</h1>" + "<!-- comment --> text"
                + "<p>footer" + "</body></html>");

        assertEquals("<html><body><h1>Header</h1> text<p>footer</p></body></html>", toString(preprocessor(dom)));
    }

    @Test
    void removeHeadTag() {
        Document dom = htmlToDom("<html><head><title>Title</title></head><body>" + "<h1>Header</h1>"
                + "<p><span>text</span></p>" + "<p>footer <em>like</em> a boss</p>" + "</body></html>");

        String expected = "<html><body>" + "<h1>Header</h1>" + "<p><span>text</span></p>"
                + "<p>footer <em>like</em> a boss</p>" + "</body></html>";
        assertEquals(expected, toString(preprocessor(dom)));
    }

    @Test
    void preprocessSimpleUnicodeString() {
        String html = "<html><head><title>Title</title></head><body>" + "<h1>Header</h1>"
                + "<p>pre<span>text</span>post<em>emph</em>popost</p>" + "<p>footer <em>like</em> a boss</p>"
                + "</body></html>";

        String expected = "<html><body>" + "<h1>Header</h1>" + "<p>pre<span>text</span>post<em>emph</em>popost</p>"
                + "<p>footer <em>like</em> a boss</p>" + "</body></html>";
        assertEquals(expected, toString(preprocessor(htmlToDom(html))));
    }

    @Test
    void preprocessSimpleBytesString() {
        String html = "<html><head><title>Title</title></head><body>" + "<h1>Header</h1>"
                + "<p>pre<span>text</span>post<em>emph</em>popost</p>" + "<p>footer <em>like</em> a boss</p>"
                + "  <!-- abcdefgh -->\n" + "</body></html>";

        String expected = "<html><body>" + "<h1>Header</h1>" + "<p>pre<span>text</span>post<em>emph</em>popost</p>"
                + "<p>footer <em>like</em> a boss</p>" + "  \n" + "</body></html>";
        assertEquals(expected, toString(preprocessor(htmlToDom(html.getBytes(StandardCharsets.UTF_8)))));
    }

    private static final String XHTML = "<?xml version=\"1.0\" encoding=\"windows-1250\"?>" + "<!DOCTYPE html>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"sk\" lang=\"sk\">" + "<head>"
            + "<title>Hello World</title>" + "<meta http-equiv=\"imagetoolbar\" content=\"no\" />"
            + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1250\" />" + "</head>"
            + "<body id=\"index\">" + "</body>" + "</html>";

    private static final String XHTML_EXPECTED =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"sk\" lang=\"sk\">" + "<body id=\"index\">"
                    + "</body>" + "</html>";

    @Test
    void preprocessSimpleUnicodeXhtmlStringWithDeclaration() {
        assertEquals(XHTML_EXPECTED, toString(preprocessor(htmlToDom(XHTML))));
    }

    @Test
    void preprocessSimpleBytesXhtmlStringWithDeclaration() {
        byte[] bytes = XHTML.getBytes(java.nio.charset.Charset.forName("windows-1250"));
        assertEquals(XHTML_EXPECTED, toString(preprocessor(htmlToDom(bytes))));
    }

    /** https://github.com/miso-belica/jusText/issues/17 */
    @Test
    void parserDoesNotHoldContextFromPreviousParsing() {
        htmlToDom("<justext></justext>");

        JustextError e = assertThrows(JustextError.class, () -> htmlToDom(""));
        assertFalse(e.getMessage().contains("justext"));
    }
}
