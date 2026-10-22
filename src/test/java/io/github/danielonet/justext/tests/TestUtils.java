// Java port of tests/test_utils.py
package io.github.danielonet.justext.tests;

import static io.github.danielonet.justext.justext.Utils.getStoplist;
import static io.github.danielonet.justext.justext.Utils.getStoplists;
import static io.github.danielonet.justext.justext.Utils.isBlank;
import static io.github.danielonet.justext.justext.Utils.normalizeWhitespace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestUtils {

    @Nested
    class TestStringUtils {
        @Test
        void emptyStringIsBlank() {
            assertTrue(isBlank(""));
        }

        @Test
        void stringWithSpaceIsBlank() {
            assertTrue(isBlank(" "));
        }

        @Test
        void stringWithNobreakSpaceIsBlank() {
            assertTrue(isBlank(" \t "));
        }

        @Test
        void stringWithNarrowNobreakSpaceIsBlank() {
            assertTrue(isBlank("  \t"));
        }

        @Test
        void stringWithSpacesIsBlank() {
            assertTrue(isBlank("    "));
        }

        @Test
        void stringWithNewlineIsBlank() {
            assertTrue(isBlank("\n"));
        }

        @Test
        void stringWithTabIsBlank() {
            assertTrue(isBlank("\t"));
        }

        @Test
        void stringWithWhitespaceIsBlank() {
            assertTrue(isBlank("\t\n "));
        }

        @Test
        void stringWithCharsIsNotBlank() {
            assertFalse(isBlank("  #  "));
        }

        @Test
        void normalizeNoChange() {
            String string = "a b c d e f g h i j k l m n o p q r s ...";
            assertEquals(string, normalizeWhitespace(string));
        }

        @Test
        void normalizeDontTrim() {
            String string = "  a b c d e f g h i j k l m n o p q r s ...  ";
            String expected = " a b c d e f g h i j k l m n o p q r s ... ";
            assertEquals(expected, normalizeWhitespace(string));
        }

        @Test
        void normalizeNewlineAndTab() {
            assertEquals("123\n456\n", normalizeWhitespace("123 \n456\t\n"));
        }

        @Test
        void normalizeNonBreakSpaces() {
            assertEquals(" € ", normalizeWhitespace(" \t €  \t"));
        }
    }

    @Nested
    class TestStoplistsUtils {
        @Test
        void getStopwordsList() {
            assertEquals(Set.of(
                    "Afrikaans", "Albanian", "Arabic", "Aragonese", "Armenian", "Aromanian", "Asturian",
                    "Azerbaijani", "Basque", "Belarusian", "Belarusian_Taraskievica", "Bengali",
                    "Bishnupriya_Manipuri", "Bosnian", "Breton", "Bulgarian", "Catalan", "Cebuano", "Croatian",
                    "Czech", "Danish", "Dutch", "English", "Esperanto", "Estonian", "Finnish", "French",
                    "Galician", "Georgian", "German", "Greek", "Gujarati", "Haitian", "Hebrew", "Hindi",
                    "Hungarian", "Chuvash", "Icelandic", "Ido", "Igbo", "Indonesian", "Irish", "Italian",
                    "Javanese", "Kannada", "Kazakh", "Korean", "Kurdish", "Kyrgyz", "Latin", "Latvian",
                    "Lithuanian", "Lombard", "Low_Saxon", "Luxembourgish", "Macedonian", "Malay", "Malayalam",
                    "Maltese", "Marathi", "Neapolitan", "Nepali", "Newar", "Norwegian_Bokmal",
                    "Norwegian_Nynorsk", "Occitan", "Persian", "Piedmontese", "Polish", "Portuguese", "Quechua",
                    "Romanian", "Russian", "Samogitian", "Serbian", "Serbo_Croatian", "Sicilian",
                    "Simple_English", "Slovak", "Slovenian", "Spanish", "Sundanese", "Swahili", "Swedish",
                    "Tagalog", "Tamil", "Telugu", "Turkish", "Turkmen", "Ukrainian", "Urdu", "Uzbek",
                    "Vietnamese", "Volapuk", "Walloon", "Waray_Waray", "Welsh", "West_Frisian",
                    "Western_Panjabi", "Yoruba"),
                    getStoplists());
        }

        @Test
        void getRealStoplist() {
            assertFalse(getStoplist("Slovak").isEmpty());
        }

        @Test
        void getMissingStoplist() {
            assertThrows(IllegalArgumentException.class, () -> getStoplist("Klingon"));
        }
    }
}
