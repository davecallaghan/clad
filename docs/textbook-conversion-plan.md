# Textbook Conversion Plan — _Trust by Design_

Turn the book from a stitched-together set of white papers into a single
textbook that reads coherently end to end.

**Decisions (locked):**
1. **Editing depth:** rewrite connective tissue (intros, bridges, cross-refs) for
   one voice; keep all technical content — axioms, theorem statements, proofs,
   tables, code, worked examples, numbers — **verbatim**.
2. **Regulatory crosswalk:** stays as the final narrative chapter (Part III), not
   an appendix.
3. **Front matter:** a new Preface opens the EPUB; `README.md` stays as-is for
   GitHub. The book and repo front doors are decoupled.

## Target structure

```
FRONT MATTER
  Title page (metadata-driven; done)
  Preface                         research/00-preface.md         (new)
  Notation & Conventions          research/01-notation.md        (new; symbols once)

PART I — FOUNDATIONS
  1. The Architecture of Assurance research/meta-framework.md
PART II — THE THREE CONTROL LAYERS
  2. Responsible Prompting (EPG)   research/wp1-enterprise-prompt-governance.md
  3. Stopping Bad Outputs (ROC)    research/wp2-runtime-output-controls.md
  4. Seeing the Whole Picture (MDR) research/sa-monitoring-detection-response.md
PART III — ASSURANCE IN PRACTICE
  5. Meeting Regulators            research/regulatory-mapping-appendix.md

BACK MATTER (one appendix set, consolidated from six in-chapter appendices)
  Appendix A. Formal Model & Theorems   research/appendix-a-formal-model.md
  Appendix B. Worked Examples            research/appendix-b-worked-examples.md
  Appendix C. Templates & Classifier Specs research/appendix-c-templates.md
  Glossary & Notation                    research/glossary.md
```

Part dividers can be `#`-level headings inside the first chapter of each part, or
short standalone files, depending on how they render in the EPUB (decide in
Phase 1).

## Global conventions

- **Voice:** first-person-plural expository ("we"), present tense, consistent
  terminology. Remove "this white paper / this document / companion paper."
- **Chapter shell** (replaces per-chapter Executive Summaries):
  - *Opener:* 2–3 sentence bridge from the previous chapter + a short "What
    you'll be able to do" list.
  - *Body:* unchanged technical content.
  - *Closer:* "Key takeaways" + one-line pointer to the next chapter.
- **Cross-references:** `WP1/WP2/SA` → "Chapter 2 (EPG)", "Chapter 3 (ROC)",
  "Chapter 4 (MDR)"; sections as "§3.2"; appendices as "Appendix A.3". Introduce
  each component's full name once, then use the abbreviation.
- **Remove:** metadata blocks (Version/Date/Audience/Relationship/Scope), origin
  labels ("formerly White Paper N"), `## DESIGN REQUIREMENT:` and `## Open
  Issues` headings, `research-log.md` pointer.
- **De-duplicate** the validation-status disclaimer into the Preface; leave a
  one-line contextual caveat where a chapter genuinely needs it.

## Content-preservation contract

Only the following may change: connective prose, headings/numbering, metadata,
cross-references, and section relocation. The following must be preserved
byte-for-byte: axiom text, definitions, lemma/theorem **statements**, proofs,
tables, code blocks, worked-example inputs/outputs, and all numeric values.
Each phase verifies this with a diff that isolates technical blocks.

## Phased execution (one PR, reviewable per phase)

- **Phase 0 — scaffold.** Branch `feat/book/textbook-rewrite`. Add this plan.
  Add Preface + Notation stubs. Wire `SUMMARY.md` and `scripts/build-book.sh`
  FILES to the new order (Preface first, README dropped from the book). Rebuild
  EPUB; confirm TOC.
- **Phase 1 — Part I (Ch 1).** Meta-framework: strip metadata/`research-log`;
  fold `DESIGN REQUIREMENT` blocks into neighboring numbered sections; move the
  chapter-local Formal Glossary to back-matter Glossary; move `Open Issues` out
  of the book; add opener/closer; add Part I divider. Rebuild.
- **Phase 2 — Part II (Ch 2–4).** EPG, ROC, MDR: delete metadata blocks; convert
  Executive Summary / Purpose sections into chapter openers; distill the
  "Connection to…" sections into opener/closer bridges; normalize cross-refs;
  **relocate** the six Appendix A/B/C sections to back matter. Rebuild.
- **Phase 3 — Part III (Ch 5).** Crosswalk: reframe from appendix to chapter;
  keep tables and the chapter-specific disclaimer; add opener/closer. Rebuild.
- **Phase 4 — Back matter.** Assemble consolidated Appendix A/B/C and Glossary
  from the relocated material; add brief connective headnotes. Rebuild.
- **Phase 5 — Pass for cohesion.** Read end-to-end; fix seams, duplicate
  definitions, dangling cross-refs; verify every `§`/`Chapter`/`Appendix`
  reference resolves. Final EPUB build; open PR.

## Verification per phase

- `./scripts/build-book.sh` exits 0 and the EPUB TOC matches the target order.
- Grep gates: zero `WP1|WP2|\bSA\b|this white paper|this document|Version:` in
  chapter bodies; zero broken internal references.
- Technical-content diff: theorem/table/code blocks unchanged from the pre-rewrite
  revision.

## Out of scope

Repo `README.md`, the landing page, code, the Lean proofs, and the GCP tooling.
This is a docs-only, additive-then-restructuring change to `research/` + the book
build wiring.
