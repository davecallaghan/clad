package clad.conformance.probes

import clad.conformance.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.time.Instant

object GhostProbe:
  private val Title = "Bypass is detected"

  def send(c: GhostConfig, out: Path): Either[String, Unit] =
    try
      val sentAt = Instant.now()
      val req = HttpRequest.newBuilder(URI.create(c.url)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""{"model":"conformance-probe","messages":[{"role":"user","content":"probe"}]}""")).build()
      val res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding())
      Files.createDirectories(out)
      Files.writeString(out.resolve("probe_ghost.json"), ujson.Obj("sentAt" -> sentAt.toString, "status" -> res.statusCode()).render())
      Right(())
    catch case e: Exception => Left(s"ghost probe failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  def score(b: Bundle, results: Option[Path], c: Option[ProbeConfig]): RequirementResult =
    val file = results.map(_.resolve("probe_ghost.json")).filter(Files.exists(_))
    (file, c.flatMap(_.ghost), c.flatMap(_.mdr)) match
      case (None, _, _) => RequirementResult("R-PROBE-GHOST", Title, "probes", Verdict.NotRun, reason = "no probe_ghost.json")
      case (_, None, _) | (_, _, None) => RequirementResult("R-PROBE-GHOST", Title, "probes", Verdict.NotRun, reason = "probe config needs ghost and mdr sections")
      case (Some(f), Some(g), Some(m)) =>
        val sentAt = Instant.parse(ujson.read(Files.readString(f))("sentAt").str)
        val seen = b.infra.getOrElse(Vector.empty).find(e => e.interactionId.isEmpty &&
          !e.ts.isBefore(sentAt.minusSeconds(g.matchToleranceSeconds)) && !e.ts.isAfter(sentAt.plusSeconds(g.matchToleranceSeconds)))
        seen match
          case None => RequirementResult("R-PROBE-GHOST", Title, "probes", Verdict.Violated, reason = s"the probe call at $sentAt is not in infra.jsonl")
          case Some(e) =>
            val alert = b.alerts.getOrElse(Vector.empty).find(a => a.detector == "ghosts" &&
              !a.raisedAt.isBefore(e.ts) && !a.raisedAt.isAfter(e.ts.plusSeconds(m.detectionDelaySeconds)))
            alert match
              case Some(a) => RequirementResult("R-PROBE-GHOST", Title, "probes", Verdict.Satisfied, Vector(s"probe at ${e.ts} alerted by ${a.alertId} at ${a.raisedAt}"))
              case None => RequirementResult("R-PROBE-GHOST", Title, "probes", Verdict.Violated, reason = s"probe at ${e.ts} seen but not alerted within ${m.detectionDelaySeconds}s")
