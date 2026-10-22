// Java port of tests/test_html_encoding.py
package io.github.danielonet.justext.tests;

import static io.github.danielonet.justext.justext.Core.DEFAULT_ENCODING;
import static io.github.danielonet.justext.justext.Core.DEFAULT_ENC_ERRORS;
import static io.github.danielonet.justext.justext.Core.decodeHtml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.danielonet.justext.justext.Core.JustextError;

class TestHtmlEncoding {

    private static final Charset ISO_8859_2 = Charset.forName("iso-8859-2");

    private static byte[] iso2(String s) {
        return s.getBytes(ISO_8859_2);
    }

    @Test
    void unicode() {
        String html = "ľščťžýáíéäňúô Ł€";
        assertEquals(html, decodeHtml(html));
    }

    @Test
    void utf8Bytes() {
        String html = "ľščťžýáíéäňúô Ł€";
        assertEquals(html, decodeHtml(html.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-2\"/> ľščťžäňôě",
            "<meta content=text/html; charset=iso-8859-2 http-equiv=\"Content-Type\"/> ľščťžäňôě",
            "<meta content='text/html; charset=iso-8859-2' http-equiv=\"Content-Type\"/> ľščťžäňôě",
            "<meta charset=iso-8859-2/> ľščťžäňôě",
            "<meta charset=\"iso-8859-2\"/> ľščťžäňôě",
            "<meta charset=iso-8859-2> ľščťžäňôě",
            "<meta charset=\"iso-8859-2\"/> charset=\"iso-fake-29\" ľščťžäňôě",
            "<meta content=text/html; charset=iso-8859-2 http-equiv=\"Content-Type\"/> charset=\"iso-fake-29\" ľščťžäňôě",
            "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; CHARSET=ISO-8859-2\"> charset=\"iso-fake-29\" ľščťžäňôě",
    })
    void metaDetection(String html) {
        assertEquals(html, decodeHtml(iso2(html)));
    }

    @Test
    void unknownEncodingInStrictMode() {
        assertThrows(JustextError.class,
                () -> decodeHtml(iso2("ľščťžäňôě"), DEFAULT_ENCODING, null, "strict"));
    }

    @Test
    void unknownEncodingWithDefaultErrorHandler() {
        String html = "ľščťžäňôě";
        assertEquals(html, decodeHtml(iso2(html), "iso-8859-2", null, DEFAULT_ENC_ERRORS));
    }

    @Test
    void givenEncoding() {
        String html = "ľščťžäňôě";
        assertEquals(html, decodeHtml(iso2(html), DEFAULT_ENCODING, "iso-8859-2", DEFAULT_ENC_ERRORS));
    }

    @Test
    void givenWrongEncoding() {
        String html = "ľščťžäňôě";
        assertEquals("�".repeat(html.length()),
                decodeHtml(iso2(html), DEFAULT_ENCODING, "ASCII", DEFAULT_ENC_ERRORS));
    }

    @Test
    void fakeEncodingInMeta() {
        String html = "<meta charset=\"iso-fake-2\"/> ľščťžäňôě";
        assertThrows(JustextError.class, () -> decodeHtml(iso2(html), DEFAULT_ENCODING, null, "strict"));
    }
}
