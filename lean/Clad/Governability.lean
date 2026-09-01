import Mathlib.Data.Fintype.Basic

namespace Clad

inductive Governability where
  | Full
  | Partial
  | External
  deriving DecidableEq, Repr

instance : Fintype Governability where
  elems := {.Full, .Partial, .External}
  complete x := by cases x <;> decide

end Clad
