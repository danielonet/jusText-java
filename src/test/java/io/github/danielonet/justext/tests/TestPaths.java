// Java port of tests/test_paths.py
package io.github.danielonet.justext.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.danielonet.justext.justext.Core.PathInfo;

class TestPaths {

    @Test
    void emptyPath() {
        PathInfo path = new PathInfo();

        assertEquals("", path.dom());
        assertEquals("/", path.xpath());
    }

    @Test
    void pathWithRootOnly() {
        PathInfo path = new PathInfo().append("html");

        assertEquals("html", path.dom());
        assertEquals("/html[1]", path.xpath());
    }

    @Test
    void pathWithMoreElements() {
        PathInfo path = new PathInfo().append("html").append("body").append("header").append("h1");

        assertEquals("html.body.header.h1", path.dom());
        assertEquals("/html[1]/body[1]/header[1]/h1[1]", path.xpath());
    }

    @Test
    void containsMultipleTagsWithTheSameName() {
        PathInfo path = new PathInfo().append("html").append("body").append("div");

        assertEquals("html.body.div", path.dom());
        assertEquals("/html[1]/body[1]/div[1]", path.xpath());

        path.pop().append("div");

        assertEquals("html.body.div", path.dom());
        assertEquals("/html[1]/body[1]/div[2]", path.xpath());
    }

    @Test
    void moreElementsInTag() {
        PathInfo path = new PathInfo().append("html").append("body").append("div");

        assertEquals("html.body.div", path.dom());
        assertEquals("/html[1]/body[1]/div[1]", path.xpath());

        path.pop();

        assertEquals("html.body", path.dom());
        assertEquals("/html[1]/body[1]", path.xpath());

        path.append("span");

        assertEquals("html.body.span", path.dom());
        assertEquals("/html[1]/body[1]/span[1]", path.xpath());

        path.pop();

        assertEquals("html.body", path.dom());
        assertEquals("/html[1]/body[1]", path.xpath());

        path.append("pre");

        assertEquals("html.body.pre", path.dom());
        assertEquals("/html[1]/body[1]/pre[1]", path.xpath());
    }

    @Test
    void removingElement() {
        PathInfo path = new PathInfo().append("html").append("body");
        path.append("div").append("a").pop();

        assertEquals("html.body.div", path.dom());
        assertEquals("/html[1]/body[1]/div[1]", path.xpath());

        path.pop();

        assertEquals("html.body", path.dom());
        assertEquals("/html[1]/body[1]", path.xpath());
    }

    @Test
    void popOnEmptyPathRaisesException() {
        PathInfo path = new PathInfo();
        assertThrows(IndexOutOfBoundsException.class, path::pop);
    }
}
