package clad.gateway.pipeline

import clad.gateway.config.Canonical

object Principal:
  private val Email = """[^\s:@]+@[^\s:@]+""".r
  private val Prefix = "sha256:"
  /** The pseudonym is the first 16 hex characters of sha256(lower(email)) (spec section 7). */
  private val PseudonymHexLength = 16

  /** Every email address becomes `user-<hex>`; the same address in any case gives the same pseudonym. */
  def pseudonymise(p: String): String =
    Email.replaceAllIn(p, m =>
      scala.util.matching.Regex.quoteReplacement("user-" +
        Canonical.sha256(m.matched.toLowerCase(java.util.Locale.ROOT)).stripPrefix(Prefix).substring(0, PseudonymHexLength)))
