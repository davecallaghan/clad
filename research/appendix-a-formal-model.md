# The EPG and ROC Formal Models

This appendix collects the formal models underpinning the control layers of
Part IV: the deontic constraint model of Enterprise Prompt Governance (Chapter 2)
and the output-evaluation model of Runtime Output Controls (Chapter 3). The
chapters draw on these results without requiring the reader to work through them
in full.

## A.1 Prompt Governance (EPG)

This appendix contains the complete EPG formal model. It uses symbolic logic throughout; §3 gives business-language explanations of the same concepts.

### Two Layers: Logical Core and Governance Meta-Layer

The model has two layers, and keeping them apart is what makes the results composable. The **logical core** is the satisfaction relation for a given C*(level) and prompt x, the inheritance rules, and the evaluability classification with its evaluation functions. The **governance meta-layer** — `author`, `approver`, `scope`, `decomp`, `CONTRADICTION`, `shared_scope`, `precedence` — governs who may create a constraint and how it is checked.

The meta-layer does not reach into the core. It changes neither the satisfaction relation, nor the inheritance rules, nor the evaluability classification. Audit Completeness and No Evaluability Gap are therefore theorems over the core alone, while Contradiction Freedom, Scope Isolation and Decomposition Coverage are theorems over the meta-layer and hold independently of it. A deployment can change its authorship controls without disturbing either set.

The logical core uses a two-operator deontic fragment {O, F} over atomic properties (§3.2a). P_meta is a governance annotation outside this fragment. The standard axiom O(φ) → P(φ) does not apply because P_meta is not a deontic operator.

### A.1.1 Primitive Sets and Domains

```
L = {enterprise, department, project}     — governance levels
C = C_m ⊎ C_p                            — operational constraints (disjoint union)
C_s                                       — semantic constraints (intent records,
                                            NOT part of C, documentation only)
X = set of all prompts                    — the governed artifacts
A = set of all agents                     — authors/owners of constraints
D = set of all domains                    — legal, security, data_privacy, etc.
T = totally ordered set of time points    — for versioning and audit
V = set of audit records                  — evaluation evidence
Φ = controlled vocabulary of atomic       — enterprise-governed property identifiers
    property identifiers
```

### A.1.2 Ordering on Governance Levels

```
enterprise ≻ department ≻ project
(≻ denotes "governs over")
(L, ≻) is a strict total order.
```

### A.1.3 Core Functions

Base functions:
```
level   : C → L           — assigns constraint to governance level
owner   : C → A           — who authored the constraint
domain  : C → D           — which domain the constraint belongs to
ver     : C × T → C_t     — version of constraint c active at time t
```

RBAC and governance meta-layer:
```
scope    : A → 𝒫(D)       — domains agent may author constraints for
approve  : C → A           — who approved the constraint
emergency: C → {true, false}  — break-glass flag

Dual control invariant:
  ∀ c ∈ C : ¬emergency(c) → author(c) ≠ approver(c)

Orthogonal approval (Critical tier):
  ∀ c ∈ C : risk_tier(c) = critical ∧ ¬emergency(c)
    → org_unit(author(c)) ≠ org_unit(approver(c))
    ∧ ∃ a ∈ approvers(c) : org_unit(a) ∈ {CISO, central_risk, compliance}

Break-glass bounds:
  ∀ c ∈ C : emergency(c)
    → tightening_only(c) ∧ ttl(c) ≤ max_ttl(industry(c))

Cross-domain review:
  cross_domain_reviewed : C → {true, false}

Enterprise Precedence Table:
  precedence : C × C → {c₁_wins, c₂_wins, unresolved}
```

### A.1.4 Constraint Taxonomy

Evaluability classes:
```
C_m = mechanically evaluable constraints
C_p = procedurally evidenced constraints
C = C_m ⊎ C_p              (operational constraints, disjoint)
```

Semantic constraints and decomposition mapping:
```
C_s = semantic constraints (intent records, NOT part of C)

decomp : C_s → (𝒫(C_m) × 𝒫(C_p))
  Maps each intent record to its operational decomposition.

⊨_s : conceptual satisfaction relation for semantic constraints
  Used in soundness claims only, NOT in evaluation machinery.
```

