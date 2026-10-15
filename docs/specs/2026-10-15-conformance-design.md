# CLAD Conformance Module: Design

Status: approved design, ready for an implementation plan
Date: 2026-10-15
Branch: `feature/conformance` (worktree `/Users/david/workspace/clad-conformance`, cut from `origin/master` at b8cd1bb)
Companion spec: `personal_finance/docs/superpowers/specs/2026-10-15-clad-enforcement-design.md` (the first app measured)

## 1. Purpose

Measure how closely a deployed AI application conforms to CLAD, using only evidence that clad's own code can verify.
An application exports an **evidence bundle**. The conformance module reads it, re-verifies it with clad's evaluators,
audit chain verifier and ghost detector, optionally runs live **probes** against the application, and writes a
**scorecard**.

The first use is a measure → enforce → remeasure loop on `personal_finance`: a baseline scorecard of the app as it is,
then one after each enforcement step, then a final one.

### Principles

- **Evidence only.** A requirement is SATISFIED only when the module verified it. The application's claims about itself
  are never scored. There is no "declared" column.
- **Independent verification.** The module re-computes digests, re-verifies signatures with the public key only,
  and re-runs prompt evaluation. It never calls code supplied by the application.
- **Honest verdicts.** Every requirement gets exactly one of SATISFIED, VIOLATED, NOT_RUN (a required input is
  missing) or INEXPRESSIBLE (the property cannot be decided from this kind of evidence). NOT_RUN and INEXPRESSIBLE
  are not passes; the headline score counts SATISFIED only.

## 2. Scope

In scope:

1. A new sbt module `conformance` (with `conformance-test`) and a CLI entry point.
2. Additive changes to existing modules so that an external application's evidence can be verified:
   `Ed25519KeyManagement`, optional `component` / `surface` / `outcome` fields on `AuditRecord`, a classifier
   evaluation detail, a JSON codec for degraded records, and optional authorship fields in `GovernanceConfig`.
3. The bundle format (section 4), the requirement catalogue (section 6), live probes (section 7) and the scorecard
   format (section 8).

Out of scope: a UI; scoring requirements from documents or questionnaires; break-glass, Gold Standard Prompt Set impact
analysis and semantic-intent (C_s) decomposition checks (they appear in the scorecard as NOT_RUN with the reason
"not implemented by the conformance module").

## 3. Changes to existing modules

All changes are additive. Existing tests, the Lean differential test and existing signatures must keep passing.
The rule that has kept old records valid so far applies to every new digest field: **a new field enters the digest only
when present, as a prefix**, so a record without it hashes exactly as before.

### 3.1 `audit`: Ed25519 key management

`Ed25519KeyManagement(privateKey: Option[java.security.PrivateKey], publicKey: java.security.PublicKey, keyId: String)`
implements `KeyManagementService` with JDK 17 `Signature.getInstance("Ed25519")`; algorithm string `"Ed25519"`.
`sign` returns `Left(KeyUnavailable(keyId))` when there is no private key, so a verifier built from a public key cannot
sign. A companion `Ed25519KeyManagement.fromPublicPem(pem: String, keyId: String)` reads an X.509
`-----BEGIN PUBLIC KEY-----` PEM; `fromPrivatePem` reads PKCS#8, for tests.

Signature input is unchanged: `record.digest.getBytes("UTF-8")`.

### 3.2 `runtime`: optional record fields

`AuditRecord` gains three trailing optional fields, after `interactionId`:

```scala
component: Option[String] = None   // "epg" | "roc" | "mdr" | "gateway"
surface: Option[Surface] = None    // clad.core.Surface
outcome: Option[String] = None     // e.g. "pass", "flag", "block", "fallback:safe_response", "fallback:redacted"
```

`digest` prefixes them only when present, in this fixed order, before the existing `interaction:` prefix:

```
component:<c>|surface:<Surface.toString>|outcome:<o>|interaction:<id>|<existing canonical string>
```

