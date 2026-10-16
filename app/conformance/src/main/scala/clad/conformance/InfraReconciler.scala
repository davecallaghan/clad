package clad.conformance

import java.time.Instant

object InfraReconciler:
  def unmatched(b: Bundle, inWindow: Instant => Boolean): Vector[InfraEntry] =
    val gilIds = b.gil.getOrElse(Vector.empty).map(_.interactionId.value).toSet
    b.infra.getOrElse(Vector.empty).filter(e => inWindow(e.ts) && !e.interactionId.exists(gilIds.contains))
