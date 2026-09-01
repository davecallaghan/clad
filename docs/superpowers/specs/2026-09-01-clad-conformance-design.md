# Three-Way Conformance: book, Lean model, Scala implementation

**Date:** 2026-09-01
**Status:** approved design, pending implementation plan

## Why

CLAD exists as three artifacts that state the same claims in three languages:

- **the book** — prose and theorem statements in `research/*.md` and `book/chapters/*.tex`
- **the Lean model** — 24 files, 69 distinct named theorems and lemmas, zero `sorry`,
  in a sibling repository at `~/prompt_approach/lean`
- **the Scala implementation** — 11 sbt modules, 3,946 lines of main code and 5,874
  lines of test code, in `code/`

Each can be edited without the others noticing, and on 2026-08-31 and 2026-09-01 all
three were found to disagree. The disagreements were not caught by any build, test, or
review process; they were caught by reading.

| claim | book | Lean | Scala | who was right |
|---|---|---|---|---|
| domain of `⊕` | total commutative monoid, corrected to **partial** 2026-09-01 | **total** (`ComponentSpec.compose` is total; `compose_comm` and `compose_assoc` carry no disjointness hypothesis) | **partial** — returns `Either[ComposeError, ComponentSpec]`, `Left` on surface overlap | Scala |
| what makes the audit chain tamper-evident | AI2 hash chain, corrected 2026-09-01 to AI3 signature with AI1 as the control being backstopped | 8 theorems, all over the hash chain | chain check is dead code; the signature does the work | book, after review |
| truncation of the chain | not addressed; **AI5 added** 2026-09-01 | not modelled — no notion of prefix, length, or count | `AuditVerifier.verify` returns `isFullyVerified = true` on a truncated chain | nobody |

The third row is the one that matters, and it is the motivating case for this design.
`AuditChain.lean` holds eight theorems, four of them directly about detecting
tampering: `content_change_detected`, `content_tamper_contradiction`,
`hash_change_breaks_next`, `hash_change_breaks_successor`. All are sound. None of them is about deleting the
tail of the chain, because `chainIntegral` is a predicate over a chain supplied as an
argument, and the model has no notion of a chain having a length. The Scala shares the
blind spot exactly. So did the book, until an adversarial read of the prose found it.

A two-way book-to-code mechanism would have caught none of the three rows. This design
is therefore three-way.

## Goals

1. Make divergence between the three artifacts a build failure rather than a discovery.
2. Close the twelve known gaps, ranked by severity, treating conformance defects and
   live defects as one backlog.
3. Make the Lean model citable in the book — as evidence for the book's thesis, not as
   a warrant for the framework's correctness.

## Non-goals

- Proving the Scala correct. The Lean proofs are about a Lean model; the differential
  test is the only bridge, and it establishes agreement on generated inputs, not
  equivalence.
- Formalising anything not already formalised. Where Lean lacks a model (truncation,
  concurrency, floating point, wall-clock time) this design records the absence rather
  than filling it, except for truncation, which is backlog item 1.
- Restructuring the Scala modules. Improvements are scoped to what the backlog needs.

## Architecture

### The conformance spine

Three extractors, one manifest, one verifier, one sbt task.

```
research/*.md      ──► extract-book.py  ──►  conformance/book.json
book/chapters/*.tex

~/prompt_approach/ ──► extract-lean.py  ──►  conformance/lean.json
  lean/**/*.lean                              (+ pinned commit SHA)

code/**/*.scala    ──► sbt conformanceScan ──► conformance/scala.json
  (@Conforms annotations)

                        join.py ──► conformance/report.md
                                └─► exit status drives `sbt conformance`
```

### Identifiers

Lean theorem names are already stable and already reviewed; the scheme adopts them
rather than inventing a parallel namespace. Where the book names an artifact that Lean
does not, the book's own designation is the identifier.

