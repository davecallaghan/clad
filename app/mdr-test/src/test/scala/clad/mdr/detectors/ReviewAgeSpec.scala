package clad.mdr.detectors

import clad.mdr.*
import clad.mdr.TestSnapshots.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}
import java.time.Duration

class ReviewAgeSpec extends AnyFunSuite:
  private def dir(approvedAt: String, status: String, attestedAt: String): Path =
    val d = Files.createTempDirectory("cfg")
    Files.writeString(d.resolve("constraints.yaml"),
      s"""approval: {status: $status, approved_by: david, approved_at: "$approvedAt", constraints_digest: "sha256:d"}\n""")
    Files.writeString(d.resolve("attestations.yaml"),
      s"""attestations: [{template_id: agent.answer, template_version: "1", attestor: david, attested_at: "$attestedAt", rationale: r}]\n""")
    d
  private def run(d: Path) = ReviewAge(cfg, d).run(empty, now)
  private val old = now.minus(Duration.ofDays(cfg.reviewCadenceDays + 1)).toString
  private val recent = now.minus(Duration.ofDays(cfg.reviewCadenceDays - 1)).toString

  test("an attestation 91 days old with cadence 90 is a P4 compliance alert") {
    val a = run(dir(recent, "approved", old)).fold(fail(_), identity)
    assert(a.map(x => (x.severity, x.category, x.dedupeKey)) == Vector((AlertSeverity.P4Low, AlertCategory.Compliance, "review:agent.answer:1")))
  }

  test("an approved constraint set past the cadence alerts (a date without time counts); a pending one does not") {
    val stale = now.minus(Duration.ofDays(cfg.reviewCadenceDays + 2)).toString.take(10)
    assert(run(dir(stale, "approved", recent)).fold(fail(_), identity).map(_.dedupeKey) == Vector("review:constraint set:sha256:d"))
    assert(run(dir(stale, "pending", recent)).fold(fail(_), identity).isEmpty)
  }

  test("a date that does not parse is a detector failure, not a skipped review; an alerted key is not repeated") {
    assert(run(dir(recent, "approved", "last tuesday")).left.exists(_.contains("agent.answer")))
    assert(ReviewAge(cfg, dir(recent, "approved", old)).run(empty.copy(alertedKeys = Set("review:agent.answer:1")), now)
      .fold(fail(_), identity).isEmpty)
  }
