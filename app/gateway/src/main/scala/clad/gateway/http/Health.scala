package clad.gateway.http

import clad.audit.KeyManagementService
import java.net.{InetSocketAddress, Socket, URI}
import java.time.Duration
import scala.util.control.NonFatal

/** GET /v1/health: every check runs on each request. Healthy (200) only when all pass; otherwise 503 naming each
  * failure. A check reports what it verified and never a secret. No check calls a model provider. */
final class Health(checks: Vector[(String, () => Either[String, String])]):
  def run: (Boolean, ujson.Obj) =
    val results = checks.map { (name, check) =>
      val r = try check() catch case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
      name -> r
    }
    val healthy = results.forall(_._2.isRight)
    (healthy, ujson.Obj(
      "status" -> (if healthy then "ok" else "unavailable"),
      "checks" -> ujson.Obj.from(results.map((n, r) => n -> ujson.Obj("ok" -> r.isRight, "detail" -> r.merge)))))

object Health:
  private val Probe = "clad-gateway health probe".getBytes("UTF-8")

  /** A TCP connection to the upstream's host and port (the egress proxy): reachability, without a provider call. */
  def tcp(upstream: URI, timeout: Duration): () => Either[String, String] = () =>
    val port = if upstream.getPort >= 0 then upstream.getPort else if upstream.getScheme == "https" then 443 else 80
    val socket = Socket()
    try
      socket.connect(InetSocketAddress(upstream.getHost, port), timeout.toMillis.toInt)
      Right(s"${upstream.getHost}:$port reachable")
    catch case e: java.io.IOException => Left(s"${upstream.getHost}:$port: ${e.getClass.getSimpleName}: ${e.getMessage}")
    finally socket.close()

  /** Each key signs a probe and verifies its own signature. */
  def signs(keys: Vector[(String, KeyManagementService)]): () => Either[String, String] = () =>
    keys.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) { case (acc, (role, kms)) =>
      acc.flatMap(done => kms.sign(Probe).flatMap(sig => kms.verify(Probe, sig)).left.map(e => s"$role: $e")
        .flatMap(ok => Either.cond(ok, done :+ role, s"$role: its signature does not verify")))
    }.map(roles => s"${roles.mkString(", ")} sign and verify")
