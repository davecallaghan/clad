# Adversarial review of `paper1-grounding`

Five reviewer personas, arguing against the paper. Each finding gives the attack in
the reviewer's voice, my verdict on whether it lands, and the repair.

Severity: **F** = would be flagged as false or a misreading. **O** = overclaimed.
**G** = gap a reviewer expects to be filled. **M** = minor.

| # | Persona | Finding | Sev |
|---|---|---|---|
| 1 | NLP | Reversal curse is not an instance of form-invariance failure | **F** |
| 2 | Data / DQ | Conflicting evidence satisfies the grounding definition | **F** |
| 3 | Data / DQ | Only one time dimension; regulated evidence is bitemporal | **G** |
| 4 | Systems | Model risk management is absent from Three Teams | **G** |
| 5 | ML | Verifier-based reasoning systems do have an outcome gate | **O** |
| 6 | Systems | "Cannot govern inference you do not operate" ignores contract | **O** |
| 7 | Data / DQ | Completeness is missing from the grounding dimensions | **G** |
| 8 | ML | §6's title overclaims relative to §6's own content | **O** |
| 9 | NLP | Context-relevance metrics are the nearest counterexample and go unmentioned | **G** |
| 10 | ML | "No term that is a function of truth" is loose | **O** |
| 11 | Systems | No cost, latency, or throughput analysis anywhere | **G** |
| 12 | Data | Structured evidence is stronger than retrieved text and is never discussed | **G** |
| 13 | ML | M/S equivocation in the "no evidence gate" argument | **M** |
| 14 | Systems | Retention conflicts with data minimisation; unmentioned | **G** |
| 15 | Systems | Tiers have no assignment criteria | **M** |
| 16 | ML | "Latent representation is not a system of record" is a straw man | **M** |

---

## 1. ML / AI architecture reviewer

### 1.1 Verifier-based reasoning systems do have a gate — **O**

> You claim later developments "change none of the structural properties," and you
> list reinforcement training on verifiable outcomes as in scope. But a system that
> samples candidates and selects among them with a verifier has a check outside the
> forward pass. For mathematics or code, that verifier is a genuine correctness
> oracle. You have asserted that no evidence gate exists in the very systems that
> most obviously have one.

**Lands.** The concession is narrow but real. For domains with a cheap, sound
verifier, outcome-RL and search-with-verification do supply something structurally
analogous to a gate — and the paper's own §11.5 argument (the gate must be external)
is *satisfied* by that design.

**Repair.** Concede explicitly, then draw the boundary that matters: a verifier is
available where correctness is cheaply decidable against a formal specification.
Regulated factual assertion is not such a domain — there is no oracle for "is this
the governing rule for this jurisdiction at this date." So the mechanism exists and
does not transfer to the case the paper is about. Stating this *strengthens* the
paper, because it shows the author knows where the exception is.

### 1.2 §6's title overclaims relative to §6's content — **O**

> "There Is No Canonical Form" is false as a flat statement. Embeddings map
> paraphrases to nearby points; that is soft canonicalisation learned from data. Your
> own body text concedes it — "a contingent achievement of training coverage." So the
> section's title contradicts the section.

**Lands, mildly.** The body is careful; the title is not. A reviewer who reads only
headings gets a claim the paper does not defend.

**Repair.** Either retitle to "There Is No Guaranteed Canonical Form," or add one
sentence under the heading distinguishing a learned tendency from an enforced
invariant. I prefer the second: the distinction is the point, and the punchy title is
worth keeping if it is immediately qualified.

### 1.3 "No term that is a function of truth" is loose — **O**

> Cross-entropy on human text absolutely makes the model sensitive to truth-correlated
> structure — that is why it works. Saying the objective has no term that is a
> function of truth, and concluding scale "cannot make an objective sensitive to a
> quantity it does not contain," conflates having no explicit term with having no
> sensitivity.

**Lands.** The philosophical framing is sloppy even though the engineering point is
right.

