// code/core-test/src/test/scala/clad/core/SurfaceSpec.scala
package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SurfaceSpec extends AnyFlatSpec with Matchers:

  "Surface" should "have exactly five values" in {
    Surface.values should have length 5
  }

  "Surface.governability" should "have an entry for every Surface value" in {
    Surface.values.foreach { s =>
      Surface.governability should contain key s
    }
  }

  it should "classify Prompt as Full" in {
    Surface.governability(Surface.Prompt) shouldBe Governability.Full
  }

  it should "classify Input as Partial" in {
    Surface.governability(Surface.Input) shouldBe Governability.Partial
  }

  it should "classify Config as Partial" in {
    Surface.governability(Surface.Config) shouldBe Governability.Partial
  }

  it should "classify Output as Partial" in {
    Surface.governability(Surface.Output) shouldBe Governability.Partial
  }

  it should "classify Delivery as Full" in {
    Surface.governability(Surface.Delivery) shouldBe Governability.Full
  }

  "Governability" should "have exactly three values" in {
    Governability.values should have length 3
  }
