import Clad.Constraint

namespace Clad

-- Deontic satisfaction: is a constraint satisfied given a detection result?
-- O(φ): satisfied iff φ detected (property MUST be present)
-- F(φ): satisfied iff φ NOT detected (property must NOT be present)
def DeonticSatisfied (c : Constraint) (detected : Bool) : Bool :=
  match c with
  | .obligation _ _  => detected
  | .prohibition _ _ => !detected

-- Rule 1: O(φ) ∧ detected → satisfied
theorem obligation_detected_satisfied (p : PropertyId) (l : Level) :
    DeonticSatisfied (.obligation p l) true = true := rfl

-- Rule 2: O(φ) ∧ ¬detected → ¬satisfied
theorem obligation_not_detected_unsatisfied (p : PropertyId) (l : Level) :
    DeonticSatisfied (.obligation p l) false = false := rfl

-- Rule 3: F(φ) ∧ detected → ¬satisfied
theorem prohibition_detected_unsatisfied (p : PropertyId) (l : Level) :
    DeonticSatisfied (.prohibition p l) true = false := rfl

-- Rule 4: F(φ) ∧ ¬detected → satisfied
theorem prohibition_not_detected_satisfied (p : PropertyId) (l : Level) :
    DeonticSatisfied (.prohibition p l) false = true := rfl

-- O and F always give opposite satisfaction results for the same detection
theorem obligation_prohibition_inversion (p : PropertyId) (l₁ l₂ : Level) (detected : Bool) :
    DeonticSatisfied (.obligation p l₁) detected =
    !(DeonticSatisfied (.prohibition p l₂) detected) := by
  cases detected <;> rfl

-- If we have both O(φ) and F(φ), at least one is always unsatisfied
theorem contradiction_always_unsatisfied (p : PropertyId) (l₁ l₂ : Level) (detected : Bool) :
    DeonticSatisfied (.obligation p l₁) detected = false ∨
    DeonticSatisfied (.prohibition p l₂) detected = false := by
  cases detected <;> simp [DeonticSatisfied]

-- Satisfaction is decidable (follows from Bool)
instance deontic_satisfaction_decidable (c : Constraint) (detected : Bool) :
    Decidable (DeonticSatisfied c detected = true) :=
  inferInstance

end Clad
