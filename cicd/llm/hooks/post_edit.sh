#!/usr/bin/env bash
# Claude Code PostToolUse hook (Edit|Write|MultiEdit): preflight on the edited file.
#
# Exists because a hook that dies is silent: Claude Code treats any exit other than 0 and 2 as a
# non-blocking error that only reaches the debug log, so a crash would read as a pass. Any other
# exit is converted to 2 here, with the error shown to Claude (guidance 3.5: never swallow errors).
set -uo pipefail
kit_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
export PYTHONPATH="${kit_root}${PYTHONPATH:+:${PYTHONPATH}}"
py="${CICD_LLM_PYTHON:-}"
[ -z "$py" ] && [ -x "${kit_root}/.venv-llm/bin/python" ] && py="${kit_root}/.venv-llm/bin/python"
"${py:-python3}" -m cicd.llm.hooks.post_edit
code=$?
if [ "$code" -ne 0 ] && [ "$code" -ne 2 ]; then
  echo "LLM gate post-edit hook failed (exit $code) using ${py:-python3}. This is a hook error, not a pass:" \
       "fix the hook environment (cicd/llm/requirements.txt, CICD_LLM_PYTHON)." >&2
  exit 2
fi
exit "$code"
