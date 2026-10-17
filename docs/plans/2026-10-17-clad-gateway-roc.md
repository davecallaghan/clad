# clad-gateway ROC Implementation Plan (runtime output controls)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ROC to clad-gateway. Every delivered model output passes deterministic checks and a Llama Guard classifier, gets a signed ROC record, and is passed, flagged, blocked, redacted, retried or replaced by a governed safe response. personal_finance then supplies its output constraints and is remeasured.

**Architecture:** New package `clad.gateway.roc`, built on clad's `output` module: `OutputArtifact`, `OutputChecker`, `ClassificationResult`, `ThresholdPolicy`. It covers ROC configuration, pure deterministic checkers, a Llama Guard client on DeepInfra, an evaluator, fallbacks, and the wiring into `Gateway.complete` and `Gateway.passthrough`. ROC entries become clad `AuditEntry`s: `MechanicalDetail` for deterministic checks, `ClassifierDetail` for classifiers. Thresholds and output constraints go into each project's exported `GovernanceConfig`, so conformance's R-ROC-THRESH and R-ROC-DECISION verify them.

**Tech Stack:** as the gateway plan (Scala 3.3.8, sbt, http4s, upickle, SnakeYAML, PostgreSQL JDBC, scalatest), plus `com.networknt:json-schema-validator:1.5.1` for `schema_valid`. personal_finance: YAML config only, plus one Python test.

**Spec:** personal_finance `docs/superpowers/specs/2026-10-15-clad-enforcement-design.md` section 8 (ROC: constraints, pipeline, classifier profile, validation) is the authority for behaviour. clad `docs/specs/2026-10-16-clad-gateway-design.md` sections 7 (step 7) and 14.1 cover where it runs. The classifier decision is personal_finance `docs/governance/decisions/2026-10-17-roc-classifier-spike.md`: chosen-token logprobs, P(unsafe) = e^lp or 1 − e^lp.

## Global Constraints

- clad work: worktree `/Users/david/workspace/clad-conformance`, branch `feature/roc`. personal_finance work: worktree `/Users/david/workspace/personal_finance-clad-enforcement`, branch `feature/roc`; the live pod runs from that worktree. Never touch `/Users/david/workspace/clad` or `/Users/david/workspace/personal_finance`. Do not merge, push or open a PR.
- All the gateway plan's Global Constraints still apply: no literal limits, timeouts, retries, thresholds or tolerances in main code (they come from the adopter's `runtime.yaml` and `roc.yaml`); no `.head`, `.take(n)` or SQL `LIMIT n` literals in main code; no swallowed exceptions; never-throw `Either` contracts; preflight before each commit; `CLAD_TEST_PG_ADMIN_URL` for DB tests; the record format is clad's.
- A classifier score is only ever computed from the model's own probabilities. If a response carries no logprob for the verdict token, that is a classifier failure (degraded record), never a guessed score.
- A score below τ is recorded as below threshold (`ClassifierDetail` with its score), never as an unscored pass.
- Labelled validation data is drafted by the implementer and reviewed by David. Record his review as an attestation entry in `attestations.yaml` with `template_id: classifier.llama_guard.validation`. An LLM never attests.
- Commit trailer: the implementer's `Co-Authored-By:` line.

## Review Focus

- **The classifier's own call is a model call.** It must go through egress with the same `X-Clad-Interaction-Id`, or conformance counts it as a ghost (R-EA1).
- **A Critical classifier timeout or error must block.** It writes a supervisor-signed degraded record (component `roc`) and returns 403, never the unchecked output (spec 8.3).
- **Numbers grounded across formats.** `6.66 %` matches the row value `0.0666328857`, and `$1,234.50` matches `1234.5`. Integers below `grounding_ignore_below` are ignored. A number that appears only in the question, not the rows, is not grounded.
- **Retry loops.** At most `max_retries` retries. A retry whose output is more than `retry_similarity` similar to a failed output is skipped. A retry is a second upstream call within the same interaction, not a new interaction.
- **The safe response leaks nothing.** The `agent.answer` safe response shows the evidence rows as a table and none of the blocked text; tool calls are removed.

---

### Task 1: ROC configuration and the exported thresholds

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/roc/RocConfig.scala`
- Modify: `app/gateway/src/main/scala/clad/gateway/config/{Runtime.scala, GatewayConfig.scala, Governance.scala}`
- Create fixtures: `app/gateway-test/src/test/resources/governance/{roc.yaml, classifiers/llama_guard.yaml}`
- Test: `app/gateway-test/src/test/scala/clad/gateway/roc/RocConfigSpec.scala`

**Interfaces:**
- Produces:

```scala
final case class OutputConstraintDef(property: String, ob: Boolean, kind: String /* deterministic|classifier */,
                                     templates: Set[String], projects: Set[String] /* empty = all */, category: Option[String])
