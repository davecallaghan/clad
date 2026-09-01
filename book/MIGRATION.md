# Migration status

One corpus. The governance framework and its epistemic foundation are the same body of
work; this directory is the single document.

## What is here

| Path | Status |
|---|---|
| `notation.tex` | **The single authoritative symbol table.** 69 macros. Add symbols here first. |
| `NOTATION-MAP.md` | Collision resolutions and the markdown→LaTeX substitution table |
| `main.tex` | Book skeleton, five parts, two appendices |
| `chapters/` | 17 chapters, converted from the epistemic corpus, on the shared notation |
| `appendices/` | Worked case; benchmark corpus and case design |
| `refs.bib` | 24 entries. Verification markers `[V]`/`[H]`/`[?]` per entry |

## What is not here yet

The governance corpus is still markdown in `../research/` and converts chapter by
chapter using the substitution table in `NOTATION-MAP.md`.

| Source | Becomes | Notes |
|---|---|---|
| `research/meta-framework.md` | Part III — the control model | Its surface partition is where the evidence-gap argument lands. Amending Axiom 1 is the substantive edit, not a rename. |
| `research/wp1-enterprise-prompt-governance.md` | Part IV — prompt governance | Largest file. Deontic `O`/`F` → `\Require`/`\Forbid` throughout. |
| `research/wp2-runtime-output-controls.md` | Part IV — output controls | |
| `research/sa-monitoring-detection-response.md` | Part IV — monitoring | |
| `research/regulatory-mapping-appendix.md` | Part V — regulatory crosswalk | |
| `research/appendix-a-formal-model.md` | Appendix — formal model | Merge with the epistemic definitions; this is where the two corpora actually join. |
| `research/appendix-b-worked-examples.md` | Appendix — worked examples | See the note below. |
| `research/appendix-c-templates.md` | Appendix — templates | |
| `research/glossary.md` | Back matter | Reconcile against `notation.tex`. |

Nothing has been deleted. `research/` and `papers/paper1-grounding/` both remain intact
until their content is converted and checked.

## Two substantive edits that are not renames

**1. Axiom 1 is incomplete for retrieval-augmented systems.** The meta-framework's
surface partition — `σ_prompt, σ_input, σ_config, σ_output, σ_delivery` — is declared
exhaustive, and Theorem 1 proves it over the interaction tuple `(x, u, M, θ, o)`. The
theorem is true. The tuple is incomplete: evidence is not in it, and retrieval is
classified Tier 2, *known but unmodeled*. The evidence layer is therefore a sixth
surface, `σ_evidence`, and adding it requires amending the axiom rather than extending
the partition. Confronting this directly is a stronger contribution than presenting the
epistemic material as a parallel framework.

**2. `applicable(·, i)` is used in a theorem and never defined.** The Output Evaluation
Completeness theorem asserts the audit record is "total over `applicable(R_ROC, i)`."
`applicable` appears four times in the formal model and has no definition anywhere in
the corpus. The epistemic corpus defines a function of exactly that shape —
`\Applicable(E, c)` with `c = \{j, t_v, f, u\}` — over evidence rather than over
constraints. Supplying the definition closes the hole.

## One finding worth carrying into the merge

`research/appendix-b-worked-examples.md`, in the financial-analysis scenario:

> **Scenario: Missing citation.** Model produces quantitative claims without citing data
> sources. Citation check (O_d) fails. Decision: BLOCK + retry… **Retry succeeds — model
> produces the same analysis with source citations. Second evaluation: PASS.**

The analysis is unchanged. Citations appeared. Nothing checked whether they support the
analysis. This is the *citation is not entailment* failure, in the governance corpus's own
worked example, scored as a success. It is the clearest single motivation for the evidence
layer, and it belongs in the introduction rather than buried in an appendix.

## Implementation

`../app/` implements a fraction of the governance corpus and none of the epistemic
corpus. Deliberately out of scope for now: the argument comes first. Recorded here so the
gap is not rediscovered later — output governance is wired to an empty constraint set,
`AuditRecord.linked` is never called from a production path, there are no
`OutputClassifier` implementations in production source, and there is no `Source` type,
provenance field, or evidence relation anywhere.
