package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import clad.core.test.Generators.given

class PropertyIdSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  "PropertyId.apply" should "accept valid snake_case identifiers" in {
    PropertyId("hipaa_disclaimer") shouldBe a[Right[_, _]]
    PropertyId("pii_in_logs") shouldBe a[Right[_, _]]
    PropertyId("a") shouldBe a[Right[_, _]]
    PropertyId("data_source_citation") shouldBe a[Right[_, _]]
    PropertyId("x123") shouldBe a[Right[_, _]]
  }

  it should "reject empty strings" in {
    PropertyId("") shouldBe a[Left[_, _]]
  }

  it should "reject strings starting with digits" in {
    PropertyId("123abc") shouldBe a[Left[_, _]]
  }

  it should "reject strings with uppercase" in {
    PropertyId("HipaaDisclaimer") shouldBe a[Left[_, _]]
  }

  it should "reject strings with special characters" in {
    PropertyId("hipaa-disclaimer") shouldBe a[Left[_, _]]
    PropertyId("pii.in.logs") shouldBe a[Left[_, _]]
    PropertyId("has spaces") shouldBe a[Left[_, _]]
  }

  "PropertyId.value" should "round-trip valid identifiers" in {
    val Right(pid) = PropertyId("hipaa_disclaimer"): @unchecked
    pid.value shouldBe "hipaa_disclaimer"
  }

  "PropertyId.unsafe" should "bypass validation" in {
    val pid = PropertyId.unsafe("INVALID")
    pid.value shouldBe "INVALID"
  }

  it should "generate valid PropertyIds" in {
    forAll { (pid: PropertyId) =>
      pid.value should fullyMatch regex "[a-z][a-z0-9_]*"
    }
  }
