# Glossary

Definitions of the terms used throughout the book. Symbols and notation are
collected separately in *Notation & Conventions* at the front of the book.

**Attestation (procedural).** Human sign-off (`⊨_p`) used to satisfy a constraint
that cannot be checked mechanically, recorded as auditable evidence. See
*decomposition*.

**Invariants (I1--I19).** The properties an assertion-emitting system does not
supply on its own (I1--I7, *behavioral*) and those it must be built to supply
(I8--I19, *system*). Both sets are tabulated in the invariants chapter; they are
listed here because they are referred to by number throughout.

| | Name | What it requires |
|---|---|---|
| I1 | Form invariance | Materially equivalent requests give equivalent decision-relevant outcomes |
| I2 | Closure | Asserting `p` and `p → q` implies asserting `q` |
| I3 | Decomposition | Asserting a conjunction implies asserting its parts |
| I4 | Consistency | Not both `p` and `not p` |
| I5 | Self-report fidelity | Claims about what it knows track what it asserts |
| I6 | Correctness | What is asserted is true |
| I7 | Repeatability | Same input, same session, same assertion |
| I8 | Source identity | Every cited source has stable identity and established authority |
| I9 | Temporal validity | Source version and effective date are recorded and compared to the decision date |
| I10 | Context binding | The resolved context is explicit before assertion, or explicitly marked unresolved |
| I11 | Applicability | Evidence is filtered on scope, not on similarity alone |
| I12 | Traceability | Each consequential claim links to the evidence supporting it |
| I13 | Support verification | Cited evidence is checked to support the claim at the strength asserted |
| I14 | Reproducibility | The assertion can be re-derived from recorded inputs and versions |
| I15 | Authorization | Reliance is permitted at the applicable tier, with duties assigned |
| I16 | Reconstructability | The full decision path is retained for the required period |
| I17 | Controlled failure | Unmet conditions produce qualification, refusal, or escalation |
| I18 | Sufficiency | The evidence set is checked for coverage of every condition the claim requires |
| I19 | Defeater search | A scoped search for contrary or undermining authority of comparable standing |

I1--I7 are the ones that do *not* hold; naming them is how the book states what a
system cannot be assumed to do. I8--I19 are requirements, each tied to a control.

**Audit chain.** The composed, hash-linked sequence of audit records produced for
a single interaction by the components that processed it. Written `chain(i)`.

**Break-glass.** An emergency mechanism (Chapter 2, §4.5) for adding constraints
under time pressure. Break-glass constraints may only *tighten*, are time-bound
(TTL), and are subject to the same audit requirements as any constraint. MDR can
trigger break-glass as a containment action.

**Classifier evaluation (soft).** ROC's probabilistic tier (`⊨_c`): a model scores
an output for a property (e.g., "medical advice") and the score is compared with a
governed threshold. Produces *scored evidence*, distinct from the *hard evidence*
of deterministic checks.

**Component (governance component).** A unit of governance modeled as the tuple
`g = (S, C, E, A, R)`: the surfaces it governs, its constraints, its evaluation
procedure, its audit output, and its requirements on other components. The three
components in this book are EPG, ROC, and MDR.

**Composite constraint.** An output constraint (`O_x`) built by combining
deterministic and classifier evaluations with explicit logic — for example, a
PHI check that is satisfied only if both a pattern match and a classifier agree.

**Composition algebra.** The rules (Chapter 1, §12) under which components combine.
Components compose under the operator `⊕` and preserve each other's guarantees
when deployed together.

**Conflict resolution.** The defined precedence (Chapter 2, §6) that determines the
outcome when two constraints apply to the same artifact and disagree; upstream
(enterprise) protections are never overridden by downstream additions.

**Constraint.** A governed requirement on an artifact, expressed as an obligation
`O(φ)` or a prohibition `F(φ)` over a property `φ`.

**Constraint hierarchy.** EPG's model in which constraints flow enterprise ≻
department ≻ project (Chapter 2, §3). Each level may add specificity and tighten,
but never remove protections established upstream (*inheritance monotonicity*).

**Control surface.** A point in an AI interaction at which governance can be
applied. The five surfaces are S_prompt, S_input, S_config, S_output, and
S_delivery.

**Decomposition (mandatory).** The requirement (Chapter 2, §5) that a semantic
constraint be broken into mechanically evaluable checks plus procedural
attestation, so that it becomes auditable rather than a matter of trust.

**Deontic modality.** The logical category of a constraint — obligatory `O`,
forbidden `F`, or permitted `P` — used by EPG to express what a prompt must, must
not, or may do.

**Deterministic evaluation (hard).** ROC's exact tier (`⊨_d`) and EPG's mechanical
checks: a rule that either matches or does not (e.g., an SSN regex). Produces
*hard evidence*.

