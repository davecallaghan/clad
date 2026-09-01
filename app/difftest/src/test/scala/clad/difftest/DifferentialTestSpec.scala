package clad.difftest

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import java.io.{ByteArrayInputStream, File}
import scala.sys.process.*

class DifferentialTestSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  val leanExePath: String = sys.env.getOrElse(
    "LEAN_DIFFTEST_EXE",
    new File("../lean/.lake/build/bin/clad-difftest").getAbsolutePath
  )

  val testCaseCount = 1000  // Start with 1000, scale up once working

  "Lean evaluator" should "agree with Scala evaluator on generated test cases" in {
    assume(new File(leanExePath).exists(), s"Lean executable not found at $leanExePath")

    val cases = (1 to testCaseCount).flatMap(_ => DiffTestGenerator.genDiffTestCase.sample)
    val inputJson = DiffTestSerializer.toJson(cases)

    // Run Lean
    val process = Process(leanExePath)
    val leanOutput = (process #< new ByteArrayInputStream(inputJson.getBytes("UTF-8"))).!!
    val leanResults = DiffTestSerializer.parseResults(leanOutput)

    // Run Scala
    val scalaResults = cases.map(ScalaEvaluator.evaluate)

    leanResults.size shouldBe scalaResults.size

    var mismatches = 0
    cases.zip(leanResults.zip(scalaResults)).zipWithIndex.foreach {
      case ((tc, (lr, sr)), idx) =>
        if lr.allSatisfied != sr.allSatisfied then
          mismatches += 1
          info(s"MISMATCH at case $idx: lean.allSatisfied=${lr.allSatisfied} scala.allSatisfied=${sr.allSatisfied}")
          info(s"  input: ${DiffTestSerializer.toJson(Seq(tc))}")

        val lSorted = lr.results.sortBy(_.property)
        val sSorted = sr.results.sortBy(_.property)
        if lSorted != sSorted then
          mismatches += 1
          info(s"RESULT MISMATCH at case $idx")
          info(s"  lean:  $lSorted")
          info(s"  scala: $sSorted")
    }

    withClue(s"$mismatches mismatches out of ${cases.size} test cases") {
      mismatches shouldBe 0
    }
  }

  it should "handle empty hierarchy" in {
    assume(new File(leanExePath).exists())
    val empty = DiffTestCase(
      DiffTestHierarchy(Nil, Nil, Nil), "Project",
      Map.empty, Map.empty, Map.empty
    )
    val inputJson = DiffTestSerializer.toJson(Seq(empty))
    val process = Process(leanExePath)
    val leanOutput = (process #< new ByteArrayInputStream(inputJson.getBytes("UTF-8"))).!!
    val leanResults = DiffTestSerializer.parseResults(leanOutput)
    val scalaResult = ScalaEvaluator.evaluate(empty)

    leanResults.head.allSatisfied shouldBe true
    scalaResult.allSatisfied shouldBe true
  }
