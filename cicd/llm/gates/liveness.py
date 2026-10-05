"""Support B: liveness (checks on the checks; kind = deciding).

Exists to catch: a gate that runs and reports nothing because it can no longer see what it
checks. Evidence (constraint-reversion, "Detection surfaces", Group 1: five controls present,
configured and silent, where no output was indistinguishable from no findings; "Liveness
canaries": a gate is described as existing only after its canary has been seen to fire, and the
canary runner's own specification named four scripts and missed a fifth, so the set is derived
from the tree).

Discovers gates as the modules in `cicd/llm/gates/` that define `GATE_ID` (derived, asserted
non-empty), runs each against every `cicd/llm/canaries/<GATE_ID>/<canary>/`, and requires
VIOLATED from each. A gate without a canary, a canary that does not fire, and a canary directory
for no gate are all VIOLATED. Limit (same section): a canary rides inside the control, so this
says nothing about a gate nothing invokes; that is the workflow's job.
"""

from __future__ import annotations

import importlib
import json
import pkgutil
import sys
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult

GATES_DIR = Path(__file__).resolve().parent
CANARIES_DIR = core.KIT_DIR / "canaries"
BLIND_SPOTS = [
    "gates nothing invokes (a canary rides inside the control; see the workflow inventory)",
    "verdicts nothing acts on (branch protection)",
    "violation classes no canary represents",
]


def discover_gates() -> dict[str, object]:
    gates = {}
    for info in pkgutil.iter_modules([str(GATES_DIR)]):
        module = importlib.import_module(f"cicd.llm.gates.{info.name}")
        if hasattr(module, "GATE_ID"):
            gates[module.GATE_ID] = module
    return gates


def _flag(findings: list[Finding], rel: str, rule: str, message: str) -> None:
    findings.append(Finding(rel, 1, core.line_hash(message), rule, message))


def check() -> GateResult:
    gates = discover_gates()
    if not gates:
        return GateResult("liveness", Verdict.INEXPRESSIBLE, "deciding", blind_spots=BLIND_SPOTS,
                          notes=[f"no gates discovered in {GATES_DIR}: {core.EMPTY_ENUMERATION}"])
    findings: list[Finding] = []
    per_gate, n_canaries, n_fired = {}, 0, 0
    for gate_id, module in sorted(gates.items()):
        canaries = sorted(p for p in (CANARIES_DIR / gate_id).glob("*") if p.is_dir())
        if not canaries:
            _flag(findings, f"canaries/{gate_id}", "gate_without_canary", f"gate {gate_id} has no canary")
        fired = []
        for c in canaries:
            n_canaries += 1
            # No try here: an error inside a canary run is loud (a traceback and a non-zero exit),
            # and a try around a canary call is what safeguard_removal flags.
            verdict = module.run_canary(c).verdict
            if verdict is Verdict.VIOLATED:
                fired.append(c.name)
            else:
                _flag(findings, f"canaries/{gate_id}/{c.name}", "canary_did_not_fire",
                      f"{gate_id} returned {verdict.value} on canary {c.name}, not VIOLATED")
        n_fired += len(fired)
        per_gate[gate_id] = {"canaries": [c.name for c in canaries], "fired": fired}
    for orphan in sorted(p.name for p in CANARIES_DIR.glob("*") if p.is_dir() and p.name not in gates):
        _flag(findings, f"canaries/{orphan}", "orphan_canary", f"canaries/{orphan} belongs to no discovered gate")
    return GateResult("liveness", Verdict.VIOLATED if findings else Verdict.SATISFIED, "deciding",
                      findings=findings, scope_size=len(gates), blind_spots=BLIND_SPOTS,
                      details={"gates_discovered": len(gates),
                               "gates_with_canaries": sum(1 for g in per_gate.values() if g["canaries"]),
                               "canaries": n_canaries, "canaries_fired": n_fired, "per_gate": per_gate})


def main() -> int:
    ap = core.base_parser(__doc__)
    args = ap.parse_args()
    result = check()
    record = result.to_dict(core.repo_root(GATES_DIR))
    print(json.dumps(record, indent=1, sort_keys=True))
    d = result.details
    print(f"liveness: {d.get('gates_discovered', 0)} gates discovered, {d.get('canaries', 0)} canaries, "
          f"{d.get('canaries_fired', 0)} fired -> {result.verdict.value}", file=sys.stderr)
    if not args.no_ledger:
        core.append_ledger(record)
    return result.verdict.exit_code


if __name__ == "__main__":
    core.main_exit(main())
