package clad.gateway.pipeline

import clad.gateway.config.{Governance, YamlJson}
import java.nio.file.Path
import java.util.regex.{Pattern, PatternSyntaxException}

/** What an input detector observed. The input text itself is never stored: only the detector, its version, the
  * category and the score. */
final case class InputSignal(detector: String, version: String, category: String, score: Double)

/** The adopter's input_detectors.yaml (injection patterns, case-insensitive, and the length bound) plus the
  * contains_pii checker patterns from constraints.yaml, all compiled at load so a bad pattern is a load error. */
final case class InputDetectorConfig(version: String, injection: Vector[(String, Pattern)], maxChars: Int,
                                     pii: Vector[Pattern])

object InputDetectorConfig:
  def load(path: Path, gov: Governance): Either[String, InputDetectorConfig] =
    try fromJson(YamlJson.read(path), gov).left.map(e => s"$path: $e")
    catch
      case e: java.io.IOException => Left(s"$path: ${e.getMessage}")
      case e: RuntimeException    => Left(s"$path: ${e.getClass.getSimpleName}: ${e.getMessage}")

  private def compile(id: String, p: String, flags: Int): Either[String, Pattern] =
    try Right(Pattern.compile(p, flags))
    catch case e: PatternSyntaxException => Left(s"$id: ${e.getDescription}")

  def fromJson(v: ujson.Value, gov: Governance): Either[String, InputDetectorConfig] =
    val o = v.objOpt.getOrElse(collection.mutable.LinkedHashMap.empty[String, ujson.Value])
    for
      version <- o.get("version").collect { case ujson.Str(s) => s; case ujson.Num(n) => ujson.Num(n).render() }
        .toRight("version is required")
      rules <- o.get("injection").flatMap(_.arrOpt).toRight("injection must be a list")
      injection <- rules.toVector.foldLeft[Either[String, Vector[(String, Pattern)]]](Right(Vector.empty)) { (acc, r) =>
        acc.flatMap { ps =>
          (r.objOpt.flatMap(_.get("id")).flatMap(_.strOpt), r.objOpt.flatMap(_.get("pattern")).flatMap(_.strOpt)) match
            case (Some(id), Some(p)) => compile(s"injection $id", p, Pattern.CASE_INSENSITIVE).map(c => ps :+ (id -> c))
            case _ => Left("every injection rule needs an id and a pattern")
        }
      }
      length <- o.get("length").flatMap(_.objOpt).toRight("length is required")
      max <- length.get("max_chars").collect { case ujson.Num(n) if n > 0 => n.toInt }.toRight("length.max_chars must be a positive number")
      pii <- gov.checkers.get("contains_pii").toVector.flatMap(_.patterns)
        .foldLeft[Either[String, Vector[Pattern]]](Right(Vector.empty))((acc, p) => acc.flatMap(ps => compile("contains_pii", p, 0).map(ps :+ _)))
    yield InputDetectorConfig(version, injection, max, pii)

/** Scans the untrusted inputs of a call (the user-role messages and the slot values; never the template text). In
  * order: each injection pattern (category `injection`, score 1.0), the contains_pii checker patterns from
  * constraints.yaml (category `pii`, score 1.0), and the length bound (category `length`, score = chars / max_chars). */
final class InputDetectors(cfg: InputDetectorConfig):

  def scan(texts: Vector[String]): Vector[InputSignal] =
    val injection = cfg.injection.collect { case (id, p) if texts.exists(t => p.matcher(t).find()) =>
      InputSignal(id, cfg.version, "injection", 1.0) }
    val found = Option.when(texts.exists(t => cfg.pii.exists(_.matcher(t).find())))(
      InputSignal("contains_pii", cfg.version, "pii", 1.0))
    val chars = texts.map(_.length).sum
    val long = Option.when(chars > cfg.maxChars)(InputSignal("length", cfg.version, "length", chars.toDouble / cfg.maxChars))
    injection ++ found ++ long

object InputDetectors:
  /** The untrusted texts of a request: user-role message content (plain or text blocks) and the slot values. */
  def texts(messages: Vector[ujson.Obj], vars: Map[String, String]): Vector[String] =
    messages.filter(_.value.get("role").contains(ujson.Str("user"))).flatMap(_.value.get("content")).flatMap {
      case ujson.Str(s) => Vector(s)
      case a: ujson.Arr => a.value.toVector.flatMap(b => b.objOpt.filter(_.get("type").contains(ujson.Str("text")))
        .flatMap(_.get("text")).flatMap(_.strOpt))
      case _ => Vector.empty
    } ++ vars.values.toVector
