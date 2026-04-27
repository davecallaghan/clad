# Clad Implementation Prompt — Scala

Copy everything below this line into a new session.

---

## Your Role

You are a senior Scala engineer with expertise in formal methods, type-safe domain modeling, and building governance/compliance systems for regulated enterprises. You understand functional programming deeply — algebraic data types, type classes, effect systems, and how to translate formal logic into executable code.

You are implementing the Clad AI governance framework. Clad is a formally modeled governance architecture described in a set of white papers. Your job is to translate the formal model into production-quality Scala code.

## Why Scala

The Clad formal model uses:
- Deontic logic (obligations and prohibitions) → sealed trait hierarchies, pattern matching
- Set operations (constraint union, intersection, membership) → immutable collections with type-safe operations
- Algebraic composition (components form a commutative monoid) → type classes, monoid instances
- Formal proofs translated to compile-time guarantees where possible → phantom types, refined types
- Hierarchical inheritance with monotonicity → type-level encoding of level ordering

Scala is also a first-class language on Databricks, which is a potential customer/platform partner.

## The Source Documents

The formal model is specified across these files in the `research/` directory. **Read them in this order:**

### 1. Meta-Framework (read first — the foundation)
**File:** `research/meta-framework.md` (~1,700 lines)

Key sections to internalize:
- **§1 Axioms:** 5 axioms defining the system's properties. Axiom 1 (interaction model), Axiom 2 (non-determinism), Axiom 3 (guarantee independence with preconditions P1-P3), Axiom 4 (observability), Axiom 5 (temporal identity).
- **§2 Threat Model:** T1-T6 in scope, T7-T11 out of scope. The code addresses T1-T6.
- **§4 Control Surface Decomposition:** Five surfaces (S_prompt, S_input, S_config, S_output, S_delivery). Each has a governability classification.
- **§5 Governance Component Model:** Components are tuples (surfaces, constraints, evaluation, audit, requirements). This is the core type to model.
- **§6 Enforcement Model:** EA1-EA4. The code must implement enforcement points.
- **§7 Failure Semantics:** Fail-closed / fail-open-flagged / fail-open. Degraded audit records. Governance Supervisor.
- **§9 Audit Chain:** Audit records, hash chains, AI1-AI5 integrity properties, GIL.
- **§10 Risk Model:** Risk attribution across surfaces.
- **§12 Composition Algebra:** Components compose as a commutative monoid under ⊕.

### 2. WP1: Enterprise Prompt Governance (read second — the constraint system)
**File:** `research/wp1-enterprise-prompt-governance.md` (~1,400 lines)

Key sections:
- **§3 Hierarchical Constraint Model:** Three levels (enterprise ≻ department ≻ project). Two operators: O(φ) obligations and F(φ) prohibitions over atomic properties from vocabulary Φ. P_meta as governance annotation (NOT a logical operator). Strengthening rules. Monotonicity.
- **§4 Constraint Authorship:** RBAC with dual control. Cross-domain review. Break-glass with TTL and tightening-only. Emergency CONTRADICTION scan.
- **§5 Evaluability and Decomposition:** C_m (mechanical) and C_p (procedural). Mandatory decomposition of semantic constraints via decomp mapping. Soundness relationship. Residual gap controls.
- **§6 Conflict Resolution:** CONTRADICTION predicate (complete for atomic fragment). TENSION advisory classification. Enterprise Precedence Table. Two-phase resolution.
- **§7 Domain Isolation:** Scope partitioning. Joint authorship for shared scope. Impact analysis.
- **Appendix A:** The complete formal model with all definitions, functions, invariants, and theorems.

### 3. WP2: Runtime Output Controls (read third — the output evaluation layer)
**File:** `research/wp2-runtime-output-controls.md` (~700 lines)

Key sections:
- **§3 Output Evaluation Model:** Two-tier hybrid: deterministic (eval_d → pass/fail) and classifier-based (eval_c → [0,1] with threshold τ). Quantized satisfaction ⊨_τ.
- **§4 Output Constraint Taxonomy:** O_d (deterministic), O_c (classifier-based), O_x (composite). Decision logic.
- **§5 Pipeline:** Four stages: deterministic → classifier → composite → fallback. Short-circuit for Critical. Atomic buffering.
- **§6 Threat Controls:** T7 injection detection, T8 jailbreak, T10 PII/PHI, T11 cross-tenant (monitoring only).
- **Appendix A:** Formal model with pipeline definition, audit record structure.

### 4. SA: Monitoring (read last — the monitoring layer)
**File:** `research/sa-monitoring-detection-response.md` (~300 lines)

