# Chapter 1 — The Architecture of Assurance

Every later chapter of this book is an instance of one idea developed here: that
the *evidence* an AI system produces about its own governance can be made
provable, even though its outputs cannot. The distinction is the whole point, and
it is easy to lose. What follows proves properties of the audit record — that it
is complete over the governed surfaces, that it is tamper-evident, that a missing
interaction is detectable — conditional on a set of assumptions enumerated at the
end of this chapter. It proves nothing about whether any particular output is
compliant; the chapters on ungrounded assertion and canonical form explain why no
such proof is available.

This chapter builds the foundation — the
axioms Clad rests on, the surfaces at which an interaction can be governed, the
model of a governance component, and the algebra by which components compose.

By the end of the chapter you will be able to:

- state the axioms Clad's guarantees depend on, and recognize when a deployment
  violates them;
- decompose an AI interaction into its five control surfaces and classify each
  surface's governability;
- describe a governance component as the tuple `g = (S, C, E, A, R)` and read the
  interface contract between two components;
- explain what the composition algebra guarantees when components are deployed
  together.

Clad formalizes a governance boundary that makes risk, responsibility, and evidentiary state explicit for regulated AI use. Rather than promising perfect prevention, it focuses on provability: it guarantees that every governed interaction produces auditable evidence of which rules were in effect, which evaluations occurred (or failed), and what artifacts and versions participated in the interaction. That provability is the core compliance deliverable for regulators and internal auditors.

Clad’s architecture combines enforcement points, a Global Interaction Log to prevent undetectable bypasses, and tamper‑evident audit chains with independent signing and Supervisor‑mediated degraded records. Together these elements ensure that failures and deviations are visible and investigable, enabling traceability, root‑cause analysis, and remediation. The framework formalizes the residual‑risk reality: governance reduces but does not eliminate risk from stochastic model behavior and external unknowns, which must be documented, monitored, and mitigated with companion controls.

This meta‑framework is the normative baseline for the component chapters that follow: implementers must map controls to the specified control surfaces, satisfy the architectural preconditions for composability, and adopt evidence formats that support independent verification and regulatory review. The three components developed in Part IV — Enterprise Prompt Governance (EPG), Runtime Output Controls (ROC), and Monitoring, Detection and Response (MDR) — are each independently rigorous but derive their scope, interfaces, and composability properties from the model built here. Those three abbreviations are used throughout the rest of the book: EPG governs the prompt before the model runs, ROC governs the output before it is delivered, and MDR watches the whole system over time.

---

## 1. Axioms

The following axioms are accepted as true about the world in which this governance system operates. They are not proven; they are the foundation on which all subsequent definitions, lemmas, and theorems rest. If any axiom is invalid in a specific deployment context, the theorems that depend on it must be re-evaluated.

### Axiom 1 (Interaction Model)

An AI interaction is described by the tuple:

```
∀ i ∈ I : i = (x, u, M, θ, o)

where:
  x ∈ X   — the assembled prompt, including any retrieved context,
             conversation history, and system instructions
  u ∈ U   — the user input that triggered the interaction
  M ∈ 𝓜   — the model identifier and version (a specific artifact, not
             an endpoint or family name)
  θ ∈ Θ   — the inference configuration: temperature, top-p, top-k,
             sampling parameters, tool availability, and any other
             parameters that affect the output distribution
  o ∈ O   — the model output
```

**Scope limitation:** This axiom models a single, non-agentic interaction turn. Multi-turn conversations are modeled as ordered sequences of interactions where each p_k includes relevant prior context from interactions i_1 through i_{k-1}. Agentic workflows (tool use, multi-model chains) are modeled as directed acyclic graphs of interactions where one interaction's output feeds into another's prompt. These extensions preserve the per-interaction governance properties but introduce additional composition concerns addressed in §12.

**Governance scope claim:** This tuple captures the elements within the governance scope of this framework. Elements outside this tuple — such as retrieval index state, external tool behavior, system-level routing, agent orchestration logic, and infrastructure configuration — may influence outputs but are classified as external factors per the three-tier model below. The framework does not claim that (x, u, M, θ, o) is a complete causal description of the output; it claims that these are the elements the governance system observes, constrains, and audits.

**Three-tier element classification:**

```
DEFINITION (Element Classification):

All factors that may influence an AI interaction's output are
classified into exactly one of three tiers:

Tier 1 — Modeled and Observable:
  Elements in the interaction tuple (x, u, M, θ, o).
  These are within governance scope: constrainable, evaluable, auditable.

Tier 2 — Known but Unmodeled:
  Elements that are identified as influencing outputs but are not
  captured in the interaction tuple. Examples:
    - RAG index state and ranking parameters
    - External tool implementations and their behavior
    - System-level routing, caching, and load balancing
    - Agent orchestration logic and internal scratchpads
    - Infrastructure differences across replicas
  These contribute documented ungoverned risk (Theorem 4, in the theorems appendix).
  Each deployment must enumerate its Tier 2 elements.

Tier 3 — Unknown / Emergent:
  Factors not yet identified that may influence outputs.
  These contribute undocumented residual risk.
  The governance system cannot account for Tier 3 elements by
  definition, but it must acknowledge their possible existence.
```

**What "prompt" includes:** The assembled prompt x is the complete input to the model at inference time. In RAG architectures, p includes retrieved documents (though the retrieval process itself is Tier 2). In multi-turn conversations, p includes conversation history. In systems with tool-use definitions, p includes tool schemas. The governance system governs p as assembled — the assembly process itself is within prompt governance scope insofar as it determines the content of p, but upstream factors that influence assembly (index state, retrieval ranking) are Tier 2.

**Statelessness assumption:** This axiom assumes stateless conditional generation: the model's output distribution is fully determined by (p, u, θ) with no hidden internal state. Models with persistent memory, non-reset hidden states, or recurrent reasoning chains that carry state across invocations violate this assumption. For such models, the internal state must be captured in p or θ, or the model must be classified as having reduced auditability on the temporal dimension (Axiom 5).

### Axiom 2 (Non-Determinism)

Governance systems must not assume deterministic model output. The output is sampled from a conditional distribution parameterized by the prompt, user input, model, and inference configuration.

