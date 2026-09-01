package clad.output

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.output.test.*
import java.time.Instant

class FallbackHandlerSpec extends AnyFlatSpec with Matchers:
  val now: Instant = Instant.parse("2026-04-22T18:00:00Z")

  def mkBlockedOutput(content: String = "SSN: 123-45-6789"): BlockedOutput =
    val artifact = OutputArtifact(content)
    val ssnChecker = RegexOutputChecker(PropertyId.unsafe("ssn_detected"), "\\d{3}-\\d{2}-\\d{4}".r)
    val constraint = DeterministicConstraint(PropertyId.unsafe("ssn_detected"), ssnChecker, RiskTier.Critical)
    val constraints = OutputConstraintSet(deterministic = Seq(constraint))
    val report = OutputEvaluator.evaluate(artifact, constraints, now)
    BlockedOutput(artifact, report, report.unsatisfied)

  "SafeResponseFallback" should "return default safe response with unable to provide" in {
    val handler = SafeResponseFallback()
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    result.action shouldBe FallbackAction.SafeResponseSubstituted
    result.deliveredContent should include("unable to provide")
    result.originalBlocked shouldBe true
  }

  it should "use custom response text when provided" in {
    val customMessage = "Custom safety message"
    val handler = SafeResponseFallback(customMessage)
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    result.action shouldBe FallbackAction.SafeResponseSubstituted
    result.deliveredContent shouldBe customMessage
    result.originalBlocked shouldBe true
  }

  "RedactionFallback" should "redact detected patterns and return Redacted when re-evaluation passes" in {
    val ssnPattern = "\\d{3}-\\d{2}-\\d{4}".r
    val handler = RedactionFallback(
      redactionMarker = "[REDACTED]",
      patterns = Seq(ssnPattern),
      reEvaluate = artifact => OutputEvaluator.evaluate(artifact, OutputConstraintSet(), now)
    )
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    result.action shouldBe FallbackAction.Redacted
    result.deliveredContent should include("[REDACTED]")
    result.deliveredContent should not include "123-45-6789"
    result.originalBlocked shouldBe true
  }

  it should "use custom redaction marker" in {
    val ssnPattern = "\\d{3}-\\d{2}-\\d{4}".r
    val customMarker = "XXX"
    val handler = RedactionFallback(
      redactionMarker = customMarker,
      patterns = Seq(ssnPattern),
      reEvaluate = artifact => OutputEvaluator.evaluate(artifact, OutputConstraintSet(), now)
    )
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    result.deliveredContent should include(customMarker)
    result.deliveredContent should not include "[REDACTED]"
  }

  it should "fall back to safe response when re-evaluation still fails" in {
    val alwaysFailChecker = AlwaysDetectsChecker(PropertyId.unsafe("always_fails"))
    val alwaysFailConstraint = DeterministicConstraint(PropertyId.unsafe("always_fails"), alwaysFailChecker, RiskTier.Critical)
    val reEvalConstraints = OutputConstraintSet(deterministic = Seq(alwaysFailConstraint))

    val ssnPattern = "\\d{3}-\\d{2}-\\d{4}".r
    val handler = RedactionFallback(
      redactionMarker = "[REDACTED]",
      patterns = Seq(ssnPattern),
      reEvaluate = artifact => OutputEvaluator.evaluate(artifact, reEvalConstraints, now)
    )
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    result.action shouldBe FallbackAction.SafeResponseSubstituted
    result.deliveredContent should include("unable to provide")
    result.originalBlocked shouldBe true
  }

  "EscalationFallback" should "invoke the escalation callback" in {
    var escalated: Option[BlockedOutput] = None
    val handler = EscalationFallback(
      onEscalate = blocked => escalated = Some(blocked),
      holdingMessage = "Your request is being reviewed. You will receive a response shortly."
    )
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    escalated shouldBe defined
    escalated.get shouldBe blocked
  }

  it should "return holding message with action Escalated" in {
    var escalated: Option[BlockedOutput] = None
    val handler = EscalationFallback(
      onEscalate = blocked => escalated = Some(blocked)
    )
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    result.action shouldBe FallbackAction.Escalated
    result.deliveredContent should include("being reviewed")
    result.originalBlocked shouldBe true
  }

  it should "use custom holding message" in {
    var escalated: Option[BlockedOutput] = None
    val customMessage = "Custom holding message"
    val handler = EscalationFallback(
      onEscalate = blocked => escalated = Some(blocked),
      holdingMessage = customMessage
    )
    val blocked = mkBlockedOutput()
    val result = handler.handle(blocked)

    result.deliveredContent shouldBe customMessage
  }

  "FallbackAction enum" should "have 3 values" in {
    FallbackAction.values.size shouldBe 3
  }

  "BlockedOutput" should "carry original artifact and non-empty reasons" in {
    val blocked = mkBlockedOutput()

    blocked.originalArtifact.content should include("SSN")
    blocked.blockedReasons should not be empty
    blocked.report.decision should not be PipelineDecision.Pass
  }
