# Theorems, Lemmas, and Proofs

The meta-framework chapter states the control model: the surfaces an interaction
presents, the component that governs one, and the algebra by which components
compose. This appendix carries the results proved over that model, and the
assumptions every one of them rests on.

They are collected here rather than in the chapter because their audience is
different. A reader deciding whether to adopt the framework needs the model and
the guarantees in prose. A reader building it, or auditing someone else's build,
needs the statements, the preconditions, and the proofs — and needs them in one
place rather than scattered across a chapter.

Read the assumption table at the end first if you are evaluating a deployment.
Every result below is conditional on it.

### 3.4 Surface Partition Theorem

```
THEOREM 1 (Surface Completeness):

Every element of every AI interaction is assigned to exactly one control
surface, and every control surface has a defined governability class.

Proof:
  By Axiom 1, every interaction is described by (x, u, M, θ, o).
  o' is derived from o by definition of the pipeline.
  By the Governance Surface Partition definition, the surfaces partition {p, u, m, θ, o, o'} exhaustively
    and disjointly.
  By the Governability classification, each surface has one.
  By Axiom 4, governability requires observability; surfaces classified as
    full or partial are observable; surfaces classified as external are
    explicitly outside the governance boundary.
  Therefore, no interaction element lacks surface assignment, no element
    is multiply-assigned, and all governance boundaries are explicit.  ∎
```

---

---

### 4.3 Component Independence

```
LEMMA 1 (Component Independence):

Given Axiom 3 preconditions P1-P3 hold:
If components g₁ and g₂ govern non-overlapping surfaces, then:

  Φ(g₁) holds regardless of whether g₂ is deployed
  Φ(g₂) holds regardless of whether g₁ is deployed

Proof:
  By Axiom 3 (Guarantee Independence, Conditional), given P1-P3,
  Φ(g) holds in deployment({g}) iff Φ(g) holds in deployment(G').
  P1 ensures adding g₂ doesn't degrade infrastructure for g₁.
  P2 ensures g₂ doesn't prevent g₁ from receiving its artifacts.
  P3 ensures no shared mutable state between g₁ and g₂.
  This is a direct consequence of the conditional axiom.  ∎

If P1-P3 are violated, independence is not guaranteed and must
be verified empirically for the specific deployment.

COROLLARY (Graceful Degradation):

Given P1-P3, deploying a subset of governance components provides the guarantees of
that subset. No guarantee is weakened by the absence of other components.

  ∀ G' ⊆ G : ∀ g ∈ G' : Φ(g) holds in deployment(G')

This enables phased adoption: an organization can deploy EPG (prompt
governance) alone and receive its full guarantees, then add ROC (output
controls) later for additional coverage.

CAVEAT (Soft Dependency Effectiveness):
  A component's GUARANTEE (Φ) holds without soft dependencies (R_soft).
  A component's EFFECTIVENESS may be reduced without them. Example:
  ROC's guarantee (evaluate every output, produce audit record) holds
  without EPG prompt context. But ROC's evaluation quality may be lower
  because it lacks the constraint context EPG would provide. The
  guarantee is about process completeness, not evaluation quality.
  Phased adoption is safe for guarantees; evaluation effectiveness
  improves as more components are deployed.
```

---

---

### 8.4 Contract Satisfaction Theorem

```
THEOREM 2 (Contract Composability):

Given: Axiom 3 preconditions P1-P3, Axiom 4 (observability),
       Axiom 5 (temporal identity), Audit Linkability requirement.

If components g₁ and g₂ each satisfy their component guarantees
(Φ(g₁), Φ(g₂)), and the interface contract K(g₁, g₂) is satisfied,
then the composed system (g₁, g₂) provides:

  Φ(g₁) ∧ Φ(g₂)                          — both individual guarantees hold
  ∧ audit_chain(g₁, g₂) is complete        — audit records compose
  ∧ coverage(g₁, g₂) = S_g₁ ∪ S_g₂       — governed surface expands

Proof:
  Φ(g₁) holds by hypothesis.
  Φ(g₂) holds by hypothesis.
  By Lemma 1 (given P1-P3), guarantees are independent.
  Contract satisfaction ensures handoff data is available, including
    interaction_id for chain linking (Design Requirement).
  Handoff includes version_manifest (Axiom 5), preserving temporal audit.
  Audit integrity (§6.4, AI1-AI4) ensures records are tamper-evident.
  Surface coverage is the union by definition.  ∎
```

