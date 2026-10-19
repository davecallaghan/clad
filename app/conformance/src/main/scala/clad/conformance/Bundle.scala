package clad.conformance

import clad.audit.{AuditRecordCodec, SignedAuditRecord}
import clad.integrity.*
import java.time.Instant

case class Window(start: Instant, end: Instant):
  def contains(t: Instant): Boolean = !t.isBefore(start) && !t.isAfter(end)
case class ProjectSpec(tier: String, expectedComponents: Vector[String])
case class BundleMeta(bundleVersion: Int, app: String, window: Window, probeWindow: Option[Window],
  projects: Map[String, ProjectSpec], keys: Map[String, String])
case class InfraEntry(ts: Instant, host: String, method: String, path: String, status: Int, interactionId: Option[String])
case class Anchor(ts: Instant, chainLength: Int, headDigest: String)
case class MdrAlert(alertId: String, raisedAt: Instant, severity: String, taxonomy: String, detector: String,
  interactionIds: Vector[String], summary: String)
case class Heartbeat(ts: Instant, detector: String)
/** `name` is the digest the file name claims; `computedDigest` is what its bytes hash to. */
case class ConfigFile(name: String, computedDigest: String, text: String)
/** `computedDigest` is `PromptArtifact.digest`: content and metadata together. */
case class ArtifactFile(name: String, computedDigest: String, content: String, metadata: Map[String, String])

/** An evidence bundle (spec section 4). `None` means the file was absent (no evidence);
  * `Some(Vector.empty)` means the file was present and empty (evidence of zero items). */
case class Bundle(meta: BundleMeta, configs: Map[String, ConfigFile], artifacts: Map[String, ArtifactFile],
  gil: Option[Vector[GilEntry]], infra: Option[Vector[InfraEntry]], audit: Option[Vector[SignedAuditRecord]],
  degraded: Option[Vector[SignedDegradedRecord]], anchors: Option[Vector[Anchor]], alerts: Option[Vector[MdrAlert]],
  heartbeats: Option[Vector[Heartbeat]], publicKeys: Map[String, String], rawLines: Map[String, Vector[String]]):
  def inWorkload(t: Instant): Boolean = meta.window.contains(t) && !meta.probeWindow.exists(_.contains(t))
  def projectOf(id: String): Option[String] = gil.flatMap(_.find(_.interactionId.value == id)).flatMap(_.metadata.get("project"))
  def tierOf(id: String): Option[String] = projectOf(id).flatMap(meta.projects.get).map(_.tier)

