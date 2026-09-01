# Adversarial review, third pass

Same five personas. This pass was run against the prediction registered before it
started: **materially fewer findings, and 0–2 at F-level.** The result is six findings,
two at F-level. Provenance is recorded for each, because that was the point of the
exercise.

Severity: **F** = false or internally inconsistent. **O** = overclaimed. **M** = minor.

| # | Persona | Finding | Sev | Provenance |
|---|---|---|---|---|
| 1 | ML / NLP | The ablation test for `Sensitive` is confounded by the paper's own form-sensitivity argument | **F** | **Missed by RT1 and RT2** |
| 2 | Data | Two stale "six dimensions" references survive the seven-conjunct change | **F** | Introduced by RT2 repairs |
| 3 | Data | The relation between `α(E,c)` and `Authoritative(E,c)` is never stated | **O** | Introduced by RT2 repairs |
| 4 | Systems | "Every regulated firm has an accepted error rate" | **O** | Introduced by RT2 repairs |
| 5 | ML | Verification cost given as `O(mk)` where it is an upper bound | **O** | Introduced by RT2 repairs |
| 6 | NLP | `undermines(E',E,p,c)` appears in a numbered definition without being defined | **M** | Introduced by RT2 repairs |

---

## 1. The one finding both earlier passes missed — **F**

> Your test for `Sensitive` is ablation: remove or contradict the putatively supporting
> evidence and see whether the assertion survives. Now apply your own Section~7 to that
> test. Removing a passage changes the token sequence. You have spent a section arguing
> that this system's behaviour is sensitive to the form of its input independently of
> content — that materially equivalent inputs produce divergent outputs. So when the
> assertion changes after ablation, you cannot attribute the change to loss of evidential
> support rather than to the perturbation of the input. **Your central argument defeats
> your central measurement.**

**This lands, and it is the finding that justifies a third pass.** It is not a
restatement of anything in RT1 or RT2; both passes examined `Sensitive` and neither
noticed that the ablation procedure is confounded by the very phenomenon the paper
establishes. It bears on the paper's headline measurement, so it is F-level rather than
merely awkward.

The confound is real in both directions. Deleting a passage shortens the context and
changes position encodings for everything after it, so a changed assertion may reflect
form rather than evidence. And an unchanged assertion is not clean either: the model may
be reproducing the answer from parametric memory, which is precisely the case `Sensitive`
exists to exclude.

**Repair, and it is a genuine methodological requirement rather than a caveat.** The
ablation must be *form-controlled*:

1. **Substitute rather than delete.** Replace the passage with a length-matched,
   structurally similar passage on an unrelated topic, so the token sequence is perturbed
   comparably without the evidence being present.
2. **Establish a null-perturbation baseline.** Substitute an *equally supporting*
   passage. If the assertion changes under that too, the measurement has no power for
   this item and it should be discarded rather than scored.
3. **Report the contrast, not the raw flip rate.** The quantity of interest is the
   difference between the change rate under evidence-removing substitution and the change
   rate under evidence-preserving substitution. A flip rate without the baseline is
   uninterpretable.
4. **Prefer contradiction over removal where possible.** Replacing the passage with one
   asserting the contrary keeps length and structure closest while inverting the
   evidential content.

This also sharpens the paper's own position. Section~9 claims grounding is measurable at
runtime; the honest version is that grounding is measurable at runtime *with a controlled
perturbation design*, and a naive ablation is not a measurement. That is a real
strengthening, because it is the kind of detail a validation function would otherwise
discover on its own and hold against the paper.

---

## 2. Stale dimension counts — **F**

> Section~6.5 evaluates retrieval "against the six dimensions" and then concludes that
> standard retrieval "supplies one of six dimensions reliably." Your definition has seven
> conjuncts. You changed the definition and updated some prose.

**Lands.** Two occurrences in `grounding-context.tex`. Internally inconsistent, and in
the section whose job is to enumerate the dimensions.

**Repair.** Both to seven, and check that the retrieval assessment actually accounts for
`Sufficient` rather than only being renumbered — retrieval does not supply coverage
either, so the count of what it fails to supply should rise.

