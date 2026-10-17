package clad.gateway

import clad.gateway.config.Fixtures
import clad.gateway.store.TestDatabase
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*

class MainSpec extends AnyFunSuite:
  /** A copy of the fixture governance directory (which includes runtime.yaml and roc.yaml). */
  private def configDir(): Path =
    val dir = Files.createTempDirectory("cfg")
    val from = Fixtures.dir
    val s = Files.walk(from)
    try s.iterator().asScala.foreach { p =>
      val target = dir.resolve(from.relativize(p).toString)
      if Files.isDirectory(p) then Files.createDirectories(target) else Files.copy(p, target)
    }
    finally s.close()
    dir

  private final case class Ran(code: Int, out: String, err: String)

  private def run(args: Seq[String], env: Map[String, String]): Ran =
    val out = java.io.ByteArrayOutputStream()
    val err = java.io.ByteArrayOutputStream()
    val code = Console.withOut(out)(Console.withErr(err)(Main.run(args, env)))
    Ran(code, out.toString, err.toString)

  test("approval-digest prints the digest and that approval is pending for the unapproved fixture") {
    val r = run(Seq("approval-digest"), Map("CLAD_GATEWAY_CONFIG_DIR" -> configDir().toString))
    assert(r.code == 0, r.err)
    assert(r.out.contains("constraints_digest: sha256:") && r.out.contains("approval: pending ("), r.out)
  }

  test("check on the fixture config returns 0") {
    val r = run(Seq("check"), Map("CLAD_GATEWAY_CONFIG_DIR" -> configDir().toString))
    assert(r.code == 0, r.out + r.err)
  }

  private def declaringOpenai(): Path =
    val dir = configDir()
    val f = dir.resolve("prompts").resolve("assistant.general.yaml")
    Files.writeString(f, Files.readString(f).replaceFirst("(?m)^model_tier:.*$", "$0\npassthrough: openai"))
    dir

  test("a template declaring the chat upstream as its passthrough makes check and serve refuse, naming the template") {
    val c = run(Seq("check"), Map("CLAD_GATEWAY_CONFIG_DIR" -> declaringOpenai().toString))
    assert(c.code == 1 && c.err.contains("CLAD_GATEWAY_ERROR assistant.general: passthrough: openai"), c.err)
    val env = Map("CLAD_GATEWAY_CONFIG_DIR" -> declaringOpenai().toString, "CLAD_GATEWAY_KEYS_DIR" -> "keys",
      "CLAD_GATEWAY_DATA_DIR" -> "data", "CLAD_GATEWAY_WRITER_URL" -> "jdbc:postgresql://unused/db",
      "CLAD_GATEWAY_UPSTREAMS" -> "openai=http://upstream.invalid", "CLAD_GATEWAY_KEY_OPENAI" -> "k",
      "CLAD_GATEWAY_TOKENS" -> "t=api", "CLAD_GATEWAY_ADMIN_TOKEN" -> "a", "CLAD_GATEWAY_PORT" -> "1")
    val s = run(Seq("serve"), env)
    assert(s.code == 1 && s.err.contains("CLAD_GATEWAY_ERROR assistant.general: passthrough: openai"), s.err)
  }

  test("serve without CLAD_GATEWAY_PORT returns 2 and names the variable") {
    val env = Map("CLAD_GATEWAY_CONFIG_DIR" -> configDir().toString, "CLAD_GATEWAY_KEYS_DIR" -> "keys", "CLAD_GATEWAY_DATA_DIR" -> "data",
      "CLAD_GATEWAY_WRITER_URL" -> "jdbc:postgresql://unused/db", "CLAD_GATEWAY_UPSTREAMS" -> "openai=http://upstream.invalid",
      "CLAD_GATEWAY_KEY_OPENAI" -> "k", "CLAD_GATEWAY_TOKENS" -> "t=api", "CLAD_GATEWAY_ADMIN_TOKEN" -> "a")
    val r = run(Seq("serve"), env)
    assert(r.code == 2 && r.err.contains("CLAD_GATEWAY_PORT"), r.err)
    val noKey = run(Seq("serve"), env - "CLAD_GATEWAY_KEY_OPENAI" + ("CLAD_GATEWAY_PORT" -> "1"))
    assert(noKey.code == 2 && noKey.err.contains("CLAD_GATEWAY_KEY_OPENAI"), noKey.err)
  }

  test("a missing config directory variable exits 2 naming it, and an unknown command exits 2") {
    val r = run(Seq("check"), Map.empty)
    assert(r.code == 2 && r.err.contains("CLAD_GATEWAY_CONFIG_DIR"), r.err)
    assert(run(Seq("nonsense"), Map.empty).code == 2)
    assert(run(Seq.empty, Map.empty).code == 2)
  }

  test("migrate does not print the passwords it was given") {
    val db = TestDatabase.fresh()
    val r = run(Seq("migrate"), Map("CLAD_GATEWAY_ADMIN_URL" -> db.adminUrl, "CLAD_GATEWAY_WRITER_PASSWORD" -> "secretwriterpw",
      "CLAD_GATEWAY_READER_PASSWORD" -> "secretreaderpw"))
    assert(r.code == 0, r.err)
    assert(!(r.out + r.err).contains("secretwriterpw") && !(r.out + r.err).contains("secretreaderpw"))
  }

  test("keys generates both key pairs once, and export against a fresh database writes bundle.json") {
    val db = TestDatabase.fresh()
    val keys = Files.createTempDirectory("keys")
    val data = Files.createTempDirectory("data")
    val cfg = configDir()
    val k = run(Seq("keys"), Map("CLAD_GATEWAY_KEYS_DIR" -> keys.toString))
    assert(k.code == 0, k.err)
    assert(Files.exists(keys.resolve("components.ed25519")) && Files.exists(keys.resolve("supervisor.ed25519")))
    assert(k.out.toLowerCase.contains("back"), k.out)
    val out = Files.createTempDirectory("parent").resolve("bundle")
    val env = Map("CLAD_GATEWAY_READER_URL" -> db.readerUrl, "CLAD_GATEWAY_EGRESS_LOG" -> data.resolve("access.log").toString,
      "CLAD_GATEWAY_LISTENERS" -> "8081=api.deepinfra.com", "CLAD_GATEWAY_CONFIG_DIR" -> cfg.toString,
      "CLAD_GATEWAY_KEYS_DIR" -> keys.toString, "CLAD_GATEWAY_DATA_DIR" -> data.toString, "CLAD_GATEWAY_APP" -> "personal_finance")
    val e = run(Seq("export", "--since", Instant.now().minusSeconds(60).toString, "--until", Instant.now().plusSeconds(60).toString,
      "--out", out.toString), env)
    assert(e.code == 0, e.err)
    assert(Files.exists(out.resolve("bundle.json")))
    val noOut = run(Seq("export", "--since", "2026-01-01T00:00:00Z", "--until", "2026-01-02T00:00:00Z"), env)
    assert(noOut.code == 2 && noOut.err.contains("--out"), noOut.err)
  }