```
M : X × U × Θ → Dist(O)
o ~ M(x, u, θ)

Formally: governance must be correct under the assumption that
  ¬∀ (x, u, M, θ) : |support(M(x, u, θ))| = 1

The output distribution M(x, u, θ) may have non-singleton support,
meaning different outputs are possible for identical inputs. The
degree of non-determinism is influenced by θ but cannot be assumed
to be zero for any configuration.
```

**Implication:** No governance mechanism operating solely on prompts can guarantee specific output properties. This is not a limitation of the governance system; it is a property of the governed artifact. Governance reduces the probability of non-compliant outputs; it does not eliminate it.

**Note on near-deterministic configurations:** Some inference configurations (e.g., temperature ≈ 0) produce near-deterministic output. The axiom does not claim high variance — it claims that governance systems must not *depend* on determinism, because (a) floating-point non-determinism in GPU computation prevents true determinism even at temperature 0, and (b) model updates can change output distributions without changing the API contract.

### Axiom 3 (Guarantee Independence, Conditional)

Each governance component's formal guarantees hold independently of whether other components are deployed, **provided that the following infrastructure preconditions are met:**

```
∀ g ∈ G, ∀ G' ⊆ G :
  Φ(g) holds in deployment({g}) ↔ Φ(g) holds in deployment(G')

  GIVEN preconditions P1, P2, P3:

  P1 (Infrastructure Stability):
    Infrastructure shared across components (storage, networking,
    compute) maintains its availability, capacity, and integrity
    guarantees independent of the number of deployed components.
    Adding a component does not degrade shared infrastructure below
    the thresholds required by existing components.

  P2 (Pipeline Non-Interference):
    No component UNILATERALLY prevents another component from
    receiving its governed artifacts, EXCEPT when blocking is a
    declared governance action (fail-closed posture per §7).

    Specifically:
    - No component silently drops or redirects interactions.
    - A component MAY block an interaction (fail-closed) if and only
      if: (a) the block is a declared governance action, AND
          (b) the blocking event is recorded in the audit chain
              (as a degraded record or a policy-enforcement record), AND
          (c) downstream components are notified of the block so they
              can record their own "interaction blocked upstream" entry.

    This exemption resolves the conflict between fail-closed posture
    (§7) and pipeline non-interference: blocking IS interference, but
    it is AUTHORIZED, RECORDED interference — distinguishable from
    silent bypass or malicious short-circuiting.

  P3 (Surface Isolation with Read-Only Cross-Flow):
    Components' EVALUATION GUARANTEES are determined solely by
    artifacts within their own governed surfaces. However, components
    MAY receive read-only data from other surfaces via soft
    requirements (R_soft) to enhance effectiveness.

    Formally, P3 distinguishes two types of cross-surface data flow:

    ALLOWED — Read-only cross-surface data (soft dependencies):
      A component g₂ may read artifacts from S_g₁ (e.g., ROC reads
      prompt context from EPG). This does NOT violate P3 because:
      - g₂'s guarantee Φ(g₂) holds even without this data (R_soft)
      - g₂ does not modify S_g₁ artifacts
      - The data flow is one-directional and read-only

    PROHIBITED — Shared mutable state affecting evaluation:
      Components must not share writable state that any component's
      evaluation function E_g depends on for its guarantee. Examples:
      - Two components writing to the same constraint store
      - A component modifying artifacts another component evaluates
      - Shared caches whose state affects evaluation outcomes

    Infrastructure services (GIL, Supervisor, KMS) are shared but
    are NOT governance components — they do not evaluate constraints
    or produce evaluation records. Their availability is covered by
    P1 (Infrastructure Stability), not P3.
```

**What this claims:** Given P1-P3, EPG's guarantee (every prompt is evaluated against all applicable constraints and a complete audit record is produced) does not require ROC to be present. ROC's guarantee does not require EPG. The guarantees are independent.

**What this does NOT claim:** It does not claim that governed *artifacts* are independent — prompts causally influence outputs. It does not claim that infrastructure is automatically stable under composition — P1 must be verified. It does not claim that pipeline interference is impossible — P2 must be architecturally enforced.

**If preconditions are violated:** If P1 fails (e.g., adding ROC causes audit storage to degrade), Φ(g) for other components may be compromised. If P2 fails (e.g., ROC short-circuits requests before EPG processes them), Φ(EPG) is violated. If P3 fails (e.g., components share mutable constraint state), evaluation determinism is compromised. Each violation must be detectable and reported (see §6: Enforcement Model).

**Why the conditional claim is sufficient:** Phased adoption requires that deploying component A alone provides A's full guarantees. P1-P3 are architectural requirements that a competent infrastructure team can verify and maintain. The preconditions are testable, not aspirational — they can be validated through capacity testing (P1), pipeline tracing (P2), and architecture review (P3).

**Status note:** This axiom is a *design goal* that requires architectural enforcement, not a fact about the world. Downstream theorems that depend on Axiom 3 are conditional on P1-P3. This conditionality is explicit in each dependent proof.

### Axiom 4 (Observability)

All elements of an AI interaction that are relevant to governance must be observable and recordable by the governance system.

```
∀ e ∈ elements(i) governed by some g ∈ G :
  ∃ observe(e) : e → record(e)
  where record(e) is capturable, storable, and retrievable at any future time t

The governance scope is bounded by the observability boundary:
  governable(e) → observable(e)
  ¬observable(e) → ¬governable(e)
```

**Implication:** If the full assembled prompt is not logged, it cannot be governed or audited. If inference parameters are not recorded, they cannot be constrained. If model version is not tracked, temporal audit is impossible. Observability is a precondition for governance, not a consequence of it.

**Contrapositive (critical for architecture):** Any interaction element that is not observable is, by definition, outside the governance scope. The governance system must explicitly declare its observability boundary and acknowledge that elements beyond it contribute ungoverned risk.

### Axiom 5 (Temporal Identity)

Models, constraints, configurations, and all governed artifacts are versioned entities whose identity includes their state at a specific point in time.

```
∀ x ∈ {M, C, Θ, P}, ∀ t₁, t₂ ∈ T :
  x(t₁) and x(t₂) are potentially distinct artifacts
  ∧ identity(x, t) = (x, ver(x, t))

Specifically for models:
  A model endpoint updated between interactions i₁ and i₂ is treated
  as two distinct models for governance purposes:
    m(t₁) ≠ m(t₂) if ver(m, t₁) ≠ ver(m, t₂)
    even if endpoint(m, t₁) = endpoint(m, t₂)
```

