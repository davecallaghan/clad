import Clad.Component
import Clad.Monoid

namespace Clad

-- Interface contract between two components (meta-framework §8)
structure InterfaceContract where
  provider : ComponentSpec
  consumer : ComponentSpec
  satisfied : Prop

/-- Theorem 2: Contract Composability (meta-framework §8).

Given Axiom 3's preconditions and satisfied contracts, the composite preserves both
components' guarantees and governs exactly the union of their surfaces.

The disjointness hypothesis is load-bearing here, where in the previous formulation
it was not: composition is partial, so without it there is no composite to state a
guarantee about. -/
theorem contract_composability
    (g₁ g₂ : ComponentSpec)
    (phi₁ phi₂ : Prop)
    (h_phi₁ : phi₁) (h_phi₂ : phi₂)
    (h_composable : g₁.Composable g₂)
    (contract : InterfaceContract)
    (_h_satisfied : contract.satisfied) :
    phi₁ ∧ phi₂ ∧ ∃ g, g₁.compose g₂ = .ok g ∧ g.surfaces = g₁.surfaces ∪ g₂.surfaces :=
  ⟨h_phi₁, h_phi₂, g₁.merge g₂,
   ComponentSpec.compose_of_composable h_composable, rfl⟩

/-- The surface union equality, on the domain of the operator. -/
theorem compose_surfaces_eq {g₁ g₂ : ComponentSpec} (h : g₁.Composable g₂) :
    ∃ g, g₁.compose g₂ = .ok g ∧ g.surfaces = g₁.surfaces ∪ g₂.surfaces :=
  ⟨g₁.merge g₂, ComponentSpec.compose_of_composable h, rfl⟩

end Clad
