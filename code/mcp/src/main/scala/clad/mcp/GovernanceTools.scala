package clad.mcp

import clad.runtime.*
import clad.output.*
import clad.config.*
import clad.core.*
import java.time.Instant

object GovernanceTools:

  // Tool definitions with JSON Schema
  def allDefinitions: Seq[McpProtocol.ToolDefinition] = Seq(
    McpProtocol.ToolDefinition(
      name = "evaluate_prompt",
      description = "Evaluate a prompt against governance constraints. Returns evaluation results with satisfied/violated constraints and audit digest.",
      inputSchema = ujson.Obj(
        "type" -> "object",
        "properties" -> ujson.Obj(
          "prompt" -> ujson.Obj(
            "type" -> "string",
            "description" -> "The prompt text to evaluate"
          ),
          "metadata" -> ujson.Obj(
            "type" -> "object",
            "description" -> "Optional metadata as key-value pairs (e.g., {\"audit_logging\": \"enabled\"})",
            "additionalProperties" -> ujson.Obj("type" -> "string")
          )
        ),
        "required" -> ujson.Arr("prompt")
      )
    ),
    McpProtocol.ToolDefinition(
      name = "evaluate_output",
      description = "Evaluate generated output against output-stage constraints. Returns pipeline decision (Pass/Flag/Block) and constraint results.",
      inputSchema = ujson.Obj(
        "type" -> "object",
        "properties" -> ujson.Obj(
          "content" -> ujson.Obj(
            "type" -> "string",
            "description" -> "The generated output content to evaluate"
          ),
          "metadata" -> ujson.Obj(
            "type" -> "object",
            "description" -> "Optional metadata as key-value pairs",
            "additionalProperties" -> ujson.Obj("type" -> "string")
          )
        ),
        "required" -> ujson.Arr("content")
      )
    ),
    McpProtocol.ToolDefinition(
      name = "list_constraints",
      description = "List all active governance constraints in the current configuration, organized by level (Enterprise/Department/Project).",
      inputSchema = ujson.Obj(
        "type" -> "object",
        "properties" -> ujson.Obj()
      )
    ),
    McpProtocol.ToolDefinition(
      name = "verify_audit_chain",
      description = "Verify the audit chain integrity. Returns information about audit chain status and capabilities.",
      inputSchema = ujson.Obj(
        "type" -> "object",
        "properties" -> ujson.Obj()
      )
    ),
    McpProtocol.ToolDefinition(
      name = "get_governance_config",
      description = "Retrieve current governance configuration summary including name, version, constraint counts, and agent information.",
      inputSchema = ujson.Obj(
        "type" -> "object",
        "properties" -> ujson.Obj()
      )
    ),
    McpProtocol.ToolDefinition(
      name = "check_authorization",
      description = "Check if an agent is authorized to modify a constraint in a specific domain and level.",
      inputSchema = ujson.Obj(
        "type" -> "object",
        "properties" -> ujson.Obj(
          "agent_id" -> ujson.Obj(
            "type" -> "string",
            "description" -> "The agent identifier to check"
          ),
          "domain" -> ujson.Obj(
            "type" -> "string",
            "description" -> "The domain to check authorization for"
          ),
          "level" -> ujson.Obj(
            "type" -> "string",
            "description" -> "The constraint level (enterprise/department/project)"
          ),
          "constraint_property" -> ujson.Obj(
            "type" -> "string",
            "description" -> "The constraint property identifier"
          ),
          "constraint_type" -> ujson.Obj(
            "type" -> "string",
            "description" -> "The constraint type (obligation/prohibition)"
          )
        ),
        "required" -> ujson.Arr("agent_id", "domain", "level", "constraint_property", "constraint_type")
      )
    )
  )

  // Execute a tool by name
  def execute(
    toolName: String,
    args: ujson.Value,
    engine: GovernanceEngine,
    config: GovernanceConfig
  ): McpProtocol.ToolResult =
    try
      toolName match
        case "evaluate_prompt" => evaluatePrompt(args, engine)
        case "evaluate_output" => evaluateOutput(args)
        case "list_constraints" => listConstraints(config)
        case "verify_audit_chain" => verifyAuditChain()
        case "get_governance_config" => getGovernanceConfig(config)
        case "check_authorization" => checkAuthorization(args, config)
        case unknown =>
          McpProtocol.ToolResult(
            content = Seq(McpProtocol.ContentBlock(text = s"Unknown tool: $unknown")),
            isError = true
          )
    catch
      case e: Exception =>
        McpProtocol.ToolResult(
          content = Seq(McpProtocol.ContentBlock(text = s"Error executing tool: ${e.getMessage}")),
          isError = true
        )

  private def evaluatePrompt(args: ujson.Value, engine: GovernanceEngine): McpProtocol.ToolResult =
    val prompt = args("prompt").str
    val metadata = args.obj.get("metadata")
      .map(_.obj.map { case (k, v) => k -> v.str }.toMap)
      .getOrElse(Map.empty)

    engine.evaluate(prompt, metadata) match
      case Left(GovernanceEngine.MissingEvidence(props)) =>
        McpProtocol.ToolResult(
          content = Seq(McpProtocol.ContentBlock(
            text = s"FAILED: Missing evidence for properties: ${props.map(_.value).mkString(", ")}"
          )),
          isError = true
        )
      case Right(report) =>
        val status = if report.evaluation.allSatisfied then "PASSED" else "FAILED"
        val resultText = buildEvaluationResultText(report)
        McpProtocol.ToolResult(
          content = Seq(McpProtocol.ContentBlock(text = resultText)),
          isError = false
        )

  private def buildEvaluationResultText(report: GovernanceReport): String =
    val sb = new StringBuilder()
    sb.append(s"Evaluation Status: ${if report.evaluation.allSatisfied then "PASSED" else "FAILED"}\n")
    sb.append(s"Satisfied: ${report.evaluation.satisfiedCount}/${report.evaluation.totalCount}\n\n")

    sb.append("Constraint Results:\n")
    report.evaluation.results.toSeq.sortBy(_.constraint.property.value).foreach { cr =>
      val symbol = if cr.satisfied then "✓" else "✗"
      val constraintType = cr.constraint match
        case _: Constraint.Obligation => "Obligation"
        case _: Constraint.Prohibition => "Prohibition"
      sb.append(s"$symbol ${cr.constraint.property.value} ($constraintType, ${cr.constraint.level}): ")
      sb.append(if cr.satisfied then "SATISFIED" else "VIOLATED")
      sb.append(s" [v${cr.version}]\n")
    }

    if report.evaluation.unsatisfied.nonEmpty then
      sb.append("\nViolated Constraints:\n")
      report.evaluation.unsatisfied.toSeq.sortBy(_.constraint.property.value).foreach { cr =>
        sb.append(s"- ${cr.constraint.property.value}: ")
        cr.detail match
          case clad.evaluation.MechanicalDetail(detected) =>
            sb.append(s"Property detected = $detected\n")
          case clad.evaluation.ProceduralDetail(attestor, _, rationale) =>
            sb.append(s"Attestor: $attestor, Rationale: $rationale\n")
      }

    sb.append(s"\nAudit Digest: ${report.audit.digest}\n")
    sb.append(s"Engine Version: ${report.engineVersion}\n")
    sb.append(s"Config Digest: ${report.configDigest}\n")

    sb.toString

  private def evaluateOutput(args: ujson.Value): McpProtocol.ToolResult =
    val content = args("content").str
    val metadata = args.obj.get("metadata")
      .map(_.obj.map { case (k, v) => k -> v.str }.toMap)
      .getOrElse(Map.empty)

    val artifact = OutputArtifact(content, metadata)
    val constraints = OutputConstraintSet()
    val timestamp = Instant.now()

    val report = OutputEvaluator.evaluate(artifact, constraints, timestamp)

    val sb = new StringBuilder()
    sb.append(s"Output Evaluation Result\n")
    sb.append(s"Decision: ${report.decision}\n")
    sb.append(s"Satisfied: ${report.satisfiedCount}/${report.totalCount}\n")

    if report.unsatisfied.nonEmpty then
      sb.append("\nUnsatisfied Constraints:\n")
      report.unsatisfied.foreach { result =>
        sb.append(s"- ${result.property.value}: not satisfied\n")
      }

    McpProtocol.ToolResult(
      content = Seq(McpProtocol.ContentBlock(text = sb.toString)),
      isError = false
    )

  private def listConstraints(config: GovernanceConfig): McpProtocol.ToolResult =
    val sb = new StringBuilder()
    sb.append("Active Governance Constraints\n")
    sb.append("=" * 40 + "\n\n")

    def listLevel(levelName: String, constraints: Seq[ConstraintConfig]): Unit =
      if constraints.nonEmpty then
        sb.append(s"$levelName Level:\n")
        constraints.foreach { cc =>
          sb.append(s"  - ${cc.property} (${cc.constraintType}, domain: ${cc.domain})\n")
          sb.append(s"    Version: ${cc.version}, Evaluability: ${cc.evaluability}\n")
        }
        sb.append("\n")

    listLevel("Enterprise", config.constraints.enterprise)
    listLevel("Department", config.constraints.department)
    listLevel("Project", config.constraints.project)

    val totalCount = config.constraints.enterprise.size +
                     config.constraints.department.size +
                     config.constraints.project.size
    sb.append(s"Total Constraints: $totalCount\n")

    McpProtocol.ToolResult(
      content = Seq(McpProtocol.ContentBlock(text = sb.toString)),
      isError = false
    )

  private def verifyAuditChain(): McpProtocol.ToolResult =
    val text = """Audit Chain Verification
========================================

Status: Session-based audit tracking active

The MCP governance session maintains audit records for each evaluation:
- Each prompt evaluation generates an AuditRecord with digest
- AuditRecord includes constraint results, timestamps, and configuration digest
- Digests use SHA-256 for content integrity verification

Note: Audit records are maintained in-memory during the MCP session.
For persistent audit chain storage and verification, integrate with an
external audit logging system.

Current session does not persist audit chain across restarts.
"""
    McpProtocol.ToolResult(
      content = Seq(McpProtocol.ContentBlock(text = text)),
      isError = false
    )

  private def getGovernanceConfig(config: GovernanceConfig): McpProtocol.ToolResult =
    val sb = new StringBuilder()
    sb.append("Governance Configuration:\n")
    sb.append("=" * 40 + "\n\n")
    sb.append(s"Name: ${config.name}\n")
    sb.append(s"Version: ${config.version}\n\n")

    sb.append("Constraints:\n")
    sb.append(s"  Enterprise: ${config.constraints.enterprise.size}\n")
    sb.append(s"  Department: ${config.constraints.department.size}\n")
    sb.append(s"  Project: ${config.constraints.project.size}\n")
    sb.append(s"  Total: ${config.constraints.enterprise.size + config.constraints.department.size + config.constraints.project.size}\n\n")

    sb.append(s"Checkers: ${config.checkers.size}\n")
    sb.append(s"Output Constraints: ${config.outputConstraints.size}\n")
    sb.append(s"Thresholds: ${config.thresholds.size}\n\n")

    if config.agents.nonEmpty then
      sb.append("Agents:\n")
      config.agents.foreach { agent =>
        sb.append(s"  - ${agent.name} (${agent.id})\n")
        sb.append(s"    Domains: ${agent.authorizedDomains.mkString(", ")}\n")
        sb.append(s"    Levels: ${agent.authorizedLevels.mkString(", ")}\n")
      }

    McpProtocol.ToolResult(
      content = Seq(McpProtocol.ContentBlock(text = sb.toString)),
      isError = false
    )

  private def checkAuthorization(args: ujson.Value, config: GovernanceConfig): McpProtocol.ToolResult =
    val agentId = args("agent_id").str
    val domainStr = args("domain").str
    val levelStr = args("level").str
    val propertyStr = args("constraint_property").str
    val constraintTypeStr = args("constraint_type").str

    // Find agent in config
    config.agents.find(_.id == agentId) match
      case None =>
        McpProtocol.ToolResult(
          content = Seq(McpProtocol.ContentBlock(text = s"Error: Agent '$agentId' not found in configuration")),
          isError = true
        )
      case Some(agentConfig) =>
        // Build agent
        val agents = ConfigLoader.buildAgents(config)
        agents.find(_.id == agentId) match
          case None =>
            McpProtocol.ToolResult(
              content = Seq(McpProtocol.ContentBlock(text = s"Error: Failed to build agent '$agentId'")),
              isError = true
            )
          case Some(agent) =>
            // Parse level
            val level = levelStr.toLowerCase match
              case "enterprise" => Level.Enterprise
              case "department" => Level.Department
              case "project" => Level.Project
              case _ =>
                return McpProtocol.ToolResult(
                  content = Seq(McpProtocol.ContentBlock(text = s"Error: Invalid level '$levelStr'")),
                  isError = true
                )

            // Build constraint
            val property = PropertyId.unsafe(propertyStr)
            val constraint = constraintTypeStr.toLowerCase match
              case "obligation" => Constraint.Obligation(property, level)
              case "prohibition" => Constraint.Prohibition(property, level)
              case _ =>
                return McpProtocol.ToolResult(
                  content = Seq(McpProtocol.ContentBlock(text = s"Error: Invalid constraint type '$constraintTypeStr'")),
                  isError = true
                )

            val domain = Domain(domainStr)
            val context = AuthorizationContext(agent, Instant.now())

            ConstraintAuthorizer.authorize(context, constraint, domain) match
              case Left(errors) =>
                val sb = new StringBuilder()
                sb.append(s"Authorization DENIED for agent '${agent.name}' (${agent.id})\n\n")
                sb.append("Reasons:\n")
                errors.foreach {
                  case ConstraintAuthorizer.DomainNotAuthorized(_, d) =>
                    sb.append(s"- Domain '${d.value}' not in authorized domains: ${agent.authorizedDomains.map(_.value).mkString(", ")}\n")
                  case ConstraintAuthorizer.LevelNotAuthorized(_, l) =>
                    sb.append(s"- Level '$l' not in authorized levels: ${agent.authorizedLevels.mkString(", ")}\n")
                }
                McpProtocol.ToolResult(
                  content = Seq(McpProtocol.ContentBlock(text = sb.toString)),
                  isError = false
                )
              case Right(_) =>
                val text = s"""Authorization GRANTED for agent '${agent.name}' (${agent.id})

Constraint: ${property.value} ($constraintTypeStr)
Domain: ${domain.value}
Level: $level

Agent is authorized to modify this constraint.
"""
                McpProtocol.ToolResult(
                  content = Seq(McpProtocol.ContentBlock(text = text)),
                  isError = false
                )