**Implication:** Audit records must capture the version of every governed element at the time of the interaction. "Which model was used?" is not answered by an endpoint URL — it is answered by a versioned model identifier. "Which constraints were in effect?" is not answered by the current constraint set — it is answered by the versioned constraint set at interaction time.

**Practical constraint:** This axiom requires that all governed elements expose version information. If a model provider does not expose model version (only an endpoint), the governance system must either (a) record whatever version signal is available (e.g., response headers, model ID strings) and document the limitation, or (b) classify the model as having reduced auditability on the temporal dimension.

---

### Design Requirement: Audit Linkability

This is an engineering requirement on the implementation rather than a property of the world, so it is stated here as a design requirement rather than an axiom.

```
REQUIREMENT (Audit Linkability):

Audit records produced by different governance components for the same
interaction must share a propagated interaction identifier enabling
chain composition.

  ∀ g₁, g₂ ∈ G, ∀ i ∈ I :
    A_g₁(i).interaction_id = A_g₂(i).interaction_id

The identifier must be:
  - Generated at interaction initiation (before any component processes it)
  - Propagated through the pipeline without modification
  - Included in every component's audit record
  - Resistant to spoofing, duplication, and accidental collision

IMPLEMENTATION NOTE: This is a distributed tracing problem with
well-established solutions (e.g., W3C Trace Context, OpenTelemetry).
The meta-framework does not prescribe an implementation — it requires
the property.
```

### Design Requirement: Global Interaction Log

This requirement addresses the "Ghost Chain" problem: Theorem 3a proves that existing records cannot be tampered with, but it does not prove that every interaction *has* a record. A bypassed or failed enforcement point could leave an interaction completely unrecorded — indistinguishable from "no interaction occurred."

Together these four properties make every interaction that enters the pipeline
either fully governed, governed with degraded records, or detectable as a gap.
The Ghost Detection theorem states this precisely, and states what it does not
cover.


---

## 2. Threat Model

A governance framework without an explicit threat model is incomplete for regulated industries. This section enumerates the adversary types, in-scope threats, and explicitly out-of-scope threats. All theorems and guarantees in subsequent sections are conditional on this threat model — they hold against the in-scope threats and make no claims about out-of-scope threats.

### 2.1 Adversary Types

```
DEFINITION (Adversary Classification):

A1 — External User (Adversarial):
  Capability: crafts inputs u to elicit non-compliant outputs.
  Motivation: data exfiltration, policy bypass, boundary probing.
  Knowledge: may have partial knowledge of prompt structure, constraints,
  and model behavior through iterative probing.

A2 — Insider (Malicious or Compromised):
  Capability: authors or modifies constraints, attestations, or prompts
  within their authorized scope.
  Motivation: weaken governance, create backdoors, exfiltrate data.
  Knowledge: full knowledge of governance structure within their domain.

A3 — Compromised Vendor / Supply Chain:
  Capability: provides models with hidden behaviors (backdoors, data
  leakage triggers, biased outputs under specific inputs).
  Motivation: espionage, competitive advantage, sabotage.
  Knowledge: full knowledge of model internals (opaque to governance).

A4 — Regulator as Adversarial Auditor:
  Capability: requests complete audit evidence for any interaction,
  tests claims against evidence, probes for gaps.
  Motivation: verify compliance, identify violations.
  Knowledge: full knowledge of governance claims and framework structure.
```

### 2.2 In-Scope Threats

These threats are addressed by the governance components defined in this framework. For each, the responsible component is identified.

```
T1 — Policy Drift and Misconfiguration:
  Constraints change over time without trace; model or inference
  config changes without governance awareness.
  Addressed by: EPG (constraint versioning), MDR (drift monitoring),
  Axiom 5 (temporal identity).

T2 — Incomplete or Absent Audit Trail:
  Interactions processed without evaluation or logging; audit records
  missing, incomplete, or tampered with.
  Addressed by: Audit chain (§8), Audit integrity (§8.4), Component
  guarantees (§5.2), Failure semantics (§5.3).

T3 — Constraint Authoring Abuse (Insider — A2):
  Malicious or negligent constraint authors weaken governance by
  creating trivially satisfiable constraints, disabling constraints
  for specific projects, or decomposing semantic constraints in
  bad faith.
  Addressed by: EPG (RBAC, separation of duties, dual control for
  constraint authorship, decomposition attestation). This chapter
  defines the requirement; EPG specifies controls.

  ADDITIONAL REQUIREMENT (Decomposition Verification):
    Semantic constraint decompositions (mechanical + procedural)
    must be subject to periodic validation ensuring the decomposed
    checks actually prevent the behavior the original semantic
    constraint intended to prevent. This requires:
      - Independent red-team testing of decomposed constraints
      - Periodic re-attestation by domain experts other than the
        original decomposition authors
      - Automated regression testing with known-bad inputs that
        mechanical checks must catch
    Without this, a formally "passing" decomposition can functionally
    fail — the "auditing a lie" problem. EPG must specify the
    verification protocol and minimum testing cadence.

T4 — Governance Bypass:
  Interactions routed around governance components (shadow AI, direct
  API access, local deployments outside governed platform).
  Addressed by: Enforcement model (§6), MDR (bypass detection).

T5 — Adversarial User Input (A1):
  Users craft inputs designed to elicit non-compliant outputs,
  extract data, or probe policy boundaries.
  Addressed by: ROC (output evaluation/filtering), MDR (input
  monitoring, rate limiting, anomaly detection).
  Note: prompt governance (EPG) reduces but cannot eliminate this
  risk per Axiom 2 and Theorem 4.

T6 — Component Failure During Interaction:
  A governance component becomes unavailable mid-interaction,
  leaving interactions ungoverned.
  Addressed by: Failure semantics (§5.3).
```

### 2.3 Acknowledged Threats — Out of Meta-Framework Scope

These threats are real but addressed by companion documents or external disciplines. The meta-framework explicitly does not claim to address them.

