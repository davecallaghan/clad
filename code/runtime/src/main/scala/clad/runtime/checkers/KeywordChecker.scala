package clad.runtime.checkers

import clad.core.*
import clad.evaluation.*

class KeywordChecker(
  val propertyId: PropertyId,
  keywords: Set[String],
  threshold: Int = 1
) extends PropertyChecker:
  def check(artifact: PromptArtifact): Boolean =
    val lower = artifact.content.toLowerCase
    keywords.count(kw => lower.contains(kw.toLowerCase)) >= threshold
