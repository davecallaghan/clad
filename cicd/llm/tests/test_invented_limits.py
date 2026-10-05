"""Gate 1, invented_limits: bound reversion at source (reversion paper, "Bound reversion")."""

from __future__ import annotations

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import invented_limits as g
from cicd.llm.tests.helpers import CANARIES, KIT, commit_all, write_files

FIXTURES = KIT / "tests" / "fixtures"


def run_repo(root, config, opts=None):
    section = config["gates"]["invented_limits"]
    scope = core.derive_scope(root, section["include"], section.get("exclude", []))
    return g.run(config, scope, opts or {})


def rules(result):
    return {f.rule for f in result.findings}


# ---- canaries: every flag type fires --------------------------------------------------------

@pytest.mark.parametrize("canary,rule", [
    ("limit_key_py", "limit_key"),
    ("sql_limit", "sql_limit"),
    ("constant_slice", "constant_slice"),
    ("truncating_call", "truncating_call"),
    ("manifest_timeout", "manifest_timeout"),
    ("manifest_only_wrapper_timeout", "manifest_timeout"),
    ("budget_below_capacity", "budget_below_capacity"),
])
def test_each_canary_is_violated_with_its_rule(canary, rule):
    result = g.run_canary(CANARIES / "invented_limits" / canary)
    assert result.verdict is Verdict.VIOLATED
    assert rule in rules(result), rules(result)


def test_manifest_only_canary_has_no_code_files():
    """The case that survived eight PRs lives only in a manifest; the canary must too."""
    d = CANARIES / "invented_limits" / "manifest_only_wrapper_timeout"
    assert [p.suffix for p in d.rglob("*") if p.is_file()] == [".yml"]


# ---- what it flags, per language ------------------------------------------------------------

def test_python_keyword_default_dict_and_assignment_are_flagged(make_repo, kit_config):
    root = make_repo({"a.py": (
        "def f(timeout=30):\n    pass\n"
        "call(top_k=5)\n"
        "cfg = {'max_rows': 500}\n"
        "row_limit = 100\n")})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.VIOLATED
    assert sorted(f.line for f in r.findings if f.rule == "limit_key") == [1, 3, 4, 5]


def test_negative_index_slice_is_flagged(make_repo, kit_config):
    """The escape paper's held-out record-loss fault was a negative-index slice."""
    root = make_repo({"a.py": "def f(rows):\n    return rows[:-1]\n"})
    assert "constant_slice" in rules(run_repo(root, kit_config))


def test_sql_limit_inside_python_string_is_flagged(make_repo, kit_config):
    root = make_repo({"a.py": (FIXTURES / "sql_limit_in_python_string.py").read_text()})
    assert "sql_limit" in rules(run_repo(root, kit_config))


def test_scala_and_ts_are_scanned_by_regex_and_marked_proxy(make_repo, kit_config):
    root = make_repo({"A.scala": "val maxRows: Int = 100\nrows.take(10)\n",
                      "b.ts": "const requestTimeout = 600;\n"})
    r = run_repo(root, kit_config)
    found = {(f.file, f.rule) for f in r.findings}
    assert ("A.scala", "limit_key") in found
    assert ("A.scala", "truncating_call") in found
    assert ("b.ts", "limit_key") in found
    assert all("proxy:regex" in f.tags for f in r.findings)


def test_json_job_spec_timeout_is_flagged(make_repo, kit_config):
    root = make_repo({"job.json": '{"name": "j", "timeout_seconds": 3600}\n'})
    r = run_repo(root, kit_config)
    assert "manifest_timeout" in rules(r)
    assert r.findings[0].line == 1


def test_scanned_file_count_is_reported_per_type(make_repo, kit_config):
    root = make_repo({"a.py": "x = 1\n", "b.py": "y = 2\n", "q.sql": "SELECT 1\n"})
    r = run_repo(root, kit_config)
    assert r.details["files_by_type"] == {".py": 2, ".sql": 1}
    assert r.scope_size == sum(r.details["files_by_type"].values())


# ---- what it must NOT flag ------------------------------------------------------------------

def test_values_read_from_config_or_environment_are_not_flagged(make_repo, kit_config):
    root = make_repo({"a.py": (
        "import os\n"
        "max_rows = int(os.environ['MAX_ROWS'])\n"
        "timeout = cfg['timeout']\n"
        "def f(limit_value=None):\n    pass\n"
        "items[: len(items)]\n")})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]


