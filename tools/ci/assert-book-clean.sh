#!/usr/bin/env bash
# Fail on LaTeX errors, dangling references, or a badly overfull box.
#
# grep -a throughout: main.log contains bytes that make grep treat it as binary,
# which silently produces no output and would make every check pass.
set -uo pipefail

log="${1:?usage: assert-book-clean.sh <main.log> <max-overfull-pt>}"
maxpt="${2:-40}"

fail=0
errors=$(grep -a -cE '^!' "$log" || true)
undef=$(grep -a -c 'undefined' "$log" || true)
multi=$(grep -a -c 'multiply defined' "$log" || true)
worst=$(grep -a -oE 'Overfull \\hbox \([0-9.]+pt' "$log" | grep -oE '[0-9.]+' | sort -rn | head -1 || true)
worst=${worst:-0}

echo "errors=$errors undefined=$undef multiply-defined=$multi worst-overfull=${worst}pt"

[ "$errors" -eq 0 ] || { echo "FAIL: $errors LaTeX error(s)"; grep -a -E '^!' -A3 "$log" | head -30; fail=1; }
[ "$undef" -eq 0 ] || { echo "FAIL: $undef undefined reference(s)"; grep -a 'undefined' "$log" | head -10; fail=1; }
[ "$multi" -eq 0 ] || { echo "FAIL: $multi multiply-defined label(s)"; fail=1; }
if awk -v w="$worst" -v m="$maxpt" 'BEGIN { exit !(w > m) }'; then
  echo "FAIL: worst overfull box ${worst}pt exceeds ${maxpt}pt"; fail=1
fi

exit "$fail"
