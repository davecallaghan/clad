package clad.gateway.config

import java.nio.file.Path
import java.time.Instant

final case class Attestation(templateId: String, version: String, attestor: String, attestedAt: Instant, rationale: String)

final class Attestations(rows: Vector[Attestation]):
  private val byKey = rows.map(a => (a.templateId, a.version) -> a).toMap
  def get(templateId: String, version: String): Option[Attestation] = byKey.get((templateId, version))

object Attestations:
  def load(path: Path, humans: Set[String]): Either[String, Attestations] =
    Loading.guard(path) {
      try
        val rows = YamlJson.read(path).obj.get("attestations").map(_.arr.toVector).getOrElse(Vector.empty).map { r =>
          Attestation(r("template_id").str, r("template_version") match { case ujson.Str(s) => s; case v => v.render() },
            r("attestor").str, Instant.parse(r("attested_at").str), r("rationale").str) }
        rows.find(a => !humans.contains(a.attestor)) match
          case Some(a) => Left(s"attestation of ${a.templateId}@${a.version}: attestor ${a.attestor} must be a declared human (an LLM is never an attestor)")
          case None    => Right(Attestations(rows))
      catch
        case e: NoSuchElementException => Left(s"$path: missing field ${e.getMessage}")
        case e: java.time.format.DateTimeParseException => Left(s"$path: ${e.getMessage}")
    }
