import Clad.Component

namespace Clad

/-! # Theorem 6: Governance Monoid (meta-framework §12)

Governance components with composition form a commutative monoid.
- Lemma 3c: Identity (`compose g empty = g`)
- Lemma 3b: Commutativity (`compose g₁ g₂ = compose g₂ g₁`)
- Lemma 3a: Associativity (`compose (compose g₁ g₂) g₃ = compose g₁ (compose g₂ g₃)`)
- Closure: disjointness of surfaces is preserved through composition
-/

-- Lemma 3c (Identity)
theorem ComponentSpec.compose_empty_right (g : ComponentSpec) :
    g.compose .empty = g := by
  ext <;> simp [ComponentSpec.compose, ComponentSpec.empty]

theorem ComponentSpec.empty_compose_left (g : ComponentSpec) :
    ComponentSpec.empty.compose g = g := by
  ext <;> simp [ComponentSpec.compose, ComponentSpec.empty]

-- Lemma 3b (Commutativity)
theorem ComponentSpec.compose_comm (g₁ g₂ : ComponentSpec) :
    g₁.compose g₂ = g₂.compose g₁ := by
  ext <;> simp [ComponentSpec.compose, Finset.union_comm]

-- Lemma 3a (Associativity)
theorem ComponentSpec.compose_assoc (g₁ g₂ g₃ : ComponentSpec) :
    (g₁.compose g₂).compose g₃ = g₁.compose (g₂.compose g₃) := by
  ext <;> simp [ComponentSpec.compose, Finset.union_assoc]

-- Closure: disjointness preserved through composition
theorem ComponentSpec.compose_disjoint_preserved {g₁ g₂ g₃ : ComponentSpec}
    (h₁₃ : Disjoint g₁.surfaces g₃.surfaces)
    (h₂₃ : Disjoint g₂.surfaces g₃.surfaces) :
    Disjoint (g₁.compose g₂).surfaces g₃.surfaces := by
  exact Disjoint.sup_left h₁₃ h₂₃

-- Concrete components (meta-framework §11)
def EPG : ComponentSpec where
  surfaces := {Surface.Prompt}
  constraints := ∅
  hardRequirements := ∅
  softRequirements := ∅

def ROC : ComponentSpec where
  surfaces := {Surface.Output, Surface.Delivery}
  constraints := ∅
  hardRequirements := ∅
  softRequirements := ∅

def MDR : ComponentSpec where
  surfaces := {Surface.Input, Surface.Config}
  constraints := ∅
  hardRequirements := ∅
  softRequirements := ∅

-- Pairwise disjointness
theorem epg_roc_disjoint : Disjoint EPG.surfaces ROC.surfaces := by decide
theorem epg_mdr_disjoint : Disjoint EPG.surfaces MDR.surfaces := by decide
theorem roc_mdr_disjoint : Disjoint ROC.surfaces MDR.surfaces := by decide

-- Full surface coverage (Theorem 5, surface aspect)
theorem full_surface_coverage :
    (EPG.compose ROC |>.compose MDR).surfaces = Finset.univ := by decide

end Clad
