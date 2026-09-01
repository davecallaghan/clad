# Overlap analysis: the grounding paper against CLAD

Written to be antagonistic. The question asked was whether the paper duplicates
pre-existing work. The answer is not the comfortable one in either direction.

**Verdict in one line:** the paper and CLAD are **orthogonal on their central claims
and badly redundant on their supporting machinery** — and where they overlap, CLAD is
the stronger document, while the paper does not cite it.

---

## 1. The layers do not overlap. This is verified, not asserted.

CLAD's governed pipeline is `user input → EPG → model → ROC → MDR → delivered output`.
Its control surfaces are declared exhaustive and disjoint:

    S_prompt = {p}   S_input = {u}   S_config = {m,θ}   S_output = {o}   S_delivery = {o'}

There is no surface for **sources**. Theorem 1 (Surface Completeness) proves every
interaction element is assigned to exactly one of those five, which means an evidence
layer is not an extension of CLAD's model — it is a sixth surface the theorem asserts is
unnecessary.

The exclusion is deliberate and repeated. Retrieval is classified Tier 2, *known but
unmodeled*: "RAG index state and ranking parameters… contribute documented ungoverned
risk." And the decisive sentence, `wp1:140`:

> EPG's evaluation engine operates on assembled prompts, **not on the source materials
> that contributed to those prompts**… The composition process is invisible to the
> evaluation layer.

Nine audits of the corpus returned NO on every evidence-side concept: no retrieval
layer, no grounding relation, no source provenance or lineage, no source authority, no
applicability of a source to a case on any of jurisdiction / valid date / population /
intended use, no treatment of factual accuracy, no bitemporality over evidence, and no
notion of conflicting *evidence*. "Hallucination" appears **once in the entire corpus**,
as an exclusion.

CLAD says so itself, in `appendix-a:302`:

> It does not guarantee properties of the governed artifacts beyond what the constraints
> specify — **content correctness**, semantic completeness, or model behavior are
> **outside scope**.

The paper is entirely about content correctness's evidentiary precondition. So on the
central claim: **no overlap. Complementary layers.**

## 2. The supporting machinery overlaps badly, and the paper loses

Of the paper's nineteen invariants, **nine already exist in CLAD, several as theorems**:

| Paper | CLAD | Which is stronger |
|---|---|---|
| I4 Consistency | `CONTRADICTION(c₁,c₂) ≡ O(φ) ∧ F(φ)`, pairwise-complete for the atomic fragment | **CLAD** — has a completeness argument |
| I7 Repeatability | THEOREM Deterministic Evaluation Reliability | **CLAD** — theorem vs table row |
| I9 Temporal validity | `ver(c,t)`, dual version stamps (Issue M3) | CLAD, for constraints |
| I12 Traceability | `audit(p,t)`, `A_ROC` schema | **CLAD** — richer schema |
| I14 Reproducibility | THEOREM Audit Completeness (total, deterministic, immutable) | **CLAD** — theorem |
| I15 Authorization / SoD | RBAC scope predicate, `author(c) ≠ approver(c)`, cross-org-unit approval for Critical, break-glass TTL bounds | **CLAD, overwhelmingly** — the paper has one line |
| I16 Reconstructability | audit immutability + predecessor pointers | CLAD |
| I17 Controlled failure | BLOCK / FLAG / BELOW_THRESHOLD, short-circuit, tier-dependent delivery | **CLAD** — operationally specified |
| I19 Defeater search | `precedence : C × C → {c₁_wins, c₂_wins, unresolved}`, `default_priority : D → ℕ` | **CLAD** |

I19 is the embarrassing one. It was added to the paper as a novel contribution two
revisions ago. CLAD's `unresolved` **is** the paper's `Contested`; CLAD's precedence table
**is** the paper's standing ordering; and CLAD additionally has a runtime resolution
protocol with a named default ordering (Safety > Legal > Security > Compliance > Business)
and tier-differentiated rules on whether unresolved tension may be accepted at all.

Same for reliance tiers. The paper has a six-row table with five dimensions named in
prose. CLAD has Critical/Standard/Low varying by fail posture, evaluation timing,
streaming permission, sampling rate, threshold conservatism, approval separation,
verification cadence, and — crucially — **control type**, with the honest observation that
"only Critical tier provides true preventative controls."

CLAD also has one construct the paper lacks entirely and needs: the evaluability
partition `C = C_m ⊎ C_p` (mechanically evaluable vs procedurally evidenced) with a
theorem that there is no evaluability gap. The paper gestures at deterministic-versus-
model-based verification and never formalises it.

## 3. Seven of nineteen are genuinely the paper's own

Absent from CLAD in any form: **I1** form invariance, **I2** closure, **I3**
decomposition, **I5** self-report fidelity, **I10** context binding, **I11**
applicability, **I18** sufficiency. Plus, at the definitional level: `Supports`,
`Sufficient`, `Sensitive`, the six-category taxonomy, and the non-identification result.

I10 and I11 are the load-bearing ones, and they are where the paper earns its existence.

## 4. The synergy, stated precisely rather than warmly

Three specific hooks, not a general feeling of compatibility.

**(a) CLAD has an undefined function that the paper defines.** `applicable(·, i)` is used
four times in CLAD's formal model — including inside the Output Evaluation Completeness
theorem, which asserts the audit record is "total over `applicable(C_ROC, i)`" — and is
**never defined anywhere in the corpus**. A theorem whose scope is a placeholder. The
paper's `Applicable(E, c)` with `c = {j, t_v, f, u}` is a definition of exactly that
shape, over evidence rather than constraints. CLAD needs the definition; the paper has
one.

**(b) CLAD's own worked example fails the paper's test and is marked PASS.**
`appendix-b:135`:

> **Scenario: Missing citation.** Model produces quantitative claims without citing data
> sources. Citation check (O_d) fails. Decision: BLOCK + retry… **Retry succeeds — model
> produces the same analysis with source citations. Second evaluation: PASS.**

The analysis is unchanged. Citations appeared. Nothing checked whether they support the
analysis. This is the paper's *citation is not entailment* claim, instantiated in CLAD's
documentation, scored as success. It is the single best piece of evidence either document
has for the other's relevance — and it is evidence **against CLAD's completeness**, which
is why it counts.

**(c) CLAD's M3 is the paper's bitemporality, one object away.** Open Issue M3 requires
version stamps at prompt assembly `t₁` and output delivery `t₂` and flags the
discrepancy. That is the same machinery the paper applies to `t_k` versus `t_v`. CLAD
applies it to constraint versions; the paper applies it to evidence validity. Note CLAD's
temporal model is **single-axis** — no `valid time` / `transaction time` anywhere in the
corpus — so this is genuinely something the paper would contribute.

## 5. Two findings that damage both documents

**The notation is unmergeable.** Eight of thirteen shared symbols collide hard:

| Symbol | CLAD | Paper |
|---|---|---|
| `O` | obligatory (deontic) | assertion operator |
| `P` / `p` | set of all prompts | a proposition |
| `C` / `c` | operational constraints | resolved context |
| `A` | agents / audit record | accuracy indicator |
| `γ` | governability class | target grounding rate |
| `S` | control surface | the deployed system |
| `V` | audit records | token vocabulary |
| `D` | governance domains | defeater search |

`O` is the worst: CLAD's deontic obligation against the paper's assertion operator, in a
repository that contains both.

**The paper does not cite CLAD.** Zero mentions across 67 pages and 24 references. And
§14 of the paper says assurance-case practice "supplies the shape of" its invariant
table — which is the author's own framework, in the same repository, with theorems. A
reviewer who finds CLAD will ask why.

## 6. The harsh part: CLAD cannot host this layer today

The implementation is **3,139 lines of main source against 6,259 lines of tests, 489
tests passing, no `???` or TODO markers** — and the parts that would matter are unwired.
Verified directly:

- Both the REST and MCP output-governance endpoints call
  `OutputEvaluator.evaluate(artifact, OutputConstraintSet(), …)` — an **empty** constraint
  set. Output governance structurally always returns Pass.
- **Zero `OutputClassifier` implementations exist in production source.** The only one is
  a test fixture returning a constant and ignoring its input.
- `GovernanceEngine.scala:60` builds every audit record with `previousDigest = None`, and
  `AuditRecord.linked` — the chaining function — is **never called from any production
  path**. The chain machinery is real, tested, and unreachable.
- `GhostDetector.detectFromStores` reads the audit store and then discards it
  (`auditedIds = Set.empty`), so every logged interaction is classified a ghost. That is a
  live bug, not a stub.
- Config parses `outputConstraints`, `thresholds`, `failurePosture`, and `domain`, then
  silently drops all four.

Searches for `retriev|embed|vector|corpus|citation|document|source` across production
source return the Scala `Vector` type and two metadata-key names. There is no `Source`
type, no provenance fields, no `supports` relation, no jurisdiction, no effective date, no
validity interval anywhere. `ProceduralEvidence` — the only thing called "evidence" — is
`(propertyId, attestor: String, satisfied: Boolean, attestedAt, rationale: String)`: a
human's boolean with a free-text note, copied straight through by the evaluator without
verification.

And the semantic core is thinner than the formal model implies. A `Constraint` is a
property name and a level — no text, no scope, no rule body. Nine of the twelve shipped
checkers are metadata-key lookups, so the governed system attests to its own compliance
and the framework records the attestation. `decision_explainability` fires on the presence
of two hedging phrases from `{"because", "based on", "reasoning", "rationale", "evidence
suggests"}`.

**So the implementation is a procedural-conformance ledger, not a governance engine**, and
building the paper's layer on it means building from nothing — there is no partial
implementation to extend and, given a `Constraint` with no domain field and no
`Source` type, the type system would have to change first.

## 7. What I would actually do

Ranked, and none of it is "merge them."

1. **Cite CLAD in the paper and drop the redundant nine.** Replace I4, I7, I9, I12, I14,
   I15, I16, I17, I19 with a citation and a sentence: these conditions are established in
   prior work; this paper adds the evidence-side conditions that work explicitly scopes
   out. The paper gets shorter, more honest, and stops competing with its author.
2. **Reposition the paper as CLAD's sixth surface** — `S_evidence` — and confront Theorem
   1 head-on rather than quietly contradicting it. That is a genuinely interesting result:
   the surface partition is exhaustive over Axiom 1's tuple `(p,u,m,θ,o)`, and evidence is
   not in the tuple, so the theorem is true and the axiom is incomplete for
   retrieval-augmented systems. **That is a stronger contribution than anything currently
   in the paper's related-work section**, and it is a critique of CLAD from inside CLAD's
   own formalism.
3. **Give CLAD its missing `applicable()` definition** and close the hole in the Output
   Evaluation Completeness theorem.
4. **Fix the notation before either document is published.** Eight hard collisions in one
   repository is not survivable.
5. **Do not claim the implementation supports any of this.** It does not support CLAD's own
   flagship guarantees today.

## 8. The answer to the question as asked

You said you did not need these to overlap. On the thing each is actually about, **they do
not** — and the non-overlap is verified against nine independent probes of the corpus plus
CLAD's own repeated scope disclaimers, not inferred from a skim.

But you have been writing the same governance machinery twice, and the older version is
better. The paper's distinctive contribution is roughly **seven of its nineteen
invariants plus four definitions and one proposition** — which is a real paper, and about
a third of the document currently claiming to be one.
