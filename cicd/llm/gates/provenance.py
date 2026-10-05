"""Gate 3: provenance (kind = deciding).

Exists to catch: grade reversion, which no count can see (constraint-reversion, "Grade
reversion", "Why counting cannot see it"); the only decidable question is where a governed value
came from ("Grade reversion requires provenance").

Every governed field `f` is stored with `f__origin` in exactly three origins: model_emitted,
code_supplied, promoted ("Three origins, not two"). VIOLATED when the origin is missing or
unknown, or when code_supplied appears on a field whose contract does not allow it. `promoted`
is never flagged as such: the two-origin design misreports correct promotions as violations, fires
constantly on correct behaviour, and gets switched off. A promotion must name its source in
`f__promoted_from` (v2: a "promoted" label on a value nothing emitted was otherwise unfalsifiable). The contract's ungraded marker stored
as code_supplied is accepted (it records that the model emitted nothing; PLAN interpretation 7).

Scope: stored rows in `<target>.governed.jsonl` files, derived from the repository (config
`outputs` globs) or from a run directory (`--outputs-dir`).
"""

from __future__ import annotations

import json
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope
from cicd.llm.governed import load_contract

GATE_ID = "provenance"
KIND = "deciding"
# constraint-reversion, "Three origins, not two".
ORIGINS = ("model_emitted", "code_supplied", "promoted")
SUFFIX = ".governed.jsonl"
BLIND_SPOTS = [
    "governed rows that are never written to a checked output file",
    "an origin recorded falsely by the code that writes it (checked behaviourally by gate 4, not here)",
]


def output_scope(root: Path, config: dict, opts: dict) -> Scope:
    cfg = core.gate_section(config, GATE_ID)
    if opts.get("outputs_dir"):
        d = Path(opts["outputs_dir"]).resolve()
        files = sorted(str(p.relative_to(d)) for p in d.rglob("*")
                       if p.is_file() and any(core.glob_match(str(p.relative_to(d)), g) for g in cfg["outputs"]))
        return Scope(root=d, files=files)
    return core.derive_scope(root, cfg["outputs"], cfg.get("exclude", []))


def check_row(rel: str, line: int, text: str, row: dict, spec: dict) -> list[Finding]:
    h, out = core.line_hash(text), []
    marker = spec.get("ungraded_marker")
    for name, fspec in spec["fields"].items():
        origin = row.get(f"{name}__origin")
        if name not in row:
            out.append(Finding(rel, line, h, "missing_field", f"governed field {name!r} is absent from the row"))
        elif f"{name}__origin" not in row:
            out.append(Finding(rel, line, h, "missing_origin", f"{name!r} is stored without {name}__origin"))
        elif origin not in ORIGINS:
            out.append(Finding(rel, line, h, "unknown_origin", f"{name}__origin = {origin!r} is not one of {ORIGINS}"))
        elif origin == "code_supplied" and "code_supplied" not in fspec.get("origins", []) and row[name] != marker:
            out.append(Finding(rel, line, h, "code_supplied_disallowed",
                               f"{name} = {row[name]!r} was supplied by code; the contract does not allow it"))
        elif origin == "promoted" and not row.get(f"{name}__promoted_from"):
            out.append(Finding(rel, line, h, "promotion_without_source",
                               f"{name} is marked promoted but {name}__promoted_from does not say what was promoted"))
    return out


def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    cfg = core.gate_section(config, GATE_ID)
    contract = load_contract(_contract_root(scope, opts) / cfg["contract"])
    targets = contract.get("targets", {})
    findings, unparseable, rows = [], [], 0
    for rel in scope.files:
        target = Path(rel).name[: -len(SUFFIX)]
        lines = (scope.root / rel).read_text(encoding="utf-8").splitlines()
        if target not in targets:
            findings.append(Finding(rel, 1, core.line_hash(""), "unknown_target",
                                    f"output for target {target!r}, which the contract does not govern"))
        for i, text in enumerate(lines if target in targets else [], start=1):
            try:
                row = json.loads(text)
            except json.JSONDecodeError as exc:
                unparseable.append(f"{rel}:{i}")
                findings.append(Finding(rel, i, core.line_hash(text), "unparseable", f"not JSON: {exc}"))
                row = None
            if row is not None:
                rows += 1
                findings += check_row(rel, i, text, row, targets[target])
    if rows == 0 and not findings:
        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)
    violations = [f for f in findings if f.rule != "unparseable"]
    verdict = Verdict.VIOLATED if violations else Verdict.NOT_RUN if unparseable else Verdict.SATISFIED
    return GateResult(GATE_ID, verdict, KIND, findings=findings, scope_size=len(scope),
                      blind_spots=BLIND_SPOTS, details={"rows_checked": rows, "unparseable": unparseable})


def _contract_root(scope: Scope, opts: dict | None) -> Path:
    """Where the contract path resolves: the repository, even when rows come from a run dir."""
    return Path(opts["repo_root"]) if opts and opts.get("repo_root") else scope.root


def run_canary(canary_dir: Path) -> GateResult:
    config, opts = core.canary_setup(canary_dir)
    return run(config, output_scope(canary_dir, config, opts), opts)


def _args(ap) -> None:
    ap.add_argument("--outputs-dir", dest="outputs_dir", help="scan stored rows in this run directory")


def _scope(root: Path, config: dict, args) -> Scope:
    return output_scope(root, config, vars(args))


def _run_cli(config: dict, scope: Scope, opts: dict) -> GateResult:
    return run(config, scope, {**opts, "repo_root": str((opts.get("root") or core.repo_root()))})


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, _run_cli, __doc__, add_args=_args, scope_fn=_scope))
