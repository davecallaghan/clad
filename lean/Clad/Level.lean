import Mathlib.Data.Fintype.Basic

namespace Clad

inductive Level where
  | Enterprise
  | Department
  | Project
  deriving DecidableEq, Repr

instance : Fintype Level where
  elems := {.Enterprise, .Department, .Project}
  complete x := by cases x <;> decide

def Level.toNat : Level → Nat
  | .Enterprise => 0
  | .Department => 1
  | .Project    => 2

end Clad
