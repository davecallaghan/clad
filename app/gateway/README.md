# clad-gateway

A governed LLM gateway. Applications send chat-completion requests to it instead of to the provider. For every call it
renders the prompt from an approved template, runs EPG (the template text against the constraint hierarchy), calls the
provider, runs ROC (runtime output controls) over the reply, and appends signed audit records to a hash chain in
Postgres (with a chain anchor on disk). A blocked or failed call still leaves a record. Non-chat providers go through a passthrough route. `export` writes the evidence
bundle that `conformance` verifies. See `docs/specs/2026-10-16-clad-gateway-design.md`.

**Not in this version:** MDR (the monitoring worker). `GET /v1/health` is static
(`{"status":"ok"}`): it does not probe the store, keys, config or upstreams.

## What the adopter supplies (`CLAD_GATEWAY_CONFIG_DIR`)

```
constraints.yaml   hierarchy, agents, checkers, approval block
prompts/<id>.yaml  one governed template per file
models.yaml        provider, tiers, model registry with approval status
attestations.yaml  human attestations per template version
runtime.yaml       every number the gateway uses (lock key, retries, timeout, back-off, ROC retries,
                   retry_similarity, grounding_ignore_below, roc_background_workers)
roc.yaml           output constraints (deterministic or classifier, by template or project), thresholds (tau per
                   level, tau_max), router intents, retry variant line, safe responses; the approval digest covers it
classifiers/llama_guard.yaml   classifier profile: model (must be approved in models.yaml), version, upstream,
                   verdict_tokens, measured_fp, measured_fn, validated_at
classifiers/validation.jsonl   labelled items for validate-classifier (labels reviewed and attested by a human)
```

plus the keys directory (`components.ed25519` and `supervisor.ed25519` with `.pub.pem` files), a data directory
(`<data>/anchors`, `<data>/containment.json`), Postgres URLs, provider keys and client tokens.

## Commands

All configuration is by environment variable. There are no defaults for ports, URLs or numbers; a missing variable
exits 2 and names it. Secrets are never printed.

| Command | Does | Needs |
|---|---|---|
| `serve` | loads config, runs the startup check (errors exit 1; warnings print `CLAD_GATEWAY_WARN ...`), serves | `CLAD_GATEWAY_CONFIG_DIR`, `CLAD_GATEWAY_KEYS_DIR`, `CLAD_GATEWAY_DATA_DIR`, `CLAD_GATEWAY_WRITER_URL`, `CLAD_GATEWAY_UPSTREAMS` (`name=url,...`; `openai` is the chat upstream; an upstream is a passthrough target only if a template declares `passthrough: <name>`, startup fails if a template names an upstream that is not listed or declares `openai`, and a passthrough name must not map to the chat provider's URL), `CLAD_GATEWAY_KEY_<NAME>` per upstream (upper-cased), `CLAD_GATEWAY_TOKENS` (`token=prefix,...`), `CLAD_GATEWAY_ADMIN_TOKEN`, `CLAD_GATEWAY_PORT`. Optional: `CLAD_GATEWAY_HOST` (set `0.0.0.0` in a container; otherwise http4s's default bind address applies) |
| `migrate` | creates the governance schemas, roles and grants | `CLAD_GATEWAY_ADMIN_URL`, `CLAD_GATEWAY_WRITER_PASSWORD`, `CLAD_GATEWAY_READER_PASSWORD` |
| `keys` | generates the `components` and `supervisor` Ed25519 pairs if absent; prints a backup reminder | `CLAD_GATEWAY_KEYS_DIR` |
| `export --since T --until T --out DIR [--probe-since T --probe-until T]` | writes the evidence bundle (`DIR` must be new or empty) | `CLAD_GATEWAY_READER_URL`, `CLAD_GATEWAY_EGRESS_LOG`, `CLAD_GATEWAY_LISTENERS` (`port=host,...`), `CLAD_GATEWAY_CONFIG_DIR`, `CLAD_GATEWAY_KEYS_DIR`, `CLAD_GATEWAY_DATA_DIR`, `CLAD_GATEWAY_APP` |
| `approval-digest` | prints `constraints_digest: <digest>` and `approval: pending (<why>)` or `approval: approved` | `CLAD_GATEWAY_CONFIG_DIR` |
| `check` | the startup check alone; exit 1 on errors (including an unapproved classifier model, or a classifier constraint on a passthrough template) | `CLAD_GATEWAY_CONFIG_DIR` |
| `validate-classifier --project P` | scores `classifiers/validation.jsonl` with the classifier and writes `<data>/classifier-validation.json` (`profile`, `validatedAt`, `fp`, `fn`, `items`, `misclassified`); prints the `measured_fp`, `measured_fn` and `validated_at` lines to copy into the profile. Refuses to run unless `attestations.yaml` holds an attestation of `classifier.llama_guard.validation` whose `template_version` is the file's `sha256:` digest. Each scoring call is registered in the GIL (principal `classifier.validation`, bound to declared project `P`), so run it outside a measured window | `CLAD_GATEWAY_CONFIG_DIR`, `CLAD_GATEWAY_KEYS_DIR`, `CLAD_GATEWAY_DATA_DIR`, `CLAD_GATEWAY_WRITER_URL`, `CLAD_GATEWAY_UPSTREAMS`, `CLAD_GATEWAY_KEY_<NAME>` for the classifier's upstream |

