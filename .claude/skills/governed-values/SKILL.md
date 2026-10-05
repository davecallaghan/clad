---
name: governed-values
description: Use before writing or changing code that stores a model-produced judgement - a severity, grade, label, rank, confidence, score or finding kind - including code that writes rows to a governed table, maps model output to stored fields, sets defaults or fallbacks for such fields, or promotes rows.
---

# Governed values

## The failure it prevents

Grade reversion: a judgement the model did not emit is fabricated in code and stored as though it
had been derived. One job wrote `"severity": "high"` onto every surviving row (seventy-five red
flags, uniformly high); others supplied a kind through an `or` fallback and a default severity through
a dictionary `get`. No count can see this: every row is present (constraint-reversion, "Grade
reversion", "Why counting cannot see it"). The original write gate died three ways: its contract
defaulted to None, it skipped a field it could not find, and its quarantine had no alarm
("Detection surfaces", Group 1).

Checked by: `governed_write`, `provenance`, `behavioural_harness`.

## Procedure

1. Find the target in `cicd/llm/contracts/governed_values.toml`: its governed fields, each field's
   domain, the allowed origins and the `ungraded_marker`.
2. Always call `write_gate(rows, contract, target)` (from `cicd/llm/governed.py`) before writing to
   a governed target, in the same function as the write, passing the contract positionally.
3. Handle the result: bind it to a name, write `outcome.accepted` (never the rows you passed in)
   to the target and `outcome.quarantined` somewhere a human will see it. Never discard the result,
   bind it to `_`, or use `write_gate(...).accepted` inline. Never write a governed table with SQL
   (`INSERT INTO`, `MERGE INTO`): those rows bypass the gate.
4. Set an origin on every governed field, as `<field>__origin`: `model_emitted` (the model said it),
   `code_supplied` (code produced it) or `promoted` (a promotion step moved a model value forward).
   A promoted field also records what it was promoted from in `<field>__promoted_from`.
5. When the model emits no value, store the contract's explicit ungraded marker with origin
   `code_supplied`. Never `dict.get(key, default)`, never `value or "default"`, never a default grade.
6. A field-name mismatch (`finding_kind` against `finding_type`) is fixed by renaming the artifact so
   the names agree, never by aliasing in the checker ("Fix the artifact, not the check").
7. Run `cicd/llm/preflight.sh <files>` (governed_write call-site scan); CI runs the provenance and
   behavioural gates.

## Examples (executable: the guidance tests run them through the real gates)

- [governed_write__good__gated_write.py](examples/governed_write__good__gated_write.py) / [governed_write__bad__ungated_write.py](examples/governed_write__bad__ungated_write.py)
- [provenance__good__three_origins.jsonl](examples/provenance__good__three_origins.jsonl) / [provenance__bad__code_supplied_grade.jsonl](examples/provenance__bad__code_supplied_grade.jsonl)
- [behavioural_harness__good__carries_model_value.py](examples/behavioural_harness__good__carries_model_value.py) / [behavioural_harness__bad__default_grade.py](examples/behavioural_harness__bad__default_grade.py)

## Does not apply when

- The target or field is not in the contract (the gates derive their scope from the contract).
- The origin is `promoted` and `<field>__promoted_from` names the source: that is correct behaviour, not a violation. A two-origin design flags it,
  fires on correct code and gets switched off ("Three origins, not two").
- The stored value is the contract's ungraded marker with origin `code_supplied`.
