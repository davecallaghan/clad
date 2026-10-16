package clad.gateway.store

import clad.audit.{KeyManagementService, SignedAuditRecord}
import clad.integrity.{GilEntry, SignedDegradedRecord}
import clad.runtime.AuditRecord

trait GatewayStore:
  /** metadata must hold "project", "principal" and "tier". */
  def register(entry: GilEntry): Either[String, Unit]
  def appendLinked(build: Option[String] => AuditRecord, kms: KeyManagementService): Either[String, SignedAuditRecord]
  def degraded(r: SignedDegradedRecord): Either[String, Unit]
  /** Idempotent. */
  def putConfig(digest: String, project: String, json: String): Either[String, Unit]
  /** Idempotent. */
  def putArtifact(digest: String, json: String): Either[String, Unit]

object GatewayStore:
  val requiredKeys: Vector[String] = Vector("project", "principal", "tier")
