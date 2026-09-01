package clad.core

sealed trait Requirement
object Requirement:
  case class Hard(description: String) extends Requirement
  case class Soft(description: String) extends Requirement

/** Identifies one evaluation function `E_g : artifact(S_g) × C_g → V`.
  *
  * The identity matters and is not derivable from the surfaces and constraints: two
  * components could govern the same constraint on the same surface through different
  * evaluators, and their audit records would then carry different evidentiary weight.
  */
opaque type EvaluatorId = String
object EvaluatorId:
  def apply(value: String): EvaluatorId = value
  extension (id: EvaluatorId) def value: String = id

/** A governance component, `g = (S_g, C_g, E_g, A_g, R_g)`.
  *
  * Four of the five tuple elements are fields here: `surfaces` is S, `constraints` is C,
  * `evaluators` is E, and R is split into hard and soft requirements.
  *
  * `A_g` — the audit records the component produces — is deliberately absent. Composition
  * sets `A = compose(A_g₁, A_g₂) via shared interaction_id`, which is not a binary
  * operation on components: the chain order is fixed by pipeline causality, a parameter
  * of neither operand. That is why the framework states its monoid result for `(S, C, E)`
  * and not for the audit component, and why a record's membership in a composed chain is
  * established at runtime by `AuditRecord.interactionId` rather than by a field here.
  */
case class ComponentSpec(
  id: String,
  surfaces: Set[Surface],
  constraints: Set[Constraint],
  hardRequirements: Set[Requirement.Hard],
  softRequirements: Set[Requirement.Soft],
  evaluators: Set[EvaluatorId] = Set.empty
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
      softRequirements = g1.softRequirements ++ g2.softRequirements,
      // E_g₁ ∪ E_g₂. The union is a function rather than a relation precisely because the
      // surfaces are disjoint, which is the condition just checked above.
      evaluators = g1.evaluators ++ g2.evaluators
    ))

  val empty: ComponentSpec = ComponentSpec(
    id = "∅",
    surfaces = Set.empty,
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set.empty,
    evaluators = Set.empty
  )

object KnownComponents:
  val EPG: ComponentSpec = ComponentSpec(
    id = "EPG",
    surfaces = Set(Surface.Prompt),
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set.empty,
    evaluators = Set(EvaluatorId("epg-prompt-evaluator"))
  )

  val ROC: ComponentSpec = ComponentSpec(
    id = "ROC",
    surfaces = Set(Surface.Output, Surface.Delivery),
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set(Requirement.Soft("handoff from EPG")),
    evaluators = Set(EvaluatorId("roc-output-evaluator"))
  )

  val MDR: ComponentSpec = ComponentSpec(
    id = "MDR",
    surfaces = Set(Surface.Input, Surface.Config),
    constraints = Set.empty,
    hardRequirements = Set.empty,
    softRequirements = Set(
      Requirement.Soft("audit records from EPG"),
      Requirement.Soft("audit records from ROC")
    ),
    evaluators = Set(EvaluatorId("mdr-monitor"))
  )