## 3. `α` and `Authoritative` — **O**

> You now have `Authoritative(E,c)` as a conjunct of `Grounded` and `α(E,c)` as a
> standing level in the defeater search. Are these the same notion at different
> resolutions, or two things? If `Authoritative` is `α` above a threshold, say so. If
> not, explain what a source can be that is authoritative but has no standing level.

**Lands.** This is exactly the objection RT2 raised about `Supports` and `Sufficient`,
which was repaired — and then reintroduced in the same edit, one conjunct over.

**Repair.** One sentence: `α(E,c)` is the standing of $E$ for decision class $c$ in the
institution's precedence order, and `Authoritative(E,c)` holds iff `α(E,c)` meets the
minimum standing that class requires. Threshold and ordering, one notion.

## 4. "Every regulated firm" — **O**

> Section~15.4 asserts that every regulated firm has an accepted error rate for the
> equivalent manual process. Many have sampling standards; rather fewer have an explicit
> tolerance for *evidentiary* quality, which is what you are anchoring to.

**Lands.** Written in the last pass, and the same species the previous two passes were
meant to eliminate.

**Repair.** "Most regulated firms have an accepted standard for the equivalent manual
process — a sampling threshold, a quality-assurance tolerance, an audit finding trigger —
and where one exists it is the right anchor." Then add the case that follows: where none
exists, the exercise of deriving one is itself worth doing, and it will be asked for
eventually.

## 5. `O(mk)` is an upper bound — **O**

> Verification is `O(mk)` only if every claim is checked against every candidate source.
> A system that verifies each claim against its cited source is `O(m)`.

**Lands, minor but it is the number an architect will plan against.** Change to "up to
`O(mk)`, and `O(m)` where verification is against the cited source only" — and note that
the difference is a design choice with an assurance consequence, since checking only the
cited source cannot find a defeater.

## 6. `undermines` is undefined — **M**

It appears inside numbered equation \eqref{eq:defeatersearch} as `\text{undermines}`,
glossed in the following prose but never defined. Either define it as a relation
(evidence that removes $E$ from scope, supersedes it, or invalidates the extraction), or
move it out of the equation into the prose and keep the formal statement to rebutting
defeat only. I would define it, since undercutting defeat is doing real work.

---

## On the question you actually asked

Three passes, same method, same reviewer:

| | Findings | F-level | Paper length |
|---|---|---|---|
| RT1 | 16 | 2 | 36 pp |
| RT2 | 14 | 5 | 53 pp |
| RT3 | **6** | **2** | 57 pp |

The prediction was fewer findings and 0–2 at F-level. That is what happened, and the
composition matters more than the count: **five of six findings this pass are defects the
previous round of repairs introduced**, and four of those are bookkeeping — a stale
count, an unstated relation, an overclaimed quantifier, a loose complexity bound. Only
one finding is a genuine discovery in unchanged material, and it is a good one.

So on your hypothesis: I do not think these findings are an artifact of asking a
generative system for a list. If they were, this pass would have produced fourteen again,
and the F-level items would be in text nobody had touched. Instead the count fell by more
than half, the F-level count returned to its starting level, and the residue is dominated
by the specific, predictable failure mode RT2 named — *adding a condition to a definition
creates obligations elsewhere that the same edit does not discharge.* That is a
convergent process with a diagnosable error mechanism, not a generator.

Two honest qualifications against my own conclusion.

**The reviewer is not independent.** I wrote most of the text I am reviewing, and I
carry the same blind spots into the review that I carried into the writing. Finding 1 is
the evidence for this: it survived two passes because it requires holding Section 7
against Section 4, and I had reasons to look at each separately. A reviewer who did not
write the paper would have found it sooner, and there are plausibly more of that kind.
**Diminishing findings from this reviewer is weaker evidence than diminishing findings
from a different one.**

**Each pass makes the paper longer.** 36 to 53 to 57 pages. Every repair adds
qualification, and RT2's structural finding — that scope is now the largest risk to
acceptance — has been made worse by the process of addressing everything else. That is a
real cost and it is not visible in a findings count. If there is a fourth pass, the most
valuable thing it could do is subtract.

