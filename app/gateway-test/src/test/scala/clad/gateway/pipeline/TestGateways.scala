package clad.gateway.pipeline

class FakeUpstream(replies: Either[String, ujson.Value]*) extends Upstream:
  val calls = scala.collection.mutable.ArrayBuffer.empty[(ujson.Obj, String)]
  private val queue = scala.collection.mutable.Queue(replies*)
  def complete(body: ujson.Obj, iid: String): Either[String, ujson.Value] =
    calls += ((body, iid)); if queue.isEmpty then Right(ok("ok")) else queue.dequeue()

def ok(content: String): ujson.Value = ujson.Obj("choices" -> ujson.Arr(ujson.Obj(
  "message" -> ujson.Obj("role" -> "assistant", "content" -> content), "finish_reason" -> "stop")))

object NoPassthrough extends PassthroughUpstream:
  def forward(name: String, path: String, body: String, interactionId: String): Either[String, (Int, String)] = Left("unused")

class FakePassthrough extends PassthroughUpstream:
  val calls = scala.collection.mutable.ArrayBuffer.empty[(String, String, String, String)]
  def forward(name: String, path: String, body: String, interactionId: String): Either[String, (Int, String)] =
    calls += ((name, path, body, interactionId)); Right((200, "{}"))

def answerRequest(template: String = "agent.answer", principal: String = "api",
                  evidence: Option[ujson.Value] = None): GovernedRequest =
  GovernedRequest(template, principal, None, Vector(ujson.Obj("role" -> "user", "content" -> "what changed?")),
    Map.empty, evidence, None, ujson.Obj())
