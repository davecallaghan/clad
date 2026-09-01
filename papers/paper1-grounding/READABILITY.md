# Readability and flow assessment

Structural review of the 57-page draft. This is not a review of whether the claims are
true — three passes covered that — but of whether a reader can follow one long argument
from beginning to end without losing the thread.

The short verdict: **the argument is sound and the local prose is fine; the problems are
all structural.** Section lengths vary by a factor of seven, one large digression sits
before the content it digresses from, three definitions arrive before the material that
explains them, and six subsections are a table with a sentence in front of it.

---

## 1. The biggest flow problem: a 752-word digression placed before the content

§3 (*The Systems in Scope*) currently runs:

| Subsection | Words |
|---|---|
| preamble | 48 |
| What is in scope, and what is not | 81 |
| What the exclusion does and does not claim | 352 |
| **Determinism is not the requirement** | **752** |
| What the system computes | 426 |
| The pretraining objective | 418 |
| In-context learning | 218 |
| Instruction tuning and preference optimisation | 463 |
| Three components that are absent | 303 |
| A floor, not merely a gap | 165 |
| What this predicts about better models | 435 |

A reader reaches §3 wanting to know what system is being discussed. They get an 81-word
answer, then **1,100 words of objection-handling** — including the longest subsection in
the paper — before arriving at the architecture. The determinism subsection answers a
question the reader has not yet formed, because it depends on the equivalence-class
argument that does not appear until §7.

**Recommendation.** Move *Determinism is not the requirement* out of §3 entirely. Its
natural home is §9 (*Grounding Is Auditable*), which is where reproducibility as a
measurable property is discussed, or as a subsection of §8 immediately after the
validation-consequence argument that gives it its motivation. Move *What the exclusion
does and does not claim* to the end of §3, after the architecture, where the comparison
with classifiers can be made concretely.

That leaves §3 as: scope → what the system computes → objective → in-context learning →
post-training → what is absent → the floor → what this predicts → and only then the
comparison with other model families. Roughly 2,900 words, in the order a reader would
want them.

## 2. Three definitions arrive before their explanations

