package clad.conformance

import clad.conformance.checks.*
import clad.conformance.probes.{GhostProbe, WormProbe}
import java.nio.file.Path

object Conformance:
  def evaluate(b: Bundle, probe: Option[ProbeConfig], probeResults: Option[Path]): Vector[RequirementResult] =
    val worm = probe.flatMap(_.worm) match
      case Some(w) => WormProbe.run(w)
      case None => RequirementResult("R-AI1", "WORM storage", "audit", Verdict.NotRun, reason = "WORM probe not configured")
    ChainChecks.all(b) ++ Vector(worm) ++ PrivacyChecks.all(b) ++ LinkageChecks.all(b) ++ ConfigChecks.all(b) ++
      EpgChecks.all(b) ++ RocChecks.all(b) ++ MdrChecks.all(b, probe.flatMap(_.mdr)) ++
      Vector(GhostProbe.score(b, probeResults, probe)) ++ NotImplemented.all
