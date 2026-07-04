# Appendix A — Formal Model & Theorems

This appendix collects the formal models underpinning the control layers of
Part II: the deontic constraint model of Enterprise Prompt Governance (Chapter 2)
and the output-evaluation model of Runtime Output Controls (Chapter 3). The
chapters draw on these results without requiring the reader to work through them
in full.

## A.1 Prompt Governance (EPG)

This appendix contains the complete, authoritative EPG formal model. It extends and supersedes the standalone formal model document (`formal-model.md`). The formal model uses symbolic logic throughout; refer to §3 for business-language explanations of these concepts.

### Conservative Extension Argument

The formal constructs introduced beyond the base model (RBAC, domain isolation, conflict detection, decomposition mapping) extend the existing formal model without altering the truth of existing theorems. New symbols (`author`, `approver`, `scope`, `decomp`, `CONTRADICTION`, `shared_scope`, `precedence`) operate on the governance meta-layer — who can create constraints and how they are checked. They do not change the satisfaction relation for a given C*(level) and prompt p. They do not change the inheritance rules. They do not change the evaluability classification or evaluation functions. Existing theorems (Audit Completeness, No Evaluability Gap) hold over the unchanged logical core. New theorems operate over the governance meta-layer and are independent of the logical core.

The logical core uses a two-operator deontic fragment {O, F} over atomic properties (§3.2a). P_meta is a governance annotation outside this fragment. The standard axiom O(φ) → P(φ) does not apply because P_meta is not a deontic operator.

### A.1 Primitive Sets and Domains

```
L = {enterprise, department, project}     — governance levels
C = C_m ⊎ C_p                            — operational constraints (disjoint union)
C_s                                       — semantic constraints (intent records,
                                            NOT part of C, documentation only)
P = set of all prompts                    — the governed artifacts
A = set of all agents                     — authors/owners of constraints
D = set of all domains                    — legal, security, data_privacy, etc.
T = totally ordered set of time points    — for versioning and audit
V = set of audit records                  — evaluation evidence
Φ = controlled vocabulary of atomic       — enterprise-governed property identifiers
    property identifiers
```

### A.2 Ordering on Governance Levels

```
enterprise ≻ department ≻ project
(≻ denotes "governs over")
(L, ≻) is a strict total order.
```

### A.3 Core Functions

Base functions (unchanged):
```
level   : C → L           — assigns constraint to governance level
owner   : C → A           — who authored the constraint
domain  : C → D           — which domain the constraint belongs to
ver     : C × T → C_t     — version of constraint c active at time t
```

RBAC and governance meta-layer (new):
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

### A.4 Constraint Taxonomy

Evaluability classes (unchanged in structure):
```
C_m = mechanically evaluable constraints
C_p = procedurally evidenced constraints
C = C_m ⊎ C_p              (operational constraints, disjoint)
```

Semantic constraints and decomposition mapping (new):
```
C_s = semantic constraints (intent records, NOT part of C)

decomp : C_s → (𝒫(C_m) × 𝒫(C_p))
  Maps each intent record to its operational decomposition.

⊨_s : conceptual satisfaction relation for semantic constraints
  Used in soundness claims only, NOT in evaluation machinery.
```

### A.5 Hierarchical Inheritance

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

### A.6 Permission Semantics

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

### A.7 Lateral Authority Scoping

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

### A.8 Conflict Detection and Resolution

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

### A.9 Decomposition Soundness

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

### A.10 Satisfaction Relations

```
⊨_m : P × C_m → {⊤, ⊥}           — automated, deterministic
⊨_p : Evidence × C_p → {⊤, ⊥}    — human-attested, deterministic
                                      given evidence

Deontic satisfaction:
  p ⊨ O(φ)  iff  φ holds in p
  p ⊨ F(φ)  iff  φ does not hold in p
```

### A.11 Core Principles (Formal)

