import Clad.Level

namespace Clad

structure PropertyId where
  value : String
  deriving DecidableEq, Repr

inductive Constraint where
  | obligation  (property : PropertyId) (level : Level)
  | prohibition (property : PropertyId) (level : Level)
  deriving DecidableEq, Repr

end Clad
