import Clad.Component

namespace Clad

/-! # Theorem 6: Governance Composition (meta-framework §12)

Governance components under `⊕` form a **partial** commutative monoid. The partiality
is the substance of the result, not a technicality: two components that govern the
same surface have no composite, because the composite would place two enforcement
points on one surface with no rule saying which prevails.

That makes the laws read differently from a total monoid's:

- **Identity** (Lemma 3c) is unconditional — `empty` governs no surface, so it is
  composable with everything.
- **Commutativity** (Lemma 3b) is unconditional *including in failure*: when the
  operands overlap, both orders fail with the same overlap.
- **Associativity** (Lemma 3a) holds on the domain and only there. Off the domain the
  two bracketings can fail with *different* overlaps — `(g₁ ⊕ g₂) ⊕ g₃` reports the
  overlap between `g₁` and `g₂`, while `g₁ ⊕ (g₂ ⊕ g₃)` reports the one between `g₂`
  and `g₃` — so unconditional associativity is false, and stating it would be an
  overclaim rather than a stronger theorem.
-/

/-! ## The underlying union -/

theorem ComponentSpec.merge_comm (g₁ g₂ : ComponentSpec) :
    g₁.merge g₂ = g₂.merge g₁ := by
  ext <;> simp [ComponentSpec.merge, Finset.union_comm]

theorem ComponentSpec.merge_assoc (g₁ g₂ g₃ : ComponentSpec) :
    (g₁.merge g₂).merge g₃ = g₁.merge (g₂.merge g₃) := by
  ext <;> simp [ComponentSpec.merge, Finset.union_assoc]

theorem ComponentSpec.merge_empty_right (g : ComponentSpec) :
    g.merge .empty = g := by
  ext <;> simp [ComponentSpec.merge, ComponentSpec.empty]

theorem ComponentSpec.empty_merge_left (g : ComponentSpec) :
    ComponentSpec.empty.merge g = g := by
  ext <;> simp [ComponentSpec.merge, ComponentSpec.empty]

/-! ## The domain of `⊕` -/

theorem ComponentSpec.composable_comm {g₁ g₂ : ComponentSpec} (h : g₁.Composable g₂) :
    g₂.Composable g₁ := by
  rw [ComponentSpec.composable_iff_disjoint] at h ⊢
  exact h.symm

theorem ComponentSpec.composable_empty_right (g : ComponentSpec) :
    g.Composable .empty := by
  simp [ComponentSpec.Composable, ComponentSpec.empty]

theorem ComponentSpec.empty_composable_left (g : ComponentSpec) :
    ComponentSpec.empty.Composable g := by
  simp [ComponentSpec.Composable, ComponentSpec.empty]

/-- Closure: composing on the left preserves disjointness from a third component.
This is what lets a composite be extended one component at a time. -/
theorem ComponentSpec.merge_composable_left {g₁ g₂ g₃ : ComponentSpec}
    (h₁₃ : g₁.Composable g₃) (h₂₃ : g₂.Composable g₃) :
    (g₁.merge g₂).Composable g₃ := by
  rw [ComponentSpec.composable_iff_disjoint] at h₁₃ h₂₃ ⊢
  simpa [ComponentSpec.merge, Finset.disjoint_union_left] using ⟨h₁₃, h₂₃⟩

theorem ComponentSpec.composable_merge_right {g₁ g₂ g₃ : ComponentSpec}
    (h₁₂ : g₁.Composable g₂) (h₁₃ : g₁.Composable g₃) :
    g₁.Composable (g₂.merge g₃) := by
  rw [ComponentSpec.composable_iff_disjoint] at h₁₂ h₁₃ ⊢
  simpa [ComponentSpec.merge, Finset.disjoint_union_right] using ⟨h₁₂, h₁₃⟩

/-! ## The monoid laws -/

-- Lemma 3c (Identity)
theorem ComponentSpec.compose_empty_right (g : ComponentSpec) :
    g.compose .empty = .ok g := by
  rw [ComponentSpec.compose_of_composable (g.composable_empty_right),
      ComponentSpec.merge_empty_right]

theorem ComponentSpec.empty_compose_left (g : ComponentSpec) :
    ComponentSpec.empty.compose g = .ok g := by
  rw [ComponentSpec.compose_of_composable (ComponentSpec.empty_composable_left g),
      ComponentSpec.empty_merge_left]

