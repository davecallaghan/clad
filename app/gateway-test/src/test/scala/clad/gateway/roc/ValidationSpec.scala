package clad.gateway.roc

import clad.gateway.config.{Attestation, Attestations, Fixtures, GatewayConfig}
import clad.gateway.store.MemoryStore
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Files
import java.time.Instant

class ValidationSpec extends AnyFunSuite:
  private val at = Instant.parse("2026-10-19T10:00:00Z")

  /** Scores each item by the assistant text: "S6 <p>" gives category S6 at p, "<p>" gives P(unsafe) = p. */
  private object TextGuard extends Guard:
    val calls = scala.collection.mutable.ArrayBuffer.empty[String]
    def score(conversation: Vector[ujson.Obj], interactionId: String): Either[String, GuardScores] =
      calls += interactionId
      conversation.last("content").str.split(" ") match
        case Array(k, p) => Right(GuardScores(p.toDouble, LlamaGuard.SafeCategories.map(_ -> 0.0).toMap + (k -> p.toDouble), "unsafe"))
        case Array(p)    => Right(GuardScores(p.toDouble, LlamaGuard.SafeCategories.map(_ -> p.toDouble).toMap, "safe"))
        case _           => Left("unparseable fake text")

  private def line(id: String, text: String, expected: String, category: Option[String] = None): String =
    val o = ujson.Obj("id" -> id, "conversation" -> ujson.Arr(ujson.Obj("role" -> "user", "content" -> "q"),
      ujson.Obj("role" -> "assistant", "content" -> text)), "expected" -> expected)
    category.foreach(c => o("category") = c)
    ujson.write(o)

  // tau is 0.5 in the fixture: s2 (safe, 0.7) is a false positive, u2 (unsafe S6, 0.2) a false negative
  private val set = Vector(line("s1", "0.1", "safe"), line("s2", "0.7", "safe"), line("s3", "0.2", "safe"),
    line("u1", "S6 0.9", "unsafe", Some("S6")), line("u2", "S6 0.2", "unsafe", Some("S6"))).mkString("\n") + "\n"

  private def written(text: String) =
    val f = Files.createTempFile("validation", ".jsonl"); Files.writeString(f, text); f

  private def attest(cfg: GatewayConfig, digest: String): GatewayConfig =
    cfg.copy(attestations = Attestations(Vector(Attestation(Validation.TemplateId, digest, "david", at, "labels reviewed"))))

  test("rates are computed at the enterprise tau, every call is registered in the GIL first, and the result is written") {
    val (digest, items) = Validation.read(written(set)).fold(fail(_), identity)
    val cfg = attest(Fixtures.config, digest)
    assert(Validation.attested(cfg, digest) == Right(()))
    val store = MemoryStore()
    val r = Validation.run(cfg, items, digest, TextGuard, store, "assistant", () => at).fold(fail(_), identity)
    assert(r.items == 5 && r.fp == 1.0 / 3 && r.fn == 0.5 && r.misclassified == Vector("s2", "u2"))
    assert(store.gil.size == 5 && store.gil.forall(g => g.metadata("principal") == Validation.Principal &&
      g.metadata("template_id") == Validation.TemplateId && g.metadata("template_version") == digest &&
      g.metadata("project") == "assistant" && g.metadata("model") == cfg.roc.classifier.model))
    assert(TextGuard.calls.takeRight(5) == store.gil.map(_.interactionId.value))
    val out = Validation.write(Files.createTempDirectory("data"), r).fold(fail(_), identity)
    val j = ujson.read(Files.readString(out))
    assert(j("profile").str == "llama_guard" && j("misclassified").arr.map(_.str) == Seq("s2", "u2") && j("items").num == 5)
    assert(r.profileLines.exists(_.startsWith("measured_fp: ")) && r.profileLines.exists(_.startsWith("validated_at: ")))
  }

  test("a missing attestation, or one for an earlier version of the file, is refused") {
    val (digest, _) = Validation.read(written(set)).fold(fail(_), identity)
    assert(Validation.attested(Fixtures.config, digest).left.exists(_.contains("no attestation")))
    val (edited, _) = Validation.read(written(set + line("s4", "0.1", "safe") + "\n")).fold(fail(_), identity)
    assert(edited != digest && Validation.attested(attest(Fixtures.config, digest), edited).isLeft)
  }

  test("malformed items, duplicate ids and a set with one label only are refused") {
    assert(Validation.read(written("""{"id": "a", "conversation": [], "expected": "safe"}""")).left.exists(_.contains("line 1")))
    assert(Validation.read(written(line("a", "0.1", "maybe"))).left.exists(_.contains("safe or unsafe")))
    assert(Validation.read(written(line("a", "0.1", "safe") + "\n" + line("a", "0.2", "safe"))).left.exists(_.contains("duplicate")))
    val (d, safeOnly) = Validation.read(written(line("a", "0.1", "safe"))).fold(fail(_), identity)
    assert(Validation.run(Fixtures.config, safeOnly, d, TextGuard, MemoryStore(), "assistant", () => at).left.exists(_.contains("both")))
  }

  test("a classifier failure ends the run; an unknown project is refused before any call") {
    val (digest, items) = Validation.read(written(set)).fold(fail(_), identity)
    val failing = new Guard { def score(c: Vector[ujson.Obj], i: String) = Left("timeout") }
    assert(Validation.run(Fixtures.config, items, digest, failing, MemoryStore(), "assistant", () => at).left.exists(_.contains("timeout")))
    val store = MemoryStore()
    assert(Validation.run(Fixtures.config, items, digest, TextGuard, store, "nope", () => at).left.exists(_.contains("unknown project")))
    assert(store.gil.isEmpty)
  }
