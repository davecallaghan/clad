# Repository Moves Implementation Plan (Plan 2 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the top level say what each directory is for, so that product, source, derived work, archive, and operations are distinguishable at a glance.

**Architecture:** Four `git mv` operations and three retirements, each followed by fixing every reference to the moved path and re-running the verification that path serves. Nothing is renamed without the same commit fixing everything that named it.

**Tech Stack:** `git mv` (preserves history), `sbt` for the application verification, `latexmk` via `tools/rebuild-book.sh` for the book verification, `bash`.

**Spec:** `docs/superpowers/specs/2026-09-01-repo-structure-and-publishing-design.md` — this plan implements §1 and the retirements in §2.

## Global Constraints

- **`git mv`, never `mv`.** History must follow the files; `git log --follow` should work afterwards.
- **One directory per task, references fixed in the same commit.** A commit that renames a path and leaves a reference dangling is worse than no rename.
- **Do not edit the dated documents in `docs/superpowers/specs/` or the audit plan.** They record what was true when written; rewriting them falsifies the record. The one exception is `docs/superpowers/plans/2026-09-01-ci-enforcement.md`, which contains forward instructions naming `code/` and must be updated — Task 1 does this.
- **`research/` keeps its name.** It has been public at that path under CC BY 4.0 since 2026-07-04, and renaming it would break the only inbound links that may exist.
- **Verify after every move.** `sbt test` for `app/`, `tools/rebuild-book.sh` for the book. A move that compiles is not a move that works.
- Commit after each task with the message given in that task's final step.

## Reference map, measured

Every tracked file outside the moved directories that names one of them:

| file | names |
|---|---|
| `.gitignore` | `code/` ×5, `papers/` ×1, `gcp/` ×1 |
| `LICENSE-CODE.md` | `code/` ×1 |
| `README.md` | `code/` ×2, `gcp/` ×2, `landing/` ×2, `SUMMARY.md` ×2 |
| `.github/workflows/deploy-landing.yml` | `gcp/` ×3, `landing/` ×2 |
| `gcp/deploy-landing.sh` | `landing` internally (line 60), `./gcp/deploy-landing.sh` in a comment |
| `book/MIGRATION.md` | `code/` ×1, `papers/` ×1 |
| `docs/textbook-conversion-plan.md` | `scripts/` ×2, `SUMMARY.md` ×1 |
| `docs/superpowers/plans/2026-09-01-ci-enforcement.md` | `code/` in `app.yml`'s `working-directory` and `paths:` |

---

### Task 1: `code/` → `app/`

**Files:**
- Move: `code/` → `app/` (145 tracked files)
- Modify: `.gitignore`, `LICENSE-CODE.md`, `README.md`, `docs/superpowers/plans/2026-09-01-ci-enforcement.md`

**Interfaces:**
- Consumes: nothing.
- Produces: the application at `app/`. Every later task and both remaining plans refer to `app/`, not `code/`. The sbt project names are unchanged — `audit`, `` `audit-test` ``, `difftest` — so test invocations become `cd app && sbt ...`.

- [ ] **Step 1: Confirm the baseline passes before moving anything**

```bash
cd /Users/david.callaghan/clad/app 2>/dev/null || cd /Users/david.callaghan/clad/code
sbt -batch test 2>&1 | grep -E 'Tests: succeeded|error' | tail -12
cd /Users/david.callaghan/clad
```

Expected: every module passing, with `canceled 2` in the difftest module. Record the total. If anything fails here, stop — the move must not be blamed for a pre-existing failure.

- [ ] **Step 2: Move it**

```bash
cd /Users/david.callaghan/clad
git mv code app
git status --short | head -5
```

- [ ] **Step 3: Fix `.gitignore`**

Replace the five `code/` lines:

```
# sbt build artifacts
target/
app/target/
app/*/target/
app/project/target/
app/project/project/
```

- [ ] **Step 4: Fix `LICENSE-CODE.md`**

Change the applies-to line:

```markdown
**Applies to:** everything under `app/`
```

- [ ] **Step 5: Fix `README.md`**

Replace both occurrences of `code/` with `app/`. Read the surrounding sentences before editing — one is a directory listing and one is prose, and the prose may need "the code" rather than a path.

- [ ] **Step 6: Fix the CI plan's forward instructions**

In `docs/superpowers/plans/2026-09-01-ci-enforcement.md`, `app.yml` names `code` in two places. Change `working-directory: code` to `working-directory: app`, and the `paths:` entry `"code/**"` to `"app/**"`.

