package clad.conformance.checks

import clad.conformance.*

object PrivacyChecks:
  private val Files = Vector("audit.jsonl", "gil.jsonl", "infra.jsonl", "degraded.jsonl", "mdr_alerts.jsonl")
  // Values that are long digit runs by construction: removed before scanning so they are not
  // reported as account numbers.
  private val Benign = Vector(
    "sha256:[0-9a-f]+".r,
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".r,
    "\"value\":\"[A-Za-z0-9+/=]+\"".r,
    "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z".r)
  private val Pii = Vector(
    "ssn" -> "\\b\\d{3}-\\d{2}-\\d{4}\\b".r,
    "ein" -> "\\b\\d{2}-\\d{7}\\b".r,
    "email" -> "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}".r,
    "phone" -> "\\b\\d{3}[-.]\\d{3}[-.]\\d{4}\\b".r,
    "account" -> "\\b\\d{12}\\d*\\b".r)
  private val RecordKeys = Set("artifactDigest", "entries", "configDigest", "timestamp", "previousDigest",
    "interactionId", "component", "surface", "outcome")
  private val LineKeys = Set("record", "signature", "recordedDigest")

  def all(b: Bundle): Vector[RequirementResult] =
    val found = Files.flatMap { f =>
      b.rawLines.getOrElse(f, Vector.empty).zipWithIndex.flatMap { (line, i) =>
        val cleaned = Benign.foldLeft(line)((s, r) => r.replaceAllIn(s, ""))
        Pii.collect { case (kind, r) if r.findFirstIn(cleaned).isDefined => s"$f:${i + 1}: $kind" }
      }
    }
    val extraKeys = b.rawLines.getOrElse("audit.jsonl", Vector.empty).zipWithIndex.flatMap { (line, i) =>
      ujson.read(line) match
        case o: ujson.Obj =>
          val outer = o.value.keySet.toSet -- LineKeys
          val inner = o.value.get("record").collect { case r: ujson.Obj => r.value.keySet.toSet -- RecordKeys }.getOrElse(Set.empty)
          Option.when((outer ++ inner).nonEmpty)(s"audit.jsonl:${i + 1}: unexpected fields ${(outer ++ inner).mkString(",")}")
        case _ => Some(s"audit.jsonl:${i + 1}: not an object")
    }
    val problems = found ++ extraKeys
    Vector(
      if problems.isEmpty then RequirementResult("R-AI6", "PII minimization", "audit", Verdict.Satisfied, Vector(s"scanned ${Files.mkString(", ")}"))
      else RequirementResult("R-AI6", "PII minimization", "audit", Verdict.Violated, problems, s"${problems.size} findings"))
