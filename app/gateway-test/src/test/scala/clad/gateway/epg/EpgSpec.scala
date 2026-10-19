package clad.gateway.epg

import clad.gateway.config.Fixtures
import org.scalatest.funsuite.AnyFunSuite
import java.time.Instant

class EpgSpec extends AnyFunSuite:
  val cfg = Fixtures.config
  val epg = Epg.build(cfg).fold(fail(_), identity)
  val at = Instant.parse("2026-10-16T12:00:00Z")
  val answer = cfg.templates.get("agent.answer").get

  test("one entry per constraint on the path; a prompt with an SSN fails contains_pii") {
    val r = epg.evaluate(answer, answer.text + " Call 123-45-6789.", answer.model, at).fold(fail(_), identity)
    assert(r.entries.size == cfg.governance.path("agent").size)
    val pii = r.entries.find(_.constraint.property.value == "contains_pii").get
    assert(!pii.satisfied && r.blocked)
  }

  test("an unattested template gives an unsatisfied procedural entry; pending models fail approved_model") {
    val r = epg.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    assert(r.entries.exists(e => e.constraint.property.value == "compliance_review" && !e.satisfied))
    assert(r.entries.exists(e => e.constraint.property.value == "approved_model" && !e.satisfied))
  }

  test("clad's own re-evaluation of the stored artifact agrees with the record") {
    val r = epg.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    val art = ujson.read(r.artifactJson)
    val hc = clad.config.ConfigLoader.loadFromString(r.configJson).flatMap(clad.config.ConfigLoader.buildHierarchy).fold(e => fail(e.mkString), identity)
    val ev = clad.evaluation.EvidenceSet(r.entries.collect { case clad.runtime.AuditEntry(c, _, _, s, clad.evaluation.ProceduralDetail(a, t, why), _) =>
      c.property -> clad.evaluation.ProceduralEvidence(c.property, a, s, t, why) }.toMap)
    val report = clad.evaluation.PromptEvaluator.evaluate(clad.evaluation.PromptArtifact(art("content").str,
      art("metadata").obj.map((k, v) => k -> v.str).toMap), hc, ev, clad.core.Level.Project, at).fold(e => fail(e.toString), identity)
    val expected = report.mechanicalResults.map(c => c.constraint -> c.satisfied).toMap
    val recorded = r.entries.filter(_.evaluabilityClass == clad.runtime.EvaluabilityClass.Mechanical).map(e => e.constraint -> e.satisfied).toMap
    assert(expected == recorded)
  }

  test("the startup check passes every template's text; approvals and attestations are warnings") {
    val rep = StartupCheck.run(cfg, epg)
    assert(rep.errors.isEmpty, rep.errors)
    assert(rep.warnings.exists(_.contains("approved_model")) && rep.warnings.exists(_.contains("attest")))
  }

  test("the startup check refuses an unapproved classifier and a classifier constraint on a passthrough template") {
    val approved = Fixtures.approvedConfig()
    val models = approved.templates.models
    val unapproved = approved.copy(templates = clad.gateway.config.Templates(approved.templates.byId,
      models.copy(statuses = models.statuses + (approved.roc.classifier.model -> "pending"))))
    val e1 = StartupCheck.run(unapproved, Epg.build(unapproved).fold(fail(_), identity)).errors
    assert(e1.exists(e => e.contains("classifier llama_guard") && e.contains("not approved")), e1)
    val routed = approved.copy(roc = approved.roc.copy(constraints = approved.roc.constraints.map(c =>
      if c.property == "unsafe_content" then c.copy(projects = c.projects + "router") else c)))
    val e2 = StartupCheck.run(routed, Epg.build(routed).fold(fail(_), identity)).errors
    assert(e2.exists(e => e.contains("router.jev") && e.contains("unsafe_content")), e2)
  }

  test("the same text evaluated with a pending and then an approved model is two artifacts, not one stored body") {
    val approved = Fixtures.approvedConfig()
    val e2 = Epg.build(approved).fold(fail(_), identity)
    val pending = epg.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    val later = e2.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    assert(pending.artifactDigest != later.artifactDigest)
    assert(ujson.read(later.artifactJson)("metadata")("model_registry_status").str == "approved")
  }

  test("the artifact digest is PromptArtifact.digest of the stored content and metadata") {
    val r = epg.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    val stored = ujson.read(r.artifactJson)
    assert(r.artifactDigest == clad.evaluation.PromptArtifact(stored("content").str,
      stored("metadata").obj.map((k, v) => k -> v.str).toMap).digest)
  }

  test("an attested template carries attestor and time into the procedural detail") {
    val att = clad.gateway.config.Attestations(Vector(clad.gateway.config.Attestation(answer.id, answer.version, "david", at, "reviewed")))
    val attEpg = Epg.build(cfg.copy(attestations = att)).fold(fail(_), identity)
    val r = attEpg.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    val e = r.entries.find(_.constraint.property.value == "compliance_review").get
    assert(e.satisfied && e.detail == clad.evaluation.ProceduralDetail("david", at, "reviewed"))
  }

  test("a template whose text violates a text constraint is a startup error, not a warning") {
    val bad = answer.copy(text = answer.text + " 123-45-6789")
    val badCfg = cfg.copy(templates = clad.gateway.config.Templates(cfg.templates.byId + (bad.id -> bad), cfg.templates.models))
    val rep = StartupCheck.run(badCfg, Epg.build(badCfg).fold(fail(_), identity))
    assert(rep.errors.exists(e => e.contains("agent.answer") && e.contains("contains_pii")), rep.errors)
    assert(!rep.warnings.exists(_.contains("contains_pii")))
  }
