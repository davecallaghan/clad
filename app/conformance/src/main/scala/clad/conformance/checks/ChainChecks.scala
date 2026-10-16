package clad.conformance.checks

import clad.audit.*
import clad.conformance.*
import clad.integrity.FailureAction

object ChainChecks:
  private val Area = "audit"

  private def kms(b: Bundle, role: String): Either[String, KeyManagementService] =
    for
      keyId <- b.meta.keys.get(role).toRight(s"bundle.json names no $role key")
      pem <- b.publicKeys.get(keyId).toRight(s"keys/$keyId.pub.pem missing")
      k <- Ed25519KeyManagement.fromPublicPem(pem, keyId).left.map(_.toString)
    yield k

  def componentKms(b: Bundle): Either[String, KeyManagementService] = kms(b, "components")
  def supervisorKms(b: Bundle): Either[String, KeyManagementService] = kms(b, "supervisor")

  def all(b: Bundle): Vector[RequirementResult] =
    val ai3 = signatures(b)
    Vector(chain(b), ai3, independent(b, ai3), truncation(b), anchor(b))

  private def chain(b: Bundle): RequirementResult =
    val t = "Hash-chain integrity"
    b.audit match
      case None => RequirementResult("R-AI2", t, Area, Verdict.NotRun, reason = "audit.jsonl absent")
      case Some(rs) if rs.isEmpty => RequirementResult("R-AI2", t, Area, Verdict.NotRun, reason = "no audit records")
      case Some(rs) =>
        val firstBad =
          if rs.headOption.exists(_.record.previousDigest.isDefined) then Some(0)
          else rs.zip(rs.drop(1)).zipWithIndex.collectFirst {
            case ((prev, cur), i) if !cur.record.previousDigest.contains(prev.record.digest) => i + 1
          }
        firstBad match
          case None => RequirementResult("R-AI2", t, Area, Verdict.Satisfied, Vector(s"${rs.size} records linked"))
          case Some(i) => RequirementResult("R-AI2", t, Area, Verdict.Violated, reason = s"chain breaks at record ${i + 1}")

  private def signatures(b: Bundle): RequirementResult =
    val t = "Signed records"
    (b.audit, componentKms(b)) match
      case (None, _) | (Some(Vector()), _) => RequirementResult("R-AI3", t, Area, Verdict.NotRun, reason = "no audit records")
      case (_, Left(err)) => RequirementResult("R-AI3", t, Area, Verdict.NotRun, reason = err)
      case (Some(rs), Right(k)) =>
        val bad = rs.zipWithIndex.collect {
          case (r, i) if r.signature.keyId != k.keyId || !r.isAuthentic(k).contains(true) => i + 1
        }
        if bad.isEmpty then RequirementResult("R-AI3", t, Area, Verdict.Satisfied, Vector(s"${rs.size} signatures verify with ${k.keyId}"))
        else RequirementResult("R-AI3", t, Area, Verdict.Violated, reason = s"${bad.size} signatures fail, first at record ${bad.headOption.getOrElse(0)}")

  private def independent(b: Bundle, ai3: RequirementResult): RequirementResult =
    val t = "Independent verification"
    if ai3.verdict == Verdict.NotRun then RequirementResult("R-AI4", t, Area, Verdict.NotRun, reason = ai3.reason)
    else
      val rs = b.audit.getOrElse(Vector.empty)
      val unverifiable = rs.count(_.recordedDigest.isEmpty)
      val mismatched = rs.count(r => r.recordedDigest.exists(_ != r.record.digest))
      if ai3.verdict == Verdict.Satisfied && unverifiable == 0 && mismatched == 0 then
        RequirementResult("R-AI4", t, Area, Verdict.Satisfied, Vector("verified with public keys only; recorded digests match"))
      else RequirementResult("R-AI4", t, Area, Verdict.Violated,
        reason = s"signatures ${ai3.verdict}; $mismatched recorded digests differ; $unverifiable records unverifiable (no recordedDigest)")

  /** An interaction is complete when, walking its project's expected components in order, each
    * one has a record or a degraded record, stopping at the first that blocked. */
  private def truncation(b: Bundle): RequirementResult =
    val t = "Truncation detection"
    (b.gil, b.audit) match
      case (None, _) | (_, None) => RequirementResult("R-AI5", t, Area, Verdict.NotRun, reason = "needs gil.jsonl and audit.jsonl")
      case (Some(gil), Some(rs)) =>
        val byId = rs.groupBy(_.record.interactionId.map(_.value).getOrElse(""))
        val degradedById = b.degraded.getOrElse(Vector.empty).groupBy(_.record.interactionId.value)
        val incomplete = gil.filter(g => b.inWorkload(g.registeredAt)).flatMap { g =>
          val id = g.interactionId.value
          val recs = byId.getOrElse(id, Vector.empty)
          val degs = degradedById.getOrElse(id, Vector.empty)
          val expected = g.metadata.get("project").flatMap(b.meta.projects.get).map(_.expectedComponents).getOrElse(Vector.empty)
          if recs.exists(r => r.record.component.contains("gateway") && r.record.outcome.exists(_.startsWith("block"))) then None
          else
            val missing = expected.foldLeft[(Boolean, Vector[String])]((false, Vector.empty)) { case ((stopped, miss), comp) =>
              if stopped then (true, miss)
              else recs.find(_.record.component.contains(comp)) match
                case Some(r) => (r.record.outcome.exists(_.startsWith("block")), miss)
                case None => degs.find(_.record.component.value == comp) match
                  case Some(d) => (d.record.actionTaken == FailureAction.Blocked, miss)
                  case None => (false, miss :+ comp)
            }._2
            if missing.isEmpty then None else Some(s"$id missing ${missing.mkString(",")}")
        }
        if incomplete.isEmpty then RequirementResult("R-AI5", t, Area, Verdict.Satisfied, Vector(s"${gil.size} interactions complete"))
        else RequirementResult("R-AI5", t, Area, Verdict.Violated, incomplete, s"${incomplete.size} interactions incomplete")

  private def anchor(b: Bundle): RequirementResult =
    val t = "Chain tail anchored"
    (b.anchors, b.audit) match
      case (None, _) | (Some(Vector()), _) => RequirementResult("R-ANCHOR", t, Area, Verdict.NotRun, reason = "no anchors")
      case (_, None) => RequirementResult("R-ANCHOR", t, Area, Verdict.NotRun, reason = "audit.jsonl absent")
      case (Some(as), Some(rs)) =>
        val last = as.maxBy(_.chainLength)
        val atPos = rs.lift(last.chainLength - 1).map(_.record.digest)
        if atPos.contains(last.headDigest) then RequirementResult("R-ANCHOR", t, Area, Verdict.Satisfied, Vector(s"anchor at length ${last.chainLength} matches"))
        else RequirementResult("R-ANCHOR", t, Area, Verdict.Violated,
          reason = s"anchor says length ${last.chainLength} head ${last.headDigest}; chain has ${rs.size} records")