```
T7 — Prompt Injection (Direct and Indirect):
  Untrusted content (user input, retrieved documents, tool outputs)
  embedded in the prompt overrides governance instructions.
  Scope: ROC (runtime output controls) and MDR (input sanitization,
  output classification). The meta-framework acknowledges this as
  R_input risk. EPG can require "prompt must include injection
  defenses" as a constraint, but cannot prevent injection at the
  framework level.

T8 — Model Jailbreaking:
  Adversarial inputs that cause the model to ignore its instructions.
  Scope: ROC (output filtering), MDR (red-team testing, monitoring).
  The meta-framework models this as part of Axiom 2 (non-determinism)
  and Theorem 4 (irreducible residual risk). It cannot be solved at
  the governance level — it requires model-level and output-level
  controls.

T9 — Model Poisoning / Supply Chain Compromise (A3):
  Models with hidden backdoors or biased training data.
  Scope: External to this framework entirely. Model governance
  (training data audits, model cards, bias testing) is a distinct
  discipline. MDR can verify model selection criteria but cannot
  inspect model internals (§13.2).

T10 — Training Data Leakage / Memorization:
  Models reproduce training data (PII, copyrighted content) in outputs.
  Scope: ROC (output filtering for PII/sensitive content), MDR
  (monitoring for data leakage patterns). The meta-framework classifies
  model internals as γ = external.

T11 — Cross-Tenant Data Leakage (Multi-Tenant Providers):
  In shared model deployments, data from one tenant influences
  outputs for another.
  Scope: Infrastructure security (outside this framework). MDR can
  monitor for anomalous cross-tenant patterns.
```

### 2.4 Threat Model Completeness Statement

```
DEFINITION (Threat Model Scope):

This framework's guarantees are valid against threats T1-T6.
They are NOT valid against threats T7-T11 without deployment of
the companion controls specified for ROC and MDR.

Any claim of "complete governance" must be qualified:
  "Complete within the meta-framework's threat model (T1-T6),
   with residual risks from T7-T11 addressed by companion controls."
```

---

## 3. Ontology (Primitive Sets and Pipeline)

### 2.1 Primitive Sets

```
I  = set of all AI interactions            — the fundamental unit of governance
P  = set of all prompts                    — assembled instructions sent to the model
U  = set of all user inputs                — runtime input from end users
M  = set of all models                     — AI models as versioned artifacts
Θ  = set of all inference configurations   — parameters affecting output distribution
O  = set of all outputs                    — raw model outputs
O' = set of all delivered outputs           — outputs after post-processing/filtering
T  = (ℝ, ≤) — totally ordered time domain  — for versioning and temporal audit
Σ  = set of all control surfaces           — partitioned elements of the interaction
G  = set of all governance components       — the building blocks of the solution
V  = set of all audit records              — evaluation evidence
```

### 2.2 The AI Interaction Pipeline

An AI interaction is a temporally ordered sequence of transformations:

```
DEFINITION (AI Interaction Pipeline):

An interaction i ∈ I is a tuple i = (x, u, M, θ, o, o', t) where:

  p  ∈ P   — the assembled prompt (including retrieved context,
              conversation history, system instructions, tool schemas)
  u  ∈ U   — user input at runtime
  m  ∈ M   — the model as a versioned artifact: identity(m, t)
  θ  ∈ Θ   — inference configuration (temperature, top-p, tool
              availability, sampling parameters)
  o  ∈ O   — raw model output:  o ~ M(x, u, θ)
  o' ∈ O'  — delivered output:   o' = filter(o)  where filter ∈ runtime controls
  t  ∈ T   — timestamp of the interaction

The pipeline has a causal ordering:
  x precedes u  (prompt exists before user input arrives)
  (p, u, θ) precedes o  (output is conditioned on all three)
  o precedes o'  (filtering follows generation)
```

---

## 4. Control Surface Decomposition

### 3.1 Definition

```
DEFINITION (Control Surface):

A control surface S ∈ Σ is a subset of the interaction pipeline elements
that can be governed. Each surface has:

  - elements(S) ⊆ {p, u, m, θ, o, o'}  — which pipeline elements it covers
  - governability(S) ∈ {full, partial, external}  — degree of control
  - owner(S) → G  — which governance component is responsible
```

### 3.2 Surface Partitioning

```
DEFINITION (Governance Surface Partition):

The interaction pipeline decomposes into five control surfaces:

  S_prompt   = {p}       — prompt content and structure
  S_input    = {u}       — user input at runtime
  S_config   = {m, θ}    — model selection and inference configuration
  S_output   = {o}       — raw model output
  S_delivery = {o'}      — post-processing and delivery

These are exhaustive and disjoint:
  S_prompt ∪ S_input ∪ S_config ∪ S_output ∪ S_delivery
    = {p, u, m, θ, o, o'}
  ∀ Sᵢ, Sⱼ : i ≠ j → Sᵢ ∩ Sⱼ = ∅
```

### 3.3 Governability Classification

```
DEFINITION (Governability):

A control surface S has governability class γ(S):

  γ(S) = full      iff ∃ mechanism that deterministically constrains all
                        elements of S prior to or during interaction execution,
                        AND all elements of S are observable (Axiom 4)

  γ(S) = partial   iff ∃ mechanism that influences but cannot deterministically
                        constrain elements of S, OR some elements are observable
                        but not fully constrainable

  γ(S) = external  iff elements of S are outside the solution's observability
                        boundary entirely
```

**Classification of surfaces:**

```
  γ(S_prompt)   = full       — prompts are authored within constraints before
                                execution; fully observable and constrainable

  γ(S_input)    = partial    — input validation constrains form (length, format,
                                injection patterns) but not semantic content;
                                observable but not fully constrainable

  γ(S_config)   = partial    — model selection and inference parameters (θ) are
                                fully constrainable; model internals/weights are
                                external. Mixed governability within this surface.
                                Decomposition:
                                  γ(model selection) = full
                                  γ(θ)               = full
                                  γ(model internals)  = external

  γ(S_output)   = partial    — output is stochastic (Axiom 2); influenced by
                                S_prompt and S_config governance, not determined
                                by it; observable post-generation

  γ(S_delivery) = full       — post-processing/filtering is deterministic
                                and fully controllable; observable
```

## 5. Governance Component Model

### 4.1 Component Definition

