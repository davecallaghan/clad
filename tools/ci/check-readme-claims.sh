#!/usr/bin/env bash
# Verify the factual claims README.md makes about this repository.
#
# The book this repository accompanies argues that assurance claims must be
# checkable at the point of assertion. This applies that to the README.
set -euo pipefail

cd "$(dirname "$0")/../.."

fail=0
note() { echo "FAIL: $*"; fail=1; }

# 1. The asserted theorem count must match the model.
actual=$(bash tools/ci/lean-facts.sh | grep '^theorems=' | cut -d= -f2)
asserted=$(grep -oE 'contains [0-9]+ theorems' README.md | grep -oE '[0-9]+' | head -1 || true)
if [ -z "$asserted" ]; then
  note "README does not state a theorem count in the expected form ('contains N theorems')."
elif [ "$asserted" != "$actual" ]; then
  note "README asserts $asserted theorems; the model has $actual."
else
  echo "OK: theorem count $actual matches the model."
fi

# 2. Claims the repository cannot support must not appear.
while IFS='|' read -r phrase reason; do
  if grep -qiF "$phrase" README.md; then
    note "README contains \"$phrase\" — $reason"
  fi
done <<'PHRASES'
mathematically proven correct|proofs of a Lean model do not establish correctness of the Scala implementation
independently verified against the production code|the differential test's status is reported by CI, not asserted here
zero mismatches|only true of a run; assert it in CI output, not in prose
PHRASES

if [ "$fail" -eq 0 ]; then echo "All README claims check out."; else exit 1; fi