Image: `bash tools/build-gateway-image.sh` builds `localhost/clad-gateway:<version>` and `:latest` with podman.
Run it as `podman run --rm -e ... localhost/clad-gateway:latest <command>`.

## HTTP API

Every route except `/v1/health` needs `Authorization: Bearer <token>`. Each client token maps to a principal prefix,
and `clad.principal` must start with it. Admin routes need the separate admin token.

- `POST /v1/chat/completions`: the OpenAI request plus `"clad": {"template_id", "vars", "principal", "session", "evidence", "model_tier"}`.
  No `system` message (the template supplies it); `model` is ignored. 200 delivered (body adds `clad.interaction_id`
  and `clad.outcome`, header `X-Clad-Interaction-Id`); 403 `governance_block`; 400 `governance_rejected` (nothing
  registered); 503 `unavailable` (a degraded record exists).
- `POST /v1/passthrough/{upstream}/{path}`: forwards body and path to the named upstream with `X-Clad-Template`,
  `X-Clad-Principal`, `X-Clad-Session`; no EPG record (provider-owned prompt). Allowed only to the upstream the
  request's template declares (`passthrough: <upstream>` in the template YAML); any other upstream, including
  `openai`, is a 400 and nothing is registered. An upstream 5xx or 429 is an upstream failure (degraded record,
  tier posture as for chat); other 4xx statuses are the provider's answer and are returned as they are.
- `POST /admin/contain {project, reason}` and `POST /admin/release {project}`: containment, a human action.
- `GET /v1/health`: static in this version.

## ROC

Every delivered reply is evaluated (spec 8.3 of personal_finance's enforcement design):

1. **Deterministic checks** in `roc.yaml` order: `numbers_grounded`, `extraction_grounded`, `schema_valid`,
   `tool_calls_valid`, `route_valid`, `contains_pii`, `guarantee_language`. For Critical the first failure ends the
   checks. A check with nothing to check against (no evidence, no schema) is recorded satisfied with nothing detected.
2. **Classifier** (Llama Guard on the profile's upstream, through egress with the call's `X-Clad-Interaction-Id`).
   The score comes only from the model's own logprob for the verdict token: P(unsafe) = e^lp for `unsafe`,
   1 - e^lp for `safe`; a category constraint reads that category's score (P(unsafe) times the category token's
   probability). No verdict logprob is a classifier failure, never a guessed score. An entry is satisfied iff
   score < tau. Critical classifies before delivery; Standard and Low classify afterwards on a background pool
   (`roc_background_workers`) and append a second ROC record (`pass`, or `flag` when a score reaches tau).
3. **Decision:** a deterministic failure blocks; a score at or over tau flags Standard and blocks Critical.
4. **Fallback for a block:** only `contains_pii` → redact and re-check (`fallback:redacted`); a grounding failure →
   up to `max_retries` retries in the same interaction with the variant line, stopping when a retry is more than
   `retry_similarity` similar to a failed output (`fallback:retry`); otherwise the governed safe response, with the
   evidence rows as a table for `rows_json` templates and no tool calls (`fallback:safe_response`).

Only what was evaluated is delivered: role, text content and tool calls (no reasoning or logprobs).

| Outcome | HTTP |
|---|---|
| `pass`, `flag`, `fallback:redacted`, `fallback:retry`, `fallback:safe_response` | 200, `clad.outcome` names it |
| ROC failure (classifier error or timeout, unreadable reply, record not stored), Critical | 403 `roc_unavailable`, degraded record `FailClosed` |
| ROC failure, Standard or Low | 200 with outcome `degraded` (role, text and tool calls only), degraded record with the tier's posture; 503 if the reply cannot be read |

**Passthrough:** a 2xx reply runs the template's deterministic constraints (`route_valid` for a router: the reply
parses and `answers.intent.choice` is one of `router_intents`). An invalid reply becomes `{}` with status 200 and
outcome `fallback:safe_response`; the client treats it as no answer and falls back to its default route. A 4xx is
returned as it is, without a ROC record.

