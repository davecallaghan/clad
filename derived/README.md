# Derived works

Blogs for Medium and whitepapers for arXiv, written from the book.

These are **hand-written**, not generated. A Medium post is not a chapter with the LaTeX
stripped out: different length, different opening, different assumed reader. What is
mechanical is not the writing but the record of what it came from.

## Front matter contract

Every file in `blogs/` and `whitepapers/` begins with:

```yaml
---
sources: [ch:canonical-form, sec:repeatability-fails]
book_commit: 39df18d
target: medium
status: draft
---
```

| field | meaning |
|---|---|
| `sources` | LaTeX labels from `book/` this work draws on. Use the label, not a chapter number |
| `book_commit` | the book commit this was written against |
| `target` | `medium`, `arxiv`, or `leanpub` |
| `status` | `draft`, `submitted`, or `published` |
| `archive_sources` | optional; paths under `archive/` where a prior version of the argument lives |

`tools/check-derived-provenance.sh` reports which works predate the current book and which
of their cited sources have changed since. It never fails the build: a blog post going
stale is not a broken build, and a check that blocks on it gets disabled.

## Why labels rather than chapter numbers

On 2026-09-01 the book's parts were reordered and every chapter number changed — the
control model moved from Chapter 5 to Chapter 11, the invariants from 6 to 5. Labels
survived that; numbers would not have. Cite `ch:framework`, never "Chapter 5".
