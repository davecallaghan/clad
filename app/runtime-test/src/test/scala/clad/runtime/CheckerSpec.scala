package clad.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import clad.runtime.checkers.*
import scala.util.matching.Regex

class CheckerSpec extends AnyFlatSpec with Matchers:
  val testProp: PropertyId = PropertyId.unsafe("test_property")

  // RegexChecker Tests
  "RegexChecker in Any mode" should "match when at least one pattern is found" in {
    val checker = RegexChecker(testProp, Seq("hello".r, "world".r), MatchMode.Any)
    val artifact = PromptArtifact("hello there")
    checker.check(artifact) shouldBe true
  }

  it should "not match when no patterns are found" in {
    val checker = RegexChecker(testProp, Seq("foo".r, "bar".r), MatchMode.Any)
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe false
  }

  it should "match with multiple patterns present" in {
    val checker = RegexChecker(testProp, Seq("hello".r, "world".r), MatchMode.Any)
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe true
  }

  "RegexChecker in All mode" should "match when all patterns are found" in {
    val checker = RegexChecker(testProp, Seq("hello".r, "world".r), MatchMode.All)
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe true
  }

  it should "not match when only some patterns are found" in {
    val checker = RegexChecker(testProp, Seq("hello".r, "foo".r), MatchMode.All)
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe false
  }

  "RegexChecker" should "handle empty content" in {
    val checker = RegexChecker(testProp, Seq("hello".r), MatchMode.Any)
    val artifact = PromptArtifact("")
    checker.check(artifact) shouldBe false
  }

  it should "expose propertyId" in {
    val checker = RegexChecker(testProp, Seq("hello".r), MatchMode.Any)
    checker.propertyId shouldBe testProp
  }

  // KeywordChecker Tests
  "KeywordChecker" should "detect keywords case-insensitively" in {
    val checker = KeywordChecker(testProp, Set("Hello", "WORLD"))
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe true
  }

  it should "return false when keywords are absent" in {
    val checker = KeywordChecker(testProp, Set("foo", "bar"))
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe false
  }

  it should "respect threshold" in {
    val checker = KeywordChecker(testProp, Set("hello", "world", "foo"), threshold = 2)
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe true
  }

  it should "return false when below threshold" in {
    val checker = KeywordChecker(testProp, Set("hello", "foo", "bar"), threshold = 2)
    val artifact = PromptArtifact("hello world")
    checker.check(artifact) shouldBe false
  }

  it should "handle empty content" in {
    val checker = KeywordChecker(testProp, Set("hello"))
    val artifact = PromptArtifact("")
    checker.check(artifact) shouldBe false
  }

  // MetadataRequirement Tests
  "MetadataRequirement.KeyExists" should "return true when key is present" in {
    val req = MetadataRequirement.KeyExists("author")
    req.isSatisfied(Map("author" -> "alice")) shouldBe true
  }

  it should "return false when key is missing" in {
    val req = MetadataRequirement.KeyExists("author")
    req.isSatisfied(Map("title" -> "test")) shouldBe false
  }

  "MetadataRequirement.KeyEquals" should "return true when key matches value" in {
    val req = MetadataRequirement.KeyEquals("author", "alice")
    req.isSatisfied(Map("author" -> "alice")) shouldBe true
  }

  it should "return false when value mismatches" in {
    val req = MetadataRequirement.KeyEquals("author", "alice")
    req.isSatisfied(Map("author" -> "bob")) shouldBe false
  }

  "MetadataRequirement.KeyMatches" should "return true when pattern matches" in {
    val req = MetadataRequirement.KeyMatches("version", "^v\\d+".r)
    req.isSatisfied(Map("version" -> "v123")) shouldBe true
  }

  it should "return false when pattern does not match" in {
    val req = MetadataRequirement.KeyMatches("version", "^v\\d+".r)
    req.isSatisfied(Map("version" -> "abc")) shouldBe false
  }

  "MetadataRequirement.AllOf" should "return true when all requirements are met" in {
    val req = MetadataRequirement.AllOf(Seq(
      MetadataRequirement.KeyExists("author"),
      MetadataRequirement.KeyEquals("status", "approved")
    ))
    req.isSatisfied(Map("author" -> "alice", "status" -> "approved")) shouldBe true
  }

  it should "return false when some requirements are not met" in {
    val req = MetadataRequirement.AllOf(Seq(
      MetadataRequirement.KeyExists("author"),
      MetadataRequirement.KeyEquals("status", "approved")
    ))
    req.isSatisfied(Map("author" -> "alice", "status" -> "pending")) shouldBe false
  }

  "MetadataRequirement.AnyOf" should "return true when at least one requirement is met" in {
    val req = MetadataRequirement.AnyOf(Seq(
      MetadataRequirement.KeyExists("author"),
      MetadataRequirement.KeyExists("reviewer")
    ))
    req.isSatisfied(Map("author" -> "alice")) shouldBe true
  }

  it should "return false when no requirements are met" in {
    val req = MetadataRequirement.AnyOf(Seq(
      MetadataRequirement.KeyExists("author"),
      MetadataRequirement.KeyExists("reviewer")
    ))
    req.isSatisfied(Map("title" -> "test")) shouldBe false
  }

  // StructuralChecker Tests
  "StructuralChecker" should "delegate to MetadataRequirement" in {
    val req = MetadataRequirement.KeyExists("author")
    val checker = StructuralChecker(testProp, req)
    val artifact = PromptArtifact("content", Map("author" -> "alice"))
    checker.check(artifact) shouldBe true
  }

  it should "return false when requirement is not met" in {
    val req = MetadataRequirement.KeyEquals("status", "approved")
    val checker = StructuralChecker(testProp, req)
    val artifact = PromptArtifact("content", Map("status" -> "pending"))
    checker.check(artifact) shouldBe false
  }

  // CompositeChecker Tests
  "CompositeChecker in All mode" should "return true when all checkers detect" in {
    val checker1 = RegexChecker(PropertyId.unsafe("prop1"), Seq("hello".r), MatchMode.Any)
    val checker2 = KeywordChecker(PropertyId.unsafe("prop2"), Set("world"))
    val composite = CompositeChecker(testProp, Seq(checker1, checker2), MatchMode.All)
    val artifact = PromptArtifact("hello world")
    composite.check(artifact) shouldBe true
  }

  it should "return false when some checkers fail" in {
    val checker1 = RegexChecker(PropertyId.unsafe("prop1"), Seq("hello".r), MatchMode.Any)
    val checker2 = KeywordChecker(PropertyId.unsafe("prop2"), Set("foo"))
    val composite = CompositeChecker(testProp, Seq(checker1, checker2), MatchMode.All)
    val artifact = PromptArtifact("hello world")
    composite.check(artifact) shouldBe false
  }

  "CompositeChecker in Any mode" should "return true when at least one checker detects" in {
    val checker1 = RegexChecker(PropertyId.unsafe("prop1"), Seq("foo".r), MatchMode.Any)
    val checker2 = KeywordChecker(PropertyId.unsafe("prop2"), Set("world"))
    val composite = CompositeChecker(testProp, Seq(checker1, checker2), MatchMode.Any)
    val artifact = PromptArtifact("hello world")
    composite.check(artifact) shouldBe true
  }
