"""Run a skill example through its real gate (guidance 3.3 item 4 and 3.7 item 1).

Examples are named `<gate>__good__<slug>.<ext>` or `<gate>__bad__<slug>.<ext>`. A good example must
be SATISFIED by the named gate and a bad one VIOLATED. Each example is placed into a temporary git
repository shaped the way its gate reads the world, then the gate's own `run` is called: no mocks.

Per-gate layouts:
- invented_limits, ruff_profile: the file itself (`.yml` goes under `resources/`, a job manifest).
- governed_write: `example.py` beside the kit's contract and `governed.py` (which defines write_gate).
- provenance: the rows file as `findings.governed.jsonl`, with the kit's contract.
- behavioural_harness: `pipeline.py` exposing `run_pipeline(records, model)`, with the kit's contract
  and reference records.
- safeguard_removal: a JSON pair `{"files": {...}, "path": ..., "base": ..., "head": ...}`, committed
  as base then head.
- completion_evidence: the claim as `completion.json`; its `fixture_files` (free text to the gate)
  are written first so evidence paths can exist.
- review_findings: a JSON bundle `{"source": {...}, "reviews": {"<name>": {...}}}` with the kit's
  rule registry; reviews use commit "WORKTREE".
"""

from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import (behavioural_harness, completion_evidence, governed_write, invented_limits,
                            provenance, review_findings, ruff_profile, safeguard_removal)

KIT = core.KIT_DIR
GATES = {m.GATE_ID: m for m in (invented_limits, governed_write, provenance, behavioural_harness,
                                review_findings, safeguard_removal, completion_evidence, ruff_profile)}
EXPECTED = {"good": Verdict.SATISFIED, "bad": Verdict.VIOLATED}


def parse_name(path: Path) -> tuple[str, str, str]:
    """(gate, kind, slug) from `<gate>__<good|bad>__<slug>.<ext>`; raises on any other name."""
    match path.stem.split("__"):
        case [gate, kind, slug] if kind in EXPECTED:
            return gate, kind, slug
    raise ValueError(f"{path.name}: expected <gate>__good__<slug>.<ext> or <gate>__bad__<slug>.<ext>")


def _git(repo: Path, *args: str) -> str:
    ident = ["-c", "user.email=example@invalid", "-c", "user.name=example"]
    return subprocess.run(["git", *ident, *args], cwd=repo, check=True, capture_output=True, text=True).stdout


def _write(repo: Path, files: dict[str, str]) -> None:
    for rel, text in files.items():
        p = repo / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")


def _commit(repo: Path, message: str) -> str:
    _git(repo, "add", "-A")
    _git(repo, "commit", "-q", "--allow-empty", "-m", message)
    return _git(repo, "rev-parse", "HEAD").strip()


def _kit_contract() -> str:
    return (KIT / "contracts" / "governed_values.toml").read_text(encoding="utf-8")


def _config(**gate_over) -> dict:
    return core.deep_merge(core.load_config(), {"gates": gate_over})


def _layout(gate: str, example: Path, repo: Path) -> tuple[dict, core.Scope, dict]:
    text = example.read_text(encoding="utf-8")
    m = GATES[gate]
    if gate in ("invented_limits", "ruff_profile"):
        rel = f"resources/example{example.suffix}" if example.suffix in (".yml", ".yaml") else f"example{example.suffix}"
        _write(repo, {rel: text})
        cfg = _config()
        sec = cfg["gates"][gate]
        return cfg, core.derive_scope(repo, sec["include"], sec.get("exclude", [])), {}
    if gate == "governed_write":
        _write(repo, {"contract.toml": _kit_contract(), "example.py": text,
                      "governed.py": (KIT / "governed.py").read_text(encoding="utf-8")})
        cfg = _config(governed_write={"contract": "contract.toml"})
        sec = cfg["gates"][gate]
        return cfg, core.derive_scope(repo, sec["include"], sec.get("exclude", [])), {}
    if gate == "provenance":
        _write(repo, {"contract.toml": _kit_contract(), "findings.governed.jsonl": text})
        cfg = _config(provenance={"contract": "contract.toml"})
        return cfg, m.output_scope(repo, cfg, {}), {}
    if gate == "behavioural_harness":
        _write(repo, {"contract.toml": _kit_contract(), "pipeline.py": text,
                      "records.json": (KIT / "reference_records.json").read_text(encoding="utf-8")})
        cfg = _config(behavioural_harness={"contract": "contract.toml", "entry": "pipeline.py:run_pipeline",
                                           "target": "findings", "records": "records.json"})
        return cfg, m.field_scope(repo, cfg), {}
    if gate == "completion_evidence":
        doc = json.loads(text)
        _write(repo, {**doc.get("fixture_files", {}), "completion.json": text})
        cfg = _config(completion_evidence={"completion": "completion.json"})
        return cfg, m.completion_scope(repo, cfg, {}), {}
    if gate == "review_findings":
        bundle = json.loads(text)
        _write(repo, {**bundle["source"], "rules.toml": (KIT / "rules.toml").read_text(encoding="utf-8"),
                      **{f"reviews/{name}.json": json.dumps(r) for name, r in bundle["reviews"].items()}})
        cfg = _config(review_findings={"rule_registry": "rules.toml"})
        opts = {"findings_dir": "reviews"}
        return cfg, m.review_scope(repo, cfg, opts), opts
    if gate == "safeguard_removal":
        pair = json.loads(text)
        _write(repo, {**pair.get("files", {}), pair["path"]: pair["base"]})
        base = _commit(repo, "base")
        _write(repo, {pair["path"]: pair["head"]})
        _commit(repo, "head")
        cfg, opts = _config(), {"base": base, "head": "HEAD"}
        return cfg, m.diff_scope(repo, cfg, opts), opts
    raise ValueError(f"{example.name}: no layout for gate {gate!r}")


def run_example(example: Path) -> core.GateResult:
    gate, _, _ = parse_name(example)
    if gate not in GATES:
        raise ValueError(f"{example.name}: unknown gate {gate!r} (known: {sorted(GATES)})")
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        _git(repo, "init", "-q")
        cfg, scope, opts = _layout(gate, example, repo)
        if gate != "safeguard_removal":
            _commit(repo, "example")
        return GATES[gate].run(cfg, scope, opts)


def copy_examples(src_dir: Path, dst_dir: Path) -> None:
    dst_dir.mkdir(parents=True, exist_ok=True)
    for p in sorted(src_dir.iterdir()):
        shutil.copy2(p, dst_dir / p.name)
