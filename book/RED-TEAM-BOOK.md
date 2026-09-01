# Red Team — the book (first pass over the CLAD half)

The paper's three red-team passes covered only the grounding material, now
Chapters 1–4 and 10–13. The CLAD material — Chapter 5 (meta-framework),
Chapters 7–9 (EPG/ROC/MDR) and Appendices A–C — has never been through one.
That is where every finding below sits, which is what you would expect: it is
the half that carries sixteen theorems and has not been attacked.

Reviewed as: NLP/ML engineer, security architect, database architect,
formal-methods reader, and regulator's auditor.

---

## C1 — `p` means two different things in one book (CRITICAL)

`notation.tex:25` states the resolution explicitly:

> `P, p` set of prompts | a proposition → **prompts become x in X. p, q keep
> propositions.**

The resolution was decided, recorded, and never applied to the CLAD half.

- Chapter 3 (paper half): "…assertion $p$, and resolved context $c$" — `p` is a
  proposition, the thing whose grounding is at issue.
- Chapter 5 (CLAD half): "The assembled prompt p is the complete input to the
  model", "p precedes u (prompt exists before user input)" — `p` is the prompt.

The interaction tuple is written both ways: `(x, u, M, θ, o)` six times in the
paper half and §15, `(p, u, m, θ, o)` seven times in the CLAD half and the
glossary. `m` is the model identifier in Chapter 5; `M` is the model as a
parameterised generative function in Chapter 2.

This is the framework's most fundamental object, written two ways, with one
spelling colliding with a symbol the notation table reserves. An auditor reading
straight through hits `p` in Chapter 5 having learned it in Chapter 3.

**Fix:** apply the recorded resolution to the CLAD half — `p→x`, `m→M`, and the
tuple to `(x, u, M, θ, o)`. Mechanical, but it touches Chapter 5 densely.

---

## M1 — Theorem 3a's premises contradict each other (MAJOR)

> **Theorem 3a (Tamper-Evident Audit Chain).** Given properties AI1–AI4, any
> modification to any audit record in chain(i) is detectable…
> *Proof.* Suppose record A_gₖ is modified after writing… By AI1, A_g(k+1)
> cannot be modified to match.

AI1 is *Immutable Storage*: records "cannot be modified or deleted by any
party." So the theorem assumes AI1, supposes a modification AI1 forbids, and
then invokes AI1 to stop the attacker making a second modification. Either the
attacker can modify records or they cannot; the proof needs both.

Under AI1 the theorem is vacuous. What it should say is the useful thing: *if
immutability is bypassed for some record, AI2's chain and AI3's signature still
reveal it.* Condition it on AI2–AI4 and state AI1 as the control being
backstopped, not as a premise.

An auditor who reads AI1 as a premise will ask what happens when the WORM bucket
is misconfigured — the exact scenario the theorem should be about.

---

## M2 — Theorem 3b's last sentence contradicts its own premise and EA3 (MAJOR)

> Every interaction **that enters the governance pipeline** is either (a)… (b)…
> (c)…
>
> **No interaction can be silently ungoverned.**

The enumeration is correctly scoped; the closing sentence is not, and it is the
sentence a reader will quote. GIL1 quantifies over `I_governed`. An interaction
that never reaches the EA1 chokepoint — a developer's own API key, a local
deployment — gets no GIL entry and no chain record, so GIL3's comparison of GIL
entries against chain records cannot see it. Absent from both sets, it is
invisible to the mechanism the theorem relies on.

The framework already says the honest thing three pages later, in EA3:

> `∀ i ∈ I_ungoverned : P(detect(i)) ≥ detection_threshold (deployment-specific)`

That is a probabilistic detection target requiring network egress controls and
credential management. Theorem 3b's closing sentence claims a guarantee EA3
explicitly declines to make.

**Fix:** "No interaction that enters the pipeline can be silently ungoverned,"
and a forward pointer to EA3 for everything outside it.

---

## M3 — `(G_abstract, ⊕)` is not a monoid (MAJOR)

Theorem 6 claims closure, associativity, identity and commutativity. The
identity is defined (`g_∅`), so that part is sound. Two problems remain.

**⊕ is partial.** Lemma 3 opens "Given components with non-overlapping surfaces
(S_g₁ ∩ S_g₂ = ∅)". Non-overlap is a relation between pairs, not a property of
an element, so "the set of components with non-overlapping surfaces" does not
pick out a set closed under ⊕. Concretely: `g₁ ⊕ g₂` governs `S₁ ∪ S₂`, so
`(g₁ ⊕ g₂) ⊕ g₁` has overlapping surfaces and is undefined. Closure fails. The
structure is a *partial* commutative monoid — a real and useful structure, but a
different one, and the phased-deployment conclusion follows from Lemma 1 alone
without needing the monoid claim at all.

