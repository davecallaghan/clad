package clad.conformance

case class RequirementResult(id: String, title: String, area: String, verdict: Verdict,
  evidence: Vector[String] = Vector.empty, reason: String = "", metrics: Map[String, Double] = Map.empty)
