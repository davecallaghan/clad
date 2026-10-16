package clad.gateway.http

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Bearer tokens: each client token maps to a principal prefix; the admin token is separate and opens only /admin. */
final case class Clients(byToken: Map[String, String], adminToken: String):
  /** Constant-time comparison, so response timing does not reveal how much of a token matched. */
  private def same(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8))

  def prefixFor(token: String): Option[String] =
    byToken.collectFirst { case (t, prefix) if same(t, token) => prefix }

  def isAdmin(token: String): Boolean = adminToken.nonEmpty && same(adminToken, token)

object Clients:
  /** `spec` is "<token>=<prefix>,<token>=<prefix>". Every failure names the problem, never a token. */
  def parse(spec: String, adminToken: String): Either[String, Clients] =
    if adminToken.isEmpty then Left("the admin token is empty")
    else
      val entries = spec.split(",").toSeq.map(_.trim).filter(_.nonEmpty)
      if entries.isEmpty then Left("no client tokens configured")
      else
        entries.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) { (acc, entry) =>
          acc.flatMap { m =>
            entry.split("=", 2) match
              case Array(token, prefix) if token.trim.nonEmpty && prefix.trim.nonEmpty =>
                if m.contains(token.trim) then Left("duplicate client token")
                else if token.trim == adminToken then Left("a client token equals the admin token")
                else Right(m + (token.trim -> prefix.trim))
              case _ => Left("a client entry is not <token>=<prefix>")
          }
        }.map(Clients(_, adminToken))
