package clad.mdr

import clad.gateway.config.YamlJson
import clad.monitoring.AlertSeverity
import java.nio.file.{Files, Path}

/** Every number MDR uses, from the adopter's runtime.yaml (`mdr:`, `config_bounds:`, `severity_due_minutes:`).
  * No defaults: a missing key is an error naming it. */
final case class MdrConfig(intervalSeconds: Long, inFlightGraceSeconds: Long, degradedRatioAlert: Double,
                           injectionWindowSeconds: Long, injectionAlertCount: Int, blockRateMultiple: Double,
                           blockRateBaselineHours: Long, reviewCadenceDays: Long,
                           temperatureMax: Double, maxTokensMax: Int,
                           dueMinutes: Map[AlertSeverity, Long], webhookTimeoutSeconds: Long)

object MdrConfig:
  /** The labels runtime.yaml and the bundle use for each severity. */
  val Labels: Map[AlertSeverity, String] = Map(AlertSeverity.P1Critical -> "P1", AlertSeverity.P2High -> "P2",
    AlertSeverity.P3Medium -> "P3", AlertSeverity.P4Low -> "P4")

  private def guard[A](path: Path)(body: => Either[String, A]): Either[String, A] =
    try body
    catch
      case e: java.io.IOException => Left(s"$path: ${e.getMessage}")
      case e: RuntimeException    => Left(s"$path: ${e.getClass.getSimpleName}: ${e.getMessage}")

  def load(runtimeYaml: Path): Either[String, MdrConfig] = guard(runtimeYaml)(fromJson(YamlJson.read(runtimeYaml)))

  def fromJson(v: ujson.Value): Either[String, MdrConfig] =
    def block(k: String): Either[String, ujson.Obj] =
      v.objOpt.flatMap(_.get(k)).collect { case o: ujson.Obj => o }.toRight(s"runtime.yaml: $k is required")
    def num(o: ujson.Obj, b: String, k: String): Either[String, Double] =
      o.value.get(k).collect { case ujson.Num(n) => n }.toRight(s"runtime.yaml: $b.$k is required")
    for
      m <- block("mdr")
      bounds <- block("config_bounds")
      due <- block("severity_due_minutes")
      interval <- num(m, "mdr", "interval_seconds")
      grace <- num(m, "mdr", "in_flight_grace_seconds")
      ratio <- num(m, "mdr", "degraded_ratio_alert")
      injWindow <- num(m, "mdr", "injection_window_seconds")
      injCount <- num(m, "mdr", "injection_alert_count")
      multiple <- num(m, "mdr", "block_rate_multiple")
      baseline <- num(m, "mdr", "block_rate_baseline_hours")
      cadence <- num(m, "mdr", "review_cadence_days")
      hookTimeout <- num(m, "mdr", "webhook_timeout_seconds")
      tMax <- num(bounds, "config_bounds", "temperature_max")
      tokMax <- num(bounds, "config_bounds", "max_tokens_max")
      dues <- Labels.toVector.foldLeft[Either[String, Map[AlertSeverity, Long]]](Right(Map.empty)) { case (acc, (sev, label)) =>
        acc.flatMap(d => num(due, "severity_due_minutes", label).map(n => d + (sev -> n.toLong)))
      }
    yield MdrConfig(interval.toLong, grace.toLong, ratio, injWindow.toLong, injCount.toInt, multiple, baseline.toLong,
      cadence.toLong, tMax, tokMax.toInt, dues, hookTimeout.toLong)

/** Where MDR reads and writes. The gateway data directory (anchors) and the keys directory (public keys only) are
  * read-only; `dataDir` holds the incident lockers. `dbUrl` connects as clad_gov_reader. */
final case class MdrEnv(configDir: Path, gatewayDataDir: Path, dataDir: Path, keysDir: Path, egressLog: Path,
                        listeners: Map[String, String], dbUrl: String, app: String, webhookUrl: Option[String])

object MdrEnv:
  private val Required = Vector("CLAD_MDR_CONFIG_DIR", "CLAD_MDR_GATEWAY_DATA_DIR", "CLAD_MDR_DATA_DIR",
    "CLAD_MDR_KEYS_DIR", "CLAD_MDR_EGRESS_LOG", "CLAD_MDR_LISTENERS", "CLAD_MDR_DB_URL", "CLAD_MDR_APP")

  /** `name=value,...`, as the gateway's CLAD_GATEWAY_LISTENERS. */
  def pairs(spec: String, what: String): Either[String, Map[String, String]] =
    spec.split(",").toVector.map(_.trim).filter(_.nonEmpty).foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
      (acc, e) => acc.flatMap(m => e.split("=", 2) match
        case Array(k, v) if k.trim.nonEmpty && v.trim.nonEmpty => Right(m + (k.trim -> v.trim))
        case _ => Left(s"$what: every entry must be name=value"))
    }

  def from(env: Map[String, String]): Either[String, MdrEnv] =
    def get(k: String) = env.get(k).filter(_.nonEmpty)
    Required.find(get(_).isEmpty) match
      case Some(missing) => Left(s"$missing is not set")
      case None =>
        def p(k: String) = Path.of(get(k).getOrElse(""))
        pairs(get("CLAD_MDR_LISTENERS").getOrElse(""), "CLAD_MDR_LISTENERS").map(listeners =>
          MdrEnv(p("CLAD_MDR_CONFIG_DIR"), p("CLAD_MDR_GATEWAY_DATA_DIR"), p("CLAD_MDR_DATA_DIR"), p("CLAD_MDR_KEYS_DIR"),
            p("CLAD_MDR_EGRESS_LOG"), listeners, get("CLAD_MDR_DB_URL").getOrElse(""), get("CLAD_MDR_APP").getOrElse(""), get("CLAD_MDR_WEBHOOK_URL")))
