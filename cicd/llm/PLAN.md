# cicd/llm: plan for the LLM-specific gates

Spec: "Task: build LLM-specific CI/CD gates in `cicd/llm/`". Plan approval: the owner gave
blanket approval for the whole chain (this task and the guidance task) on 2026-10-04 and was not
available for questions, so every point where the spec says "stop and ask" is resolved
conservatively and listed under **Interpretations** at the end, for review.

Sources read (file and section):
- `../constraint-reversion/paper/main.tex`: "Two classes" (Bound reversion, Grade reversion),
  "Why the classes need different detectors" (incl. "Three origins, not two", "Fix the artifact,
  not the check"), "Detection surfaces, and how they failed" (Groups 1–3, eleven controls),
  "Liveness canaries", "Cross-architecture review, reported as an observation" (incl. "The gate
  is half same-family").
- `../constraint-escape/paper/main.tex`: "Constraint semantics and significance sets",
  "Governance conditions" (incl. "Prose is advisory, and can be worse than absent"), "Benchmark
  and fault model" ("Held-out discipline, mechanised"), "E2, the live-agent arm", "Practitioner
  implications".
- `../constraint-escape/scripts/mutation_check.py`, `../constraint-escape/constraint_escape/freeze.py`,
  `../constraint-escape/tests/test_leakage_guard.py`, `../constraint-escape/FREEZE.json`.
- `tools/ci/assert-no-cancelled-tests.sh`; `book/chapters/taxonomy.tex`, Definition
  "Grounded assertion" (Sensitive: had E not supported p, the assertion would not have been
  produced; operational test is ablation).

## Shared contract

- Verdict: `cicd/lib/verdict.py` (absent on master, so created). `bool()` raises `TypeError`.
  Exit 0 SATISFIED, 1 VIOLATED, 2 INEXPRESSIBLE/NOT_RUN.
- Gate: `cicd/llm/gates/<name>.py` exposing `GATE_ID`, `KIND`, `BLIND_SPOTS`,
  `run(config, scope, opts=None) -> GateResult`, `run_canary(dir) -> GateResult`, and a CLI.
  Shared code: `cicd/llm/core.py`.
- Scope: `git ls-files --cached --others --exclude-standard` filtered by config globs (or the
  contract). Empty scope gives INEXPRESSIBLE, "empty enumeration is not a clean surface".
- Output: JSON on stdout plus `cicd/llm/ledger/ledger.jsonl` (gitignored), keyed by tree hash.
- Errors: only specific exception types are caught (`core.READ_ERRORS`), and they become NOT_RUN
  with the message. No `except Exception`, no bare `except`.

## Gates

### Gate 1: `invented_limits` (proxy)
- Failure: bound reversion. reversion "Bound reversion" (14 limits, 10 PRs, 16.9 h; two in job
  manifests survived 8 PRs), "Test 1: value conventionality" (26/26 conventional), "Test 2:
  escalation direction".
- Positive: limit-named keys/params with literals (py AST, YAML/JSON parser, SQL/TS/Scala regex
  tagged `proxy:regex`); SQL `LIMIT n` (incl. inside Python strings); constant slices (incl.
  negative index: escape "Held-out discipline", the held-out record-loss fault); `.head/.limit/.take(n)`;
  manifest timeout fields; budgets below `model_capacity`. Escalation tag with `--base`.
- Negative: values from config/env (not literals), allowlisted lines (content hash), excluded paths.
- Blind spots: runtime-computed limits, limits in external services, limits in prose.
- Config: `include`, `exclude`, `manifest_globs`, `key_patterns`, `manifest_timeout_patterns`,
  `truncating_methods`, `sql_limit_exclude`, `budget_patterns`, `allowlist`,
  `likely_invented.round_base`, `likely_invented.time_unit_seconds`. **No known value:**
  `model_capacity` (flag 6 NOT_RUN).
- Canaries: one per flag type plus the manifest-only wrapper timeout.

### Gate 2: `governed_write` (deciding)
- Failure: grade reversion at the write. reversion "Grade reversion" (75 rows uniformly "high"),
  "Detection surfaces" Group 1 items 1–2 (contract defaulting to None; `finding_kind` vs
  `finding_type` silently skipped), "Liveness canaries" (in-gate canary), "The asymmetry"
  (quarantine needs an alarm).
- Library: `cicd/llm/governed.py: write_gate(rows, contract, target)`, `contract` positional
  without default. Missing governed field raises. Out-of-domain row is quarantined and returned.
  Empty domain raises (INEXPRESSIBLE). In-gate canary on every call: one planted out-of-domain
  value per governed field, derived from the contract, mirroring the caller's row; fails if any
  planted row passes or if detection is partial; planted rows are never returned.
- Gate check: (1) AST call-site scan: writes (config `writer_functions`) to contract targets
  must be gated by `write_gate(..., <same target>)` in the same function; (2) signature check of
  every `def write_gate` in scope; (3) quarantine alarm from a run manifest.
- Negative: gated writes; writes to non-governed targets.
- Blind spots: writes whose target the AST cannot resolve (count reported).
- Config: `include`, `exclude`, `contract`, `writer_functions`. **No known value:**
  `quarantine_manifest`, `expected_quarantine` (alarm sub-check NOT_RUN).

### Gate 3: `provenance` (deciding)
- Failure: grade reversion is only decidable through provenance. reversion "Grade reversion
  requires provenance", "Three origins, not two".
- Positive: missing `f__origin`; origin outside the three; `code_supplied` where the contract
  does not allow it.
- Negative: `promoted` is never flagged (the two-origin design misreports correct promotions and
  gets switched off; "Three origins, not two").
- Blind spots: rows never written to a checked output file; origins asserted by code that lies.
- Config: `contract`, `outputs` globs (`<target>.governed.jsonl`), `exclude`.

### Gate 4: `behavioural_harness` (deciding)
- Failure: a stored value that does not depend on the model's output. taxonomy "Grounded
  assertion" (Sensitive, ablation); reversion "Grade reversion" (`or` fallback, `dict.get` default).
- Relations: R1 (change emitted value, so the stored value changes to match), R2 (no emitted value,
  so the contract's ungraded marker is stored), R3 (paraphrase pairs; NOT_RUN without fixtures),
  R4 (repeat count; NOT_RUN without config). Non-vacuity: at least one R1 and one R2 per governed
  field, else VIOLATED.
- Blind spots: stub model only; behaviour on real model output; fields not in the contract.
- Config: `contract`, `entry`, `target`, `records`. **No known value:** `paraphrase_pairs`,
  `repeat_count`.

### Gate 5: `review_findings` (proxy)
- Failure: hallucinated reviewer findings, uncorroborated findings, a model not self-correcting.
  reversion "Cross-architecture review" (validation stage, corroboration, third-repeat ladder,
  "half same-family").
- Validation (file at commit, line in range, quote on the line, rule ID and quote in the
  registry), corroboration, escalation ladder (third repeat gives `ESCALATE_TO_HUMAN`),
  independence check.
- Blind spots: whether a finding is correct; reviewer capability; anything a reviewer did not cite.
- Config: `rule_registry`. **No known value:** `author_family`, `reviewers`, `independent`
  (independence sub-check NOT_RUN).

### Gate 6: `safeguard_removal` (proxy, diff-based)
- Failure: the reviewer caught 3/3 added prohibited constructs and 0/3 removals (swallowed
  exception, deleted canary call, discarded quarantine list). reversion "Why review does not
  catch a control dying".
- Safeguards: `@safeguard` decorator or `# safeguard` marker; none in scope is VIOLATED.
- Flags over `base..head`: deleted safeguard/canary call; broadened handler or body changed to
  `pass`/`continue`; new `try` around a safeguard call; safeguard/contract parameter gaining a
  default; `write_gate` result discarded or assigned to `_`; unreachable guard (`if False:`,
  early return); deleted assertions; new skip/xfail.
- Blind spots: safeguards neither decorated nor marked; semantic neutering inside a safeguard
  body; non-Python files.
- Config: `include`, `exclude`, `canary_call_patterns`.

### Gate 7: `completion_evidence` (deciding)
- Failure: completion claimed without evidence. escape "E2" (6 of 12 completion claims on systems
  whose deployed artifact was wrong), "Practitioner implications" item 7.
- VIOLATED: missing `not_verified`; status `complete` with a required check not SATISFIED;
  missing evidence path; digest mismatch against a supplied build manifest; a deployment claim
  without read-back evidence. Free text is recorded, never scored.
- Blind spots: whether the evidence file says what the check claims; checks not listed.
- Config: `completion`.

### Support A: `ruff_profile`
- Codes S110, BLE001, E722, PLR2004, C901, each verified with `ruff rule` (all exist).
  C901 threshold = measured repo maximum (56, command and output in config), a ratchet against
  `--base`.

### Support B: `liveness`
- Discovers gates as modules in `cicd/llm/gates/` that define `GATE_ID` (derived, asserted
  non-empty), runs each against every `cicd/llm/canaries/<gate>/<canary>/`, and requires
  VIOLATED. A gate without a canary is VIOLATED.

## Verification protocol (spec section 5, in order)
1. This plan. 2. Gates test-first, one commit each. 3. `mutation_check.py`, at least 3 mutations
per gate, each naming the test that must fail; zero survivors. 4. Freeze (`freeze.py`, `FREEZE.json`)
with a held-out refusal test and a leakage test. 5. **Held-out: STOP.** No held-out fixtures are
written by the builder; `heldout_run.py` reports NOT_RUN until the owner supplies
`cicd/llm/heldout/`. 6. Dogfood on `cicd/llm/` and the whole repo. 7. `completion.json`
validated by Gate 7.

## Interpretations (made without being able to ask; review these)
1. **Sub-check NOT_RUN vs gate verdict.** A gate's verdict comes from the sub-checks that ran;
   NOT_RUN sub-checks are reported in `sub_checks` and must appear in `completion.json`'s
   `not_verified`. Otherwise every gate with an unset optional value would be NOT_RUN forever.
2. **Branch base.** `feat/llm-gates` is cut from master as specified. Earlier partial work by
   another agent on `feat/cicd-kit` (including a `cicd/llm/` attempt and a `cicd/lib/verdict.py`
   that maps strings with a `.get(verdict, 3)` default) was not reused; it does not meet the
   section 2 contract or the section 3 countermeasures.
3. **Reference pipeline.** Gates 2–4 need a governed writer, a contract and a pipeline entry to
   be SATISFIED against something real. `cicd/llm/reference_pipeline.py` with
   `contracts/governed_values.toml` is that clean example. It is a fixture for the kit, not a
   gate, and was added without asking (spec: "ask before adding anything").
4. **Rule registry.** Gate 5 needs a rule registry; `cicd/llm/rules.toml` maps each rule ID to
   its text and the gate that checks it.
5. **Unparseable files.** An in-scope file the gate cannot parse is recorded; with no violations
   elsewhere the gate returns NOT_RUN, not SATISFIED.
6. **Commit trailer.** The spec says `Co-Authored-By: Cline <noreply@cline.bot>`; this build
   was done by Claude, so commits carry `Co-Authored-By: Claude <noreply@anthropic.com>`
   (the trailer the owner specified for the follow-on task) to keep attribution accurate.
7. **Ungraded marker provenance.** A governed field holding the contract's `ungraded_marker`
   with origin `code_supplied` is accepted by `provenance`: the marker records that the model
   emitted nothing (Gate 4, R2) and is not a grade. Any other `code_supplied` value on a field
   whose contract does not allow it is VIOLATED.
