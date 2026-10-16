package clad.gateway.pipeline

final case class GovernedRequest(templateId: String, principal: String, session: Option[String], messages: Vector[ujson.Obj],
                                 vars: Map[String, String], evidence: Option[ujson.Value], modelTier: Option[String],
                                 options: ujson.Obj)

enum GatewayResult:
  case Delivered(interactionId: String, outcome: String, response: ujson.Value)
  case Blocked(interactionId: String, reason: String)
  case Rejected(reason: String)
  case Unavailable(interactionId: Option[String], reason: String)

trait Upstream:
  /** Left: unavailable. */
  def complete(body: ujson.Obj, interactionId: String): Either[String, ujson.Value]

trait PassthroughUpstream:
  def forward(name: String, path: String, body: String, interactionId: String): Either[String, (Int, String)]