### A.1.5 Hierarchical Inheritance

```
C*(enterprise) = C(enterprise)
C*(department) = C(enterprise) ∪ C(department)
C*(project)    = C(enterprise) ∪ C(department) ∪ C(project)

Generally: C*(l) = ⋃{C(l') | l' ≽ l}

Monotonicity (inviolability):
  ∀ l₁, l₂ ∈ L : l₁ ≻ l₂ → C*(l₁) ⊆ C*(l₂)

Strengthening rules:
  ∀ l₁ ≻ l₂ :
    O(φ) ∈ C*(l₁)  →  O(φ) ∈ C*(l₂)     — obligations propagate
    F(φ) ∈ C*(l₁)  →  F(φ) ∈ C*(l₂)     — prohibitions propagate
    ¬∃ c ∈ C(l₂) that negates any c' ∈ C*(l₁)

Only O and F constraints participate in inheritance.
P_meta annotations are NOT part of C*(level).
```

### A.1.6 Permission Semantics

```
P_meta(φ) is a governance annotation, NOT a deontic operator.

Properties:
  - NO inheritance effect
  - NO evaluation effect
  - NO role in CONTRADICTION detection
  - For satisfaction and inheritance: equivalent to absence
  - For meta-governance (audit, review triggers): NOT equivalent
    to absence — signals "considered"

The system is NOT permissive-by-default:
  ¬F(φ) does NOT entail P_meta(φ) or any form of permission.
```

### A.1.7 Lateral Authority Scoping

```
Authorship constraint:
  ∀ c ∈ C : domain(c) ∈ scope(owner(c))

Direct overlap:
  shared_scope(D₁, D₂) = scope(D₁) ∩ scope(D₂)

Joint authorship for shared scope:
  ∀ c : property(c) ∈ shared_scope(D₁, D₂)
    → approved_by(c, D₁) ∧ approved_by(c, D₂)

Indirect interaction (advisory, not formal):
  affects(D₁, D₂) — captures known systemic couplings
  Detected via impact analysis (§7.3), NOT by scope intersection
```

### A.1.8 Conflict Detection and Resolution

```
CONTRADICTION predicate (formal, complete for atomic fragment):
  CONTRADICTION(c₁, c₂) ≡ c₁ = O(φ) ∧ c₂ = F(φ)
    for some atomic φ ∈ Φ

  Completeness: pairwise check is sufficient for independent
  atomic properties. Joint satisfiability of the full set follows
  from pairwise satisfiability in this fragment.

TENSION classification (advisory, NOT formal):
  Heuristic, not sound or complete.
  No theorem depends on TENSION.

Enterprise Precedence Table:
  precedence : C × C → {c₁_wins, c₂_wins, unresolved}
  default_priority : D → ℕ  (domain priority ordering)

Resolution protocol:
  Phase 1: authoring-time prevention (CONTRADICTION blocks,
           TENSION triggers review)
  Phase 2: automated precedence → default priority → arbiter
  Anti-circularity: arbiter resolutions must produce permanent
           structural fixes
```

### A.1.9 Decomposition Soundness

```
Soundness relationship (goal, not guarantee):
  Soundness(c_s) ⇔ ∀p, evidence :
    (∀c_m ∈ decomp_m(c_s) : p ⊨_m c_m) ∧
    (∀c_p ∈ decomp_p(c_s) : evidence ⊨_p c_p)
    → p ⊨_s c_s

Operational approximation:
  No_counterexample_found(c_s) — from tests and reviews.
  NOT equated with logical soundness.

Completeness NOT claimed:
  Over-tight decomposition may reject compliant prompts
  (conservative failure mode).

Residual gap controls:
  Coverage requirements, maximum gap thresholds,
  compensating control mandates, HITL trigger (§5.6).

Decomposition change triggers:
  Any change to decomp(c_s) reruns CONTRADICTION detection
  on affected levels.
```

### A.1.10 Satisfaction Relations

```
⊨_m : X × C_m → {⊤, ⊥}           — automated, deterministic
⊨_p : Evidence × C_p → {⊤, ⊥}    — human-attested, deterministic
                                      given evidence

Deontic satisfaction:
  x ⊨ O(φ)  iff  φ holds in x
  x ⊨ F(φ)  iff  φ does not hold in x
```

