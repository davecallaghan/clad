"""The gate freeze (spec section 5.4), modelled on ../constraint-escape/constraint_escape/freeze.py.

Held-out fixtures have evidential value only if the gates could not have been tailored to them.
With one builder that rests on ordering made mechanical: FREEZE.json records a SHA-256 per frozen
file and the commit, and the held-out runner refuses to run if any digest has moved. This is a
mitigation, not a proof (constraint-escape, "Held-out discipline, mechanised").

Frozen files are derived, not listed: every module in cicd/llm/gates/, plus every cicd.* module
those import, transitively.
"""

from __future__ import annotations

import ast
import hashlib
import json
import subprocess
import time
from pathlib import Path

KIT = Path(__file__).resolve().parent
ROOT = KIT.parents[1]
FREEZE_PATH = KIT / "FREEZE.json"


class FreezeViolation(RuntimeError):
    """A frozen file changed after the freeze, or there is no freeze."""


def _module_path(name: str) -> Path | None:
    p = ROOT.joinpath(*name.split("."))
    return p.with_suffix(".py") if p.with_suffix(".py").is_file() else None


def _cicd_imports(path: Path) -> set[Path]:
    found = set()
    for n in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
        names = [a.name for a in n.names] if isinstance(n, ast.Import) else \
            [n.module] + [f"{n.module}.{a.name}" for a in n.names] if isinstance(n, ast.ImportFrom) and n.module else []
        found |= {p for m in names if m.startswith("cicd.") and (p := _module_path(m)) is not None}
    return found


def frozen_files() -> list[str]:
    todo = [p for p in sorted((KIT / "gates").glob("*.py"))]
    if not todo:
        raise FreezeViolation("no gate files found: an empty enumeration is not a clean surface")
    seen: set[Path] = set()
    while todo:
        p = todo.pop()
        if p not in seen:
            seen.add(p)
            todo.extend(_cicd_imports(p) - seen)
    return sorted(str(p.relative_to(ROOT)) for p in seen)


def _digest(path: Path) -> str:
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


def current_digests() -> dict[str, str]:
    return {rel: _digest(ROOT / rel) for rel in frozen_files()}


def _git_commit() -> str:
    r = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else "unknown"


def write_freeze(supersedes: str | None = None, reason: str | None = None,
                 logic_unchanged_evidence: str | None = None) -> dict:
    """A re-freeze must say what it supersedes and why, or the audit chain breaks."""
    record = {"frozen_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "git_commit": _git_commit(),
              "digests": current_digests(),
              "note": "Gates frozen before any held-out fixture was supplied. heldout_run.py refuses "
                      "to run if any digest here has moved."}
    if supersedes:
        record.update(supersedes=supersedes, supersede_reason=reason,
                      logic_unchanged_evidence=logic_unchanged_evidence)
    FREEZE_PATH.write_text(json.dumps(record, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    return record


def verify_freeze() -> dict:
    if not FREEZE_PATH.exists():
        raise FreezeViolation("no FREEZE.json: the gates were never frozen")
    record = json.loads(FREEZE_PATH.read_text(encoding="utf-8"))
    now = current_digests()
    moved = sorted(rel for rel in set(now) | set(record["digests"]) if record["digests"].get(rel) != now.get(rel))
    if moved:
        raise FreezeViolation(f"frozen files changed after the freeze; held-out results would be worthless: {moved}")
    return record


if __name__ == "__main__":
    print(json.dumps(write_freeze(), indent=1, sort_keys=True))
