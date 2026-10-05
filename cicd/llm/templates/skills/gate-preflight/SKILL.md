---
name: gate-preflight
description: Use before committing, before pushing, after editing files a gate checks, and whenever you need to run the LLM gates or interpret a gate verdict, its JSON output or its exit code (SATISFIED, VIOLATED, INEXPRESSIBLE, NOT_RUN).
---

# Gate preflight

## The failure it prevents

A gate that was not run, or whose silence was read as a pass. Eleven controls produced no effect:
present and silent, wired to nothing, or running and binding nothing; no output was
indistinguishable from no findings (constraint-reversion, "Detection surfaces, and how they
failed"). An undecided check coerced to a boolean becomes a pass (constraint-escape, "Constraint
semantics": the verdict type raises on Boolean coercion).

Checked by: every gate: invented_limits, governed_write, provenance, behavioural_harness,
review_findings, safeguard_removal, completion_evidence, ruff_profile.

## Procedure

1. Fast gates, on the files you changed (default: everything changed against HEAD, plus untracked files):

       cicd/llm/preflight.sh [paths...]

   It runs invented_limits, ruff_profile, safeguard_removal (HEAD against the working tree) and
   governed_write's call-site scan, prints one line per gate, and writes the full JSON to
   `cicd/llm/ledger/preflight.json`.
2. Any single gate: `python -m cicd.llm.gates.<gate> [--root DIR] [gate options]` prints JSON:
   `verdict`, `findings[]` (file, line, rule, message, `likely_invented`, tags), `scope_size`,
   `sub_checks`, `blind_spots`, `notes`, `details`, `tree_hash`. Exit 0 SATISFIED, 1 VIOLATED,
   2 INEXPRESSIBLE or NOT_RUN.
3. Read `sub_checks`: a sub-check marked NOT_RUN did not run, so its property is unverified. List it
   in `completion.json` `not_verified`.
4. **NOT_RUN and INEXPRESSIBLE are not passes.** INEXPRESSIBLE usually means "empty enumeration is
   not a clean surface": the derived scope was empty. Find out why; do not report it as clean.
5. Allowlisting needs a source and an approver, which an agent cannot supply. Ask a human.
6. The slow gates (behavioural harness, mutation check, liveness) run in CI
   (`.github/workflows/llm-gates.yml`).

## Examples (one good and one bad per gate; executable, copies of the per-skill examples)

See [examples/](examples/): `<gate>__good__<slug>` must be SATISFIED and `<gate>__bad__<slug>`
VIOLATED by the named gate. The guidance tests check that these copies match the originals.

## Does not apply when

- A path is in no fast gate's scope (for example a Markdown file): preflight prints
  "not applicable" for that gate. That is not a pass of the gate either; it means the gate does not
  read that file.
