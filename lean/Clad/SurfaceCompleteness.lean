import Clad.Surface
import Clad.Governability
import Clad.InteractionElement
import Mathlib.Data.Finset.Basic

namespace Clad

-- Map each surface to the interaction elements it governs (meta-framework §4, amended
-- to the six-element tuple i = (x, u, M, θ, o, E))
def surfaceElements : Surface → Finset InteractionElement
  | .Prompt   => {.Prompt}
  | .Input    => {.UserInput}
  | .Config   => {.Model, .InferConf}
  | .Output   => {.RawOutput}
  | .Delivery => {.Delivered}
  | .Evidence => {.Evidence}

/-- Governability class of a surface, given how evidence is provided.

The deployment parameter is not decoration. Every other surface's class is fixed by the
technology: prompts are governable because they are constructed, model internals are
external because they are not observable. The evidence surface is either, depending on
whether the architecture makes the basis explicit — see
`evidence_is_the_only_deployment_dependent_surface`, which is the formal content of the
claim that this turns a classification into a design requirement. -/
def surfaceGovernability : Surface → EvidenceProvision → Governability
  | .Prompt,   _            => .Full
  | .Input,    _            => .Partial
  | .Config,   _            => .Partial
  | .Output,   _            => .Partial
  | .Delivery, _            => .Full
  | .Evidence, .Retrieved   => .Full
  | .Evidence, .Parametric  => .External

/-- The evidence surface is the only one whose governability depends on the deployment. -/
theorem evidence_is_the_only_deployment_dependent_surface :
    ∀ s : Surface,
      (∃ p q : EvidenceProvision, surfaceGovernability s p ≠ surfaceGovernability s q)
        ↔ s = .Evidence := by
  decide

-- Theorem 1, Part 1: Every interaction element is covered by some surface
theorem surface_elements_exhaustive :
    ∀ e : InteractionElement, ∃ s : Surface, e ∈ surfaceElements s := by
  intro e
  cases e
  · exact ⟨.Prompt, by decide⟩
  · exact ⟨.Input, by decide⟩
  · exact ⟨.Config, by decide⟩
  · exact ⟨.Config, by decide⟩
  · exact ⟨.Output, by decide⟩
  · exact ⟨.Delivery, by decide⟩
  · exact ⟨.Evidence, by decide⟩

-- Theorem 1, Part 2: Surfaces govern disjoint elements
theorem surface_elements_pairwise_disjoint :
    ∀ s₁ s₂ : Surface, s₁ ≠ s₂ → Disjoint (surfaceElements s₁) (surfaceElements s₂) := by
  decide

-- Theorem 1, Part 3: Every surface has at least one element
theorem surface_elements_nonempty :
    ∀ s : Surface, (surfaceElements s).Nonempty := by
  intro s; cases s
  · exact ⟨.Prompt, by decide⟩
  · exact ⟨.UserInput, by decide⟩
  · exact ⟨.Model, by decide⟩
  · exact ⟨.RawOutput, by decide⟩
  · exact ⟨.Delivered, by decide⟩
  · exact ⟨.Evidence, by decide⟩

-- Theorem 1, Part 4: Each element belongs to exactly one surface
def elementSurface : InteractionElement → Surface
  | .Prompt    => .Prompt
  | .UserInput => .Input
  | .Model     => .Config
  | .InferConf => .Config
  | .RawOutput => .Output
  | .Delivered => .Delivery
  | .Evidence  => .Evidence

theorem element_unique_surface :
    ∀ e : InteractionElement, e ∈ surfaceElements (elementSurface e) := by
  intro e; cases e <;> decide

theorem element_surface_unique :
    ∀ e : InteractionElement, ∀ s : Surface, e ∈ surfaceElements s → s = elementSurface e := by
  decide

end Clad
