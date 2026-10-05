"""Freeze and leakage guard (spec section 5.4; constraint-escape "Held-out discipline, mechanised")."""

from __future__ import annotations

import json
import subprocess
import sys

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import freeze
from cicd.llm.freeze import FreezeViolation, verify_freeze


def test_the_freeze_verifies():
    verify_freeze()


def test_every_gate_file_is_frozen_and_the_set_is_derived():
    files = freeze.frozen_files()
    gates = {f"cicd/llm/gates/{p.name}" for p in (freeze.KIT / "gates").glob("*.py")}
    assert gates <= set(files)
    assert {"cicd/llm/core.py", "cicd/llm/governed.py", "cicd/lib/verdict.py"} <= set(files)


def test_a_moved_gate_file_is_detected(monkeypatch):
    real = freeze.current_digests()
    monkeypatch.setattr(freeze, "current_digests",
                        lambda: {**real, "cicd/llm/gates/provenance.py": "sha256:tampered"})
    with pytest.raises(FreezeViolation, match="changed after the freeze"):
        verify_freeze()


def test_a_new_unfrozen_gate_file_is_detected(monkeypatch):
    real = freeze.current_digests()
    monkeypatch.setattr(freeze, "current_digests", lambda: {**real, "cicd/llm/gates/new_gate.py": "sha256:x"})
    with pytest.raises(FreezeViolation):
        verify_freeze()


def test_heldout_runner_refuses_when_the_freeze_has_moved(tmp_path, monkeypatch):
    record = json.loads(freeze.FREEZE_PATH.read_text())
    record["digests"]["cicd/llm/core.py"] = "sha256:stale"
    fake = tmp_path / "FREEZE.json"
    fake.write_text(json.dumps(record))
    code = subprocess.run(
        [sys.executable, "-c",
         "import sys; from pathlib import Path; import cicd.llm.freeze as f; "
         f"f.FREEZE_PATH = Path({str(fake)!r}); import cicd.llm.heldout_run as h; sys.exit(h.main())"],
        cwd=freeze.ROOT, capture_output=True, text=True)
    assert code.returncode == Verdict.NOT_RUN.exit_code and "REFUSING TO RUN" in code.stdout


def _identifiers(heldout_dir):
    return {p.name for g in heldout_dir.glob("*") if g.is_dir() for p in g.iterdir() if p.is_dir()}


def _leaks(identifiers):
    return [(rel, ident) for rel in freeze.frozen_files()
            for ident in identifiers if ident in (freeze.ROOT / rel).read_text(encoding="utf-8")]


def test_no_heldout_identifier_appears_in_any_frozen_file():
    assert _leaks(_identifiers(freeze.KIT / "heldout")) == [], (
        "a gate that names the fixture it will be tested against proves nothing")


def test_the_leakage_check_is_not_vacuous(tmp_path):
    """There are no held-out fixtures yet; prove the check fires on a planted one."""
    (tmp_path / "provenance" / "write_gate").mkdir(parents=True)  # an identifier that IS in a gate file
    assert _leaks(_identifiers(tmp_path)) != []


def test_a_superseding_freeze_names_what_it_replaced_and_why():
    record = json.loads(freeze.FREEZE_PATH.read_text())
    for key in ("frozen_at", "git_commit", "digests", "note"):
        assert record.get(key), key
    if "supersedes" in record:
        assert (freeze.KIT / record["supersedes"]).exists()
        assert record.get("supersede_reason") and record.get("logic_unchanged_evidence")
