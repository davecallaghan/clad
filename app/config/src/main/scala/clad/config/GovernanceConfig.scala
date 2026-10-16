package clad.config

case class AgentConfig(
  id: String,
  name: String,
  authorizedDomains: Seq[String],
  authorizedLevels: Seq[String],
  kind: String = "human"
)

case class ConstraintConfig(
  property: String,
  constraintType: String,
  level: String,
  domain: String,
  version: String = "1.0",
  evaluability: String = "mechanical",
  author: String = "",
  approver: String = ""
)

case class ConstraintHierarchyConfig(
  enterprise: Seq[ConstraintConfig] = Seq.empty,
  department: Seq[ConstraintConfig] = Seq.empty,
  project: Seq[ConstraintConfig] = Seq.empty
)

case class CheckerBinding(
  property: String,
  checkerType: String,
  config: Map[String, String] = Map.empty
)

case class OutputConstraintConfig(
  property: String,
  constraintType: String,
  riskTier: String,
  config: Map[String, String] = Map.empty
)

case class ThresholdConfig(
  property: String,
  flagThreshold: Double,
  riskTier: String,
  owner: String,
  version: String = "1.0",
  level: String = "enterprise"
)

case class FailurePostureConfig(
  epg: String = "fail-closed",
  roc: String = "fail-closed",
  gil: String = "fail-closed"
)

case class GovernanceConfig(
  name: String,
  version: String,
  agents: Seq[AgentConfig] = Seq.empty,
  constraints: ConstraintHierarchyConfig = ConstraintHierarchyConfig(),
  checkers: Seq[CheckerBinding] = Seq.empty,
  outputConstraints: Seq[OutputConstraintConfig] = Seq.empty,
  thresholds: Seq[ThresholdConfig] = Seq.empty,
  failurePosture: FailurePostureConfig = FailurePostureConfig(),
  centralApprovers: Seq[String] = Seq.empty,
  criticalDomains: Seq[String] = Seq.empty,
  project: String = ""
)
