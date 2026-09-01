# EPUB assets

Salvaged from the retired `scripts/` pandoc pipeline. `tools/build-epub.sh` consumes these.

- `epub-metadata.xml` — Dublin Core metadata: title, author, language, rights
- `epub.css` — stylesheet for the EPUB rendition
- `make-cover.sh` — renders `cover/cover.html` to `cover/cover.png`
- `mermaid-filter.lua` — pandoc filter turning mermaid fences into images
- `cover/` — cover source and rendered PNG

## Why the old pipeline was deleted

`scripts/build-book.sh` built an EPUB by running pandoc over `research/*.md` in the order
given by `SUMMARY.md`. That reads only the markdown sources, which generate 10 of the
book's 24 `.tex` files. The other 14 — the whole grounding argument, 35,276 words — have
no markdown form. `SUMMARY.md` listed five chapters because it was structurally blind to
two thirds of the book, and its output was publicly downloadable.

The replacement converts from `book/main.tex`, which is the source of truth for everything.
A feasibility probe on 2026-09-01 confirmed `tex4ebook -f epub3` converts the manuscript
with exit 0 and no errors, producing 33 chapter files. Two constructs need explicit
configuration: math is rasterised to images rather than emitted as MathML, and `cladnote`
loses its monospace class along with the hard-space alignment it depends on.
