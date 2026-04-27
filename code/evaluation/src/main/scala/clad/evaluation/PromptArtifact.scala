package clad.evaluation

case class PromptArtifact(
  content: String,
  metadata: Map[String, String] = Map.empty
)