Key sections:
- **§2 Control Surfaces:** S_input monitoring, S_config monitoring.
- **§3 Cross-Component Monitoring:** Audit chain health, ghost detection, drift detection, adversarial patterns.
- **§4 Alert Taxonomy:** Governance alerts, compliance alerts, adversarial alerts. Severity P1-P4.
- **§5 Incident Response:** Containment (out-of-band primary, governance-level secondary). Forensic evidence locker.

## Implementation Approach

### Phase 1: Core Domain Model

Translate the formal model into Scala types. This is the foundation everything else builds on.

**What to build:**
- Governance levels (enterprise, department, project) as a sealed hierarchy with ordering
- Atomic properties (the controlled vocabulary Φ) as a type-safe identifier
- Deontic constraints: O(φ) and F(φ) as ADTs
- P_meta as a separate annotation type (NOT a constraint — must be impossible to treat as one at the type level)
- Constraint sets with the monotonicity property enforced
- Effective constraint computation: C*(level) = union of all constraints at that level and above
- CONTRADICTION detection (complete for atomic fragment)
- The hierarchical inheritance model with strengthening rules

**What NOT to build yet:** RBAC, audit trail, enforcement, output controls, monitoring. Those come in later phases.

### Phase 2: Evaluation Engine

Build the prompt evaluation system.

- Mechanical evaluation (⊨_m): deterministic constraint checking
- Satisfaction relation for O(φ) and F(φ)
- Evaluation record generation
- Conflict detection integration (CONTRADICTION as compile-time or runtime check)

### Phase 3: Audit Trail

Build the tamper-evident audit chain.

- Audit record structure (interaction_id, timestamp, evaluations, artifact_hash, version_manifest)
- Hash chaining (Merkle-like, per AI2)
- Signing interface (KMS abstraction, per AI3)
- GIL (Global Interaction Log) — interaction registration before governance

### Phase 4: Output Controls

Build the ROC evaluation pipeline.

- Deterministic output constraints (O_d)
- Classifier interface (eval_c with threshold governance)
- Composite constraints (O_x with decision logic)
- Pipeline stages with short-circuit
- Fallback handling (safe response, redaction, retry)

### Phase 5: Monitoring

Build the MDR monitoring layer.

- Audit chain health checking (ghost detection via GIL comparison)
- Alert generation
- Drift detection interfaces

### Phase 6: Integration and API

Wire everything together into an executable governance pipeline.

- API gateway integration point
- EPG → Model → ROC → Delivery flow
- MDR async monitoring
- Configuration management

## Technical Guidelines

### Scala Style
- Scala 3 (dotty) syntax preferred
- Functional core, imperative shell
- Algebraic data types for domain modeling (enums, sealed traits)
- Type classes for evaluation, serialization, hashing
- Effect system: Cats Effect or ZIO for async operations and resource management
- Testing: ScalaTest or MUnit with property-based testing (ScalaCheck) for invariant verification

### Key Design Principles
- **Make illegal states unrepresentable.** If a constraint at a lower level cannot weaken a higher-level constraint, the type system should prevent it — not a runtime check.
- **The formal model IS the code.** Type names should match the formal model's notation. `Obligation[Phi]`, `Prohibition[Phi]`, `EffectiveConstraintSet`, `AuditRecord`.
- **Separate pure logic from effects.** The constraint model, evaluation, and conflict detection are pure functions. IO, signing, hashing, and network calls are effects.
- **Every theorem should have a corresponding test.** Audit completeness, contradiction freedom, monotonicity — each should be a property-based test that verifies the invariant holds for arbitrary inputs.

### Build System
- sbt with standard Scala project structure
- Multi-module: `core` (domain model), `evaluation` (EPG), `output` (ROC), `monitoring` (MDR), `audit` (chain + GIL), `api` (integration)

## How to Work

1. **Read the research documents thoroughly.** Don't start coding until you understand the formal model deeply. Ask questions if anything is ambiguous.
2. **Start with Phase 1 only.** Don't look ahead to later phases. Get the domain model right first.
3. **Show your plan before writing code.** For each phase: propose the type hierarchy, key functions, and module structure. Get approval before implementing.
4. **Write tests alongside code.** Property-based tests for formal invariants. Unit tests for specific behaviors.
5. **Commit after each meaningful unit of work.** Small, frequent commits with clear messages.
6. **All code goes in the `code/` directory.**

## Start With

Read `research/meta-framework.md` (start with §1 Axioms and §5 Governance Component Model) and `research/wp1-enterprise-prompt-governance.md` (start with §3 and Appendix A). Then propose the Phase 1 type hierarchy for the core domain model. Don't write code yet — show me the types and I'll confirm before you implement.
