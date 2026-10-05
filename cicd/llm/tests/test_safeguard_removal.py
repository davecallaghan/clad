"""Gate 6, safeguard_removal: removals the reviewer missed (reversion, "Why review does not catch
a control dying": 3/3 additions caught, 0/3 removals). Tests use real git repos and commits."""

from __future__ import annotations

import pytest

from cicd.lib.verdict import Verdict
from cicd.llm import core
from cicd.llm.gates import safeguard_removal as g
from cicd.llm.tests.helpers import CANARIES, commit_all, write_files

GUARDS = ("from cicd.llm.safeguard import safeguard\n\n\n@safeguard\n"
          "def check_rows(rows):\n    return rows\n")


def diff_run(make_repo, kit_config, base_src, head_src, head="HEAD", extra=None):
    root = make_repo({"guards.py": GUARDS, "m.py": base_src, **(extra or {})})
    base = commit_all(root, "base")
    write_files(root, {"m.py": head_src})
    if head == "HEAD":
        commit_all(root, "head")
    return g.run(kit_config, g.diff_scope(root, kit_config, {"base": base, "head": head}),
                 {"base": base, "head": head})


def rules(r):
    return {f.rule for f in r.findings}


@pytest.mark.parametrize("canary,rule", [
    ("deleted_canary_call", "deleted_safeguard_call"),
    ("swallowed_exception", "broadened_handler"),
    ("wrapped_safeguard", "wrapped_safeguard"),
    ("contract_gains_default", "parameter_gained_default"),
    ("discarded_quarantine", "write_gate_result_discarded"),
    ("unreachable_guard", "unreachable_guard"),
    ("deleted_assertion_and_skip", "assertion_deleted"),
])
def test_each_canary_is_violated_with_its_rule(canary, rule):
    r = g.run_canary(CANARIES / "safeguard_removal" / canary)
    assert r.verdict is Verdict.VIOLATED
    assert rule in rules(r), rules(r)


def test_swallowed_exception_canary_also_reports_the_neutered_body():
    r = g.run_canary(CANARIES / "safeguard_removal" / "swallowed_exception")
    assert "neutered_handler" in rules(r)


def test_skip_canary_also_reports_the_new_skip():
    r = g.run_canary(CANARIES / "safeguard_removal" / "deleted_assertion_and_skip")
    assert "test_skipped" in rules(r)


def test_bare_except_counts_as_broadened(make_repo, kit_config):
    r = diff_run(make_repo, kit_config,
                 "def f():\n    try:\n        g()\n    except KeyError:\n        raise\n",
                 "def f():\n    try:\n        g()\n    except:\n        raise\n")
    assert "broadened_handler" in rules(r)


def test_continue_in_handler_counts_as_neutered(make_repo, kit_config):
    r = diff_run(make_repo, kit_config,
                 "def f(xs):\n    for x in xs:\n        try:\n            g(x)\n        except KeyError:\n            raise\n",
                 "def f(xs):\n    for x in xs:\n        try:\n            g(x)\n        except KeyError:\n            continue\n")
    assert "neutered_handler" in rules(r)


def test_if_false_around_a_safeguard_is_unreachable(make_repo, kit_config):
    r = diff_run(make_repo, kit_config, "def f(r):\n    check_rows(r)\n",
                 "def f(r):\n    if False:\n        check_rows(r)\n")
    assert "unreachable_guard" in rules(r)


def test_safeguard_marker_comment_is_honoured(make_repo, kit_config):
    r = diff_run(make_repo, kit_config,
                 "def f(r):\n    verify(r)  # safeguard\n    return r\n",
                 "def f(r):\n    return r\n")
    assert "deleted_safeguard_call" in rules(r)


def test_worktree_head_is_compared_against_base(make_repo, kit_config):
    r = diff_run(make_repo, kit_config, "def f(r):\n    check_rows(r)\n", "def f(r):\n    return r\n",
                 head="WORKTREE")
    assert "deleted_safeguard_call" in rules(r)


def test_deleting_a_whole_file_with_safeguard_calls_is_flagged(make_repo, kit_config):
    root = make_repo({"guards.py": GUARDS, "m.py": "def f(r):\n    check_rows(r)\n"})
    base = commit_all(root, "base")
    (root / "m.py").unlink()
    commit_all(root, "delete")
    r = g.run(kit_config, g.diff_scope(root, kit_config, {"base": base}), {"base": base})
    assert "deleted_safeguard_call" in rules(r)


# ---- must NOT flag ----------------------------------------------------------------------------