| kind | identifier form | source of truth |
|---|---|---|
| theorem, lemma, corollary | `THM-1`, `THM-3a`, `LEM-2`, `COR-graceful-degradation` | book designation |
| axiom | `AXM-1` … `AXM-5` | book |
| named requirement | `AI-1` … `AI-6`, `GIL-1` … `GIL-4`, `EA-1` … `EA-4`, `P-1` … `P-3` | book |
| invariant | `INV-1` … `INV-19` | book |
| control surface | `SURF-prompt`, `SURF-output`, `SURF-evidence`, … | book |
| Lean declaration | the Lean name, verbatim | Lean |

Corollaries take slugs rather than numbers: their `N.M` designations were removed from
the book on 2026-08-31 because nothing referenced them and the numbering falsely
implied a chapter.

### Scala annotation

```scala
@Conforms("THM-3a", "AI-3")
object AuditVerifier
```

A retention-`RUNTIME` annotation, harvested by an sbt task. Multiple identifiers per
site are allowed; a single identifier may appear at several sites.

### The Lean repository is a sibling, and must be pinned

`~/prompt_approach/lean` is a separate checkout with no submodule relationship to this
repository. `conformance/lean.json` records the commit SHA it was extracted from, and
`extract-lean.py` fails if the working tree is dirty or the SHA has moved without the
manifest being regenerated. Making it a git submodule is deferred: it is a change to
two repositories' history and is not needed for the mechanism to work.

## What the build does on divergence

Three severities. Failing on everything guarantees the check gets disabled.

| severity | condition | build |
|---|---|---|
| **error** | an identifier in `book.json` with no entry in either `lean.json` or `scala.json`; any `sorry` in the Lean sources; the pinned Lean SHA not matching the working tree | `sbt conformance` fails |
| **warn** | present in two manifests, absent from the third | reported, exit 0 |
| **info** | present in all three, statement shapes not machine-comparable | reported, exit 0 |

Statement-shape comparison is deliberately narrow. It checks the two properties that
actually caused divergence — whether an operation is total or partial, and whether a
predicate ranges over a supplied collection or over a store — and nothing else. A
general prose-to-Lean comparison is not attempted.

CI runs no tests today; `.github/workflows/deploy-landing.yml` deploys the landing page
and nothing else. `sbt test` and `sbt conformance` both join CI as part of this work.

## The backlog

One list, ranked by severity, conformance and live defects together.

| # | item | artifacts | why it ranks here |
|---|---|---|---|
| 1 | **Truncation undetectable.** Persist an external append-only anchor of chain length and head digest; compare in `verify`. Add a length/prefix model to Lean. AI5 is already in the book. | all three | Produces a false clean verdict — `isFullyVerified = true` on a chain missing its tail. The worst failure mode this system can have. |
| 2 | **Differential test cancels instead of running.** Path `../lean/.lake/build/bin/clad-difftest` resolves to `~/lean`, which does not exist; the Lean project is at `~/prompt_approach/lean`. `assume(...)` cancels rather than fails. | Scala, Lean | The only bridge between model and implementation, currently inert. Gates the book change. |
| 3 | **`compose` total in Lean, partial in Scala.** Reconcile Lean to `Either`; the Scala and the corrected book agree. | Lean, book | Two models disagree about their own operator's domain. Gates the book change. |
| 4 | **Concurrent `append` drops records.** `FileChannel.lock()` throws `OverlappingFileLockException` rather than blocking when a lock is held in the same JVM; no caller retries. Add an in-process mutex and retry. | Scala | Silent loss of an audit record under the http4s server. |
| 5 | **Dead chain-integrity check.** `AuditRecord.digest` is a `lazy val` derived from the record's own fields and never persisted, so `recomputeDigest(record) == record.digest` holds by construction. Persist an independent signed digest, or delete the check. | Scala | `chainIntegral` reads `true` on tampered content. |
| 6 | **`ConfigLoader` matches constraints by property name alone.** Key on `(property, level, constraintType)`. | Scala | A project-level override silently inherits the enterprise entry's version and evaluability, reclassifying mechanical as procedural. |
| 7 | **`GhostDetector.detectFromStores` hardcodes `auditedIds` and `degradedIds` to `Set.empty`.** | Scala | Every GIL entry classifies as a ghost. |
| 8 | **Sixth surface absent.** `SURF-evidence` — governability `full` when retrieval-provided, `external` when parametric. | all three | The book's surface partition has six members; `Surface` enumerates five. |
| 9 | **`ComponentSpec` missing `E` and `A`** from `g = (S, C, E, A, R)`. | Scala | Two of five tuple elements unrepresented. |
| 10 | **Risk tiers are `String`.** Typed enum driving fail posture, evaluation timing, audit depth, and item 1's expected component set. | Scala | Tiers govern behaviour in the book and govern nothing in the code. |
| 11 | **`applicable()` absent.** With `scope` predicates and recorded exclusion, per the definition added to the book on 2026-08-31. | Scala, book | The ROC completeness theorem is stated over it. |
| 12 | **`compose` does not subtract mutual provisions** — the book's ⊕ specifies `R_hard(g₁) ∪ R_hard(g₂) \ {mutual provisions}`. | Scala | Divergence from the stated operator. |

