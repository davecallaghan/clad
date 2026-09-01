import Clad.Monoid

namespace Clad

/-- Theorem 5: Full Solution Audit Completeness (meta-framework §11).

When EPG, ROC and MDR are deployed with the infrastructure guarantees in place, every
Tier 1 element is covered. The surface aspect is `pipeline_surface_coverage` in
Monoid.lean; this wraps it with the conditional infrastructure hypotheses.

Two limits, both deliberate. The infrastructure hypotheses are opaque `Prop`s carried
through unexamined, so what this theorem establishes on its own is the surface-coverage
conjunct. And that conjunct is over the five pipeline surfaces: the evidence surface is
outside the composed guarantee (`evidence_surface_uncovered`). -/
theorem theorem5_audit_completeness
    (enforcement_holds : Prop)
    (audit_integrity : Prop)
    (gil_properties : Prop)
    (contracts_satisfied : Prop)
    (h_enf : enforcement_holds)
    (h_ai : audit_integrity)
    (h_gil : gil_properties)
    (h_contracts : contracts_satisfied) :
    enforcement_holds ∧ audit_integrity ∧ gil_properties ∧ contracts_satisfied ∧
    (EPG.compose ROC >>= (·.compose MDR)) = .ok
      { surfaces := pipelineSurfaces, constraints := ∅
        evaluators := { ⟨"epg-prompt-evaluator"⟩, ⟨"roc-output-evaluator"⟩, ⟨"mdr-monitor"⟩ }
        hardRequirements := ∅, softRequirements := ∅ } :=
  ⟨h_enf, h_ai, h_gil, h_contracts, pipeline_surface_coverage⟩

/-- Surface coverage does not depend on the infrastructure hypotheses: it is a purely
algebraic fact about the three component definitions. -/
theorem surface_coverage_unconditional :
    (EPG.compose ROC >>= (·.compose MDR)) = .ok
      { surfaces := pipelineSurfaces, constraints := ∅
        evaluators := { ⟨"epg-prompt-evaluator"⟩, ⟨"roc-output-evaluator"⟩, ⟨"mdr-monitor"⟩ }
        hardRequirements := ∅, softRequirements := ∅ } :=
  pipeline_surface_coverage

end Clad
