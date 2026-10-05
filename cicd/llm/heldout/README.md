# Held-out fixtures (supplied by the owner, never by the builder)

Spec section 5.5: the builder does not write held-out fixtures. Put them here as
`heldout/<gate_id>/<fixture_id>/`, in the same layout as `canaries/<gate_id>/<canary>/`
(including `canary.toml` and, for safeguard_removal, `base/` and `head/`). Then run:

    python cicd/llm/heldout_run.py

It refuses to run if any frozen file changed since `FREEZE.json`, runs every gate against its
fixtures without changing any gate, and reports caught/total per gate. A low catch rate is a
result to report, not a problem to fix in this phase.
