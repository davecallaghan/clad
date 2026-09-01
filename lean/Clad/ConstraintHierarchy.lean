import Mathlib.Data.Finset.Lattice.Basic
import Clad.Constraint
import Clad.LevelOrder

namespace Clad

-- Constraint hierarchy: constraints organized by governance level
structure ConstraintHierarchy where
  enterprise : Finset Constraint
  department : Finset Constraint
  project    : Finset Constraint

-- Effective constraints at a given level (cumulative from above)
def ConstraintHierarchy.effectiveAt (h : ConstraintHierarchy) : Level → Finset Constraint
  | .Enterprise => h.enterprise
  | .Department => h.enterprise ∪ h.department
  | .Project    => h.enterprise ∪ h.department ∪ h.project

-- Contradiction predicate: O(φ) vs F(φ) on same property
def Constraint.contradicts : Constraint → Constraint → Bool
  | .obligation p₁ _, .prohibition p₂ _ => p₁ == p₂
  | .prohibition p₁ _, .obligation p₂ _ => p₁ == p₂
  | _, _ => false

-- Contradiction is symmetric
theorem contradicts_symm (c₁ c₂ : Constraint) :
    c₁.contradicts c₂ = c₂.contradicts c₁ := by
  match c₁, c₂ with
  | .obligation _ _, .obligation _ _ => simp [Constraint.contradicts]
  | .prohibition _ _, .prohibition _ _ => simp [Constraint.contradicts]
  | .obligation p₁ _, .prohibition p₂ _ =>
      simp [Constraint.contradicts]
      exact eq_comm
  | .prohibition p₁ _, .obligation p₂ _ =>
      simp [Constraint.contradicts]
      exact eq_comm

-- Same modality never contradicts
theorem contradicts_obligation_obligation (p₁ p₂ : PropertyId) (l₁ l₂ : Level) :
    (Constraint.obligation p₁ l₁).contradicts (Constraint.obligation p₂ l₂) = false := by
  simp [Constraint.contradicts]

theorem contradicts_prohibition_prohibition (p₁ p₂ : PropertyId) (l₁ l₂ : Level) :
    (Constraint.prohibition p₁ l₁).contradicts (Constraint.prohibition p₂ l₂) = false := by
  simp [Constraint.contradicts]

-- Monotonicity: effective constraints grow as we go down the hierarchy
theorem effectiveAt_monotone_ent_dept (h : ConstraintHierarchy) :
    h.effectiveAt .Enterprise ⊆ h.effectiveAt .Department := by
  unfold ConstraintHierarchy.effectiveAt
  exact Finset.subset_union_left

theorem effectiveAt_monotone_dept_proj (h : ConstraintHierarchy) :
    h.effectiveAt .Department ⊆ h.effectiveAt .Project := by
  unfold ConstraintHierarchy.effectiveAt
  intro x hx
  simp [Finset.mem_union] at hx ⊢
  tauto

theorem effectiveAt_monotone_ent_proj (h : ConstraintHierarchy) :
    h.effectiveAt .Enterprise ⊆ h.effectiveAt .Project := by
  exact Finset.Subset.trans (effectiveAt_monotone_ent_dept h) (effectiveAt_monotone_dept_proj h)

-- General monotonicity theorem
theorem effectiveAt_monotone (h : ConstraintHierarchy) (l₁ l₂ : Level)
    (hgov : l₁.governs l₂ = true) :
    h.effectiveAt l₁ ⊆ h.effectiveAt l₂ := by
  match l₁, l₂ with
  | .Enterprise, .Enterprise => exact Finset.Subset.refl _
  | .Enterprise, .Department => exact effectiveAt_monotone_ent_dept h
  | .Enterprise, .Project => exact effectiveAt_monotone_ent_proj h
  | .Department, .Department => exact Finset.Subset.refl _
  | .Department, .Project => exact effectiveAt_monotone_dept_proj h
  | .Project, .Project => exact Finset.Subset.refl _
  | .Department, .Enterprise => simp [Level.governs, Level.toNat] at hgov
  | .Project, .Enterprise => simp [Level.governs, Level.toNat] at hgov
  | .Project, .Department => simp [Level.governs, Level.toNat] at hgov

end Clad