Each part is omitted entirely when its field is `None`. `AuditRecordCodec` reads them with `json.obj.get(...)` so old
lines decode to `None`, and writes `null` when absent. A golden test pins the digest of a record with all fields
`None` to the value it had before this change.

### 3.3 `evaluation` / `audit`: classifier detail

`EvalDetail` gains
`ClassifierDetail(classifierId: String, classifierVersion: String, score: Double, threshold: Double, thresholdVersion: String)`.
`EvaluabilityClass` gains `Classifier`. The codec writes it as `{"type":"Classifier", ...}`. The digest is unchanged in
shape (it already includes `evaluabilityClass.toString`).

### 3.4 `integrity` / `audit`: degraded record codec

`DegradedRecordCodec.encode/decode` for `SignedDegradedRecord`, JSON shape:

```json
{"record":{"interactionId":"…","timestamp":"…","component":"epg","status":"Degraded",
           "failureReason":"…","postureApplied":"FailClosed","actionTaken":"Blocked"},
 "signature":{"value":"<base64>","keyId":"…","algorithm":"Ed25519"},
 "signedBy":"supervisor","onBehalfOf":"epg"}
```

The signed bytes are the existing `SignedDegradedRecord.sign` data string; do not change it.

### 3.5 `config`: authorship and levels

All new fields have defaults, so existing JSON still loads:

- `ConstraintConfig`: `author: String = ""`, `approver: String = ""`.
- `AgentConfig`: `kind: String = "human"` (`"human"` or `"agent"`).
- `GovernanceConfig`: `centralApprovers: Seq[String] = Seq.empty`, `criticalDomains: Seq[String] = Seq.empty`,
  `project: String = ""` (which project path this config describes; see 4.2).
- `ThresholdConfig`: `level: String = "enterprise"`.

`ConfigLoader.buildEngine` behaviour does not change. Note for producers: `regex` patterns and `keyword` keywords are
split on `,`, so a pattern or keyword containing a comma cannot be expressed. Producers must not emit one; the
conformance module reports such a config as VIOLATED on R-CFG-PARSE rather than guessing.

## 4. Evidence bundle format (version 1)

A bundle is a directory. All JSONL files are UTF-8, one JSON object per line. Absent files mean "no evidence", which
yields NOT_RUN for the requirements that need them; an empty file means "evidence of zero items".

```
bundle/
  bundle.json
  configs/<configDigest>.json      # one GovernanceConfig per project path
  artifacts/<artifactDigest>.json  # governed prompt artifacts (no user data)
  gil.jsonl
  infra.jsonl
  audit.jsonl
  degraded.jsonl
  anchors.jsonl
  mdr_alerts.jsonl
  mdr_heartbeats.jsonl
  keys/<keyId>.pub.pem
```

### 4.1 `bundle.json`

```json
{
  "bundleVersion": 1,
  "app": "personal_finance",
  "window": {"start": "2026-10-15T10:00:00Z", "end": "2026-10-15T11:00:00Z"},
  "probeWindow": {"start": "…", "end": "…"},
  "projects": {
    "agent":        {"tier": "Critical", "expectedComponents": ["epg", "roc"]},
    "assistant":    {"tier": "Standard", "expectedComponents": ["epg", "roc"]},
    "router":       {"tier": "Standard", "expectedComponents": ["roc"]},
    "planner-chat": {"tier": "Critical", "expectedComponents": ["epg", "roc"]},
    "doc-extract":  {"tier": "Critical", "expectedComponents": ["epg", "roc"]}
  },
  "keys": {"components": "<keyId>", "supervisor": "<keyId>"},
  "exportedAt": "…",
  "exporter": "pf-stack governance export 1.0"
}
```

`probeWindow` is optional; it is the interval in which probes injected deliberate violations (section 7). Records in
the probe window are scored by the probe requirements only and are excluded from the workload ghost rate.

### 4.2 `configs/<configDigest>.json`

A `GovernanceConfig` (with section 3.5 fields) for one project path: enterprise constraints, that project's department
constraints, and that project's constraints. `configDigest` is `"sha256:" + hex(sha256(file bytes))`, and every
`AuditRecord.configDigest` must name one of these files.

