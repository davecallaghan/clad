package clad.gateway.config

import org.scalatest.funsuite.AnyFunSuite
import java.time.Instant

class YamlJsonSpec extends AnyFunSuite:
  test("an unquoted timestamp keeps the full instant, so Instant.parse still works") {
    val v = YamlJson.parse("at: 2026-10-17T10:00:00Z\nmidnight: 2026-10-17T00:00:00Z\n")
    assert(v("at").str == "2026-10-17T10:00:00Z" && Instant.parse(v("at").str) == Instant.parse("2026-10-17T10:00:00Z"))
    assert(v("midnight").str == "2026-10-17T00:00:00Z")
  }

  test("an unquoted date-only value stays the date string") {
    assert(YamlJson.parse("day: 2026-10-17\n")("day").str == "2026-10-17")
  }

  test("an unquoted attested_at loads as an attestation") {
    val f = java.nio.file.Files.createTempFile("att", ".yaml")
    java.nio.file.Files.writeString(f, "attestations: [{template_id: t, template_version: \"1\", attestor: david, attested_at: 2026-10-17T10:00:00Z, rationale: x}]")
    val a = Attestations.load(f, Set("david")).fold(e => fail(e), identity)
    assert(a.get("t", "1").map(_.attestedAt) == Some(Instant.parse("2026-10-17T10:00:00Z")))
  }
