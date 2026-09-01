import Mathlib.Data.Fintype.Basic

namespace Clad

inductive Surface where
  | Prompt
  | Input
  | Config
  | Output
  | Delivery
  deriving DecidableEq, Repr

instance : Fintype Surface where
  elems := {.Prompt, .Input, .Config, .Output, .Delivery}
  complete x := by cases x <;> decide

end Clad
