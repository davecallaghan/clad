package clad.integrity

enum FailurePosture:
  case FailClosed
  case FailOpenFlagged
  case FailOpen

enum FailureAction:
  case Blocked
  case ProceededFlagged
  case ProceededSilent

object FailurePosture:
  def actionFor(posture: FailurePosture): FailureAction = posture match
    case FailurePosture.FailClosed      => FailureAction.Blocked
    case FailurePosture.FailOpenFlagged => FailureAction.ProceededFlagged
    case FailurePosture.FailOpen        => FailureAction.ProceededSilent

opaque type ComponentId = String
object ComponentId:
  def apply(value: String): ComponentId = value
  extension (c: ComponentId) def value: String = c
  val Epg: ComponentId = ComponentId("epg")
  val Roc: ComponentId = ComponentId("roc")
  val Supervisor: ComponentId = ComponentId("supervisor")
