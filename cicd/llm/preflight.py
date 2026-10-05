"""Preflight: the fast LLM gates on changed files (guidance 3.4).

Exists to catch an LLM-gate violation while the agent is still editing, before CI. Runs
invented_limits, ruff_profile, safeguard_removal (HEAD against the working tree) and governed_write's
call-site scan on the given paths (default: everything changed against HEAD, plus untracked files),
prints one line per gate and the JSON location, and exits non-zero on VIOLATED (1), or on NOT_RUN or
INEXPRESSIBLE (2). The slow gates (behavioural harness, mutation check) stay in CI.

A gate none of whose derived scope is among the given paths is reported as "not applicable": it does
not read those files. That is not a pass of the gate (PLAN interpretation 8). With no paths at all the
result is INEXPRESSIBLE: an empty enumeration is not a clean surface.
"""

from __future__ import annotations

import argparse
import ast
import json
import os
import sys
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import GateResult, Scope
from cicd.llm.gates import governed_write, invented_limits, ruff_profile, safeguard_removal
from cicd.llm.governed import load_contract

NOT_APPLICABLE = "not applicable"


def changed_paths(root: Path) -> list[str]:
    diff = core.git(root, "diff", "--name-only", "HEAD").split("\n")
    untracked = core.git(root, "ls-files", "--others", "--exclude-standard").split("\n")
    return sorted({p for p in diff + untracked if p and (root / p).exists()})


def normalise(root: Path, paths: list[str]) -> tuple[list[str], list[str]]:
    root = root.resolve()  # both sides resolved: /var and /private/var are one directory on macOS
    inside, outside = [], []
    for p in paths:
        full = (Path.cwd() / p).resolve() if not Path(p).is_absolute() else Path(p).resolve()
        if full.is_relative_to(root):
            inside.append(full.relative_to(root).as_posix())
        else:
            outside.append(p)
    return sorted(set(inside)), outside


def _restrict(scope: Scope, paths: set[str]) -> Scope:
    return Scope(scope.root, [f for f in scope.files if f in paths])


def callsite_scan(config: dict, scope: Scope) -> GateResult:
    """governed_write's call-site check only (the signature and alarm checks need the whole repo)."""
    gw = governed_write
    cfg = core.gate_section(config, gw.GATE_ID)
    targets = set(load_contract(scope.root / cfg["contract"]).get("targets", {}))
    if not targets:
        return core.empty_scope(gw.GATE_ID, gw.KIND, gw.BLIND_SPOTS)
    findings, gated, unresolved = [], 0, 0
    for rel in scope.files:
        text = (scope.root / rel).read_text(encoding="utf-8")
        tree, lines = ast.parse(text), text.splitlines()
        f, g, u = gw.scan_calls(rel, tree, lines, targets, cfg["writer_functions"])
        findings, gated, unresolved = findings + f + gw.sql_writes(rel, tree, lines, targets), gated + g, unresolved + u
    return GateResult(gw.GATE_ID, Verdict.VIOLATED if findings else Verdict.SATISFIED, gw.KIND,
                      findings=findings, scope_size=len(scope), blind_spots=gw.BLIND_SPOTS,
                      sub_checks={"call_sites": "RUN", "signature": "CI", "quarantine_alarm": "CI"},
                      details={"gated_writes": gated, "unresolved_writes": unresolved})


def _scopes(root: Path, config: dict, paths: set[str]) -> dict:
    def globbed(gate):
        sec = core.gate_section(config, gate)
        return _restrict(core.derive_scope(root, sec["include"], sec.get("exclude", [])), paths)

    sg_opts = {"base": "HEAD", "head": "WORKTREE"}
    return {
        invented_limits.GATE_ID: (lambda s: invented_limits.run(config, s, {}), lambda: globbed(invented_limits.GATE_ID)),
        ruff_profile.GATE_ID: (lambda s: ruff_profile.run(config, s, {}), lambda: globbed(ruff_profile.GATE_ID)),
        safeguard_removal.GATE_ID: (lambda s: safeguard_removal.run(config, s, sg_opts),
                                    lambda: _restrict(safeguard_removal.diff_scope(root, config, sg_opts), paths)),
        governed_write.GATE_ID: (lambda s: callsite_scan(config, s), lambda: globbed(governed_write.GATE_ID)),
    }


def run_paths(root: Path, paths: list[str], config: dict | None = None) -> tuple[int, dict]:
    config = config or core.load_config()
    inside, outside = normalise(root, paths)
    report = {"root": str(root), "paths": inside, "outside_repo": outside, "gates": {}}
    if not inside:
        report["verdict"] = Verdict.INEXPRESSIBLE.value
        report["note"] = f"no paths to check: {core.EMPTY_ENUMERATION}"
        return Verdict.INEXPRESSIBLE.exit_code, report
    for gate, (run, scope_fn) in _scopes(root, config, set(inside)).items():
        try:
            scope = scope_fn()
            result = run(scope) if len(scope) else None
        except core.READ_ERRORS as exc:
            scope, result = None, core.not_run(gate, "", [], f"{type(exc).__name__}: {exc}")
        report["gates"][gate] = NOT_APPLICABLE if result is None else result.to_dict(root)
    verdicts = [g["verdict"] for g in report["gates"].values() if g != NOT_APPLICABLE]
    if Verdict.VIOLATED.value in verdicts:
        code = Verdict.VIOLATED.exit_code
    elif any(v in (Verdict.NOT_RUN.value, Verdict.INEXPRESSIBLE.value) for v in verdicts):
        code = Verdict.NOT_RUN.exit_code
    else:
        code = Verdict.SATISFIED.exit_code
    report["exit_code"] = code
    return code, report


def summary_lines(report: dict) -> list[str]:
    if "note" in report:
        return [f"preflight: {report['verdict']} ({report['note']})"]
    lines = []
    for gate, r in report["gates"].items():
        if r == NOT_APPLICABLE:
            lines.append(f"{gate:<18} {NOT_APPLICABLE} (no given path is in this gate's scope)")
        else:
            not_run = sorted(k for k, v in r["sub_checks"].items() if v == Verdict.NOT_RUN.value)
            extra = f"; NOT_RUN sub-checks: {not_run}" if not_run else ""
            lines.append(f"{gate:<18} {r['verdict']:<13} ({r['scope_size']} files, {len(r['findings'])} findings{extra})"
                         + (f" {r['notes']}" if r["notes"] else ""))
            lines += [f"    {f['file']}:{f['line']} {f['rule']}: {f['message']}" for f in r["findings"]]
    if report["outside_repo"]:
        lines.append(f"ignored (outside {report['root']}): {report['outside_repo']}")
    return lines


def output_path() -> Path:
    return Path(os.environ.get("CICD_LLM_PREFLIGHT_JSON", core.DEFAULT_LEDGER.parent / "preflight.json"))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paths", nargs="*", help="files to check (default: changed against HEAD, plus untracked)")
    args = ap.parse_args()
    root = core.repo_root()
    code, report = run_paths(root, args.paths or changed_paths(root))
    out = output_path()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print("\n".join(summary_lines(report)))
    print(f"json: {out}")
    return code


if __name__ == "__main__":
    sys.exit(main())
