"""Gate 3, provenance: three origins, not two (reversion, "Grade reversion requires provenance")."""

from __future__ import annotations

import json

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import provenance as g
from cicd.llm.tests.helpers import CANARIES

CONTRACT = """[targets.findings]
ungraded_marker = "ungraded"
[targets.findings.fields.severity]
domain = ["high", "low"]
origins = ["model_emitted", "promoted"]
[targets.findings.fields.finding_kind]
domain = ["gap"]
origins = ["model_emitted", "code_supplied"]
"""


def jsonl(*rows):
    return "".join(json.dumps(r) + "\n" for r in rows)


def ok_row(**over):
    r = {"id": 1, "severity": "high", "severity__origin": "model_emitted",
         "finding_kind": "gap", "finding_kind__origin": "model_emitted"}
    r.update(over)
    return r


def run_repo(root, kit_config, opts=None):
    config = core.deep_merge(kit_config, {"gates": {"provenance": {"contract": "contract.toml"}}})
    return g.run(config, g.output_scope(root, config, opts or {}), opts or {})


def rules(r):
    return {f.rule for f in r.findings}


@pytest.mark.parametrize("canary,rule", [
    ("missing_origin", "missing_origin"),
    ("code_supplied_disallowed", "code_supplied_disallowed"),
    ("unknown_origin", "unknown_origin"),
])
def test_each_canary_is_violated_with_its_rule(canary, rule):
    r = g.run_canary(CANARIES / "provenance" / canary)
    assert r.verdict is Verdict.VIOLATED
    assert rule in rules(r), rules(r)


def test_model_emitted_rows_are_satisfied(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "out/findings.governed.jsonl": jsonl(ok_row())})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.SATISFIED
    assert r.details["rows_checked"] == 1


def test_promoted_is_never_flagged(make_repo, kit_config):
    """Two-origin designs misreport correct promotions and get switched off."""
    root = make_repo({"contract.toml": CONTRACT,
                      "findings.governed.jsonl": jsonl(ok_row(severity__origin="promoted"),
                                                       ok_row(finding_kind__origin="promoted"))})
    assert run_repo(root, kit_config).verdict is Verdict.SATISFIED


def test_code_supplied_is_allowed_where_the_contract_allows_it(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT,
                      "findings.governed.jsonl": jsonl(ok_row(finding_kind__origin="code_supplied"))})
    assert run_repo(root, kit_config).verdict is Verdict.SATISFIED


def test_ungraded_marker_with_code_supplied_origin_is_accepted(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT,
                      "findings.governed.jsonl": jsonl(ok_row(severity="ungraded",
                                                              severity__origin="code_supplied"))})
    assert run_repo(root, kit_config).verdict is Verdict.SATISFIED


def test_a_code_supplied_grade_is_violated(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT,
                      "findings.governed.jsonl": jsonl(ok_row(severity="low", severity__origin="code_supplied"))})
    r = run_repo(root, kit_config)
    assert "code_supplied_disallowed" in rules(r)
    assert r.findings[0].line == 1


def test_missing_governed_field_is_an_error_not_a_skip(make_repo, kit_config):
    row = ok_row()
    del row["finding_kind"], row["finding_kind__origin"]
    row["finding_type"] = "gap"
    root = make_repo({"contract.toml": CONTRACT, "findings.governed.jsonl": jsonl(row)})
    assert "missing_field" in rules(run_repo(root, kit_config))


def test_output_for_a_target_not_in_the_contract_is_violated(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "other.governed.jsonl": jsonl(ok_row())})
    assert "unknown_target" in rules(run_repo(root, kit_config))


def test_no_outputs_is_inexpressible(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.INEXPRESSIBLE
    assert core.EMPTY_ENUMERATION in r.notes


def test_empty_output_file_is_inexpressible(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "findings.governed.jsonl": ""})
    assert run_repo(root, kit_config).verdict is Verdict.INEXPRESSIBLE


def test_outputs_dir_option_scans_a_run_directory(tmp_path, make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT})
    run_dir = tmp_path / "run"
    run_dir.mkdir()
    (run_dir / "findings.governed.jsonl").write_text(jsonl(ok_row(severity__origin=None)))
    r = run_repo(root, kit_config, {"outputs_dir": str(run_dir), "repo_root": str(root)})
    assert "unknown_origin" in rules(r)


def test_malformed_output_is_not_a_clean_result(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "findings.governed.jsonl": "{not json\n"})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.NOT_RUN


def test_exactly_three_origins():
    """reversion, "Three origins, not two": the only paper-sourced constant of its kind."""
    assert g.ORIGINS == ("model_emitted", "code_supplied", "promoted")
