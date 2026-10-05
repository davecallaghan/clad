"""Gate 4: behavioural_harness (dependence on the model's output; kind = deciding).

Exists to catch: a governed value that code assigned rather than carried from the model, which
every count reconciles and no output property reveals in the non-degenerate case
(constraint-reversion, "Grade reversion", "Why counting cannot see it"). The test is the
Sensitive condition of book/chapters/taxonomy.tex, Definition "Grounded assertion": had the
evidence not supported the value, the value would not have been produced; operationally,
ablation.

The harness drives the configured pipeline entry with a stub model:
- R1: change the model's emitted value of a governed field; the stored value must change to match.
- R2: the model emits nothing for a field; the stored value must be the contract's ungraded
  marker, never a default grade.
- R3: recorded paraphrase pairs give equal governed outputs (NOT_RUN without fixtures).
- R4: repeated identical input gives identical governed outputs (NOT_RUN without repeat_count).
Non-vacuity: at least one R1 and one R2 assertion per governed field, else VIOLATED.
"""

from __future__ import annotations

import importlib
import importlib.util
import json
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope
from cicd.llm.governed import load_contract

GATE_ID = "behavioural_harness"
KIND = "deciding"
BLIND_SPOTS = [
    "runs with a stub model only: behaviour on real model output is not observed",
    "fields not declared in the contract",
    "code paths the configured records do not reach",
]


def field_scope(root: Path, config: dict) -> Scope:
    """The governed fields of the configured target, derived from the contract."""
    cfg = core.gate_section(config, GATE_ID)
    contract = load_contract(root / cfg["contract"])
    spec = contract.get("targets", {}).get(cfg["target"], {})
    return Scope(root=root, files=sorted(spec.get("fields", {})))


def load_entry(root: Path, entry: str):
    target, func = entry.split(":")
    if target.endswith(".py"):
        path = root / target
        spec = importlib.util.spec_from_file_location(f"_harness_{abs(hash(str(path)))}", path)
        if spec is None or spec.loader is None:
            raise core.GateError(f"cannot load pipeline entry {entry!r}")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
    else:
        module = importlib.import_module(target)
    return getattr(module, func)


class _Stub:
    """A model that emits exactly the values it is given, for every record."""

    def __init__(self, emitted: dict):
        self.emitted = emitted

    def __call__(self, record: dict) -> dict:
        return dict(self.emitted)


def _stored(entry, records: list[dict], emitted: dict, name: str) -> list:
    return [row.get(name, ("absent",)) for row in entry(records, _Stub(emitted))]


class _Run:
    def __init__(self, entry, records, spec, cfg_file: str):
        self.entry, self.records, self.spec, self.cfg_file = entry, records, spec, cfg_file
        self.findings: list[Finding] = []
        self.counts: dict[str, dict[str, int]] = {}

    def baseline(self) -> dict:
        return {n: s["domain"][0] for n, s in self.spec["fields"].items()}

    def flag(self, rule: str, message: str) -> None:
        self.findings.append(Finding(self.cfg_file, 1, core.line_hash(message), rule, message))

    def r1(self, name: str) -> None:
        domain = self.spec["fields"][name]["domain"]
        for value in domain:
            stored = _stored(self.entry, self.records, {**self.baseline(), name: value}, name)
            self.counts[name]["R1"] += len(stored)
            if any(v != value for v in stored):
                self.flag("r1_not_dependent",
                          f"{name}: model emitted {value!r} but the pipeline stored {sorted(set(map(str, stored)))}")

    def r2(self, name: str) -> None:
        marker = self.spec.get("ungraded_marker")
        if marker is None:
            self.flag("no_ungraded_marker", f"the contract declares no ungraded_marker; R2 for {name} cannot be asserted")
            return
        emitted = {k: v for k, v in self.baseline().items() if k != name}
        stored = _stored(self.entry, self.records, emitted, name)
        self.counts[name]["R2"] += len(stored)
        if any(v != marker for v in stored):
            self.flag("r2_default_grade",
                      f"{name}: model emitted nothing but the pipeline stored {sorted(set(map(str, stored)))}, not {marker!r}")

    def non_vacuity(self, name: str) -> None:
        c = self.counts[name]
        if len(self.spec["fields"][name]["domain"]) <= 1 or c["R1"] == 0 or c["R2"] == 0:
            self.flag("vacuous", f"{name}: R1 asserted {c['R1']} time(s), R2 {c['R2']} (both must be non-zero, "
                                 "over at least two domain values)")


def r3(root: Path, cfg: dict, entry, run: _Run) -> str:
    if not cfg.get("paraphrase_pairs"):
        return Verdict.NOT_RUN.value
    pairs = json.loads((root / cfg["paraphrase_pairs"]).read_text(encoding="utf-8"))
    for i, pair in enumerate(pairs):
        a = entry([pair["a"]["record"]], _Stub(pair["a"]["emitted"]))
        b = entry([pair["b"]["record"]], _Stub(pair["b"]["emitted"]))
        for name in run.spec["fields"]:
            if [r.get(name) for r in a] != [r.get(name) for r in b]:
                run.flag("r3_paraphrase_differs", f"pair {i}: {name} differs between paraphrases")
    return "RUN"


def r4(cfg: dict, run: _Run) -> str:
    n = cfg.get("repeat_count")
    if n is None:
        return Verdict.NOT_RUN.value
    for name in run.spec["fields"]:
        outputs = [_stored(run.entry, run.records, run.baseline(), name) for _ in range(n)]
        if any(o != outputs[0] for o in outputs):
            run.flag("r4_not_repeatable", f"{name}: {n} identical runs gave different stored values")
    return "RUN"


def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    cfg = core.gate_section(config, GATE_ID)
    if len(scope) == 0:
        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)
    spec = load_contract(scope.root / cfg["contract"])["targets"][cfg["target"]]
    records = json.loads((scope.root / cfg["records"]).read_text(encoding="utf-8"))
    entry = load_entry(scope.root, cfg["entry"])
    h = _Run(entry, records, spec, cfg["contract"])
    for name in scope.files:
        h.counts[name] = {"R1": 0, "R2": 0}
        h.r1(name)
        h.r2(name)
        h.non_vacuity(name)
    sub = {"R1": "RUN", "R2": "RUN", "R3": r3(scope.root, cfg, entry, h), "R4": r4(cfg, h)}
    return GateResult(GATE_ID, Verdict.VIOLATED if h.findings else Verdict.SATISFIED, KIND,
                      findings=h.findings, scope_size=len(scope), blind_spots=BLIND_SPOTS,
                      sub_checks=sub, details={"assertions": h.counts, "records": len(records)})


def run_canary(canary_dir: Path) -> GateResult:
    config, opts = core.canary_setup(canary_dir)
    return run(config, field_scope(canary_dir, config), opts)


def _scope(root: Path, config: dict, args) -> Scope:
    return field_scope(root, config)


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, run, __doc__, scope_fn=_scope))
