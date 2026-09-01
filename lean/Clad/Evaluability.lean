import Mathlib.Data.Finset.Lattice.Basic
import Clad.Constraint

namespace Clad

-- Evaluability classes (meta-framework §5.1)
inductive EvaluabilityClass where
  | Mechanical   -- automated, deterministic checkers
  | Procedural   -- human attestation with evidence
  deriving DecidableEq, Repr

instance : Fintype EvaluabilityClass where
  elems := {.Mechanical, .Procedural}
  complete x := by cases x <;> decide

-- A constraint paired with its evaluability classification
structure EvaluableConstraint where
  constraint : Constraint
  evaluability : EvaluabilityClass
  deriving DecidableEq

-- No evaluability gap: every EvaluabilityClass is either Mechanical or Procedural
-- (This is trivially true from the 2-constructor inductive, but we state it
-- for correspondence with the meta-framework's "No Evaluability Gap Theorem")
theorem no_evaluability_gap (ec : EvaluabilityClass) :
    ec = .Mechanical ∨ ec = .Procedural := by
  cases ec <;> simp

-- The mechanical and procedural subsets of a constraint set are disjoint
theorem evaluability_partition_disjoint (cs : Finset EvaluableConstraint) :
    Disjoint
      (cs.filter fun ec => ec.evaluability = .Mechanical)
      (cs.filter fun ec => ec.evaluability = .Procedural) := by
  apply Finset.disjoint_filter.mpr
  intro x _ hm hp
  simp at hm hp
  rw [hm] at hp
  exact absurd hp (by decide)

-- The mechanical and procedural subsets cover the full set
theorem evaluability_partition_complete (cs : Finset EvaluableConstraint) :
    (cs.filter fun ec => ec.evaluability = .Mechanical) ∪
    (cs.filter fun ec => ec.evaluability = .Procedural) = cs := by
  ext x
  simp only [Finset.mem_union, Finset.mem_filter]
  constructor
  · rintro (⟨h, _⟩ | ⟨h, _⟩) <;> exact h
  · intro hx
    cases h : x.evaluability <;> simp [hx]

end Clad