```
DEFINITION (Governance Component):

A governance component g ∈ G is a tuple g = (S_g, C_g, E_g, A_g, R_g) where:

  S_g ⊆ Σ              — the control surfaces this component governs
  C_g                   — the constraint set this component enforces
  E_g : artifact(S_g) × C_g → V
                        — the evaluation function (produces audit records)
  A_g ⊆ V              — the audit records this component produces
  R_g                   — the requirements this component has of other components
                          (may be ∅ for independently deployable components)

Precondition (from Axiom 4):
  ∀ s ∈ S_g : observable(s) = true
  A component can only govern surfaces whose elements are observable.
```

### 4.2 Component Guarantees

```
DEFINITION (Component Guarantee):

A component g provides guarantee Φ(g) iff:

  ∀ i ∈ I_governed_by_g :
    E_g is total over C_g                   — every constraint is evaluated
                                               (completeness)
    ∧ E_g is deterministic                   — same inputs produce same
                                               evaluation (repeatability)
    ∧ A_g(i) is immutable                    — audit records cannot be
                                               altered post-hoc
    ∧ A_g(i) contains interaction_id         — enables chain composition
                                               (Design Requirement)
    ∧ A_g(i) contains ver(x, t) for all      — all governed elements are
      governed elements x (Axiom 5)             version-stamped
```

## 6. Enforcement Model

The governance framework describes what SHOULD happen. The enforcement model defines HOW compliance is ensured at runtime. Without enforcement, governance is advisory — which contradicts the "inviolable" property required for regulated industries.

### 5.1 Enforcement Points

```
DEFINITION (Enforcement Point):

An enforcement point is a location in the interaction pipeline where
governance constraints are evaluated and non-compliant interactions
are blocked, modified, or flagged.

  EP = { ep₁, ep₂, ..., epₙ }

Each enforcement point has:
  - location(ep) ∈ pipeline stages   — where in the pipeline it operates
  - mode(ep) ∈ {blocking, flagging}  — whether it can stop interactions
  - component(ep) → g                — which governance component operates it
  - bypass_resistance(ep) ∈ {strong, moderate, weak}
```

### 5.2 Required Enforcement Architecture

```
DEFINITION (Enforcement Architecture):

For the governance system to provide its guarantees, the following
enforcement properties must hold:

EA1 (Chokepoint Enforcement):
  All AI interactions MUST pass through governed enforcement points.
  No path from user input to model invocation may bypass EPG.
  No path from model output to user delivery may bypass ROC.

  Formally: ∀ i ∈ I_governed :
    ∃ ep ∈ EP : component(ep) = g_EPG ∧ i passes through ep
    ∧ ∃ ep' ∈ EP : component(ep') = g_ROC ∧ i passes through ep'

EA2 (Identity Binding):
  Every model invocation is bound to a governed project identity.
  Anonymous or unattributed model calls are blocked by default.
  This prevents shadow AI and ungoverned deployments.

  Formally: ∀ i ∈ I :
    ∃ project_id(i) ∈ governed_projects
    ∨ i is blocked

EA3 (Bypass Detection):
  The system MUST detect and alert on interactions that circumvent
  governance enforcement points. Detection mechanisms include:
    - Network-level monitoring for direct model API calls
    - API key / credential management restricting model access
    - Anomaly detection for ungoverned interaction patterns

  Formally: ∀ i ∈ I_ungoverned :
    P(detect(i)) ≥ detection_threshold (deployment-specific)

EA4 (Enforcement of Constraint Authorship):
  Constraint creation, modification, and deletion require:
    - Authenticated identity with authorized scope
    - Separation of duties: author ≠ sole approver
    - Audit trail of all constraint changes
  This addresses insider threat T3 from §2.2.
```

### 5.3 Governability Conditioned on Enforcement

Full governability of a surface is therefore conditional on enforcement, not a
property of the surface alone: without a chokepoint, a surface classified as fully
governable is only fully governable for the interactions that happen to pass
through the governed path. The Conditional Full Governability theorem states the
condition.


### 5.4 Enforcement Failure Modes

See §7 (Failure Semantics) for how enforcement point failures are handled.

---

## 7. Failure Semantics

Governance components can fail. The framework must define what happens when they do. A framework silent on failure semantics does not say what its guarantees mean during an outage, which is when they matter most.

### 7.1 Failure Postures

```
DEFINITION (Failure Posture):

Each governance component and enforcement point declares a failure
posture:

  fail_posture(g) ∈ {fail-closed, fail-open-flagged, fail-open}

  fail-closed:
    If the component cannot evaluate or log, the interaction is BLOCKED.
    No interaction proceeds without governance.
    Appropriate for: high-risk workloads, regulated data, safety-critical.

  fail-open-flagged:
    If the component cannot evaluate or log, the interaction proceeds
    but is FLAGGED as ungoverned in the audit trail with a degraded-
    state indicator. A separate monitoring alert is generated.
    Appropriate for: medium-risk workloads where availability matters.

  fail-open:
    If the component cannot evaluate or log, the interaction proceeds
    silently. NOT RECOMMENDED for any regulated workload.
    This posture exists to model legacy/uncontrolled deployments.
```

### 7.2 Degraded State Audit Records

```
DEFINITION (Degraded Audit Record):

When a component g fails during interaction i, a degraded audit
record is produced:

  A_g_degraded(i, t) = {
    interaction_id   : identifier(i)
    timestamp        : t
    component        : g
    status           : DEGRADED
    failure_reason   : description of failure
    posture_applied  : fail_posture(g)
    action_taken     : {blocked, proceeded_flagged, proceeded_silent}
  }

This record is written to the audit chain in place of the normal
A_g(i, t), preserving chain completeness even during failures.
```

### 7.3 Component Guarantee Under Failure

```
DEFINITION (Component Guarantee Under Failure):

A component g provides guarantee Φ(g) iff:

  For all interactions i where g is operational:
    E_g is total over C_g                    — completeness
    ∧ E_g is deterministic                   — repeatability
    ∧ A_g(i) is immutable                    — tamper resistance
    ∧ A_g(i) contains interaction_id         — chain linkage
    ∧ A_g(i) contains ver(x, t) for all      — version stamps
      governed elements x

  For all interactions i where g has failed:
    A_g_degraded(i, t) is produced           — failure is recorded
    ∧ fail_posture(g) is enforced            — declared posture applies
    ∧ monitoring alert is generated           — failure is detected

Φ(g) is thus a guarantee about BOTH normal and failure modes.
The component either governs the interaction fully OR records
that it could not and applies the declared failure posture.
```

