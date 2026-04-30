package clad.difftest

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScalaEvaluatorSpec extends AnyFlatSpec with Matchers:

  "ScalaEvaluator" should "evaluate empty hierarchy as all satisfied" in {
    val empty = DiffTestCase(
      DiffTestHierarchy(Nil, Nil, Nil),
      "Project",
      Map.empty,
      Map.empty,
      Map.empty
    )
    val result = ScalaEvaluator.evaluate(empty)
    result.allSatisfied shouldBe true
    result.results shouldBe Nil
  }

  it should "evaluate satisfied obligation at enterprise level" in {
    val tc = DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = List(DiffTestConstraint("phi", "Obligation", "Enterprise")),
        department = Nil,
        project = Nil
      ),
      level = "Enterprise",
      detections = Map("phi" -> true),
      evidence = Map.empty,
      evaluabilities = Map("phi" -> "Mechanical")
    )
    val result = ScalaEvaluator.evaluate(tc)
    result.allSatisfied shouldBe true
    result.results should have size 1
    result.results.head.property shouldBe "phi"
    result.results.head.satisfied shouldBe true
  }

  it should "evaluate unsatisfied obligation at enterprise level" in {
    val tc = DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = List(DiffTestConstraint("phi", "Obligation", "Enterprise")),
        department = Nil,
        project = Nil
      ),
      level = "Enterprise",
      detections = Map("phi" -> false),
      evidence = Map.empty,
      evaluabilities = Map("phi" -> "Mechanical")
    )
    val result = ScalaEvaluator.evaluate(tc)
    result.allSatisfied shouldBe false
    result.results should have size 1
    result.results.head.satisfied shouldBe false
  }

  it should "evaluate satisfied prohibition at enterprise level" in {
    val tc = DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = List(DiffTestConstraint("psi", "Prohibition", "Enterprise")),
        department = Nil,
        project = Nil
      ),
      level = "Enterprise",
      detections = Map("psi" -> false),
      evidence = Map.empty,
      evaluabilities = Map("psi" -> "Mechanical")
    )
    val result = ScalaEvaluator.evaluate(tc)
    result.allSatisfied shouldBe true
    result.results should have size 1
    result.results.head.satisfied shouldBe true
  }

  it should "evaluate unsatisfied prohibition at enterprise level" in {
    val tc = DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = List(DiffTestConstraint("psi", "Prohibition", "Enterprise")),
        department = Nil,
        project = Nil
      ),
      level = "Enterprise",
      detections = Map("psi" -> true),
      evidence = Map.empty,
      evaluabilities = Map("psi" -> "Mechanical")
    )
    val result = ScalaEvaluator.evaluate(tc)
    result.allSatisfied shouldBe false
    result.results should have size 1
    result.results.head.satisfied shouldBe false
  }

  it should "inherit enterprise constraints at department level" in {
    val tc = DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = List(DiffTestConstraint("phi", "Obligation", "Enterprise")),
        department = List(DiffTestConstraint("psi", "Prohibition", "Department")),
        project = Nil
      ),
      level = "Department",
      detections = Map("phi" -> true, "psi" -> false),
      evidence = Map.empty,
      evaluabilities = Map("phi" -> "Mechanical", "psi" -> "Mechanical")
    )
    val result = ScalaEvaluator.evaluate(tc)
    result.allSatisfied shouldBe true
    result.results should have size 2
  }

  it should "inherit enterprise and department constraints at project level" in {
    val tc = DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = List(DiffTestConstraint("phi", "Obligation", "Enterprise")),
        department = List(DiffTestConstraint("psi", "Prohibition", "Department")),
        project = List(DiffTestConstraint("chi", "Obligation", "Project"))
      ),
      level = "Project",
      detections = Map("phi" -> true, "psi" -> false, "chi" -> true),
      evidence = Map.empty,
      evaluabilities = Map("phi" -> "Mechanical", "psi" -> "Mechanical", "chi" -> "Mechanical")
    )
    val result = ScalaEvaluator.evaluate(tc)
    result.allSatisfied shouldBe true
    result.results should have size 3
  }

  it should "use evidence for procedural constraints" in {
    val tc = DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = List(DiffTestConstraint("phi", "Obligation", "Enterprise")),
        department = Nil,
        project = Nil
      ),
      level = "Enterprise",
      detections = Map.empty,
      evidence = Map("phi" -> true),
      evaluabilities = Map("phi" -> "Procedural")
    )
    val result = ScalaEvaluator.evaluate(tc)
    result.allSatisfied shouldBe true
    result.results should have size 1
    result.results.head.evaluability shouldBe "Procedural"
    result.results.head.satisfied shouldBe true
  }
