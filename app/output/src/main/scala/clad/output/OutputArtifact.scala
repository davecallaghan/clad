package clad.output

import clad.core.*

case class PromptContext(
  promptDigest: String,
  constraintProperties: Set[PropertyId]
)

case class OutputArtifact(
  content: String,
  metadata: Map[String, String] = Map.empty,
  promptContext: Option[PromptContext] = None
)
