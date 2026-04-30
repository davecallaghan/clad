package clad.difftest

import org.scalacheck.Gen
import clad.core.*
import clad.core.test.Generators

object DiffTestGenerator:

  private def toTestConstraint(c: Constraint): DiffTestConstraint =
    DiffTestConstraint(
      property = c.property.value,
      `type` = c match
        case _: Constraint.Obligation => "Obligation"
        case _: Constraint.Prohibition => "Prohibition",
      level = c.level.toString
    )

  val genDiffTestCase: Gen[DiffTestCase] =
    for
      hierarchy <- Generators.genValidHierarchy
      level     <- Generators.genLevel
      effective  = hierarchy.effectiveAt(level)
      // Assign evaluability randomly per constraint
      evalAssignments <- Gen.sequence[List[(Constraint, String)], (Constraint, String)](
        effective.toList.map(c => Gen.oneOf("Mechanical", "Procedural").map(e => (c, e)))
      )
      // Generate detection results for mechanical constraints
      detections <- Gen.sequence[List[(String, Boolean)], (String, Boolean)](
        evalAssignments.filter(_._2 == "Mechanical").map { case (c, _) =>
          Gen.oneOf(true, false).map(b => (c.property.value, b))
        }
      )
      // Generate evidence results for procedural constraints
      evidence <- Gen.sequence[List[(String, Boolean)], (String, Boolean)](
        evalAssignments.filter(_._2 == "Procedural").map { case (c, _) =>
          Gen.oneOf(true, false).map(b => (c.property.value, b))
        }
      )
    yield DiffTestCase(
      hierarchy = DiffTestHierarchy(
        enterprise = hierarchy.enterprise.constraints.toList.map(toTestConstraint),
        department = hierarchy.department.constraints.toList.map(toTestConstraint),
        project = hierarchy.project.constraints.toList.map(toTestConstraint)
      ),
      level = level.toString,
      detections = detections.toMap,
      evidence = evidence.toMap,
      evaluabilities = evalAssignments.map { case (c, e) => (c.property.value, e) }.toMap
    )
