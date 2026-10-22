// Java port of justext/__main__.py
package io.github.danielonet.justext.justext;

import static io.github.danielonet.justext.justext.Core.DEFAULT_ENCODING;
import static io.github.danielonet.justext.justext.Core.DEFAULT_ENC_ERRORS;
import static io.github.danielonet.justext.justext.Core.LENGTH_HIGH_DEFAULT;
import static io.github.danielonet.justext.justext.Core.LENGTH_LOW_DEFAULT;
import static io.github.danielonet.justext.justext.Core.MAX_HEADING_DISTANCE_DEFAULT;
import static io.github.danielonet.justext.justext.Core.MAX_LINK_DENSITY_DEFAULT;
import static io.github.danielonet.justext.justext.Core.STOPWORDS_HIGH_DEFAULT;
import static io.github.danielonet.justext.justext.Core.STOPWORDS_LOW_DEFAULT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.danielonet.justext.justext.Core.JustextError;
import io.github.danielonet.justext.justext.Core.JustextInvalidOptions;

public final class Main {

    private static final String PROGNAME = "justext";

    /** Long options; a trailing '=' means the option takes a value. */
    private static final List<String> LONG_OPTIONS = List.of(
            "encoding=", "enc-force", "enc-errors=", "format=", "no-headings", "help", "version",
            "length-low=", "length-high=", "stopwords-low=", "stopwords-high=", "max-link-density=",
            "max-heading-distance=", "list-stoplists");
    /** Short options taking a value. */
    private static final String SHORT_WITH_VALUE = "os";
    private static final String SHORT_FLAGS = "hV";

    private Main() {
    }

    public static String usage() {
        return String.format(Locale.ROOT, """
                Usage: %1$s -s STOPLIST [OPTIONS] [HTML_FILE]
                Convert HTML to plain text and remove boilerplate.

                  -o OUTPUT_FILE   if not specified, output is written to stdout
                  --encoding=...   default character encoding to be used if not specified
                                   in the HTML meta tags (default: %2$s)
                  --enc-force      force specified encoding, ignore HTML meta tags
                  --enc-errors=... errors handling for character encoding conversion:
                                     strict: fail on error
                                     ignore: ignore characters which can't be converted
                                     replace: replace characters which can't be converted
                                              with U+FFFD unicode replacement characters
                                   (default: %3$s)
                  --format=...     output format; possible values:
                                     default: one paragraph per line, each preceded with
                                              <p> or <h> (headings)
                                     boilerplate: same as default, except for boilerplate
                                                  paragraphs are included, too, preceded
                                                  with <b>
                                     detailed: one paragraph per line, each preceded with
                                               <p> tag containing detailed information
                                               about classification as attributes
                                     krdwrd: KrdWrd compatible format
                  --no-headings    disable special handling of headings
                  --list-stoplists print a list of inbuilt stoplists and exit
                  -V, --version    print version information and exit
                  -h, --help       display this help and exit

                If no HTML_FILE specified, input is read from stdin.

                STOPLIST must be one of the following:
                  - one of the inbuilt stoplists; see:
                      %1$s --list-stoplists
                  - path to a file with the most frequent words for given language,
                    one per line, in UTF-8 encoding
                  - None - this activates a language-independent mode

                Advanced options:
                  --length-low=INT (default %4$d)
                  --length-high=INT (default %5$d)
                  --stopwords-low=FLOAT (default %6$f)
                  --stopwords-high=FLOAT (default %7$f)
                  --max-link-density=FLOAT (default %8$f)
                  --max-heading-distance=INT (default %9$d)
                """, PROGNAME, DEFAULT_ENCODING, DEFAULT_ENC_ERRORS, LENGTH_LOW_DEFAULT, LENGTH_HIGH_DEFAULT,
                STOPWORDS_LOW_DEFAULT, STOPWORDS_HIGH_DEFAULT, MAX_LINK_DENSITY_DEFAULT,
                MAX_HEADING_DISTANCE_DEFAULT);
    }