**⊕ is not a binary operation on components.** The definition sets
`A = compose(A_g₁, A_g₂) via shared interaction_id`, and the associativity proof
defends itself with "chain order is determined by pipeline causality, not
composition order." That concedes the point: `compose` depends on pipeline
causality, a parameter outside both operands. A hash chain is order-dependent by
construction (AI2: `hash(A_g(k-1).chain_hash ∥ content)`), so `compose(A₁, A₂)`
and `compose(A₂, A₁)` differ at the byte level. Commutativity holds for surface
and constraint unions; it does not hold for the audit component as defined.

Also: Lemma 3(b) proves commutativity by invoking Axiom 3 (guarantee
independence). Tuple equality follows from union commutativity; guarantee
preservation is a different claim. The proof conflates them.

**Fix:** downgrade to "partial commutative monoid", restrict the claim to the
`(S, C, E)` components of the tuple, and state audit composition as
causality-ordered rather than commutative. Nothing else depends on Theorem 6.

---

## m1 — "AI governance can be made *provable*" (MODERATE)

Chapter 5 opens with it. Chapters 1–4 spend sixty pages arguing that
confident-sounding assurance about LLM systems is the problem, and that the
reason to prefer grounding over accuracy is precisely that accuracy invites
claims the architecture cannot support.

What is actually proven is *audit completeness and tamper-evidence, conditional
on roughly twenty-two named assumptions*. That is a real and defensible result,
and it is not what "governance can be made provable" says to a reader who has
just been taught to distrust that register. The framework's own Principle 3 is
scrupulous — the system guarantees audit completeness and explicitly does **not**
guarantee constraint satisfaction. The opening sentence gives that away.

---

## m2 — the assumption load is never stated in one place (MODERATE)

Counted: **Axioms 1–5, P1–P3, AI1–AI5, GIL1–GIL4, EA1–EA4** — 21 named
assumptions, plus Lemma 2's "all components correctly implemented".

Several are the things that fail in practice: AI1 (immutable storage — a
lifecycle policy away from false), EA1 (chokepoint — one unmanaged key away from
false), P1 (shared infrastructure keeps its guarantees as components are added).

Five of the sixteen statements do not name their preconditions in the statement
text (Theorem 1, Theorem 4, and three corollaries); they rely on the proof or
adjacent prose. That is normal practice but it compounds the problem: there is
no single page an auditor can turn to for "what must be true for any of this to
hold."

**Fix:** one table. It would also be the most persuasive page in the book for a
regulator, because it is the page that says what the framework does not assume
away.

---

## m3 — Lemma 2's risk measure excludes governance's main compliance cost (MODERATE)

> **Lemma 2.** Deploying additional governance components cannot increase total
> compliance risk.

`R(i)` is defined as "the probability that the **delivered output o'** violates a
compliance requirement." Audit stores are outside that by construction — the
definition routes infrastructure risk to a separate bucket and calls it
"operational, not compliance."

But an audit chain that records prompts and outputs verbatim *is* a new PHI/PII
repository, and the regulatory appendix treats it as a live obligation: "PHI in
logs/audit records → Security §164.308(a)(4) → EPG: F(pii_in_logs),
Meta-framework AI5." AI5 requires encryption, access control and minimisation
for exactly this reason.

So the lemma is true of a risk measure that excludes the way governance most
often creates compliance risk, while the appendix treats that risk as real. Both
cannot be the whole story. A privacy officer will raise this in the first
review, and the honest answer — that governance trades output risk for
custody risk, deliberately — is a stronger position than the lemma.

---

## m4 — the chain does not detect truncation; WORM does (MODERATE)

Deleting the tail of a hash chain leaves every remaining link valid and every
signature verifying. AI2's chain detects *modification*, not *removal*. What
prevents truncation is AI1's "cannot be modified or deleted" plus AI4's
completeness check against interaction_id generation logs.

That is a sound design. But "Tamper-Evident Audit Chain" and AI2's "tampering
with any record… making modification detectable" invite the reader to think the
cryptography carries the load. Under partial AI1 failure, whole-interaction
deletion is caught by AI4, and *partial* chain truncation — dropping ROC's record
while keeping EPG's — appears to be caught only if something checks the expected
component set for the interaction's tier. I did not find that check.

---

## n1 — corollaries now read as unconditional (MINOR, and mine)

