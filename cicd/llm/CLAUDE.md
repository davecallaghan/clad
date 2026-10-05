# Editing the gates in cicd/llm/

These rules are for changes to the gates themselves. Each is checked by a test or a mechanism named
at the end of its line.

- Gates are frozen. Changing any file listed in `FREEZE.json` means a new freeze
  (`python -m cicd.llm.freeze` with `supersedes`, `reason` and `logic_unchanged_evidence`) and a
  fresh held-out run; the earlier freeze file and its held-out results stay in the tree and stay
  reported. Checked by: `tests/test_freeze.py`, `heldout_run.py` (refuses on moved digests).
- Never weaken a canary, test or mutation to make it pass, and never add `skip`, `skipif`, `xfail`,
  `importorskip` or `assume`. If a gate cannot catch its canary, that is a finding about the gate.
  Checked by: `tests/test_no_skips.py`, `assert_no_skipped_tests.py`, `gates/liveness.py`.
- Every behaviour claim needs a mutation in `mutation_check.py` that names the one test that must
  fail; docstrings are wishes. A surviving mutation means adding a test, never editing the mutation.
  Checked by: `mutation_check.py` (zero survivors in CI).
- When a gate disagrees with an artifact, fix the artifact, not the check (constraint-reversion,
  "Fix the artifact, not the check": aliasing a field name in the checker preserves a silent failure;
  renaming the artifact makes the next mismatch fail loudly). Change the gate only when a failing
  negative-case test shows the gate itself is wrong, and then re-freeze as above.
