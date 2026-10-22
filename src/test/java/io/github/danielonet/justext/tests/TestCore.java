// Java port of tests/test_core.py
package io.github.danielonet.justext.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.danielonet.justext.justext.JustextInit;
import io.github.danielonet.justext.justext.Paragraph;

class TestCore {

    @Test
    void wordsShouldBeSplitByBrTag() {
        List<Paragraph> paragraphs =
                JustextInit.justext("abc<br/>def becoming abcdef", JustextInit.getStoplist("English"));

        assertEquals(List.of("abc def becoming abcdef"), paragraphs.stream().map(Paragraph::text).toList());
    }
}
