// code/core-test/src/test/scala/clad/core/ComponentCompositionSpec.scala
package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen
import clad.core.test.Generators
import clad.core.test.Generators.given

class ComponentCompositionSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")

  // --- Basic composition tests ---

  "ComponentComposition.compose" should "combine surfaces" in {
    val g1 = ComponentSpec("EPG", Set(Surface.Prompt), Set.empty, Set.empty, Set.empty)
    val g2 = ComponentSpec("ROC", Set(Surface.Output, Surface.Delivery), Set.empty, Set.empty, Set.empty)
    val Right(composed) = ComponentComposition.compose(g1, g2): @unchecked
    composed.surfaces shouldBe Set(Surface.Prompt, Surface.Output, Surface.Delivery)
  }

  it should "combine constraints" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Enterprise)
    val g1 = ComponentSpec("A", Set(Surface.Prompt), Set(c1), Set.empty, Set.empty)
    val g2 = ComponentSpec("B", Set(Surface.Output), Set(c2), Set.empty, Set.empty)
    val Right(composed) = ComponentComposition.compose(g1, g2): @unchecked
    composed.constraints shouldBe Set(c1, c2)
  }

  it should "reject overlapping surfaces" in {
    val g1 = ComponentSpec("A", Set(Surface.Prompt), Set.empty, Set.empty, Set.empty)
    val g2 = ComponentSpec("B", Set(Surface.Prompt, Surface.Output), Set.empty, Set.empty, Set.empty)
    val result = ComponentComposition.compose(g1, g2)
    result shouldBe a[Left[_, _]]
    val Left(err) = result: @unchecked
    err shouldBe ComponentComposition.OverlappingSurfaces(Set(Surface.Prompt))
  }

  // --- Identity element ---

  "Identity" should "satisfy g + g_empty = g" in {
    val g = ComponentSpec("EPG", Set(Surface.Prompt), Set.empty, Set.empty, Set.empty)
    val Right(composed) = ComponentComposition.compose(g, ComponentComposition.empty): @unchecked
    composed.surfaces shouldBe g.surfaces
    composed.constraints shouldBe g.constraints
    composed.hardRequirements shouldBe g.hardRequirements
    composed.softRequirements shouldBe g.softRequirements
  }

  it should "satisfy g_empty + g = g" in {
    val g = ComponentSpec("EPG", Set(Surface.Prompt), Set.empty, Set.empty, Set.empty)
    val Right(composed) = ComponentComposition.compose(ComponentComposition.empty, g): @unchecked
    composed.surfaces shouldBe g.surfaces
    composed.constraints shouldBe g.constraints
  }

  // --- Known components ---

  "KnownComponents" should "compose EPG + ROC + MDR to cover the five pipeline surfaces" in {
    val epgRoc = ComponentComposition.compose(KnownComponents.EPG, KnownComponents.ROC)
    epgRoc shouldBe a[Right[_, _]]
    val Right(partial) = epgRoc: @unchecked

    val full = ComponentComposition.compose(partial, KnownComponents.MDR)
    full shouldBe a[Right[_, _]]
    val Right(composed) = full: @unchecked
    // The five pipeline surfaces, and only those. The book states that adding the
    // evidence surface leaves the composed guarantee "unaffected in what it claims and
    // narrowed in what it covers"; asserting Surface.values here would quietly widen the
    // claim to a surface no component governs.
    composed.surfaces shouldBe Surface.pipeline
    composed.surfaces should not contain Surface.Evidence
  }

  // --- The E element of the tuple ---

  "compose" should "union the evaluation functions" in {
    // E = E_g₁ ∪ E_g₂. Two components governing disjoint surfaces through different
    // evaluators compose into one that carries both.
    val Right(epgRoc) = ComponentComposition.compose(
      KnownComponents.EPG, KnownComponents.ROC): @unchecked
    val Right(all) = ComponentComposition.compose(epgRoc, KnownComponents.MDR): @unchecked

    all.evaluators.map(_.value) shouldBe
      Set("epg-prompt-evaluator", "roc-output-evaluator", "mdr-monitor")
  }

  it should "leave evaluators unchanged when composing with the identity" in {
    val Right(composed) =
      ComponentComposition.compose(KnownComponents.EPG, ComponentComposition.empty): @unchecked
    composed.evaluators shouldBe KnownComponents.EPG.evaluators
  }


  it should "leave the evidence surface ungoverned" in {
    // Explicit, so the gap is a stated result rather than an omission. Whoever adds a
    // component for the evidence surface has to change this test.
    val Right(epgRoc) = ComponentComposition.compose(
      KnownComponents.EPG, KnownComponents.ROC): @unchecked
    val Right(all) = ComponentComposition.compose(epgRoc, KnownComponents.MDR): @unchecked

    Surface.values.toSet -- all.surfaces shouldBe Set(Surface.Evidence)
  }

  // --- Property-based: Commutativity ---

  "Commutativity" should "hold: g1 + g2 = g2 + g1 for non-overlapping components" in {
    forAll(Generators.genNonOverlappingPair) { case (g1, g2) =>
      val r1 = ComponentComposition.compose(g1, g2)
      val r2 = ComponentComposition.compose(g2, g1)
      (r1, r2) match
        case (Right(c1), Right(c2)) =>
          c1.surfaces shouldBe c2.surfaces
          c1.constraints shouldBe c2.constraints
          c1.hardRequirements shouldBe c2.hardRequirements
          c1.softRequirements shouldBe c2.softRequirements
        case _ => fail("Both compositions should succeed for non-overlapping components")
    }
  }

  it should "report the same overlap in either order when components overlap" in {
    forAll(Generators.genOverlappingPair) { case (g1, g2) =>
      (ComponentComposition.compose(g1, g2), ComponentComposition.compose(g2, g1)) match
        case (Left(e1), Left(e2)) => e1 shouldBe e2
        case other => fail(s"overlapping components must not compose: $other")
    }
  }

  // --- Property-based: Associativity ---

  "Associativity" should "hold: (g1 + g2) + g3 = g1 + (g2 + g3) for non-overlapping components" in {
    forAll(Generators.genNonOverlappingTriple) { case (g1, g2, g3) =>
      val leftAssoc = for
        ab <- ComponentComposition.compose(g1, g2)
        abc <- ComponentComposition.compose(ab, g3)
      yield abc

      val rightAssoc = for
        bc <- ComponentComposition.compose(g2, g3)
        abc <- ComponentComposition.compose(g1, bc)
      yield abc

      (leftAssoc, rightAssoc) match
        case (Right(l), Right(r)) =>
          l.surfaces shouldBe r.surfaces
          l.constraints shouldBe r.constraints
          l.hardRequirements shouldBe r.hardRequirements
          l.softRequirements shouldBe r.softRequirements
        case _ => fail("Both association orders should succeed for non-overlapping components")
    }
  }

  // --- Property-based: Identity ---

  "Identity property" should "hold for all components" in {
    forAll(Generators.genComponentSpec) { g =>
      val Right(withEmpty) = ComponentComposition.compose(g, ComponentComposition.empty): @unchecked
      withEmpty.surfaces shouldBe g.surfaces
      withEmpty.constraints shouldBe g.constraints
      withEmpty.hardRequirements shouldBe g.hardRequirements
      withEmpty.softRequirements shouldBe g.softRequirements
    }
  }
