package clad.evaluation.test

import org.scalacheck.{Arbitrary, Gen}
import clad.core.*
import clad.core.test.{Generators => CoreGen}
import clad.evaluation.*
import java.time.Instant

object Generators:

  val genVersion: Gen[String] =
    for
      major <- Gen.choose(0, 9)
      minor <- Gen.choose(0, 9)
      patch <- Gen.choose(0, 9)
    yield s"$major.$minor.$patch"

  val genPromptArtifact: Gen[PromptArtifact] =
    for
      content <- Gen.alphaNumStr
    yield PromptArtifact(content)

  given Arbitrary[PromptArtifact] = Arbitrary(genPromptArtifact)

  val genMechanicalConstraint: Gen[MechanicalConstraint] =
    for
      c <- CoreGen.genConstraint
      v <- genVersion
    yield MechanicalConstraint(c, v)

  given Arbitrary[MechanicalConstraint] = Arbitrary(genMechanicalConstraint)

  val genProceduralConstraint: Gen[ProceduralConstraint] =
    for
      c <- CoreGen.genConstraint
      v <- genVersion
    yield ProceduralConstraint(c, v)

  given Arbitrary[ProceduralConstraint] = Arbitrary(genProceduralConstraint)

  val genEvaluableConstraint: Gen[EvaluableConstraint] =
    Gen.oneOf(genMechanicalConstraint, genProceduralConstraint)

  given Arbitrary[EvaluableConstraint] = Arbitrary(genEvaluableConstraint)

  val genProceduralEvidence: Gen[ProceduralEvidence] =
    for
      prop <- CoreGen.genPropertyId
      attestor <- Gen.alphaNumStr.map(s => s"attestor_$s@example.com")
      satisfied <- Gen.oneOf(true, false)
      rationale <- Gen.alphaNumStr.map(s => s"rationale: $s")
    yield ProceduralEvidence(prop, attestor, satisfied, Instant.now(), rationale)

  given Arbitrary[ProceduralEvidence] = Arbitrary(genProceduralEvidence)

  def genCompleteEvidenceFor(constraints: Set[ProceduralConstraint]): Gen[EvidenceSet] =
    val evidenceGens = constraints.toList.map { pc =>
      for
        attestor <- Gen.alphaNumStr.map(s => s"attestor_$s@example.com")
        satisfied <- Gen.oneOf(true, false)
        rationale <- Gen.alphaNumStr.map(s => s"rationale: $s")
      yield ProceduralEvidence(pc.constraint.property, attestor, satisfied, Instant.now(), rationale)
    }
    Gen.sequence[List[ProceduralEvidence], ProceduralEvidence](evidenceGens)
      .map(evs => EvidenceSet(evs.map(e => e.constraintProperty -> e).toMap))
