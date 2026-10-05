"""Held-out runner (spec section 5.5). Refuses to run unless the freeze verifies.

Reports, per gate, how many owner-supplied held-out fixtures were caught (VIOLATED) out of how
many. With no fixtures supplied it reports NOT_RUN and exits 2: the held-out phase has not run.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.freeze import FreezeViolation, verify_freeze
from cicd.llm.gates.liveness import discover_gates

HELDOUT = Path(__file__).resolve().parent / "heldout"


def fixtures(base: Path = HELDOUT) -> dict[str, list[Path]]:
    return {d.name: sorted(p for p in d.iterdir() if p.is_dir()) for d in sorted(base.glob("*")) if d.is_dir()}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dir", type=Path, default=HELDOUT, help="fixture set (default: cicd/llm/heldout)")
    base = ap.parse_args().dir
    try:
        record = verify_freeze()
    except FreezeViolation as exc:
        print(f"REFUSING TO RUN: {exc}")
        return Verdict.NOT_RUN.exit_code
    supplied = {g: fx for g, fx in fixtures(base).items() if fx}
    if not supplied:
        print(json.dumps({"verdict": Verdict.NOT_RUN.value, "freeze_commit": record["git_commit"],
                          "note": "no held-out fixtures supplied in cicd/llm/heldout/"}, indent=1))
        return Verdict.NOT_RUN.exit_code
    gates = discover_gates()
    report = {}
    for gate_id, fx in supplied.items():
        if gate_id not in gates:
            report[gate_id] = {"error": "no such gate"}
        else:
            caught = [f.name for f in fx if gates[gate_id].run_canary(f).verdict is Verdict.VIOLATED]
            report[gate_id] = {"caught": len(caught), "total": len(fx), "missed": sorted(set(f.name for f in fx) - set(caught))}
    out = {"freeze_commit": record["git_commit"], "fixture_set": str(base), "per_gate": report}
    print(json.dumps(out, indent=1, sort_keys=True))
    core.append_ledger({"gate": "heldout", **out})
    return 0


if __name__ == "__main__":
    sys.exit(main())
