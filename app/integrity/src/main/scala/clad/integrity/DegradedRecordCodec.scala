package clad.integrity

import clad.audit.Signature
import java.time.Instant
import java.util.Base64

object DegradedRecordCodec:
  def encode(s: SignedDegradedRecord): String =
    val r = s.record
    ujson.Obj(
      "record" -> ujson.Obj(
        "interactionId" -> r.interactionId.value, "timestamp" -> r.timestamp.toString,
        "component" -> r.component.value, "status" -> r.status.toString,
        "failureReason" -> r.failureReason, "postureApplied" -> r.postureApplied.toString,
        "actionTaken" -> r.actionTaken.toString),
      "signature" -> ujson.Obj("value" -> Base64.getEncoder.encodeToString(s.signature.value),
        "keyId" -> s.signature.keyId, "algorithm" -> s.signature.algorithm),
      "signedBy" -> s.signedBy.value, "onBehalfOf" -> s.onBehalfOf.value
    ).render()

  def decode(line: String): Either[String, SignedDegradedRecord] =
    try
      val j = ujson.read(line)
      val r = j("record")
      val sig = j("signature")
      Right(SignedDegradedRecord(
        DegradedAuditRecord(InteractionId(r("interactionId").str), Instant.parse(r("timestamp").str),
          ComponentId(r("component").str), DegradedStatus.valueOf(r("status").str), r("failureReason").str,
          FailurePosture.valueOf(r("postureApplied").str), FailureAction.valueOf(r("actionTaken").str)),
        Signature(Base64.getDecoder.decode(sig("value").str), sig("keyId").str, sig("algorithm").str),
        ComponentId(j("signedBy").str), ComponentId(j("onBehalfOf").str)))
    catch case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