def test_adding_a_safeguard_call_and_an_assertion_is_satisfied(make_repo, kit_config):
    r = diff_run(make_repo, kit_config, "def f(r):\n    return r\n",
                 "def f(r):\n    check_rows(r)\n    assert r\n    return r\n")
    assert r.verdict is Verdict.SATISFIED, [f.to_dict() for f in r.findings]


def test_narrowing_a_handler_is_satisfied(make_repo, kit_config):
    r = diff_run(make_repo, kit_config,
                 "def f():\n    try:\n        g()\n    except Exception:\n        raise\n",
                 "def f():\n    try:\n        g()\n    except KeyError:\n        raise\n")
    assert r.verdict is Verdict.SATISFIED


def test_unrelated_edit_is_satisfied(make_repo, kit_config):
    r = diff_run(make_repo, kit_config, "def f(r):\n    check_rows(r)\n    return r\n",
                 "def f(r):\n    check_rows(r)\n    return list(r)\n")
    assert r.verdict is Verdict.SATISFIED


def test_keeping_the_write_gate_result_is_satisfied(make_repo, kit_config):
    r = diff_run(make_repo, kit_config, "def f(r, c):\n    return r\n",
                 "def f(r, c):\n    out = write_gate(r, c, 't')\n    return out.accepted\n")
    assert r.verdict is Verdict.SATISFIED


# ---- scope ------------------------------------------------------------------------------------

def test_no_safeguards_in_scope_is_violated(make_repo, kit_config):
    root = make_repo({"m.py": "def f(r):\n    return r\n"})
    base = commit_all(root, "base")
    write_files(root, {"m.py": "def f(r):\n    return list(r)\n"})
    commit_all(root, "head")
    r = g.run(kit_config, g.diff_scope(root, kit_config, {"base": base}), {"base": base})
    assert "no_safeguards" in rules(r)


def test_no_base_is_not_run(make_repo, kit_config):
    root = make_repo({"guards.py": GUARDS})
    r = g.run(kit_config, g.diff_scope(root, kit_config, {}), {})
    assert r.verdict is Verdict.NOT_RUN


def test_no_changed_python_files_is_inexpressible(make_repo, kit_config):
    root = make_repo({"guards.py": GUARDS, "README.md": "a\n"})
    base = commit_all(root, "base")
    write_files(root, {"README.md": "b\n"})
    commit_all(root, "head")
    r = g.run(kit_config, g.diff_scope(root, kit_config, {"base": base}), {"base": base})
    assert r.verdict is Verdict.INEXPRESSIBLE
    assert core.EMPTY_ENUMERATION in r.notes


# ---- v2 ------------------------------------------------------------------------------------------

def test_handler_that_logs_and_continues_is_neutered(make_repo, kit_config):
    r = diff_run(make_repo, kit_config,
                 "def f(xs):\n    for x in xs:\n        try:\n            g(x)\n        except KeyError:\n            raise\n",
                 "def f(xs):\n    for x in xs:\n        try:\n            g(x)\n        except KeyError:\n            log.warning('skip')\n            continue\n")
    assert "neutered_handler" in rules(r)


def test_inline_use_of_write_gate_result_discards_the_quarantine(make_repo, kit_config):
    r = diff_run(make_repo, kit_config,
                 "def f(s, r, c):\n    out = write_gate(r, c, 't')\n    s.write_rows('t', out.accepted)\n    s.write_rows('q', out.quarantined)\n",
                 "def f(s, r, c):\n    s.write_rows('t', write_gate(r, c, 't').accepted)\n")
    assert "write_gate_result_discarded" in rules(r)


def test_new_conditional_exit_before_a_safeguard_is_flagged(make_repo, kit_config):
    r = diff_run(make_repo, kit_config, "def f(r):\n    return check_rows(r)\n",
                 "def f(r):\n    if FAST:\n        return r\n    return check_rows(r)\n")
    assert "conditional_bypass" in rules(r)


def test_existing_conditional_exit_is_not_new(make_repo, kit_config):
    src = "def f(r):\n    if not r:\n        return []\n    return check_rows(r)\n"
    r = diff_run(make_repo, kit_config, src, src.replace("return []", "return list()"))
    assert "conditional_bypass" not in rules(r)


def test_early_exit_in_a_new_function_bypasses_nothing(make_repo, kit_config):
    """Found by dogfood: an empty-input guard in brand-new code is not a bypass of an existing safeguard."""
    r = diff_run(make_repo, kit_config, "x = 1\n",
                 "x = 1\n\n\ndef f(r):\n    if not r:\n        return []\n    return check_rows(r)\n")
    assert "conditional_bypass" not in rules(r)