### A.1.11 Core Principles (Formal)

```
Principle 1 (Constraint, not prescription):
  ∀ c ∈ C : c ∈ (X → {⊤, ⊥})

Principle 2 (Execution is local):
  ∀ x ∈ X_executed : level(x) = project

Principle 3 (Risk management):
  System guarantees: ∀ x ∈ X_executed : audit(x, t) is COMPLETE ∧ IMMUTABLE
  System does NOT guarantee: ∀ x ∈ X_executed : ∀ c ∈ C*(project) : x ⊨ c

Principle 4 (Audit the artifact):
  ∀ x ∈ X_executed, ∀ t ∈ T :
    ∃ audit(x, t) = {(c, ver(c,t), eval(c,p)) | c ∈ C*(project)}

Principle 5 (Downward inheritance, lateral scoping):
  Inheritance: C*(l) = ⋃{C(l') | l' ≽ l}
  Scoping: ∀ c ∈ C : domain(c) ∈ scope(owner(c))
```

### A.1.12 Audit Record

```
audit(x, t) =
  { (c, ver(c,t), ⊨_m(x, c))                       | c ∈ C*_m(project) }
∪ { (c, ver(c,t), ⊨_p(evidence(x, c),c), attestor) | c ∈ C*_p(project) }

Extended fields:
  - conflict_detection_results : CONTRADICTION scan outcome
  - precedence_rule_applied : reference to precedence table entry (if any)
  - decomposition_version : ver(decomp(c_s), t) for each c derived from C_s
  - emergency_flag : emergency(c) for any break-glass constraints
```

### A.1.13 Theorems

Theorems over the logical core:

```
THEOREM (Audit Completeness):
  ∀ x ∈ X_executed : audit(x, t) is total over C*(project)
  ∧ audit(x, t) is deterministic
  ∧ audit(x, t) is immutable

THEOREM (No Evaluability Gap):
  ∀ c ∈ C*(project) : c ∈ C_m ∨ c ∈ C_p
```

Theorems over the governance meta-layer, with explicit preconditions and scope:

```
THEOREM (Contradiction Freedom — Pairwise, Atomic Fragment):
  After Phase 1 authoring review, no pair of constraints in
  C*(level) is contradictory per the CONTRADICTION predicate
  over atomic properties φ ∈ Φ.

  Scope: complete for the atomic fragment. Multi-constraint
  and semantic interactions are handled by governance process
  (tension detection, cross-domain review), not formal guarantee.

THEOREM (Scope Isolation — Direct, Under Fixed Definitions):
  No agent can create, modify, or delete constraints outside
  their authorized domain scope, given fixed domain definitions.

  Scope: does NOT cover indirect effects (addressed by impact
  analysis §7.3), scope redefinition by the cross-domain
  governance body, or constraints authored via break-glass.

THEOREM (Decomposition Coverage — Existence, Not Quality):
  Every semantic intent in C_s has a registered decomposition
  in decomp(c_s).

  Scope: guarantees existence, not soundness or adequacy.
  Soundness is approximated empirically (§5.7). Coverage
  quality for Critical-tier constraints is governed by
  residual gap controls (§5.6).
```

### A.1.14 Limitations of the Formal Model

The formal model guarantees properties of the governance process: who can author what, contradictions are detected, audit is complete, scope is isolated. It does not guarantee properties of the governed artifacts beyond what the constraints specify — content correctness, semantic completeness, or model behavior are outside scope.

Mechanisms that are governance-process tools — tension detection, impact analysis, residual gap review, collusion detection — are operationally valuable but not mathematically proven. They are best-effort controls, not formal guarantees.

This distinction is critical for the target audience: formal guarantees are hard commitments that can be verified and audited. Governance-process tools are organizational controls that improve outcomes but cannot be reduced to logical proofs.

---


## A.2 Runtime Output Controls (ROC)

### A.2.1 Primitive Sets

