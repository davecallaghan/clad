# Adversarial review, second pass

Same five personas, on the revised paper. This pass is weighted toward defects
**introduced by the first round of repairs**, which is where a second review earns its
keep. Fourteen findings.

Severity: **F** = false or internally inconsistent. **O** = overclaimed. **G** = gap a
reviewer expects filled. **S** = structural risk to acceptance. **M** = minor.

| # | Persona | Finding | Sev |
|---|---|---|---|
| 1 | Data / DQ | `NoDefeater` quantifies over all subsets: uncheckable, and always false in practice | **F** |
| 2 | Data / DQ | `NoDefeater` has no authority ordering, so precedence cannot resolve conflicts | **F** |
| 3 | Data | Bitemporality handles a stale corpus and misses the anachronistic case | **F** |
| 4 | Systems | Two new definitional conditions have no corresponding system invariant | **F** |
| 5 | ML | §12 says parameter count is negligible; §11.4 says the parse is the critical risk | **F** |
| 6 | NLP | "Defeater" is imported philosophy vocabulary, unacknowledged, against your own rule | **G** |
| 7 | NLP | `Sufficient` is arguably redundant with `Supports`; the relation is never stated | **O** |
| 8 | Editor | 53 pages, 17 sections, six contributions — most likely rejection reason | **S** |
| 9 | Editor | 504-word abstract | **M** |
| 10 | Systems | The cost section promises orders of magnitude and delivers adjectives | **O** |
| 11 | Systems | The evaluation protocol never says what an acceptable number looks like | **G** |
| 12 | All | No end-to-end worked example anywhere | **G** |
| 13 | ML | Structured evidence plus reclassification argues the LLM out of the paper | **G** |
| 14 | Data | `Contested` is not given the subset treatment `Misapplied` received | **M** |

---

## 1. Data architect / data quality

### 1.1 `NoDefeater` is uncheckable as written — **F**

> Your new condition is
> $\neg \exists E' \subseteq E_S : \Applicable \wedge \Authoritative \wedge \Supports(E', \neg p, c)$.
> That quantifies over the powerset of the evidential base. For a corpus of any size
> it cannot be evaluated, and worse, for a large corpus it will essentially always be
> violated — somewhere in any substantial regulatory base there is text that supports
> the negation of almost anything, under some reading. You have written a condition
> that is either computationally hopeless or trivially false. Meanwhile §10.6 says the
> check is "a query for contradicting rows," which is a completely different and
> tractable operation. Your formalism and your architecture do not describe the same
> thing.

**Lands, and it is the most serious finding in this pass.** The formal statement
overreaches what the architecture proposes and what any system could do.

**Repair.** Bind the quantifier to a defined search rather than the powerset:

    NoDefeater(E_S, p, c) ≡ the governed defeater query over E_S at authority
                            level ≥ that of the supporting evidence returns nothing

and state that the query is a specified retrieval — a scoped lookup over the same
governed corpus, filtered by the same applicability predicates, seeking contrary
holdings at equal or higher standing. That is checkable, matches §10.6, and makes the
condition a *control* rather than a metaphysical quantifier. The honest framing is that
`NoDefeater` is relative to a defined search, and that an undetected defeater outside
that search is a corpus-coverage failure rather than a definitional loophole.

### 1.2 No authority ordering, so precedence cannot operate — **F**

> `Authoritative(E', c)` is a predicate, so *any* authoritative contrary source
> defeats. But conflicts between authorities are usually resolved by precedence, not
> escalated: statute over guidance, regulator over trade body, group policy over local
> practice unless local is stricter. Your definition treats a resolvable conflict as an
> unresolvable one and would send routine cases to adjudication. That is not
> conservative, it is unusable.

**Lands.** And it undercuts your own remedy: §4.7 says the remedy is "precedence rules,
adjudication, escalation," but the definition gives precedence nothing to act on.

**Repair.** Make authority an ordering rather than a predicate — $\Authoritative(E,c)$
becomes a level $\alpha(E, c)$ in a partial order the institution defines. Then a
defeater is contrary evidence at level $\geq \alpha$ of the supporting evidence, and
contrary evidence at a lower level is *resolved* rather than defeating. `Contested` is
then precisely the case where the order does not separate them — either equal levels or
incomparable ones. That makes the sixth category smaller, sharper, and genuinely the
escalation case.

This is a better result than the current one: it says the precedence order is itself a
governed artefact, which is exactly the policy function's deliverable.

### 1.3 Bitemporality is half-implemented — **F**

> You handle $t_k \prec t_v$: the corpus is older than the decision date, so amendments
> may be missing. You do not handle $t_k \succ t_v$, which is the other half of every
> bitemporal query and in regulated work the more dangerous half. A corpus current to
> today, answering a question as-of two years ago, will happily apply rules that were
> not yet in force. That is anachronistic application, and in a dispute it is worse
> than a missing amendment, because the answer is confidently wrong in a direction that
> favours whoever asked.

**Lands.** This is the classic bitemporal failure and its omission is conspicuous given
the paper now claims the distinction as a contribution.

