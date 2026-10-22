// Java port of justext/__init__.py
/*
 * Copyright (c) 2011 Jan Pomikalek
 *
 * This software is licensed as described in the file LICENSE.rst.
 */
package io.github.danielonet.justext.justext;

import java.util.List;
import java.util.Set;

/** Public entry points of the library, like the Python package's {@code __init__}. */
public final class JustextInit {

    public static final String VERSION = "3.0.2";

    private JustextInit() {
    }

    public static List<Paragraph> justext(String htmlText, Iterable<String> stoplist) {
        return Core.justext(htmlText, stoplist);
    }

    public static List<Paragraph> justext(byte[] htmlBytes, Iterable<String> stoplist) {
        return Core.justext(htmlBytes, stoplist);
    }

    public static Set<String> getStoplist(String language) {
        return Utils.getStoplist(language);
    }

    public static Set<String> getStoplists() {
        return Utils.getStoplists();
    }
}