### 4.3 `artifacts/<artifactDigest>.json`

```json
{"content": "<rendered system prompt>", "metadata": {"template_id": "agent.answer", "template_version": "3",
 "provider": "deepinfra", "model": "…", "model_registry_status": "approved", "project": "agent"}}
```

`artifactDigest` is `"sha256:" + hex(sha256(UTF-8 content))`. Only EPG artifacts are included; ROC artifacts (model
outputs) are never exported.

### 4.4 Logs

- `gil.jsonl`: `{"interactionId", "registeredAt", "metadata": {"project", "principal", "tier", "session"?, …}}`.
- `infra.jsonl`: one line per upstream model call seen by the infrastructure (independent of the application):
  `{"ts", "host", "method", "path", "status", "interactionId": "<header value or null>"}`.
- `audit.jsonl`: `SignedAuditRecord` lines in `AuditRecordCodec` format, in chain order.
- `degraded.jsonl`: `DegradedRecordCodec` lines.
- `anchors.jsonl`: `{"ts", "chainLength", "headDigest"}`, written outside the audit store.
- `mdr_alerts.jsonl`: `{"alertId", "raisedAt", "severity": "P1".."P4", "taxonomy": "governance|compliance|adversarial",
  "detector", "interactionIds": [...], "summary"}`.
- `mdr_heartbeats.jsonl`: `{"ts", "detector"}`.

## 5. Module structure

```
conformance/src/main/scala/clad/conformance/
  Main.scala               # CLI: run --bundle <dir> --out <dir> [--probe-config <file>]
  Bundle.scala             # BundleReader: parses section 4; Either[BundleError, Bundle]
  Verdict.scala            # enum Verdict { Satisfied, Violated, NotRun, Inexpressible }
  Requirement.scala        # RequirementResult(id, title, area, verdict, evidence: Vector[String], reason: String)
  checks/
    ChainChecks.scala      # R-AI2, R-AI3, R-AI4, R-AI5, R-ANCHOR
    LinkageChecks.scala    # R-EA1, R-EA2, R-EA3 (uses GhostDetector + InfraReconciler)
    EpgChecks.scala        # R-EPG-*
    RocChecks.scala        # R-ROC-*
    ConfigChecks.scala     # R-CFG-*, R-EA4, R-AX5
    MdrChecks.scala        # R-MDR-*
    PrivacyChecks.scala    # R-AI6
  InfraReconciler.scala
  probes/
    WormProbe.scala        # R-AI1
    GhostProbe.scala       # R-PROBE-GHOST
  Scorecard.scala          # JSON + Markdown writers
```

Dependencies: `core`, `evaluation`, `runtime`, `audit`, `output`, `integrity`, `config`, plus the PostgreSQL JDBC
driver (for the WORM probe) and the JDK HTTP client. Each check is a pure function
`Bundle => Vector[RequirementResult]` except the probes.

## 6. Requirement catalogue

The evidence column lists what each verdict rests on. "Window" means the workload window unless stated.

