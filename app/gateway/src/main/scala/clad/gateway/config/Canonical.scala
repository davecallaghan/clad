package clad.gateway.config

import java.security.MessageDigest

object Canonical:
  /** Compact JSON with every object's keys sorted: the input to approval digests. */
  def render(v: ujson.Value): String = sorted(v).render()

  private def sorted(v: ujson.Value): ujson.Value = v match
    case o: ujson.Obj => ujson.Obj.from(o.value.toSeq.sortBy(_._1).map((k, x) => k -> sorted(x)))
    case a: ujson.Arr => ujson.Arr.from(a.value.map(sorted))
    case other        => other

  def sha256(s: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
