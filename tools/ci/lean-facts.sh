#!/usr/bin/env bash
# Report the two facts the README asserts about the Lean model, and fail on sorry.
#
# NOTE: absence of `sorry` does NOT mean the proofs check. A file with a type error
# contains no `sorry` and does not compile either. Only `lake build` establishes
# that. This script reports necessary conditions cheaply; the proofs job in
# lean.yml is what earns the word "machine-checked".
set -euo pipefail

cd "$(dirname "$0")/../../lean"

# Search this model's own sources only. `.` would descend into .lake/packages,
# where mathlib's ~8,000 files both swamp the theorem count and make the grep take
# minutes. The model is Clad.lean plus Clad/.
SRC=(Clad.lean Clad)

# `|| true` on both: grep exits 1 when it finds nothing, and for the sorry count
# finding nothing is the outcome we want. Without it, `set -e -o pipefail` fails
# the script precisely when the model is clean.
# The name character class MUST include `.`: Lean names are namespaced, and a class
# without it truncates `ComponentSpec.compose_comm` to `ComponentSpec`, collapsing
# every theorem in a namespace into a single name. That undercounted this model by
# 4, the README shipped the undercount, and this script then confirmed it as correct.
theorems=$( { grep -rhoE "^[[:space:]]*(theorem|lemma) [A-Za-z_][A-Za-z0-9_.'!?]*" \
                 --include='*.lean' "${SRC[@]}" || true; } | awk '{print $2}' | sort -u | wc -l | tr -d ' ')
sorries=$( { grep -rhow 'sorry' --include='*.lean' "${SRC[@]}" || true; } | wc -l | tr -d ' ')

echo "theorems=$theorems"
echo "sorry=$sorries"

if [ "$sorries" -gt 0 ]; then
  echo "FAIL: $sorries occurrence(s) of sorry — the model has unfinished proofs." >&2
  grep -rnw 'sorry' --include='*.lean' "${SRC[@]}" >&2
  exit 1
fi
