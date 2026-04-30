package clad.difftest

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class DiffTestGeneratorSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  "DiffTestGenerator" should "generate valid test cases" in {
    forAll(DiffTestGenerator.genDiffTestCase) { tc =>
      // Hierarchy should have constraints only at their declared level
      tc.hierarchy.enterprise.foreach { c =>
        c.level shouldBe "Enterprise"
      }
      tc.hierarchy.department.foreach { c =>
        c.level shouldBe "Department"
      }
      tc.hierarchy.project.foreach { c =>
        c.level shouldBe "Project"
      }

      // Level should be valid
      tc.level should (be("Enterprise") or be("Department") or be("Project"))

      // Evaluability should be assigned for all constraints
      val allProps = (tc.hierarchy.enterprise.map(_.property) ++
                      tc.hierarchy.department.map(_.property) ++
                      tc.hierarchy.project.map(_.property)).toSet

      // Get effective constraints based on level
      val effectiveProps = tc.level match
        case "Enterprise" => tc.hierarchy.enterprise.map(_.property).toSet
        case "Department" => (tc.hierarchy.enterprise ++ tc.hierarchy.department).map(_.property).toSet
        case "Project" => (tc.hierarchy.enterprise ++ tc.hierarchy.department ++ tc.hierarchy.project).map(_.property).toSet
        case _ => Set.empty[String]

      effectiveProps.foreach { prop =>
        tc.evaluabilities should contain key prop
      }

      // Mechanical constraints should have detection values
      tc.evaluabilities.foreach { case (prop, evalClass) =>
        if evalClass == "Mechanical" && effectiveProps.contains(prop) then
          tc.detections should contain key prop
      }

      // Procedural constraints should have evidence values
      tc.evaluabilities.foreach { case (prop, evalClass) =>
        if evalClass == "Procedural" && effectiveProps.contains(prop) then
          tc.evidence should contain key prop
      }
    }
  }

  it should "serialize test cases to JSON" in {
    forAll(DiffTestGenerator.genDiffTestCase) { tc =>
      val json = DiffTestSerializer.toJson(Seq(tc))
      json should not be empty
      json should include("hierarchy")
      json should include("level")
      json should include("detections")
      json should include("evidence")
      json should include("evaluabilities")
    }
  }

  it should "generate cases that Scala evaluator can process" in {
    forAll(DiffTestGenerator.genDiffTestCase) { tc =>
      noException should be thrownBy {
        ScalaEvaluator.evaluate(tc)
      }
    }
  }
