package clad.config

import clad.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class AgentSpec extends AnyFlatSpec with Matchers:

  val safety: Domain = Domain("safety")
  val privacy: Domain = Domain("privacy")
  val security: Domain = Domain("security")

  val securityTeam = Agent(
    "security_team",
    "Security Team",
    Set(safety, security),
    Set(Level.Enterprise, Level.Department)
  )

  val complianceLead = Agent(
    "compliance_lead",
    "Compliance Lead",
    Set(privacy),
    Set(Level.Enterprise)
  )

  "Agent" should "store id, name, domains, and levels" in {
    securityTeam.id shouldBe "security_team"
    securityTeam.name shouldBe "Security Team"
    securityTeam.authorizedDomains shouldBe Set(safety, security)
    securityTeam.authorizedLevels shouldBe Set(Level.Enterprise, Level.Department)
  }

  "AuthorizationContext" should "carry agent and timestamp" in {
    val timestamp = Instant.now()
    val context = AuthorizationContext(securityTeam, timestamp)
    context.agent shouldBe securityTeam
    context.timestamp shouldBe timestamp
  }

  "ConstraintAuthorizer" should "authorize when domain and level match" in {
    val context = AuthorizationContext(securityTeam, Instant.now())
    val constraint = Constraint.Obligation(PropertyId.unsafe("encryption_required"), Level.Enterprise)

    val result = ConstraintAuthorizer.authorize(context, constraint, security)
    result shouldBe Right(constraint)
  }

  it should "reject unauthorized domain" in {
    val context = AuthorizationContext(securityTeam, Instant.now())
    val constraint = Constraint.Obligation(PropertyId.unsafe("data_retention"), Level.Enterprise)

    val result = ConstraintAuthorizer.authorize(context, constraint, privacy)
    result.isLeft shouldBe true
    result.left.map { errors =>
      errors should have size 1
      errors.head shouldBe ConstraintAuthorizer.DomainNotAuthorized(securityTeam, privacy)
    }
  }

  it should "reject unauthorized level" in {
    val context = AuthorizationContext(securityTeam, Instant.now())
    val constraint = Constraint.Obligation(PropertyId.unsafe("security_audit"), Level.Project)

    val result = ConstraintAuthorizer.authorize(context, constraint, security)
    result.isLeft shouldBe true
    result.left.map { errors =>
      errors should have size 1
      errors.head shouldBe ConstraintAuthorizer.LevelNotAuthorized(securityTeam, Level.Project)
    }
  }

  it should "reject both domain and level" in {
    val context = AuthorizationContext(securityTeam, Instant.now())
    val constraint = Constraint.Obligation(PropertyId.unsafe("privacy_policy"), Level.Project)

    val result = ConstraintAuthorizer.authorize(context, constraint, privacy)
    result.isLeft shouldBe true
    result.left.map { errors =>
      errors should have size 2
      errors should contain(ConstraintAuthorizer.DomainNotAuthorized(securityTeam, privacy))
      errors should contain(ConstraintAuthorizer.LevelNotAuthorized(securityTeam, Level.Project))
    }
  }

  it should "enforce non-overlapping domain isolation" in {
    val securityContext = AuthorizationContext(securityTeam, Instant.now())
    val complianceContext = AuthorizationContext(complianceLead, Instant.now())
    val privacyConstraint = Constraint.Obligation(PropertyId.unsafe("data_privacy"), Level.Enterprise)

    val securityResult = ConstraintAuthorizer.authorize(securityContext, privacyConstraint, privacy)
    securityResult.isLeft shouldBe true
    securityResult.left.map { errors =>
      errors should contain(ConstraintAuthorizer.DomainNotAuthorized(securityTeam, privacy))
    }

    val complianceResult = ConstraintAuthorizer.authorize(complianceContext, privacyConstraint, privacy)
    complianceResult shouldBe Right(privacyConstraint)
  }
