import Clad.Evaluate
import Clad.DiffTest.Json

open Lean

def main : IO Unit := do
  let stdin ← IO.getStdin
  let input ← stdin.readToEnd
  match Json.parse input with
  | Except.error e => IO.eprintln s!"JSON parse error: {e}"; IO.Process.exit 1
  | Except.ok json =>
    match FromJson.fromJson? json (α := Array Clad.EvalInput) with
    | Except.error e => IO.eprintln s!"Deserialization error: {e}"; IO.Process.exit 1
    | Except.ok cases =>
      let results := cases.map Clad.evaluate
      let jsonOut := toJson results
      IO.println jsonOut.compress
