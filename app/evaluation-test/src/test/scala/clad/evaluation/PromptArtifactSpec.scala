package clad.evaluation

import org.scalatest.funsuite.AnyFunSuite

class PromptArtifactSpec extends AnyFunSuite:
  private val a = PromptArtifact("text", Map("model_registry_status" -> "pending", "model" -> "m"))

  test("the digest covers the metadata: the same text evaluated as approved is a different artifact") {
    assert(a.digest != a.copy(metadata = a.metadata + ("model_registry_status" -> "approved")).digest)
    assert(a.digest != a.copy(content = "text2").digest)
  }

  test("the digest does not depend on metadata order, and is unambiguous at field boundaries") {
    assert(a.digest == PromptArtifact("text", Map("model" -> "m", "model_registry_status" -> "pending")).digest)
    assert(PromptArtifact("ab", Map("c" -> "d")).digest != PromptArtifact("a", Map("bc" -> "d")).digest)
    assert(PromptArtifact("a", Map("b" -> "c,d")).digest != PromptArtifact("a", Map("b" -> "c", "d" -> "")).digest)
  }

  test("the canonical form is the documented netstrings") {
    assert(String(PromptArtifact.canonical(PromptArtifact("hé", Map("k" -> "v"))), "UTF-8") == "16:clad-artifact-v1,3:hé,1:k,1:v,")
    assert(a.digest.startsWith("sha256:") && a.digest.length == "sha256:".length + 64)
  }