final case class ThresholdDef(property: String, level: String /* enterprise|department|project */, scope: String /* "" | department | project */,
                              tau: Double, version: String, owner: String)
final case class ClassifierProfile(id: String, model: String, version: String, upstream: String,
                                   measuredFp: Option[Double], measuredFn: Option[Double], validatedAt: Option[String])
final case class RocConfig(constraints: Vector[OutputConstraintDef], thresholds: Vector[ThresholdDef], tauMax: Double,
                           routerIntents: Set[String], safeResponses: Map[String, String] /* template id or "default" -> text */,
                           retryVariant: String, classifier: ClassifierProfile):
  def forTemplate(t: clad.gateway.config.Template): Vector[OutputConstraintDef]
  def tau(property: String, project: String, governance: clad.gateway.config.Governance): Double  // the lowest applicable level wins
object RocConfig { def load(dir: java.nio.file.Path, gov: clad.gateway.config.Governance): Either[String, RocConfig] }
```

- `Runtime` gains the required keys `latency_budget_ms`, `max_retries`, `retry_similarity` and `grounding_ignore_below`; there are no defaults. personal_finance's `runtime.yaml` already has all four.
- `GatewayConfig` gains `roc: RocConfig`, loaded from `roc.yaml` and `classifiers/llama_guard.yaml`.
- `Governance.governanceConfig(project, roc)` fills two fields:
  - `outputConstraints`: `OutputConstraintConfig(property, constraintType, riskTier = the project tier, config)` for each constraint applying to any template of the project;
  - `thresholds`: `ThresholdConfig(property, flagThreshold, riskTier, owner, version, level)` for each applicable threshold, plus `ThresholdConfig("tau_max", tauMax, "Critical", owner, version, "enterprise")`.
  
  Every caller (EPG, the startup check, export) now passes `cfg.roc`, so one digest covers both.
- **The approval covers ROC too.** `Governance.approvalDigest` becomes the sha256 of the canonical JSON `{"constraints": <constraints.yaml without approval>, "roc": <roc.yaml>}`. The thresholds and output constraints are governed constraints, so the human approval of the constraint set must cover them. Consequences:
  - an approval recorded before `roc.yaml` exists no longer matches, and the set goes back to pending;
  - `approval-digest` prints the new digest;
  - test that changing a τ in `roc.yaml` changes the digest and puts the approval back to pending.

`roc.yaml` format (personal_finance's version is written in Task 9):

```yaml
version: "1"
tau_max: 0.5
owner: david
constraints:
  - {property: numbers_grounded, type: obligation, kind: deterministic, templates: [agent.answer]}
  - {property: extraction_grounded, type: obligation, kind: deterministic, templates: [doc.extract_1099]}
  - {property: schema_valid, type: obligation, kind: deterministic, templates: [agent.plan_metrics, doc.extract_1099]}
  - {property: tool_calls_valid, type: obligation, kind: deterministic, templates: [planner.chat, planner.interview]}
  - {property: contains_pii, type: prohibition, kind: deterministic}
  - {property: guarantee_language, type: prohibition, kind: deterministic}
  - {property: specialized_advice, type: prohibition, kind: classifier, category: S6, projects: [agent, assistant, planner-chat]}
  - {property: unsafe_content, type: prohibition, kind: classifier, projects: [agent, assistant, planner-chat, doc-extract]}
  - {property: route_valid, type: obligation, kind: deterministic, templates: [router.jev]}
thresholds:
  - {property: specialized_advice, level: enterprise, tau: 0.5, version: "1"}
  - {property: unsafe_content, level: enterprise, tau: 0.5, version: "1"}
router_intents: [metric_question, data_question, plan_edit, general]
retry_variant: "Use only numbers that appear in the data block, exactly as written there."
safe_responses:
  agent.answer: "I can't state that reliably from the data."
  default: "I can't help with that request."