---

---

### 6.3 Audit Completeness Theorem

```
THEOREM 3 (Audit Chain Completeness):

Given: Axiom 3 (P1-P3), Axiom 4 (observability), Axiom 5 (temporal
       identity), Audit Linkability requirement, Enforcement (EA1-EA2),
       Audit Integrity (AI1-AI4).

For a deployed component set G' ⊆ G with satisfied interface contracts,
the audit chain for any interaction i covers every governed surface,
bounded by the observability frontier and enforcement boundary.

  ∀ i ∈ I_governed : ⋃{A_g.surface | A_g ∈ chain(i)} = ⋃{S_g | g ∈ G'}

  where I_governed = interactions that pass through enforcement points (EA1)
  and A_g may be either a normal record or a degraded record (§7.2).

Preconditions:
  - All elements in ⋃{S_g | g ∈ G'} are observable (Axiom 4).
  - All interactions pass through enforcement points (EA1-EA2).
  - Audit records are tamper-evident (AI1-AI4).

Elements beyond the observability boundary, and interactions that
bypass enforcement, are not covered. Both contribute ungoverned risk
that must be documented per Tier 2 classification (Axiom 1) and
detected per EA3 (bypass detection).

Proof:
  For governed interactions (enforcement ensures they reach components):
  Each deployed component g ∈ G' produces either A_g(i, t) (normal) or
    A_g_degraded(i, t) (failure) by the guarantee under failure (§7.3).
  Each record covers surface S_g by definition.
  Each record includes version_manifest by Axiom 5.
  Chain linking via shared interaction_id is ensured by Design Requirement.
  Record integrity is ensured by AI1-AI4.
  Observability is ensured by Axiom 4 and component precondition (§5.1).
  Therefore, the chain covers the union of all deployed component surfaces
    for all governed interactions, with explicit degraded-state markers
    for any component failures.  ∎

COROLLARY (CISO Audit Property):

Given the premises of Theorem 3:

For any interaction i at any time t, the audit chain provides:
  - Which constraints were in effect: ⋃{C_g | g ∈ G'} with ver(c, t)
  - Whether each constraint was satisfied: eval(c, artifact(i))
  - Who authored each constraint: owner(c)
  - The artifact as it existed: verifiable via artifact_hash
  - Which model version processed the interaction: ver(m, t)  (Axiom 5)
  - What inference parameters were used: θ at time t  (Axiom 5)
  - What was NOT observable: observability_note per record  (Axiom 4)

This is the formal guarantee behind "show me exactly which rules were in
effect when this AI produced that output" — with the added rigor that
the system explicitly declares what it could and could not observe.

IMPORTANT DISTINCTION (Record Types in the Chain):

  Audit records in chain(i) are one of two types with different
  evidentiary strength:

  EVALUATION RECORD (component-signed, §9.1):
    Produced by the governance component itself during normal operation.
    Contains actual constraint evaluations (eval(c, artifact(i))).
    Signed by the component's KMS key.
    Evidentiary value: STRONG — proves constraints were evaluated.

  PROCESS RECORD (supervisor-signed, §7.4):
    Produced by the Governance Supervisor on behalf of a failed component.
    Contains failure metadata but NO constraint evaluations.
    Signed by the Supervisor's KMS key.
    Evidentiary value: MODERATE — proves the component failed and the
    failure was recorded, but does NOT prove constraints were evaluated.

  Consumers of audit records (regulators, auditors, incident responders)
  MUST distinguish between these types. A chain containing process
  records has unbroken integrity but incomplete evaluation coverage.
  The presence of process records should trigger investigation, not
  be treated as equivalent to full evaluation.
```

---

### 7.3 Monotonic Risk Reduction Lemma

