package clad.api

import cats.effect.IO
import cats.effect.kernel.Ref
import org.http4s.*
import org.http4s.dsl.io.*
import clad.core.*
import clad.runtime.*
import clad.output.*
import clad.config.*
import clad.api.JsonSupport.given
import java.nio.file.Path
import java.time.{Duration, Instant}

class GovernanceRoutes(
  engineRef: Ref[IO, GovernanceEngine],
  configRef: Ref[IO, GovernanceConfig],
  configPath: Path,
  startTime: Instant
):

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req @ POST -> Root / "api" / "v1" / "evaluate" =>
      for
        body <- req.as[EvaluateRequest]
        engine <- engineRef.get
        result = engine.evaluate(body.prompt, body.metadata)
        response <- result match
          case Right(report) =>
            val unsatisfied = report.evaluation.unsatisfied.toSeq.map { cr =>
              ConstraintSummary(
                property = cr.constraint.property.value,
                constraintType = cr.constraint match
                  case _: Constraint.Obligation => "obligation"
                  case _: Constraint.Prohibition => "prohibition"
                ,
                level = cr.constraint.level.toString.toLowerCase
              )
            }
            val response = EvaluateResponse(
              allSatisfied = report.evaluation.allSatisfied,
              totalConstraints = report.evaluation.totalCount,
              satisfiedCount = report.evaluation.satisfiedCount,
              unsatisfied = unsatisfied,
              auditDigest = report.audit.artifactDigest
            )
            Ok(response)
          case Left(GovernanceEngine.MissingEvidence(props)) =>
            val error = ErrorResponse(
              error = s"Missing evidence for properties: ${props.map(_.value).mkString(", ")}",
              code = "MISSING_EVIDENCE"
            )
            BadRequest(error)
      yield response

    case req @ POST -> Root / "api" / "v1" / "evaluate" / "output" =>
      for
        body <- req.as[OutputEvaluateRequest]
        artifact = OutputArtifact(body.content, body.metadata)
        report = OutputEvaluator.evaluate(artifact, OutputConstraintSet(), Instant.now())
        unsatisfied = report.unsatisfied.map { r =>
          OutputConstraintSummary(
            property = r.property.value,
            constraintType = r match
              case _: DeterministicResult => "deterministic"
              case _: ClassifierResult => "classifier"
              case _: CompositeResult => "composite"
            ,
            riskTier = "standard"
          )
        }
        response = OutputEvaluateResponse(
          decision = report.decision match
            case PipelineDecision.Pass => "pass"
            case PipelineDecision.Flag(_) => "flag"
            case PipelineDecision.Block(_) => "block"
          ,
          totalConstraints = report.totalCount,
          satisfiedCount = report.satisfiedCount,
          unsatisfied = unsatisfied
        )
        result <- Ok(response)
      yield result

    case GET -> Root / "api" / "v1" / "config" =>
      for
        config <- configRef.get
        summary = ConfigSummary(
          name = config.name,
          version = config.version,
          constraintCount = config.constraints.enterprise.size + config.constraints.department.size + config.constraints.project.size,
          checkerCount = config.checkers.size,
          agentCount = config.agents.size
        )
        result <- Ok(summary)
      yield result

    case POST -> Root / "api" / "v1" / "config" / "reload" =>
      IO.delay(ConfigLoader.loadFromFile(configPath)).flatMap {
        case Right(newConfig) =>
          IO.delay(ConfigLoader.buildEngine(newConfig)).flatMap {
            case Right(newEngine) =>
              for
                _ <- configRef.set(newConfig)
                _ <- engineRef.set(newEngine)
                resp <- Ok(ReloadResponse("reloaded", newConfig.name, newConfig.version))
              yield resp
            case Left(errors) =>
              Ok(ReloadResponse("failed", errors = errors.map(_.toString)))
          }
        case Left(errors) =>
          Ok(ReloadResponse("failed", errors = errors.map(_.toString)))
      }

    case GET -> Root / "api" / "v1" / "constraints" =>
      for
        config <- configRef.get
        allConstraints = config.constraints.enterprise ++ config.constraints.department ++ config.constraints.project
        summaries = allConstraints.map { cc =>
          ConstraintSummary(
            property = cc.property,
            constraintType = cc.constraintType.toLowerCase,
            level = cc.level.toLowerCase
          )
        }
        response = ConstraintListResponse(
          constraints = summaries,
          total = summaries.size
        )
        result <- Ok(response)
      yield result

    case GET -> Root / "api" / "v1" / "health" =>
      for
        config <- configRef.get
        uptime = Duration.between(startTime, Instant.now()).toString
        response = HealthResponse(
          status = "healthy",
          configLoaded = true,
          configName = config.name,
          configVersion = config.version,
          uptime = uptime
        )
        result <- Ok(response)
      yield result
  }
