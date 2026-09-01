import Mathlib.Data.Finset.Basic

namespace Clad

-- Interaction status: the three possible states of a governed interaction
inductive InteractionStatus where
  | FullyGoverned     -- complete audit chain exists
  | PartiallyGoverned -- degraded records exist (component failure)
  | Ghost             -- registered in GIL but no records found
  deriving DecidableEq, Repr

-- Classify an interaction based on audit and degraded record sets
def classifyInteraction (α : Type*) [DecidableEq α] (id : α)
    (audited : Finset α) (degraded : Finset α) : InteractionStatus :=
  if id ∈ audited then .FullyGoverned
  else if id ∈ degraded then .PartiallyGoverned
  else .Ghost

-- Theorem 3b: Every interaction is in exactly one of three states
theorem ghost_trichotomy (α : Type*) [DecidableEq α] (id : α)
    (audited degraded : Finset α) :
    let s := classifyInteraction α id audited degraded
    s = .FullyGoverned ∨ s = .PartiallyGoverned ∨ s = .Ghost := by
  simp only [classifyInteraction]
  split
  · left; rfl
  · split
    · right; left; rfl
    · right; right; rfl

-- Not audited and not degraded implies Ghost
theorem no_silent_ungoverned (α : Type*) [DecidableEq α] (id : α)
    (audited degraded : Finset α)
    (h₁ : id ∉ audited) (h₂ : id ∉ degraded) :
    classifyInteraction α id audited degraded = .Ghost := by
  simp [classifyInteraction, h₁, h₂]

-- FullyGoverned implies in audited set
theorem fully_governed_implies_audited (α : Type*) [DecidableEq α] (id : α)
    (audited degraded : Finset α)
    (h : classifyInteraction α id audited degraded = .FullyGoverned) :
    id ∈ audited := by
  unfold classifyInteraction at h
  by_cases h₁ : id ∈ audited
  · exact h₁
  · simp [h₁] at h
    by_cases h₂ : id ∈ degraded
    · simp [h₂] at h
    · simp [h₂] at h

-- PartiallyGoverned implies in degraded but not audited
theorem partially_governed_implies_degraded (α : Type*) [DecidableEq α] (id : α)
    (audited degraded : Finset α)
    (h : classifyInteraction α id audited degraded = .PartiallyGoverned) :
    id ∉ audited ∧ id ∈ degraded := by
  simp [classifyInteraction] at h
  split at h
  · contradiction
  · split at h
    · exact ⟨‹_›, ‹_›⟩
    · contradiction

-- Ghost implies not in audited and not in degraded
theorem ghost_implies_unrecorded (α : Type*) [DecidableEq α] (id : α)
    (audited degraded : Finset α)
    (h : classifyInteraction α id audited degraded = .Ghost) :
    id ∉ audited ∧ id ∉ degraded := by
  simp [classifyInteraction] at h
  split at h
  · contradiction
  · split at h
    · contradiction
    · exact ⟨‹_›, ‹_›⟩

-- Classification is exhaustive: the three cases cover all possibilities
theorem classification_exhaustive (α : Type*) [DecidableEq α] (id : α)
    (audited degraded : Finset α) :
    (id ∈ audited) ∨ (id ∉ audited ∧ id ∈ degraded) ∨ (id ∉ audited ∧ id ∉ degraded) := by
  by_cases h₁ : id ∈ audited
  · left; exact h₁
  · by_cases h₂ : id ∈ degraded
    · right; left; exact ⟨h₁, h₂⟩
    · right; right; exact ⟨h₁, h₂⟩

end Clad
