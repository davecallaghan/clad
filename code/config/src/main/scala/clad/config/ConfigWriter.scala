package clad.config

import upickle.default.*

object ConfigWriter:
  private given ReadWriter[AgentConfig] = macroRW
  private given ReadWriter[ConstraintConfig] = macroRW
  private given ReadWriter[ConstraintHierarchyConfig] = macroRW
  private given ReadWriter[CheckerBinding] = macroRW
  private given ReadWriter[OutputConstraintConfig] = macroRW
  private given ReadWriter[ThresholdConfig] = macroRW
  private given ReadWriter[FailurePostureConfig] = macroRW
  private given ReadWriter[GovernanceConfig] = macroRW

  def toJson(config: GovernanceConfig): String = write(config)
  def toPrettyJson(config: GovernanceConfig): String = write(config, indent = 2)
