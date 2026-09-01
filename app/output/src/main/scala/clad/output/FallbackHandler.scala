package clad.output

case class BlockedOutput(
  originalArtifact: OutputArtifact,
  report: OutputEvaluationReport,
  blockedReasons: Vector[OutputEvalResult]
)

enum FallbackAction:
  case SafeResponseSubstituted
  case Redacted
  case Escalated

case class FallbackResult(
  action: FallbackAction,
  deliveredContent: String,
  originalBlocked: Boolean
)

trait FallbackHandler:
  def handle(blocked: BlockedOutput): FallbackResult

class SafeResponseFallback(
  defaultResponse: String = "I'm unable to provide that information. Please contact a qualified professional."
) extends FallbackHandler:
  def handle(blocked: BlockedOutput): FallbackResult =
    FallbackResult(FallbackAction.SafeResponseSubstituted, defaultResponse, originalBlocked = true)

class RedactionFallback(
  redactionMarker: String = "[REDACTED]",
  patterns: Seq[scala.util.matching.Regex],
  reEvaluate: OutputArtifact => OutputEvaluationReport
) extends FallbackHandler:
  def handle(blocked: BlockedOutput): FallbackResult =
    val redacted = redactContent(blocked.originalArtifact.content)
    val redactedArtifact = OutputArtifact(redacted, blocked.originalArtifact.metadata, blocked.originalArtifact.promptContext)
    val reEvalReport = reEvaluate(redactedArtifact)
    reEvalReport.decision match
      case PipelineDecision.Pass =>
        FallbackResult(FallbackAction.Redacted, redacted, originalBlocked = true)
      case _ =>
        FallbackResult(
          FallbackAction.SafeResponseSubstituted,
          "I'm unable to provide that information. Please contact a qualified professional.",
          originalBlocked = true
        )

  private def redactContent(content: String): String =
    patterns.foldLeft(content) { (text, pattern) =>
      pattern.replaceAllIn(text, redactionMarker)
    }

class EscalationFallback(
  onEscalate: BlockedOutput => Unit,
  holdingMessage: String = "Your request is being reviewed. You will receive a response shortly."
) extends FallbackHandler:
  def handle(blocked: BlockedOutput): FallbackResult =
    onEscalate(blocked)
    FallbackResult(FallbackAction.Escalated, holdingMessage, originalBlocked = true)
