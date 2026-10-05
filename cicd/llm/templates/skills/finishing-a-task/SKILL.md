---
name: finishing-a-task
description: Use before claiming a task is complete, done, finished, ready to merge or working - including before saying tests pass or a build is green as evidence of completion, and before ending a session that changed files.
---

# Finishing a task

## The failure it prevents

A completion claim without evidence. In the live-agent arm, 6 of 12 completion claims were made on
systems whose deployed artifact was wrong, because nothing the agent was shown reported the deployed
digest (constraint-escape, "E2, the live-agent arm"). A structured claim with uncovered scope as a
mandatory field makes the absence of evidence explicit and mechanically rejectable ("Practitioner
implications", item 7). In 1992 of 2136 cells every stage exited zero while the constraint was
violated or undecidable (item 9): exit status is not completion.

Checked by: `completion_evidence`. The Stop hook runs it when the session changed files in governed scope.

## Procedure

1. Run `cicd/llm/preflight.sh` on the files you changed and save its output to an evidence file.
2. Write `completion.json` at the repository root with this schema (from
   `cicd/llm/gates/completion_evidence.py`):
   - `status`: exactly `"complete"` (only if every required check is SATISFIED) or `"incomplete"`;
   - `commit`, `tree_hash`: `git rev-parse HEAD` and `git rev-parse HEAD^{tree}`;
   - `artifact_digests`: `{path: "sha256:<hex>"}` for any artifact you claim;
   - `checks`: `[{id, verdict, evidence_path, required?, claims_deployment?, readback_evidence?}]`,
     with `verdict` one of SATISFIED, VIOLATED, INEXPRESSIBLE, NOT_RUN, and an `evidence_path` that exists;
   - `not_verified`: **required** (an empty list is allowed, a missing key is not);
   - `build_manifest` (optional): a file of `{"digests": {...}}` to compare digests against.
3. In `not_verified`, list every NOT_RUN sub-check from the gate outputs (for example R3, R4,
   `budget_below_capacity`, `quarantine_alarm`) and every surface you did not check, such as live
   deployment, branch protection or production data.
4. A check that claims deployment needs `readback_evidence`: what is deployed, read back, not the
   deploy command's own report. It must be a different file from the deploy evidence and contain
   at least one digest from `artifact_digests`.
5. Run `python -m cicd.llm.gates.completion_evidence --completion completion.json` and paste its output.

## Examples (executable: the guidance tests run them through the real gate)

- [completion_evidence__good__scoped_completion.json](examples/completion_evidence__good__scoped_completion.json)
- [completion_evidence__bad__no_not_verified.json](examples/completion_evidence__bad__no_not_verified.json)
- [completion_evidence__bad__complete_with_not_run_check.json](examples/completion_evidence__bad__complete_with_not_run_check.json)

(`fixture_files` in the examples only creates the evidence files for the test; it is not part of the schema.)

## Does not apply when

- You report progress without claiming completion (set `status` to anything but `complete`, or write no claim).
- Free text (summaries, notes) is recorded but never scored: it neither helps nor hurts a claim.
