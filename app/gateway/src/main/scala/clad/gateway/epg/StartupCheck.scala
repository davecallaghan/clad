package clad.gateway.epg

import clad.gateway.config.{GatewayConfig, Templates}
import clad.runtime.EvaluabilityClass
import java.time.Instant

/** Renders every template with its slot descriptions and evaluates it, so a template that cannot pass is found at startup. */
object StartupCheck:
  final case class Report(errors: Vector[String], warnings: Vector[String])

  val DeploymentStateKeys: Set[String] = Set("model_registry_status")

  def run(cfg: GatewayConfig, epg: Epg): Report =
    val at = Instant.now()
    val found = cfg.templates.all.flatMap { t =>
      val evaluated = Templates.render(t, t.slots).flatMap(epg.evaluate(t, _, t.model, at))
      evaluated match
        case Left(e) => Vector(Left(s"${t.id}: $e"))
        case Right(r) => r.entries.filterNot(_.satisfied).map { e =>
          val prop = e.constraint.property.value
          e.evaluabilityClass match
            case EvaluabilityClass.Procedural => Right(s"${t.id}: $prop not attested")
            case _ =>
              val deployment = cfg.governance.checkers.get(prop).exists(c => c.kind == "structural" && DeploymentStateKeys.contains(c.key))
              if deployment then Right(s"${t.id}: $prop unsatisfied by deployment state (model ${t.model} is ${cfg.templates.models.statusOf(t.model)})")
              else Left(s"${t.id}: $prop violated by the template text")
        }
    }
    // Left = error, Right = warning; deployment warnings and procedural warnings share the Right side.
    Report(found.collect { case Left(e) => e }, found.collect { case Right(w) => w })
