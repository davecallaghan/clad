package clad.evaluation

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

case class PromptArtifact(
  content: String,
  metadata: Map[String, String] = Map.empty
):
  /** The artifact's identity: what it said AND what it was evaluated as. A digest of the content alone let two
    * artifacts with the same text but different metadata (a model pending, then approved) share one stored body. */
  def digest: String = PromptArtifact.digestOf(this)

object PromptArtifact:
  private val Version = "clad-artifact-v1"

  /** Netstrings (`<byte length>:<bytes>,`) of the version tag, the content, then each metadata key and value in
    * key order. Unambiguous for any content, and reproducible without a JSON library. */
  def canonical(a: PromptArtifact): Array[Byte] =
    def net(s: String): String = s"${s.getBytes(UTF_8).length}:$s,"
    val fields = Vector(Version, a.content) ++ a.metadata.toVector.sortBy(_._1).flatMap((k, v) => Vector(k, v))
    fields.map(net).mkString.getBytes(UTF_8)

  def digestOf(a: PromptArtifact): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(canonical(a)).map("%02x".format(_)).mkString