```
LEMMA 2 (Monotonic Risk Reduction):

Given: Axiom 3 preconditions P1-P3, and that all components are
correctly implemented (no implementation defects that introduce
new vulnerabilities).

Deploying additional governance components cannot increase total
compliance risk AS MEASURED BY R(i).

  ∀ G' ⊆ G'' ⊆ G : R(i | G'' deployed) ≤ R(i | G' deployed)

Scope, and it is a real restriction. R(i) is the probability that the
DELIVERED OUTPUT violates a requirement. Governance creates compliance
risk of a different kind, which this measure cannot represent: an audit
chain that records prompts and outputs verbatim is a new repository of
regulated data, subject to minimization, retention, access control and
breach exposure in its own right. The regulatory mapping treats this as
a live obligation, not a hypothetical — PHI in logs and audit records
maps to Security §164.308(a)(4), and AI1-AI6 exist partly to discharge
it.

So the honest statement of what governance does is not that it lowers
risk monotonically. It TRADES output risk for custody risk, deliberately,
and the trade is worth making because output risk is unbounded and
undetectable while custody risk is bounded, located, and controllable by
established means. A privacy officer is right to raise this, and the
trade is the answer, not the lemma.

Proof:
  Let g ∈ G'' \ G' (a component in the larger set but not the smaller).
  By the Risk Reduction definition, deploying g reduces R_Sg: R_Sg(i|g) ≤ R_Sg(i|¬g).
  By Axiom 3 (given P1-P3), deploying g does not affect the guarantees
    of any g' ∈ G'. Therefore, deploying g does not increase R_Sₖ for
    any k ≠ g's surface.
  Total risk R(i) = ΣR_Sₖ. One term decreases, others are unchanged.
  Therefore R(i) is non-increasing.  ∎

CAVEAT (Implementation Risk):
  This lemma assumes correct implementation. In practice, deploying a
  new component may introduce implementation defects (bugs, misconfigs,
  new attack surface) that increase risk outside the formal model.
  Implementation risk is addressed by testing, security review, and
  staged rollout — not by the governance framework itself.
  The lemma holds for the GOVERNANCE risk model; it does not claim
  that total OPERATIONAL risk is monotonically non-increasing.

COROLLARY (Incremental Value):

Given P1-P3:

Each component deployed provides non-negative marginal risk reduction:
  ΔR(g) = R(i | G' deployed) - R(i | G' ∪ {g} deployed) ≥ 0

This formally justifies phased adoption: every component added improves
the risk posture. There is no deployment order that makes things worse.
```

---

### 7.5 Residual Risk Theorem

```
THEOREM 4 (Irreducible Residual Risk):

Even with all governance components deployed, residual compliance
risk is non-zero.

  ∀ G fully deployed : R(i | G) > 0

Proof:
  By Axiom 2 (Non-Determinism), model output is sampled from a
    distribution: o ~ M(x, u, θ).
  For any non-degenerate distribution, ∃ o in the support such that
    o violates a compliance requirement.
  Output filtering (S_delivery) operates on generated content, not
    on the distribution — it cannot prevent generation, only intercept.
  Novel violation patterns not covered by existing filters remain possible.
  By Axiom 4 (Observability), elements beyond the observability boundary
    contribute ungoverned risk that no component can reduce.
  Therefore, R(i | G) > 0 for any finite governance system.  ∎

COROLLARY (Risk Management, Not Elimination):

Given the premises of Theorem 4:

The goal of the governance solution is to minimize R(i) to an
acceptable level and ensure that all residual risk is:
  (a) documented — known and acknowledged
  (b) monitored — detectable when it materializes
  (c) remediable — traceable to root cause via the audit chain
  (d) bounded — the observability frontier is declared, so ungoverned
      risk is explicitly identified, not silently ignored (Axiom 4)
```

---

---

### 8.3 Full Chain Audit

