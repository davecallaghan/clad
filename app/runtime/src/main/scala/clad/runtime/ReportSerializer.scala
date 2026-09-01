package clad.runtime

import clad.core.*
import clad.evaluation.*
import upickle.default.*
import java.time.Instant

object ReportSerializer:
  sealed trait SerializationError
  case class ParseError(message: String) extends SerializationError
  case class ValidationError(message: String) extends SerializationError

  // PropertyId: serialize as String
  private given ReadWriter[PropertyId] = readwriter[ujson.Value].bimap[PropertyId](
    pid => ujson.Str(pid.value),
    json => PropertyId.unsafe(json.str)
  )

  // Level: serialize as String
  private given ReadWriter[Level] = readwriter[ujson.Value].bimap[Level](
    level => ujson.Str(level.toString),
    json => Level.valueOf(json.str)
  )

  // EvaluabilityClass: serialize as String
  private given ReadWriter[EvaluabilityClass] = readwriter[ujson.Value].bimap[EvaluabilityClass](
    ec => ujson.Str(ec.toString),
    json => EvaluabilityClass.valueOf(json.str)
  )

  // Instant: serialize as ISO-8601 String
  private given ReadWriter[Instant] = readwriter[ujson.Value].bimap[Instant](
    instant => ujson.Str(instant.toString),
    json => Instant.parse(json.str)
  )

  // Constraint: serialize with type discriminator
  private given ReadWriter[Constraint] = readwriter[ujson.Value].bimap[Constraint](
    constraint => constraint match
      case Constraint.Obligation(property, level) =>
        ujson.Obj(
          "type" -> ujson.Str("Obligation"),
          "property" -> writeJs(property),
          "level" -> writeJs(level)
        )
      case Constraint.Prohibition(property, level) =>
        ujson.Obj(
          "type" -> ujson.Str("Prohibition"),
          "property" -> writeJs(property),
          "level" -> writeJs(level)
        )
    ,
    json =>
      val obj = json.obj
      val constraintType = obj("type").str
      val property = read[PropertyId](obj("property"))
      val level = read[Level](obj("level"))
      constraintType match
        case "Obligation" => Constraint.Obligation(property, level)
        case "Prohibition" => Constraint.Prohibition(property, level)
        case other => throw new IllegalArgumentException(s"Unknown constraint type: $other")
  )

  // EvalDetail: serialize with type discriminator
  private given ReadWriter[EvalDetail] = readwriter[ujson.Value].bimap[EvalDetail](
    detail => detail match
      case MechanicalDetail(propertyDetected) =>
        ujson.Obj(
          "type" -> ujson.Str("Mechanical"),
          "propertyDetected" -> ujson.Bool(propertyDetected)
        )
      case ProceduralDetail(attestor, attestedAt, rationale) =>
        ujson.Obj(
          "type" -> ujson.Str("Procedural"),
          "attestor" -> ujson.Str(attestor),
          "attestedAt" -> writeJs(attestedAt),
          "rationale" -> ujson.Str(rationale)
        )
    ,
    json =>
      val obj = json.obj
      val detailType = obj("type").str
      detailType match
        case "Mechanical" =>
          MechanicalDetail(obj("propertyDetected").bool)
        case "Procedural" =>
          ProceduralDetail(
            obj("attestor").str,
            read[Instant](obj("attestedAt")),
            obj("rationale").str
          )
        case other => throw new IllegalArgumentException(s"Unknown detail type: $other")
  )

  // ConstraintResult
  private given ReadWriter[ConstraintResult] = readwriter[ujson.Value].bimap[ConstraintResult](
    cr => ujson.Obj(
      "constraint" -> writeJs(cr.constraint),
      "version" -> ujson.Str(cr.version),
      "satisfied" -> ujson.Bool(cr.satisfied),
      "detail" -> writeJs(cr.detail)
    ),
    json =>
      val obj = json.obj
      ConstraintResult(
        read[Constraint](obj("constraint")),
        obj("version").str,
        obj("satisfied").bool,
        read[EvalDetail](obj("detail"))
      )
  )

  // PromptArtifact
  private given ReadWriter[PromptArtifact] = readwriter[ujson.Value].bimap[PromptArtifact](
    pa => ujson.Obj(
      "content" -> ujson.Str(pa.content),
      "metadata" -> writeJs(pa.metadata)
    ),
    json =>
      val obj = json.obj
      PromptArtifact(
        obj("content").str,
        read[Map[String, String]](obj("metadata"))
      )
  )

  // PromptEvaluationReport
  private given ReadWriter[PromptEvaluationReport] = readwriter[ujson.Value].bimap[PromptEvaluationReport](
    per => ujson.Obj(
      "level" -> writeJs(per.level),
      "results" -> writeJs(per.results.toSeq),
      "mechanicalResults" -> writeJs(per.mechanicalResults.toSeq),
      "proceduralResults" -> writeJs(per.proceduralResults.toSeq),
      "allSatisfied" -> ujson.Bool(per.allSatisfied),
      "timestamp" -> writeJs(per.timestamp),
      "satisfiedCount" -> ujson.Num(per.satisfiedCount.toDouble),
      "totalCount" -> ujson.Num(per.totalCount.toDouble)
    ),
    json =>
      val obj = json.obj
      PromptEvaluationReport(
        read[Level](obj("level")),
        read[Seq[ConstraintResult]](obj("results")).toSet,
        read[Seq[ConstraintResult]](obj("mechanicalResults")).toSet,
        read[Seq[ConstraintResult]](obj("proceduralResults")).toSet,
        obj("allSatisfied").bool,
        read[Instant](obj("timestamp"))
      )
  )

  // AuditEntry
  private given ReadWriter[AuditEntry] = readwriter[ujson.Value].bimap[AuditEntry](
    ae => ujson.Obj(
      "constraint" -> writeJs(ae.constraint),
      "constraintVersion" -> ujson.Str(ae.constraintVersion),
      "evaluabilityClass" -> writeJs(ae.evaluabilityClass),
      "satisfied" -> ujson.Bool(ae.satisfied),
      "detail" -> writeJs(ae.detail),
      "timestamp" -> writeJs(ae.timestamp)
    ),
    json =>
      val obj = json.obj
      AuditEntry(
        read[Constraint](obj("constraint")),
        obj("constraintVersion").str,
        read[EvaluabilityClass](obj("evaluabilityClass")),
        obj("satisfied").bool,
        read[EvalDetail](obj("detail")),
        read[Instant](obj("timestamp"))
      )
  )

  // AuditRecord
  private given ReadWriter[AuditRecord] = readwriter[ujson.Value].bimap[AuditRecord](
    ar => ujson.Obj(
      "artifactDigest" -> ujson.Str(ar.artifactDigest),
      "entries" -> writeJs(ar.entries),
      "configDigest" -> ujson.Str(ar.configDigest),
      "timestamp" -> writeJs(ar.timestamp),
      "previousDigest" -> (ar.previousDigest match
        case Some(digest) => ujson.Str(digest)
        case None => ujson.Null
      ),
      "digest" -> ujson.Str(ar.digest),
      "entryCount" -> ujson.Num(ar.entryCount.toDouble),
      "satisfiedCount" -> ujson.Num(ar.satisfiedCount.toDouble)
    ),
    json =>
      val obj = json.obj
      AuditRecord(
        obj("artifactDigest").str,
        read[Vector[AuditEntry]](obj("entries")),
        obj("configDigest").str,
        read[Instant](obj("timestamp")),
        obj("previousDigest") match
          case ujson.Null => None
          case ujson.Str(s) => Some(s)
          case _ => None
      )
  )

  // GovernanceReport
  private given ReadWriter[GovernanceReport] = readwriter[ujson.Value].bimap[GovernanceReport](
    gr => ujson.Obj(
      "evaluation" -> writeJs(gr.evaluation),
      "artifact" -> writeJs(gr.artifact),
      "audit" -> writeJs(gr.audit),
      "engineVersion" -> ujson.Str(gr.engineVersion),
      "configDigest" -> ujson.Str(gr.configDigest)
    ),
    json =>
      val obj = json.obj
      GovernanceReport(
        read[PromptEvaluationReport](obj("evaluation")),
        read[PromptArtifact](obj("artifact")),
        read[AuditRecord](obj("audit")),
        obj("engineVersion").str,
        obj("configDigest").str
      )
  )

  // Public API
  def toJson(report: GovernanceReport): String =
    write(report)

  def fromJson(json: String): Either[SerializationError, GovernanceReport] =
    try
      Right(read[GovernanceReport](json))
    catch
      case e: ujson.ParseException =>
        Left(ParseError(s"JSON parsing failed: ${e.getMessage}"))
      case e: upickle.core.AbortException =>
        Left(ParseError(s"JSON deserialization failed: ${e.getMessage}"))
      case e: IllegalArgumentException =>
        Left(ValidationError(e.getMessage))
      case e: Exception =>
        Left(ParseError(s"Unexpected error: ${e.getMessage}"))

  def toPrettyJson(report: GovernanceReport): String =
    write(report, indent = 2)
