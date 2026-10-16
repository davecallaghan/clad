# clad-conformance

Measures how closely a deployed AI application conforms to CLAD, using only evidence clad's own
code can verify. The application exports an **evidence bundle**; this module re-verifies it
(re-computes digests, checks Ed25519 signatures with the public key only, re-runs prompt
evaluation, detects ghosts), optionally runs live **probes**, and writes a **scorecard**.

The application's claims about itself are never scored. Design:
[`docs/specs/2026-10-15-conformance-design.md`](../../docs/specs/2026-10-15-conformance-design.md).

## Evidence bundle

A directory: `bundle.json`, `configs/<digest>.json`, `artifacts/<digest>.json`, `gil.jsonl`,
`infra.jsonl`, `audit.jsonl`, `degraded.jsonl`, `anchors.jsonl`, `mdr_alerts.jsonl`,
`mdr_heartbeats.jsonl` and `keys/<keyId>.pub.pem`. The format is spec section 4. An absent file
means "no evidence" (its requirements are NOT_RUN); an empty file means "zero items".

## Commands

```bash
cd app
# Score a bundle
sbt "conformance/run run --bundle /path/to/bundle --out /path/to/out --probe-config probe.json"

# Two-phase ghost probe: inject a bypassing call, have the app re-export, then score with the results
sbt "conformance/run probe --probe-config probe.json --out /path/to/probe-results"
sbt "conformance/run run --bundle /path/to/new-bundle --out /path/to/out --probe-config probe.json --probe-results /path/to/probe-results"
```

`probe.json` is spec section 7 plus `ghost.matchToleranceSeconds`. Every threshold the checks use
(heartbeat interval, detection delay, match tolerance) comes from this file, never from code.

The output directory gets `scorecard.json` and `scorecard.md` (spec section 8).

## Verdicts and exit codes

Each requirement gets exactly one of SATISFIED, VIOLATED, NOT_RUN (a required input is missing)
or INEXPRESSIBLE (the property cannot be decided from this kind of evidence). **NOT_RUN and
INEXPRESSIBLE are not passes**; the headline counts SATISFIED only.

Exit code 0 when every requirement is SATISFIED, 1 when any is VIOLATED, 2 when none is VIOLATED
but any is NOT_RUN or INEXPRESSIBLE.

## Golden vectors

`conformance-test/src/test/resources/golden/digests.json` pins the audit-record digest for a
legacy record, a record with only an interaction id, and a record with every optional field, each
with its canonical string. Any other implementation that writes audit records for this module to
verify (the Python one in personal_finance among them) must test against these vectors.