```
THEOREM 5 (Full Solution Audit Completeness):

Given: Axiom 3 (P1-P3), Axiom 4, Axiom 5, enforcement (EA1-EA2),
       audit integrity (AI1-AI6), GIL (GIL1-GIL4).

When all three components are deployed with satisfied interface
contracts, the composed audit chain covers every Tier 1 element
of every governed interaction within the observability boundary.

  ∀ i ∈ I_governed :
    chain(i) = [A_EPG(i, t), A_ROC(i, t), A_MDR(i, t)]

    covers: S_prompt ∪ S_output ∪ S_delivery ∪ S_input ∪ S_config
          = {p, o, o', u, m, θ}
          = all Tier 1 interaction elements

  NOT covered by this theorem:
    - Tier 2 elements (known but unmodeled — documented as ungoverned)
    - Tier 3 elements (unknown — undocumented residual risk)
    - Model internals (γ = external)
    - Ungoverned interactions that bypass enforcement (detectable via GIL)
    - Threats T7-T11 (out of scope per §2.4)

Proof:
  For governed interactions (registered in GIL, passing through EA1):
  g_EPG covers S_prompt = {p}.
  g_ROC covers S_output ∪ S_delivery = {o, o'}.
  g_MDR covers S_input ∪ S_config = {u, m, θ}.
  By Theorem 1, these surfaces are exhaustive over Tier 1 elements.
  By Theorem 2 (given P1-P3), the chain composes correctly.
  By Theorem 3, every governed surface has audit records.
  Model internals are documented as ungoverned (Axiom 4).
  Tier 2 elements are documented per Axiom 1 three-tier classification.
  Therefore, the chain covers all Tier 1 elements of governed
    interactions, and explicitly declares what it does not cover.  ∎
```

---

---

### 9.2 Algebraic Properties

```
LEMMA 3 (Composition Properties):

Given components with non-overlapping surfaces (S_g₁ ∩ S_g₂ = ∅):

(a) Associativity:
    (g₁ ⊕ g₂) ⊕ g₃ = g₁ ⊕ (g₂ ⊕ g₃)

    Proof: Surface union is associative. Constraint union is associative.
    Audit composition via shared interaction_id is associative (chain
    order is determined by pipeline causality, not composition order).  ∎

(b) Commutativity:
    g₁ ⊕ g₂ = g₂ ⊕ g₁

    Proof: Surface union and constraint union are commutative, which
    gives equality of the S, C and E components directly. (Axiom 3 is
    not needed here and does not belong in this proof: it concerns
    preservation of Φ under composition, which is a different claim from
    equality of the composed tuple.) The audit component is excluded —
    its order is fixed by pipeline causality, not by composition.  ∎

(c) Identity:
    ∃ g_∅ = (∅, ∅, ∅, ∅, ∅) such that g ⊕ g_∅ = g

    The null component governs nothing, produces no audit records,
    and does not affect any other component.  ∎

THEOREM 6 (Governance Partial Commutative Monoid — Abstract Components):

Given: Axiom 3 preconditions P1-P3, and components satisfying P3's
       isolation constraints.

The abstract governance components form a commutative monoid under ⊕
that is PARTIAL, and the claim holds for the (S, C, E) components of the
tuple rather than for the audit component.

  (G_abstract, ⊕) satisfies: associativity, identity, commutativity,
                              on the domain where ⊕ is defined.

Two qualifications, both load-bearing:

  Closure fails, and ⊕ is partial. Non-overlap is a relation between a
  pair of components, not a property of one, so "components with
  non-overlapping surfaces" does not pick out a set closed under ⊕.
  Concretely: g₁ ⊕ g₂ governs S₁ ∪ S₂, so (g₁ ⊕ g₂) ⊕ g₁ has
  overlapping surfaces and is undefined. This is a partial commutative
  monoid — a real structure, and the right one, but not a monoid.

  Audit composition is not a binary operation on components. The
  definition of ⊕ sets A = compose(A_g₁, A_g₂) via shared
  interaction_id, and chain order is fixed by pipeline causality, which
  is a parameter of neither operand. Because AI2 chains each record to
  its predecessor, compose(A₁, A₂) and compose(A₂, A₁) differ at the
  byte level. Commutativity and associativity therefore hold for surface
  and constraint union, which is all the phased-deployment argument
  needs; they do not hold for the audit component, and no claim is made
  that they do.

APPLICATION TO CONCRETE COMPONENTS (EPG, ROC, MDR):
  The concrete solution components have read-only cross-surface data
  flows (R_soft) permitted by P3. These flows do not affect guarantee
  composition (Lemma 1), so the monoid properties hold for guarantees.
  However, the read-only flows mean that EVALUATION EFFECTIVENESS
  (not guarantees) depends on deployment composition — ROC is more
  effective with EPG's context than without it.

  Therefore: guarantee-level composition is commutative and associative.
  Effectiveness-level composition is not — deployment order matters
  for quality, even though it does not matter for formal guarantees.

This distinction is the mathematical basis for phased deployment:
  guarantees are safe in any order; effectiveness improves as more
  components provide cross-surface context.
```