## MDR (`clad-mdr`)

The monitoring worker ships in the same image (`--entrypoint clad-mdr`). It holds no signing key and no provider key,
connects as `clad_gov_reader` (SELECT on `governance.*`, INSERT on `mdr.*`) and never blocks a call: containment stays
a human action (`POST /admin/contain`), which an alert only recommends.

| Command | Does |
|---|---|
| `run` | a cycle every `mdr.interval_seconds`; a failed cycle is logged (`CLAD_MDR_CYCLE_FAILED`) and the next one runs |
| `once` | one cycle (tests, the measure script); prints `CLAD_MDR_CYCLE window=... alerts=... failed_detectors=... redelivered=...` |
| `check` | config loads, the store answers, the egress log is readable, the data dir is writable; exit 0 or 1 |

Environment: `CLAD_MDR_CONFIG_DIR` (the adopter's governance directory, read-only), `CLAD_MDR_GATEWAY_DATA_DIR` (its
`anchors/`, read-only), `CLAD_MDR_DATA_DIR` (writable: `incidents/<id>/` lockers), `CLAD_MDR_KEYS_DIR` (public keys
only), `CLAD_MDR_EGRESS_LOG`, `CLAD_MDR_LISTENERS` (`port=host,...`, as the gateway's), `CLAD_MDR_DB_URL` (the reader),
`CLAD_MDR_APP`. Optional: `CLAD_MDR_WEBHOOK_URL`; without it each alert is one `CLAD_MDR_ALERT {json}` line on stdout.
`runtime.yaml` needs the `mdr:` block (including `webhook_timeout_seconds`), `config_bounds:` and `severity_due_minutes:`.

**A cycle:**
1. The window runs from the last heartbeat minus `in_flight_grace_seconds` to now (first run: from the earliest infra
   call or GIL entry, so calls made before MDR existed are alerted).
2. One REPEATABLE READ snapshot, widened by `block_rate_baseline_hours`.
3. Every detector runs.
4. Alerts are stored, then one heartbeat per detector, then undelivered alerts are retried.
   - A detector that fails still gets its heartbeat, plus a P2 `detector_failed` alert.
   - A snapshot failure, or an alert that cannot be stored, writes no heartbeat. The cursor stays put, and
     R-MDR-LIVE shows the gap.

| Detector | Alerts |
|---|---|
| `ghosts` | P1: an infra call whose id has no GIL entry, or with no id (listed as `infra@<ts>`); a GIL entry past the grace with no record, or delivered (2xx) with no ROC record. P2 instead when a degraded record explains it |
| `chain_health` | P1: chain digests, links, signatures or the anchor fail; P3: degraded/GIL ratio over `degraded_ratio_alert` |
| `input` | P2 adversarial: `injection_alert_count` injection signals per principal in `injection_window_seconds` |
| `config` | P2: unapproved model, temperature or max_tokens over `config_bounds`, a template's new model, an unknown config digest |
| `block_rate` | P3: a property's block rate over `block_rate_multiple` times its baseline (no baseline, no alert) |
| `review_age` | P4: the constraint-set approval or an attestation older than `review_cadence_days` |
| `flags` | P3: each ROC `flag` record (the review queue) |
| `component_health` | clad's `ComponentHealthDetector` over degraded records: P2 FailClosed, P3 otherwise |

**Alerts** carry `due_at = raised_at + severity_due_minutes`.
- A dedupe key (unique in `mdr.alert`) means a restarted worker neither repeats nor loses an alert. A condition that
  holds across overlapping windows is keyed by its newest record.
- The row is written before the webhook POST. A failed POST is logged with the alert id
  (`CLAD_MDR_DELIVERY_FAILED`) and retried each cycle until an `mdr.alert_delivery` row exists.
- P1 and P2 open an incident: a bundle export from the alert's earliest interaction to now, in
  `<data>/incidents/<id>/`, made read-only.
  - A failed export is recorded on the incident (`export_error`), raises a P2 `locker_export_failed` alert and is
    logged (`CLAD_MDR_LOCKER_FAILED`).
  - A repeat of an alert whose incident row was never written opens it.

## Operating notes

- The `gateway-test` suite needs `CLAD_TEST_PG_ADMIN_URL` pointing at a Postgres server with **no live clad store**:
  the tests reset the cluster-wide `clad_gov_writer` and `clad_gov_reader` passwords.
- When the governance schemas are dropped and re-created, clear the anchors directory (`<data>/anchors`) too. A stale
  anchor with a higher chain length suppresses new anchors.
