"""Support A: ruff_profile (LLM-targeted lint configuration; kind = proxy).

Exists to catch, at lint speed, the constructs behind the removals the reviewer missed
(constraint-reversion, "Why review does not catch a control dying": a swallowed exception) and
unexplained literals (constraint-reversion, "Bound reversion"):
S110 try-except-pass, BLE001 blind except, E722 bare except, PLR2004 magic-value comparison, and
C901 complexity above a ratchet. Each code was verified with `ruff rule <code>` before use (the
command and ruff version are recorded in config). The C901 threshold is the measured repository
maximum; it is a ratchet and may only go down (checked against `--base`). `--ignore-noqa` is
passed so a suppression comment cannot hide a finding. ruff is a dev tool; without it the gate
is NOT_RUN, never SATISFIED.
"""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tomllib
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.core import Finding, GateResult, Scope

GATE_ID = "ruff_profile"
KIND = "proxy"
CONFIG_REL = "cicd/llm/config.toml"
BLIND_SPOTS = [
    "anything outside the five configured codes",
    "non-Python files",
    "complexity below the ratchet (C901 measures the worst function, not the trend)",
]


def ratchet(root: Path, base: str | None, current: int | None) -> tuple[str, list[Finding]]:
    if not base or current is None:
        return Verdict.NOT_RUN.value, []
    shown = subprocess.run(["git", "show", f"{base}:{CONFIG_REL}"], cwd=root, capture_output=True, text=True)
    if shown.returncode != 0:
        return Verdict.NOT_RUN.value, []
    previous = tomllib.loads(shown.stdout).get("gates", {}).get(GATE_ID, {}).get("c901_max_complexity")
    if previous is not None and current > previous:
        msg = f"c901_max_complexity raised from {previous} to {current}; the ratchet may only go down"
        return "RUN", [Finding(CONFIG_REL, 1, core.line_hash(msg), "ratchet_raised", msg)]
    return "RUN", []


def run(config: dict, scope: Scope, opts: dict | None = None) -> GateResult:
    opts = opts or {}
    cfg = core.gate_section(config, GATE_ID)
    if len(scope) == 0:
        return core.empty_scope(GATE_ID, KIND, BLIND_SPOTS)
    if importlib.util.find_spec("ruff") is None:
        return core.not_run(GATE_ID, KIND, BLIND_SPOTS, "ruff is not installed in this environment")
    threshold = cfg.get("c901_max_complexity")
    codes = [c for c in cfg["rules"] if c != "C901" or threshold is not None]
    cmd = [sys.executable, "-m", "ruff", "check", "--isolated", "--ignore-noqa", "--no-cache",
           "--output-format", "json", "--select", ",".join(codes)]
    for setting in cfg.get("ruff_settings", []):
        cmd += ["--config", setting]
    if threshold is not None:
        cmd += ["--config", f"lint.mccabe.max-complexity={threshold}"]
    proc = subprocess.run([*cmd, *scope.files], cwd=scope.root, capture_output=True, text=True)
    if proc.returncode not in (0, 1):
        return core.not_run(GATE_ID, KIND, BLIND_SPOTS, f"ruff failed (exit {proc.returncode}): {proc.stderr.strip()}")
    findings = []
    for item in json.loads(proc.stdout or "[]"):
        rel = str(Path(item["filename"]).resolve().relative_to(scope.root.resolve()))
        line = item["location"]["row"]
        text = (scope.root / rel).read_text(encoding="utf-8").splitlines()
        findings.append(Finding(rel, line, core.line_hash(text[line - 1] if line <= len(text) else ""),
                                item["code"], item["message"]))
    ratchet_state, ratchet_findings = ratchet(scope.root, opts.get("base"), threshold)
    findings += ratchet_findings
    return GateResult(GATE_ID, Verdict.VIOLATED if findings else Verdict.SATISFIED, KIND,
                      findings=findings, scope_size=len(scope), blind_spots=BLIND_SPOTS,
                      sub_checks={"lint": "RUN", "C901": "RUN" if threshold is not None else Verdict.NOT_RUN.value,
                                  "ratchet": ratchet_state},
                      details={"codes": codes, "c901_max_complexity": threshold})


def run_canary(canary_dir: Path) -> GateResult:
    config, opts = core.canary_setup(canary_dir)
    cfg = core.gate_section(config, GATE_ID)
    return run(config, core.derive_scope(canary_dir, cfg["include"], cfg.get("exclude", [])), opts)


def _args(ap) -> None:
    ap.add_argument("--base", help="git ref whose c901_max_complexity the current value may not exceed")


if __name__ == "__main__":
    core.main_exit(core.cli(GATE_ID, KIND, BLIND_SPOTS, run, __doc__, add_args=_args))
