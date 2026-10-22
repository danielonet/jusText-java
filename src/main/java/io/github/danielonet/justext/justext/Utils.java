// Java port of justext/utils.py
package io.github.danielonet.justext.justext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Utils {

    /** Same as Python's {@code re.compile(r"\s+", re.UNICODE)}. */
    public static final Pattern MULTIPLE_WHITESPACE_PATTERN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern EDGE_WHITESPACE_PATTERN =
            Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

    /** Classpath folder holding the built-in {@code <Language>.txt} stoplists. */
    private static final String STOPLISTS_DIR = "/io/github/danielonet/justext/stoplists/";
    /** Names of all built-in stoplists, one per line (a jar can't be listed like a folder). */
    private static final String STOPLISTS_INDEX = "/io/github/danielonet/justext/stoplists.index";

    private Utils() {
    }

    /**
     * Translates multiple whitespace into single space character.
     * If there is at least one new line character chunk is replaced
     * by single LF (Unix new line) character.
     */
    public static String normalizeWhitespace(String text) {
        Matcher m = MULTIPLE_WHITESPACE_PATTERN.matcher(text);
        return m.replaceAll(match -> {
            String ws = match.group();
            return ws.contains("\n") || ws.contains("\r") ? "\n" : " ";
        });
    }

    /** Strips Unicode whitespace (incl. non-breaking spaces) like Python's {@code str.strip()}. */
    public static String strip(String text) {
        return EDGE_WHITESPACE_PATTERN.matcher(text).replaceAll("");
    }

    /** Splits on runs of Unicode whitespace like Python's {@code str.split()}. */
    public static String[] split(String text) {
        String stripped = strip(text);
        if (stripped.isEmpty()) {
            return new String[0];
        }
        return MULTIPLE_WHITESPACE_PATTERN.split(stripped);
    }

    /**
     * Returns {@code true} if string contains only white-space characters
     * or is empty. Otherwise {@code false} is returned.
     */
    public static boolean isBlank(String string) {
        if (string == null || string.isEmpty()) {
            return true;
        }
        return string.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    /** Returns a collection of built-in stop-lists. */
    public static Set<String> getStoplists() {
        try (InputStream in = Utils.class.getResourceAsStream(STOPLISTS_INDEX)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + STOPLISTS_INDEX);
            }
            return readLines(in).stream()
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a built-in stop-list for the language as a set of words. */
    public static Set<String> getStoplist(String language) {
        try (InputStream in = Utils.class.getResourceAsStream(STOPLISTS_DIR + language + ".txt")) {
            if (in == null) {
                throw new IllegalArgumentException(String.format(
                        "Stoplist for language '%s' is missing. "
                                + "Please use function 'getStoplists' for complete list of stoplists "
                                + "and feel free to contribute by your own stoplist.",
                        language));
            }
            return readLines(in).stream()
                    .map(w -> w.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static java.util.List<String> readLines(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }
}
