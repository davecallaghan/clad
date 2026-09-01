package clad.core

/** A control surface of the interaction pipeline.
  *
  * The framework's original partition is the five pipeline surfaces over the tuple
  * `(x, u, M, θ, o)`. `Evidence` is the sixth, over the amended tuple
  * `i = (x, u, M, θ, o, E)`, where `E` is the evidential basis an assertion is
  * permitted to rest on. It is a surface rather than an attribute of the prompt because
  * no presence check on the artifact can establish that an assertion *rested on* the
  * evidence cited for it.
  */
enum Surface:
  case Prompt, Input, Config, Output, Delivery, Evidence

enum Governability:
  case Full, Partial, External

/** How the evidential basis reaches the model, which is a deployment choice rather than
  * a property of the technology — and therefore determines the evidence surface's
  * governability class. */
enum EvidenceProvision:
  case Retrieved   // identified and versioned at inference time
  case Parametric  // absorbed in training weights, not identifiable per interaction

object Surface:

  /** The five surfaces whose governability is fixed by the technology. */
  private val fixedGovernability: Map[Surface, Governability] = Map(
    Surface.Prompt   -> Governability.Full,
    Surface.Input    -> Governability.Partial,
    Surface.Config   -> Governability.Partial,
    Surface.Output   -> Governability.Partial,
    Surface.Delivery -> Governability.Full
  )

  /** The original five-element partition, over the pipeline tuple. Composition of EPG,
    * ROC and MDR covers exactly this set — not every surface. */
  val pipeline: Set[Surface] =
    Set(Surface.Prompt, Surface.Input, Surface.Config, Surface.Output, Surface.Delivery)

  /** Governability of a surface under a given evidence deployment.
    *
    * Takes the deployment because the evidence surface is the only one whose class is
    * split by deployment choice: retrieval-provided evidence is identified at inference
    * time and so fully governable, while parametric evidence absorbed in training is
    * external. Flattening that into a constant would erase the distinction the surface
    * exists to make.
    */
  def governability(surface: Surface, evidence: EvidenceProvision): Governability =
    surface match
      case Surface.Evidence =>
        evidence match
          case EvidenceProvision.Retrieved  => Governability.Full
          case EvidenceProvision.Parametric => Governability.External
      case other => fixedGovernability(other)
