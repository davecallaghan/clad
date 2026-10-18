package clad.gateway.roc

import java.util.Locale
import java.util.regex.{Matcher, Pattern}
import scala.util.Try

/** The model output the ROC evaluates: the first choice's text content and its tool calls. Content given as blocks is
  * the `text` blocks joined by newlines; other blocks (reasoning) are neither evaluated nor delivered. `nullContent`
  * keeps a null content (a tool-call-only reply) null on delivery. */
final case class ModelOutput(content: String, toolCalls: Vector[ujson.Obj], nullContent: Boolean = false):
  /** The evaluated output text: what the digest covers, the classifier reads and the retry similarity compares. */
  def text: String = if toolCalls.isEmpty then content else content + "\n" + ujson.write(ujson.Arr.from(toolCalls))

object ModelOutput:
  private def textBlocks(blocks: ujson.Arr): Either[String, String] =
    blocks.value.toVector.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) { (acc, b) =>
      acc.flatMap { texts =>
        b.objOpt.flatMap(_.get("type")).flatMap(_.strOpt) match
          case Some("text") => b.obj.get("text").flatMap(_.strOpt).map(texts :+ _).toRight("roc: a text block has no text")
          case Some(_)      => Right(texts)
          case None         => Left("roc: a content block has no type")
      }
    }.map(_.mkString("\n"))

  def of(reply: ujson.Value): Either[String, ModelOutput] =
    for
      choice <- reply.objOpt.flatMap(_.get("choices")).flatMap(_.arrOpt).flatMap(_.headOption).toRight("roc: the reply has no choice")
      msg <- choice.objOpt.flatMap(_.get("message")).collect { case o: ujson.Obj => o }.toRight("roc: the reply's choice has no message")
      content <- msg.value.get("content") match
        case None | Some(ujson.Null) => Right(None)
        case Some(ujson.Str(s))      => Right(Some(s))
        case Some(a: ujson.Arr)      => textBlocks(a).map(Some(_))
        case Some(_)                 => Left("roc: the reply's content is neither text nor content blocks")
      calls <- msg.value.get("tool_calls") match
        case None | Some(ujson.Null)                                        => Right(Vector.empty)
        case Some(a: ujson.Arr) if a.value.forall(_.isInstanceOf[ujson.Obj]) => Right(a.value.collect { case o: ujson.Obj => o }.toVector)
        case Some(_)                                                        => Left("roc: the reply's tool_calls is not a list of objects")
    yield ModelOutput(content.getOrElse(""), calls, content.isEmpty)

/** The fallbacks for a blocked output (spec 8.3 stage 4): redaction, the retry request and the governed safe response. */
object Fallbacks:
  val Redacted = "[redacted]"
  private val NonWord = Pattern.compile("""[^\p{L}\p{N}]+""")

  /** Every match of every pattern replaced with [redacted]. */
  def redact(text: String, patterns: Vector[String]): Either[String, String] =
    Try(patterns.map(Pattern.compile(_))).toEither.left.map(e => s"roc: contains_pii pattern: ${e.getMessage}").map(
      _.foldLeft(text)((s, p) => p.matcher(s).replaceAll(Matcher.quoteReplacement(Redacted))))

  private def tokens(s: String): Set[String] = NonWord.split(s.toLowerCase(Locale.ROOT)).iterator.filter(_.nonEmpty).toSet

  /** Token-set Jaccard similarity of two texts: lowercase tokens split on anything not a letter or digit. Two texts with
    * no tokens at all are identical. */
  def similarity(a: String, b: String): Double =
    val (x, y) = (tokens(a), tokens(b))
    val union = (x | y).size
    if union == 0 then 1.0 else (x & y).size.toDouble / union

  /** The same request with the retry variant appended as a user message; the caller's request is not changed. */
  def withVariant(request: ujson.Obj, variant: String): ujson.Obj =
    val messages = request.value.get("messages").flatMap(_.arrOpt).map(_.toVector).getOrElse(Vector.empty)
    val copy = ujson.Obj.from(request.value.toSeq)
    copy("messages") = ujson.Arr.from(messages :+ ujson.Obj("role" -> "user", "content" -> variant))
    copy

  /** Evidence rows as a Markdown table, the columns in the order the rows first name them; None when there are no rows. */
  def table(evidence: ujson.Value): Option[String] =
    val rows = evidence match
      case a: ujson.Arr => a.value.toVector.collect { case o: ujson.Obj => o }
      case o: ujson.Obj => Vector(o)
      case _            => Vector.empty
    val columns = rows.flatMap(_.value.keys).distinct
    def cell(v: Option[ujson.Value]): String = (v match
      case None | Some(ujson.Null) => ""
      case Some(ujson.Str(s))      => s
      case Some(x)                 => x.render()
    ).replace("|", "\\|").replace("\r", " ").replace("\n", " ")
    def line(cells: Vector[String]): String = cells.mkString("| ", " | ", " |")
    Option.when(rows.nonEmpty && columns.nonEmpty)(
      (Vector(line(columns.map(c => cell(Some(ujson.Str(c))))), line(columns.map(_ => "---"))) ++
        rows.map(r => line(columns.map(c => cell(r.value.get(c)))))).mkString("\n"))

  /** The reply fields kept around the delivered choice; anything else the upstream sent is not delivered. */
  private val ReplyFields = Vector("id", "object", "created", "model", "system_fingerprint", "usage")

  private def build(reply: ujson.Value, choice: ujson.Obj => ujson.Obj): Either[String, ujson.Value] =
    reply match
      case o: ujson.Obj =>
        o.value.get("choices").flatMap(_.arrOpt).flatMap(_.headOption) match
          case Some(c: ujson.Obj) =>
            val out = ujson.Obj.from(ReplyFields.flatMap(k => o.value.get(k).map(v => k -> ujson.read(ujson.write(v)))))
            out("choices") = ujson.Arr(choice(c))
            Right(out)
          case _ => Left("roc: the reply has no choice")
      case _ => Left("roc: the reply is not an object")

  private def indexOf(c: ujson.Obj): ujson.Value = c.value.getOrElse("index", ujson.Num(0))

  /** The reply holding only the evaluated choice, built from what was evaluated: role, the content as plain text and
    * the tool calls. Reasoning, refusal, audio, function_call and logprobs were never evaluated, so they are not
    * delivered. */
  def deliver(reply: ujson.Value, out: ModelOutput): Either[String, ujson.Value] =
    build(reply, c => {
      val msg = ujson.Obj("role" -> "assistant", "content" -> (if out.nullContent then ujson.Null else ujson.Str(out.content)))
      if out.toolCalls.nonEmpty then msg("tool_calls") = ujson.Arr.from(out.toolCalls.map(t => ujson.read(ujson.write(t))))
      ujson.Obj("index" -> indexOf(c), "message" -> msg, "finish_reason" -> c.value.getOrElse("finish_reason", ujson.Null))
    })

  /** A fresh choice holding only the safe text: nothing of the blocked message is carried over. */
  def safe(reply: ujson.Value, text: String): Either[String, ujson.Value] =
    build(reply, c => ujson.Obj("index" -> indexOf(c), "message" -> ujson.Obj("role" -> "assistant", "content" -> text),
      "finish_reason" -> "stop"))
