package clad.monitoring.test

import org.scalacheck.{Arbitrary, Gen}
import clad.monitoring.*

object Generators:
  val genAlertSeverity: Gen[AlertSeverity] = Gen.oneOf(AlertSeverity.values.toSeq)
  given Arbitrary[AlertSeverity] = Arbitrary(genAlertSeverity)
  val genAlertCategory: Gen[AlertCategory] = Gen.oneOf(AlertCategory.values.toSeq)
  given Arbitrary[AlertCategory] = Arbitrary(genAlertCategory)
