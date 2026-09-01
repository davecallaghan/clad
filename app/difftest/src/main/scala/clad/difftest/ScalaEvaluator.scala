package clad.difftest

object ScalaEvaluator:
  def evaluate(tc: DiffTestCase): DiffTestResult =
    val ent = tc.hierarchy.enterprise
    val dept = tc.hierarchy.department
    val proj = tc.hierarchy.project

    val effective = tc.level match
      case "Enterprise" => ent
      case "Department" => ent ++ dept
      case "Project"    => ent ++ dept ++ proj
      case _ => ent ++ dept ++ proj

    val results = effective.map { c =>
      val prop = c.property
      val evalClass = tc.evaluabilities.getOrElse(prop, "Mechanical")
      val detected = evalClass match
        case "Procedural" => tc.evidence.getOrElse(prop, false)
        case _ => tc.detections.getOrElse(prop, false)

      val satisfied = c.`type` match
        case "Obligation"  => detected
        case "Prohibition" => !detected
        case _ => false

      DiffTestConstraintResult(
        property = prop,
        constraintType = c.`type`,
        evaluability = evalClass,
        satisfied = satisfied
      )
    }

    DiffTestResult(
      results = results,
      allSatisfied = results.forall(_.satisfied)
    )
