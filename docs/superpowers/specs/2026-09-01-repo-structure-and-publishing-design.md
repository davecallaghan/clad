# Repository Structure and Publishing Design

**Date:** 2026-09-01
**Status:** approved design, pending implementation plan

## Why

The repository serves five outputs that have grown into each other:

- an **open-source Scala application** — 11 sbt modules, 4,145 lines of main code
- a **book** — 219 pages, 15 chapters in 7 parts, 4 appendices, 3 back-matter
  chapters, destined for Leanpub and then Kindle
- **blogs** for Medium, derived from the book
- **whitepapers** for arXiv, derived from the book
- a **deployed marketing site**, already live via GCP

plus an external **Lean model** in a sibling repository, monitored but not owned here.

Nothing about the current layout distinguishes these. `code/`, `book/`, `research/`,
`papers/`, `scripts/`, `tools/`, `gcp/`, `landing/`, `cover/` and `build/` sit at the
same level with no statement of which is a product, which is a source, which is an
archive, and which is operations. Four discoveries made the cost concrete.

**The book is half generated and half authored.** Ten `.tex` files are output —
`tools/rebuild-book.sh` regenerates them from `research/*.md` on every build, and
editing them is pointless. Fourteen are source: no markdown exists for them anywhere.
The split falls on a clean line: the governance framework is markdown-sourced, the
grounding argument is LaTeX-sourced, and they are close to equal in words.

**Two book pipelines existed, one three weeks stale.** `scripts/build-book.sh` plus
`SUMMARY.md` built an EPUB with pandoc, reading `research/*.md` directly. It listed
five chapters because it is structurally blind to the 35,276 authored words. Its output,
`build/trust-by-design.epub`, was publicly downloadable and misrepresented the book. It
has been removed.

**Half the book was already public, under a licence that cannot be revoked.**
`research/*.md` — 43,599 words — has been on a public GitHub repository since
2026-07-04 under CC BY 4.0, which grants commercial reuse and adaptation. The authored
LaTeX, 35,276 words, had never been pushed.

**The claims in the README are not enforced by anything.** It asserts 76 machine-checked
theorems (the count is 69), "mathematically proven correct", and "independently verified
against the production code" — where the differential test silently cancels because it
looks for `../lean/.lake/build/bin/clad-difftest`, a path that does not exist. The
build prints `canceled 2` and `All tests passed` in the same run.

## The licensing decision, recorded

Because CC BY 4.0 had already been granted on half the manuscript, "keep the book
private" was not available: CC licences cannot be revoked for distributed copies, and
the material persists in public history, clones and forks regardless of deletion.

The decision taken is to make **the book freely available alongside the code**, in one
public repository. The book's purpose is professional standing rather than sales
revenue. This makes the licensing coherent rather than contradictory, and removes the
need for a private repository, a cross-repository conformance pin for the book, and any
licence boundary to police.

## Goals

1. A layout that states what each directory is for: product, source, derived work,
   archive, or operations.
2. One source of truth per artifact, with EPUB and PDF both generated from it.
3. Derived works that record what they came from, so staleness is visible.
4. CI that enforces the claims the README makes, rather than asserting them.

## Non-goals

- Rewriting public history. The stale EPUB and the CC BY corpus remain in it. Both are
  accepted rather than concealed.
- Converting the authored LaTeX to markdown. That was considered and rejected: it would
  put 35,276 words of the most polished prose through a lossy round trip.
- Restructuring the Scala modules. `code/` becomes `app/` and is otherwise untouched.
- Owning the Lean model. It stays a sibling repository, pinned by commit SHA.

## §1 Topology: one public repo, five zones

```
clad/                              (public)
  app/          <- code/           Scala application, MIT
  book/                            the manuscript; LaTeX is the source of truth
  research/                        markdown sources for 10 of the book's files, CC BY 4.0
  derived/
    blogs/                         Medium drafts, provenance in front matter
    whitepapers/                   arXiv submissions
  archive/      <- papers/         prior three-paper program, benchmark fetch scripts
  ops/          <- gcp/ landing/   the deployed marketing site
  lean/                            the formal model, moved in 2026-09-01
  tools/                           both build pipelines
  docs/                            specs, plans, architecture notes
  .github/workflows/               CI
```

Four moves — `code→app`, `papers→archive`, `gcp→ops/gcp`, `landing→ops/landing` — one
new directory, and three retirements: `scripts/`, `cover/` (absorbed into `tools/`), and
`SUMMARY.md`.

`research/` keeps its name. It is already public under CC BY 4.0 at that path, and
renaming it would break the only inbound links that may exist.

