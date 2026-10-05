"""Gate 2, governed_write (gate half): call sites, signature, quarantine alarm."""

from __future__ import annotations

import json

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import governed_write as g
from cicd.llm.tests.helpers import CANARIES

CONTRACT = """[targets.findings]
ungraded_marker = "ungraded"
[targets.findings.fields.severity]
domain = ["high", "low"]
origins = ["model_emitted"]
"""
GATE_DEF = "def write_gate(rows, contract, target):\n    return rows\n"
GATED = (
    "def publish(sink, rows, contract):\n"
    "    out = write_gate(rows, contract, 'findings')\n"
    "    sink.write_rows('findings', out.accepted)\n"
)


def config_for(kit_config, **over):
    cfg = core.deep_merge(kit_config, {"gates": {"governed_write": {"contract": "contract.toml", **over}}})
    return cfg


def run_repo(root, config):
    sec = config["gates"]["governed_write"]
    return g.run(config, core.derive_scope(root, sec["include"], sec.get("exclude", [])))


def rules(r):
    return {f.rule for f in r.findings}


@pytest.mark.parametrize("canary,rule", [
    ("ungated_write", "ungated_write"),
    ("contract_default_none", "contract_has_default"),
    ("quarantine_alarm", "quarantine_nonzero"),
])
def test_each_canary_is_violated_with_its_rule(canary, rule):
    r = g.run_canary(CANARIES / "governed_write" / canary)
    assert r.verdict is Verdict.VIOLATED
    assert rule in rules(r), rules(r)


def test_gated_write_is_satisfied(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": GATED})
    r = run_repo(root, config_for(kit_config))
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]
    assert r.details["gated_writes"] == 1


def test_write_gated_for_a_different_target_is_still_ungated(make_repo, kit_config):
    src = GATED.replace("write_gate(rows, contract, 'findings')", "write_gate(rows, contract, 'other')")
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    assert "ungated_write" in rules(run_repo(root, config_for(kit_config)))


def test_target_named_by_module_constant_is_resolved(make_repo, kit_config):
    src = "TARGET = 'findings'\n" + GATED.replace("'findings'", "TARGET")
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    r = run_repo(root, config_for(kit_config))
    assert r.verdict is Verdict.SATISFIED
    assert r.details["unresolved_writes"] == 0


def test_spark_table_write_with_qualified_name_is_governed(make_repo, kit_config):
    src = "def f(df):\n    df.write.saveAsTable('main.analysis.findings')\n"
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    assert "ungated_write" in rules(run_repo(root, config_for(kit_config)))


def test_write_to_a_non_governed_target_is_not_flagged(make_repo, kit_config):
    src = "def f(sink, rows):\n    sink.write_rows('audit_log', rows)\n"
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    assert run_repo(root, config_for(kit_config)).verdict is Verdict.SATISFIED


def test_dynamic_target_is_counted_as_a_blind_spot(make_repo, kit_config):
    src = "def f(sink, rows, name):\n    sink.write_rows(name, rows)\n"
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    r = run_repo(root, config_for(kit_config))
    assert r.details["unresolved_writes"] == 1
    assert r.verdict is Verdict.SATISFIED


def test_missing_write_gate_definition_is_violated(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "p.py": "x = 1\n"})
    assert "write_gate_missing" in rules(run_repo(root, config_for(kit_config)))


def test_contract_keyword_only_is_violated(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT,
                      "gate.py": "def write_gate(rows, target, *, contract):\n    return rows\n"})
    assert "contract_not_positional" in rules(run_repo(root, config_for(kit_config)))


def test_quarantine_alarm_is_not_run_without_a_manifest(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF})
    assert run_repo(root, config_for(kit_config)).sub_checks["quarantine_alarm"] == "NOT_RUN"


def test_expected_quarantine_with_a_source_is_not_flagged(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF,
                      "m.json": json.dumps({"quarantine": {"findings": 2}})})
    cfg = config_for(kit_config, quarantine_manifest="m.json",
                     expected_quarantine={"findings": {"count": 2, "source": "REQ-7"}})
    assert run_repo(root, cfg).verdict is Verdict.SATISFIED


def test_expected_quarantine_without_a_source_does_not_count(make_repo, kit_config):
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF,
                      "m.json": json.dumps({"quarantine": {"findings": 2}})})
    cfg = config_for(kit_config, quarantine_manifest="m.json",
                     expected_quarantine={"findings": {"count": 2}})
    assert "quarantine_nonzero" in rules(run_repo(root, cfg))


def test_contract_with_no_targets_is_inexpressible(make_repo, kit_config):
    root = make_repo({"contract.toml": "# nothing governed\n", "gate.py": GATE_DEF})
    r = run_repo(root, config_for(kit_config))
    assert r.verdict is Verdict.INEXPRESSIBLE


def test_kit_reference_pipeline_is_satisfied():
    """The kit's own clean example passes against the kit's own contract."""
    root = core.repo_root(CANARIES)
    config = core.load_config()
    sec = config["gates"]["governed_write"]
    r = g.run(config, core.derive_scope(root, ["cicd/llm/**/*.py"], sec["exclude"]))
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]
    assert r.details["gated_writes"] >= 1


# ---- v2 ------------------------------------------------------------------------------------------

def test_sql_insert_into_a_governed_table_is_flagged(make_repo, kit_config):
    src = "def f(spark):\n    spark.sql(\"INSERT INTO main.dd.findings SELECT * FROM staged\")\n"
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    assert "sql_write" in rules(run_repo(root, config_for(kit_config)))


def test_sql_write_to_a_non_governed_table_is_not_flagged(make_repo, kit_config):
    src = "def f(spark):\n    spark.sql('MERGE INTO audit_log USING s ON 1=1')\n"
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    assert run_repo(root, config_for(kit_config)).verdict is Verdict.SATISFIED


def test_gated_write_that_writes_the_raw_rows_is_flagged(make_repo, kit_config):
    src = ("def publish(sink, rows, contract):\n    out = write_gate(rows, contract, 'findings')\n"
           "    sink.write_rows('findings', rows)\n    return out\n")
    root = make_repo({"contract.toml": CONTRACT, "gate.py": GATE_DEF, "p.py": src})
    assert "raw_rows_written" in rules(run_repo(root, config_for(kit_config)))