**Domain isolation.** The property (Chapter 2, §7) that a department or project
cannot weaken another domain's protections; lateral authority is bounded to a
domain's own scope.

**Dual control.** A separation-of-duties requirement (Chapter 2, §4) that the
author of a constraint is not its approver, with orthogonal reporting lines for
critical constraints.

**Enforcement architecture (EA1–EA4).** The conditions under which a surface is
*fully* governable — chokepoint enforcement (EA1), identity binding (EA2), and
related requirements. Without them, a component's guarantees apply only to
interactions that voluntarily pass through it.

**EPG (Enterprise Prompt Governance).** The component that governs S_prompt: the
instructions sent to a model before inference. Component `g_EPG`. See Chapter 2.

**Evaluation record vs. process record.** An *evaluation record* is
component-signed and carries the actual evaluation result; a *process record* is
produced by the Governance Supervisor during a component failure and documents
the failure without evaluation content — a weaker form of evidence.

**Fail-closed / fail-open.** A component's failure posture. Fail-closed blocks the
interaction when the component is unavailable; fail-open lets it proceed
(fail-open-*flagged* records it as ungoverned). Posture is assigned per risk tier
and is itself a governed constraint.

**Ghost interaction (ghost chain).** An interaction that reached the model without
being recorded by the governance pipeline. The Global Interaction Log and MDR
exist to make ghosts detectable.

**GIL (Global Interaction Log).** An independent log that records the existence of
every interaction before any component processes it, so bypassed or failed
interactions remain detectable (Chapter 1, design requirement).

**Governability.** The degree to which a control surface can be constrained,
written `γ(S)`: *full*, *partial*, or *external*. It bounds what any component
governing that surface can promise.

**Governance Supervisor.** The independent element that maintains audit-chain
continuity when a component fails, emitting supervisor-signed *process records*.

**Hard vs. soft requirements.** A component's `R_hard` are the other components it
requires to function; `R_soft` are ones that improve it but are not required. All
three components in this book have `R_hard = ∅` — each is independently
deployable.

**Interaction.** A single, non-agentic exchange with an AI model, modeled as the
tuple `(x, u, M, θ, o)` — prompt, user input, model, inference configuration, and
output (Chapter 1, Axiom 1).

**Interface contract.** The formal agreement `K(g₁, g₂)` describing what one
component provides to another (Chapter 1, §8). Contracts carry EPG context to ROC
and both components' records to MDR.

**Mechanical evaluation.** EPG's exact tier (`⊨_m`): keyword, regular-expression,
and structural checkers that decide constraint satisfaction deterministically.

**MDR (Monitoring, Detection & Response).** The cross-cutting component that
governs S_input and S_config and correlates the audit records of EPG and ROC to
detect and respond to system-level issues. Component `g_MDR`. See Chapter 4.

**Pass / Flag / Block.** The three decisions of ROC's output pipeline (Chapter 3,
§5): deliver as-is, deliver but record a violation, or withhold and substitute a
fallback.

**Property (φ) and property vocabulary (Φ).** An atomic, named characteristic a
constraint ranges over (φ), drawn from a controlled vocabulary (Φ) shared across
components so their records compose.

**Redaction / fallback.** ROC's handling of a blocked or non-compliant output —
removing offending content, or delivering a safe substitute response in place of
the model's output.

**Residual risk.** The compliance risk that remains after governance is applied.
By the Irreducible Residual Risk theorem (Chapter 1, Theorem 4) it is non-zero
even with all components deployed; governance reduces and evidences it rather than
eliminating it.

**Risk tier.** The classification of a workload as *Critical*, *Standard*, or
*Low*, which selects evaluation timing (real-time blocking vs. asynchronous) and
failure posture.

**ROC (Runtime Output Controls).** The component that governs S_output and
S_delivery: model output before it reaches a user. Component `g_ROC`. See
Chapter 3.

**Tamper-evident.** A property of the audit records whereby any modification is
detectable, via hash-chaining and independent signing (Chapter 1, Theorem 3a).

**Threat model (T1–T11).** The enumerated threats (Chapter 1, §2), classified as
in-scope for the meta-framework (T1–T6) or deferred to ROC and MDR (T7–T11).

**Threshold (τ) and threshold monotonicity.** The cutoff a classifier score is
compared against. Thresholds obey the same tightening-only inheritance as
constraints: a lower level may lower a threshold (more flags) but never raise it
above the level above.

**Version stamp / version manifest.** The recorded version of every governed
element at interaction time (Chapter 1, Axiom 5); the manifest is the set of these
stamps included in an audit record so "which rules were in effect" is answerable.
