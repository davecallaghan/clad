"""Fail if any pytest test was skipped, xfailed or xpassed.

Generalises tools/ci/assert-no-cancelled-tests.sh to pytest: a skipped check is not a passing
check. ScalaTest's `assume` once left the Lean differential test inert from April to September
while sbt printed "All tests passed"; pytest's skip and xfail have the same shape.

    python -m pytest cicd/llm/tests --junitxml=cicd/llm/ledger/junit.xml
    python cicd/llm/assert_no_skipped_tests.py cicd/llm/ledger/junit.xml
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET


def skipped_cases(path: str) -> list[str]:
    out = []
    for case in ET.parse(path).getroot().iter("testcase"):
        if case.find("skipped") is not None:
            out.append(f"{case.get('classname')}::{case.get('name')}")
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("junit", help="JUnit XML written by pytest --junitxml")
    path = ap.parse_args().junit
    total = sum(1 for _ in ET.parse(path).getroot().iter("testcase"))
    if total == 0:
        print("FAIL: the JUnit file records no tests. An empty run is not a passing run.")
        return 1
    skipped = skipped_cases(path)
    if skipped:
        print(f"FAIL: {len(skipped)} test(s) skipped or xfailed. A skipped test is not a passing test.")
        for name in skipped:
            print(f"  {name}")
        return 1
    print(f"OK: {total} tests, none skipped.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
