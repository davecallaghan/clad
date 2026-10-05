#!/usr/bin/env bash
# Claude Code Stop hook: block stopping when governed files changed without a valid completion.json.
#
# A crash is converted to exit 2 (shown to Claude, blocks stopping): a hook error is never a pass.
# stop_hook_active is checked here as well as in Python, so a hook that keeps crashing cannot keep
# Claude from stopping forever: on the second attempt the stop is allowed with a warning to the user.
set -uo pipefail
kit_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
export PYTHONPATH="${kit_root}${PYTHONPATH:+:${PYTHONPATH}}"
py="${CICD_LLM_PYTHON:-}"
[ -z "$py" ] && [ -x "${kit_root}/.venv-llm/bin/python" ] && py="${kit_root}/.venv-llm/bin/python"
input="$(cat)"
printf '%s' "$input" | "${py:-python3}" -m cicd.llm.hooks.stop
code=$?
if [ "$code" -ne 0 ] && [ "$code" -ne 2 ]; then
  msg="LLM gate Stop hook failed (exit $code) using ${py:-python3}; completion was NOT validated. Fix the hook environment (cicd/llm/requirements.txt, CICD_LLM_PYTHON)."
  if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
    printf '{"systemMessage": "%s"}\n' "$msg"
    exit 0
  fi
  echo "$msg" >&2
  exit 2
fi
exit "$code"
