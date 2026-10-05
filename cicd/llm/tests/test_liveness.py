"""Support B, liveness: a gate exists only after its canary has been seen to fire
(constraint-reversion, "Liveness canaries")."""

from __future__ import annotations

import shutil

from cicd.lib.verdict import Verdict
from cicd.llm.core import GateResult
from cicd.llm.gates import invented_limits, liveness
from cicd.llm.tests.helpers import CANARIES


def test_every_discovered_gate_fires_every_canary():
    r = liveness.check()
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]
    d = r.details
    assert d["gates_discovered"] > 0
    assert d["gates_discovered"] == d["gates_with_canaries"]
    assert d["canaries"] == d["canaries_fired"] > 0


def test_discovery_is_derived_from_the_package_not_a_list():
    discovered = set(liveness.discover_gates())
    on_disk = {p.stem for p in liveness.GATES_DIR.glob("*.py") if p.stem not in ("__init__", "liveness")}
    assert discovered == on_disk


def test_a_gate_that_always_returns_satisfied_fails_liveness(monkeypatch):
    def always_satisfied(canary_dir):
        return GateResult(invented_limits.GATE_ID, Verdict.SATISFIED, invented_limits.KIND)

    monkeypatch.setattr(invented_limits, "run_canary", always_satisfied)
    r = liveness.check()
    assert r.verdict is Verdict.VIOLATED
    assert {f.rule for f in r.findings} == {"canary_did_not_fire"}


def test_a_deleted_canary_fails_liveness(tmp_path, monkeypatch):
    copy = tmp_path / "canaries"
    shutil.copytree(CANARIES, copy)
    shutil.rmtree(copy / "provenance")
    monkeypatch.setattr(liveness, "CANARIES_DIR", copy)
    r = liveness.check()
    assert r.verdict is Verdict.VIOLATED
    assert any(f.rule == "gate_without_canary" and "provenance" in f.message for f in r.findings)


def test_an_orphan_canary_is_reported(tmp_path, monkeypatch):
    copy = tmp_path / "canaries"
    shutil.copytree(CANARIES, copy)
    (copy / "no_such_gate" / "x").mkdir(parents=True)
    monkeypatch.setattr(liveness, "CANARIES_DIR", copy)
    assert "orphan_canary" in {f.rule for f in liveness.check().findings}


def test_no_gates_discovered_is_not_a_clean_result(tmp_path, monkeypatch):
    empty = tmp_path / "gates"
    empty.mkdir()
    monkeypatch.setattr(liveness, "GATES_DIR", empty)
    r = liveness.check()
    assert r.verdict is Verdict.INEXPRESSIBLE
