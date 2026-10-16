package clad.conformance.checks

import clad.conformance.*

object NotImplemented:
  def all: Vector[RequirementResult] = Vector(
    "R-BREAKGLASS" -> "Break-glass procedure",
    "R-GSPS" -> "Gold Standard Prompt Set impact analysis",
    "R-CS" -> "Semantic intent decomposition"
  ).map((id, t) => RequirementResult(id, t, "not implemented", Verdict.NotRun, reason = "not implemented by the conformance module"))
