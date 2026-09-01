package clad.runtime.test

import org.scalacheck.{Arbitrary, Gen}
import clad.core.*
import clad.core.test.{Generators => CoreGen}
import clad.evaluation.*
import clad.evaluation.test.{Generators => EvalGen}
import clad.runtime.*
import java.time.Instant

object Generators:
  val genEvaluabilityClass: Gen[EvaluabilityClass] =
    Gen.oneOf(EvaluabilityClass.Mechanical, EvaluabilityClass.Procedural)
  given Arbitrary[EvaluabilityClass] = Arbitrary(genEvaluabilityClass)

  val genAuditEntry: Gen[AuditEntry] =
    for
      constraint <- CoreGen.genConstraint
      version    <- EvalGen.genVersion
      evalClass  <- genEvaluabilityClass
      satisfied  <- Gen.oneOf(true, false)
    yield AuditEntry(constraint, version, evalClass, satisfied,
      MechanicalDetail(satisfied), Instant.parse("2026-04-22T18:00:00Z"))
  given Arbitrary[AuditEntry] = Arbitrary(genAuditEntry)

  val genAuditRecord: Gen[AuditRecord] =
    for
      numEntries <- Gen.choose(1, 5)
      entries    <- Gen.listOfN(numEntries, genAuditEntry).map(_.toVector)
    yield AuditRecord("sha256:test", entries, "sha256:config",
      Instant.parse("2026-04-22T18:00:00Z"), None)
  given Arbitrary[AuditRecord] = Arbitrary(genAuditRecord)
