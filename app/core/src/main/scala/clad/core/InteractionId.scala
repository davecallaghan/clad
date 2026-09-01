package clad.core

/** The identifier of a single governed AI interaction — `identifier(i)` in the
  * meta-framework, where `i ∈ I`.
  *
  * This lives in `core` rather than alongside the interaction log because every audit
  * record is indexed by it (`A_g(i, t)`), and `runtime` — where audit records are
  * defined — cannot depend on `integrity`. Audit Linkability is a framework-level
  * design requirement, not a monitoring detail.
  */
opaque type InteractionId = String

object InteractionId:
  def apply(value: String): InteractionId = value
  def generate(): InteractionId = java.util.UUID.randomUUID().toString
  extension (id: InteractionId) def value: String = id