```

- [ ] **Step 1: Failing tests** (`RocConfigSpec`):
  - the fixture loads;
  - `forTemplate(agent.answer)` gives `numbers_grounded`, `contains_pii`, `guarantee_language`, `specialized_advice` and `unsafe_content`, and `router.jev` gets only `route_valid` plus the global deterministic ones;
  - a project-level threshold above the enterprise one is refused at load ("a lower level may only lower τ");
  - a Critical threshold above `tau_max` is refused;
  - `governanceConfig("agent", roc)` exports output constraints and thresholds that clad's `ConfigLoader.loadFromString` accepts;
  - a conformance `RocChecks` run over a minimal bundle built from that config gives R-ROC-THRESH SATISFIED. Build the bundle in a temp directory, as `RoundTripSpec` does.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Run** the spec, then `sbt test`. Earlier specs that construct `Runtime` need the new fields: add them only to `Fixtures.runtime`, the one site already allowlisted. That allowlist entry is keyed to the line's content, so changing the line invalidates it. Report that, and ask the controller, who asks David to re-approve the new line hash. Do not edit the allowlist yourself.
- [ ] **Step 4: Commit** `feat(gateway): ROC configuration and exported thresholds`.

---

### Task 2: Deterministic output checkers

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/roc/Checkers.scala`
- Modify: `app/build.sbt` (add `"com.networknt" % "json-schema-validator" % "1.5.1"` to `gateway`)
- Test: `app/gateway-test/src/test/scala/clad/gateway/roc/CheckersSpec.scala`

**Interfaces:**
- Produces: `object Checkers { def check(property: String, ctx: OutputContext, runtime: Runtime, roc: RocConfig, gov: Governance): Either[String, Boolean] }`, where `true` means the property is present (detected). It is built on:

```scala
final case class OutputContext(content: String, toolCalls: Vector[ujson.Obj], evidence: Option[ujson.Value],
                               requestTools: Vector[String], responseSchema: Option[ujson.Value], templateId: String)
```

Semantics (spec 8.2), each a pure function:
- **`numbers_grounded`** (obligation): every number in `content` appears in the evidence rows after normalisation.
  - Strip `$` and thousands separators.
  - A percentage p also matches the ratio p/100.
  - A figure matches when it equals a row value rounded to the number of decimals shown.
  - Integers below `runtime.groundingIgnoreBelow` are ignored.
  - No evidence → not applicable, treated as satisfied, and the entry says so.
- **`extraction_grounded`**: every number in the extracted JSON values appears in the evidence pages text under the same normalisation.
- **`schema_valid`**: the content parses as JSON and validates against the request's `response_format.json_schema.schema` (networknt, draft 2020-12).
- **`tool_calls_valid`**: every tool call names a tool from the request, and its `arguments` parse as a JSON object.
- **`contains_pii`**: the EPG checker's regex patterns (reuse `Governance.checkers("contains_pii")`).
- **`guarantee_language`**: the regexes `guaranteed? returns?`, `risk[- ]free`, `can'?t lose` and `no risk`, case-insensitive. These are fixed by the spec, so they're constants, not limits.
- **`route_valid`**: the Jev reply JSON has `answers.intent.choice`, and it is in `roc.routerIntents`.

- [ ] **Step 1: Failing tests**, at least three per checker, including the Review Focus number cases:
  - `6.66 %` against `0.0666328857` → grounded; `7.61%` against `0.0761007292` → grounded;
  - `$1,234.50` against `1234.5` → grounded;
  - `42` absent from the rows → ungrounded; `3` → ignored;
  - a number only in the question → ungrounded;
  - tool call `apply_change_set` not in the request's tools → invalid; arguments `"[1]"` → invalid;
  - Jev intent `"other"` → `route_valid` unsatisfied.
- [ ] **Step 2: Implement.** **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): deterministic ROC checkers`.

---

### Task 3: The Llama Guard classifier

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/roc/LlamaGuard.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/roc/LlamaGuardSpec.scala`

**Interfaces:**
- Consumes: `Upstream` (gateway plan Task 7), so the call goes through `HttpUpstream` → egress, with the interaction id.
- Produces:

```scala
final case class GuardScores(unsafe: Double, categories: Map[String, Double], verdict: String /* safe|unsafe */)
final class LlamaGuard(upstream: clad.gateway.pipeline.Upstream, profile: ClassifierProfile):
  def score(conversation: Vector[ujson.Obj] /* user + assistant turns */, interactionId: String): Either[String, GuardScores]
```

- **Request:** `{"model": profile.model, "messages": conversation, "max_tokens": <from profile: verdict_tokens>, "logprobs": true, "temperature": 0}`. Add `verdict_tokens` to the profile YAML; it is the number of tokens needed to read the verdict and category.
- **Parse:** find the first token whose text trimmed is `safe` or `unsafe`. Take its logprob lp:
  - `unsafe` → P(unsafe) = e^lp;
  - `safe` → P(unsafe) = 1 − e^lp.