### 7.4 Supervisor Signing Protocol for Degraded Records

*Added after Round 2 review identified that a crashed component cannot call KMS to sign its own degraded record, breaking the cryptographic chain (AI2-AI3).*

```
DEFINITION (Governance Supervisor):

A Governance Supervisor is an infrastructure service that:
  - Monitors the health of all governance components
  - Produces and signs degraded audit records on behalf of
    failed components
  - Operates independently of any individual governance component
  - Has its own KMS-managed signing key (distinct from component keys)

PROTOCOL (Degraded Record Signing):

When component g fails during interaction i:

  1. The Supervisor detects g's failure (via health check or timeout).
  2. The Supervisor generates A_g_degraded(i, t) containing:
     - The failure reason and posture applied
     - The Supervisor's identity (not g's)
     - A signature using the Supervisor's KMS key
  3. The degraded record includes:
     signed_by    : supervisor_id (NOT component g)
     signing_key  : KMS_key_supervisor
     on_behalf_of : g
  4. The Merkle chain continues: the degraded record's chain_hash
     is computed from the predecessor, maintaining AI2 integrity.

PROPERTY (Chain Continuity Under Failure):

  The audit chain remains cryptographically intact even when a
  component fails, because:
  - The Supervisor signs on behalf of the failed component
  - The chain_hash links through the degraded record
  - Independent verifiers can distinguish Supervisor-signed records
    from component-signed records (different key identity)
  - A Supervisor-signed record is weaker evidence than a component-
    signed record (the Supervisor did not perform evaluation), but
    it is stronger than a gap in the chain (which is undetectable
    without the GIL)
```

---

## 8. Interface Contracts

### 8.1 Contract Definition

```
DEFINITION (Interface Contract):

An interface contract between components g₁ and g₂ is a tuple
K(g₁, g₂) = (provides, requires, handoff) where:

  provides(g₁ → g₂)  — what g₁ makes available to g₂
  requires(g₂ ← g₁)  — what g₂ needs from g₁ to function optimally
  handoff(g₁, g₂)    — the data and identifiers exchanged at the boundary

A contract is SATISFIED iff:
  provides(g₁ → g₂) ⊇ requires(g₂ ← g₁)
```

### 8.2 Contract Optionality

```
DEFINITION (Hard vs. Soft Requirements):

  R_hard(g) ⊆ R_g   — requirements without which g cannot produce Φ(g)
  R_soft(g) ⊆ R_g   — requirements that enhance g's effectiveness but
                        are not necessary for Φ(g)

For independently deployable components (required by Axiom 3):
  R_hard(g) = ∅

For dependent components:
  R_hard(g) ≠ ∅      — the component requires another component's output
```

### 8.3 Handoff Protocol

```
DEFINITION (Handoff):

A handoff between g₁ and g₂ for interaction i is:

  handoff(g₁, g₂, i) = {
    interaction_id   : identifier(i)       — shared linking key
    artifact         : output(g₁, i)       — the governed artifact
    artifact_hash    : hash(artifact)       — integrity verification
    audit_ref        : pointer(A_g₁(i))    — reference to g₁'s audit record
    constraint_ctx   : C_g₁                — the constraints g₁ enforced
    version_manifest : {ver(x, t) | x ∈    — version stamps of all governed
                       elements(S_g₁)}       elements (Axiom 5)
  }
```

## 9. Audit Chain

### 6.1 Audit Record Definition

```
DEFINITION (Component Audit Record):

For component g and interaction i at time t:

  A_g(i, t) = {
    interaction_id   : identifier(i)
    timestamp        : t
    component        : g
    surface          : S_g
    evaluations      : { (c, ver(c, t), eval(c, artifact(i))) | c ∈ C_g }
    artifact_hash    : hash(artifact(i))
    version_manifest : { (x, ver(x, t)) | x ∈ elements(S_g) }   — Axiom 5
    predecessor      : pointer(A_g'(i, t)) | ⊥
    observability_note : any elements of S_g not fully observable  — Axiom 4
  }
```

### 6.2 Chain Composition

```
DEFINITION (Audit Chain):

An audit chain for interaction i is an ordered sequence of
component audit records:

  chain(i) = [A_g₁(i, t), A_g₂(i, t), ..., A_gₙ(i, t)]

  where:
    ∀ k ∈ {2,...,n} : A_gₖ.predecessor = A_{g(k-1)}      — linked
    ∧ ∀ k : A_gₖ.interaction_id = identifier(i)            — same interaction
    ∧ ∀ k : artifact integrity is verifiable via hash chain  — tamper-evident
```

### 9.4 Audit Integrity Properties

An artifact hash alone is insufficient for tamper resistance in regulated industries: a malicious administrator or compromised system could rewrite records and recompute the hashes. The properties below close that gap.

```
DEFINITION (Audit Integrity Requirements):

The audit system must satisfy the following integrity properties:

AI1 (Immutable Storage):
  Audit records, once written, cannot be modified or deleted by
  any party, including system administrators, for the duration
  of the regulatory retention period.
  Implementation approaches: WORM storage (e.g., S3 Object Lock,
  Azure Immutable Blob), append-only databases, write-once volumes.

AI2 (Cryptographic Chain Integrity):
  Each audit record A_gₖ includes a cryptographic hash of its
  predecessor record, forming a Merkle-like chain:

    A_gₖ.chain_hash = hash(A_g(k-1).chain_hash ∥ A_gₖ.content)

  Tampering with any record in the chain invalidates all subsequent
  chain_hash values, making modification detectable.

AI3 (Record Signing):
  Each audit record is signed by the producing component using a
  key managed by an independent key management service (KMS):

    A_g(i, t).signature = sign(KMS_key_g, A_g(i, t).content)

  No component holds its own signing key. Key management is
  independent of component operation, preventing a compromised
  component from forging records.

AI4 (Independent Verification):
  An external auditor or independent system can verify the integrity
  of the audit chain without relying on any governance component:
    - Chain_hash values can be independently recomputed
    - Signatures can be verified against KMS public keys
    - Record completeness can be verified against interaction_id
      generation logs

AI5 (Chain Truncation Detection):
  Whole-interaction deletion is detected by AI4's comparison against
  interaction_id generation logs. PARTIAL truncation is not: dropping
  ROC's record while keeping EPG's leaves a chain whose every remaining
  link verifies and whose every remaining signature is valid, under an
  interaction_id that is present as expected.

  The audit system MUST therefore record, per interaction, the set of
  components its risk tier requires, and verification MUST compare the
  chain against that set:

    ∀ i ∈ I_governed :
      expected_components(tier(i)) ⊆ {g | A_g ∈ chain(i)}
      ∨ ∃ degraded_record(g, i) for each g in the difference

  A chain shorter than its tier requires, with no degraded record
  accounting for the gap, is a truncation and MUST be investigated on
  the GIL3 ghost pathway. Without this check the cryptographic chain
  provides no protection against a truncating adversary, because
  truncation is not modification.

AI6 (Retention and Jurisdiction):
  Audit records must be:
    - Retained for the regulatory minimum period applicable to the
      deployment context (e.g., 6 years for SOX, as required by
      HIPAA, as specified by sector regulators)
    - Stored in jurisdictions compliant with applicable data
      residency requirements (GDPR, state privacy laws)
    - Protected when they contain PII/PHI: encrypted at rest,
      access-controlled, with PII minimization where possible
      (e.g., hashing identifiers in logs rather than storing plaintext)
```