    /** Escapes {@code &}, {@code <} and {@code >} (Python's {@code html.escape(s, quote=False)}). */
    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Outputs the paragraphs as:
     * <pre>
     * &lt;tag&gt; text of the first paragraph
     * &lt;tag&gt; text of the second paragraph
     * ...
     * </pre>
     * where &lt;tag&gt; is &lt;p&gt;, &lt;h&gt; or &lt;b&gt; which indicates
     * standard paragraph, heading or boilerplate respectively.
     */
    public static void outputDefault(List<Paragraph> paragraphs, PrintWriter fp, boolean noBoilerplate) {
        for (Paragraph paragraph : paragraphs) {
            String tag;
            if ("good".equals(paragraph.classType)) {
                tag = paragraph.heading ? "h" : "p";
            } else if (noBoilerplate) {
                continue;
            } else {
                tag = "b";
            }
            fp.println(String.format("<%s> %s", tag, escape(paragraph.text())));
        }
    }

    /**
     * Same as outputDefault, but only &lt;p&gt; tags are used and the following
     * attributes are added: class, cfclass and heading.
     */
    public static void outputDetailed(List<Paragraph> paragraphs, PrintWriter fp) {
        for (Paragraph paragraph : paragraphs) {
            fp.println(String.format(
                    "<p class=\"%s\" cfclass=\"%s\" heading=\"%d\" xpath=\"%s\"> %s",
                    paragraph.classType,
                    paragraph.cfClass,
                    paragraph.heading ? 1 : 0,
                    paragraph.xpath,
                    escape(paragraph.text())));
        }
    }

    /**
     * Outputs the paragraphs in a KrdWrd compatible format:
     * <pre>
     * class&lt;TAB&gt;first text node
     * class&lt;TAB&gt;second text node
     * ...
     * </pre>
     * where class is 1, 2 or 3 which means boilerplate, undecided or good
     * respectively. Headings are output as undecided.
     */
    public static void outputKrdwrd(List<Paragraph> paragraphs, PrintWriter fp) {
        for (Paragraph paragraph : paragraphs) {
            int cls;
            if ("good".equals(paragraph.classType) || "neargood".equals(paragraph.classType)) {
                cls = paragraph.heading ? 2 : 3;
            } else {
                cls = 1;
            }
            for (String textNode : paragraph.textNodes) {
                fp.println(cls + "\t" + Utils.strip(textNode));
            }
        }
    }

    /** One parsed command line option. */
    private record Opt(String name, String value) {
    }

    /** Minimal getopt: supports -x VALUE, -xVALUE, --name=VALUE, --name VALUE and flags. */
    private static List<Opt> parseOptions(String[] argv, List<String> rest) throws JustextInvalidOptions {
        List<Opt> opts = new ArrayList<>();
        int i = 0;
        while (i < argv.length) {
            String arg = argv[i];
            if ("--".equals(arg)) {
                i++;
                break;
            }
            if (arg.startsWith("--")) {
                String body = arg.substring(2);
                String name = body;
                String value = null;
                int eq = body.indexOf('=');
                if (eq >= 0) {
                    name = body.substring(0, eq);
                    value = body.substring(eq + 1);
                }
                if (LONG_OPTIONS.contains(name + "=")) {
                    if (value == null) {
                        if (i + 1 >= argv.length) {
                            throw new JustextInvalidOptions("option --" + name + " requires argument");
                        }
                        value = argv[++i];
                    }
                } else if (LONG_OPTIONS.contains(name)) {
                    if (value != null) {
                        throw new JustextInvalidOptions("option --" + name + " must not have an argument");
                    }
                } else {
                    throw new JustextInvalidOptions("option --" + name + " not recognized");
                }
                opts.add(new Opt("--" + name, value));
            } else if (arg.startsWith("-") && arg.length() > 1) {
                char c = arg.charAt(1);
                if (SHORT_WITH_VALUE.indexOf(c) >= 0) {
                    String value = arg.length() > 2 ? arg.substring(2) : null;
                    if (value == null) {
                        if (i + 1 >= argv.length) {
                            throw new JustextInvalidOptions("option -" + c + " requires argument");
                        }
                        value = argv[++i];
                    }
                    opts.add(new Opt("-" + c, value));
                } else if (SHORT_FLAGS.indexOf(c) >= 0 && arg.length() == 2) {
                    opts.add(new Opt("-" + c, null));
                } else {
                    throw new JustextInvalidOptions("option " + arg + " not recognized");
                }
            } else {
                // getopt stops at the first non-option argument
                break;
            }
            i++;
        }
        for (; i < argv.length; i++) {
            rest.add(argv[i]);
        }
        return opts;
    }

