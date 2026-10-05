"""Shared fixtures: real temporary git repositories, never mocks of git."""

from __future__ import annotations

import pytest

from cicd.llm import core
from cicd.llm.tests.helpers import _git, write_files, commit_all


@pytest.fixture
def make_repo(tmp_path):
    """Create a git repo with the given files committed; returns its root."""
    counter = {"n": 0}

    def _make(files: dict[str, str], commit: bool = True) -> Path:
        counter["n"] += 1
        root = tmp_path / f"repo{counter['n']}"
        root.mkdir()
        _git(root, "init", "-q")
        _git(root, "config", "user.email", "t@example.invalid")
        _git(root, "config", "user.name", "t")
        write_files(root, files)
        if commit:
            commit_all(root, "init")
        return root

    return _make


@pytest.fixture
def kit_config():
    return core.load_config()


@pytest.fixture(autouse=True)
def _isolated_ledger(tmp_path, monkeypatch):
    monkeypatch.setenv("CICD_LLM_LEDGER", str(tmp_path / "ledger.jsonl"))
