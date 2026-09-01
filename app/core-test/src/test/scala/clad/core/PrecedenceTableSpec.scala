package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PrecedenceTableSpec extends AnyFlatSpec with Matchers:

  val safety: Domain = Domain("safety")
  val legal: Domain = Domain("legal")
  val security: Domain = Domain("security")
  val compliance: Domain = Domain("compliance")
  val business: Domain = Domain("business")

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")
  val rho: PropertyId = PropertyId.unsafe("data_retention")

  val defaultPriority: DomainPriority = DomainPriority(
    List(safety, legal, security, compliance, business)
  )

  val domainOf: Constraint => Domain = {
    case c if c.property == phi => safety
    case c if c.property == psi => legal
    case c if c.property == rho => business
    case _ => business
  }

  "PrecedenceTable.resolve" should "use specific rule when available" in {
    val rule = PrecedenceRule(phi, safety, psi, legal, PrecedenceOutcome.First, "safety wins over legal for these")
    val table = PrecedenceTable(
      rules = Map((phi, psi) -> rule),
      defaultPriority = defaultPriority
    )
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Enterprise)
    table.resolve(c1, c2, domainOf) shouldBe PrecedenceOutcome.First
  }

  it should "fall back to domain priority when no specific rule exists" in {
    val table = PrecedenceTable(rules = Map.empty, defaultPriority = defaultPriority)
    val c1 = Constraint.Obligation(phi, Level.Enterprise)   // safety
    val c2 = Constraint.Obligation(rho, Level.Enterprise)   // business
    table.resolve(c1, c2, domainOf) shouldBe PrecedenceOutcome.First
  }

  it should "return Second when c2's domain has higher priority" in {
    val table = PrecedenceTable(rules = Map.empty, defaultPriority = defaultPriority)
    val c1 = Constraint.Obligation(rho, Level.Enterprise)   // business
    val c2 = Constraint.Obligation(phi, Level.Enterprise)   // safety
    table.resolve(c1, c2, domainOf) shouldBe PrecedenceOutcome.Second
  }

  it should "return Unresolved when domains have equal priority" in {
    val table = PrecedenceTable(rules = Map.empty, defaultPriority = defaultPriority)
    val c1 = Constraint.Obligation(phi, Level.Enterprise)  // safety
    val c2 = Constraint.Obligation(phi, Level.Department)  // safety (same domain)
    table.resolve(c1, c2, domainOf) shouldBe PrecedenceOutcome.Unresolved
  }

  it should "return Unresolved when domain is not in priority list" in {
    val unknownDomain = Domain("unknown")
    val table = PrecedenceTable(rules = Map.empty, defaultPriority = defaultPriority)
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Obligation(psi, Level.Enterprise)
    val domainAllUnknown: Constraint => Domain = _ => unknownDomain
    table.resolve(c1, c2, domainAllUnknown) shouldBe PrecedenceOutcome.Unresolved
  }
