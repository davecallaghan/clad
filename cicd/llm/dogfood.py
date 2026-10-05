"""Dogfood (spec section 5.6): run every gate against cicd/llm/ and against the whole repository.

Gates 1 (invented_limits) and 6 (safeguard_removal) must pass on the kit's own code; this exits
non-zero if either does not. Every other result is printed as found, including NOT_RUN and
INEXPRESSIBLE: they are reported, not hidden.

    python -m cicd.llm.dogfood [--base <ref>]   # base for safeguard_removal (default: merge-base with master)
"""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import (behavioural_harness, completion_evidence, governed_write, invented_limits,
                            provenance, review_findings, ruff_profile, safeguard_removal)
from cicd.llm.reference_pipeline import run_pipeline

KIT_PREFIX = "cicd/llm/"


def _scoped(cfg: dict, root: Path, gate, prefix: str) -> core.Scope:
    sec = cfg["gates"][gate.GATE_ID]
    sc = core.derive_scope(root, sec["include"], sec.get("exclude", []))
    return core.Scope(root, [f for f in sc.files if f.startswith(prefix)])


def _line(label: str, r: core.GateResult) -> dict:
    print(f"{label:<48} {r.verdict.value:<13} scope={r.scope_size:<4} findings={len(r.findings)} sub={r.sub_checks}")
    for f in r.findings:
        print(f"      {f.file}:{f.line} {f.rule} | {f.message}")
    return {"label": label, **r.to_dict()}


def _reference_rows(root: Path) -> list[dict]:
    """Stored rows from the reference pipeline with a model that emits for some records only."""
    records = json.loads((root / "cicd/llm/reference_records.json").read_text(encoding="utf-8"))
    first = records[0]["document_id"]
    return run_pipeline(records, lambda r: {"severity": "high", "finding_kind": "gap"} if r["document_id"] == first else {})


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--base", help="base ref for safeguard_removal")
    args = ap.parse_args()
    root, cfg = core.repo_root(), core.load_config()
    out = []
    own = {}
    for gate in (invented_limits, governed_write, ruff_profile):
        own[gate.GATE_ID] = gate.run(cfg, _scoped(cfg, root, gate, KIT_PREFIX), {})
        out.append(_line(f"{gate.GATE_ID} [cicd/llm]", own[gate.GATE_ID]))
        out.append(_line(f"{gate.GATE_ID} [whole repo]", gate.run(cfg, _scoped(cfg, root, gate, ""), {})))
    base = args.base or core.git(root, "merge-base", "master", "HEAD").strip()
    o = {"base": base, "head": "HEAD"}
    own["safeguard_removal"] = safeguard_removal.run(cfg, safeguard_removal.diff_scope(root, cfg, o), o)
    out.append(_line(f"safeguard_removal [{base}..HEAD]", own["safeguard_removal"]))
    out.append(_line("behavioural_harness [reference pipeline]",
                     behavioural_harness.run(cfg, behavioural_harness.field_scope(root, cfg), {})))
    with tempfile.TemporaryDirectory() as d:
        (Path(d) / "findings.governed.jsonl").write_text(
            "".join(json.dumps(x) + "\n" for x in _reference_rows(root)), encoding="utf-8")
        po = {"outputs_dir": d, "repo_root": str(root)}
        out.append(_line("provenance [reference pipeline output]",
                         provenance.run(cfg, provenance.output_scope(root, cfg, po), po)))
    out.append(_line("provenance [whole repo]",
                     provenance.run(cfg, provenance.output_scope(root, cfg, {}), {"repo_root": str(root)})))
    out.append(_line("review_findings [no review input supplied]",
                     review_findings.run(cfg, review_findings.review_scope(root, cfg, {}), {})))
    out.append(_line("completion_evidence [config completion path]",
                     completion_evidence.run(cfg, completion_evidence.completion_scope(root, cfg, {}), {})))
    core.append_ledger({"gate": "dogfood", "results": out})
    must = {k: own[k].verdict for k in ("invented_limits", "safeguard_removal")}
    ok = all(v is Verdict.SATISFIED for v in must.values())
    print(f"\ngates 1 and 6 on the kit's own code: {', '.join(f'{k}={v.value}' for k, v in must.items())}"
          f" -> {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
