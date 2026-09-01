#!/usr/bin/env bash
# Fail if any test was cancelled.
#
# ScalaTest's `assume` cancels a test rather than failing it, and sbt then prints
# "All tests passed" in the same run as "canceled 2". The differential test
# against the Lean model was inert from April to September because of this.
# A skipped check is not a passing check.
set -euo pipefail

log="${1:?usage: assert-no-cancelled-tests.sh <sbt-log-file>}"

cancelled=$(grep -oE 'canceled [0-9]+' "$log" | grep -oE '[0-9]+' | paste -sd+ - | bc)
cancelled=${cancelled:-0}

if [ "$cancelled" -gt 0 ]; then
  echo "FAIL: $cancelled test(s) cancelled. A cancelled test is not a passing test."
  echo
  grep -nE 'canceled [1-9]|CANCELED' "$log" | head -20
  exit 1
fi
echo "OK: no cancelled tests."