**Repair.** State both directions. $t_k \prec t_v$ risks omission; $t_k \succ t_v$
risks anachronism, and requires the retrieval to filter on the source's *own* validity
interval containing $t_v$, not merely on the source being present in the corpus. That
is `Current(E, t_v)` doing real work: the condition is that the instrument was in force
at $t_v$, which excludes both the not-yet-enacted and the already-superseded. Say so —
at present `Current` is glossed only as "current," which reads as "recent."

### 1.4 `Contested` lacks the treatment `Misapplied` got — **M**

> You carefully explain that `Misapplied` is "operationally ungrounded, $G_i = 0$" so
> nobody accuses you of double-counting. `Contested` is in the same position and gets no
> such paragraph.

**Lands, trivially fixable.** One sentence, parallel to the existing one.

---

## 2. NLP reviewer

### 2.1 "Defeater" is imported philosophy, unacknowledged — **G**

> `NoDefeater` is not a term you coined. Defeaters are Pollock's, and the
> rebutting/undercutting distinction is standard in defeasible reasoning and
> argumentation frameworks. You have imported a term of art from exactly the literature
> you elsewhere declined to engage, without citation, into a paper whose stated
> register is data and ML engineering.

**Lands twice over** — as a citation gap and as a violation of the paper's own
vocabulary discipline.

**Repair, and take both halves.** First, use the distinction, because you need it: a
**rebutting** defeater supports $\neg p$; an **undercutting** defeater attacks the
support relation itself — the source is authentic but was withdrawn, or the extraction
was wrong. Your current condition captures only rebutting defeat, and undercutting
defeat is a real and separate case in a governed corpus. Second, either cite the
provenance in one clause, or rename to the audience's own term. `ContraryAuthority` is
the data-governance-native option; my preference is to keep `NoDefeater`, add
undercutting defeat, and acknowledge the source in half a sentence, since the term is
doing precise work.

### 2.2 `Sufficient` may be redundant with `Supports` — **O**

> If `Supports(E,p,c)` means the source establishes $p$ at the strength asserted, then
> evidence addressing three of five required conditions does not support $p$, and
> `Sufficient` is entailed. You justify the split by remedy — retrieve more versus
> retrieve better — which is a fact about repair, not a fact about the relation. Either
> the conjunct is redundant or `Supports` is weaker than you said.

**Lands.** `Supports` is in fact defined as a range down to weak evidential favouring,
so the two *are* independent — but the paper never says that is why.

**Repair.** State the relation explicitly. `Supports` is the positive relation: the
evidence favours $p$, at some strength. `Sufficient` is the coverage relation: the
evidence addresses every condition the claim requires. Independent because evidence can
strongly favour a partial claim. One sentence in §6.4, and it removes the redundancy
objection entirely.

---

## 3. ML / AI reviewer

### 3.1 §12 contradicts §11.4 on whether model capability matters — **F**

> §11.4 tells me the critical failure has moved to natural-language-to-schema parsing,
> and that a misparse produces an internally consistent record in which every control
> passes. §12's table then tells me model parameter count has "no direct effect" on
> auditability and rates it "negligible." Parse quality is a model capability. You have
> identified the highest-consequence failure in the architecture and then rated the
> variable that most affects it as irrelevant.

**Lands. This is an internal inconsistency the first pass created**, by adding §11 and
§12 without reconciling them.

**Repair.** The table's claim should be that parameter count has no direct effect on the
*system invariants*, which is true, and note the indirect path: where an in-scope model
performs context resolution or request construction, its capability bears directly on
I10, and that dependency is a reason to make the parse reviewable (§11.4) rather than a
reason to buy a larger model. That keeps the ranking honest and removes the
contradiction.

### 3.2 Structured evidence argues the model out of the paper — **G**

> §10.7 says a recorded query against a system of record is the strongest form of
> evidence and satisfies your conditions more cleanly than retrieval. §11.7 says that
> with typed input and constrained output the model is a rendering layer over a decision
> service. Put together, your best architecture barely uses the capability that
> motivated the technology. Either say so and own it, or explain what the model is for.

**Lands as a tension worth naming rather than a defect.** It is the honest conclusion of
your own argument and a reviewer will reach it whether or not you do.

**Repair.** Address it directly. Where a claim is decidable from governed structured
data, the model should not be deciding it, and the architecture should route it away —
that is a *feature* of the framework, since it identifies which decisions do not need a
language model at all. What remains for the model is the genuinely open-textured
residue: synthesis across heterogeneous sources, questions whose evidence exists only as
prose, and interaction with users who cannot express their need in a schema. That is a
smaller claim than the field usually makes and a more defensible one.

---

## 4. Systems and governance architect

### 4.1 Two new conditions, no invariants — **F**

> Your definition now requires `Sufficient` and `NoDefeater`. Table
> `invariants-system` still lists I8 through I17 and contains neither. So the paper
> requires two properties of a grounded assertion and specifies no system obligation
> to establish them. A reviewer building from your invariant table will build a system
> your definition rejects.

**Lands, and it is the kind of inconsistency an architect reads a paper to find.**

