# clad-gateway

A governed LLM gateway. Applications send chat-completion requests to it instead of to the provider. For every call it
renders the prompt from an approved template, runs EPG (the template text against the constraint hierarchy), calls the
provider, and appends a signed audit record to a hash chain in Postgres (with a chain anchor on disk). A blocked or
failed call still leaves a record. Non-chat providers go through a passthrough route. `export` writes the evidence
bundle that `conformance` verifies. See `docs/specs/2026-10-16-clad-gateway-design.md`.

**Not in this version:** ROC (runtime output controls) and MDR (the monitoring worker). `GET /v1/health` is static
(`{"status":"ok"}`): it does not probe the store, keys, config or upstreams.

## What the adopter supplies (`CLAD_GATEWAY_CONFIG_DIR`)

```
constraints.yaml   hierarchy, agents, checkers, approval block
prompts/<id>.yaml  one governed template per file
models.yaml        provider, tiers, model registry with approval status
attestations.yaml  human attestations per template version
runtime.yaml       every number the gateway uses (lock key, retries, timeout, back-off)
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
| `check` | the startup check alone; exit 1 on errors | `CLAD_GATEWAY_CONFIG_DIR` |

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

## Operating notes

- The `gateway-test` suite needs `CLAD_TEST_PG_ADMIN_URL` pointing at a Postgres server with **no live clad store**:
  the tests reset the cluster-wide `clad_gov_writer` and `clad_gov_reader` passwords.
- When the governance schemas are dropped and re-created, clear the anchors directory (`<data>/anchors`) too. A stale
  anchor with a higher chain length suppresses new anchors.
