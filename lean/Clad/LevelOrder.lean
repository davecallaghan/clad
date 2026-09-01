import Clad.Level

namespace Clad

-- Enterprise governs all levels; Project is governed by all
def Level.governs (l₁ l₂ : Level) : Bool := l₁.toNat ≤ l₂.toNat

def Level.strictlyGoverns (l₁ l₂ : Level) : Bool := l₁.toNat < l₂.toNat

-- Reflexivity
theorem level_governs_refl (l : Level) : l.governs l = true := by
  cases l <;> decide

-- Transitivity
theorem level_governs_trans (l₁ l₂ l₃ : Level)
    (h₁ : l₁.governs l₂ = true) (h₂ : l₂.governs l₃ = true) :
    l₁.governs l₃ = true := by
  cases l₁ <;> cases l₂ <;> cases l₃ <;> simp_all [Level.governs, Level.toNat]

-- Antisymmetry
theorem level_governs_antisymm (l₁ l₂ : Level)
    (h₁ : l₁.governs l₂ = true) (h₂ : l₂.governs l₁ = true) :
    l₁ = l₂ := by
  cases l₁ <;> cases l₂ <;> simp_all [Level.governs, Level.toNat]

-- Totality
theorem level_governs_total (l₁ l₂ : Level) :
    l₁.governs l₂ = true ∨ l₂.governs l₁ = true := by
  cases l₁ <;> cases l₂ <;> simp [Level.governs, Level.toNat]

-- Enterprise governs all
theorem enterprise_governs_all (l : Level) :
    Level.Enterprise.governs l = true := by
  cases l <;> decide

-- Project governed by all
theorem project_governed_by_all (l : Level) :
    l.governs Level.Project = true := by
  cases l <;> decide

-- Strict ordering is irreflexive
theorem level_strictlyGoverns_irrefl (l : Level) :
    l.strictlyGoverns l = false := by
  cases l <;> decide

-- toNat is injective
theorem level_toNat_injective (l₁ l₂ : Level)
    (h : l₁.toNat = l₂.toNat) : l₁ = l₂ := by
  cases l₁ <;> cases l₂ <;> simp_all [Level.toNat]

end Clad
