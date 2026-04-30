#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Building Lean evaluator ==="
cd "$REPO_ROOT/lean"
export PATH="$HOME/.elan/bin:$PATH"
lake build clad-difftest

echo "=== Running differential test ==="
cd "$REPO_ROOT/code"
LEAN_DIFFTEST_EXE="$REPO_ROOT/lean/.lake/build/bin/clad-difftest" sbt 'difftest/test'
