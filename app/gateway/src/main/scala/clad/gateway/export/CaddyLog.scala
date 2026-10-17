package clad.gateway.`export`

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*

/** Reads the egress proxy's JSON access log into conformance infra entries (spec section 4). */
object CaddyLog:
  /** The one request header copied: the interaction id the gateway sends upstream. No other header is read. */
  val IdHeader = "X-Clad-Interaction-Id"
  private val MillisPerSecond = 1000.0

  /** `listeners` maps a proxy port to the provider host behind it; an unknown port is kept as the host.
    * A line that is not a log entry (not JSON, or without `ts`, `request` or `status`) is a Left naming the line. */
  def read(path: Path, listeners: Map[String, String]): Either[String, Vector[ujson.Obj]] =
    try
      Files.readAllLines(path).asScala.toVector.zipWithIndex.filter(_._1.trim.nonEmpty)
        .foldLeft[Either[String, Vector[ujson.Obj]]](Right(Vector.empty)) { case (acc, (line, i)) =>
          acc.flatMap { rows =>
            try Right(rows :+ entry(ujson.read(line), listeners))
            catch case e: Exception => Left(s"$path:${i + 1}: ${e.getClass.getSimpleName}: ${e.getMessage}")
          }
        }
    catch case e: java.io.IOException => Left(s"cannot read $path: ${e.getMessage}")

  private def entry(e: ujson.Value, listeners: Map[String, String]): ujson.Obj =
    val req = e("request").obj
    def text(k: String): String = req.get(k).collect { case ujson.Str(s) => s }.getOrElse("")
    val host = text("host")
    val port = host.substring(host.lastIndexOf(':') + 1)
    val id = req.get("headers").collect { case o: ujson.Obj => o }
      .flatMap(_.obj.get(IdHeader)).collect { case a: ujson.Arr => a.value }.flatMap(_.headOption).collect { case ujson.Str(s) => s }
    val ts = Instant.ofEpochMilli(Math.round(e("ts").num * MillisPerSecond))
    ujson.Obj("ts" -> ts.toString, "host" -> listeners.getOrElse(port, port), "method" -> text("method"),
      "path" -> text("uri"), "status" -> e("status").num.toInt,
      "interactionId" -> id.fold[ujson.Value](ujson.Null)(ujson.Str(_)))
