import Lean.Data.Json
import Clad.Evaluate

namespace Clad.DiffTest

open Lean Json

instance : FromJson Clad.Level where
  fromJson? j := do
    let s ← j.getStr?
    match s with
    | "Enterprise" => pure .Enterprise
    | "Department" => pure .Department
    | "Project"    => pure .Project
    | other => throw s!"unknown level: {other}"

instance : ToJson Clad.Level where
  toJson
    | .Enterprise => "Enterprise"
    | .Department => "Department"
    | .Project    => "Project"

instance : FromJson Clad.Constraint where
  fromJson? j := do
    let prop ← (j.getObjVal? "property").bind (·.getStr?)
    let typ ← (j.getObjVal? "type").bind (·.getStr?)
    let lvlStr ← (j.getObjVal? "level").bind (·.getStr?)
    let lvl ← FromJson.fromJson? (Json.str lvlStr)
    match typ with
    | "Obligation"  => pure (.obligation ⟨prop⟩ lvl)
    | "Prohibition" => pure (.prohibition ⟨prop⟩ lvl)
    | other => throw s!"unknown constraint type: {other}"

instance : ToJson Clad.Constraint where
  toJson c := match c with
    | .obligation p l => Json.mkObj [("property", p.value), ("type", "Obligation"), ("level", toJson l)]
    | .prohibition p l => Json.mkObj [("property", p.value), ("type", "Prohibition"), ("level", toJson l)]

private def parseBoolMap (j : Json) : Except String (List (String × Bool)) := do
  let obj ← j.getObj?
  let pairs := obj.toArray.toList
  pairs.mapM fun (k, v) => do
    let b ← v.getBool?
    pure (k, b)

private def parseStringMap (j : Json) : Except String (List (String × String)) := do
  let obj ← j.getObj?
  let pairs := obj.toArray.toList
  pairs.mapM fun (k, v) => do
    let s ← v.getStr?
    pure (k, s)

instance : FromJson Clad.EvalInput where
  fromJson? j := do
    let hier ← j.getObjVal? "hierarchy"
    let entJson ← hier.getObjVal? "enterprise"
    let deptJson ← hier.getObjVal? "department"
    let projJson ← hier.getObjVal? "project"
    let ent ← FromJson.fromJson? entJson (α := Array Clad.Constraint)
    let dept ← FromJson.fromJson? deptJson (α := Array Clad.Constraint)
    let proj ← FromJson.fromJson? projJson (α := Array Clad.Constraint)
    let lvl ← (j.getObjVal? "level").bind FromJson.fromJson?
    let dets ← (j.getObjVal? "detections").bind parseBoolMap
    let evid ← (j.getObjVal? "evidence").bind parseBoolMap
    let evals ← (j.getObjVal? "evaluabilities").bind parseStringMap
    pure {
      enterprise := Array.toList ent
      department := Array.toList dept
      project := Array.toList proj
      level := lvl
      detections := dets
      evidence := evid
      evaluabilities := evals
    }

instance : ToJson Clad.ConstraintEvalResult where
  toJson r := Json.mkObj [
    ("property", r.property),
    ("constraintType", r.constraintType),
    ("evaluability", r.evaluability),
    ("satisfied", r.satisfied)
  ]

instance : ToJson Clad.EvalOutput where
  toJson o := Json.mkObj [
    ("results", toJson o.results.toArray),
    ("allSatisfied", o.allSatisfied)
  ]

end Clad.DiffTest
