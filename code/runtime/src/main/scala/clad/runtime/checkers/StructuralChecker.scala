package clad.runtime.checkers

import clad.core.*
import clad.evaluation.*

class StructuralChecker(
  val propertyId: PropertyId,
  requirement: MetadataRequirement
) extends PropertyChecker:
  def check(artifact: PromptArtifact): Boolean =
    requirement.isSatisfied(artifact.metadata)
