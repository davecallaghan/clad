package clad.gateway.`export`

import clad.audit.ChainAnchorStore
import clad.gateway.config.Fixtures
import clad.gateway.epg.Epg
import clad.gateway.keys.Keys
import clad.gateway.pipeline.*
import clad.gateway.store.{Jdbc, PgStore, TestDatabase}
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}
import java.time.Instant

class RoundTripSpec extends AnyFunSuite:
  private def keysDir(): Path =
    val keys = Files.createTempDirectory("keys")
    Seq("components", "supervisor").foreach(r => Keys.generate(keys, r))
    keys

  test("records written by the gateway export to a bundle that conformance verifies") {
    val db = TestDatabase.fresh()
    val keys = keysDir()
    val anchors = Files.createTempDirectory("anchors")
    val cfg = Fixtures.approvedConfig()
    val comp = Keys.load(keys, "components").toOption.get
    val sup = Keys.load(keys, "supervisor").toOption.get
    val store = PgStore(db.writerUrl, cfg.runtime.chainLockKey, ChainAnchorStore(anchors), comp)
    val up = FakeUpstream()
    val containment = Files.createTempFile("c", ".json")
    Files.writeString(containment, "{}")   // nothing contained; an empty file would be unreadable and block every call
    val gw = Gateway(cfg, Epg.build(cfg).toOption.get, store, comp, sup, up, NoPassthrough, containment, () => Instant.now())
    val start = Instant.now().minusSeconds(60)
    (1 to 3).foreach(_ => gw.complete(answerRequest()))
    gw.complete(answerRequest(template = "agent.sql"))
    val log = Files.createTempFile("access", ".log")
    Files.writeString(log, up.calls.map((_, iid) => ujson.write(ujson.Obj("ts" -> Instant.now().getEpochSecond.toDouble,
      "status" -> 200, "request" -> ujson.Obj("method" -> "POST", "uri" -> "/v1/openai/chat/completions", "host" -> "egress:8081",
      "headers" -> ujson.Obj("X-Clad-Interaction-Id" -> ujson.Arr(iid)))))).mkString("\n") + "\n")
    val out = Files.createTempDirectory("bundle")
    BundleExport.write(out, start, Instant.now().plusSeconds(60), None, db.readerUrl, Some(log),
      Map("8081" -> "api.deepinfra.com"), anchors, keys, cfg.governance, "personal_finance").fold(fail(_), identity)
    val results = clad.conformance.BundleReader.read(out).map(b => clad.conformance.Conformance.evaluate(b, None, None)).fold(fail(_), identity)
    info(results.map(r => s"${r.id} -> ${r.verdict} ${r.reason}").mkString("\n"))
    for id <- Seq("R-AI2", "R-AI3", "R-AI4", "R-ANCHOR", "R-AI6", "R-EA1", "R-EA2", "R-AX5", "R-CFG-PARSE", "R-EPG-REEVAL", "R-EPG-BLOCK") do
      assert(results.find(_.id == id).exists(_.verdict == clad.conformance.Verdict.Satisfied), s"$id: ${results.find(_.id == id)}")
  }

  test("a database where migrate has not run gives a bundle without gil.jsonl or audit.jsonl") {
    val migrated = TestDatabase.fresh()
    val bare = s"clad_gw_test_bare_${ProcessHandle.current().pid()}"
    Jdbc.withConnection(TestDatabase.admin) { c =>
      c.createStatement().execute(s"DROP DATABASE IF EXISTS $bare WITH (FORCE)")
      c.createStatement().execute(s"CREATE DATABASE $bare")
    }.fold(fail(_), identity)
    val readerUrl = migrated.readerUrl.replaceFirst("/clad_gw_test_[0-9_]+", "/" + bare)
    val keys = keysDir()
    val cfg = Fixtures.approvedConfig()
    val log = Files.createTempFile("access", ".log")
    Files.writeString(log, "")
    val out = Files.createTempDirectory("bundle")
    BundleExport.write(out, Instant.now().minusSeconds(60), Instant.now(), None, readerUrl, Some(log), Map.empty,
      Files.createTempDirectory("anchors"), keys, cfg.governance, "personal_finance").fold(fail(_), identity)
    assert(Files.exists(out.resolve("bundle.json")))
    assert(Files.exists(out.resolve("infra.jsonl")))
    assert(!Files.exists(out.resolve("gil.jsonl")))
    assert(!Files.exists(out.resolve("audit.jsonl")))
  }
