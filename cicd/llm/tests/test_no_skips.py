"""Countermeasure 4: no skip, skipif, xfail, importorskip or assume anywhere in the tests,
checked statically over the derived set of test files, plus the JUnit checker itself."""

from __future__ import annotations

import ast
import subprocess
import sys
from pathlib import Path

from cicd.llm.tests.helpers import KIT

FORBIDDEN = ("skip", "skipif", "xfail", "importorskip", "assume")


def _forbidden_uses(text: str) -> list[str]:
    hits = []
    for node in ast.walk(ast.parse(text)):
        if isinstance(node, ast.Attribute) and node.attr in FORBIDDEN:
            hits.append(f"line {node.lineno}: .{node.attr}")
        if isinstance(node, ast.Name) and node.id in FORBIDDEN:
            hits.append(f"line {node.lineno}: {node.id}")
    return hits


def test_test_files_are_derived_and_non_empty():
    assert len(list((KIT / "tests").glob("test_*.py"))) > 0


def test_no_test_file_uses_a_skip_construct():
    offenders = {p.name: _forbidden_uses(p.read_text()) for p in (KIT / "tests").glob("*.py")
                 if p.name != Path(__file__).name}
    assert {k: v for k, v in offenders.items() if v} == {}


def test_the_static_check_detects_a_planted_skip():
    assert _forbidden_uses("import pytest\n@pytest.mark.skip\ndef test_x():\n    pass\n")


def _junit(tmp_path, body):
    p = tmp_path / "junit.xml"
    p.write_text(f'<testsuites><testsuite name="s">{body}</testsuite></testsuites>')
    return subprocess.run([sys.executable, str(KIT / "assert_no_skipped_tests.py"), str(p)],
                          capture_output=True, text=True)


def test_junit_checker_fails_on_a_skipped_case(tmp_path):
    r = _junit(tmp_path, '<testcase classname="a" name="t"><skipped message="x"/></testcase>')
    assert r.returncode == 1 and "skipped" in r.stdout


def test_junit_checker_fails_on_an_empty_run(tmp_path):
    assert _junit(tmp_path, "").returncode == 1


def test_junit_checker_passes_when_nothing_was_skipped(tmp_path):
    assert _junit(tmp_path, '<testcase classname="a" name="t"/>').returncode == 0
