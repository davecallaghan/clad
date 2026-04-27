package clad.config

import clad.core.*

object ConstraintAuthorizer:
  sealed trait AuthorizationError
  case class DomainNotAuthorized(agent: Agent, domain: Domain) extends AuthorizationError
  case class LevelNotAuthorized(agent: Agent, level: Level) extends AuthorizationError

  def authorize(
    context: AuthorizationContext,
    constraint: Constraint,
    domain: Domain
  ): Either[List[AuthorizationError], Constraint] =
    val errors = List.newBuilder[AuthorizationError]
    if !context.agent.authorizedDomains.contains(domain) then
      errors += DomainNotAuthorized(context.agent, domain)
    if !context.agent.authorizedLevels.contains(constraint.level) then
      errors += LevelNotAuthorized(context.agent, constraint.level)
    val errs = errors.result()
    if errs.nonEmpty then Left(errs)
    else Right(constraint)