Converting the CLAD statements to `amsthm` split four corollaries out of the
fenced blocks that held them with their parent lemma. Proximity survives — "If
P1–P3 are violated, independence is not guaranteed" still sits immediately above
Corollary (Graceful Degradation) — but the corollary's own text reads
unconditionally: "No guarantee is weakened by the absence of other components."
Quoted alone, as a slide bullet will quote it, the preconditions vanish.

**Fix:** carry "Given P1–P3" into the four corollary statements.

---

## What I did not find

No fabricated citations, no invented legal authorities, no invented DOIs. The
regulatory references I spot-checked resolve to real provisions (§164.502,
§164.308, §164.404, Art. 9/14/17). Axiom 2 (non-determinism) is stated more
carefully than most published treatments. Theorem 5 carries an explicit
"NOT covered by this theorem" list, which is the single best-engineered piece of
scoping in the CLAD half and the model the other theorems should follow.
EA3 and the "MODELING SIMPLIFICATION (Risk Additivity)" note are both instances
of the framework volunteering its own weaknesses, unprompted.

The two halves are compatible in substance. Their disagreement is one of
register: the paper half is careful to a fault about what it claims, and the CLAD
half is not, in four specific sentences (C1 aside, that is M2's closer, m1's
opener, M3's monoid, and m3's lemma).


---

# Disposition — all nine applied

| # | Finding | What changed |
|---|---|---|
| C1 | `p` meant prompt and proposition | Recorded resolution applied to the CLAD half: `p→x`, `P→X`, `m→M`, set of models `𝓜`. Tuple is `(x, u, M, θ, o)` in all 9 places |
| M1 | Thm 3a's premises contradicted | Reconditioned on AI2–AI4; AI1 restated as the control being backstopped, with the vacuity explained; proof reordered so the signature carries the tail case |
| M2 | Thm 3b overclaimed | Closer narrowed to "no interaction **that enters the pipeline**", with the reason and a pointer to EA3's probabilistic target |
| M3 | Not a monoid | Retitled *Partial Commutative Monoid*; closure failure shown by counterexample; claim restricted to `(S, C, E)`; audit composition stated as causality-ordered. Lemma 3(b)'s appeal to Axiom 3 removed |
| m1 | "governance can be made provable" | Rewritten: the *evidence* is provable, the outputs are not, conditional on assumptions enumerated at the chapter's end |
| m2 | Assumption load scattered | New §"What must be true": 23-row table of every assumption, what it requires, and how it fails in practice. AI1, EA1, AI5 called out as the silent failures |
| m3 | Lemma 2's risk measure | Scoped to `R(i)` explicitly, with the custody-risk trade stated as the real answer rather than the lemma |
| m4 | Truncation undetected | New **AI5 (Chain Truncation Detection)** requiring per-tier expected component sets; old AI5 → AI6; dependent ranges corrected |
| n1 | Corollaries read unconditionally | All four now carry their inherited preconditions |

Two things surfaced while applying these.

**The notation table's own `C → R` resolution is unusable — and the collision it
was meant to fix is smaller than I first said.** Resolved on a second pass.

`R` is taken three times over: compliance risk `R(i)`, the component requirement
set `R_hard`/`R_soft` in `g = (S, C, E, A, R)`, and ROC's deterministic rules
`r ∈ R`. Applying the recorded resolution would trade one collision for three.
Withdrawn in `notation.tex`, with the reason.

My first framing — "the same defect as C1, one layer down" — was wrong, and I
should not have asserted it without the check. C1 was serious because the
interaction tuple was written *two different ways* in one book and `p` collided
inside formal expressions on both sides. For `c` there is no competing spelling,
and a file-by-file check shows the two senses are chapter-disjoint: constraint-`c`
appears only in the governance chapters and their appendix, resolved-context-`c`
only in the grounding chapters. Capital `C` is unused outside the governance
material entirely.

The book contained exactly one co-occurrence, and I introduced it: the
`applicable()` definition added to Appendix A earlier the same day imported
`c = {j, t_v, f, u}` into a file that uses `c` for a constraint fourteen times.
Fixed by naming the four dimensions instead of symbolising them, which that
passage needed only once.

So no rename is warranted. What the book needed was an accurate notation table
and one corrected passage, both now done.

**A numeric chapter reference in a CLAD source silently points at the wrong
chapter.** The converter maps "Chapter 3"/"Chapter 4" in CLAD sources to ROC and
MDR. My first draft of the m1 rewrite said "Chapters 3 and 4 explain why", meaning
the grounding chapters, and would have rendered as pointers to ROC and MDR. Any
future edit to a CLAD source must name chapters, never number them.

Build after all nine: 0 errors, 0 undefined references, 0 multiply-defined
labels, 216 pages, worst overfull box 35.7pt, none over 50pt.
