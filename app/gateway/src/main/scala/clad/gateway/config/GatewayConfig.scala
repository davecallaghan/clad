package clad.gateway.config

import clad.gateway.pipeline.InputDetectorConfig
import clad.gateway.roc.RocConfig
import java.nio.file.Path

final case class GatewayConfig(governance: Governance, templates: Templates, attestations: Attestations, runtime: Runtime,
                          roc: RocConfig, inputDetectors: InputDetectorConfig)

object GatewayConfig:
  def load(dir: Path): Either[String, GatewayConfig] = Runtime.load(dir.resolve("runtime.yaml")).flatMap(loadWith(dir, _))

  def loadWith(dir: Path, runtime: Runtime): Either[String, GatewayConfig] =
    for
      gov <- Governance.load(dir.resolve("constraints.yaml"))
      models <- Templates.loadModels(dir.resolve("models.yaml"), gov)
      templates <- Templates.load(dir.resolve("prompts"), models, gov)
      attestations <- Attestations.load(dir.resolve("attestations.yaml"), gov.humans)
      roc <- RocConfig.load(dir, gov)
      inputs <- InputDetectorConfig.load(dir.resolve("input_detectors.yaml"), gov)
    yield GatewayConfig(gov, templates, attestations, runtime, roc, inputs)