**Repair.** Sharpen to the claim that survives: the objective contains no term that
**rewards truth over plausible falsehood where the two diverge**. On the training
distribution they mostly coincide; off it they do not, and the loss is indifferent.
That is precise, defensible, and says exactly what the paper needs.

### 1.4 M/S equivocation in the gate argument — **M**

> You distinguish $M$ from $S$ carefully and then argue "the forward pass has no
> evidence gate" as though it were a claim about the deployed system. Retrieval-
> augmented systems with guardrail classifiers do have gates. Which object is the
> claim about?

**Lands as an internal-consistency issue.** The claim is about $M$ and is correct
about $M$. The prose slides.

**Repair.** State it as a claim about $M$ explicitly, and note that $S$ may have a
gate — indeed must, per I17 — which is the paper's own recommendation. One clause.

### 1.5 The system-of-record comparison is a straw man — **M**

> Nobody claims a model's activations are a system of record. You are refuting a
> position no one holds.

**Partly lands.** Nobody says it explicitly; plenty of architectures behave as though
it were true by treating model output as authoritative without lineage. Keep the
argument, drop the framing that someone asserted it.

---

## 2. NLP reviewer

### 2.1 The reversal curse is not form-invariance failure — **F**

> This is the paper's headline empirical evidence and it is the wrong result. The
> reversal curse concerns what a model *learned*: training on "A is B" does not yield
> the ability to answer a query about B. That is a generalisation and
> retrieval-direction failure. Form invariance is a claim about *inputs* — that
> materially equivalent requests yield equivalent outcomes. "Who was the ninth
> Chancellor?" and "What was Scholz's role?" are not two encodings of one request;
> they are different requests with a shared answer. You have offered a
> representational result as an instance of an input-invariance property, told the
> reader it is "the cleanest case," and invited them to show it to a sceptical
> colleague. The colleague will be an NLP researcher.

**Lands hard.** This is the most serious finding in the review. The two phenomena are
related — both evidence that content is not represented independently of form — but
they are not the same failure, and the paper's framing is a category error.

**Repair.** Two moves, and both improve the paper.

1. **Relocate the reversal curse** to §3.1, as evidence for the *representational*
   claim: the parameterisation has no slot for a proposition, and facts are stored in
   a form tied to their training surface order. That is what the result actually
   shows, and it is strong support for the deeper thesis.
2. **Promote format sensitivity to the clean case of I1 failure.** Sclar et al. is the
   input-side result: same semantic content, varied formatting, divergent behaviour.
   Sycophancy is the second: same question, different framing, different answer. Those
   are what I1 is about.

### 2.2 Context-relevance metrics go unmentioned — **G**

> You claim applicability is invisible to existing metrics. RAG evaluation frameworks
> include context-relevance scoring. You should say why that is not applicability
> rather than write as though nothing in the area exists.

**Lands.** The claim survives, but the nearest counterexample must be named or the
omission looks like unfamiliarity.

**Repair.** Add a sentence: context relevance scores whether retrieved evidence is
relevant **to the query**. Applicability asks whether it **governs the case**. A
paediatric question retrieving adult guidance scores as highly relevant — the topic
matches — and the guidance does not govern. Relevance to a query and authority over a
decision are different relations, and only the first is measured.

---

## 3. Data architect / data quality reviewer

### 3.1 Conflicting evidence satisfies the definition — **F**

> Read your own definition. $\Grd_S(p \mid c)$ holds if **there is some** $E$ in the
> evidential base with $\Grounded(E,p,c)$ and $\Sensitive(S,p,E)$. Suppose the base
> contains two authentic, authoritative, applicable, current sources, one supporting
> $p$ and one supporting $\neg p$. Your definition returns *grounded*. An auditor
> would return *unresolved conflict — not fit for reliance*. The existential
> quantifier is doing something you did not intend.

