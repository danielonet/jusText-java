# jusText-java

[![Maven Central](https://img.shields.io/maven-central/v/io.github.danielonet/justext?logo=apachemaven)](https://central.sonatype.com/artifact/io.github.danielonet/justext)
[![build](https://github.com/danielonet/jusText-java/actions/workflows/build.yml/badge.svg)](https://github.com/danielonet/jusText-java/actions/workflows/build.yml)
[![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

Remove boilerplate (navigation, headers, footers, link lists) from HTML pages in Java:
a port of [jusText](https://github.com/miso-belica/jusText) 3.0.2, the Python library by
Jan Pomikálek, maintained by Michal Belica.

```java
import io.github.danielonet.justext.justext.JustextInit;
import io.github.danielonet.justext.justext.Paragraph;

byte[] html = HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder(URI.create("https://peps.python.org/pep-0020/")).build(),
              HttpResponse.BodyHandlers.ofByteArray())
        .body();

for (Paragraph p : JustextInit.justext(html, JustextInit.getStoplist("English"))) {
    if (!p.isBoilerplate()) {
        System.out.println(p.text());
    }
}
```

Output (shortened), with the site navigation, sidebar and footer removed:

```
Beautiful is better than ugly.
Explicit is better than implicit.
Simple is better than complex.
...
Namespaces are one honking great idea -- let's do more of those!
```

**Why:** jusText keeps text made of full sentences, which makes it a good fit for
building linguistic resources such as web corpora. It is also the fallback extractor
in [trafilatura](https://github.com/adbar/trafilatura), where
[trafilatura-java](https://github.com/danielonet/trafilatura-java) currently ships only
a stub. This library runs in the same process as your JVM code, with the same algorithm
and the same 100 built-in stoplists.

> **Status (3.0.2-SNAPSHOT)**
>
> - **Fidelity:** follows jusText 3.0.2 module by module. The library and the CLI are
>   fully ported.
> - **Tests:** all 7 upstream pytest modules are ported to JUnit 5 and pass, 57 tests in
>   all: classification, core, DOM cleaning, HTML encoding, paths, paragraph building
>   and utilities.
> - **Not published yet:** there is no Maven Central release; build it from source.
> - **Known gaps:** HTML is parsed with jsoup instead of libxml2. See
>   [Differences from the Python version](#differences-from-the-python-version).

It returns a list of paragraphs, each classified as `good` or boilerplate (`bad`), with
its text, DOM path, XPath, word count, link density and stopword density. The CLI
writes the result as plain paragraphs, paragraphs plus boilerplate, a detailed
format with the classification as attributes, or the KrdWrd format.

## Installation

Not on Maven Central yet. Install it into your local Maven repository:

```bash
git clone https://github.com/danielonet/jusText-java.git
cd jusText-java
mvn install
```

Then depend on it.

Maven:

```xml
<dependency>
    <groupId>io.github.danielonet</groupId>
    <artifactId>justext</artifactId>
    <version>3.0.2-SNAPSHOT</version>
</dependency>
```

Gradle (with `mavenLocal()` in your repositories):

```groovy
implementation 'io.github.danielonet:justext:3.0.2-SNAPSHOT'
```

## Usage

### As a library

The public API mirrors the Python one, with Java-style names
(`justext`, `getStoplist`, `getStoplists`, `isBoilerplate`, `classType`...), so the
[jusText algorithm description](https://github.com/miso-belica/jusText/blob/main/doc/algorithm.rst)
applies, and the Python source can be read side by side with this code.

```java
import io.github.danielonet.justext.justext.Core;
import io.github.danielonet.justext.justext.JustextInit;
import io.github.danielonet.justext.justext.Paragraph;

// a String is used as is; byte[] lets jusText detect the encoding from <meta> tags
List<Paragraph> paragraphs = JustextInit.justext(html, JustextInit.getStoplist("English"));

// all thresholds, the encoding handling and the error mode, like Python's keyword arguments
List<Paragraph> tuned = Core.justext(
        htmlBytes, JustextInit.getStoplist("German"),
        Core.LENGTH_LOW_DEFAULT, Core.LENGTH_HIGH_DEFAULT,
        Core.STOPWORDS_LOW_DEFAULT, Core.STOPWORDS_HIGH_DEFAULT,
        Core.MAX_LINK_DENSITY_DEFAULT, Core.MAX_HEADING_DISTANCE_DEFAULT,
        false,         // noHeadings
        null,          // encoding: null = detect
        "utf8",        // defaultEncoding
        "replace");    // errors: strict | ignore | replace

// language-independent mode: an empty stoplist and both stopword thresholds at 0
```

`JustextInit` re-exports the main entry points, like the Python package's
`__init__.py`. Errors are reported as `Core.JustextError`, an unchecked exception.

### Command line

```bash
CP=target/justext-3.0.2-SNAPSHOT.jar:path/to/jsoup.jar
java -cp $CP io.github.danielonet.justext.justext.Main -s English page.html
java -cp $CP io.github.danielonet.justext.justext.Main -s English -o text.txt https://example.org/article
cat page.html | java -cp $CP io.github.danielonet.justext.justext.Main -s None --format=detailed
```

The options are the same as the Python CLI (`python -m justext`); run with `--help`
for the list, and `--list-stoplists` for the built-in languages.

### Stoplists

The 100 built-in stoplists from upstream are bundled in the jar. `-s` (or
`getStoplist`) takes a language name such as `English` or `Slovak`. On the command
line it also accepts a path to your own file, one word per line in UTF-8, or `None` for
the language-independent mode.

### Thread safety

All entry points are static and keep no shared mutable state: each call parses its own
document and builds its own paragraph list, so `justext` can be called from several
threads at once. Stoplists are read from the jar on every `getStoplist` call; load them
once and reuse the returned set, which is immutable.

## Differences from the Python version

- **HTML parsing.** HTML is parsed and cleaned with jsoup (HTML5 rules) instead of
  lxml/libxml2, so tree shapes can differ on malformed markup. The lxml `Cleaner` setup
  (drop `<head>`, scripts, styles, comments, forms and embedded objects) is reproduced
  by hand.
- **Empty documents.** `htmlToDom("")` throws `JustextError("Document is empty")` where
  lxml raises `ParserError`.
- **Encodings.** Encoding names and error modes go through `java.nio.charset`, which
  knows most of the names Python does but not all of them.
- **Python 2 support and `lru_cache`** are dropped; they have no use in Java.

## Building from source

JDK 21 and Maven 3.8 or newer.

```bash
mvn package       # compile, test, and build target/justext-3.0.2-SNAPSHOT.jar
```

Dependencies, all fetched by the build: jsoup (HTML parsing and cleaning, replaces
lxml) and JUnit 5 (tests, replaces pytest).

### Project layout

```
src/main/java/io/github/danielonet/justext/justext/
  Core.java          core.py: decoding, cleaning, paragraph building, classification
  Paragraph.java     paragraph.py
  Utils.java         utils.py: whitespace handling, stoplist loading
  Main.java          __main__.py: the command-line tool
  JustextInit.java   __init__.py: public entry points and version
src/main/resources/io/github/danielonet/justext/   stoplists/*.txt and their index
src/test/java/io/github/danielonet/justext/tests/  ported pytest suites (JUnit 5)
```

## Contributing

Bug reports with the URL of a page that comes out differently from Python jusText are
the most useful contribution. When changing the code, keep it close to the Python
source so the two can be compared side by side.

## Credits and citation

The algorithm, the heuristics, the stoplists and the test suites are the work of
Jan Pomikálek, Michal Belica and the
[jusText contributors](https://github.com/miso-belica/jusText/graphs/contributors);
this project only carries them over to the JVM. jusText was developed at the Natural
Language Processing Centre of Masaryk University in Brno. The port started from a
machine translation (Pyrite) and was then rewritten by hand to compile and to behave
like the Python original.

If you use it in research, cite Jan Pomikálek's
[PhD thesis](https://is.muni.cz/th/45523/fi_d/phdthesis.pdf), where the algorithm is
described:

```bibtex
@phdthesis{pomikalek-2011-justext,
  title  = {Removing Boilerplate and Duplicate Content from Web Corpora},
  author = {Pomik{\'a}lek, Jan},
  school = {Masaryk University, Faculty of Informatics},
  address = {Brno},
  year   = {2011}
}
```

## License

Apache License 2.0. See [LICENSE](LICENSE).

The port is derived from jusText (BSD-2-Clause, Jan Pomikálek and Michal Belica); its
license and copyright notice are kept in
[licenses/LICENSE-jusText.txt](licenses/LICENSE-jusText.txt).
