package clad.evaluation.test

import clad.core.*
import clad.evaluation.*

object TestCheckers:

  class AlwaysDetects(val propertyId: PropertyId) extends PropertyChecker:
    def check(artifact: PromptArtifact): Boolean = true

  class NeverDetects(val propertyId: PropertyId) extends PropertyChecker:
    def check(artifact: PromptArtifact): Boolean = false

  class KeywordChecker(val propertyId: PropertyId, keyword: String) extends PropertyChecker:
    def check(artifact: PromptArtifact): Boolean = artifact.content.contains(keyword)
