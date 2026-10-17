package clad.gateway.roc

import clad.gateway.pipeline.Upstream

final case class GuardScores(unsafe: Double, categories: Map[String, Double], verdict: String /* safe|unsafe */)

/** The seam later tasks fake: a classifier that scores a conversation. */
trait Guard:
  def score(conversation: Vector[ujson.Obj], interactionId: String): Either[String, GuardScores]

/** Llama Guard through the chat endpoint. Scores come only from the model's own token logprobs (spike 2026-10-17):
  * P(unsafe) = e^lp for an `unsafe` verdict token and 1 - e^lp for a `safe` one. The call goes through the Upstream, so it
  * carries the interaction id and passes egress. */
final class LlamaGuard(upstream: Upstream, profile: ClassifierProfile) extends Guard:
  import LlamaGuard.*

  def score(conversation: Vector[ujson.Obj], interactionId: String): Either[String, GuardScores] =
    val request = ujson.Obj("model" -> profile.model, "messages" -> ujson.Arr.from(conversation),
      "max_tokens" -> profile.verdictTokens, "logprobs" -> true, "temperature" -> 0)
    upstream.complete(request, interactionId).flatMap(parse)

object LlamaGuard:
  val Split = "classifier verdict split across tokens"
  private val Verdicts = Set("safe", "unsafe")
  val NoVerdict = "classifier reply has no verdict logprob"
  private val CategoryCode = "S[0-9]+".r

  private final case class Tok(text: String, logprob: Option[Double])

  private def tokens(reply: ujson.Value): Vector[Tok] =
    val content = for
      choice <- reply.objOpt.flatMap(_.get("choices")).flatMap(_.arrOpt).flatMap(_.headOption)
      lp <- choice.objOpt.flatMap(_.get("logprobs")).flatMap(_.objOpt)
      items <- lp.get("content").flatMap(_.arrOpt)
    yield items.toVector.flatMap { t =>
      t.objOpt.flatMap(_.get("token")).flatMap(_.strOpt).map(text => Tok(text, t.obj.get("logprob").flatMap(_.numOpt)))
    }
    content.getOrElse(Vector.empty)

  private def parse(reply: ujson.Value): Either[String, GuardScores] =
    val toks = tokens(reply)
    val at = toks.indexWhere(_.text.trim.nonEmpty)
    if at < 0 then Left(NoVerdict)
    else
      val word = verdictWord(toks, at)
      if !Verdicts.contains(word) then Left(NoVerdict)
      else if word != toks(at).text.trim then Left(Split)
      else scoreVerdict(toks, at)

  /** The text of the verdict line: from the first non-blank token to the first newline after it. */
  private def verdictWord(toks: Vector[Tok], at: Int): String =
    val rest = toks.drop(at)
    val (head, tail) = rest.tail.span(!_.text.contains('\n'))
    val closing = tail.headOption.map(_.text.takeWhile(_ != '\n')).getOrElse("")
    (rest.head.text +: head.map(_.text) :+ closing).mkString.trim

  private def scoreVerdict(toks: Vector[Tok], at: Int): Either[String, GuardScores] =
    {
      val verdict = toks(at)
      verdict.logprob match
        case None => Left(NoVerdict)
        case Some(lp) if verdict.text.trim == "safe" =>
          val unsafe = 1.0 - math.exp(lp)
          Right(GuardScores(unsafe, SafeCategories.map(_ -> unsafe).toMap, "safe"))
        case Some(lp) =>
          val unsafe = math.exp(lp)
          val coded = toks.drop(at + 1).filter(t => CategoryCode.matches(t.text.trim))
          if coded.isEmpty then Left("classifier reply has no category token")
          else if coded.exists(_.logprob.isEmpty) then Left("classifier reply has no category logprob")
          else Right(GuardScores(unsafe, coded.map(t => t.text.trim -> unsafe * math.exp(t.logprob.get)).toMap, "unsafe"))
    }

  /** When the verdict is safe no category is generated, so each Llama Guard category (S1 to S14) gets the P(unsafe) upper bound. */
  val SafeCategories: Vector[String] = (1 to 14).map(i => s"S$i").toVector
