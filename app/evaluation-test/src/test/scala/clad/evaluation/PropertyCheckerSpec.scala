package clad.evaluation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.test.TestCheckers.*

class PropertyCheckerSpec extends AnyFlatSpec with Matchers:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")

  "CheckerRegistry.build" should "succeed with distinct checkers" in {
    val result = CheckerRegistry.build(Seq(
      AlwaysDetects(phi),
      NeverDetects(psi)
    ))
    result shouldBe a[Right[_, _]]
  }

  it should "reject duplicate checkers for the same property" in {
    val result = CheckerRegistry.build(Seq(
      AlwaysDetects(phi),
      NeverDetects(phi)
    ))
    result shouldBe a[Left[_, _]]
    val Left(err) = result: @unchecked
    err.properties should contain(phi)
  }

  it should "build an empty registry" in {
    val result = CheckerRegistry.build(Seq.empty)
    result shouldBe a[Right[_, _]]
    val Right(reg) = result: @unchecked
    reg.checkers shouldBe empty
  }

  "CheckerRegistry.empty" should "have no checkers" in {
    CheckerRegistry.empty.checkers shouldBe empty
  }

  "AlwaysDetects" should "detect property in any artifact" in {
    val checker = AlwaysDetects(phi)
    checker.check(PromptArtifact("anything")) shouldBe true
    checker.check(PromptArtifact("")) shouldBe true
  }

  "NeverDetects" should "not detect property in any artifact" in {
    val checker = NeverDetects(phi)
    checker.check(PromptArtifact("anything")) shouldBe false
  }

  "KeywordChecker" should "detect property when keyword is present" in {
    val checker = KeywordChecker(phi, "HIPAA")
    checker.check(PromptArtifact("This includes a HIPAA disclaimer")) shouldBe true
  }

  it should "not detect property when keyword is absent" in {
    val checker = KeywordChecker(phi, "HIPAA")
    checker.check(PromptArtifact("No disclaimer here")) shouldBe false
  }