def test_clean_repository_is_satisfied(make_repo, kit_config):
    root = make_repo({"a.py": "def total(xs):\n    return sum(xs)\n"})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.SATISFIED
    assert r.findings == []


def test_excluded_paths_are_not_flagged(make_repo, kit_config):
    root = make_repo({"cicd/llm/canaries/x/a.py": "max_rows = 5\n", "b.py": "ok = True\n"})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.SATISFIED
    assert r.scope_size == 1


def _allow(file, line, value, **over):
    entry = {"file": file, "line_hash": core.line_hash(line), "value": value,
             "justification": "presentation cap on a complete result",
             "source": "REQ-1", "approver": "reviewer@example.invalid"}
    entry.update(over)
    body = "".join(f'{k} = {v!r}\n'.replace("'", '"') for k, v in entry.items() if v is not None)
    return "[[entry]]\n" + body


def test_allowlisted_value_is_not_flagged(make_repo, kit_config):
    line = "max_rows = 500"
    root = make_repo({"a.py": line + "\n",
                      "cicd/llm/allowlist.toml": _allow("a.py", line, 500)})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.SATISFIED
    assert r.details["allowlisted"] == 1


def test_allowlist_entry_missing_source_is_violated(make_repo, kit_config):
    line = "max_rows = 500"
    root = make_repo({"a.py": line + "\n",
                      "cicd/llm/allowlist.toml": _allow("a.py", line, 500, source=None)})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.VIOLATED
    assert "allowlist_invalid" in rules(r)


def test_editing_an_allowlisted_value_invalidates_the_entry(make_repo, kit_config):
    root = make_repo({"a.py": "max_rows = 900\n",
                      "cicd/llm/allowlist.toml": _allow("a.py", "max_rows = 500", 500)})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.VIOLATED
    assert "limit_key" in rules(r)


# ---- likely_invented and escalation ---------------------------------------------------------

def test_likely_invented_marks_round_and_time_values_only(make_repo, kit_config):
    root = make_repo({"a.py": "max_a = 4000\nmax_b = 437\nmax_c = 1800\nmax_d = 90\n"})
    flags = {f.line: f.likely_invented for f in run_repo(root, kit_config).findings}
    assert flags == {1: True, 2: False, 3: True, 4: True}


def test_likely_invented_does_not_change_the_verdict(make_repo, kit_config):
    root = make_repo({"a.py": "max_b = 437\n"})
    assert run_repo(root, kit_config).verdict is Verdict.VIOLATED


def test_raising_a_flagged_literal_is_tagged_escalation(make_repo, kit_config):
    root = make_repo({"a.py": "timeout = 14400\n"})
    base = commit_all(root, "base")
    write_files(root, {"a.py": "timeout = 28800\n"})
    commit_all(root, "raise it")
    r = run_repo(root, kit_config, {"base": base})
    assert [f.tags for f in r.findings] == [["escalation"]]


def test_lowering_or_keeping_a_literal_is_not_escalation(make_repo, kit_config):
    root = make_repo({"a.py": "timeout = 600\nmax_rows = 10\n"})
    base = commit_all(root, "base")
    write_files(root, {"a.py": "timeout = 300\nmax_rows = 10\n"})
    commit_all(root, "lower it")
    r = run_repo(root, kit_config, {"base": base})
    assert all("escalation" not in f.tags for f in r.findings)


# ---- scope and sub-check states -------------------------------------------------------------

def test_empty_scope_is_inexpressible(make_repo, kit_config):
    root = make_repo({"README.md": "no code\n"})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.INEXPRESSIBLE
    assert core.EMPTY_ENUMERATION in r.notes


def test_budget_subcheck_is_not_run_without_model_capacity(make_repo, kit_config):
    root = make_repo({"a.py": "ok = True\n"})
    r = run_repo(root, kit_config)
    assert r.sub_checks["budget_below_capacity"] == "NOT_RUN"


def test_unparseable_file_is_not_a_clean_result(make_repo, kit_config):
    root = make_repo({"a.py": "def broken(:\n"})
    r = run_repo(root, kit_config)
    assert r.verdict is Verdict.NOT_RUN
    assert r.details["unparseable"] == ["a.py"]
