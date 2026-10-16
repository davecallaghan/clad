package clad.conformance

import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GoldenVectorsSpec extends AnyFunSuite with Matchers:
  test("published golden vectors match the Scala digest"):
    val src = scala.io.Source.fromResource("golden/digests.json")
    val vectors = try ujson.read(src.mkString).arr finally src.close()
    vectors.foreach { v =>
      val r = v("record")
      def opt(k: String) = r(k) match { case ujson.Null => None; case x => Some(x.str) }
      val t = Instant.parse(r("timestamp").str)
      val entries = r("entries").arr.toVector.map(e =>
        AuditEntry(Constraint.Obligation(PropertyId.unsafe(e("property").str), Level.Enterprise), e("constraintVersion").str,
          EvaluabilityClass.valueOf(e("evaluabilityClass").str), e("satisfied").bool, MechanicalDetail(true), t))
      val rec = AuditRecord(r("artifactDigest").str, entries, r("configDigest").str, t, opt("previousDigest"),
        opt("interactionId").map(InteractionId(_)), opt("component"), opt("surface").map(Surface.valueOf(_)), opt("outcome"))
      withClue(v("name").str)(rec.digest shouldBe v("digest").str)
    }
