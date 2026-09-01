package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class LevelSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  "Level ordering" should "have Enterprise govern all levels" in {
    Level.Enterprise.governs(Level.Enterprise) shouldBe true
    Level.Enterprise.governs(Level.Department) shouldBe true
    Level.Enterprise.governs(Level.Project) shouldBe true
  }

  it should "have Department govern Department and Project only" in {
    Level.Department.governs(Level.Enterprise) shouldBe false
    Level.Department.governs(Level.Department) shouldBe true
    Level.Department.governs(Level.Project) shouldBe true
  }

  it should "have Project govern only itself" in {
    Level.Project.governs(Level.Enterprise) shouldBe false
    Level.Project.governs(Level.Department) shouldBe false
    Level.Project.governs(Level.Project) shouldBe true
  }

  "strictlyGoverns" should "be irreflexive" in {
    Level.values.foreach { l =>
      l.strictlyGoverns(l) shouldBe false
    }
  }

  it should "be transitive" in {
    Level.Enterprise.strictlyGoverns(Level.Department) shouldBe true
    Level.Department.strictlyGoverns(Level.Project) shouldBe true
    Level.Enterprise.strictlyGoverns(Level.Project) shouldBe true
  }

  "Level" should "have exactly three values" in {
    Level.values should have length 3
  }

  "Ordering" should "be a total order — all pairs comparable" in {
    val ord = summon[Ordering[Level]]
    for
      l1 <- Level.values
      l2 <- Level.values
    do
      if l1 != l2 then
        (ord.lt(l1, l2) || ord.gt(l1, l2)) shouldBe true
  }
