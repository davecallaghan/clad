# Development set (formerly the first held-out set)

These 36 fixtures were the held-out set for FREEZE-2026-10-04-b (21/36 caught,
evidence/llm-gates/heldout.txt). The v2 gates were changed to close the 15 they missed, so they
shaped v2 and are no longer held-out for it. They stay as a regression set:
`python -m cicd.llm.heldout_run --dir cicd/llm/devset`.