| Id | Requirement (source) | SATISFIED when | NOT_RUN when |
|---|---|---|---|
| R-AI2 | Hash-chain integrity (AI2) | Records in `audit.jsonl` form one chain: the first has `previousDigest = None`, each later one has `previousDigest = Some(prev.record.digest)` | `audit.jsonl` absent |
| R-AI3 | Signed records (AI3) | Every record's signature verifies against the bundle's component public key | no key or no records |
| R-AI4 | Independent verification (AI4) | R-AI3 passed using **only** a public key, and every `recordedDigest` equals the recomputed `record.digest` | as R-AI3 |
| R-AI5 | Truncation detection (AI5) | Every audited interaction in the window has a record from each `expectedComponents` entry for its project (project read from GIL metadata), or a degraded record for the missing component | no GIL or no records |
| R-ANCHOR | Chain tail anchored | The latest anchor's `headDigest` is the digest of the record at position `chainLength` | no anchors |
| R-AI1 | WORM storage (AI1) | WORM probe refused UPDATE, DELETE and TRUNCATE (section 7) | probe not configured |
| R-AI6 | PII minimization (AI6) | No field in `audit.jsonl`, `gil.jsonl`, `infra.jsonl`, `degraded.jsonl` or `mdr_alerts.jsonl` matches the PII patterns (SSN, EIN/TIN, email, 10-digit phone, 12–19 digit account number), and audit records carry digests, never content | — |
| R-EA1 | Single chokepoint (EA1) | Every `infra.jsonl` entry in the window carries an `interactionId` that exists in the GIL | `infra.jsonl` absent |
| R-EA2 | Identity binding (EA2) | Every GIL entry has non-empty `project` and `principal`, and `project` is a key of `bundle.json` `projects` | no GIL |
| R-EA3 | Bypass detection (EA3) | Workload-window ghost rate is 0: `GhostDetector.detect` over the GIL and audited ids finds no ghosts, and InfraReconciler finds no infra entry without a GIL entry. Reports ghost count, rate and unlinkable count | no GIL or no infra |
| R-EA4 | Constraint authorship (EA4) | Every constraint in every config has non-empty `author` and `approver`, `author != approver`, the approver is an agent of kind `human`, the author passes `ConstraintAuthorizer.authorize` for its domain and level, and for domains in `criticalDomains` the approver is in `centralApprovers` | no configs |
| R-AX5 | Version identity (Axiom 5) | Every record's `configDigest` names a file in `configs/` whose bytes hash to it, and every EPG record's `artifactDigest` names a file in `artifacts/` whose content hashes to it | no records |
| R-CFG-PARSE | Configs load | Every config loads with `ConfigLoader.loadFromString` and `buildEngine` succeeds | no configs |
| R-CFG-MONO | Hierarchy monotonic, no contradictions | Every config builds a `ConstraintHierarchy` without `Contradiction`, and every enterprise constraint present in one project's config is present in all of them | no configs |
| R-CFG-TENSION | Tensions surfaced | Always SATISFIED with the tension list as evidence when configs load (advisory) | no configs |
| R-EPG-REEVAL | EPG re-evaluation | For every EPG record, `PromptEvaluator.evaluate` on its artifact with its config at `Level.Project` yields, per constraint, the same `satisfied` value as the record's entries | no EPG records |
| R-EPG-BLOCK | Violations blocked | Every EPG record with an unsatisfied entry has `outcome = "block"`, and no ROC record exists for that interaction | no EPG records |
| R-ROC-PRESENT | Outputs evaluated | Every interaction with an infra `status` of 2xx for a model call has a ROC record or a degraded ROC record | no infra or no records |
| R-ROC-DECISION | Decision follows evidence | For every ROC record: if a Critical-tier deterministic entry is unsatisfied the outcome is `block` or a `fallback:*`; if all entries are satisfied the outcome is `pass`; classifier entries with score ≥ threshold are never `pass` | no ROC records |
| R-ROC-THRESH | Threshold monotonicity | For each property, project/department thresholds are ≤ the enterprise threshold, and Critical thresholds are ≤ the config's τ_max (`ThresholdConfig` with property `tau_max`) | no thresholds |
| R-POSTURE | Failure posture | Every degraded record's `postureApplied` matches the project's tier (Critical → FailClosed/Blocked, Standard → FailOpenFlagged/ProceededFlagged), and its supervisor signature verifies | no degraded records (NOT_RUN, not VIOLATED) |
| R-MDR-ALERT | Ghosts alerted | Every ghost and every infra entry without a GIL entry (any window) has an MDR alert listing its interaction id (or, for null ids, an alert raised within the configured detection delay) | no `mdr_alerts.jsonl` |
| R-MDR-LIVE | Monitoring continuity | Heartbeats cover the window with no gap longer than the configured interval × 2; gaps are listed as ungoverned monitoring windows | no heartbeats |
| R-PROBE-GHOST | Bypass is detected | The ghost probe's injected call appears in `infra.jsonl`, is reported by R-EA3's machinery in the probe window, and has an MDR alert | probe not configured |
| R-BREAKGLASS, R-GSPS, R-CS | Break-glass, Gold Standard Prompt Set, semantic intent | — | always NOT_RUN: not implemented |

