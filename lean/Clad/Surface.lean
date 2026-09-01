import Mathlib.Data.Fintype.Basic

namespace Clad

/-- A control surface of the interaction pipeline.

`Prompt`–`Delivery` are the framework's original five-element partition over the tuple
`(x, u, M, θ, o)`. `Evidence` is the sixth, over the amended tuple `(x, u, M, θ, o, E)`,
where `E` is the evidential basis an assertion is permitted to rest on. It is a surface
of its own because no presence check on the artifact can establish that an assertion
*rested on* the evidence cited for it. -/
inductive Surface where
  | Prompt
  | Input
  | Config
  | Output
  | Delivery
  | Evidence
  deriving DecidableEq, Repr

instance : Fintype Surface where
  elems := {.Prompt, .Input, .Config, .Output, .Delivery, .Evidence}
  complete x := by cases x <;> decide

/-- How the evidential basis reaches the model. A deployment choice rather than a
property of the technology, and therefore what determines the evidence surface's
governability class. -/
inductive EvidenceProvision where
  | Retrieved   -- identified and versioned at inference time
  | Parametric  -- absorbed in training weights, not identifiable per interaction
  deriving DecidableEq, Repr

instance : Fintype EvidenceProvision where
  elems := {.Retrieved, .Parametric}
  complete x := by cases x <;> decide

/-- The original five-element partition. Composition of EPG, ROC and MDR covers exactly
this set — see `Clad.pipeline_surface_coverage` and `Clad.evidence_surface_uncovered`. -/
def pipelineSurfaces : Finset Surface :=
  {.Prompt, .Input, .Config, .Output, .Delivery}

theorem evidence_not_pipeline : Surface.Evidence ∉ pipelineSurfaces := by decide

theorem pipeline_ne_univ : pipelineSurfaces ≠ Finset.univ := by decide

end Clad
