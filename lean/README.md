# Clad — Lean 4 Formal Verification

Machine-checked proofs of algebraic properties from the Clad governance framework.

## What's Proved

**Theorem 6 (Governance Monoid, meta-framework §12):** Governance components
with composition form a commutative monoid.

| Lemma | Statement | File |
|-------|-----------|------|
| 3c (Identity) | `g.compose empty = g` | `Clad/Monoid.lean` |
| 3b (Commutativity) | `g₁.compose g₂ = g₂.compose g₁` | `Clad/Monoid.lean` |
| 3a (Associativity) | `(g₁.compose g₂).compose g₃ = g₁.compose (g₂.compose g₃)` | `Clad/Monoid.lean` |
| Closure | Disjointness preserved through composition | `Clad/Monoid.lean` |
| Full Coverage | EPG + ROC + MDR covers all 5 surfaces | `Clad/Monoid.lean` |

## Type Mapping

| Lean Type | Scala Type | Meta-Framework |
|-----------|------------|----------------|
| `Clad.Surface` | `clad.core.Surface` | §4 (Control Surfaces) |
| `Clad.Level` | `clad.core.Level` | §3 (Constraint Hierarchy) |
| `Clad.Constraint` | `clad.core.Constraint` | §3.2 (Deontic Operators) |
| `Clad.ComponentSpec` | `clad.core.ComponentSpec` | §8 (Component Specs) |
| `Clad.ComponentSpec.compose` | `ComponentComposition.compose` | §12 (Composition ⊕) |

## Prerequisites

Install [elan](https://github.com/leanprover/elan) (Lean version manager):

    curl https://raw.githubusercontent.com/leanprover/elan/master/elan-init.sh -sSf | sh

## Build & Verify

    cd lean
    lake exe cache get   # download prebuilt mathlib (first time only)
    lake build           # type-check all proofs

A successful `lake build` with no `sorry` warnings means all proofs are machine-checked.

## Assumptions

The proofs assume the Lean types faithfully model the Scala implementation.
This correspondence is verified by inspection, not by automated extraction.
The Axiom 3 preconditions (P1-P3) are assumed, not proved — they are
infrastructure properties outside the scope of algebraic verification.
