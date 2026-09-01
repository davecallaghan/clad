package clad.audit.test

import clad.audit.*

object InMemoryKeyManagement:
  def apply(secretKey: Array[Byte], keyId: String): KeyManagementService =
    HmacKeyManagement(secretKey, keyId)

  val default: KeyManagementService =
    HmacKeyManagement("test-default-key-32bytes!1234567".getBytes("UTF-8"), "test-default")