```
O_raw = set of all raw model outputs        — generated by model, before filtering
O_del = set of all delivered outputs         — after ROC processing
K     = set of all classifiers              — versioned ML models for output scoring
R     = set of all deterministic rules      — pattern, blocklist, structural checks
τ     = K → [0, 1]                          — threshold function mapping classifiers
                                               to their governance thresholds
```

ROC also uses the shared sets from the meta-framework: I (interactions), T (time), V (audit records), Φ (property vocabulary).

#### Applicability

`applicable` is used in the pipeline definition and in the Output Evaluation
Completeness theorem, and it carries more weight than its notation suggests. The
theorem states that the audit record is *total over* `applicable(C_ROC, i)`. Left
undefined, that claim is vacuous: an implementation could define `applicable` to
return ∅ for every interaction and satisfy the theorem while evaluating nothing.
The definition is therefore part of the guarantee, not a detail beneath it.

```
scope    : C_ROC → (I → {true, false})   — each constraint declares the
                                            interactions it governs
applicable : 𝒫(C_ROC) × I → 𝒫(C_ROC)
applicable(X, i) = { c ∈ X | scope(c)(i) }

Resolved against the interaction's context — the same four dimensions the
grounding chapters index evidence to, named here rather than symbolized, because
in this appendix `c` is a constraint:
  jurisdiction
  valid time (which version of the rule was in force)
  data classification / regulated form
  the requesting role
```

Three requirements make the definition load-bearing rather than decorative:

- **Totality.** `scope(c)` is total over I. There is no interaction for which a
  constraint's applicability is undefined; a constraint either governs an
  interaction or it does not.
- **Recorded exclusion.** `A_ROC(i, t)` records `C_ROC \ applicable(C_ROC, i)`
  together with the `scope` predicate version that excluded each constraint. A
  constraint deemed inapplicable is an auditable decision, not an absence. This
  is what closes the vacuity: narrowing applicability does not shrink the audit
  record, it changes what the record must justify.
- **Governed change.** `scope` is a versioned governed artifact under the same
  dual-control and version-stamping rules as `τ` (§A.2.6). Narrowing a
  constraint's applicability is a governance action with an author, an approver,
  and a timestamp.

Without recorded exclusion, evaluation completeness and evaluation avoidance are
indistinguishable in the audit record — the failure mode the meta-framework's
evidence-surface discussion identifies, where a control that is never invoked
looks identical to a control that always passes.

### A.2.2 Output Constraint Taxonomy

```
C_ROC = O_d ⊎ O_c ⊎ O_x     (disjoint union)

O_d = deterministic output constraints
  eval_d : O_raw × R → {pass, fail}
  Properties: total, deterministic, repeatable

O_c = classifier-based output constraints
  eval_c : O_raw × K → [0, 1]
  Governance decision: eval_c(o, k) ≥ τ(k) → flag ; else → below_threshold
  Quantized satisfaction: o ⊨_τ c  (weaker than deterministic o ⊨ c)
  Properties: total, probabilistic, threshold-dependent

Threshold monotonicity invariant:
  ∀ l₂ ≺ l₁ : τ(k, l₂) ≤ τ(k, l₁)
  Lower levels can only tighten (lower) thresholds, never loosen.

Threshold policy bounds (Critical tier):
  ∀ k, risk_tier = critical : τ(k) ≤ τ_max(k)
  where τ_max is policy-defined and cannot be exceeded without
  exceptional process.

O_x = composite output constraints
  eval_x : O_raw × (𝒫(O_d) × 𝒫(O_c)) × logic → {pass, flag, block}
  where logic ∈ {any_flag, all_flag, weighted}
  Properties: deterministic given component evaluations
```

### A.2.3 Pipeline Stages

