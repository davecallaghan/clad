package clad.difftest

case class DiffTestConstraint(
  property: String,
  `type`: String,    // "Obligation" | "Prohibition"
  level: String      // "Enterprise" | "Department" | "Project"
)

case class DiffTestHierarchy(
  enterprise: List[DiffTestConstraint],
  department: List[DiffTestConstraint],
  project: List[DiffTestConstraint]
)

case class DiffTestCase(
  hierarchy: DiffTestHierarchy,
  level: String,
  detections: Map[String, Boolean],
  evidence: Map[String, Boolean],
  evaluabilities: Map[String, String]
)

case class DiffTestConstraintResult(
  property: String,
  constraintType: String,
  evaluability: String,
  satisfied: Boolean
)

case class DiffTestResult(
  results: List[DiffTestConstraintResult],
  allSatisfied: Boolean
)
