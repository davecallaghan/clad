import Mathlib.Data.Finset.Lattice.Basic
import Mathlib.Data.Fintype.Basic
import Clad.Surface
import Clad.Constraint

namespace Clad

structure Requirement where
  description : String
  deriving DecidableEq, Repr

@[ext]
structure ComponentSpec where
  surfaces         : Finset Surface
  constraints      : Finset Constraint
  hardRequirements : Finset Requirement
  softRequirements : Finset Requirement
  deriving DecidableEq

/-- Why `⊕` is undefined on a pair of components.

Composition is partial: two components that both govern the same surface cannot be
composed, because the composite would have two enforcement points on one surface and
no rule saying which prevails. `Clad.ComponentComposition.ComposeError` in the Scala
implementation is this type. -/
inductive ComposeError where
  | overlappingSurfaces (overlap : Finset Surface)
  deriving DecidableEq, Repr

/-- Componentwise union. Total, and *not* the composition operator: it is meaningful
only on components whose surfaces are disjoint, which is what `compose` enforces. -/
def ComponentSpec.merge (g₁ g₂ : ComponentSpec) : ComponentSpec where
  surfaces         := g₁.surfaces ∪ g₂.surfaces
  constraints      := g₁.constraints ∪ g₂.constraints
  hardRequirements := g₁.hardRequirements ∪ g₂.hardRequirements
  softRequirements := g₁.softRequirements ∪ g₂.softRequirements

/-- The domain of `⊕`. Stated as an empty intersection rather than `Disjoint` so that
it is decidable by `DecidableEq (Finset Surface)`; `composable_iff_disjoint` bridges
the two. -/
def ComponentSpec.Composable (g₁ g₂ : ComponentSpec) : Prop :=
  g₁.surfaces ∩ g₂.surfaces = ∅

theorem ComponentSpec.composable_iff_disjoint (g₁ g₂ : ComponentSpec) :
    g₁.Composable g₂ ↔ Disjoint g₁.surfaces g₂.surfaces := by
  rw [ComponentSpec.Composable, Finset.disjoint_iff_inter_eq_empty]

instance (g₁ g₂ : ComponentSpec) : Decidable (g₁.Composable g₂) :=
  decEq _ _

/-- The composition operator `⊕` of the meta-framework, partial in exactly the way
the Scala implementation is: it returns an error rather than a component when the two
operands govern a common surface. -/
def ComponentSpec.compose (g₁ g₂ : ComponentSpec) : Except ComposeError ComponentSpec :=
  if g₁.Composable g₂ then .ok (g₁.merge g₂)
  else .error (.overlappingSurfaces (g₁.surfaces ∩ g₂.surfaces))

/-- Composition succeeds exactly on its domain. This is what makes the operator
*partial* rather than merely fallible: there is no other reason it can fail. -/
theorem ComponentSpec.compose_eq_ok_iff (g₁ g₂ : ComponentSpec) :
    (∃ g, g₁.compose g₂ = .ok g) ↔ g₁.Composable g₂ := by
  unfold ComponentSpec.compose
  by_cases h : g₁.Composable g₂ <;> simp [h]

theorem ComponentSpec.compose_of_composable {g₁ g₂ : ComponentSpec}
    (h : g₁.Composable g₂) : g₁.compose g₂ = .ok (g₁.merge g₂) := by
  simp [ComponentSpec.compose, h]

def ComponentSpec.empty : ComponentSpec where
  surfaces         := ∅
  constraints      := ∅
  hardRequirements := ∅
  softRequirements := ∅

end Clad
