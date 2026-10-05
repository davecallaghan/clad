"""PostToolUse hook (guidance 3.5): run preflight on the file Claude just edited.

On a failed gate the findings go to stderr with exit 2, which Claude Code shows to Claude as
feedback (the tool has already run). Editing a file outside any git repository is not gated.
Input (stdin JSON, per the Claude Code hooks reference): `tool_input.file_path`.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

from cicd.llm import preflight

BLOCK = 2  # Claude Code hooks reference: exit 2 is the blocking/feedback code for PostToolUse


def main() -> int:
    data = json.load(sys.stdin)
    path = (data.get("tool_input") or {}).get("file_path")
    if not path:
        print("post-edit hook: the hook input has no tool_input.file_path; nothing was checked", file=sys.stderr)
        return BLOCK
    target = Path(path)
    top = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=target.parent if target.parent.is_dir() else None,
                         capture_output=True, text=True)
    if top.returncode != 0:
        return 0  # not in a git repository: no gate reads this file
    root = Path(top.stdout.strip())
    code, report = preflight.run_paths(root, [str(target)])
    if code == 0:
        return 0
    print(f"LLM gate preflight failed on {path} (exit {code}). NOT_RUN and INEXPRESSIBLE are not passes.",
          file=sys.stderr)
    print("\n".join(preflight.summary_lines(report)), file=sys.stderr)
    print("Fix the findings before continuing; see the gate-preflight skill.", file=sys.stderr)
    return BLOCK


if __name__ == "__main__":
    sys.exit(main())
