"""Hook canaries (guidance 3.5). The hooks run as Claude Code runs them: the shell command from
.claude/settings.json, the documented stdin JSON, and its exit code and stdout as the result."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path

from cicd.llm.tests.helpers import KIT, commit_all, write_files

ROOT = KIT.parents[1]
SETTINGS = ROOT / ".claude" / "settings.json"
CONTRACT = (KIT / "contracts" / "governed_values.toml").read_text()
GUARDS = "from cicd.llm.safeguard import safeguard\n\n\n@safeguard\ndef check_rows(rows):\n    return rows\n"
BLOCK = 2  # Claude Code hooks reference: exit 2 blocks / feeds stderr back to Claude


def hook_command(event: str) -> str:
    groups = json.loads(SETTINGS.read_text())["hooks"][event]
    return groups[0]["hooks"][0]["command"]


def run_hook(event: str, payload: dict, python: str | None = None) -> subprocess.CompletedProcess:
    env = {**os.environ, "CLAUDE_PROJECT_DIR": str(ROOT), "CICD_LLM_PYTHON": python or sys.executable}
    return subprocess.run(["bash", "-c", hook_command(event)], input=json.dumps(payload), env=env,
                          capture_output=True, text=True)


def project(make_repo) -> Path:
    root = make_repo({"guards.py": GUARDS, "a.py": "x = 1\n", "cicd/llm/contracts/governed_values.toml": CONTRACT})
    commit_all(root, "base")
    return root


def edit_payload(path: Path) -> dict:
    return {"hook_event_name": "PostToolUse", "tool_name": "Edit", "cwd": str(path.parent),
            "tool_input": {"file_path": str(path)}, "tool_response": "File edited successfully"}


def stop_payload(root: Path, active: bool = False) -> dict:
    return {"hook_event_name": "Stop", "cwd": str(root), "stop_hook_active": active}


def blocked(r: subprocess.CompletedProcess) -> bool:
    return bool(r.stdout.strip()) and json.loads(r.stdout).get("decision") == "block"


# The two canaries the spec requires (edit adding an invented limit; Stop without completion.json
# after a governed change) live in test_guidance.py, which lists them as guidance 3.7 item 5.


# ---- the rest of the hook contract -------------------------------------------------------------

def test_clean_edit_passes(make_repo):
    root = project(make_repo)
    write_files(root, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    r = run_hook("PostToolUse", edit_payload(root / "a.py"))
    assert r.returncode == 0, r.stderr


def test_stop_with_no_governed_change_is_allowed(make_repo):
    root = project(make_repo)
    write_files(root, {"NOTES.md": "text\n"})
    r = run_hook("Stop", stop_payload(root))
    assert r.returncode == 0 and not blocked(r)


def test_stop_hook_active_is_respected_so_it_cannot_loop(make_repo):
    root = project(make_repo)
    write_files(root, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    r = run_hook("Stop", stop_payload(root, active=True))
    assert r.returncode == 0 and not blocked(r)


def _completion(root: Path, **over) -> None:
    write_files(root, {"evidence/preflight.txt": "ok\n"})
    doc = {"status": "complete", "commit": "c" * 40, "tree_hash": "t" * 40, "artifact_digests": {},
           "checks": [{"id": "preflight", "verdict": "SATISFIED", "evidence_path": "evidence/preflight.txt"}],
           "not_verified": ["live deployment"], **over}
    (root / "completion.json").write_text(json.dumps(doc))


def test_stop_with_a_valid_current_completion_is_allowed(make_repo):
    root = project(make_repo)
    write_files(root, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    _completion(root)
    r = run_hook("Stop", stop_payload(root))
    assert r.returncode == 0 and not blocked(r), r.stdout


def test_stop_with_a_violated_completion_blocks(make_repo):
    root = project(make_repo)
    write_files(root, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    _completion(root, checks=[{"id": "preflight", "verdict": "NOT_RUN", "evidence_path": "evidence/preflight.txt"}])
    r = run_hook("Stop", stop_payload(root))
    assert blocked(r) and "complete_with_unsatisfied_check" in json.loads(r.stdout)["reason"]


def test_stop_with_a_stale_completion_blocks(make_repo):
    root = project(make_repo)
    _completion(root)
    past = time.time() - 3600
    os.utime(root / "completion.json", (past, past))
    write_files(root, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    r = run_hook("Stop", stop_payload(root))
    assert blocked(r) and "stale" in json.loads(r.stdout)["reason"]


def test_a_crashing_post_edit_hook_is_reported_not_passed(make_repo, tmp_path):
    root = project(make_repo)
    r = run_hook("PostToolUse", edit_payload(root / "a.py"), python="/usr/bin/false")
    assert r.returncode == BLOCK
    assert "hook error, not a pass" in r.stderr


def test_a_crashing_stop_hook_blocks_once_then_lets_go_with_a_warning(make_repo):
    root = project(make_repo)
    first = run_hook("Stop", stop_payload(root), python="/usr/bin/false")
    assert first.returncode == BLOCK and "NOT validated" in first.stderr
    second = run_hook("Stop", stop_payload(root, active=True), python="/usr/bin/false")
    assert second.returncode == 0 and "systemMessage" in json.loads(second.stdout)


def test_missing_file_path_in_hook_input_is_an_error_not_a_pass():
    r = run_hook("PostToolUse", {"hook_event_name": "PostToolUse", "tool_input": {}})
    assert r.returncode == BLOCK


# ---- the settings file itself (constraint-reversion, Group 1 item 3) ---------------------------

def test_settings_json_parses_and_hooks_point_at_existing_executables():
    """An unparseable settings file is discarded in its entirety, in silence; a hook that hardcodes
    one developer's home directory dies on every other machine."""
    settings = json.loads(SETTINGS.read_text())
    commands = [h["command"] for groups in settings["hooks"].values() for g in groups for h in g["hooks"]]
    assert len(commands) == len(settings["hooks"])
    for c in commands:
        assert "${CLAUDE_PROJECT_DIR}" in c
        assert str(Path.home()) not in c and "/Users/" not in c and "/home/" not in c
        rel = c.split("/", 1)[1]
        assert os.access(ROOT / rel, os.X_OK), rel


def test_post_edit_matcher_covers_the_file_editing_tools():
    matcher = json.loads(SETTINGS.read_text())["hooks"]["PostToolUse"][0]["matcher"]
    assert set(matcher.split("|")) == {"Edit", "Write", "MultiEdit"}
