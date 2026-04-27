package clad.audit

import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import upickle.default.*
import java.time.Instant
import java.util.Base64

object AuditRecordCodec:

  private given ReadWriter[Instant] = readwriter[String].bimap(_.toString, Instant.parse(_))
  private given ReadWriter[PropertyId] = readwriter[String].bimap(_.value, PropertyId.unsafe(_))
  private given ReadWriter[Level] = readwriter[String].bimap(_.toString, Level.valueOf(_))
  private given ReadWriter[EvaluabilityClass] = readwriter[String].bimap(_.toString, EvaluabilityClass.valueOf(_))

  private given ReadWriter[Constraint] = readwriter[ujson.Value].bimap(
    { case Constraint.Obligation(prop, level) => ujson.Obj("type" -> "Obligation", "property" -> writeJs(prop), "level" -> writeJs(level))
      case Constraint.Prohibition(prop, level) => ujson.Obj("type" -> "Prohibition", "property" -> writeJs(prop), "level" -> writeJs(level)) },
    json => json("type").str match
      case "Obligation" => Constraint.Obligation(read[PropertyId](json("property")), read[Level](json("level")))
      case "Prohibition" => Constraint.Prohibition(read[PropertyId](json("property")), read[Level](json("level")))
  )

  private given ReadWriter[EvalDetail] = readwriter[ujson.Value].bimap(
    { case MechanicalDetail(detected) => ujson.Obj("type" -> "Mechanical", "propertyDetected" -> detected)
      case ProceduralDetail(attestor, at, rationale) => ujson.Obj("type" -> "Procedural", "attestor" -> attestor, "attestedAt" -> writeJs(at), "rationale" -> rationale) },
    json => json("type").str match
      case "Mechanical" => MechanicalDetail(json("propertyDetected").bool)
      case "Procedural" => ProceduralDetail(json("attestor").str, read[Instant](json("attestedAt")), json("rationale").str)
  )

  private given ReadWriter[AuditEntry] = readwriter[ujson.Value].bimap(
    ae => ujson.Obj("constraint" -> writeJs(ae.constraint), "constraintVersion" -> ae.constraintVersion,
      "evaluabilityClass" -> writeJs(ae.evaluabilityClass), "satisfied" -> ae.satisfied,
      "detail" -> writeJs(ae.detail), "timestamp" -> writeJs(ae.timestamp)),
    json => AuditEntry(read[Constraint](json("constraint")), json("constraintVersion").str,
      read[EvaluabilityClass](json("evaluabilityClass")), json("satisfied").bool,
      read[EvalDetail](json("detail")), read[Instant](json("timestamp")))
  )

  private given ReadWriter[AuditRecord] = readwriter[ujson.Value].bimap(
    ar => ujson.Obj("artifactDigest" -> ar.artifactDigest, "entries" -> writeJs(ar.entries),
      "configDigest" -> ar.configDigest, "timestamp" -> writeJs(ar.timestamp),
      "previousDigest" -> (ar.previousDigest match { case Some(d) => ujson.Str(d); case None => ujson.Null })),
    json => AuditRecord(json("artifactDigest").str, read[Vector[AuditEntry]](json("entries")),
      json("configDigest").str, read[Instant](json("timestamp")),
      json("previousDigest") match { case ujson.Null => None; case v => Some(v.str) })
  )

  private given ReadWriter[Signature] = readwriter[ujson.Value].bimap(
    sig => ujson.Obj("value" -> Base64.getEncoder.encodeToString(sig.value), "keyId" -> sig.keyId, "algorithm" -> sig.algorithm),
    json => Signature(Base64.getDecoder.decode(json("value").str), json("keyId").str, json("algorithm").str)
  )

  private given ReadWriter[SignedAuditRecord] = readwriter[ujson.Value].bimap(
    sar => ujson.Obj("record" -> writeJs(sar.record), "signature" -> writeJs(sar.signature)),
    json => SignedAuditRecord(read[AuditRecord](json("record")), read[Signature](json("signature")))
  )

  def encode(record: SignedAuditRecord): String = write(record)
  def decode(line: String): Either[String, SignedAuditRecord] =
    try Right(read[SignedAuditRecord](line))
    catch case e: Exception => Left(e.getMessage)