These properties are what make the chain tamper-evident: modification of any
record is detectable by an independent verifier even if immutable storage is
bypassed for one of them. The Tamper-Evident Audit Chain theorem states the result
and the case it does not cover, which is deletion.


---

## 10. Risk Model

### 7.1 Risk Attribution

```
DEFINITION (Compliance Risk):

For an interaction i, define compliance risk R(i) as the probability
that the delivered output o' violates a compliance requirement.

By the pipeline model:
  R(i) = P(o' violates requirement | p, u, m, θ)

This risk is attributable to multiple surfaces:

  R(i) = R_prompt(i) + R_input(i) + R_config(i) + R_output(i) + R_delivery(i)

  where each R_Sₖ represents risk attributable to surface Sₖ:
    R_prompt    — risk from inadequate prompt constraints
    R_input     — risk from adversarial or edge-case user input
    R_config    — risk from inappropriate model selection or inference
                  parameters (e.g., high temperature for safety-critical task)
    R_output    — risk from raw output content (model behavior)
    R_delivery  — risk from post-processing errors

MODELING SIMPLIFICATION (Risk Additivity):
  The additive decomposition R(i) = ΣR_Sₖ is a modeling simplification,
  not a claim of statistical independence. In practice, risks interact:
  a weak prompt constraint combined with high temperature (R_prompt ×
  R_config) may produce higher risk than either alone. The additive
  model is used for ATTRIBUTION (which surface contributes what risk)
  not for precise QUANTIFICATION. Implementations requiring precise
  risk measurement should use empirical methods, not this decomposition.

ADDITIONAL RISK: Infrastructure Dependencies
  The governance infrastructure itself introduces risk:
    R_GIL       — risk from GIL unavailability (fail-closed blocks all
                  governed interactions; single critical-path dependency)
    R_supervisor — risk from Supervisor unavailability (degraded records
                  cannot be signed; chain integrity weakened)
  These are operational risks of the governance system, not compliance
  risks of the AI interaction. They must be managed through standard
  HA/DR practices for critical infrastructure.

RISK-TIERED GOVERNANCE:
  Not all interactions require the same governance intensity. Deployments
  SHOULD define risk tiers that bind data classification to governance
  posture:

    Tier: Critical (PHI, PCI, financial reporting)
      fail_posture: fail-closed (mandatory)
      audit: full chain with AI1-AI6
      constraints: full EPG + ROC + MDR

    Tier: Standard (internal tools, non-sensitive data)
      fail_posture: fail-open-flagged
      audit: full chain, sampling permitted for volume management
      constraints: EPG + ROC, MDR optional

    Tier: Low (development, sandbox, experimentation)
      fail_posture: fail-open-flagged or fail-open
      audit: interaction logging, chain optional
      constraints: enterprise-level EPG only

  The specific tier definitions are deployment-specific. The meta-
  framework requires that tiers exist and that the binding between
  data classification and governance posture is itself a governed,
  auditable constraint (subject to EA4 change control).
```

### 10.2 Governance as Risk Reduction

```
DEFINITION (Risk Reduction):

A governance component g with guarantee Φ(g) reduces the risk
attributable to its governed surface:

  R_Sg(i | g deployed) ≤ R_Sg(i | g not deployed)

The residual risk after deploying g is:
  R_Sg_residual = R_Sg(i | g deployed)
```

### 7.4 Prompt Governance Risk Reduction Syllogism

```
SYLLOGISM 1 (Prompt-to-Output Risk Transfer):

Major Premise:
  The probability distribution of model outputs is conditioned on the
  prompt and inference configuration.
  Formally: o ~ M(x, u, θ), therefore P(o | p₁, u, θ) ≠ P(o | p₂, u, θ)
  in general.

Minor Premise:
  Prompt governance constrains prompts to satisfy properties that reduce
  the probability of non-compliant outputs.
  Formally: x ⊨ C*(project) → P(o violates | p, u, θ) ≤ P(o violates | p', u, θ)
  where x' is an ungoverned prompt.

Conclusion:
  Prompt governance reduces the probability of non-compliant outputs
  for the class of violations attributable to prompt deficiency.
  Formally: R_prompt(i | EPG deployed) < R_prompt(i | EPG not deployed)

Note: This does NOT claim R_input, R_config, R_output are reduced by
prompt governance. Those risks require their own governance components.
```

## 11. Component Instantiation

### 8.1 Solution Component Map

```
DEFINITION (Solution Components):

The Clad instantiates the following:

  g_EPG = (
    S      = {S_prompt},
    C      = constraint hierarchy (deontic),
    E      = mechanical evaluation ∪ procedural attestation,
    A      = prompt audit records,
    R_hard = ∅                          — independently deployable
    R_soft = {constraint_ctx from organizational governance}
  )

  g_ROC = (
    S      = {S_output, S_delivery},
    C      = output constraint set,
    E      = output evaluation function,
    A      = output audit records,
    R_hard = ∅                          — independently deployable
    R_soft = {handoff from g_EPG}       — enhanced by prompt context
  )

  g_MDR = (
    S      = {S_input, S_config},
    C      = monitoring rules, thresholds, model selection policies,
    E      = anomaly detection, compliance drift analysis,
             model/config constraint evaluation,
    A      = monitoring and incident records,
    R_hard = ∅                          — independently deployable
    R_soft = {audit records from g_EPG, g_ROC}
  )
```

