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

def ComponentSpec.compose (g₁ g₂ : ComponentSpec) : ComponentSpec where
  surfaces         := g₁.surfaces ∪ g₂.surfaces
  constraints      := g₁.constraints ∪ g₂.constraints
  hardRequirements := g₁.hardRequirements ∪ g₂.hardRequirements
  softRequirements := g₁.softRequirements ∪ g₂.softRequirements

def ComponentSpec.empty : ComponentSpec where
  surfaces         := ∅
  constraints      := ∅
  hardRequirements := ∅
  softRequirements := ∅

end Clad
