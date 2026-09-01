package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SurfaceSpec extends AnyFlatSpec with Matchers:

  "Surface" should "have six values: the five pipeline surfaces plus evidence" in {
    Surface.values should have length 6
    Surface.values.toSet shouldBe Surface.pipeline + Surface.Evidence
  }

  it should "keep the pipeline partition to five" in {
    // The composed guarantee of EPG + ROC + MDR is stated over these, and only these.
    Surface.pipeline should have size 5
    Surface.pipeline should not contain Surface.Evidence
  }

  "Surface.governability" should "be defined for every surface under either deployment" in {
    for
      s <- Surface.values
      e <- EvidenceProvision.values
    do Surface.governability(s, e) shouldBe a[Governability]
  }

  it should "classify the pipeline surfaces independently of the evidence deployment" in {
    val expected = Map(
      Surface.Prompt   -> Governability.Full,
      Surface.Input    -> Governability.Partial,
      Surface.Config   -> Governability.Partial,
      Surface.Output   -> Governability.Partial,
      Surface.Delivery -> Governability.Full
    )
    for
      (surface, cls) <- expected
      deployment <- EvidenceProvision.values
    do Surface.governability(surface, deployment) shouldBe cls
  }

  it should "split the evidence surface's class by deployment choice" in {
    // The only surface in the framework whose class is a deployment decision rather than
    // a property of the technology. Retrieval makes the basis identifiable per
    // interaction; parametric evidence is absorbed in the weights and is not.
    Surface.governability(Surface.Evidence, EvidenceProvision.Retrieved) shouldBe
      Governability.Full
    Surface.governability(Surface.Evidence, EvidenceProvision.Parametric) shouldBe
      Governability.External
  }

  "Governability" should "have exactly three values" in {
    Governability.values should have length 3
  }
