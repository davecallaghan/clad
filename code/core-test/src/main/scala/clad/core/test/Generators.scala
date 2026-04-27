package clad.core.test

import org.scalacheck.{Arbitrary, Gen}
import clad.core.*

object Generators:

  val genLevel: Gen[Level] = Gen.oneOf(Level.values.toSeq)
  given Arbitrary[Level] = Arbitrary(genLevel)

  val genPropertyIdString: Gen[String] =
    for
      head <- Gen.alphaLowerChar
      tail <- Gen.listOf(Gen.frequency(
        (26, Gen.alphaLowerChar),
        (10, Gen.numChar),
        (5, Gen.const('_'))
      ))
    yield (head :: tail).mkString

  val genPropertyId: Gen[PropertyId] = genPropertyIdString.map(PropertyId.unsafe(_))
  given Arbitrary[PropertyId] = Arbitrary(genPropertyId)

  val genConstraint: Gen[Constraint] =
    for
      prop  <- genPropertyId
      level <- genLevel
      isObligation <- Gen.oneOf(true, false)
    yield
      if isObligation then Constraint.Obligation(prop, level)
      else Constraint.Prohibition(prop, level)

  given Arbitrary[Constraint] = Arbitrary(genConstraint)

  def genConstraintAt(level: Level): Gen[Constraint] =
    for
      prop <- genPropertyId
      isObligation <- Gen.oneOf(true, false)
    yield
      if isObligation then Constraint.Obligation(prop, level)
      else Constraint.Prohibition(prop, level)

  val genGovernanceAnnotation: Gen[GovernanceAnnotation] =
    for
      prop  <- genPropertyId
      level <- genLevel
      rationale <- Gen.alphaNumStr.map(s => s"reviewed: $s")
    yield GovernanceAnnotation(prop, level, rationale)

  given Arbitrary[GovernanceAnnotation] = Arbitrary(genGovernanceAnnotation)

  def genContradictionFreeSet(level: Level, usedProperties: Set[PropertyId] = Set.empty): Gen[Set[Constraint]] =
    for
      size <- Gen.choose(0, 5)
      props <- Gen.listOfN(size, genPropertyId).map(_.toSet -- usedProperties)
      constraints <- Gen.sequence[List[Constraint], Constraint](
        props.toList.map { prop =>
          Gen.oneOf(
            Gen.const(Constraint.Obligation(prop, level)),
            Gen.const(Constraint.Prohibition(prop, level))
          )
        }
      )
    yield constraints.toSet

  val genValidHierarchy: Gen[ConstraintHierarchy] =
    for
      entProps <- Gen.listOf(genPropertyId).map(_.take(3).toSet)
      entConstraints <- Gen.sequence[List[Constraint], Constraint](
        entProps.toList.map { prop =>
          Gen.oneOf(
            Gen.const(Constraint.Obligation(prop, Level.Enterprise)),
            Gen.const(Constraint.Prohibition(prop, Level.Enterprise))
          )
        }
      )
      deptProps <- Gen.listOf(genPropertyId).map(_.take(3).toSet -- entProps)
      deptConstraints <- Gen.sequence[List[Constraint], Constraint](
        deptProps.toList.map { prop =>
          Gen.oneOf(
            Gen.const(Constraint.Obligation(prop, Level.Department)),
            Gen.const(Constraint.Prohibition(prop, Level.Department))
          )
        }
      )
      projProps <- Gen.listOf(genPropertyId).map(_.take(3).toSet -- entProps -- deptProps)
      projConstraints <- Gen.sequence[List[Constraint], Constraint](
        projProps.toList.map { prop =>
          Gen.oneOf(
            Gen.const(Constraint.Obligation(prop, Level.Project)),
            Gen.const(Constraint.Prohibition(prop, Level.Project))
          )
        }
      )
    yield
      ConstraintHierarchy.build(
        entConstraints.toSet,
        deptConstraints.toSet,
        projConstraints.toSet
      ) match
        case Right(h) => h
        case Left(_) =>
          ConstraintHierarchy.build(Set.empty, Set.empty, Set.empty).toOption.get

  given Arbitrary[ConstraintHierarchy] = Arbitrary(genValidHierarchy)

  val genDomain: Gen[Domain] =
    Gen.oneOf("safety", "legal", "security", "compliance", "business").map(Domain(_))
  given Arbitrary[Domain] = Arbitrary(genDomain)

  val genSurfaceSubset: Gen[Set[Surface]] =
    Gen.someOf(Surface.values.toSeq).map(_.toSet)

  val genComponentSpec: Gen[ComponentSpec] =
    for
      id <- Gen.alphaNumStr.map(s => s"comp_$s")
      surfaces <- genSurfaceSubset
      constraints <- Gen.listOf(genConstraint).map(_.take(3).toSet)
      hard <- Gen.listOf(Gen.alphaNumStr.map(s => Requirement.Hard(s"req_$s"))).map(_.take(2).toSet)
      soft <- Gen.listOf(Gen.alphaNumStr.map(s => Requirement.Soft(s"soft_$s"))).map(_.take(2).toSet)
    yield ComponentSpec(id, surfaces, constraints, hard, soft)

  given Arbitrary[ComponentSpec] = Arbitrary(genComponentSpec)

  val genNonOverlappingPair: Gen[(ComponentSpec, ComponentSpec)] =
    val allSurfaces = Surface.values.toList
    for
      subset <- Gen.someOf(allSurfaces)
      s1 = subset.toSet
      s2 = allSurfaces.toSet -- s1
      g1 <- genComponentSpec.map(_.copy(surfaces = s1))
      g2 <- genComponentSpec.map(_.copy(surfaces = s2))
    yield (g1, g2)

  val genNonOverlappingTriple: Gen[(ComponentSpec, ComponentSpec, ComponentSpec)] =
    val allSurfaces = Surface.values.toList
    for
      assignment <- Gen.listOfN(allSurfaces.length, Gen.choose(0, 2))
      pairs = allSurfaces.zip(assignment)
      s1 = pairs.collect { case (s, 0) => s }.toSet
      s2 = pairs.collect { case (s, 1) => s }.toSet
      s3 = pairs.collect { case (s, 2) => s }.toSet
      g1 <- genComponentSpec.map(_.copy(surfaces = s1))
      g2 <- genComponentSpec.map(_.copy(surfaces = s2))
      g3 <- genComponentSpec.map(_.copy(surfaces = s3))
    yield (g1, g2, g3)
