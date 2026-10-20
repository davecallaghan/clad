package clad.mdr.detectors

import clad.audit.{AuditVerifier, ChainAnchor, ChainAnchorStore, Ed25519KeyManagement}
import clad.gateway.keys.Keys
import clad.mdr.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.nio.file.Path
import java.time.Instant

/** Verifies the whole chain (recorded digests, links, Ed25519 signatures with the components public key from
  * `keysDir`), checks the last anchor in `anchorDir` against the chain at the anchor's length, and the degraded ratio
  * (degraded records / GIL entries in the window) against `degraded_ratio_alert`. The anchor is read before the
  * chain, so a chain longer than its anchor is consistent; one shorter is truncation. */
final class ChainHealth(cfg: MdrConfig, source: Source, keysDir: Path, anchorDir: Path) extends Detector:
  val id = "chain_health"

  private def integrity(s: Snapshot): Either[String, Option[MdrAlert]] =
    for
      pem <- Keys.publicPem(keysDir, "components")
      keyId <- Keys.keyIdOf(pem)
      kms <- Ed25519KeyManagement.fromPublicPem(pem, keyId).left.map(_.toString)
      anchor <- ChainAnchorStore(anchorDir).read.toEither.left.map(e => s"cannot read the chain anchor in $anchorDir: ${e.getMessage}")
      chain <- source.chain
    yield
      val report = AuditVerifier.verify(chain, kms)
      val anchorProblems = anchor.toVector.flatMap { a =>
        Vector(
          Option.when(!ChainAnchor.isAuthentic(a, kms))("the anchor's signature does not verify"),
          Option.when(a.anchor.count > chain.size)(s"truncation: the anchor names ${a.anchor.count} records, the chain has ${chain.size}"),
          Option.when(a.anchor.count <= chain.size && a.anchor.count > 0 &&
            chain(a.anchor.count - 1).record.digest != a.anchor.headDigest)(
            s"the anchor's head ${a.anchor.headDigest} is not record ${a.anchor.count} of the chain")
        ).flatten
      }
      val problems = report.failures.map(_.toString) ++ anchorProblems
      val head = chain.lastOption.map(_.record.digest).getOrElse(ChainAnchor.Genesis)
      Option.when(problems.nonEmpty && !s.alertedKeys.contains(s"chain:$head"))(
        MdrAlert(s"chain:$head", AlertSeverity.P1Critical, AlertCategory.Governance, id, Vector.empty,
          s"Audit chain integrity failure (${chain.size} records): ${problems.mkString("; ")}. Preserve the store and " +
            "the anchors directory; treat every record after the first failure as unverified.", None))

  private def ratio(s: Snapshot): Option[MdrAlert] =
    val gil = s.gil.count(g => s.window.contains(g.registeredAt))
    val degraded = s.degraded.filter(d => s.window.contains(d.record.timestamp))
    // keyed by the newest degraded record, not the window: windows overlap by the in-flight grace, so the same records
    // must not alert again next cycle; a new degraded record while the ratio holds is a new alert
    val key = s"degraded_ratio:${degraded.map(_.record.timestamp).maxOption.getOrElse(s.window.end)}"
    Option.when(gil > 0 && degraded.size.toDouble / gil > cfg.degradedRatioAlert && !s.alertedKeys.contains(key))(
      MdrAlert(key, AlertSeverity.P3Medium, AlertCategory.Governance, id, degraded.map(_.record.interactionId.value).distinct,
        f"Degraded records are ${degraded.size} of $gil interactions in ${s.window.start} – ${s.window.end} " +
          f"(${degraded.size.toDouble / gil}%.3f, alert above ${cfg.degradedRatioAlert}). Check provider and component health.", None))

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    integrity(s).map(_.toVector ++ ratio(s))
