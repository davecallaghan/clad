package clad.config

import clad.core.*
import java.time.Instant

case class Agent(
  id: String,
  name: String,
  authorizedDomains: Set[Domain],
  authorizedLevels: Set[Level]
)

case class AuthorizationContext(
  agent: Agent,
  timestamp: Instant
)
