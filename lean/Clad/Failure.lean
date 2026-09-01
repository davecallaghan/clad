import Mathlib.Logic.Function.Basic

namespace Clad

-- Failure posture: how a component behaves when it fails
inductive FailurePosture where
  | FailClosed      -- block all interactions
  | FailOpenFlagged -- proceed but flag for review
  | FailOpen        -- proceed silently
  deriving DecidableEq, Repr

-- Failure action: what actually happens
inductive FailureAction where
  | Blocked           -- interaction blocked
  | ProceededFlagged  -- interaction proceeds, flagged
  | ProceededSilent   -- interaction proceeds, no flag
  deriving DecidableEq, Repr

-- Deterministic mapping from posture to action
def FailurePosture.action : FailurePosture → FailureAction
  | .FailClosed      => .Blocked
  | .FailOpenFlagged => .ProceededFlagged
  | .FailOpen        => .ProceededSilent

-- Each specific mapping
theorem fail_closed_blocks :
    FailurePosture.FailClosed.action = .Blocked := rfl

theorem fail_open_flagged_proceeds :
    FailurePosture.FailOpenFlagged.action = .ProceededFlagged := rfl

theorem fail_open_proceeds_silent :
    FailurePosture.FailOpen.action = .ProceededSilent := rfl

-- The mapping is injective (different postures → different actions)
theorem posture_action_injective (p₁ p₂ : FailurePosture)
    (h : p₁.action = p₂.action) : p₁ = p₂ := by
  cases p₁ <;> cases p₂ <;> simp [FailurePosture.action] at h ⊢

-- The mapping is surjective (every action has a posture)
theorem posture_action_surjective (a : FailureAction) :
    ∃ p : FailurePosture, p.action = a := by
  cases a
  · exact ⟨.FailClosed, rfl⟩
  · exact ⟨.FailOpenFlagged, rfl⟩
  · exact ⟨.FailOpen, rfl⟩

-- Therefore it's a bijection (bonus theorem)
theorem posture_action_bijective :
    Function.Bijective FailurePosture.action := by
  constructor
  · intro p₁ p₂ h; exact posture_action_injective p₁ p₂ h
  · intro a; exact posture_action_surjective a

end Clad