**Lands hard, and it is a defect in the central definition.** Conflicting
authoritative sources are routine — two regulators, a standard and its national
implementation, a guideline and a local protocol. The definition as written picks
whichever source the system happened to use and declares the assertion grounded.

**Repair.** Add a conjunct. The cleanest form is a no-defeater condition:

    Grounded(E,p,c) ∧ Sensitive(S,p,E) ∧ ¬∃E' ⊆ E_S :
        Applicable(E',c) ∧ Authoritative(E',c) ∧ Supports(E',¬p,c)

Stated in prose: no equally applicable and authoritative evidence in the base
supports the contrary. Where such evidence exists the correct outcome is escalation,
not assertion — which is I17, and §10 component 6 already lists "conflicting" as a
gating trigger. The definition simply has not caught up with the architecture.

This is worth doing properly rather than patching: unresolved conflict is a **sixth
category**, distinct from all five, with its own remedy (adjudication or escalation)
and its own owner (policy).

### 3.2 Only one time dimension — **G**

> Your context tuple has a single $t$. Regulated evidence is bitemporal. *Valid time*
> is when the rule was in force; *transaction time* is when the system knew it.
> Correctness needs the first; reconstruction needs the second. A decision taken on
> Tuesday under a rule amended on Monday but not ingested until Friday is defensible
> or not depending on which you mean, and your notation cannot express the difference.

**Lands.** This is the finding a senior data architect would raise first, and it is
sophisticated enough that getting it right signals real fluency.

**Repair.** Split $t$ into $t_v$ (valid time, the effective date the assertion is
about) and $t_k$ (transaction time, the corpus state the system had access to).
$\Current(E, t_v)$ becomes a validity claim; $t_k$ becomes part of the audit record
and the replay key. It also sharpens §12: the corpus snapshot is $t_k$, and
disagreement between $t_v$ and $t_k$ is the *cause* of the staleness failure the paper
already describes.

### 3.3 Completeness is missing — **G**

> Six dimensions, and none is completeness. Evidence can be authentic, versioned,
> authoritative, applicable, supporting, and traceable — and cover three of the five
> conditions the rule actually imposes. That assertion is grounded on your definition
> and wrong in practice. Completeness is a core data-quality dimension and its absence
> from a data-governance-flavoured definition is conspicuous.

**Lands.** Related to 3.1 but distinct: 3.1 is contrary evidence, this is *absent*
evidence.

**Repair.** Either add `Sufficient(E, p, c)` as a seventh conjunct — the evidence
addresses every condition the claim requires — or fold it explicitly into
`Supports` and say so, since "supports at the strength asserted" is close. I prefer
naming it, because sufficiency failures have a distinct remedy: retrieve more, not
retrieve better.

### 3.4 Structured evidence is never discussed — **G**

> Your architecture is document-centric throughout. The strongest grounding available
> in an enterprise is not a retrieved passage; it is a query against a system of
> record with the query text, the result set, and the as-of timestamp recorded. That
> is more auditable than any text retrieval, and you do not mention it.

**Lands, and it is an opportunity rather than only a gap.** A recorded query against
a governed table satisfies I8, I9, I11, I12, and I14 more cleanly than passage
retrieval does, because the evidence has a schema.

**Repair.** Add a subsection to §10 on structured evidence: a query plus result set
plus as-of is the highest-assurance form of $E$, and the applicability conditions
become schema predicates rather than judgements. This is the natural home ground of
the paper's intended audience and its absence is odd.

---

## 4. Systems and governance architect

### 4.1 Model risk management is absent — **G**

> A paper on model governance for regulated enterprises that names three functions —
> data, policy, platform — and omits model risk management. In banking, MRM is the
> second-line function that *owns* model validation, with an independent reporting
> line and a mandate under supervisory guidance. Independent validation is the
> control your paper is arguing for, and you have left out the function that performs
> it.

**Lands hard.** For a financial-services reader this is the most conspicuous omission
in the paper.

**Repair.** Add a fourth row. MRM owns independent validation and challenge; its
failure mode in isolation is validating a model in the abstract while the system's
evidence path, applicability logic, and authorization go unexamined — validating $M$
when the risk is in $S$. That framing is a genuine contribution to the MRM
conversation, not merely an addition to a table.

### 4.2 "Cannot govern inference you do not operate" — **O**

> Enterprises govern third-party processors constantly, by contract: audit rights,
> change-notification clauses, version-pinning commitments, sub-processor
> restrictions. Your claim is too strong, and it is the kind of overstatement that
> makes a procurement function stop reading.

**Lands.** Same overclaim species as the earlier findings.

**Repair.** "You cannot govern inference you do not operate **or contract for**." Then
the useful version: the controls that would otherwise be configuration become
contract terms, and the paper can name them — pinned versions, notice of change,
deterministic serving options, retention and audit rights. That converts a dead end
into a procurement checklist, which this audience will actually use.

### 4.3 No cost, latency, or throughput analysis — **G**

> Context resolution adds a round trip. Claim decomposition multiplies calls.
> Per-claim verification multiplies them again. Audit records multiply storage. You
> recommend all of it and never once say what it costs. Any architect evaluating this
> will assume you have not built it.

**Lands, and it is the finding most likely to cost you credibility with your own
audience.** The paper's authority rests on practitioner experience, and practitioners
lead with cost.

**Repair.** A short subsection in §10: which components are cheap (metadata filtering,
audit logging), which are expensive (per-claim verification, human adjudication), and
that the tiering of §11 exists precisely so the expensive controls are applied only
where consequence justifies them. Order-of-magnitude statements are enough; no
benchmarks required.

### 4.4 Retention conflicts with data minimisation — **G**

> You require retaining prompts, retrieved passages, and resolved context. In a
> regulated enterprise those may contain personal or special-category data, and
> minimisation and erasure obligations cut directly against the retention you
> mandate. You have written a controls proposal that conflicts with another control
> regime and not noticed.

**Lands.** A privacy or records-management reviewer raises this immediately.

**Repair.** Acknowledge the tension and give the standard resolutions: retain
references and hashes rather than content where possible; segregate and
access-control the audit store; align retention to the decision's own retention
schedule rather than choosing a new one; and note that the conflict is a design
decision requiring the privacy function, which is a fifth stakeholder.

### 4.5 Tiers have no assignment criteria — **M**

> Six tiers and no procedure for placing a use case in one. "Consequence of reliance"
> is not operational.

**Partly lands.** The section says it is compact by design. But naming the dimensions
costs three lines: reversibility, magnitude, breadth of affected parties,
detectability of error, and availability of a human check. Worth adding.

---

## 5. The cross-cutting finding

Across all five personas, one habit accounts for most of the damage: **a strong
practical limitation stated as a structural impossibility.** Findings 1.1, 1.3, 4.2,
and the earlier hallucination and reproducibility issues are all this. The pattern is
"cannot" where the defensible claim is "does not, and here is the architectural
reason."

It is worth a deliberate pass over every modal verb in the paper — *cannot, never, no
amount of, impossible, only* — asking of each whether the strong reading is defended.
The weaker claim is almost always both true and more persuasive to this audience,
because practitioners distrust absolutes about systems they have seen behave
surprisingly.

## Recommended order of repair

1. **2.1** relocate the reversal curse — it is the headline evidence and currently
   miscast.
2. **3.1** the conflict defeater — a defect in the central definition, and it yields a
   sixth category.
3. **3.2** bitemporality — cheap to add, high signal to the intended reader.
4. **4.1** model risk management — conspicuous omission for financial services.
5. **1.1** concede verifier-based systems, then bound the concession.
6. **4.2, 1.3, 1.2** the overclaim pass.
7. **3.3, 3.4** completeness and structured evidence.
8. **4.3, 4.4** cost and retention.
9. **1.4, 1.5, 2.2, 4.5** the minor items.
