package clad.api

import upickle.default.*

case class EvaluateRequest(prompt: String, metadata: Map[String, String] = Map.empty) derives ReadWriter
case class ConstraintSummary(property: String, constraintType: String, level: String) derives ReadWriter
case class EvaluateResponse(allSatisfied: Boolean, totalConstraints: Int, satisfiedCount: Int,
  unsatisfied: Seq[ConstraintSummary], auditDigest: String) derives ReadWriter

case class OutputEvaluateRequest(content: String, metadata: Map[String, String] = Map.empty) derives ReadWriter
case class OutputConstraintSummary(property: String, constraintType: String, riskTier: String) derives ReadWriter
case class OutputEvaluateResponse(decision: String, totalConstraints: Int, satisfiedCount: Int,
  unsatisfied: Seq[OutputConstraintSummary]) derives ReadWriter

case class ConfigSummary(name: String, version: String, constraintCount: Int, checkerCount: Int, agentCount: Int) derives ReadWriter
case class ReloadResponse(status: String, name: String = "", version: String = "", errors: Seq[String] = Seq.empty) derives ReadWriter
case class ConstraintListResponse(constraints: Seq[ConstraintSummary], total: Int) derives ReadWriter
case class HealthResponse(status: String, configLoaded: Boolean, configName: String, configVersion: String, uptime: String) derives ReadWriter
case class ErrorResponse(error: String, code: String) derives ReadWriter
