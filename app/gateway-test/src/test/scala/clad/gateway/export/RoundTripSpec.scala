package clad.gateway.`export`

import clad.audit.ChainAnchorStore
import clad.gateway.config.Fixtures
import clad.gateway.epg.Epg
import clad.gateway.keys.Keys
import clad.gateway.roc.LlamaGuard
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

  /** The classifier upstream: every request gets a `safe` verdict with its logprob. */
  private final class SafeVerdict extends Upstream:
    val calls = scala.collection.mutable.ArrayBuffer.empty[String]
    def complete(body: ujson.Obj, iid: String): Either[String, ujson.Value] =
      calls.synchronized(calls += iid)
      Right(ujson.Obj("choices" -> ujson.Arr(ujson.Obj("message" -> ujson.Obj("role" -> "assistant", "content" -> "safe"),
        "logprobs" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("token" -> "safe", "logprob" -> -0.001)))))))

  private def egressLine(iid: String, uri: String): String =
    ujson.write(ujson.Obj("ts" -> Instant.now().getEpochSecond.toDouble, "status" -> 200, "request" -> ujson.Obj(
      "method" -> "POST", "uri" -> uri, "host" -> "egress:8081", "headers" -> ujson.Obj("X-Clad-Interaction-Id" -> ujson.Arr(iid)))))

  test("records written by the gateway, ROC included, export to a bundle that conformance verifies") {
    val db = TestDatabase.fresh()
    val keys = keysDir()
    val anchors = Files.createTempDirectory("anchors")
    val cfg = Fixtures.approvedConfig("assistant.general")
    val comp = Keys.load(keys, "components").toOption.get
    val sup = Keys.load(keys, "supervisor").toOption.get
    val store = PgStore(db.writerUrl, cfg.runtime.chainLockKey, ChainAnchorStore(anchors), comp)
    val rows = ujson.read("""[{"scenario": "base", "tax_drag_ratio": 0.0666328857}]""")
    val ungrounded = ok("The base scenario's tax drag is 9.99 %.")
    // 3 answers (agent.sql is unattested, so EPG blocks it before upstream), a grounded answer, an ungrounded answer
    // and its retry, then the Standard call
    val up = FakeUpstream(Right(ok("ok")), Right(ok("ok")), Right(ok("ok")),
      Right(ok("The base scenario's tax drag is 6.66 %.")), Right(ungrounded), Right(ungrounded), Right(ok("Hello there.")))
    val classifier = SafeVerdict()
    val pass = FakePassthrough()
    val background = QueuedExecutor()
    val containment = Files.createTempFile("c", ".json")
    Files.writeString(containment, "{}")   // nothing contained; an empty file would be unreadable and block every call
    val gw = Gateway(cfg, Epg.build(cfg).toOption.get, store, comp, sup, up, pass, containment, () => Instant.now(),
      LlamaGuard(classifier, cfg.roc.classifier), background)
    val start = Instant.now().minusSeconds(60)
    (1 to 3).foreach(_ => gw.complete(answerRequest()))
    gw.complete(answerRequest(template = "agent.sql"))
    // a contained project: the gateway's block record names a config that must be in the bundle
    assert(gw.contain("agent", "incident") == Right(()))
    gw.complete(answerRequest())
    assert(gw.release("agent") == Right(()))
    assert(gw.complete(answerRequest(evidence = Some(rows))).isInstanceOf[GatewayResult.Delivered])
    gw.complete(answerRequest(evidence = Some(rows))) match
      case GatewayResult.Delivered(_, "fallback:safe_response", _) => ()
      case other                                                   => fail(s"ungrounded answer gave $other")
    gw.complete(GovernedRequest("assistant.general", "api", None, Vector(ujson.Obj("role" -> "user", "content" -> "hi")),
      Map.empty, None, None, ujson.Obj()))
    background.runAll()
    gw.passthrough("jev", "", "router.jev", "api", None, "{}") match
      case GatewayResult.Delivered(_, "pass", _) => ()
      case other                                 => fail(s"router gave $other")
    val log = Files.createTempFile("access", ".log")
    val lines = up.calls.map((_, iid) => egressLine(iid, "/v1/openai/chat/completions")) ++
      classifier.calls.map(egressLine(_, "/v1/openai/chat/completions")) ++ pass.calls.map(c => egressLine(c._4, "/v1/jev/"))
    Files.writeString(log, lines.mkString("\n") + "\n")
    val out = Files.createTempDirectory("bundle")
    BundleExport.write(out, start, Instant.now().plusSeconds(60), None, db.readerUrl, Some(log),
      Map("8081" -> "api.deepinfra.com"), anchors, keys, cfg.governance, "personal_finance").fold(fail(_), identity)
    val results = clad.conformance.BundleReader.read(out).map(b => clad.conformance.Conformance.evaluate(b, None, None)).fold(fail(_), identity)
    info(results.map(r => s"${r.id} -> ${r.verdict} ${r.reason}").mkString("\n"))
    for id <- Seq("R-AI2", "R-AI3", "R-AI4", "R-ANCHOR", "R-AI5", "R-AI6", "R-EA1", "R-EA2", "R-EA3", "R-AX5", "R-CFG-PARSE",
                  "R-EPG-REEVAL", "R-EPG-BLOCK", "R-ROC-PRESENT", "R-ROC-DECISION", "R-ROC-THRESH") do
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

  test("export refuses a directory that already holds files") {
    val db = TestDatabase.fresh()
    val out = Files.createTempDirectory("bundle")
    Files.writeString(out.resolve("stale.jsonl"), "x\n")
    val r = BundleExport.write(out, Instant.now().minusSeconds(60), Instant.now(), None, db.readerUrl, None, Map.empty,
      Files.createTempDirectory("anchors"), keysDir(), Fixtures.approvedConfig().governance, "personal_finance")
    assert(r.left.exists(_.contains("not empty")), r)
    assert(!Files.exists(out.resolve("bundle.json")))
  }