### 8.2 Interface Contract Instantiation

```
K(g_EPG, g_ROC) = {
  provides(g_EPG → g_ROC):
    - The assembled prompt x and its cryptographic hash
    - The effective constraint set C*(project)
    - The prompt audit record A_EPG(i, t) with version_manifest
    - The interaction identifier

  requires(g_ROC ← g_EPG):
    - (soft) Prompt context for output evaluation
    - (soft) Constraint set for contextual output assessment

  handoff:
    - interaction_id: shared identifier (Design Requirement)
    - artifact: the assembled prompt x
    - artifact_hash: hash(p)
    - audit_ref: pointer to A_EPG(i, t)
    - constraint_ctx: C*(project)
    - version_manifest: {ver(m, t), ver(θ, t), ver(c, t) ∀ c}
}

K(g_ROC, g_MDR) = {
  provides(g_ROC → g_MDR):
    - The output audit record A_ROC(i, t)
    - Any flagged violations or anomalies
    - The delivered output o' and its hash

  requires(g_MDR ← g_ROC):
    - (soft) Output audit records for trend analysis
    - (soft) Violation flags for incident correlation

  handoff:
    - interaction_id: same identifier
    - artifact: delivered output o'
    - artifact_hash: hash(o')
    - audit_ref: pointer to A_ROC(i, t)
    - violation_flags: set of flagged constraint violations
}

K(g_EPG, g_MDR) = {
  provides(g_EPG → g_MDR):
    - The prompt audit record A_EPG(i, t)
    - Constraint version history for drift detection

  requires(g_MDR ← g_EPG):
    - (soft) Prompt audit records for root cause analysis

  handoff:
    - interaction_id: same identifier
    - audit_ref: pointer to A_EPG(i, t)
    - constraint_versions: {ver(c, t) | c ∈ C*(project)}
}
```

## 12. Composition Algebra

### 9.1 Component Composition Operator

```
DEFINITION (Composition):

Define the composition operator ⊕ on governance components:

  g₁ ⊕ g₂ = (
    S     = S_g₁ ∪ S_g₂,
    C     = C_g₁ ∪ C_g₂,
    E     = E_g₁ ∪ E_g₂,
    A     = compose(A_g₁, A_g₂) via shared interaction_id,
    R_hard = R_hard(g₁) ∪ R_hard(g₂) \ {mutual provisions}
  )
```

## 14. Scope Limitations and Extension Points

### 14.1 Single-Turn Interaction Scope

This meta-framework models single, non-agentic interaction turns. The following patterns require extension:

```
Multi-turn conversations:
  Modeled as: sequence [i₁, i₂, ..., iₙ] where p_k includes context
  from prior interactions. Per-interaction governance holds. Cross-turn
  governance (e.g., "the conversation as a whole must not reveal X")
  requires additional constraints on the sequence, not yet formalized.

Agentic / tool-use workflows:
  Modeled as: DAG of interactions where output(iₖ) → prompt(iⱼ).
  Per-interaction governance holds at each node. Graph-level governance
  (e.g., "the agent must not take action X without authorization")
  requires constraints on the DAG, not yet formalized.

Multi-model chains:
  Modeled as: pipeline [m₁, m₂, ..., mₙ] where each model processes
  the prior model's output. Per-interaction governance holds per model.
  Chain-level governance (e.g., "no PII may flow from model 1 to model 2")
  requires constraints on inter-model data flow, not yet formalized.
```

Per-interaction governance (applying EPG/ROC/MDR to each individual turn or node) is directly supported by this framework. However, cross-interaction governance (constraints that span multiple turns, nodes, or models) requires additional formal machinery not yet defined. The framework does NOT claim "architectural compatibility" with these patterns in their full generality — it claims that its per-interaction guarantees hold at each node, while acknowledging that graph-level and sequence-level governance is an open problem.

Additionally, multi-turn and agentic patterns may introduce stateful behavior (KV caches, agent scratchpads, persistent memory) that violates Axiom 1's statelessness assumption. For such patterns, either the state must be captured in p or θ at each turn, or the deployment must accept reduced auditability for the stateful elements (classified as Tier 2).

### 14.2 Model Internals

Model internals (weights, training data, fine-tuning provenance) are classified as external to the governance scope (γ = external in S_config). Governance of model internals (model cards, training data audits, bias testing) is a distinct discipline not addressed by this framework. Interface point: g_MDR can verify that a model meets selection criteria (e.g., "model must have a published model card with bias assessment") without governing the internals themselves.

---

## Where the results are

This chapter states the model: the surfaces an interaction presents, the component
that governs one, the contracts between components, and the algebra by which they
compose. What is *proved* over that model — sixteen theorems, lemmas and
corollaries, their proofs, and the twenty-two assumptions every one of them depends
on — is collected in the theorems appendix.

The separation is deliberate. Deciding whether to adopt this framework needs the
model and the guarantees in prose, which is this chapter. Building it, or auditing
someone else's build, needs the statements with their preconditions in one place,
which is the appendix. Results are referred to by designation throughout — Theorem
4, Lemma 2, Axiom 5 — and those designations are stable.

## Key takeaways

- Clad's guarantees rest on the axioms of §1 and the two design requirements derived from them — audit linkability and the Global Interaction Log. Where a deployment cannot satisfy an axiom, the theorems that depend on it must be re-evaluated.
- An AI interaction is governed at five control surfaces (§4); each surface carries a governability class that bounds what any component can promise.
- A governance component is the tuple `g = (S, C, E, A, R)` (§5), and components compose under a well-defined algebra (§12) that preserves each component's guarantees when they are deployed together.
- Governance reduces but does not eliminate residual risk (Theorem 4); what it guarantees is provable evidence, not perfect prevention.

The three chapters of Part IV instantiate this model, one control surface at a time. They begin with the prompt — the surface an enterprise fully controls before the model ever runs.