```
DEFINITION (ROC Pipeline):

For interaction i with raw output o ∈ O_raw:

  Stage 1: D_results = { (r, eval_d(o, r)) | r ∈ applicable(O_d, i) }
  Stage 2: C_results = { (k, eval_c(o, k), τ(k)) | k ∈ applicable(O_c, i) }
  Stage 3: X_results = { (x, eval_x(o, x, D_results, C_results)) |
                          x ∈ applicable(O_x, i) }

  Short-circuit (Critical tier):
    if ∃ r ∈ D_results : eval_d = fail → Decision = BLOCK immediately
    (do not await Stage 2/3)

  Decision(o) =
    BLOCK  if ∃ r ∈ D_results : eval_d = fail ∧ risk_tier = critical
           ∨ ∃ x ∈ X_results : eval_x = block
           ∨ risk_tier = critical ∧ classifier_timeout
    FLAG   if ∃ k ∈ C_results : eval_c ≥ τ(k)
           ∨ ∃ x ∈ X_results : eval_x = flag
    BELOW_THRESHOLD  otherwise
    (Note: BELOW_THRESHOLD, not PASS — for classifier-evaluated outputs)

  Delivery(o) =
    Critical: atomic buffered — o' emitted only after Decision is terminal
    Standard: o' = o delivered immediately; evaluation async
    Low: o' = o delivered; evaluation sampled async

    if BLOCK: o' = fallback(o, decision_reason)
    if FLAG (Critical): o' = fallback or human review
    if FLAG (Standard/Low): o' = o (delivered, flag in audit)
```

### A.2.4 Audit Record

```
A_ROC(i, t) = {
  interaction_id    : identifier(i)
  timestamp         : t
  raw_output_hash   : hash(o)
  delivered_hash    : hash(o')        — differs from raw if redaction occurred
  d_evaluations     : D_results       — deterministic: (rule, pass/fail)
  c_evaluations     : C_results       — classifier: (classifier_ver, score, τ, decision)
  x_evaluations     : X_results       — composite: (constraint, decision, logic_applied)
  pipeline_decision : {PASS, FLAG, BLOCK}
  fallback_action   : action taken if BLOCK (substitution, redaction, retry, escalation)
  timing_mode       : {synchronous, asynchronous, sampled}
  epg_context       : pointer(A_EPG(i, t)) | ⊥
  predecessor       : pointer(A_EPG(i, t)) | ⊥
}
```

### A.2.5 Formal Properties

```
THEOREM (Output Evaluation Completeness — Critical and Standard Tiers):
  ∀ i ∈ I_governed where timing_mode ∈ {synchronous, asynchronous} :
    A_ROC(i, t) is total over applicable(C_ROC, i)
    ∧ A_ROC(i, t) is immutable
    ∧ A_ROC(i, t) contains interaction_id

  Scope: applies to Critical and Standard tier interactions within
  ROC's enforcement boundary. Low-tier sampled interactions have
  evaluation records only when sampled. Outputs that bypass ROC
  entirely are detected via GIL.

THEOREM (Deterministic Evaluation Reliability):
  ∀ o, r : eval_d(o, r, t₁) = eval_d(o, r, t₂)
    given ver(r, t₁) = ver(r, t₂)

  Deterministic evaluations produce identical results for
  identical inputs and rule versions. This is the same
  evidentiary standard as EPG's mechanical evaluation.

PROPERTY (Classifier-Based Evaluation — Scored, Not Guaranteed):
  eval_c is probabilistic. The following are NOT theorems:
    - eval_c is deterministic (it is not — classifier non-determinism)
    - eval_c has zero false negatives (it does not at any threshold)
    - eval_c captures all violations (novel patterns evade classifiers)

  Classifier evaluations are SCORED EVIDENCE, not formal guarantees.
  The audit record captures the score, threshold, and classifier
  version, enabling post-hoc assessment of evaluation quality.
```

### A.2.6 Threshold Governance

```
Thresholds are governed artifacts:
  ∀ k ∈ K : τ(k) is subject to:
    - Dual-control modification (author ≠ approver)
    - Version tracking: ver(τ(k), t) recorded in audit
    - Risk-tier defaults: τ_critical < τ_standard < τ_low
      (lower threshold = more conservative = more flags)
```

### A.2.7 Limitations

The formal model provides guarantees for the deterministic tier and scored evidence for the classifier tier. It does not provide:

- Soundness or completeness guarantees for classifiers
- Coverage guarantees for novel threat patterns
- Guarantees about model behavior (only about output evaluation)

This two-tier evidentiary structure is intentional. Merging probabilistic evaluations with deterministic ones would either overstate classifier reliability or understate deterministic reliability. Keeping them separate enables honest, tier-appropriate audit.

---
