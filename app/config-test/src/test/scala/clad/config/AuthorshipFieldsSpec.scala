package clad.config

import clad.core.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AuthorshipFieldsSpec extends AnyFunSuite with Matchers:
  private val json = """{
    "name": "pf", "version": "1", "project": "agent",
    "centralApprovers": ["david"], "criticalDomains": ["security"],
    "agents": [{"id": "david", "name": "David", "authorizedDomains": ["security"], "authorizedLevels": ["enterprise"], "kind": "human"},
               {"id": "cline", "name": "Cline", "authorizedDomains": ["security"], "authorizedLevels": ["enterprise"], "kind": "agent"}],
    "constraints": {"enterprise": [
      {"property": "contains_pii", "constraintType": "prohibition", "level": "enterprise", "domain": "security",
       "version": "1", "evaluability": "mechanical", "author": "cline", "approver": "david"},
      {"property": "compliance_review", "constraintType": "obligation", "level": "enterprise", "domain": "security",
       "version": "1", "evaluability": "procedural", "author": "cline", "approver": "david"}],
      "department": [], "project": []},
    "checkers": [{"property": "contains_pii", "checkerType": "regex", "config": {"patterns": "\\d{3}-\\d{2}-\\d{4}"}}],
    "thresholds": [{"property": "unsafe_content", "flagThreshold": 0.4, "riskTier": "Critical", "owner": "david", "level": "project"}]
  }"""

  test("new fields load"):
    val c = ConfigLoader.loadFromString(json).toOption.get
    c.project shouldBe "agent"
    c.centralApprovers shouldBe Seq("david")
    c.agents.map(_.kind) shouldBe Seq("human", "agent")
    c.constraints.enterprise.map(_.approver).distinct shouldBe Seq("david")
    c.thresholds.map(_.level) shouldBe Seq("project")

  test("old JSON without the fields still loads with defaults"):
    val old = """{"name": "x", "version": "1"}"""
    val c = ConfigLoader.loadFromString(old).toOption.get
    c.project shouldBe ""
    c.centralApprovers shouldBe Seq.empty

  test("buildHierarchy succeeds with a procedural constraint and no evidence"):
    val c = ConfigLoader.loadFromString(json).toOption.get
    ConfigLoader.buildEngine(c).isLeft shouldBe true   // EvidenceGap: why buildHierarchy exists
    val eh = ConfigLoader.buildHierarchy(c).toOption.get
    eh.proceduralAt(Level.Project).map(_.constraint.property.value) shouldBe Set("compliance_review")
    eh.mechanicalAt(Level.Project).map(_.constraint.property.value) shouldBe Set("contains_pii")
