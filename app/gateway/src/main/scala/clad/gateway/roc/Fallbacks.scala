package clad.gateway.roc

import java.util.Locale
import java.util.regex.{Matcher, Pattern}
import scala.util.Try

/** The model output the ROC evaluates: the first choice's message, its text content and its tool calls. */
final case class ModelOutput(content: String, toolCalls: Vector[ujson.Obj]):
  /** The evaluated output text: what the digest covers, the classifier reads and the retry similarity compares. */
  def text: String = if toolCalls.isEmpty then content else content + "\n" + ujson.write(ujson.Arr.from(toolCalls))

object ModelOutput:
  def of(reply: ujson.Value): Either[String, ModelOutput] =
    for
      choice <- reply.objOpt.flatMap(_.get("choices")).flatMap(_.arrOpt).flatMap(_.headOption).toRight("roc: the reply has no choice")
      msg <- choice.objOpt.flatMap(_.get("message")).collect { case o: ujson.Obj => o }.toRight("roc: the reply's choice has no message")
      content <- msg.value.get("content") match
        case None | Some(ujson.Null) => Right("")
        case Some(ujson.Str(s))      => Right(s)
        case Some(_)                 => Left("roc: the reply's content is not text")
      calls <- msg.value.get("tool_calls") match
        case None | Some(ujson.Null)                                        => Right(Vector.empty)
        case Some(a: ujson.Arr) if a.value.forall(_.isInstanceOf[ujson.Obj]) => Right(a.value.collect { case o: ujson.Obj => o }.toVector)
        case Some(_)                                                        => Left("roc: the reply's tool_calls is not a list of objects")
    yield ModelOutput(content, calls)

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

  /** A copy of the reply holding only its first (the evaluated) choice, with the content replaced when given and the
    * tool calls removed when asked; a choice that finished on tool calls then finishes on stop. */
  def deliver(reply: ujson.Value, content: Option[String], dropToolCalls: Boolean): Either[String, ujson.Value] =
    ujson.read(ujson.write(reply)) match
      case o: ujson.Obj =>
        o.value.get("choices").flatMap(_.arrOpt).flatMap(_.headOption) match
          case Some(choice: ujson.Obj) =>
            choice.value.get("message") match
              case Some(m: ujson.Obj) =>
                content.foreach(c => m("content") = c)
                if dropToolCalls then
                  m.value.remove("tool_calls")
                  if choice.value.get("finish_reason").contains(ujson.Str("tool_calls")) then choice("finish_reason") = "stop"
                o("choices") = ujson.Arr(choice)
                Right(o)
              case _ => Left("roc: the reply's choice has no message")
          case _ => Left("roc: the reply has no choice")
      case _ => Left("roc: the reply is not an object")