    private static int parseInt(String o, String a) {
        try {
            return Integer.parseInt(a.strip());
        } catch (NumberFormatException e) {
            throw new JustextInvalidOptions(String.format("Invalid value for %s: '%s'. Integer expected.", o, a));
        }
    }

    private static double parseDouble(String o, String a) {
        try {
            return Double.parseDouble(a.strip());
        } catch (NumberFormatException e) {
            throw new JustextInvalidOptions(String.format("Invalid value for %s: '%s'. Float expected.", o, a));
        }
    }

    private static Set<String> readStoplistFile(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
            Set<String> words = new HashSet<>();
            for (String line : text.split("\\R")) {
                words.add(line.strip());
            }
            return words;
        } catch (CharacterCodingException e) {
            throw new JustextInvalidOptions(
                    "Unicode decoding error when reading the stoplist (probably not in UTF-8): " + e);
        } catch (IOException e) {
            throw new JustextInvalidOptions(String.format("Can't open %s for reading: %s", file, e));
        }
    }

    /** Runs the CLI and returns the process exit code. */
    public static int run(String[] argv, InputStream stdin, OutputStream stdout, PrintWriter stderr) {
        List<String> args = new ArrayList<>();
        List<Opt> opts;
        try {
            opts = parseOptions(argv, args);
        } catch (JustextInvalidOptions e) {
            stderr.println(e.getMessage());
            stderr.println(usage());
            stderr.flush();
            return 1;
        }

        OutputStream out = stdout;
        Set<String> stoplist = null;
        String format = "default";
        boolean noHeadings = false;
        int lengthLow = LENGTH_LOW_DEFAULT;
        int lengthHigh = LENGTH_HIGH_DEFAULT;
        double stopwordsLow = STOPWORDS_LOW_DEFAULT;
        double stopwordsHigh = STOPWORDS_HIGH_DEFAULT;
        double maxLinkDensity = MAX_LINK_DENSITY_DEFAULT;
        int maxHeadingDistance = MAX_HEADING_DISTANCE_DEFAULT;
        String encoding = null;
        String defaultEncoding = DEFAULT_ENCODING;
        boolean forceDefaultEncoding = false;
        String encErrors = DEFAULT_ENC_ERRORS;

        PrintWriter fpOut = null;
        try {
            for (Opt opt : opts) {
                String o = opt.name();
                String a = opt.value();
                switch (o) {
                    case "-h", "--help" -> {
                        PrintWriter pw = utf8Writer(stdout);
                        pw.print(usage());
                        pw.flush();
                        return 0;
                    }
                    case "-V", "--version" -> {
                        PrintWriter pw = utf8Writer(stdout);
                        pw.println(String.format(
                                "%s: jusText v%s%n%nCopyright (c) 2011 Jan Pomikalek <jan.pomikalek@gmail.com>",
                                PROGNAME, JustextInit.VERSION));
                        pw.flush();
                        return 0;
                    }
                    case "--list-stoplists" -> {
                        PrintWriter pw = utf8Writer(stdout);
                        Utils.getStoplists().stream().sorted().forEach(pw::println);
                        pw.flush();
                        return 0;
                    }
                    case "-o" -> {
                        try {
                            out = Files.newOutputStream(Path.of(a));
                        } catch (IOException e) {
                            throw new JustextInvalidOptions(String.format("Can't open %s for writing: %s", a, e));
                        }
                    }
                    case "-s" -> {
                        if ("none".equals(a.toLowerCase(Locale.ROOT))) {
                            stoplist = new HashSet<>();
                        } else if (Files.isRegularFile(Path.of(a))) {
                            stoplist = readStoplistFile(Path.of(a));
                        } else if (Utils.getStoplists().contains(a)) {
                            stoplist = Utils.getStoplist(a);
                        } else if (Pattern.matches("(?U)^\\w*$", a)) {
                            // only alphabetical chars, probably misspelled or
                            // unsupported language
                            throw new JustextInvalidOptions(String.format(
                                    "Unknown stoplist: %s%nAvailable stoplists:%n%s",
                                    a, String.join("\n", Utils.getStoplists().stream().sorted().toList())));
                        } else {
                            // probably incorrectly specified path
                            throw new JustextInvalidOptions(String.format("File not found: %s", a));
                        }
                    }
                    case "--encoding" -> {
                        try {
                            Charset.forName(a);
                        } catch (IllegalArgumentException e) {
                            throw new JustextInvalidOptions(String.format("Unknown character encoding: %s", a));
                        }
                        defaultEncoding = a;
                    }
                    case "--enc-force" -> forceDefaultEncoding = true;
                    case "--enc-errors" -> {
                        String lower = a.toLowerCase(Locale.ROOT);
                        if (List.of("strict", "ignore", "replace").contains(lower)) {
                            encErrors = lower;
                        } else {
                            throw new JustextInvalidOptions(String.format("Invalid --enc-errors value: %s", a));
                        }
                    }
                    case "--format" -> {
                        if (List.of("default", "boilerplate", "detailed", "krdwrd").contains(a)) {
                            format = a;
                        } else {
                            throw new JustextInvalidOptions(String.format("Unknown output format: %s", a));
                        }
                    }
                    case "--no-headings" -> noHeadings = true;
                    case "--length-low" -> lengthLow = parseInt(o, a);
                    case "--length-high" -> lengthHigh = parseInt(o, a);
                    case "--stopwords-low" -> stopwordsLow = parseDouble(o, a);
                    case "--stopwords-high" -> stopwordsHigh = parseDouble(o, a);
                    case "--max-link-density" -> maxLinkDensity = parseDouble(o, a);
                    case "--max-heading-distance" -> maxHeadingDistance = parseInt(o, a);
                    default -> throw new AssertionError("Unhandled option: " + o);
                }
            }

            if (forceDefaultEncoding) {
                encoding = defaultEncoding;
            }

            if (stoplist == null) {
                throw new JustextInvalidOptions("No stoplist specified.");
            }

            if (stoplist.isEmpty()) {
                // empty stoplist, switch to language-independent mode
                stopwordsHigh = 0;
                stopwordsLow = 0;
            }

            byte[] htmlBytes;
            if (!args.isEmpty()) {
                String source = args.get(0);
                try {
                    if (Pattern.compile("^[^:/]+://").matcher(source).find()) {
                        try (InputStream in = URI.create(source).toURL().openStream()) {
                            htmlBytes = in.readAllBytes();
                        }
                    } else {
                        htmlBytes = Files.readAllBytes(Path.of(source));
                    }
                } catch (IOException | IllegalArgumentException e) {
                    throw new JustextInvalidOptions(String.format("Can't open %s for reading: %s", source, e));
                }
            } else {
                try {
                    htmlBytes = stdin.readAllBytes();
                } catch (IOException e) {
                    throw new JustextError("Can't read from stdin: " + e, e);
                }
            }

            List<Paragraph> paragraphs = Core.justext(
                    htmlBytes, stoplist, lengthLow, lengthHigh, stopwordsLow, stopwordsHigh,
                    maxLinkDensity, maxHeadingDistance, noHeadings, encoding, defaultEncoding, encErrors);

            fpOut = utf8Writer(out);
            switch (format) {
                case "default" -> outputDefault(paragraphs, fpOut, true);
                case "boilerplate" -> outputDefault(paragraphs, fpOut, false);
                case "detailed" -> outputDetailed(paragraphs, fpOut);
                case "krdwrd" -> outputKrdwrd(paragraphs, fpOut);
                // this should not happen; format checked when parsing options
                default -> throw new AssertionError("Unknown format: " + format);
            }
            fpOut.flush();
            return 0;
        } catch (JustextError e) {
            stderr.println(String.format("%s: %s", PROGNAME, e.getMessage()));
            stderr.flush();
            return 1;
        } finally {
            if (out != stdout) {
                if (fpOut != null) {
                    fpOut.close();
                } else {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                        // nothing useful to do
                    }
                }
            }
        }
    }

    private static PrintWriter utf8Writer(OutputStream out) {
        return new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    public static void main(String[] args) {
        PrintWriter stderr = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        int code = run(args, System.in, System.out, stderr);
        System.out.flush();
        System.exit(code);
    }
}