---

---

## 13. Syllogistic Arguments for Solution Validity

These connect the formal model to the business case using classical deductive form.

### Syllogism 2 (Necessity of Prompt Governance)

```
Major Premise:
  Model outputs are conditioned on prompts (Axiom 2, pipeline definition).

Minor Premise:
  Ungoverned prompts have no guaranteed constraint satisfaction,
  therefore no auditable compliance posture for the prompt surface.

Conclusion:
  Without prompt governance, the prompt surface contributes unmanaged,
  unauditable compliance risk.

Contrapositive:
  If compliance risk on the prompt surface is managed and auditable,
  then prompt governance (or a functional equivalent) is deployed.
```

### Syllogism 3 (Necessity of Output Controls)

```
Major Premise:
  Prompt governance cannot guarantee output compliance
  (Axiom 2, Theorem 4).

Minor Premise:
  Output compliance is a regulatory requirement in governed industries.

Conclusion:
  Prompt governance alone is insufficient; output-level controls are
  necessary to address the residual risk on the output surface.
```

### Syllogism 4 (Sufficiency of the Composed Solution)

```
Major Premise:
  The composed solution covers all observable interaction surfaces
  (Theorem 5), given Axiom 3 preconditions P1-P3, enforcement
  architecture EA1-EA2, and audit integrity AI1-AI4.

Minor Premise:
  Each component provides deterministic, immutable, version-stamped
  audit records for its surfaces (Component Guarantee, Axiom 5),
  including degraded-state records during component failures (§7).

Conclusion:
  The composed solution provides an auditable compliance posture
  across the governed portion of the AI interaction pipeline,
  within the framework's threat model (T1-T6).

Scope of "auditable compliance posture" — what this DOES mean:
  - Every governed interaction has a tamper-evident audit chain
  - Every applicable constraint is evaluated with version stamps
  - Every component failure is recorded with declared posture
  - Observability gaps are explicitly documented

What this does NOT mean:
  - Every output is compliant (Theorem 4: residual risk > 0)
  - Ungoverned interactions are covered (EA3 detects, doesn't prevent)
  - Threats T7-T11 are addressed (requires ROC, MDR)
  - Tier 2 and Tier 3 elements are governed (Axiom 1)
  - Implementation defects are absent (Lemma 2 caveat)
```

### Syllogism 5 (Observability as Governance Precondition)

```
Major Premise:
  Governance requires evaluation of governed elements against
  constraints (Component Definition, §5.1).

Minor Premise:
  Evaluation requires observation of the element's state
  (Axiom 4: governable → observable).

Conclusion:
  Any interaction element that is not observable cannot be governed.
  The governance boundary is determined by the observability boundary.

Implication:
  Extending governance coverage requires extending observability first.
  Deploying a governance component without ensuring observability of
  its surfaces produces a guarantee that is formally vacuous.
```

---

---

## What must be true

Every theorem in this chapter is conditional. The conditions are named in
twenty-two places across the chapter, which is twenty-one too many for anyone who
has to sign an attestation. They are collected here.

The rightmost column is the one that matters. An assumption that fails silently
is worse than one that fails loudly, because the theorems keep reading as though
they hold.