The Lean model **moves in**, superseding the earlier plan to keep it external. It was in
a private repository under a different GitHub account, so a public workflow here could
not read it without a personal access token. Relocating it removes the secret, the
pinned SHA, and one of the three artifacts that can drift.

## §2 Where the two pipelines land

Both live in `tools/`, because they share a source.

| pipeline | reads | produces | status |
|---|---|---|---|
| `tools/rebuild-book.sh` | `research/*.md` → 10 `.tex`; then all 24 `.tex` | PDF via `latexmk` | exists, works |
| `tools/build-epub.sh` | `book/main.tex` | EPUB for Leanpub and Kindle | **new** |

`scripts/build-book.sh` and `SUMMARY.md` are deleted rather than migrated. They read
markdown directly and cannot see 35,276 words; retaining them invites someone to run
one and publish half a book. What is salvageable — `epub-metadata.xml`, `epub.css`,
`make-cover.sh`, `cover/` — moves into `tools/`.

**EPUB fidelity is the risk in this design, and it is testable.** The manuscript
contains 130 `cladnote` monospace blocks whose column alignment is carried by hard
spaces, 65 amsthm statements across eleven environments, 33 tables including two
`longtable`s, one tikz figure, and inline math throughout. Rather than hoping these
survive, `tools/check-epub-fidelity.sh` counts each construct in the LaTeX and asserts
the same count appears in the EPUB, failing the build on a shortfall.

Leanpub becomes a build-and-upload target rather than a repository integration: the
EPUB is produced here and handed over, because Leanpub's native path reads markdown
and would see only the generated third of the book.

## §3 Derived works and the archive

Blogs and whitepapers are hand-written for their own audiences — a Medium post is not a
chapter with the LaTeX stripped out — but each records where it came from:

```yaml
---
sources: [ch:canonical-form, sec:repeatability-fails]
book_commit: 39df18d
target: medium
status: draft
---
```

`tools/check-derived-provenance.sh` reports which derived works predate the current book
and which of their cited sources have changed since. **Informational, never blocking:** a
blog post going stale is not a build failure, and a check that blocks on it will be
disabled within a week.

`archive/` gets a README stating, for each item, what it was, what superseded it, and
what it remains useful for. The three-paper program and the standalone grounding paper
were merged into the book; the openFDA, DailyMed and EMA fetch scripts were verified
against live APIs and are the seed of any empirical work. The directory is source
material, not sediment.

## §4 What CI enforces

Four workflows replace the single one that deploys the landing page.

| workflow | trigger | enforces |
|---|---|---|
| `app.yml` | push, PR | `sbt test`. **Fails on any cancelled test** — which alone turns the differential test's silent skip into a red build |
| `book.yml` | push touching `book/`, `research/`, `tools/` | `rebuild-book.sh`, then fail on LaTeX errors, undefined references, or an overfull box above 40pt |
| `lean.yml` | push touching `lean/`, and nightly | two jobs: theorem count and zero-`sorry` on every change; `lake build` with the mathlib cache nightly. Only the second licenses the phrase "machine-checked" — a file with a type error has no `sorry` and does not compile either |
| `conformance.yml` | push, and nightly | the three-way manifest of the 2026-09-01 conformance spec. **Deferred** to that spec's own plan, which needs the extractors and the `@Conforms` annotation. The Lean relocation below removes its hardest constraint |
| `claims.yml` | push touching `README.md`, and nightly | every factual claim the README makes: the Lean theorem count, zero `sorry`, and that the differential test ran rather than cancelled |

`claims.yml` is the direct answer to the problem that prompted this section. A book
arguing that assurance claims must be checkable cannot ship a README whose own claims
are unchecked. Each assertion becomes either verified or a build failure.

`app.yml` failing on cancelled tests deserves emphasis: it is one line of configuration
and it closes the exact hole that let `canceled 2` sit beside `All tests passed` for
four months.

## Risks

- **EPUB fidelity is unproven.** No LaTeX→EPUB conversion of this manuscript has been
  attempted. The fidelity check reduces the risk of shipping a broken book but does not
  guarantee a good one; the first conversion may need per-construct work, and
  `cladnote` alignment is the likeliest casualty.
- **The public CC BY corpus can be republished commercially by anyone.** This is now
  accepted rather than mitigated. It is worth knowing rather than discovering.
- **Renaming `code/` to `app/` breaks external references** — the deployed landing site,
  any bookmark, any clone's muscle memory. Cheap now, and cheaper now than later.
- **Four workflows on a repository with no CI history** will surface pre-existing
  failures that were previously invisible. That is the point, but the first green build
  may take several passes.
