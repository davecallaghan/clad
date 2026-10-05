#!/usr/bin/env bash
# Fast LLM gates on changed files (guidance 3.4).
#
# Exists to catch an LLM-gate violation (an invented limit, a swallowed exception, a removed
# safeguard, an ungated governed write) while the agent is still editing, before CI. Exits 1 on
# VIOLATED and 2 on NOT_RUN or INEXPRESSIBLE: a check that did not run is not a passing check.
#
#   cicd/llm/preflight.sh [paths...]     # default: changed against HEAD, plus untracked
#
# The interpreter needs PyYAML and ruff (cicd/llm/requirements.txt); set CICD_LLM_PYTHON to choose it.
set -euo pipefail
kit_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PYTHONPATH="${kit_root}${PYTHONPATH:+:${PYTHONPATH}}"
exec "${CICD_LLM_PYTHON:-python3}" -m cicd.llm.preflight "$@"
