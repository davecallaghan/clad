package clad.core

sealed trait TensionKind
object TensionKind:
  case object AnnotationOverridden extends TensionKind
  case object CrossDomain extends TensionKind
  case object OntologyRelated extends TensionKind

case class Tension(
  kind: TensionKind,
  constraint: Constraint,
  relatedTo: Either[GovernanceAnnotation, Constraint],
  advisory: String
)

object TensionDetector:

  def detectAnnotationTensions(
    annotations: Map[Level, Set[GovernanceAnnotation]],
    hierarchy: ConstraintHierarchy
  ): List[Tension] =
    val result = List.newBuilder[Tension]
    for
      (annotationLevel, annots) <- annotations
      annot <- annots
      constraintLevel <- Level.values
      if annotationLevel.strictlyGoverns(constraintLevel)
      constraint <- hierarchy.effectiveAt(constraintLevel)
        .diff(hierarchy.effectiveAt(annotationLevel))
      if constraint.property == annot.property
    do
      result += Tension(
        kind = TensionKind.AnnotationOverridden,
        constraint = constraint,
        relatedTo = Left(annot),
        advisory = s"P_meta(${annot.property.value}) at $annotationLevel overridden by constraint at $constraintLevel"
      )
    result.result().distinctBy(t => (t.constraint, t.relatedTo))

  def detectCrossDomainTensions(
    constraints: Set[Constraint],
    domainOf: Constraint => Domain
  ): List[Tension] =
    val byProperty = constraints.groupBy(_.property)
    val result = List.newBuilder[Tension]
    for
      (prop, cs) <- byProperty
      if cs.size > 1
      grouped = cs.groupBy(c => domainOf(c).value)
      if grouped.size > 1
      pair <- grouped.values.toList.combinations(2)
      c1 <- pair(0)
      c2 <- pair(1)
    do
      result += Tension(
        kind = TensionKind.CrossDomain,
        constraint = c1,
        relatedTo = Right(c2),
        advisory = s"Property ${prop.value} constrained by domains ${domainOf(c1).value} and ${domainOf(c2).value}"
      )
    result.result()