| Assumption | What it requires | How it fails in practice |
|---|---|---|
| Axiom 1 (Interaction Model) | The interaction is described by `(x, u, M, θ, o)` | Tier 2 elements — retrieval index state, tool behavior, routing — influence output and are not in the tuple |
| Axiom 2 (Non-Determinism) | Governance assumes non-singleton output support | Rarely fails; the risk is a deployment that quietly assumes determinism at temperature 0 |
| Axiom 3 (Guarantee Independence) | P1-P3 below | See P1-P3 |
| Axiom 4 (Observability) | Governed elements are observable | A vendor endpoint that does not return the resolved prompt, or logs it lossily |
| Axiom 5 (Temporal Identity) | Every artifact carries the version in force at interaction time | A constraint edited between prompt assembly and output delivery, stamped once |
| P1 (Infrastructure Stability) | Shared storage, network and compute keep their guarantees as components are added | Loudly, under load — and this is the assumption most likely to be false in a real deployment |
| P2 (Pipeline Non-Interference) | No component silently drops or redirects an interaction | A retry or circuit-breaker that swallows an interaction without a record |
| P3 (Surface Isolation) | No shared mutable evaluation state between components | A shared cache or feature store introduced for performance |
| AI1 (Immutable Storage) | Records cannot be modified or deleted for the retention period | A lifecycle policy, a misconfigured Object Lock, an administrator with bucket-level rights. Silently |
| AI2 (Chain Integrity) | Each record hashes its predecessor | Rarely fails once built; detects modification, not deletion |
| AI3 (Record Signing) | Components sign via an independent KMS and hold no keys | A component with a local key, added for latency |
| AI4 (Independent Verification) | An external party can verify without any governance component | Verification tooling that reads through the system it is verifying |
| AI5 (Chain Truncation Detection) | Per-tier expected component sets are recorded and compared | Not implemented — the check has to exist, and its absence is invisible |
| AI6 (Retention and Jurisdiction) | Records retained per regulation, residency respected, regulated data protected | The audit store becomes the largest unreviewed PII repository in the estate |
| GIL1 (Pre-Component Registration) | Every governed interaction registers before processing | Registration inside a component rather than at the chokepoint |
| GIL2 (Independent Operation) | The GIL survives any component failure, and fails closed | A GIL that shares infrastructure with what it observes |
| GIL3 (Completeness Verification) | GIL entries are reconcilable against chain records | Reconciliation that is possible but never run |
| GIL4 (GIL Integrity) | The GIL carries AI1-AI6 | The GIL treated as telemetry rather than as evidence |
| EA1 (Chokepoint Enforcement) | No path to the model bypasses EPG; none to the user bypasses ROC | One unmanaged credential. Silently |
| EA2 (Identity Binding) | Every invocation binds to a governed project identity | Service accounts shared across projects |
| EA3 (Bypass Detection) | Ungoverned interactions are detected with probability at least a stated threshold | The threshold is never stated, so the requirement is unfalsifiable |
| EA4 (Constraint Authorship) | Constraint changes require dual control and audit | Break-glass used as routine |
| Lemma 2 side condition | Components are correctly implemented, introducing no new vulnerabilities | Always partly false; it is an engineering assumption, not a provable one |

Three of these deserve to be read twice, because they fail silently and every
audit-completeness result rests on them: **AI1**, **EA1**, and **AI5**. A
deployment that cannot evidence all three does not have the guarantees this
chapter proves, whatever its documentation says.

---

---

## Theorems stated with the requirements they justify

These three are proved over requirements rather than over the surface or
component model: the Global Interaction Log, the enforcement architecture, and
the audit integrity properties respectively. The chapter states what each
requirement buys; the results are here.