- **Category scores:** the category token after `unsafe` (`S1`…`S14`) gets P(unsafe) × e^(its lp). When the verdict is `safe`, every category score is P(unsafe), an upper bound (spike decision).
- **Failure:** no verdict token, or no logprob on it → `Left("classifier reply has no verdict logprob")`.

- [ ] **Step 1: Failing tests** with a local `HttpServer` returning the spike's real shapes:
  - `\n\nsafe` with lp −3.7e-6 → unsafe ≈ 3.7e-6;
  - `\n\nunsafe\nS6` with lp 0.0 on each token → unsafe 1.0 and S6 1.0;
  - no logprobs → Left;
  - the request carries `X-Clad-Interaction-Id`, `logprobs: true` and the profile's model.
- [ ] **Step 2: Implement.** **Step 3: Run.** **Step 4: Commit** `feat(gateway): Llama Guard classifier with logprob scores`.

---

### Task 4: The ROC evaluator, decision and fallbacks

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/roc/{Roc.scala, Fallbacks.scala}`
- Test: `app/gateway-test/src/test/scala/clad/gateway/roc/RocSpec.scala`

**Interfaces:**
- Produces:

```scala
final case class RocOutcome(outcome: String /* pass|flag|block|fallback:redacted|fallback:retry|fallback:safe_response */,
                            entries: Vector[clad.runtime.AuditEntry], delivered: ujson.Value /* the reply to return */,
                            artifactDigest: String /* sha256 of the evaluated output text */, blockedBy: Vector[String])
final class Roc(cfg: GatewayConfig, guard: LlamaGuard, retry: (ujson.Obj, String) => Either[String, ujson.Value] /* upstream call for a retry */):
  def evaluate(t: Template, request: ujson.Obj, reply: ujson.Value, evidence: Option[ujson.Value], iid: String,
               classifierSync: Boolean, at: java.time.Instant): Either[String, RocOutcome]   // Left = component failure
  def classifyLater(t: Template, request: ujson.Obj, reply: ujson.Value, iid: String, at: java.time.Instant): Either[String, Vector[clad.runtime.AuditEntry]]
