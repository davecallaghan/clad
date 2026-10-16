package clad.conformance

import java.nio.file.Path
import java.time.Instant

object Scorecard:
  private def count(rs: Vector[RequirementResult], v: Verdict) = rs.count(_.verdict == v)
  private def label(v: Verdict) = v match
    case Verdict.Satisfied => "SATISFIED"
    case Verdict.Violated => "VIOLATED"
    case Verdict.NotRun => "NOT_RUN"
    case Verdict.Inexpressible => "INEXPRESSIBLE"
  private def metric(rs: Vector[RequirementResult], k: String) = rs.flatMap(_.metrics.get(k)).headOption.getOrElse(0.0)

  def exitCode(rs: Vector[RequirementResult]): Int =
    if rs.exists(_.verdict == Verdict.Violated) then 1
    else if rs.exists(r => r.verdict == Verdict.NotRun || r.verdict == Verdict.Inexpressible) then 2
    else 0

  def json(b: Bundle, bundlePath: Path, rs: Vector[RequirementResult]): ujson.Value =
    ujson.Obj(
      "app" -> b.meta.app, "bundle" -> bundlePath.toString,
      "window" -> ujson.Obj("start" -> b.meta.window.start.toString, "end" -> b.meta.window.end.toString),
      "generatedAt" -> Instant.now().toString, "cladVersion" -> sys.env.getOrElse("CLAD_VERSION", "unknown"),
      "summary" -> ujson.Obj("satisfied" -> count(rs, Verdict.Satisfied), "violated" -> count(rs, Verdict.Violated),
        "notRun" -> count(rs, Verdict.NotRun), "inexpressible" -> count(rs, Verdict.Inexpressible), "total" -> rs.size,
        "ghostRate" -> metric(rs, "ghostRate"), "ghostCount" -> metric(rs, "ghostCount"),
        "unlinkableRecords" -> metric(rs, "unlinkableRecords")),
      "requirements" -> ujson.Arr.from(rs.map(r => ujson.Obj("id" -> r.id, "title" -> r.title, "area" -> r.area,
        "verdict" -> label(r.verdict), "evidence" -> ujson.Arr.from(r.evidence.map(ujson.Str(_))), "reason" -> r.reason))))

  def markdown(b: Bundle, rs: Vector[RequirementResult]): String =
    val sb = StringBuilder()
    sb ++= s"# CLAD conformance scorecard: ${b.meta.app}\n\n"
    sb ++= s"Window ${b.meta.window.start} – ${b.meta.window.end}\n\n"
    sb ++= "| Satisfied | Violated | Not run | Inexpressible | Total | Ghost rate |\n|---|---|---|---|---|---|\n"
    sb ++= f"| ${count(rs, Verdict.Satisfied)} | ${count(rs, Verdict.Violated)} | ${count(rs, Verdict.NotRun)} | ${count(rs, Verdict.Inexpressible)} | ${rs.size} | ${metric(rs, "ghostRate")}%.3f |\n\n"
    sb ++= "NOT_RUN and INEXPRESSIBLE are not passes.\n\n"
    rs.groupBy(_.area).toVector.sortBy(_._1).foreach { (area, items) =>
      sb ++= s"## $area\n\n| Id | Requirement | Verdict | Reason |\n|---|---|---|---|\n"
      items.foreach(r => sb ++= s"| ${r.id} | ${r.title} | ${label(r.verdict)} | ${r.reason.replace("|", "\\|")} |\n")
      sb ++= "\n"
    }
    val detailed = rs.filter(r => r.verdict != Verdict.Satisfied && r.evidence.nonEmpty)
    if detailed.nonEmpty then
      sb ++= "## Evidence for findings\n\n"
      detailed.foreach { r =>
        sb ++= s"### ${r.id}\n\n"
        r.evidence.foreach(e => sb ++= s"- $e\n")
        sb ++= "\n"
      }
    sb.result()