- [ ] **Step 7: Verify the application still builds and tests from its new home**

```bash
cd /Users/david.callaghan/clad/app
sbt -batch test 2>&1 | grep -E 'Tests: succeeded|error' | tail -12
cd /Users/david.callaghan/clad
```

Expected: the same totals as Step 1, including `canceled 2`.

- [ ] **Step 8: Verify the difftest's relative path still resolves**

The differential test resolves `../../lean/.lake/build/bin/clad-difftest` from its module directory. `code/difftest` and `app/difftest` are the same depth, so this must be unchanged:

```bash
cd /Users/david.callaghan/clad/app/difftest && \
  python3 -c "import os; print(os.path.abspath('../../lean/.lake/build/bin/clad-difftest'))" && \
  cd /Users/david.callaghan/clad
```

Expected: `/Users/david.callaghan/clad/lean/.lake/build/bin/clad-difftest`. If it prints a path outside the repository, the depth assumption is wrong — stop and fix `DifferentialTestSpec.scala` before committing.

- [ ] **Step 9: Confirm no dangling reference remains**

```bash
git grep -n 'code/' -- ':!docs/superpowers/specs' ':!docs/superpowers/plans/2026-09-01-audit-integrity.md' | grep -v '^app/'
```

Expected: no output. Matches inside the dated specs and the audit plan are historical and stay.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: code/ -> app/

The top level had product, source, archive and operations at the same depth with
nothing to tell them apart. app/ says what this is.

git mv preserves history; git log --follow works across the rename. sbt project
names are unchanged, so only the invocation directory moves. The differential
test's relative path to lean/ is unaffected — same depth.

References fixed in this commit: .gitignore (5), LICENSE-CODE.md, README.md (2),
and the CI plan's app.yml working-directory and paths filter. The dated specs and
the audit plan keep their original paths; they record what was true when written."
```

---

### Task 2: `papers/` → `archive/`, with a README that says what it is

The directory holds the three-paper program and the standalone grounding paper the book's
first half was derived from, plus the openFDA, DailyMed and EMA benchmark fetch scripts
verified against live APIs. Without a README it reads as abandoned. It is source material
for blogs and whitepapers.

**Files:**
- Move: `papers/` → `archive/` (123 tracked files)
- Create: `archive/README.md`
- Modify: `.gitignore`, `README.md`, `book/MIGRATION.md`

**Interfaces:**
- Consumes: nothing.
- Produces: `archive/` with a README stating, per item, what it was, what superseded it, and what it remains useful for. Plan 3's derived works cite `archive/` paths as prior art.

- [ ] **Step 1: Move it**

```bash
cd /Users/david.callaghan/clad
git mv papers archive
```

- [ ] **Step 2: Fix `.gitignore`**

Change `papers/*/build/` to `archive/*/build/`.

- [ ] **Step 3: Write `archive/README.md`**

```markdown
# Archive

Superseded source material, retained deliberately. Nothing here is current; everything
here is usable.

## What is in it

**`paper1-grounding/`** — the 67-page standalone paper on grounding, hallucination as
ungrounded assertion, and the non-identification result. Superseded by the book, whose
first half was derived from it: Chapters 1-5 and 9-13 are this paper, restructured.
Retained because it is the argument in a single-document form, which is closer to what
arXiv and a Medium series need than the book's chapter structure is.

**`paper1-subdoxastic/`** — an earlier framing of the same argument through formal
epistemology, abandoned when the epistemic apparatus proved impossible to defend to a
specialist reader. Retained as a record of why the book's register is what it is.

**`paper2-institutional-warrant/`**, **`paper3-verified-computation/`** — outlines only,
two pages each. The three-paper program was collapsed into one book. Retained because
the outlines are whitepaper scopes: institutional warrant and verified computation are
each a paper, and neither is fully developed in the book.

