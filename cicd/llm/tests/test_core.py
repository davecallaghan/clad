"""The shared contract: verdicts cannot become booleans, globs derive scope correctly."""

from __future__ import annotations

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core


@pytest.mark.parametrize("v", list(Verdict))
def test_bool_of_a_verdict_raises(v):
    with pytest.raises(TypeError):
        bool(v)


def test_exit_codes():
    assert {v: v.exit_code for v in Verdict} == {
        Verdict.SATISFIED: 0, Verdict.VIOLATED: 1, Verdict.INEXPRESSIBLE: 2, Verdict.NOT_RUN: 2}


@pytest.mark.parametrize("path,pattern,expected", [
    ("resources/wrapper.yml", "**/resources/**/*.yml", True),
    ("a/resources/x/y.yml", "**/resources/**/*.yml", True),
    ("a.py", "**/*.py", True),
    ("x/y/a.py", "**/*.py", True),
    ("x/ab.py", "*.py", False),
    ("src/databricks.yml.bak", "**/databricks.yml", False),
])
def test_glob_semantics(path, pattern, expected):
    assert core.glob_match(path, pattern) is expected


def test_untracked_files_are_in_scope_and_ignored_files_are_not(make_repo):
    root = make_repo({".gitignore": "build/\n", "a.py": "x = 1\n"})
    (root / "new.py").write_text("y = 2\n")
    (root / "build").mkdir()
    (root / "build" / "gen.py").write_text("z = 3\n")
    assert core.derive_scope(root, ["**/*.py"], []).files == ["a.py", "new.py"]


def test_gate_error_in_cli_becomes_not_run(make_repo, monkeypatch, capsys):
    import json
    import sys

    from cicd.llm.gates import invented_limits
    root = make_repo({"a.py": "x = 1\n"})
    (root / "bad.toml").write_text("[gates\n")
    monkeypatch.setattr(sys, "argv", ["x", "--root", str(root), "--config", str(root / "bad.toml"), "--no-ledger"])
    code = core.cli(invented_limits.GATE_ID, invented_limits.KIND, [], invented_limits.run, "")
    assert code == Verdict.NOT_RUN.exit_code
    assert json.loads(capsys.readouterr().out)["verdict"] == "NOT_RUN"