## Book-side work

Gated on items 2 and 3. Citing Lean in the book while the differential test does not
run, and while the two `compose` definitions disagree about their domain, would place
an unverifiable claim in a book about unverifiable claims.

1. **New appendix — the Lean model.** What it proves, the commit it was checked at, the
   count (69 distinct theorems and lemmas, zero `sorry`), and, at equal length, what it
   does not model: chain length, concurrency, floating-point arithmetic, wall-clock
   time. The scope statement is the point, not the count.

2. **A passage in the invariants or limitations chapter — the truncation story.** Eight
   machine-checked theorems on the audit chain, four of them squarely about detecting
   tampering, all sound, and none about the attack that matters, because the model had no notion of a chain having a length; the
   implementation shared the blind spot; so did the book's own Theorem 3a until it was
   read adversarially. This is the book's thesis demonstrated on the book's own work,
   with a machine as witness. It also answers, pre-emptively, the formal-methods
   reader's first question.

3. **One factual sentence in the introduction.** Machine-checked proofs of the formal
   results exist, with a pointer to the appendix's scope statement. Not "proven
   correct": a reader will read that as "correct", and Chapters 3 and 4 exist to say
   that inference does not hold.

4. **Cedar in related work.** AWS Cedar used a Lean specification with differential
   testing against a Rust authorization engine — a real industrial precedent for this
   methodology, and the one this project follows.

5. **Correct the README.** It currently claims "mathematically proven correct and
   independently verified against the production code", and 76 theorems. The count is
   69; "proven correct" overstates what proofs of a model establish; and "independently
   verified against the production code" describes a differential test that does not
   currently run.

## Testing

Each backlog item gets a test that fails before the fix. Three absent today:

- **truncation** — append N records, verify, delete the last K lines, verify again,
  assert detection. Neither `AuditVerifierSpec` nor `AppendOnlyFileStoreSpec` exercises
  it; every existing test appends sequentially and reads back whole.
- **concurrent append** — two threads append; assert both records persist.
- **multi-level property** — the same property configured at enterprise and project;
  assert each constraint keeps its own version and evaluability.

The differential test becomes the centre of the conformance suite once it runs: it is
the only check that compares Lean against Scala on generated input rather than on
examples.

## Risks

- **The Lean repository is not under this repository's control.** Pinning detects
  divergence; it does not prevent it. If the Lean project moves or is rewritten, the
  conformance build fails loudly, which is the intended behaviour but will be
  inconvenient.
- **Statement-shape comparison is narrow by design.** It catches total-versus-partial
  and supplied-collection-versus-store. It will not catch a semantic divergence of a
  kind not yet encountered. The mechanism reduces the class of undetectable drift; it
  does not eliminate it.
- **Annotation coverage is a judgement call.** An identifier can be marked conformant
  by an annotation on code that does not in fact implement it. The differential test
  and the per-item tests are what make the annotation more than a label.
