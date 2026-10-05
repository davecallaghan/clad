# clad

CLAD ("Trust by Design") is a governance framework for enterprise AI: a Scala 3 engine in `app/`, a
Lean 4 formal model in `lean/`, the book in `book/` (generated from `research/`), CI scripts in
`tools/ci/`, and in `cicd/llm/` a kit of CI gates for code written by LLM agents. Prose in this file
is advisory and is expected to leak; the gates and hooks named below are what bind, and CI is the
final authority.

## Non-negotiables

One rule per gate. The rule text is the registry text in `cicd/llm/rules.toml`.

- **LLM-LIMITS.** Do not introduce a numeric limit, timeout, budget, LIMIT or slice that makes the system process less than all of its input; a cap must come from a requirement. Applies when: you write a literal value for a limit-named key or parameter (`max_*`, `*_limit`, `*_cap`, `*_budget`, `*timeout*`, `top_k`), a SQL `LIMIT n`/`TOP n`/`FETCH FIRST n`, a constant slice bound, `.head/.limit/.take/.slice(n)`, a literal bound passed to `islice` or `stop_after_attempt`, a literal default for a limit-named setting, or a timeout in a job manifest, in `.py`, `.sql`, `.scala`, `.ts`, `.json`, `databricks.yml` or `resources/**/*.yml`. Does not apply when: the value is read from config or the environment, the line has an allowlist entry with a source and an approver, or the path is excluded in `cicd/llm/config.toml`. Checked by: invented_limits
- **LLM-GOVERNED-WRITE.** Every write of a governed value goes through write_gate with an explicit contract, and quarantined rows are handled, never dropped. Applies when: code calls a configured writer (`write_rows`, `saveAsTable`, `insertInto`) on a target named in `cicd/llm/contracts/governed_values.toml`, or defines `write_gate`. Does not apply when: the target is not in the contract. Checked by: governed_write
- **LLM-PROVENANCE.** Every governed field is stored with its origin: model_emitted, code_supplied or promoted. Applies when: rows for a contract target are stored (`<target>.governed.jsonl` or a run's outputs). Does not apply when: the field is not governed by the contract; `promoted` is never a violation, and the contract's ungraded marker stored as `code_supplied` is accepted. Checked by: provenance
- **LLM-NO-DEFAULT-GRADE.** A governed value is carried from what the model emitted; when the model emits nothing, store the ungraded marker, never a default grade. Applies when: code maps model output to a governed field. Does not apply when: the field is not in the contract. Checked by: behavioural_harness
- **LLM-REVIEW.** Act only on review findings that survived citation validation and corroboration; on the third repeat of a rule, stop and escalate to a human. Applies when: you respond to cross-architecture AI review findings. Does not apply when: a finding was dropped by validation or not corroborated (do not act on it). Checked by: review_findings
- **LLM-SAFEGUARDS.** Never delete, wrap, short-circuit or broaden a safeguard or its exception handling to make something pass. Applies when: you edit or delete code containing a `@safeguard` function, a `# safeguard` line, a canary call, an exception handler, an assertion, or a test. Does not apply when: you add a safeguard or an assertion, narrow a handler, or make an unrelated edit. Checked by: safeguard_removal
- **LLM-COMPLETION.** A completion claim is a completion.json with evidence for every check and a not_verified list; exit codes and green builds are not completion. Applies when: you state that a task is done. Does not apply when: you report progress without claiming completion. Checked by: completion_evidence
- **LLM-LINT.** No swallowed, blind or bare exception handlers, no unexplained magic comparisons, and no function above the complexity ratchet. Applies when: you change Python files. Does not apply when: the file is not Python, or is excluded in `cicd/llm/config.toml`. Checked by: ruff_profile

## Definition of done

1. Run `cicd/llm/preflight.sh` on the files you changed and paste its output.
2. Write `completion.json` (schema: `cicd/llm/gates/completion_evidence.py`) with a check entry and an
   evidence file for every gate you ran, and a `not_verified` list naming every NOT_RUN sub-check and
   every surface you did not check.
3. Run `python -m cicd.llm.gates.completion_evidence --completion completion.json` and paste its output.

Exit codes, green builds and "tests pass" are not done.

## Skills

- `limits-and-caps`: before introducing or changing any numeric bound, timeout, budget, `LIMIT`, slice or manifest parameter.
- `governed-values`: before writing or changing code that stores a model-produced judgement.
- `touching-safeguards`: before editing code with `@safeguard` functions, exception handlers or canary calls, or deleting tests.
- `finishing-a-task`: before claiming a task is complete.
- `responding-to-ai-review`: before acting on cross-architecture review findings.
- `gate-preflight`: before committing, or when interpreting a gate verdict.

## Verdicts

SATISFIED (exit 0) is a pass. VIOLATED (exit 1) is a failure. INEXPRESSIBLE (the property cannot be
decided here) and NOT_RUN (a required input or config value is missing) exit 2 and are **not
passes**: report them, never treat them as clean.
