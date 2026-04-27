// code/core/src/main/scala/clad/core/Component.scala
package clad.core

sealed trait Requirement
object Requirement:
  case class Hard(description: String) extends Requirement
  case class Soft(description: String) extends Requirement

case class ComponentSpec(
  id: String,
  surfaces: Set[Surface],
  constraints: Set[Constraint],
  hardRequirements: Set[Requirement.Hard],
  softRequirements: Set[Requirement.Soft]
)

object ComponentComposition:
  sealed trait ComposeError
  case class OverlappingSurfaces(overlap: Set[Surface]) extends ComposeError

  def compose(g1: ComponentSpec, g2: ComponentSpec): Either[ComposeError, ComponentSpec] =
    val overlap = g1.surfaces.intersect(g2.surfaces)
    if overlap.nonEmpty then
      Left(OverlappingSurfaces(overlap))
    else Right(ComponentSpec(
      id = if g1.id == "∅" then g2.id
           else if g2.id == "∅" then g1.id
           else s"${g1.id}⊕${g2.id}",
      surfaces = g1.surfaces ++ g2.surfaces,
      constraints = g1.constraints ++ g2.constraints,
      hardRequirements = g1.hardRequirements ++ g2.hardRequirements,
      softRequirements = g1.softRequirements ++ g2.softRequirements
    ))

  val empty: ComponentSpec = ComponentSpec(
    id = "∅",
    surfaces = Set.empty,
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set.empty
  )

object KnownComponents:
  val EPG: ComponentSpec = ComponentSpec(
    id = "EPG",
    surfaces = Set(Surface.Prompt),
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set.empty
  )

  val ROC: ComponentSpec = ComponentSpec(
    id = "ROC",
    surfaces = Set(Surface.Output, Surface.Delivery),
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set(Requirement.Soft("handoff from EPG"))
  )

  val MDR: ComponentSpec = ComponentSpec(
    id = "MDR",
    surfaces = Set(Surface.Input, Surface.Config),
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set(
      Requirement.Soft("audit records from EPG"),
      Requirement.Soft("audit records from ROC")
    )
  )
