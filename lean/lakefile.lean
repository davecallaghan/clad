import Lake
open Lake DSL

package «clad-lean» where
  leanOptions := #[⟨`autoImplicit, false⟩]

require "leanprover-community" / "mathlib"

@[default_target]
lean_lib «Clad» where

lean_exe «clad-difftest» where
  root := `Clad.DiffTest.Main
