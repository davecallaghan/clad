package clad.gateway.`export`

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}

class CaddyLogSpec extends AnyFunSuite:
  private def logOf(lines: String*): Path =
    val p = Files.createTempFile("access", ".log")
    Files.writeString(p, lines.mkString("\n") + "\n")
    p

  private val listeners = Map("8081" -> "api.deepinfra.com")

  private def line(ts: String = "1760000000.123", host: String = "egress:8081", headers: String = "{}") =
    s"""{"ts":$ts,"status":200,"request":{"method":"POST","uri":"/v1/x","host":"$host","headers":$headers}}"""

  test("only X-Clad-Interaction-Id is copied; Authorization and Cookie never appear") {
    val l = line(headers = """{"Authorization":["Bearer s3cret"],"Cookie":["sid=abc"],"X-Clad-Interaction-Id":["iid-1","iid-2"]}""")
    val rows = CaddyLog.read(logOf(l), listeners).fold(fail(_), identity)
    assert(rows.map(_("interactionId").str) == Vector("iid-1"))
    val text = rows.map(ujson.write(_)).mkString
    assert(!text.contains("s3cret") && !text.contains("sid=abc") && !text.contains("Authorization") && !text.contains("Cookie"))
    assert(rows.flatMap(_.obj.keys).toSet == Set("ts", "host", "method", "path", "status", "interactionId"))
  }

  test("a missing header gives null") {
    val rows = CaddyLog.read(logOf(line()), listeners).fold(fail(_), identity)
    assert(rows.map(_("interactionId")) == Vector(ujson.Null))
  }

  test("fractional epoch seconds keep milliseconds") {
    val rows = CaddyLog.read(logOf(line()), listeners).fold(fail(_), identity)
    assert(rows.map(_("ts").str) == Vector("2025-10-09T08:53:20.123Z"))
  }

  test("a known port maps to its provider host and an unknown port is kept") {
    val rows = CaddyLog.read(logOf(line(), line(host = "egress:9999")), listeners).fold(fail(_), identity)
    assert(rows.map(_("host").str) == Vector("api.deepinfra.com", "9999"))
  }

  test("a malformed line is a Left naming the file and line") {
    val p = logOf(line(), "not json")
    val r = CaddyLog.read(p, listeners)
    assert(r.left.exists(m => m.contains(p.toString) && m.contains(":2:")), r)
  }

  test("a line without request or status is malformed, not a fabricated entry") {
    val noRequest = CaddyLog.read(logOf("""{"ts":1.0,"status":200}"""), listeners)
    val noStatus = CaddyLog.read(logOf("""{"ts":1.0,"request":{"method":"GET","uri":"/","host":"h:1"}}"""), listeners)
    assert(noRequest.isLeft && noStatus.isLeft, (noRequest, noStatus))
  }
