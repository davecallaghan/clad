package clad.mdr.detectors

import clad.mdr.*
import clad.monitoring.ComponentFailure
import clad.monitoring.detectors.ComponentHealthDetector
import java.time.Instant

/** clad's ComponentHealthDetector over the snapshot's ComponentFailure events (degraded records): P2 for a FailClosed
  * failure, P3 otherwise, one alert per interaction. */
final class ComponentHealth extends Detector:
  val id = "component_health"
  private val clad = ComponentHealthDetector()

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    val project = s.gil.map(g => g.interactionId -> g.project).toMap
    Right(Events.from(s).collect { case f: ComponentFailure => f }.flatMap(clad.detect).flatMap { a =>
      a.interactionId.map(_.value).map(i => s"component:$i" -> i).filterNot((k, _) => s.alertedKeys.contains(k)).map { (k, i) =>
        MdrAlert(k, a.severity, a.category, id, Vector(i), s"${a.title}. ${a.detail}", project.get(i))
      }
    }.distinctBy(_.dedupeKey))
