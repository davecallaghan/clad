package clad.integrity.test

import org.scalacheck.{Arbitrary, Gen}
import clad.integrity.*
import java.time.Instant

object Generators:
  val genInteractionId: Gen[InteractionId] = Gen.uuid.map(u => InteractionId(u.toString))
  given Arbitrary[InteractionId] = Arbitrary(genInteractionId)

  val genComponentId: Gen[ComponentId] = Gen.oneOf("epg", "roc", "supervisor", "mdr").map(ComponentId(_))
  given Arbitrary[ComponentId] = Arbitrary(genComponentId)

  val genFailurePosture: Gen[FailurePosture] = Gen.oneOf(FailurePosture.values.toSeq)
  given Arbitrary[FailurePosture] = Arbitrary(genFailurePosture)

  val genFailureAction: Gen[FailureAction] = Gen.oneOf(FailureAction.values.toSeq)
  given Arbitrary[FailureAction] = Arbitrary(genFailureAction)

  val genGhostClassification: Gen[GhostDetector.GhostClassification] = Gen.oneOf(GhostDetector.GhostClassification.values.toSeq)
  given Arbitrary[GhostDetector.GhostClassification] = Arbitrary(genGhostClassification)

  val genGilEntry: Gen[GilEntry] =
    for id <- genInteractionId
    yield GilEntry(id, Instant.parse("2026-04-23T10:00:00Z"))
  given Arbitrary[GilEntry] = Arbitrary(genGilEntry)
