"""Preflight (guidance 3.4): fast gates on changed files, non-zero on VIOLATED, NOT_RUN or INEXPRESSIBLE."""

from __future__ import annotations

import os
import subprocess
import sys

from cicd.lib.verdict import Verdict
from cicd.llm import preflight
from cicd.llm.tests.helpers import KIT, commit_all, write_files

CONTRACT = (KIT / "contracts" / "governed_values.toml").read_text()
GUARDS = "from cicd.llm.safeguard import safeguard\n\n\n@safeguard\ndef check_rows(rows):\n    return rows\n"


def repo_with_change(make_repo, files_after: dict, files_before: dict | None = None):
    root = make_repo({"guards.py": GUARDS, "cicd/llm/contracts/governed_values.toml": CONTRACT,
                      **(files_before or {"a.py": "x = 1\n"})})
    commit_all(root, "base")
    write_files(root, files_after)
    return root


def test_invented_limit_in_a_changed_file_fails(make_repo, monkeypatch):
    root = repo_with_change(make_repo, {"a.py": "max_rows = 500\n"})
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(root, preflight.changed_paths(root))
    assert code == Verdict.VIOLATED.exit_code
    assert report["gates"]["invented_limits"]["verdict"] == "VIOLATED"


def test_clean_change_passes(make_repo, monkeypatch):
    root = repo_with_change(make_repo, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(root, ["a.py"])
    assert code == Verdict.SATISFIED.exit_code, preflight.summary_lines(report)


def test_removed_safeguard_against_head_fails(make_repo, monkeypatch):
    root = repo_with_change(make_repo, {"a.py": "def f(r):\n    return r\n"},
                            {"a.py": "def f(r):\n    check_rows(r)\n    return r\n"})
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(root, ["a.py"])
    assert code == Verdict.VIOLATED.exit_code
    assert report["gates"]["safeguard_removal"]["verdict"] == "VIOLATED"


def test_ungated_governed_write_fails_the_callsite_scan(make_repo, monkeypatch):
    root = repo_with_change(make_repo, {"p.py": "def f(sink, rows):\n    sink.write_rows('findings', rows)\n"})
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(root, ["p.py"])
    assert report["gates"]["governed_write"]["verdict"] == "VIOLATED"
    assert code == Verdict.VIOLATED.exit_code


def test_file_in_no_gate_scope_is_not_applicable_not_a_pass_of_that_gate(make_repo, monkeypatch):
    root = repo_with_change(make_repo, {"notes.md": "text\n"})
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(root, ["notes.md"])
    assert set(report["gates"].values()) == {preflight.NOT_APPLICABLE}
    assert code == Verdict.SATISFIED.exit_code


def test_missing_contract_makes_the_callsite_scan_not_run(make_repo, monkeypatch):
    root = make_repo({"a.py": "x = 1\n"})
    write_files(root, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(root, ["a.py"])
    assert report["gates"]["governed_write"]["verdict"] == "NOT_RUN"
    # This repo also has no safeguards, so safeguard_removal is VIOLATED; VIOLATED takes precedence.
    assert code == Verdict.VIOLATED.exit_code


def test_nothing_to_check_is_inexpressible(make_repo, monkeypatch):
    root = make_repo({"a.py": "x = 1\n"})
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(root, preflight.changed_paths(root))
    assert code == Verdict.INEXPRESSIBLE.exit_code
    assert report["verdict"] == "INEXPRESSIBLE"


def test_missing_ruff_is_not_run_and_fails_preflight(make_repo, monkeypatch):
    root = repo_with_change(make_repo, {"a.py": "def total(xs):\n    return sum(xs)\n"})
    monkeypatch.chdir(root)
    real = preflight.ruff_profile.importlib.util.find_spec
    monkeypatch.setattr(preflight.ruff_profile.importlib.util, "find_spec",
                        lambda name: None if name == "ruff" else real(name))
    code, report = preflight.run_paths(root, ["a.py"])
    assert report["gates"]["ruff_profile"]["verdict"] == "NOT_RUN"
    assert code == Verdict.NOT_RUN.exit_code


def test_preflight_sh_runs_end_to_end(make_repo, tmp_path):
    root = repo_with_change(make_repo, {"a.py": "max_rows = 500\n"})
    env = {**os.environ, "CICD_LLM_PYTHON": sys.executable, "CICD_LLM_PREFLIGHT_JSON": str(tmp_path / "p.json")}
    r = subprocess.run([str(KIT / "preflight.sh")], cwd=root, env=env, capture_output=True, text=True)
    assert r.returncode == Verdict.VIOLATED.exit_code, r.stderr
    assert "invented_limits" in r.stdout and "VIOLATED" in r.stdout
    assert (tmp_path / "p.json").is_file()


def test_a_symlinked_root_still_finds_its_files(make_repo, tmp_path, monkeypatch):
    """Found by the eval harness test: on macOS /var is a symlink to /private/var, and paths under
    an unresolved root were reported as outside the repository and silently not checked."""
    root = repo_with_change(make_repo, {"a.py": "max_rows = 500\n"})
    link = tmp_path / "linked"
    link.symlink_to(root)
    monkeypatch.chdir(root)
    code, report = preflight.run_paths(link, [str(root / "a.py")])
    assert report["outside_repo"] == []
    assert code == Verdict.VIOLATED.exit_code
