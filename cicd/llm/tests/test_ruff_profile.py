"""Support A, ruff_profile: LLM-targeted lint codes, each verified to exist with `ruff rule`."""

from __future__ import annotations

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import ruff_profile as g
from cicd.llm.tests.helpers import CANARIES, commit_all, write_files


def run_repo(root, config, opts=None):
    sec = config["gates"]["ruff_profile"]
    return g.run(config, core.derive_scope(root, sec["include"], sec.get("exclude", [])), opts or {})


def codes(r):
    return {f.rule for f in r.findings}


@pytest.mark.parametrize("canary,code", [
    ("try_except_pass", "S110"),
    ("blind_except", "BLE001"),
    ("bare_except", "E722"),
    ("magic_value", "PLR2004"),
    ("complexity", "C901"),
])
def test_each_canary_is_violated_with_its_code(canary, code):
    r = g.run_canary(CANARIES / "ruff_profile" / canary)
    assert r.verdict is Verdict.VIOLATED
    assert code in codes(r), codes(r)


def test_noqa_is_ignored_so_a_suppression_cannot_hide_a_finding():
    """The bare_except canary carries `# noqa: S110`; the profile must still report E722."""
    assert "E722" in codes(g.run_canary(CANARIES / "ruff_profile" / "bare_except"))


def test_clean_code_is_satisfied(make_repo, kit_config):
    root = make_repo({"m.py": "def f(xs):\n    return [x for x in xs if x]\n"})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]


def test_c901_is_not_run_without_a_threshold(make_repo, kit_config):
    cfg = core.deep_merge(kit_config, {})
    del cfg["gates"]["ruff_profile"]["c901_max_complexity"]
    root = make_repo({"m.py": "x = 1\n"})
    assert run_repo(root, cfg).sub_checks["C901"] == "NOT_RUN"


def test_raising_the_complexity_ratchet_is_violated(make_repo, kit_config):
    conf = "[gates.ruff_profile]\nc901_max_complexity = 10\n"
    root = make_repo({"m.py": "x = 1\n", "cicd/llm/config.toml": conf})
    base = commit_all(root, "base")
    write_files(root, {"cicd/llm/config.toml": conf.replace("10", "12")})
    commit_all(root, "raise")
    cfg = core.deep_merge(kit_config, {"gates": {"ruff_profile": {"c901_max_complexity": 12}}})
    r = run_repo(root, cfg, {"base": base})
    assert "ratchet_raised" in codes(r)


def test_lowering_the_ratchet_is_satisfied(make_repo, kit_config):
    conf = "[gates.ruff_profile]\nc901_max_complexity = 10\n"
    root = make_repo({"m.py": "x = 1\n", "cicd/llm/config.toml": conf})
    base = commit_all(root, "base")
    cfg = core.deep_merge(kit_config, {"gates": {"ruff_profile": {"c901_max_complexity": 8}}})
    r = run_repo(root, cfg, {"base": base})
    assert r.verdict is Verdict.SATISFIED
    assert r.sub_checks["ratchet"] == "RUN"


def test_empty_scope_is_inexpressible(make_repo, kit_config):
    root = make_repo({"README.md": "x\n"})
    assert run_repo(root, kit_config).verdict is Verdict.INEXPRESSIBLE