**`paper1-grounding/benchmark/fetch/`** — Python fetchers for openFDA, DailyMed and the
EMA medicines register, verified against the live APIs, with response caching and
throttling at 0.35s (about 170 requests per minute, under openFDA's 240 limit). Retained
because they are the seed of any empirical work: the book's evaluation protocol is
specified and unrun, and these are how a corpus gets built.

**`paper1-grounding/RED-TEAM*.md`**, **`OVERLAP-ANALYSIS.md`**, **`READABILITY.md`** —
three adversarial review passes and the analyses that followed. Retained because they
record which claims were weakened and why, which is the provenance for anything in the
book that reads as unusually hedged.

## What it is for

Blogs and whitepapers, per `derived/`. When a derived work draws on something here, cite
the path in its front matter alongside the book sections it uses.

## What not to do with it

Do not edit these to match the book. They are a snapshot of what the argument looked like
before the book existed, and their value is that they differ.
```

- [ ] **Step 4: Fix `README.md` and `book/MIGRATION.md`**

Replace `papers/` with `archive/` in each — one occurrence in `MIGRATION.md`, and check `README.md`'s directory listing if it has one.

- [ ] **Step 5: Verify nothing referenced the old path**

```bash
git grep -n 'papers/' -- ':!docs/superpowers/specs' ':!docs/superpowers/plans' | grep -v '^archive/'
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: papers/ -> archive/, with a README that says what it is

The directory holds the three-paper program, the standalone grounding paper the
book's first half was derived from, three adversarial review passes, and the
openFDA/DailyMed/EMA fetch scripts verified against live APIs. Without a README it
reads as abandoned; it is source material for blogs and whitepapers.

The README states, per item, what it was, what superseded it, and what it remains
useful for — including that the paper2 and paper3 outlines are whitepaper scopes
for material the book does not fully develop, and that the fetchers are the seed
of any empirical work, since the book's evaluation protocol is specified and unrun.

It also says what not to do with it: these are not to be edited to match the book.
Their value is that they differ."
```

---

### Task 3: `gcp/` and `landing/` → `ops/`

**Files:**
- Move: `gcp/` → `ops/gcp/` (7 files), `landing/` → `ops/landing/` (4 files)
- Modify: `.gitignore`, `README.md`, `.github/workflows/deploy-landing.yml`, `ops/gcp/deploy-landing.sh`

**Interfaces:**
- Consumes: nothing.
- Produces: operations under `ops/`. The deploy workflow's `paths:` filter and script path change; the script's internal `gsutil rsync` source changes from `landing` to `ops/landing`.

- [ ] **Step 1: Move both**

```bash
cd /Users/david.callaghan/clad
mkdir -p ops
git mv gcp ops/gcp
git mv landing ops/landing
```

- [ ] **Step 2: Fix the deploy script's internal path**

`ops/gcp/deploy-landing.sh` line 60 reads `gsutil -m rsync -R landing "${BUCKET}"`. The
script is invoked from the repository root, so change it to:

```bash
gsutil -m rsync -R ops/landing "${BUCKET}"
```

Also update the echo on line 59 to `echo "Uploading ops/landing ..."` and the usage
comment on line 10 to `./ops/gcp/deploy-landing.sh`.

- [ ] **Step 3: Fix the workflow**

In `.github/workflows/deploy-landing.yml`, update the `paths:` filter and the run step:

```yaml
    paths:
      - "ops/landing/**"
      - "ops/gcp/deploy-landing.sh"
      - ".github/workflows/deploy-landing.yml"
```

and the deploy step's `run: ./ops/gcp/deploy-landing.sh`.

- [ ] **Step 4: Fix `.gitignore` and `README.md`**

`.gitignore` has one `gcp/` reference — check what it ignores and re-point it at `ops/gcp/`.
`README.md` has two `gcp/` and two `landing/` references.

- [ ] **Step 5: Verify the deploy script is syntactically valid and its paths resolve**

```bash
cd /Users/david.callaghan/clad
bash -n ops/gcp/deploy-landing.sh && echo "syntax OK"
test -d ops/landing && test -f ops/landing/index.html && echo "landing present"
grep -n 'ops/landing' ops/gcp/deploy-landing.sh
```

Do **not** run the deploy itself: it publishes to a live bucket. Syntax and path checks
are the verification available without side effects.

- [ ] **Step 6: Confirm no dangling reference**

```bash
git grep -nE '(^|[^/a-z])(gcp|landing)/' -- ':!docs/superpowers' | grep -v '^ops/'
```

Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: gcp/ and landing/ -> ops/

The deployed marketing site and its deploy scripts are operations, not product.
Grouping them under ops/ leaves the top level with one entry per audience.

Fixed in this commit: the workflow's paths filter and script path, the script's
internal gsutil rsync source and its usage comment, .gitignore, and README.

The deploy itself is not run as verification — it publishes to a live bucket.
Syntax check and path existence are what is available without side effects."
```

---

### Task 4: Retire `scripts/`, `cover/`, and `SUMMARY.md`

`scripts/build-book.sh` and `SUMMARY.md` build an EPUB with pandoc by reading
`research/*.md` directly. That path is blind to the 14 authored `.tex` chapters — 35,276
words — which is why `SUMMARY.md` lists five chapters and the book has fifteen. Keeping
them invites someone to run one and publish a third of a book.

The metadata, stylesheet and cover are salvage: Plan 3's EPUB pipeline needs them.

**Files:**
- Move: `scripts/epub-metadata.xml`, `scripts/epub.css`, `scripts/make-cover.sh`, `scripts/mermaid-filter.lua` → `tools/epub/`
- Move: `scripts/run-differential-test.sh` → `tools/run-differential-test.sh`
- Move: `cover/` → `tools/epub/cover/`
- Delete: `scripts/build-book.sh`, `SUMMARY.md`
- Modify: `docs/textbook-conversion-plan.md`, `README.md`

**Interfaces:**
- Consumes: nothing.
- Produces: `tools/epub/` holding `epub-metadata.xml`, `epub.css`, `make-cover.sh`, `mermaid-filter.lua`, and `cover/`, consumed by Plan 3's `tools/build-epub.sh`; and `tools/run-differential-test.sh`, the harness that builds the Lean evaluator and runs it against the Scala engine, which the conformance plan uses.

- [ ] **Step 1: Salvage what Plan 3 needs**

```bash
cd /Users/david.callaghan/clad
mkdir -p tools/epub
git mv scripts/epub-metadata.xml scripts/epub.css scripts/make-cover.sh scripts/mermaid-filter.lua tools/epub/
git mv cover tools/epub/cover
# A test harness, not an EPUB asset: it builds the Lean evaluator and runs the
# differential test against the Scala engine.
git mv scripts/run-differential-test.sh tools/run-differential-test.sh
ls tools tools/epub tools/epub/cover
```

- [ ] **Step 2: Record why the pandoc pipeline is being deleted, then delete it**

Create `tools/epub/README.md`:

```markdown
# EPUB assets

Salvaged from the retired `scripts/` pandoc pipeline. `tools/build-epub.sh` (Plan 3)
consumes these.

- `epub-metadata.xml` — Dublin Core metadata: title, author, language, rights
- `epub.css` — stylesheet for the EPUB rendition
- `make-cover.sh` — renders `cover/cover.html` to `cover/cover.png`
- `mermaid-filter.lua` — pandoc filter turning mermaid fences into images
- `cover/` — cover source and rendered PNG

## Why the old pipeline was deleted

`scripts/build-book.sh` built an EPUB by running pandoc over `research/*.md`, in the
order given by `SUMMARY.md`. That reads only the markdown sources, which generate 10 of
the book's 24 `.tex` files. The other 14 — the whole grounding argument, 35,276 words —
have no markdown form. `SUMMARY.md` listed five chapters because it was structurally
blind to two thirds of the book, and its output was publicly downloadable.

The replacement converts from `book/main.tex`, which is the source of truth for
everything.
```

Then delete:

```bash
git rm scripts/build-book.sh SUMMARY.md
rmdir scripts 2>/dev/null || true
```

- [ ] **Step 2b: Point the differential-test harness at `app/`**

`tools/run-differential-test.sh` computes `REPO_ROOT` from its own location and then does
`cd "$REPO_ROOT/code"`. Task 1 renamed that directory. Two edits:

```bash
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"     # unchanged: tools/ is one level down
cd "$REPO_ROOT/app"                            # was $REPO_ROOT/code
```

The `$REPO_ROOT/lean` reference is already correct — the harness was written expecting the
model in this repository, which is what Plan 1 Task 1 delivers.

Verify:

```bash
bash -n tools/run-differential-test.sh && echo "syntax OK"
grep -n 'REPO_ROOT' tools/run-differential-test.sh
```

- [ ] **Step 3: Fix the references**

`docs/textbook-conversion-plan.md` names `scripts/` twice and `SUMMARY.md` once. It is a
dated planning document, so add a note at the top rather than rewriting its body:

```markdown
> **Superseded 2026-09-02.** The pandoc pipeline this plan describes (`scripts/build-book.sh`,
> `SUMMARY.md`) has been retired: it read `research/*.md` directly and could not see the 14
> authored `.tex` chapters. Assets salvaged to `tools/epub/`. See
> `docs/superpowers/specs/2026-09-01-repo-structure-and-publishing-design.md`.
```

`README.md` names `SUMMARY.md` twice — remove both references, since the file is gone.

- [ ] **Step 4: Verify the book still builds and nothing references the deleted files**

```bash
cd /Users/david.callaghan/clad
bash tools/rebuild-book.sh 2>&1 | tail -2
git grep -n 'SUMMARY\.md\|scripts/build-book' -- ':!docs/textbook-conversion-plan.md' ':!docs/superpowers' ':!tools/epub/README.md'
```

Expected: the book builds with 0 errors, and the grep prints nothing.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: retire the pandoc EPUB pipeline, salvage its assets

scripts/build-book.sh built an EPUB by running pandoc over research/*.md in the
order given by SUMMARY.md. Those markdown files generate 10 of the book's 24 .tex
files; the other 14 — the whole grounding argument, 35,276 words — have no
markdown form. SUMMARY.md listed five chapters because it was structurally blind
to two thirds of the book, and its output was publicly downloadable until
yesterday.

Deleted rather than migrated: keeping it invites someone to run it and publish a
third of a book. The metadata, stylesheet, cover and mermaid filter move to
tools/epub/ with a README recording why, since Plan 3's build-epub.sh needs them.

docs/textbook-conversion-plan.md gets a superseded note rather than a rewrite; it
is a dated document."
```

---

### Task 5: Create `derived/`

**Files:**
- Create: `derived/README.md`, `derived/blogs/.gitkeep`, `derived/whitepapers/.gitkeep`

**Interfaces:**
- Consumes: nothing.
- Produces: `derived/blogs/` and `derived/whitepapers/`, and the front-matter contract that Plan 3's `tools/check-derived-provenance.sh` parses.

- [ ] **Step 1: Create the directories and the contract**

```bash
cd /Users/david.callaghan/clad
mkdir -p derived/blogs derived/whitepapers
touch derived/blogs/.gitkeep derived/whitepapers/.gitkeep
```

Create `derived/README.md`:

```markdown
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
| `sources` | LaTeX labels from `book/` this work draws on. Use the label, not a chapter number — numbers move, and did move on 2026-09-01 |
| `book_commit` | the book commit this was written against |
| `target` | `medium`, `arxiv`, or `leanpub` |
| `status` | `draft`, `submitted`, or `published` |
| `archive_sources` | optional; paths under `archive/` where a prior version of the argument lives |

`tools/check-derived-provenance.sh` (Plan 3) reports which works predate the current book
and which of their cited sources have changed since. It never fails the build: a blog post
going stale is not a broken build, and a check that blocks on it gets disabled.

## Why labels rather than chapter numbers

On 2026-09-01 the book's parts were reordered and every chapter number changed — the
control model moved from Chapter 5 to Chapter 11, the invariants from 6 to 5. Labels
survived that; numbers would not have. Cite `ch:framework`, never "Chapter 5".
```

- [ ] **Step 2: Verify the labels named in the example exist**

```bash
cd /Users/david.callaghan/clad
for l in ch:canonical-form sec:repeatability-fails ch:framework; do
  printf '%-28s %s\n' "$l" "$(grep -rc "label{$l}" book/chapters/*.tex | grep -v ':0' | head -1)"
done
```

Expected: each label found in exactly one file. If any is missing, correct the README —
a contract whose own example does not resolve teaches the wrong thing.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add derived/ with the provenance contract

Blogs and whitepapers are hand-written — a Medium post is not a chapter with the
LaTeX stripped out — but each records what it came from: the book labels it draws
on, the book commit it was written against, its target and status.

Sources are cited by LaTeX label, never chapter number. On 2026-09-01 the parts
were reordered and every number changed: the control model went from Chapter 5 to
Chapter 11, the invariants from 6 to 5. Labels survived; numbers would not have.

Plan 3 adds check-derived-provenance.sh, which reports staleness and never blocks."
```

---

## What this plan does not do

- **No `sbt` module restructuring.** `app/` is `code/` renamed; the eleven modules and
  their names are untouched.
- **No EPUB pipeline.** Task 4 salvages the assets; `tools/build-epub.sh` is Plan 3.
- **No provenance checker.** Task 5 defines the contract; the script that reads it is Plan 3.
- **No history rewriting.** The stale EPUB and the CC BY corpus remain in public history.
  Both are accepted rather than concealed.
- **The dated specs and the audit plan keep their original paths.** They record what was
  true when written. Only `2026-09-01-ci-enforcement.md` is edited, because it contains
  forward instructions that would otherwise generate a workflow naming a directory that
  no longer exists.
