#!/usr/bin/env bash
# Create the Python environment the LLM gate hooks, preflight and tests use: <repo>/.venv-llm,
# with the pinned tools in cicd/llm/requirements.txt. The hooks pick it up automatically
# (CICD_LLM_PYTHON overrides). Exists because a hook whose interpreter lacks PyYAML or ruff
# reports an error on every edit (a hook error is never treated as a pass).
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if command -v uv >/dev/null 2>&1; then
  uv venv -q "${root}/.venv-llm" --python 3.12
  uv pip install -q --python "${root}/.venv-llm/bin/python" -r "${root}/cicd/llm/requirements.txt"
else
  python3 -m venv "${root}/.venv-llm"
  "${root}/.venv-llm/bin/python" -m pip install -q -r "${root}/cicd/llm/requirements.txt"
fi
"${root}/.venv-llm/bin/python" -c "import yaml, pytest, ruff; print('ok: .venv-llm ready')"
