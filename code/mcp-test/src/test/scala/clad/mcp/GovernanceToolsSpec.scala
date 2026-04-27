package clad.mcp

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.config.*
import clad.config.test.SampleConfigs

class GovernanceToolsSpec extends AnyFlatSpec with Matchers:

  "GovernanceTools.allDefinitions" should "return 6 tool definitions" in {
    GovernanceTools.allDefinitions.size shouldBe 6
  }

  it should "include evaluate_prompt tool" in {
    val tools = GovernanceTools.allDefinitions
    tools.exists(_.name == "evaluate_prompt") shouldBe true
  }

  it should "include all expected tool names" in {
    val tools = GovernanceTools.allDefinitions
    val names = tools.map(_.name).toSet
    names should contain allOf(
      "evaluate_prompt",
      "evaluate_output",
      "list_constraints",
      "verify_audit_chain",
      "get_governance_config",
      "check_authorization"
    )
  }

  "evaluate_prompt" should "return PASSED for clean prompt" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj(
      "prompt" -> "This is a clean prompt with no sensitive data",
      "metadata" -> ujson.Obj("logging" -> "enabled")
    )

    val result = GovernanceTools.execute("evaluate_prompt", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("PASSED")
  }

  it should "return FAILED for prompt with PII" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj(
      "prompt" -> "Patient SSN: 123-45-6789",
      "metadata" -> ujson.Obj("audit_logging" -> "enabled")
    )

    val result = GovernanceTools.execute("evaluate_prompt", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("FAILED")
    result.content.head.text should include("VIOLATED")
  }

  it should "include audit digest in result" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj(
      "prompt" -> "Clean prompt",
      "metadata" -> ujson.Obj("logging" -> "enabled")
    )

    val result = GovernanceTools.execute("evaluate_prompt", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("Audit Digest:")
  }

  "evaluate_output" should "return decision text" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj(
      "content" -> "This is output content",
      "metadata" -> ujson.Obj()
    )

    val result = GovernanceTools.execute("evaluate_output", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("Decision:")
  }

  "list_constraints" should "include Active Governance Constraints header" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj()

    val result = GovernanceTools.execute("list_constraints", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("Active Governance Constraints")
  }

  it should "include property names from config" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj()

    val result = GovernanceTools.execute("list_constraints", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("contains_pii")
    result.content.head.text should include("audit_logging")
  }

  "get_governance_config" should "include Governance Configuration header" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj()

    val result = GovernanceTools.execute("get_governance_config", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("Governance Configuration:")
  }

  it should "include config name" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj()

    val result = GovernanceTools.execute("get_governance_config", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("Healthcare Governance")
  }

  "verify_audit_chain" should "include Audit Chain text" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj()

    val result = GovernanceTools.execute("verify_audit_chain", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("Audit Chain")
  }

  "check_authorization" should "return GRANTED for authorized agent" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    // Use medical_writer with healthcare domain and enterprise level (from sample config)
    val args = ujson.Obj(
      "agent_id" -> "medical_writer",
      "domain" -> "healthcare",
      "level" -> "enterprise",
      "constraint_property" -> "contains_pii",
      "constraint_type" -> "prohibition"
    )

    val result = GovernanceTools.execute("check_authorization", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("GRANTED")
  }

  it should "return DENIED for unauthorized domain" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    // medical_writer not authorized for "finance" domain
    val args = ujson.Obj(
      "agent_id" -> "medical_writer",
      "domain" -> "finance",
      "level" -> "enterprise",
      "constraint_property" -> "contains_pii",
      "constraint_type" -> "prohibition"
    )

    val result = GovernanceTools.execute("check_authorization", args, engine, config)
    result.isError shouldBe false
    result.content.head.text should include("DENIED")
  }

  it should "return error for unknown agent" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj(
      "agent_id" -> "unknown_agent",
      "domain" -> "healthcare",
      "level" -> "enterprise",
      "constraint_property" -> "contains_pii",
      "constraint_type" -> "prohibition"
    )

    val result = GovernanceTools.execute("check_authorization", args, engine, config)
    result.isError shouldBe true
    result.content.head.text should include("not found")
  }

  "execute" should "return error for unknown tool" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val engine = ConfigLoader.buildEngine(config).toOption.get

    val args = ujson.Obj()

    val result = GovernanceTools.execute("unknown_tool", args, engine, config)
    result.isError shouldBe true
    result.content.head.text should include("Unknown tool")
  }