**The context tuple.** §4's central definition uses `Applicable(E, c)`, but `c = {j, t_v,
f, u}` is not given until §6.1, two sections later. The reader meets a seven-conjunct
definition containing a term they have been told is "the resolved context of a decision"
and nothing more.

*Recommendation:* move §6.1 (*The resolved context*, 484 words) into §2, which is only
560 words and already has a notation subsection. This fixes the dependency, balances the
paper's thinnest early section, and makes §2 do real work rather than only stating a
premise. §6 then opens directly on illicit universalization, which is its strongest
material.

**The seven dimensions.** Introduced in §4.2, explained in §6.3. The forward reference is
signposted, so this is tolerable — but combined with the context problem it means §4's
definition has two unexplained dependencies at once. Fixing the context tuple reduces
this to one, which is acceptable.

**Invariant I1.** §7 opens by referring to "Invariant I1 of Section 8" — a forward
reference to the paper's most important invariant, from the section that establishes why
it fails. §8 then refers back to §7 for `~_c`. That circularity has to be broken
somewhere.

*Recommendation:* §7 should name the property in its own terms — *form invariance:
materially equivalent requests should produce equivalent decision-relevant outcomes* —
and let §8 formalise it as I1. One sentence changed, circularity gone.

## 3. Six subsections are a table with a sentence in front of it

| Section | Subsection | Prose words |
|---|---|---|
| §4 | Four remedies | **7** |
| §16 | What to report | **9** |
| §12 | The construction | **42** |
| §16 | Repeatability audit | **56** |
| §11 | The flow | **67** |
| §11 | What each intervention buys | **69** |

A subsection heading is a promise that something will be explained. When the body is one
sentence and a table, the heading has spent the reader's attention without repaying it.
Two options per case, and the right one differs:

- **§4 Four remedies** and **§16 What to report** should lose their headings and fold
  into the preceding subsection, with the table left in place. Nothing is missing; the
  heading is the problem.
- **§11 What each intervention buys** deserves elaboration — it is one of the most useful
  tables in the paper and currently arrives unexplained. 150 words on how to read it, and
  on why retrieval appears twice.
- **§11 The flow** should elaborate or fold. The seven-stage sequence is worth a paragraph
  explaining that each arrow is a stopping point and each writes a record — which the
  text says in one sentence and could say properly.
- **§12 The construction** needs 100 words on why *these* fields and not others, or the
  reader takes it as an arbitrary example.
- **§16 Repeatability audit** is thin against its siblings (101, 347, 237 words) and
  reads as an afterthought, when I7 is one of the two invariants the paper says
  invalidates validation. It should be at least 150 words and should state what an
  acceptable repeatability rate looks like, in line with §16.5.

## 4. §4's "Two predicates" is doing too much at 1,060 words

It contains: the assertion definition, the grounded-evidence definition, the grounded-
assertion definition, the justification for `Sensitive`, the justification for
`NoDefeater` in three parts, and the justification for `Sufficient`. That is six distinct
moves under one heading, and the heading says "two predicates."

**Recommendation.** Split into *Assertion* (~200 words), *Grounding* (~400: the two
definitions plus `Sensitive`), and *Why grounding needs a defeater condition* (~450: the
three-part `NoDefeater` argument plus `Sufficient`). The last is substantive enough to be
found in the table of contents, which currently it is not.

## 5. Section-length variance is a factor of seven

3722, 3014, 2130, 1973, 1778, 1679, 1525, 1137, 1136, 954, 884, 690, 648, 600, 572, 563,
538.

The two problems this creates are at the extremes. §3 and §4 together are 6,700 words —
a third of the paper before the reader reaches the independence result. And §5
(*Accuracy and Grounding Are Independent*, 538 words, no subsections) sits between two
sections of 3,000 words each. It reads as an interruption rather than as the pivot it is.

**Recommendation for §5.** It does not need to be longer, but it should look deliberate
rather than truncated. Give it two subsections — *The result* and *What it implies for a
validation programme* — so its structure signals that it is a hinge rather than a
fragment. The content is already there; it is one heading.

## 6. Four sections open cold

§4, §9, §16, and §17 begin without connecting to what preceded them. §4 is the important
one: it opens on "The term *hallucination* is in universal use" immediately after a
section on transformer architecture, with no bridge. §9 opens on "Accuracy and grounding
are both properties of assertions" after a section on invariants.

**Recommendation.** One sentence each. §4 should open from §3's conclusion — the
architecture produces failures of a specific shape, and the received vocabulary
misdescribes them. §9 should open from §8 — the invariants say what does not hold, and
this section says which property can be measured instead. §16 and §17 matter less; a cold
open on a protocol section and a conventional one on related work are both normal.

## 7. §11's subsections are in the wrong order

Current: preamble → Holodeck → Eight components → The flow → Structured evidence → What
this costs → Retention → **What this does not claim** → **What each intervention buys**.

The caveat subsection sits *before* substantive content. Move *What each intervention
buys* up to follow *Eight components*, where it belongs, and leave *What this does not
claim* last.

---

## Recommended order of work

1. **Move the determinism subsection out of §3** (finding 1). Largest single improvement
   to the reader's experience, and it is a cut-and-paste.
2. **Move the resolved context into §2** (finding 2). Fixes a real dependency and
   balances the paper's thinnest section.
3. **Break the §7/§8 circularity** (finding 2). One sentence.
4. **Split §4's "Two predicates"** (finding 4) and **reorder §11** (finding 7). Both are
   heading changes.
5. **Elaborate the four table-stubs that need it, fold the two that do not** (finding 3).
   Roughly 550 new words in total.
6. **Add the four opening sentences** (finding 6) and **give §5 two subsections**
   (finding 5).

Items 1–4 are rearrangement and add no length. Item 5 adds about 550 words, item 6 about
60. The net is roughly +600 words against a 19,000-word paper, in exchange for removing
every structural obstacle I can find.

## A note on the terseness pass you have planned

Do the structural work first, then the duplication pass — in that order, not the reverse.
Rearrangement changes what reads as duplication: several repetitions exist only because
material is separated from the argument that motivates it, and moving the material makes
the repetition unnecessary rather than merely redundant. Two examples I can already see:
the measurability asymmetry is stated in §9.4, §11.5, and §13.3, and the
"appearance versus relation" point about post-training appears in §3.4 and again in §11.
Both are candidates for consolidation, and where they should consolidate *to* depends on
decisions 1 and 2 above.