```

Pipeline (spec 8.3):
1. **Deterministic checks** in config order. Each becomes an `AuditEntry`: `Mechanical`, `MechanicalDetail(detected)`, level `Enterprise`, version `roc.version`. For a Critical template, the first unsatisfied check goes straight to step 4.
2. **Classifier,** when `classifierSync`. The caller passes `true` for Critical. `false` means the caller runs `classifyLater` after delivery (Standard). Each classifier constraint becomes an `AuditEntry` with `Classifier` and `ClassifierDetail(profile.id, profile.version, score, τ, thresholdVersion)`. The entry is satisfied iff score < τ.
3. **Decision:**
   - any unsatisfied Critical deterministic constraint → `block`;
   - any classifier score ≥ τ → `flag` for Standard, `block` for Critical;
   - otherwise `pass`.
4. **Fallback for `block`** (`Fallbacks.scala`):
   - **Only `contains_pii` unsatisfied** → replace each match with `[redacted]`, then re-run step 1 on the redacted text. If it now passes, the outcome is `fallback:redacted` and the delivered content is the redacted text.
   - **`numbers_grounded` or `extraction_grounded` unsatisfied** → up to `runtime.maxRetries` retries. Each retry calls `retry(request + a user message holding roc.retryVariant, iid)`. Skip a retry when the token-set Jaccard similarity of the new output to a previous failed output exceeds `runtime.retrySimilarity`. If a retry's output passes steps 1–3, the outcome is `fallback:retry` and that output is delivered.
   - **Otherwise** → `fallback:safe_response`. The content is `roc.safeResponses(t.id)`, or `default`. For `agent.answer`, append the evidence rows as a Markdown table. Remove `tool_calls`.
   - If the redaction or a retry fails to pass, use the safe response.

The final outcome determines the reply's content. The entries recorded are those of the output finally delivered: the redacted text, the retry output, or, for the safe response, the evaluation of the blocked output with outcome `fallback:safe_response`.

- [ ] **Step 1: Failing tests** with fake `LlamaGuard` scores and a fake `retry`:
  - grounded agent answer → `pass`, with entries for every applicable constraint and classifier entries carrying their scores;
  - ungrounded number → the retry passes → `fallback:retry`, and the retry was called once with the variant line;
  - ungrounded twice, identical output → the similarity skip goes straight to the safe response, which contains the rows table and none of the blocked text;
  - an SSN in an assistant answer → `fallback:redacted`, with no SSN in the delivered content;
  - an S6 score of 0.9 on Standard `assistant.general` → `flag` (content unchanged); on Critical `agent.answer` → `block` → safe response;
  - a classifier `Left` with `classifierSync` → `Left` (the caller writes the degraded record and blocks);
  - planner tool calls valid → `pass`; an invalid tool name → safe response with no tool calls;
  - R-ROC-DECISION's rules, checked against clad's `RocChecks` logic on the produced entries and outcomes: never `pass` with an unsatisfied entry; never `pass` with score ≥ τ; Critical deterministic failure → `block` or `fallback:*`.
- [ ] **Step 2: Implement.** **Step 3: Run.** **Step 4: Commit** `feat(gateway): ROC decision and fallbacks`.

---

### Task 5: Wire ROC into the pipeline

**Files:**
- Modify: `app/gateway/src/main/scala/clad/gateway/pipeline/Gateway.scala`, `app/gateway/src/main/scala/clad/gateway/Main.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/pipeline/GatewayRocSpec.scala`

Behaviour:
- **After a valid upstream reply in `complete`:** run `roc.evaluate`, with `classifierSync = (tier == "Critical")`.
  - A `Left` is a component failure: `fail(t, iid, "roc: …")`, so Critical gives 403 with a degraded record.
  - Otherwise append the ROC record: component `roc`, surface `Output`, outcome `RocOutcome.outcome`, the entries, artifact digest = sha256 of the evaluated output text, and the project's config digest.
  - Then map the outcome:

    | Outcome | Response |
    |---|---|
    | `pass`, `flag`, `fallback:*` | `Delivered(iid, outcome, delivered)` |
    | `block` with no fallback | `Blocked(iid, <first blocking property>)` |

    `block` without a fallback should not occur, since `Roc` always falls back. Keep the case for safety.
- **Standard tier:** when `classifierSync` is false, the ROC record holds the deterministic entries only and the response returns at once. A background task on a bounded executor (pool size from `runtime.yaml`, new key `roc_background_workers`) then runs `classifyLater` and appends a second ROC record with the classifier entries:
  - outcome `flag` when any score is ≥ τ, else `pass`;
  - on failure, a degraded record with posture FailOpenFlagged.
  
  One interaction can therefore have two ROC records. Confirm that conformance's `RocChecks` and R-AI5 accept that, and record what you find. If R-AI5 or R-ROC-DECISION reject it, stop and report NEEDS_CONTEXT; don't change conformance.
- **Passthrough:** after a delivered `router.jev` reply, run `route_valid`, and append a ROC record with outcome `pass` or `fallback:safe_response`.
  - An invalid route returns `{}` with status 200 (spec 8.2: the router falls back to its default route), with outcome `fallback:safe_response`.
  - This gives router interactions their ROC record, so R-AI5 and R-EA3 no longer count them as incomplete.
- **Main:** build `LlamaGuard` with the profile's upstream from `CLAD_GATEWAY_UPSTREAMS`; it is normally `openai`, since DeepInfra serves Llama Guard on the same API. The `StartupCheck` adds an error if the classifier model is not `approved` in `models.yaml`.

- [ ] **Step 1: Failing tests**, with fakes, on `MemoryStore`:
  - a delivered Critical call has exactly one EPG and one ROC record in the chain;
  - a classifier timeout on Critical → 403 plus a degraded record, and the output is not delivered;
  - Standard: a ROC record on return, then a second one once the background task completes (await the executor in the test);
  - router: a ROC record with `route_valid`; an invalid intent → `{}` with outcome `fallback:safe_response`;
  - the classifier call reaches the upstream with the same interaction id.
- [ ] **Step 2: Implement.** **Step 3: Run.** **Step 4: Commit** `feat(gateway): ROC in the pipeline`.

---

### Task 6: Classifier validation command

**Files:**
- Modify: `app/gateway/src/main/scala/clad/gateway/Main.scala` (command `validate-classifier`)
- Create: `app/gateway/src/main/scala/clad/gateway/roc/Validation.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/roc/ValidationSpec.scala`

`clad-gateway validate-classifier`:
1. Read `<config>/classifiers/validation.jsonl`, lines of `{"id", "conversation": [...], "expected": "safe"|"unsafe", "category"?}`.
2. Refuse to run unless `attestations.yaml` holds an attestation for `template_id: classifier.llama_guard.validation` with the file's sha256 as `template_version` (the human label review).
3. Score every item with `LlamaGuard`.
4. Compute FP and FN rates at the enterprise τ, and write `<data>/classifier-validation.json`: `{profile, validatedAt, fp, fn, items, misclassified: [ids]}`. The config mount is read-only.
5. Print the two lines the operator copies into `classifiers/llama_guard.yaml` (`measured_fp`, `measured_fn`, `validated_at`).

Each scoring call is registered in the GIL under a `classifier.validation` principal. Otherwise conformance would count them as ghosts.

- [ ] **Steps:** a failing test with a fake `LlamaGuard` (rates computed; a missing or stale attestation refused), then implement, run and **commit** `feat(gateway): classifier validation`.

---

### Task 7: Export, round trip, docs

**Files:**
- Modify: `app/gateway-test/src/test/scala/clad/gateway/export/RoundTripSpec.scala`, `app/gateway/README.md`, `docs/specs/2026-10-16-clad-gateway-design.md` (section 7 step 7: the implemented behaviour, with no open question left), `completion.json`

- [ ] **Step 1:** Extend the round trip with ROC. It needs:
  - a fake classifier upstream (the same `FakeUpstream`, answering Llama Guard requests with a `safe` verdict and logprob);
  - one delivered `agent.answer` (pass), one ungrounded answer that ends in `fallback:safe_response`, and one router passthrough.

  Then assert SATISFIED for R-ROC-PRESENT, R-ROC-DECISION, R-ROC-THRESH, R-AI5 and R-EA3, plus all the requirements already asserted.
- [ ] **Step 2:** README: ROC config files, outcomes and HTTP mapping, `validate-classifier`, the scoring method. Spec: replace open question 14.1 with the decision.
- [ ] **Step 3:** `sbt test`, preflight, completion record. **Commit** `feat(gateway): ROC in the bundle; docs`.

---

### Task 8: personal_finance ROC configuration and labelled validation set

**Files (personal_finance worktree, branch `feature/roc`):**
- Create: `config/governance/roc.yaml` (Task 1's format, with the full spec 8.2 table), `config/governance/classifiers/llama_guard.yaml`, `config/governance/classifiers/validation.jsonl`
- Modify: `config/governance/runtime.yaml` (`roc_background_workers` with a source comment); `stack/tests/test_compose.py` is not needed

- [ ] **Step 1:** Write `roc.yaml` and the profile:
  - model `meta-llama/Llama-Guard-4-12B`, which `models.yaml` already lists as classifier, approved by David;
  - upstream `openai`;
  - `verdict_tokens`;
  - empty measured fields.
- [ ] **Step 2:** Draft about 50 labelled items in `validation.jsonl`, using personal-finance answers only:
  - grounded answers, safe;
  - fund or stock recommendations, `unsafe`, S6;
  - guarantee language;
  - neutral small talk;
  - borderline hedged suggestions;
  - planner replies.

  No household names, and no real figures from `data/`.
- [ ] **Step 3:** A Python test in `stack/tests/test_governance_config.py` that `roc.yaml` names only templates that exist in `config/governance/prompts/`. **Commit** `governance: ROC constraints and a labelled classifier validation set (labels pending David's review)`.
- [ ] **Step 4 (HUMAN):** David reviews the labels. Claude then records his attestation `{template_id: classifier.llama_guard.validation, template_version: <sha256 of validation.jsonl>, attestor: david, ...}` at his instruction.

---

### Task 9 (OPERATOR): deploy, validate, remeasure

1. Rebuild the gateway image; recreate `clad-gateway`.
2. `clad-gateway check`; expect exit 0.
3. `clad-gateway validate-classifier`. Copy `measured_fp`, `measured_fn` and `validated_at` into the profile, and commit.
4. `scripts/governance_measure.sh roc`, then commit `docs/governance/scorecards/05-roc.{md,json}`.
5. Expected, with the constraint approval still pending:
   - R-ROC-PRESENT, R-ROC-DECISION, R-ROC-THRESH, R-AI5 and R-EA3 SATISFIED;
   - R-EA4 VIOLATED until David approves the constraint set;
   - R-MDR-ALERT VIOLATED until the MDR plan.
6. Update `docs/governance/decisions/2026-10-16-gateway-moves-to-clad.md` with the result.
