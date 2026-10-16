package clad.audit

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class Ed25519KeyManagementSpec extends AnyFunSuite with Matchers:

  test("a signature made with the private key verifies with a public-key-only instance"):
    val (signer, publicPem) = Ed25519KeyManagement.generate("k1")
    val sig = signer.sign("hello".getBytes("UTF-8")).toOption.get
    sig.algorithm shouldBe "Ed25519"
    sig.keyId shouldBe "k1"
    val verifier = Ed25519KeyManagement.fromPublicPem(publicPem, "k1").toOption.get
    verifier.verify("hello".getBytes("UTF-8"), sig) shouldBe Right(true)
    verifier.verify("hellO".getBytes("UTF-8"), sig) shouldBe Right(false)

  test("a public-key-only instance cannot sign"):
    val (_, publicPem) = Ed25519KeyManagement.generate("k1")
    val verifier = Ed25519KeyManagement.fromPublicPem(publicPem, "k1").toOption.get
    verifier.sign("x".getBytes("UTF-8")) shouldBe Left(KeyUnavailable("k1"))

  test("a malformed PEM is an error, not an exception"):
    Ed25519KeyManagement.fromPublicPem("-----BEGIN PUBLIC KEY-----\nnot base64!\n-----END PUBLIC KEY-----", "k1").isLeft shouldBe true

  test("a signature from another key does not verify"):
    val (a, _) = Ed25519KeyManagement.generate("a")
    val (_, bPem) = Ed25519KeyManagement.generate("b")
    val sig = a.sign("m".getBytes("UTF-8")).toOption.get
    Ed25519KeyManagement.fromPublicPem(bPem, "b").toOption.get.verify("m".getBytes("UTF-8"), sig) shouldBe Right(false)