-- Lemma 3b (Commutativity), including agreement of the failure payload
theorem ComponentSpec.compose_comm (g₁ g₂ : ComponentSpec) :
    g₁.compose g₂ = g₂.compose g₁ := by
  unfold ComponentSpec.compose
  by_cases h : g₁.Composable g₂
  · simp [h, ComponentSpec.composable_comm h, ComponentSpec.merge_comm]
  · have h' : ¬ g₂.Composable g₁ := fun hc => h (ComponentSpec.composable_comm hc)
    simp [h, h', Finset.inter_comm]

-- Lemma 3a (Associativity), on the domain of the operator
theorem ComponentSpec.compose_assoc_of_composable {g₁ g₂ g₃ : ComponentSpec}
    (h₁₂ : g₁.Composable g₂) (h₁₃ : g₁.Composable g₃) (h₂₃ : g₂.Composable g₃) :
    (g₁.compose g₂ >>= (·.compose g₃)) = (g₂.compose g₃ >>= (g₁.compose ·)) := by
  rw [ComponentSpec.compose_of_composable h₁₂, ComponentSpec.compose_of_composable h₂₃]
  simp only [bind, Except.bind]
  rw [ComponentSpec.compose_of_composable (ComponentSpec.merge_composable_left h₁₃ h₂₃),
      ComponentSpec.compose_of_composable (ComponentSpec.composable_merge_right h₁₂ h₁₃),
      ComponentSpec.merge_assoc]

/-! ## The three concrete components (meta-framework §11) -/

def EPG : ComponentSpec where
  evaluators := { ⟨"epg-prompt-evaluator"⟩ }
  surfaces := {Surface.Prompt}
  constraints := ∅
  hardRequirements := ∅
  softRequirements := ∅

def ROC : ComponentSpec where
  evaluators := { ⟨"roc-output-evaluator"⟩ }
  surfaces := {Surface.Output, Surface.Delivery}
  constraints := ∅
  hardRequirements := ∅
  softRequirements := ∅

def MDR : ComponentSpec where
  evaluators := { ⟨"mdr-monitor"⟩ }
  surfaces := {Surface.Input, Surface.Config}
  constraints := ∅
  hardRequirements := ∅
  softRequirements := ∅

-- Pairwise composability: the three components are in the operator's domain
theorem epg_roc_composable : EPG.Composable ROC := by decide
theorem epg_mdr_composable : EPG.Composable MDR := by decide
theorem roc_mdr_composable : ROC.Composable MDR := by decide

/-- The composition of the three components is defined, and covers exactly the five
pipeline surfaces (Theorem 5, surface aspect).

Both halves matter: a coverage claim about a composite that does not exist would be
vacuous. And the covered set is `pipelineSurfaces`, not `Finset.univ` — adding the
evidence surface leaves the composed guarantee "unaffected in what it claims and
narrowed in what it covers". Stating it over `univ` would be false; stating the
*equality* rather than a containment is what makes `evidence_surface_uncovered`
follow. -/
theorem pipeline_surface_coverage :
    (EPG.compose ROC >>= (·.compose MDR)) = .ok
      { surfaces := pipelineSurfaces, constraints := ∅
        evaluators := { ⟨"epg-prompt-evaluator"⟩, ⟨"roc-output-evaluator"⟩, ⟨"mdr-monitor"⟩ }
        hardRequirements := ∅, softRequirements := ∅ } := by
  decide

/-- No component governs the evidence surface. A stated result rather than an omission:
the residual risk attributable to evidence is nameable precisely because it is outside
the composed guarantee, and whoever introduces a component for it has to change this. -/
theorem evidence_surface_uncovered :
    ∀ g, (EPG.compose ROC >>= (·.compose MDR)) = .ok g →
      Surface.Evidence ∉ g.surfaces := by
  intro g h
  rw [pipeline_surface_coverage] at h
  have : g = { surfaces := pipelineSurfaces, constraints := ∅
               evaluators := { ⟨"epg-prompt-evaluator"⟩, ⟨"roc-output-evaluator"⟩,
                               ⟨"mdr-monitor"⟩ }
               hardRequirements := ∅, softRequirements := ∅ } := by
    injection h
  subst this
  exact evidence_not_pipeline

end Clad
