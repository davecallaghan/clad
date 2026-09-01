import Clad.Deontic
import Clad.Evaluability

namespace Clad

structure EvalInput where
  enterprise    : List Constraint
  department    : List Constraint
  project       : List Constraint
  level         : Level
  detections    : List (String × Bool)
  evidence      : List (String × Bool)
  evaluabilities : List (String × String)

structure ConstraintEvalResult where
  property       : String
  constraintType : String
  evaluability   : String
  satisfied      : Bool

structure EvalOutput where
  results      : List ConstraintEvalResult
  allSatisfied : Bool

def effectiveConstraintsList (ent dept proj : List Constraint) : Level → List Constraint
  | .Enterprise => ent
  | .Department => ent ++ dept
  | .Project    => ent ++ dept ++ proj

def lookupBool (map : List (String × Bool)) (key : String) : Bool :=
  match map.find? (fun p => p.1 == key) with
  | some (_, b) => b
  | none => false

def lookupString (map : List (String × String)) (key : String) : String :=
  match map.find? (fun p => p.1 == key) with
  | some (_, s) => s
  | none => "Mechanical"

def constraintProperty (c : Constraint) : String :=
  match c with
  | .obligation p _ => p.value
  | .prohibition p _ => p.value

def constraintTypeName (c : Constraint) : String :=
  match c with
  | .obligation _ _ => "Obligation"
  | .prohibition _ _ => "Prohibition"

def evaluateOne (input : EvalInput) (c : Constraint) : ConstraintEvalResult :=
  let prop := constraintProperty c
  let evalClass := lookupString input.evaluabilities prop
  let detected := match evalClass with
    | "Procedural" => lookupBool input.evidence prop
    | _ => lookupBool input.detections prop
  let satisfied := DeonticSatisfied c detected
  { property := prop
    constraintType := constraintTypeName c
    evaluability := evalClass
    satisfied := satisfied }

def evaluate (input : EvalInput) : EvalOutput :=
  let constraints := effectiveConstraintsList input.enterprise input.department input.project input.level
  let results := constraints.map (evaluateOne input)
  { results := results
    allSatisfied := results.all (·.satisfied) }

-- Smoke test: obligation with detection = satisfied
#eval
  let input : EvalInput := {
    enterprise := [.obligation ⟨"phi"⟩ .Enterprise]
    department := []
    project := []
    level := .Enterprise
    detections := [("phi", true)]
    evidence := []
    evaluabilities := [("phi", "Mechanical")]
  }
  (evaluate input).allSatisfied  -- should be true

-- Smoke test: prohibition with detection = unsatisfied
#eval
  let input : EvalInput := {
    enterprise := [.prohibition ⟨"psi"⟩ .Enterprise]
    department := []
    project := []
    level := .Enterprise
    detections := [("psi", true)]
    evidence := []
    evaluabilities := [("psi", "Mechanical")]
  }
  (evaluate input).allSatisfied  -- should be false

end Clad
