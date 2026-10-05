"""Section 4 harness plumbing. No model is called: a fake `claude` executable stands in, because
these tests check the harness (arms, refusal, ledger, report), not the model."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys

from cicd.lib.verdict import Verdict
from cicd.llm.eval import run_eval
from cicd.llm.tests.helpers import KIT, commit_all, write_files

ROOT = KIT.parents[1]
# The fake claude ignores the budget; a sentinel avoids writing a number nobody chose.
SETTINGS = {"permission_mode": "acceptEdits", "max_budget_usd": "FIXTURE-NOT-A-BUDGET"}


def test_harness_refuses_to_run_without_owner_approval(tmp_path):
    (tmp_path / "t1.md").write_text("Add a feature.")
    r = subprocess.run([sys.executable, "-m", "cicd.llm.eval.run_eval", "--run", "--tasks-dir", str(tmp_path)],
                       cwd=ROOT, capture_output=True, text=True)
    assert r.returncode == Verdict.NOT_RUN.exit_code and "owner" in r.stderr


def test_harness_refuses_when_required_settings_are_unset(tmp_path):
    (tmp_path / "t1.md").write_text("Add a feature.")
    r = subprocess.run([sys.executable, "-m", "cicd.llm.eval.run_eval", "--run", "--owner-approved",
                        "--tasks-dir", str(tmp_path)], cwd=ROOT, capture_output=True, text=True)
    assert r.returncode == Verdict.NOT_RUN.exit_code and "permission_mode" in r.stderr


def test_plan_is_tasks_times_arms_times_repeats(tmp_path):
    for t in ("t1", "t2"):
        (tmp_path / f"{t}.md").write_text("x")
    s = run_eval.load_settings()
    runs = run_eval.plan(tmp_path, s)
    assert len(runs) == len(["t1", "t2"]) * len(s["arms"]) * s["repeats"]


def test_no_tasks_is_not_run(tmp_path):
    r = subprocess.run([sys.executable, "-m", "cicd.llm.eval.run_eval", "--plan", "--tasks-dir", str(tmp_path)],
                       cwd=ROOT, capture_output=True, text=True)
    assert r.returncode == Verdict.NOT_RUN.exit_code


def test_arms_remove_exactly_what_they_must(tmp_path):
    for arm in ("A", "B", "C"):
        wt = tmp_path / arm
        shutil.copytree(ROOT / ".claude", wt / ".claude")
        shutil.copy(ROOT / "CLAUDE.md", wt / "CLAUDE.md")
        (wt / "cicd" / "llm").mkdir(parents=True)
        shutil.copy(KIT / "CLAUDE.md", wt / "cicd" / "llm" / "CLAUDE.md")
        run_eval.prepare_arm(wt, arm)
        hooks = "hooks" in json.loads((wt / ".claude" / "settings.json").read_text())
        state = (bool(list(wt.rglob("CLAUDE.md"))), (wt / ".claude" / "skills").exists(), hooks)
        assert state == {"A": (False, False, False), "B": (True, True, False), "C": (True, True, True)}[arm]


def test_arm_a_runs_in_safe_mode_and_b_c_exclude_user_settings():
    s = SETTINGS
    assert "--safe-mode" in run_eval.claude_argv("A", "p", s)
    for arm in ("B", "C"):
        argv = run_eval.claude_argv(arm, "p", s)
        assert "--safe-mode" not in argv and argv[argv.index("--setting-sources") + 1] == "project,local"


def test_run_one_records_first_verdicts_with_a_fake_claude(make_repo, tmp_path, monkeypatch):
    root = make_repo({"a.py": "x = 1\n", "guards.py": "from cicd.llm.safeguard import safeguard\n\n\n@safeguard\ndef g(r):\n    return r\n",
                      "cicd/llm/contracts/governed_values.toml": (KIT / "contracts" / "governed_values.toml").read_text()})
    commit_all(root, "base")
    fake = tmp_path / "claude"
    fake.write_text("#!/bin/sh\nprintf 'max_rows = 500\\n' > a.py\necho '{}'\n")
    fake.chmod(0o755)
    monkeypatch.setenv("CICD_LLM_CLAUDE", str(fake))
    rec = run_eval.run_one(root, "t1", "do it", "C", 1, SETTINGS)
    assert rec["first"]["verdicts"]["invented_limits"] == "VIOLATED"
    assert rec["attempts_to_pass"] == "NOT_RUN"
    assert "eval/t1/C/1" not in subprocess.run(["git", "branch"], cwd=root, capture_output=True, text=True).stdout


def test_report_gives_counts_not_rates():
    recs = [{"arm": "A", "first": {"verdicts": {"invented_limits": "VIOLATED"}}, "attempts_to_pass": "NOT_RUN"},
            {"arm": "A", "first": {"verdicts": {"invented_limits": "SATISFIED"}}, "attempts_to_pass": "NOT_RUN"}]
    cell = run_eval.report(recs)["A"]["invented_limits"]
    assert cell["first_attempt"] == "1/2"
    assert not any(isinstance(v, float) for v in cell.values())
