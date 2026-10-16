package clad.gateway.`export`

import java.nio.file.{Files, Path}
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*

/** Reads the egress proxy's JSON access log into conformance infra entries (spec section 4). */
object CaddyLog:
  /** The one request header copied: the interaction id the gateway sends upstream. No other header is read. */
  val IdHeader = "X-Clad-Interaction-Id"
  private val MillisPerSecond = 1000.0

  /** `listeners` maps a proxy port to the provider host behind it; an unknown port is kept as the host.
    * A line that is not a log entry throws IllegalArgumentException naming the line. */
  def read(path: Path, listeners: Map[String, String]): Vector[ujson.Obj] =
    Files.readAllLines(path).asScala.toVector.zipWithIndex.filter(_._1.trim.nonEmpty).map { (line, i) =>
      try entry(ujson.read(line), listeners)
      catch case e: Exception =>
        throw IllegalArgumentException(s"$path:${i + 1}: ${e.getClass.getSimpleName}: ${e.getMessage}", e)
    }

  private def entry(e: ujson.Value, listeners: Map[String, String]): ujson.Obj =
    val req = e.obj.get("request").collect { case o: ujson.Obj => o }.getOrElse(ujson.Obj())
    def text(k: String): String = req.obj.get(k).collect { case ujson.Str(s) => s }.getOrElse("")
    val host = text("host")
    val port = host.substring(host.lastIndexOf(':') + 1)
    val id = req.obj.get("headers").collect { case o: ujson.Obj => o }
      .flatMap(_.obj.get(IdHeader)).collect { case a: ujson.Arr => a.value }.flatMap(_.headOption).collect { case ujson.Str(s) => s }
    val ts = Instant.ofEpochMilli(Math.floor(e("ts").num * MillisPerSecond).toLong).truncatedTo(ChronoUnit.MILLIS)
    ujson.Obj("ts" -> ts.toString, "host" -> listeners.getOrElse(port, port), "method" -> text("method"),
      "path" -> text("uri"), "status" -> e.obj.get("status").map(_.num.toInt).getOrElse(0),
      "interactionId" -> id.fold[ujson.Value](ujson.Null)(ujson.Str(_)))
