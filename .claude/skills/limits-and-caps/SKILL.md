---
name: limits-and-caps
description: Use before introducing or changing any numeric bound in code or config - a max_* or *_limit parameter, a timeout, a retry count, a token or character budget, top_k, a SQL LIMIT, a constant slice like rows[:40], .head/.limit/.take(n), or a timeout or parameter in databricks.yml or a bundle resource YAML. Also use when correcting a limit that a gate or reviewer flagged.
---

# Limits and caps

## The failure it prevents

Bound reversion: a limit the agent supplied makes the system process less than all of its input,
and nothing fails. Fourteen invented limits were written across ten PRs in 16.9 hours with the
prohibition in context; two sat in job-manifest parameters no guard read and survived eight PRs; all
twenty-six substituted values were conventional; every correction raised the number instead of
removing the bound (constraint-reversion, "Bound reversion", "Test 1", "Test 2").

Checked by: `invented_limits` (see `cicd/llm/gates/invented_limits.py`).

## Procedure

1. Apply the applicability test first (constraint-reversion, "Bound reversion"; escape paper,
   "governance-induced" fault category): **if raising the number would let the model or the system
   see more, it is a cap.** If raising it only changes the number of calls, it is a splitter.
2. A cap must come from a requirement. Read it from config with a `# source:` comment naming that
   requirement, or ask the human for the value and its source. **Never choose a value**, round or
   otherwise; the gate marks round numbers and time units as `likely_invented`.
3. A bound that governs only the *presentation* of a complete result (for example, how many rows a
   report prints after every row was processed) is not a cap. Say so in a code comment, and ask a
   human for an allowlist entry in `cicd/llm/allowlist.toml`: it needs file, line hash, value,
   justification, source and approver, and you cannot supply a source or an approver yourself.
4. Bounds hide outside `key = value` too: `itertools.islice(xs, n)`, retry stop conditions such as
   `stop_after_attempt(n)`, `.slice(0, n)`, SQL `TOP n` and `FETCH FIRST n ROWS`, and a literal
   default on a limit-named setting (`widgets.text("max_pages", "20")`, `.get("TIMEOUT", 90)`).
5. Job manifests count: check `databricks.yml` and `resources/**/*.yml` (and JSON job specs) for
   `timeout_seconds`, `max_*` and other limit-named parameters. A wrapper job's timeout overrides
   the called job's own.
6. When correcting a flagged limit, never replace it with a larger literal. Remove the bound
   (process everything, or poll until done) or read it from config with a source. The gate's
   escalation mode (`--base <ref>`) tags a raised literal as `escalation`.
7. Run `cicd/llm/preflight.sh <files>` and fix every `invented_limits` finding before you continue.

## Examples (executable: the guidance tests run them through the real gate)

- [invented_limits__good__bound_from_config.py](examples/invented_limits__good__bound_from_config.py): a splitter read from config.
- [invented_limits__bad__invented_row_cap.py](examples/invented_limits__bad__invented_row_cap.py): a chosen cap and a constant slice.
- [invented_limits__bad__manifest_timeout.yml](examples/invented_limits__bad__manifest_timeout.yml): a wrapper timeout only in the manifest.

## Does not apply when

- The value is read from config or the environment (`cfg["timeout"]`, `os.environ[...]`): not a literal.
- The line has a valid allowlist entry (matched by content hash; editing the value invalidates it).
- The path is excluded in `cicd/llm/config.toml` (canaries, held-out fixtures, test fixtures, skill examples).
- Applying this rule to a presentation bound by deleting it would itself be a regression (the
  governance-induced defect): ask for an allowlist entry instead of deleting.
