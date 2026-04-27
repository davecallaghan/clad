package clad.core

enum PrecedenceOutcome:
  case First, Second, Unresolved

case class PrecedenceRule(
  constraint1: PropertyId,
  domain1: Domain,
  constraint2: PropertyId,
  domain2: Domain,
  winner: PrecedenceOutcome,
  rationale: String
)

case class DomainPriority(ordering: List[Domain])

case class PrecedenceTable(
  rules: Map[(PropertyId, PropertyId), PrecedenceRule],
  defaultPriority: DomainPriority
):
  def resolve(
    c1: Constraint, c2: Constraint,
    domainOf: Constraint => Domain
  ): PrecedenceOutcome =
    rules.get((c1.property, c2.property))
      .map(_.winner)
      .getOrElse {
        val d1 = domainOf(c1)
        val d2 = domainOf(c2)
        val idx1 = defaultPriority.ordering.indexWhere(_.value == d1.value)
        val idx2 = defaultPriority.ordering.indexWhere(_.value == d2.value)
        if idx1 < 0 || idx2 < 0 then PrecedenceOutcome.Unresolved
        else if idx1 < idx2 then PrecedenceOutcome.First
        else if idx2 < idx1 then PrecedenceOutcome.Second
        else PrecedenceOutcome.Unresolved
      }
