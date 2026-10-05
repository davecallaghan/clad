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


def harness(*args, tmp_path, settings: str | None = None) -> subprocess.CompletedProcess:
    """Run the harness CLI so that it can never reach a real model: `claude` is a fake that fails,
    and the settings file is the test's own."""
    fake = tmp_path / "no-claude"
    fake.write_text("#!/bin/sh\necho 'a test reached claude' >&2\nexit 99\n")
    fake.chmod(0o755)
    cfg = tmp_path / "eval.toml"
    cfg.write_text(settings if settings is not None else 'repeats = 2\narms = ["A", "B", "C"]\n')
    env = {**os.environ, "CICD_LLM_CLAUDE": str(fake)}
    return subprocess.run([sys.executable, "-m", "cicd.llm.eval.run_eval", *args, "--settings", str(cfg)],
                          cwd=ROOT, capture_output=True, text=True, env=env)
# The fake claude ignores the budget; a sentinel avoids writing a number nobody chose.
SETTINGS = {"permission_mode": "acceptEdits", "max_budget_usd": "FIXTURE-NOT-A-BUDGET"}


def test_harness_refuses_to_run_without_owner_approval(tmp_path):
    tasks = tmp_path / "tasks"
    tasks.mkdir()
    (tasks / "t1.md").write_text("Add a feature.")
    r = harness("--run", "--tasks-dir", str(tasks), tmp_path=tmp_path,
                settings='repeats = 2\narms = ["A"]\npermission_mode = "acceptEdits"\nmax_budget_usd = 1.0\n')
    assert r.returncode == Verdict.NOT_RUN.exit_code and "owner" in r.stderr


def test_harness_refuses_when_required_settings_are_unset(tmp_path):
    tasks = tmp_path / "tasks"
    tasks.mkdir()
    (tasks / "t1.md").write_text("Add a feature.")
    r = harness("--run", "--owner-approved", "--tasks-dir", str(tasks), tmp_path=tmp_path)
    assert r.returncode == Verdict.NOT_RUN.exit_code and "permission_mode" in r.stderr
    assert "a test reached claude" not in r.stderr


def test_plan_is_tasks_times_arms_times_repeats(tmp_path):
    for t in ("t1", "t2"):
        (tmp_path / f"{t}.md").write_text("x")
    s = run_eval.load_settings()
    runs = run_eval.plan(tmp_path, s)
    assert len(runs) == len(["t1", "t2"]) * len(s["arms"]) * s["repeats"]


def test_no_tasks_is_not_run(tmp_path):
    empty = tmp_path / "tasks"
    empty.mkdir()
    r = harness("--plan", "--tasks-dir", str(empty), tmp_path=tmp_path)
    assert r.returncode == Verdict.NOT_RUN.exit_code


def test_arms_remove_exactly_what_they_must(tmp_path):
    for arm in ("A", "B", "C"):
        wt = tmp_path / arm
        shutil.copytree(ROOT / ".claude", wt / ".claude")
        shutil.copy(ROOT / "CLAUDE.md", wt / "CLAUDE.md")
        (wt / "cicd" / "llm").mkdir(parents=True)
        shutil.copy(KIT / "CLAUDE.md", wt / "cicd" / "llm" / "CLAUDE.md")
        (wt / "completion.json").write_text("{}")
        run_eval.prepare_arm(wt, arm)
        assert not (wt / "completion.json").exists(), "an earlier task's claim must not be scored"
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
    recs = [_rec("t", "A", 1, invented_limits="VIOLATED"), _rec("t", "A", 2, invented_limits="SATISFIED")]
    cell = run_eval.report(recs)["arms"]["A"]["invented_limits"]
    assert cell["first_attempt"] == "1/2"
    assert not any(isinstance(v, float) for v in cell.values())


def _rec(task, arm, n, exit_code=0, **verdicts):
    return {"task": task, "arm": arm, "repeat": n, "base": "b",
            "first": {"claude_exit": exit_code, "verdicts": verdicts}, "attempts_to_pass": {}}


def test_report_drops_duplicates_and_excludes_failed_sessions():
    recs = [_rec("t", "A", 1, invented_limits="VIOLATED"), _rec("t", "A", 1, invented_limits="SATISFIED"),
            _rec("t", "B", 1, exit_code=1, safeguard_removal="INEXPRESSIBLE"),
            _rec("t", "C", 1, invented_limits="SATISFIED", review_findings="NOT_RUN")]
    rep = run_eval.report(recs)
    assert rep["arms"]["A"]["invented_limits"]["first_attempt"] == "1/1"
    assert rep["excluded_failed_sessions"] == [["t", "B", 1]]
    assert rep["duplicates_dropped"] == 1
    assert "B" not in rep["arms"] and "review_findings" not in rep["arms"]["C"]


def test_resume_skips_runs_with_a_valid_record(tmp_path, monkeypatch):
    tasks = tmp_path / "tasks"
    tasks.mkdir()
    (tasks / "t.md").write_text("x")
    ledger = tmp_path / "eval.jsonl"
    ledger.write_text(json.dumps(_rec("t", "A", 1)) + "\n" + json.dumps(_rec("t", "A", 2, exit_code=1)) + "\n")
    monkeypatch.setattr(run_eval, "LEDGER", ledger)
    done, _ = run_eval.latest_valid([json.loads(x) for x in ledger.read_text().splitlines()])
    remaining = [r for r in run_eval.plan(tasks, {"arms": ["A"], "repeats": 2}) if r not in done]
    assert remaining == [("t", "A", 2)]


def test_a_failed_session_is_recorded_invalid_with_its_output(make_repo, tmp_path, monkeypatch):
    root = make_repo({"a.py": "x = 1\n"})
    commit_all(root, "base")
    fake = tmp_path / "claude"
    fake.write_text("#!/bin/sh\necho \"You've hit your session limit\"\nexit 1\n")
    fake.chmod(0o755)
    monkeypatch.setenv("CICD_LLM_CLAUDE", str(fake))
    rec = run_eval.run_one(root, "t", "p", "C", 1, {**SETTINGS, "max_attempts": 2})
    assert rec["valid"] is False and "session limit" in rec["first"]["stdout_tail"]