**Repair.** Add two rows: **I18 Sufficiency** — the evidence set is checked for coverage
of every condition the claim requires, supplied by required-field or checklist
verification; **I19 Defeater search** — a scoped search for contrary authority at equal
or higher standing is performed and recorded, supplied by the governed defeater query
of finding 1.1. Then update the tier table, since I18 and I19 belong at Tier 3, and the
cost table, since the defeater query is the cheap one and coverage checking is not.

### 4.2 The cost section promises quantification and gives adjectives — **O**

> "Rough orders of magnitude" and then a table reading negligible, multiplies calls,
> dominant cost, storage growing. Those are not orders of magnitude. I still cannot
> tell whether your architecture costs 2× or 50× a bare call, which is the only number
> that determines whether I can propose it.

**Lands.** Either quantify or stop promising to.

**Repair.** Give the multipliers structurally, which requires no benchmarks: generation
is one call; claim decomposition is one call plus one per claim; support verification is
claims × candidate sources model calls unless deterministic; the defeater query is one
retrieval; audit storage is proportional to retained evidence. A ten-claim response over
three candidate sources each is order thirty verification calls, so the dominant term is
the product and the design lever is reducing claims per response or replacing model
verification with deterministic checks. That is honest, derivable, and actionable.

### 4.3 No acceptance thresholds — **G**

> Your protocol tells me to measure the ungrounded assertion rate, the misapplied rate,
> form divergence, and repeatability. It never tells me what number is acceptable, or
> how to set one. A measurement regime with no thresholds is not a control regime.

**Lands, and it is a fair demand even though the honest answer is uncomfortable.**

**Repair.** Say plainly that no universal threshold exists, and give the method for
deriving a local one: the threshold is set by the consequence tier and the downstream
control. Where a competent human reviews every assertion, a high ungrounded rate is
tolerable and the metric is a workload predictor. Where the assertion is acted on
directly, the tolerable rate approaches the rate at which the institution tolerates
unevidenced decisions by people, which is a number every regulated firm already has for
its manual processes. Anchoring to the existing manual control standard is the move,
and it is one your audience will recognise.

### 4.4 No end-to-end worked example — **G**

> Fragments throughout: a paediatric dosing case, a treaty date, a subsidiary
> headcount. No single case traced from request through context resolution, retrieval,
> applicability, defeater search, verification, gating, and audit record. For this
> audience that single artefact would be worth more than three of your sections.

**Lands, and it is the largest missed opportunity in the paper.**

**Repair.** One appendix, one case, traced end to end, showing what each invariant
produces as a record and where the assertion would have been stopped. Use the
superseded-approval case, since it exercises `Applicable`, both time dimensions, and the
defeater search at once.

---

## 5. Editor / reviewer of record

### 5.1 Scope is now the biggest risk to acceptance — **S**

> Fifty-three pages. Seventeen sections. Six claimed contributions. You redefine a
> term, prove an independence result, analyse an architecture, propose a reference
> architecture, propose an interface discipline, analyse procurement, propose an
> evaluation protocol, analyse organisational structure, and discuss cost and retention.
> Each is competent. Together they are three papers, and the reviewer who likes your
> hallucination result will not finish the organisational analysis.

**Lands, and it is the most likely rejection reason even if every claim survives.** This
is the cost of taking the last two review passes strongly: the paper absorbed everything.

**Repair, and this one is genuinely yours.** The natural fracture is clean. Sections 1–9
plus 15 are a complete paper: the redefinition, the independence result, the
architecture and training argument, the canonical-form argument, the invariants, the
measurement, the protocol. Sections 10–14 are a second paper on governed architecture,
bounded requests, scale, warrant, tiers, and organisation. The first is a
measurement-and-definition paper; the second is a design-and-governance paper; and the
second cites the first for its motivation. That was the split you abandoned for good
reason — you could not carry a purely philosophical Paper 1 — but this fracture is not
that one. Both halves are engineering.

If you keep it as one paper, target a journal rather than a conference and expect the
length to draw comment.

### 5.2 The abstract is 504 words — **M**

Two to three times the normal limit, and arXiv listings truncate. The first paragraph
now carries the whole hallucination argument. Cut to roughly 250: the redefinition in
two sentences, the independence result in one, the canonical-form claim in one, the
measurement conclusion in two.

---

## What this pass says about the last one

Five of the fourteen findings above (1.1, 1.2, 1.3, 3.1, 4.1) are defects the previous
round of repairs introduced. That rate is normal for a revision of this size and it is
the argument for doing a second pass rather than shipping after the first. The pattern
is specific and worth naming: **adding a condition to a definition creates obligations
elsewhere in the paper** — an invariant, a control, a cost line, a tier assignment —
and the first pass added three conditions while updating none of the downstream tables.

Recommended order: 1.1 and 1.2 together, since they are one repair to the same
condition; then 4.1, which follows from them; then 1.3, 3.1, and 2.2, which are
localised; then 2.1; then 4.2 and 4.3; then the structural decision in 5.1, which
should be taken before writing the worked example in 4.4, since where that appendix
lands depends on whether the paper splits.
