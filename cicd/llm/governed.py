"""`write_gate`: the write-time gate for governed values (spec Gate 2, library half).

Exists to catch grade reversion at the write: a judgement the model did not emit, fabricated in
code and stored as though derived (constraint-reversion, "Grade reversion": seventy-five red
flags uniformly graded "high").

Its shape answers the three ways the original write gate died (constraint-reversion,
"Detection surfaces", Group 1):
- `contract` is a required positional parameter with no default. The original took it as an
  optional keyword defaulting to None; its largest caller omitted it, so the gate never ran.
- A governed field absent from a row is an error, not a skip. The original was configured for
  `finding_kind` while rows carried `finding_type`, and skipped silently.
- An in-gate canary plants one violating value per governed field on every call, derived from
  the contract and mirroring the caller's own row, and fails if any planted row passes or if
  detection is partial ("Liveness canaries"). Planted rows are never returned.

Quarantined rows are returned, never dropped: a quarantine without an alarm is quiet data loss
("The asymmetry, as it appears in a real test inventory"). Callers must handle them.
"""

from __future__ import annotations

import tomllib
from dataclasses import dataclass, field
from pathlib import Path

from cicd.llm.safeguard import safeguard

EMPTY_ENUMERATION = "empty enumeration is not a clean surface"


class ContractInexpressible(Exception):
    """The contract cannot be checked: no governed fields, or a field with an empty domain."""


class MissingGovernedField(Exception):
    """A governed field is absent from a row. Never skipped: a mismatch must fail loudly."""


class CanaryFailure(Exception):
    """The in-gate canary was not caught (fully or partially): the gate is not live."""


@dataclass
class GateOutcome:
    accepted: list[dict] = field(default_factory=list)
    quarantined: list[dict] = field(default_factory=list)


def load_contract(path: Path) -> dict:
    with open(path, "rb") as fh:
        return tomllib.load(fh)


def target_spec(contract: dict, target: str) -> dict:
    targets = contract.get("targets", {})
    if target not in targets:
        raise ContractInexpressible(f"target {target!r} is not in the contract ({sorted(targets)})")
    spec = targets[target]
    fields = spec.get("fields", {})
    if not fields:
        raise ContractInexpressible(f"target {target!r} declares no governed fields: {EMPTY_ENUMERATION}")
    empty = [f for f, s in fields.items() if not s.get("domain")]
    if empty:
        raise ContractInexpressible(f"target {target!r} fields {empty} have an empty domain: {EMPTY_ENUMERATION}")
    return spec


def allowed_values(spec: dict, name: str) -> list:
    marker = spec.get("ungraded_marker")
    domain = list(spec["fields"][name]["domain"])
    return domain + ([marker] if marker is not None else [])


def _out_of_domain(spec: dict, row: dict) -> list[str]:
    """Governed fields of `row` holding a value outside the domain; raises if one is absent."""
    bad = []
    for name in spec["fields"]:
        if name not in row:
            raise MissingGovernedField(
                f"governed field {name!r} is absent from the row (row has {sorted(row)}). "
                "Fix the artifact's field name; do not alias it in the checker.")
        if row[name] not in allowed_values(spec, name):
            bad.append(name)
    return bad


def planted_value(spec: dict, name: str):
    """A value guaranteed outside the field's allowed values, derived from the contract."""
    return ("canary", name, tuple(allowed_values(spec, name)))


@safeguard
def plant_canary(spec: dict, template: dict) -> None:
    caught, missed = [], []
    for name in spec["fields"]:
        planted = dict(template, **{name: planted_value(spec, name)})
        (caught if name in _out_of_domain(spec, planted) else missed).append(name)
    if missed:
        detail = "partially" if caught else "entirely"
        raise CanaryFailure(f"in-gate canary {detail} missed: planted out-of-domain values in "
                            f"{missed} passed (caught: {caught}). The write gate is not live.")


@safeguard
def write_gate(rows, contract, target):
    """Validate governed fields of `rows` for `target`. Returns accepted and quarantined rows."""
    spec = target_spec(contract, target)
    rows = list(rows)
    template = dict(rows[0]) if rows else {n: allowed_values(spec, n)[0] for n in spec["fields"]}
    plant_canary(spec, template)  # safeguard
    outcome = GateOutcome()
    for row in rows:
        (outcome.quarantined if _out_of_domain(spec, row) else outcome.accepted).append(row)
    return outcome
