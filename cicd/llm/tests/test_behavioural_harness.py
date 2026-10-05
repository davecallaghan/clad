"""Gate 4, behavioural_harness: the stored value must depend on the model's output.

taxonomy, Definition "Grounded assertion": Sensitive(S, p, E) holds iff, had E not supported p,
the assertion would not have been produced; its operational test is ablation.
"""

from __future__ import annotations

import json

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import behavioural_harness as g
from cicd.llm.tests.helpers import CANARIES

CONTRACT = """[targets.findings]
ungraded_marker = "ungraded"
[targets.findings.fields.severity]
domain = ["high", "low"]
origins = ["model_emitted"]
"""
GOOD = """
def run_pipeline(records, model):
    out = []
    for r in records:
        e = model(r)
        out.append({"document_id": r["document_id"],
                    "severity": e["severity"] if "severity" in e else "ungraded"})
    return out
"""
RECORDS = json.dumps([{"document_id": "a"}, {"document_id": "b"}])


def config_for(kit_config, **over):
    sec = {"contract": "contract.toml", "entry": "pipeline.py:run_pipeline",
           "target": "findings", "records": "records.json", **over}
    return core.deep_merge(kit_config, {"gates": {"behavioural_harness": sec}})


def run_repo(root, config):
    return g.run(config, g.field_scope(root, config))


def rules(r):
    return {f.rule for f in r.findings}


@pytest.mark.parametrize("canary,rule", [
    ("assigned_constant", "r1_not_dependent"),
    ("default_grade", "r2_default_grade"),
    ("vacuous", "vacuous"),
])
def test_each_canary_is_violated_with_its_rule(canary, rule):
    r = g.run_canary(CANARIES / "behavioural_harness" / canary)
    assert r.verdict is Verdict.VIOLATED
    assert rule in rules(r), rules(r)


def test_dependent_pipeline_is_satisfied(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "pipeline.py": GOOD, "records.json": RECORDS})
    r = run_repo(root, config_for(kit_config))
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]
    assert r.details["assertions"]["severity"]["R1"] > 0
    assert r.details["assertions"]["severity"]["R2"] > 0


def test_kit_reference_pipeline_is_satisfied():
    config = core.load_config()
    r = g.run(config, g.field_scope(core.repo_root(CANARIES), config))
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]
    assert set(r.details["assertions"]) == {"severity", "finding_kind"}


def test_r3_and_r4_are_not_run_without_inputs(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "pipeline.py": GOOD, "records.json": RECORDS})
    r = run_repo(root, config_for(kit_config))
    assert r.sub_checks["R3"] == "NOT_RUN" and r.sub_checks["R4"] == "NOT_RUN"


def test_r4_runs_when_a_repeat_count_is_configured(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "pipeline.py": GOOD, "records.json": RECORDS})
    r = run_repo(root, config_for(kit_config, repeat_count=3))
    assert r.sub_checks["R4"] == "RUN"
    assert r.verdict is Verdict.SATISFIED


def test_r4_catches_nondeterministic_output(make_repo, kit_config):
    flaky = GOOD.replace('"severity": e["severity"] if "severity" in e else "ungraded"',
                         '"severity": __import__("random").choice(["high", "low"])')
    root = make_repo({"contract.toml": CONTRACT, "pipeline.py": flaky, "records.json": RECORDS})
    r = run_repo(root, config_for(kit_config, repeat_count=40))
    assert "r4_not_repeatable" in rules(r)


def test_r3_compares_recorded_paraphrase_pairs(make_repo, kit_config):
    pairs = [{"a": {"record": {"document_id": "a"}, "emitted": {"severity": "high"}},
              "b": {"record": {"document_id": "a2"}, "emitted": {"severity": "low"}}}]
    root = make_repo({"contract.toml": CONTRACT, "pipeline.py": GOOD, "records.json": RECORDS,
                      "pairs.json": json.dumps(pairs)})
    r = run_repo(root, config_for(kit_config, paraphrase_pairs="pairs.json"))
    assert r.sub_checks["R3"] == "RUN"
    assert "r3_paraphrase_differs" in rules(r)


def test_single_value_domain_cannot_satisfy_r1(make_repo, kit_config):
    contract = CONTRACT.replace('domain = ["high", "low"]', 'domain = ["high"]')
    root = make_repo({"contract.toml": contract, "pipeline.py": GOOD, "records.json": RECORDS})
    r = run_repo(root, config_for(kit_config))
    assert r.verdict is Verdict.VIOLATED
    assert "vacuous" in rules(r)


def test_missing_ungraded_marker_is_violated(make_repo, kit_config):
    contract = CONTRACT.replace('ungraded_marker = "ungraded"\n', "")
    root = make_repo({"contract.toml": contract, "pipeline.py": GOOD, "records.json": RECORDS})
    assert "no_ungraded_marker" in rules(run_repo(root, config_for(kit_config)))


def test_no_governed_fields_is_inexpressible(make_repo, kit_config):
    root = make_repo({"contract.toml": "[targets.findings]\n", "pipeline.py": GOOD, "records.json": RECORDS})
    assert run_repo(root, config_for(kit_config)).verdict is Verdict.INEXPRESSIBLE
