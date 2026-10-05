"""Stop hook (guidance 3.5): no stopping with governed changes and no valid completion.json.

When the working tree differs from HEAD in a file any gate reads, a completion claim must exist at
the configured path (`[hooks] completion`), be at least as new as those changes (stale completion
evidence is fault F5 in constraint-escape, "Benchmark and fault model"), and be SATISFIED by the
completion_evidence gate. Otherwise the hook blocks with the reason. A session that changed nothing
in governed scope is not blocked. `stop_hook_active` is respected so the hook cannot loop.
Input (stdin JSON): `stop_hook_active`, `cwd`. Output: `{"decision": "block", "reason": ...}`.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

from cicd.lib.verdict import Verdict
from cicd.llm import core, preflight
from cicd.llm.core import Scope
from cicd.llm.gates import completion_evidence

SCOPED_GATES = ("invented_limits", "governed_write", "safeguard_removal", "ruff_profile")


def governed_changes(root: Path, config: dict) -> list[str]:
    changed = set(preflight.changed_paths(root))
    hit = set()
    for gate in SCOPED_GATES:
        sec = core.gate_section(config, gate)
        hit |= {p for p in changed if any(core.glob_match(p, g) for g in sec["include"])
                and not any(core.glob_match(p, g) for g in sec.get("exclude", []))}
    return sorted(hit)


def decide(root: Path) -> str | None:
    """The reason to block, or None to allow stopping."""
    config = core.load_config()
    changed = governed_changes(root, config)
    if not changed:
        return None
    rel = config["hooks"]["completion"]
    claim = root / rel
    if not claim.is_file():
        return (f"This session changed files the LLM gates read ({', '.join(changed)}) and there is no {rel}. "
                "Run cicd/llm/preflight.sh, write the completion claim (finishing-a-task skill), and validate it.")
    newest = max((root / p).stat().st_mtime for p in changed)
    if claim.stat().st_mtime < newest:
        return f"{rel} is older than the latest governed change; a stale completion claim is not evidence. Update it."
    result = completion_evidence.run(config, Scope(root, [rel]), {})
    if result.verdict is not Verdict.SATISFIED:
        why = "; ".join(f"{f.rule}: {f.message}" for f in result.findings) or "; ".join(result.notes)
        return f"{rel} is {result.verdict.value} under completion_evidence: {why}"
    return None


def main() -> int:
    data = json.load(sys.stdin)
    if data.get("stop_hook_active"):
        return 0  # already continued once because of a Stop hook: do not loop
    start = Path(data.get("cwd") or os.environ.get("CLAUDE_PROJECT_DIR") or ".")
    top = core.git(start, "rev-parse", "--show-toplevel").strip()
    reason = decide(Path(top))
    if reason:
        print(json.dumps({"decision": "block", "reason": reason}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