Thresholds used by checks (detection delay, heartbeat interval) come from the probe config file, never from literals in
code (clad's LLM-LIMITS rule).

## 7. Probes

Probes run only when `--probe-config <file>` is given:

```json
{
  "worm": {"jdbcUrl": "jdbc:postgresql://localhost:5433/pf", "user": "pf_gov_writer", "passwordFile": "…",
           "tables": ["governance.audit_record", "governance.gil", "governance.degraded_record"]},
  "ghost": {"url": "http://localhost:<egress port>/v1/openai/chat/completions"},
  "mdr": {"heartbeatIntervalSeconds": 60, "detectionDelaySeconds": 300}
}
```

- **WORM probe.** For each table, open a transaction and, each in its own savepoint, attempt an `UPDATE`, a `DELETE`
  and a `TRUNCATE` that would affect one existing row (`WHERE ctid = (SELECT min(ctid) FROM <table>)`, which avoids a `LIMIT` the LLM-LIMITS gate would flag; a statement
  that matches no row would prove nothing). Then roll back; the probe never commits. SATISFIED only if all three are
  refused with a permission or trigger error on every table. An empty table gives NOT_RUN for that table.
- **Ghost probe.** Sends one minimal request directly to the egress proxy, without going through the gateway, records
  the time, and writes `probe_ghost.json` (time, response status) into the output directory. Scoring happens after the
  application re-exports a bundle that covers the probe window; the CLI therefore has two phases:
  `probe --probe-config … --out <dir>` then `run --bundle … --probe-results <dir> --out …`.

## 8. Scorecard

`scorecard.json`:

```json
{"app": "…", "bundle": "<path>", "window": {…}, "generatedAt": "…", "cladVersion": "<git sha>",
 "summary": {"satisfied": 0, "violated": 0, "notRun": 0, "inexpressible": 0, "total": 0,
             "ghostRate": 0.0, "ghostCount": 0, "unlinkableRecords": 0},
 "requirements": [{"id": "R-AI2", "title": "…", "area": "audit", "verdict": "SATISFIED",
                   "evidence": ["…"], "reason": "…"}]}
```

`scorecard.md` renders the same: a summary table, then one row per requirement grouped by area (audit, linkage, EPG,
ROC, config, MDR, probes, not implemented), with the reason for every non-SATISFIED verdict.

Exit codes follow clad's convention: 0 when every requirement is SATISFIED, 1 when any is VIOLATED, 2 when none is
VIOLATED but any is NOT_RUN or INEXPRESSIBLE.

## 9. Testing

- Unit tests per check with hand-built bundles: one passing and at least one failing case per requirement.
- Golden digest test: a record with all optional fields `None` keeps its pre-change digest; one with all fields set
  matches a pinned value. The pinned values are published in `conformance-test/src/test/resources/golden/` so other
  implementations (the Python one in personal_finance) can test against the same vectors.
- Tamper tests: flip one byte in a record, delete a middle record, delete the tail (caught by R-ANCHOR), swap a
  signature, change a config file (caught by R-AX5).
- Ed25519: sign with private, verify with public-only instance; public-only instance cannot sign.
- End-to-end: a fixture bundle in `conformance-test/src/test/resources/bundles/minimal/` produces a known scorecard.
- `sbt test` for the whole build stays green, and the differential test (`tools/run-differential-test.sh`) still passes.

## 10. Repository rules that bind the implementation

The implementer follows clad's `CLAUDE.md`: no literal limits or timeouts (read them from the probe config),
no swallowed exceptions, and the definition of done there (`cicd/llm/preflight.sh`, `completion.json`,
`completion_evidence`). The checkout at `/Users/david/workspace/clad` is on another branch with uncommitted work;
all work for this spec happens in the `clad-conformance` worktree only.
