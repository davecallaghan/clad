import Clad.Surface
import Clad.Governability
import Clad.InteractionElement
import Mathlib.Data.Finset.Basic

namespace Clad

-- Map each surface to the interaction elements it governs (meta-framework §4)
def surfaceElements : Surface → Finset InteractionElement
  | .Prompt   => {.Prompt}
  | .Input    => {.UserInput}
  | .Config   => {.Model, .InferConf}
  | .Output   => {.RawOutput}
  | .Delivery => {.Delivered}

-- Map each surface to its governability class (meta-framework §4)
def surfaceGovernability : Surface → Governability
  | .Prompt   => .Full
  | .Input    => .Partial
  | .Config   => .Partial
  | .Output   => .Partial
  | .Delivery => .Full

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

-- Theorem 1, Part 4: Each element belongs to exactly one surface
def elementSurface : InteractionElement → Surface
  | .Prompt    => .Prompt
  | .UserInput => .Input
  | .Model     => .Config
  | .InferConf => .Config
  | .RawOutput => .Output
  | .Delivered => .Delivery

theorem element_unique_surface :
    ∀ e : InteractionElement, e ∈ surfaceElements (elementSurface e) := by
  intro e; cases e <;> decide

theorem element_surface_unique :
    ∀ e : InteractionElement, ∀ s : Surface, e ∈ surfaceElements s → s = elementSurface e := by
  decide

end Clad
