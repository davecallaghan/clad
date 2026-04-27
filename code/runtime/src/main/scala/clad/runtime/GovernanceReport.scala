package clad.runtime

import clad.evaluation.*

case class GovernanceReport(
  evaluation: PromptEvaluationReport,
  artifact: PromptArtifact,
  audit: AuditRecord,
  engineVersion: String,
  configDigest: String
)
