import Mathlib.Data.Fintype.Basic

namespace Clad

inductive InteractionElement where
  | Prompt      -- p (prompt)
  | UserInput   -- u (user input)
  | Model       -- m (model)
  | InferConf   -- θ (inference configuration)
  | RawOutput   -- o (raw model output)
  | Delivered   -- o' (delivered output)
  | Evidence    -- E (the evidential basis the assertion may rest on)
  deriving DecidableEq, Repr

instance : Fintype InteractionElement where
  elems := {.Prompt, .UserInput, .Model, .InferConf, .RawOutput, .Delivered}
  complete x := by cases x <;> decide

end Clad
