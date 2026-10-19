package clad.gateway.pipeline

class FakeUpstream(replies: Either[String, ujson.Value]*) extends Upstream:
  val calls = scala.collection.mutable.ArrayBuffer.empty[(ujson.Obj, String)]
  /** Snapshot taken inside complete: what the probe saw at call time (tests set it to read the GIL). */
  var probe: () => Seq[String] = () => Seq.empty
  val seenAtCall = scala.collection.mutable.ArrayBuffer.empty[Seq[String]]
  private val queue = scala.collection.mutable.Queue(replies*)
  def complete(body: ujson.Obj, iid: String): Either[String, ujson.Value] =
    calls += ((body, iid)); seenAtCall += probe(); if queue.isEmpty then Right(ok("ok")) else queue.dequeue()

def ok(content: String): ujson.Value = ujson.Obj("choices" -> ujson.Arr(ujson.Obj(
  "message" -> ujson.Obj("role" -> "assistant", "content" -> content), "finish_reason" -> "stop")))

object NoPassthrough extends PassthroughUpstream:
  def forward(name: String, path: String, body: String, interactionId: String): Either[String, (Int, String)] = Left("unused")

/** A Jev reply that route_valid accepts. */
val ValidRoute: String = """{"answers": {"intent": {"choice": "general"}}}"""

class FakePassthrough(reply: String = ValidRoute) extends PassthroughUpstream:
  val calls = scala.collection.mutable.ArrayBuffer.empty[(String, String, String, String)]
  def forward(name: String, path: String, body: String, interactionId: String): Either[String, (Int, String)] =
    calls += ((name, path, body, interactionId)); Right((200, reply))

def answerRequest(template: String = "agent.answer", principal: String = "api",
                  evidence: Option[ujson.Value] = None): GovernedRequest =
  GovernedRequest(template, principal, None, Vector(ujson.Obj("role" -> "user", "content" -> "what changed?")),
    Map.empty, evidence, None, ujson.Obj())

/** A classifier that always returns the same scores (or failure). */
class FixedGuard(result: Either[String, clad.gateway.roc.GuardScores]) extends clad.gateway.roc.Guard:
  val calls = scala.collection.mutable.ArrayBuffer.empty[String]
  def score(conversation: Vector[ujson.Obj], interactionId: String): Either[String, clad.gateway.roc.GuardScores] =
    calls += interactionId; result

/** Every Llama Guard category at `unsafe`, verdict safe. */
def guardScores(unsafe: Double): clad.gateway.roc.GuardScores =
  clad.gateway.roc.GuardScores(unsafe, clad.gateway.roc.LlamaGuard.SafeCategories.map(_ -> unsafe).toMap, "safe")

/** Holds background tasks until the test runs them, so a test can see the state before and after. */
class QueuedExecutor extends java.util.concurrent.Executor:
  private val queue = scala.collection.mutable.Queue.empty[Runnable]
  def execute(r: Runnable): Unit = queue.synchronized(queue.enqueue(r))
  def pending: Int = queue.synchronized(queue.size)
  def runAll(): Unit = Iterator.continually(queue.synchronized(queue.removeHeadOption())).takeWhile(_.isDefined).flatten.foreach(_.run())
