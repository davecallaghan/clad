package clad.audit

import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.InMemoryKeyManagement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.Try

class ChainAnchorSpec extends AnyFlatSpec with Matchers:

  private given Lift[Try] = Lift.given_Lift_Try
  private val kms: KeyManagementService = InMemoryKeyManagement.default
  private val now: Instant = Instant.parse("2026-04-22T18:00:00Z")

  private def mkRecord(prev: Option[String], salt: String): AuditRecord =
    val entry = AuditEntry(
      Constraint.Obligation(PropertyId.unsafe("test_property"), Level.Enterprise), "1.0",
      EvaluabilityClass.Mechanical, satisfied = true, MechanicalDetail(true), now
    )
    AuditRecord(s"sha256:artifact$salt", Vector(entry), "sha256:config", now, prev)

  private def appendChain(dir: Path, n: Int): Unit =
    // The KMS is required for the store to write an anchor.
    val store = AppendOnlyFileStore[Try](dir, Some(kms))
    var prev: Option[String] = None
    (0 until n).foreach { i =>
      val rec = mkRecord(prev, i.toString)
      store.append(SignedAuditRecord.sign(rec, kms).toOption.get).get
      prev = Some(rec.digest)
    }

  private def dropLastLines(dir: Path, k: Int): Unit =
    val f = dir.resolve("chain.jsonl")
    val kept = Files.readAllLines(f).asScala.toVector.filter(_.nonEmpty).dropRight(k)
    Files.write(f, (kept.mkString("\n") + "\n").getBytes("UTF-8"))

  "an anchored chain" should "verify when intact" in {
    val dir = Files.createTempDirectory("clad-anchor-intact")
    appendChain(dir, 5)

    // No KMS needed to read; a reader never writes an anchor.
    val records = AppendOnlyFileStore[Try](dir).readAll.get
    val anchor = ChainAnchorStore(dir).read.get

    val report = AuditVerifier.verifyAgainstAnchor(records, anchor, kms)
    report.isFullyVerified shouldBe true
    report.recordCount shouldBe 5
  }

  it should "detect a chain whose tail has been deleted" in {
    val dir = Files.createTempDirectory("clad-anchor-truncated")
    appendChain(dir, 5)
    dropLastLines(dir, 2)

    val records = AppendOnlyFileStore[Try](dir).readAll.get
    val anchor = ChainAnchorStore(dir).read.get

    // The surviving chain is internally consistent. This assertion documents the
    // gap the anchor exists to close, and must stay.
    AuditVerifier.verify(records, kms).isFullyVerified shouldBe true

    val report = AuditVerifier.verifyAgainstAnchor(records, anchor, kms)
    report.isFullyVerified shouldBe false
    report.failures should contain(AuditVerifier.TruncationDetected(5, 3))
  }

  it should "report AnchorMissing when no anchor is present" in {
    val report = AuditVerifier.verifyAgainstAnchor(Vector.empty, None, kms)
    report.failures should contain(AuditVerifier.AnchorMissing)
    report.isFullyVerified shouldBe false
  }
