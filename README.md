# jusText-java
java port for jusText from https://github.com/miso-belica/jusText/tree/main

Yes — based on the package metadata in setup.py, this project declares these dependencies:

- Runtime
  - lxml[html_clean] >= 4.4.2
  - backports.functools-lru-cache; python_version < "3.2"

- Test dependencies
  - pytest
  - pytest-cov
  - coverage

The install requirements are defined in the project’s setup.py, and the project is basically a small library around lxml for HTML parsing/cleaning.

Not very hard, for this project specifically.

The Python dependency footprint is small:
- lxml[html_clean] >= 4.4.2
- backports.functools-lru-cache (only for older Python 2 compatibility)
- test tooling only: pytest, pytest-cov, coverage

For Java, the practical equivalents are:

- lxml[html_clean]
  - Best match: Jsoup
  - Why: it parses HTML, traverses the DOM, can clean/sanitize markup, and is widely used in Java
  - Alternative: Jericho HTML or HTMLCleaner if you need looser/older HTML handling
  - This is the main real dependency to replace

- backports.functools-lru-cache
  - In Java, this is usually unnecessary
  - Use:
    - a regular Map / LinkedHashMap with custom eviction
    - or Caffeine / Guava Cache if you want a real cache
  - In this project, it is not a core concern; it’s mostly compatibility glue

- pytest / coverage / pytest-cov
  - Map to JUnit + Mockito + JaCoCo
  - Not runtime dependencies; just testing infrastructure

What makes this translation manageable:
- The utility code in justext/utils.py is mostly stdlib-style logic:
  - regex whitespace normalization
  - file system scanning for stoplists
  - loading packaged resource files
- Those are straightforward in Java
- The main algorithmic work is not in the library dependencies; it is in the heuristic boilerplate-removal logic itself, which is a normal Java DOM/HTML-processing task

So overall:
- Dependency replacement: easy
- Full port to Java: moderate, not huge
- Time estimate for a competent Java dev: a few days to a couple weeks depending on the accuracy you want to preserve

If you want, I can also map the specific functions in this repo to Java classes and suggest a concrete Java stack like:
- Jsoup + JUnit + Caffeine
- or Jsoup + Apache Commons Text + custom stopword loader