object BundleReader:
  import java.nio.file.{Files, Path}
  import scala.jdk.CollectionConverters.*

  private def sha(b: Array[Byte]): String =
    "sha256:" + java.security.MessageDigest.getInstance("SHA-256").digest(b).map("%02x".format(_)).mkString

  private def lines(dir: Path, name: String): Option[Vector[String]] =
    val p = dir.resolve(name)
    if Files.exists(p) then Some(Files.readAllLines(p).asScala.toVector.filter(_.trim.nonEmpty)) else None

  private def each[A](name: String, ls: Option[Vector[String]])(f: String => A): Either[String, Option[Vector[A]]] =
    ls match
      case None => Right(None)
      case Some(v) =>
        v.zipWithIndex.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { case (acc, (line, i)) =>
          acc.flatMap { xs =>
            try Right(xs :+ f(line))
            catch case e: Exception => Left(s"$name:${i + 1}: ${e.getClass.getSimpleName}: ${e.getMessage}")
          }
        }.map(Some(_))

  private def optStr(j: ujson.Value, k: String): Option[String] =
    j.obj.get(k).flatMap { case ujson.Null => None; case v => Some(v.str) }

  private def window(j: ujson.Value): Window =
    Window(java.time.Instant.parse(j("start").str), java.time.Instant.parse(j("end").str))

  private def strMap(j: ujson.Value): Map[String, String] =
    j.obj.map { case (k, v) => k -> (v match { case ujson.Str(s) => s; case other => other.render() }) }.toMap

  def read(dir: Path): Either[String, Bundle] =
    val metaPath = dir.resolve("bundle.json")
    if !Files.exists(metaPath) then return Left(s"bundle.json not found in $dir")
    try
      val m = ujson.read(Files.readString(metaPath))
      val meta = BundleMeta(m("bundleVersion").num.toInt, m("app").str, window(m("window")),
        m.obj.get("probeWindow").collect { case w: ujson.Obj => window(w) },
        m("projects").obj.map { case (k, v) => k -> ProjectSpec(v("tier").str, v("expectedComponents").arr.map(_.str).toVector) }.toMap,
        strMap(m("keys")))
      def dirFiles(sub: String, suffix: String): Vector[Path] =
        val d = dir.resolve(sub)
        if Files.isDirectory(d) then Files.list(d).iterator().asScala.filter(_.toString.endsWith(suffix)).toVector else Vector.empty
      val configs = dirFiles("configs", ".json").map { p =>
        val bytes = Files.readAllBytes(p)
        val name = p.getFileName.toString.stripSuffix(".json")
        name -> ConfigFile(name, sha(bytes), String(bytes, "UTF-8"))
      }.toMap
      val artifacts = dirFiles("artifacts", ".json").map { p =>
        val j = ujson.read(Files.readString(p))
        val name = p.getFileName.toString.stripSuffix(".json")
        val (content, metadata) = (j("content").str, strMap(j("metadata")))
        name -> ArtifactFile(name, clad.evaluation.PromptArtifact(content, metadata).digest, content, metadata)
      }.toMap
      val keys = dirFiles("keys", ".pub.pem").map(p => p.getFileName.toString.stripSuffix(".pub.pem") -> Files.readString(p)).toMap
      val names = Vector("gil.jsonl", "infra.jsonl", "audit.jsonl", "degraded.jsonl", "anchors.jsonl", "mdr_alerts.jsonl", "mdr_heartbeats.jsonl")
      val raw = names.flatMap(n => lines(dir, n).map(n -> _)).toMap
      for
        gil <- each("gil.jsonl", raw.get("gil.jsonl")) { l =>
          val j = ujson.read(l)
          GilEntry(InteractionId(j("interactionId").str), java.time.Instant.parse(j("registeredAt").str), strMap(j("metadata")))
        }
        infra <- each("infra.jsonl", raw.get("infra.jsonl")) { l =>
          val j = ujson.read(l)
          InfraEntry(java.time.Instant.parse(j("ts").str), j("host").str, j("method").str, j("path").str, j("status").num.toInt, optStr(j, "interactionId"))
        }
        audit <- each("audit.jsonl", raw.get("audit.jsonl"))(l => AuditRecordCodec.decode(l).fold(e => throw IllegalArgumentException(e), identity))
        degraded <- each("degraded.jsonl", raw.get("degraded.jsonl"))(l => DegradedRecordCodec.decode(l).fold(e => throw IllegalArgumentException(e), identity))
        anchors <- each("anchors.jsonl", raw.get("anchors.jsonl")) { l =>
          val j = ujson.read(l); Anchor(java.time.Instant.parse(j("ts").str), j("chainLength").num.toInt, j("headDigest").str)
        }
        alerts <- each("mdr_alerts.jsonl", raw.get("mdr_alerts.jsonl")) { l =>
          val j = ujson.read(l)
          MdrAlert(j("alertId").str, java.time.Instant.parse(j("raisedAt").str), j("severity").str, j("taxonomy").str,
            j("detector").str, j("interactionIds").arr.map(_.str).toVector, j("summary").str)
        }
        beats <- each("mdr_heartbeats.jsonl", raw.get("mdr_heartbeats.jsonl")) { l =>
          val j = ujson.read(l); Heartbeat(java.time.Instant.parse(j("ts").str), j("detector").str)
        }
      yield Bundle(meta, configs, artifacts, gil, infra, audit, degraded, anchors, alerts, beats, keys, raw)
    catch case e: Exception => Left(s"bundle.json or a bundle directory is malformed: ${e.getClass.getSimpleName}: ${e.getMessage}")
