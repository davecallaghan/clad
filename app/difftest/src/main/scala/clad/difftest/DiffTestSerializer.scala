package clad.difftest

import upickle.default.*

object DiffTestSerializer:
  // upickle ReadWriters
  given ReadWriter[DiffTestConstraint] = macroRW
  given ReadWriter[DiffTestHierarchy] = macroRW
  given ReadWriter[DiffTestCase] = macroRW
  given ReadWriter[DiffTestConstraintResult] = macroRW
  given ReadWriter[DiffTestResult] = macroRW

  def toJson(cases: Seq[DiffTestCase]): String = write(cases)
  def parseResults(json: String): Seq[DiffTestResult] = read[Seq[DiffTestResult]](json)
