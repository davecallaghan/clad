package clad.conformance

import java.nio.file.{Files, Path}

case class WormConfig(jdbcUrl: String, user: String, passwordFile: String, tables: Vector[String])
case class GhostConfig(url: String, matchToleranceSeconds: Long)
case class MdrConfig(heartbeatIntervalSeconds: Long, detectionDelaySeconds: Long)
case class ProbeConfig(worm: Option[WormConfig], ghost: Option[GhostConfig], mdr: Option[MdrConfig])

object ProbeConfig:
  def read(path: Path): Either[String, ProbeConfig] =
    try
      val j = ujson.read(Files.readString(path))
      Right(ProbeConfig(
        j.obj.get("worm").map(w => WormConfig(w("jdbcUrl").str, w("user").str, w("passwordFile").str, w("tables").arr.map(_.str).toVector)),
        j.obj.get("ghost").map(g => GhostConfig(g("url").str, g("matchToleranceSeconds").num.toLong)),
        j.obj.get("mdr").map(m => MdrConfig(m("heartbeatIntervalSeconds").num.toLong, m("detectionDelaySeconds").num.toLong))))
    catch case e: Exception => Left(s"probe config $path: ${e.getClass.getSimpleName}: ${e.getMessage}")
