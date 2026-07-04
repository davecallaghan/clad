# Glossary

Definitions of the core terms used throughout the book. Symbols and notation are
collected separately in *Notation & Conventions* at the front of the book.

**Audit chain.** The composed, hash-linked sequence of audit records produced for
a single interaction by the governance components that processed it. Written
`chain(i)` for interaction `i`.

**Component (governance component).** A unit of governance modeled as the tuple
`g = (S, C, E, A, R)`: the surfaces it governs, its constraints, its evaluation
procedure, its audit output, and its requirements on other components. The three
components in this book are EPG, ROC, and MDR.

**Composition algebra.** The rules (Chapter 1, §12) under which components combine.
Components compose under the operator `⊕` and preserve each other's guarantees
when deployed together.

**Constraint.** A governed requirement on an artifact, expressed as an obligation
`O(φ)` or a prohibition `F(φ)` over a property `φ`.

**Control surface.** A point in an AI interaction at which governance can be
applied. The five surfaces are S_prompt, S_input, S_config, S_output, and
S_delivery.

**Deontic modality.** The logical category of a constraint — obligatory `O`,
forbidden `F`, or permitted `P` — used by EPG to express what a prompt must, must
not, or may do.

**EPG (Enterprise Prompt Governance).** The component that governs S_prompt: the
instructions sent to a model before inference. Component `g_EPG`. See Chapter 2.

**Ghost interaction.** An interaction that reached the model without being
recorded by the governance pipeline. Detecting ghosts is a function of the Global
Interaction Log and MDR.

**GIL (Global Interaction Log).** An independent log that records the existence of
every interaction before any governance component processes it, so that bypassed
or failed interactions are still detectable (Chapter 1, design requirement).

**Governability.** The degree to which a control surface can be constrained,
written `γ(S)`: *full*, *partial*, or *external*. It bounds what any component
governing that surface can promise.

**Hard vs. soft requirements.** A component's `R_hard` are the other components it
requires to function; `R_soft` are ones that improve it but are not required. All
three components in this book have `R_hard = ∅` — each is independently
deployable.

**Interaction.** A single, non-agentic exchange with an AI model, modeled as the
tuple `(p, u, m, θ, o)` — prompt, user input, model, inference configuration, and
output (Chapter 1, Axiom 1).

**MDR (Monitoring, Detection & Response).** The cross-cutting component that
governs S_input and S_config and correlates the audit records of EPG and ROC to
detect and respond to system-level issues. Component `g_MDR`. See Chapter 4.

**Procedural attestation.** Human sign-off (`⊨_p`) used to satisfy a constraint
that cannot be checked mechanically, recorded as auditable evidence.

**Residual risk.** The compliance risk that remains after governance is applied.
By the Irreducible Residual Risk theorem (Chapter 1, Theorem 4) it is non-zero
even with all components deployed; governance reduces and evidences it rather than
eliminating it.

**ROC (Runtime Output Controls).** The component that governs S_output and
S_delivery: model output before it reaches a user. Component `g_ROC`. See
Chapter 3.

**Tamper-evident.** A property of the audit records whereby any modification of a
record is detectable, via hash-chaining and independent signing (Chapter 1,
Theorem 3a).
