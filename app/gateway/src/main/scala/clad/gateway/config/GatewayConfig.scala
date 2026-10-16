package clad.gateway.config

import java.nio.file.Path

final case class GatewayConfig(governance: Governance, templates: Templates, attestations: Attestations, runtime: Runtime)

object GatewayConfig:
  def load(dir: Path): Either[String, GatewayConfig] = Runtime.load(dir.resolve("runtime.yaml")).flatMap(loadWith(dir, _))

  def loadWith(dir: Path, runtime: Runtime): Either[String, GatewayConfig] =
    for
      gov <- Governance.load(dir.resolve("constraints.yaml"))
      models <- Templates.loadModels(dir.resolve("models.yaml"))
      templates <- Templates.load(dir.resolve("prompts"), models, gov)
      attestations <- Attestations.load(dir.resolve("attestations.yaml"), gov.humans)
    yield GatewayConfig(gov, templates, attestations, runtime)