```
REQUIREMENT (Global Interaction Log — GIL):

A Global Interaction Log records the existence of every AI interaction
BEFORE any governance component processes it. The GIL is independent
of all governance components.

Properties:
  GIL1 (Pre-Component Registration):
    Every interaction i receives an interaction_id that is registered
    in the GIL before the interaction enters the governance pipeline.
    This registration occurs at the enforcement chokepoint (EA1),
    not within any governance component.

    ∀ i ∈ I_governed : ∃ gil_entry(i) with timestamp t_registered
      where t_registered < t_first_component_evaluation

  GIL2 (Independent Operation):
    The GIL operates independently of EPG, ROC, and MDR. A failure
    in any governance component does not affect GIL availability.
    The GIL has its own fail-closed posture: if the GIL is
    unavailable, no interaction proceeds.

  GIL3 (Completeness Verification):
    For any time window [t₁, t₂], it is possible to compare:
      - The set of interaction_ids registered in the GIL
      - The set of interaction_ids present in chain records
    Any GIL entry without a corresponding chain is a "ghost" —
    an interaction that was initiated but not fully governed.
    Ghosts MUST be investigated and classified as either:
      (a) component failure (degraded record should exist)
      (b) enforcement bypass (security incident — T4)
      (c) in-flight interaction (not yet completed)

  GIL4 (Integrity):
    The GIL is subject to the same integrity properties as audit
    records (AI1-AI6): immutable storage, signed entries,
    independent verification.

THEOREM 3b (Ghost Detection):

Given GIL properties GIL1-GIL4 and Audit Integrity AI1-AI5:

  Every interaction that enters the governance pipeline is either:
    (a) fully governed with a complete audit chain, OR
    (b) partially governed with degraded records in the chain, OR
    (c) detectable as a "ghost" via GIL completeness verification

  No interaction that enters the pipeline can be silently ungoverned.

  This is deliberately narrower than "no interaction can be silently
  ungoverned," which the GIL cannot support. An interaction that never
  reaches the enforcement chokepoint — a developer's own API credential,
  a model deployed outside the governed platform — receives no GIL entry
  and produces no chain record. Absent from both sets, it is invisible to
  GIL3's comparison, which detects interactions that entered and then
  vanished. Interactions that never entered are the subject of EA3
  (§6), where detection is probabilistic, deployment-specific, and
  depends on network egress controls and credential management rather
  than on anything the GIL can prove.

Proof:
  By GIL1, every governed interaction is registered before processing.
  By GIL2, registration is independent of component health.
  Case (a): All components operational → full chain (Theorem 3).
  Case (b): Component failure → degraded records (§7.2) in chain.
  Case (c): Neither full nor degraded records exist → GIL entry
    without chain match → ghost detected by GIL3.
  By GIL4, the GIL itself is tamper-evident.
  Therefore, silent ungoverned processing is impossible given
  GIL availability.  ∎

Note: If the GIL itself is unavailable, GIL2 mandates fail-closed:
no interactions proceed. This makes the GIL a critical-path
dependency — a deliberate architectural choice that prioritizes
governance completeness over availability for governed workloads.
```

```
THEOREM 1a (Conditional Full Governability):

γ(S_prompt) = full IFF enforcement architecture EA1-EA2 holds.

If EA1 is violated (interactions bypass EPG):
  γ(S_prompt) = partial — some prompts are governed, others are not.

If EA2 is violated (anonymous model calls exist):
  γ(S_prompt) = partial — ungoverned interactions exist.

Proof:
  γ = full requires that ALL elements of S_prompt are deterministically
  constrained prior to execution (Definition, §4.3).
  Without EA1, some interactions bypass constraint evaluation.
  Without EA2, some interactions are unattributable to governed projects.
  In either case, ∃ i where S_prompt is unconstrained → γ ≠ full.  ∎
```

```
THEOREM 3a (Tamper-Evident Audit Chain):

Given properties AI2-AI4, and supposing AI1 has been defeated for a
single record, any modification to any audit record in chain(i) is
detectable by an independent verifier.

Note on the premises: AI1 (immutable storage) is the control this
theorem backstops, not one of its premises. Assuming AI1 holds makes
the theorem vacuous — modification would be impossible by hypothesis.
The useful claim is the one an auditor asks for: if immutability is
bypassed for some record, whether by a misconfigured retention policy
or an administrator with lifecycle rights, the chain and the signatures
still reveal it.

Proof:
  Suppose record A_gₖ is modified after writing.
  By AI3, the signature on A_gₖ was computed over its original content
    with a key held only by the KMS; the modified record therefore
    fails signature verification on its own, independent of the chain.
  By AI2, A_g(k+1).chain_hash was computed from the original A_gₖ, so
    the modified A_gₖ also breaks the chain at position k+1. Repairing
    the chain requires re-signing every subsequent record, which AI3
    prevents without KMS compromise.
  By AI4, an independent verifier can recompute both and detect the
    discrepancies without relying on any governance component.
  The tail record is covered: signature verification does not depend on
    a successor existing.  ∎

Scope — deletion is a different attack. The chain detects modification.
Removing the tail of a chain leaves every remaining link valid and every
remaining signature verifying, so truncation is not detected
cryptographically. It is prevented by AI1 and detected by AI4's
completeness check against interaction_id generation logs. See the
Chain Truncation requirement below for the partial case.
```