```
Principle 1 (Constraint, not prescription):
  ∀ c ∈ C : c ∈ (P → {⊤, ⊥})

Principle 2 (Execution is local):
  ∀ p ∈ P_executed : level(p) = project

Principle 3 (Risk management):
  System guarantees: ∀ p ∈ P_executed : audit(p,t) is COMPLETE ∧ IMMUTABLE
  System does NOT guarantee: ∀ p ∈ P_executed : ∀ c ∈ C*(project) : p ⊨ c

Principle 4 (Audit the artifact):
  ∀ p ∈ P_executed, ∀ t ∈ T :
    ∃ audit(p,t) = {(c, ver(c,t), eval(c,p)) | c ∈ C*(project)}

Principle 5 (Downward inheritance, lateral scoping):
  Inheritance: C*(l) = ⋃{C(l') | l' ≽ l}
  Scoping: ∀ c ∈ C : domain(c) ∈ scope(owner(c))
```

### A.12 Audit Record

```
audit(p, t) =
  { (c, ver(c,t), ⊨_m(p,c))                       | c ∈ C*_m(project) }
∪ { (c, ver(c,t), ⊨_p(evidence(p,c),c), attestor) | c ∈ C*_p(project) }

Extended fields:
  - conflict_detection_results : CONTRADICTION scan outcome
  - precedence_rule_applied : reference to precedence table entry (if any)
  - decomposition_version : ver(decomp(c_s), t) for each c derived from C_s
  - emergency_flag : emergency(c) for any break-glass constraints
```

### A.13 Theorems

Existing theorems (unchanged):

```
THEOREM (Audit Completeness):
  ∀ p ∈ P_executed : audit(p,t) is total over C*(project)
  ∧ audit(p,t) is deterministic
  ∧ audit(p,t) is immutable

THEOREM (No Evaluability Gap):
  ∀ c ∈ C*(project) : c ∈ C_m ∨ c ∈ C_p
```

New theorems (with explicit preconditions and scope):

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

### A.14 Limitations of the Formal Model

The formal model guarantees properties of the governance process: who can author what, contradictions are detected, audit is complete, scope is isolated. It does not guarantee properties of the governed artifacts beyond what the constraints specify — content correctness, semantic completeness, or model behavior are outside scope.

Mechanisms that are governance-process tools — tension detection, impact analysis, residual gap review, collusion detection — are operationally valuable but not mathematically proven. They are best-effort controls, not formal guarantees.

This distinction is critical for the target audience: formal guarantees are hard commitments that can be verified and audited. Governance-process tools are organizational controls that improve outcomes but cannot be reduced to logical proofs.

---


## A.2 Runtime Output Controls (ROC)

### A.1 Primitive Sets

```
O_raw = set of all raw model outputs        — generated by model, before filtering
O_del = set of all delivered outputs         — after ROC processing
K     = set of all classifiers              — versioned ML models for output scoring
R     = set of all deterministic rules      — pattern, blocklist, structural checks
τ     = K → [0, 1]                          — threshold function mapping classifiers
                                               to their governance thresholds
```

ROC also uses the shared sets from the meta-framework: I (interactions), T (time), V (audit records), Φ (property vocabulary).

### A.2 Output Constraint Taxonomy

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

### A.3 Pipeline Stages

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

### A.4 Audit Record

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

### A.5 Formal Properties

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

### A.6 Threshold Governance

```
Thresholds are governed artifacts:
  ∀ k ∈ K : τ(k) is subject to:
    - Dual-control modification (author ≠ approver)
    - Version tracking: ver(τ(k), t) recorded in audit
    - Risk-tier defaults: τ_critical < τ_standard < τ_low
      (lower threshold = more conservative = more flags)
```

### A.7 Limitations

The formal model provides guarantees for the deterministic tier and scored evidence for the classifier tier. It does not provide:

- Soundness or completeness guarantees for classifiers
- Coverage guarantees for novel threat patterns
- Guarantees about model behavior (only about output evaluation)

This two-tier evidentiary structure is intentional. Merging probabilistic evaluations with deterministic ones would either overstate classifier reliability or understate deterministic reliability. Keeping them separate enables honest, tier-appropriate audit.

---
