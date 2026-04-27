package clad.output.test

import clad.core.*
import clad.output.*

class AlwaysFlags(val classifierId: String, val version: String = "1.0", label: String = "always_flag") extends OutputClassifier:
  def classify(artifact: OutputArtifact): ClassificationResult =
    ClassificationResult(classifierId, version, 1.0, label)

class NeverFlags(val classifierId: String, val version: String = "1.0", label: String = "never_flag") extends OutputClassifier:
  def classify(artifact: OutputArtifact): ClassificationResult =
    ClassificationResult(classifierId, version, 0.0, label)

class FixedScoreClassifier(val classifierId: String, val version: String = "1.0", score: Double, label: String = "fixed_score") extends OutputClassifier:
  def classify(artifact: OutputArtifact): ClassificationResult =
    ClassificationResult(classifierId, version, score, label)

class AlwaysDetectsChecker(val propertyId: PropertyId) extends OutputChecker:
  def check(artifact: OutputArtifact): Boolean = true

class NeverDetectsChecker(val propertyId: PropertyId) extends OutputChecker:
  def check(artifact: OutputArtifact): Boolean = false

class KeywordOutputChecker(val propertyId: PropertyId, keyword: String) extends OutputChecker:
  def check(artifact: OutputArtifact): Boolean = artifact.content.toLowerCase.contains(keyword.toLowerCase)

class RegexOutputChecker(val propertyId: PropertyId, pattern: scala.util.matching.Regex) extends OutputChecker:
  def check(artifact: OutputArtifact): Boolean = pattern.findFirstIn(artifact.content).isDefined
