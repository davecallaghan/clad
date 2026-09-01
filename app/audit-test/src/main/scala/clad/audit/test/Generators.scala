package clad.audit.test

import org.scalacheck.{Arbitrary, Gen}
import clad.core.test.{Generators => CoreGen}
import clad.evaluation.test.{Generators => EvalGen}
import clad.evaluation.*
import clad.runtime.*
import clad.runtime.test.{Generators => RuntimeGen}
import clad.audit.*
import java.time.Instant

object Generators:
  val genSignature: Gen[Signature] =
    for
      bytes <- Gen.listOfN(32, Gen.choose(0.toByte, 127.toByte)).map(_.toArray)
      keyId <- Gen.alphaNumStr.map(s => s"key_$s")
    yield Signature(bytes, keyId, "HmacSHA256")

  given Arbitrary[Signature] = Arbitrary(genSignature)

  def genSignedAuditRecord(kms: KeyManagementService): Gen[SignedAuditRecord] =
    RuntimeGen.genAuditRecord.map { record =>
      SignedAuditRecord.sign(record, kms).toOption.get
    }
