# Runtime Output Controls: Governing AI Model Outputs in Regulated Enterprises

**White Paper 2 of Clad**

**Version:** 1.0
**Date:** April 2026
**Audience:** CIO/CISO, Senior AI Architects, Compliance Officers
**Relationship:** Builds on the Clad Meta-Framework (v5). Companion to WP1: Enterprise Prompt Governance.
**Scope:** Governs control surfaces S_output (raw model output) and S_delivery (delivered output after post-processing)

---

## 1. Executive Summary

### 1.1 The Problem: Ungoverned Model Outputs

Enterprise Prompt Governance (WP1) ensures that the instructions sent to AI models are compliant, consistent, and auditable. But a governed prompt does not guarantee a governed output. AI models are stochastic — the same prompt can produce different outputs across invocations. A perfectly governed prompt can still yield an output that discloses protected health information, provides unauthorized financial advice, reproduces copyrighted training data, or responds to a jailbreak attempt with content the prompt explicitly prohibited.

This is not a theoretical concern. It is a mathematical certainty, formalized in the meta-framework as the Irreducible Residual Risk theorem: even with all governance components deployed, residual compliance risk is non-zero. The question is not whether non-compliant outputs will occur, but whether the enterprise can detect them before they reach end users, contain them when they do, and trace them back to root cause.

Runtime Output Controls (ROC) addresses this gap. Where EPG governs what goes into the model, ROC governs what comes out.

### 1.2 What ROC Provides

ROC is a runtime evaluation and filtering layer that operates between model output generation and output delivery to end users or downstream systems. It provides:

**Hybrid output evaluation.** Hard output constraints — such as "never include Social Security numbers in responses" — are evaluated using deterministic checks identical in character to EPG's mechanical evaluations. Soft output constraints — such as "detect potential medical advice" — are evaluated using classifier-based scoring with configurable thresholds. Both tiers produce auditable evaluation records, but with explicitly different evidentiary properties.

**Risk-tiered timing.** Critical workloads (PHI, PCI, safety-critical operations) receive real-time blocking evaluation: no output reaches the end user until ROC has evaluated it. Standard and low-risk workloads receive outputs immediately, with asynchronous evaluation and post-hoc flagging of violations. This balances safety against latency.

**Threat-specific controls.** ROC addresses the threats that EPG explicitly deferred: prompt injection detection (T7), jailbreak detection (T8), PII/PHI filtering (T10), training data leakage detection (T10), and cross-tenant data isolation (T11).

**Full audit chain participation.** ROC produces output audit records — component-signed, hash-chained, and version-stamped — that compose with EPG's prompt audit records and MDR's monitoring records into the complete audit chain defined by the meta-framework.

### 1.3 How ROC Fits the Governance Solution

ROC instantiates component g_ROC from the meta-framework, governing S_output (raw model output, governability γ = partial) and S_delivery (delivered output after filtering, governability γ = full). It is independently deployable — its guarantees hold without EPG or MDR present — but its evaluation effectiveness improves with EPG context (the prompt and its constraint evaluations inform output risk assessment).

The three-component governance pipeline is: EPG governs the prompt (before inference) → the model produces output → ROC evaluates and filters the output (before delivery) → MDR monitors patterns across interactions (continuous). Each component provides independent guarantees that compose into system-wide coverage.

**Relationship to WP1's formal model.** EPG uses a deontic constraint framework (obligations O and prohibitions F over atomic properties from a controlled vocabulary Φ). ROC uses a different evaluation model — deterministic rules, classifier-based scoring, and composite logic — because output evaluation is fundamentally different from prompt constraint evaluation. Outputs are dynamic, stochastic artifacts that require probabilistic detection, not static artifacts that can be evaluated against binary predicates. The two components share the property vocabulary Φ and compose at the audit chain level (shared interaction_id, hash-chained records), but they do not share a constraint evaluation logic. This is by design, not an inconsistency.

---

## 2. Principles

ROC shares the meta-framework's foundational principles (risk management over elimination, audit the artifact, composable governance). Three additional principles are specific to output controls.

### 2.1 Evaluate What Was Produced, Not What Was Intended

EPG ensures the prompt requests compliant behavior. ROC evaluates whether the model actually produced compliant output. These are different questions with different answers. A prompt that forbids medical diagnosis may still receive a diagnostic response if the model ignores, misinterprets, or is jailbroken past the instruction. ROC's evaluation target is the output artifact itself — the actual tokens the model generated — not the prompt that requested them.

### 2.2 Honest Probabilism Over False Determinism

Some output evaluations are deterministic: a Social Security number regex either matches or it doesn't. Others are probabilistic: a classifier scoring whether text constitutes "medical advice" produces a confidence score, not a binary answer. ROC does not pretend probabilistic evaluations are deterministic. It explicitly distinguishes two evaluation tiers — deterministic and classifier-based — with different audit properties. A deterministic evaluation is hard evidence ("this output contained an SSN"). A classifier-based evaluation is scored evidence ("this output scored 0.87 on the medical-advice classifier, above the 0.80 threshold"). Both are auditable. Neither is falsely promoted to the other's evidentiary standard.

### 2.3 Speed Is a Safety Property

In output governance, latency is not just a performance concern — it is a safety and availability property. A real-time blocking evaluator that takes 10 seconds per output transforms a responsive AI system into an unusable one. An unusable governed system drives users to ungoverned alternatives (shadow AI). Therefore, ROC explicitly models the tradeoff between evaluation depth and delivery latency through risk-tiered timing: real-time blocking for Critical workloads where safety outweighs speed, and asynchronous evaluation for Standard/Low workloads where speed enables adoption and adoption enables governance.

### 2.4 Defense in Depth, Not Defense in Duplication

ROC does not duplicate EPG's work. EPG ensures the prompt is well-formed. ROC ensures the output is compliant. The two components address different failure modes: EPG addresses governance process failures (wrong instructions). ROC addresses model behavior failures (wrong outputs despite right instructions). When EPG provides prompt context to ROC, ROC uses it to improve evaluation accuracy — but ROC's guarantees do not depend on EPG being present.

---

## 3. The Output Evaluation Model

EPG evaluates prompts — static artifacts authored before inference. ROC evaluates outputs — dynamic artifacts generated by a stochastic model at inference time. This fundamental difference shapes the entire evaluation model.

### 3.1 Two Evaluation Tiers

ROC uses a hybrid evaluation model with two tiers, each with distinct properties:

**Tier 1: Deterministic evaluation.** Rule-based checks that produce binary pass/fail results. A regex for Social Security numbers either matches or doesn't. A keyword blocklist either flags or doesn't. A structural check for required disclaimers either finds them or doesn't. These evaluations are deterministic, repeatable, and produce the same evidentiary standard as EPG's mechanical checks (C_m). They are the output-side equivalent of EPG's mechanical evaluation.

Formal property: `eval_d : O × R → {pass, fail}` where O is the output and R is the rule. The evaluation is a total function — every output has a definite result for every rule.

**Tier 2: Classifier-based evaluation.** ML classifiers that produce confidence scores. A PII detection model scores the likelihood that an output contains personally identifiable information. A toxicity classifier scores the likelihood of harmful content. A medical-advice detector scores the likelihood that text constitutes clinical guidance. These evaluations are probabilistic — the same output evaluated twice by the same classifier may produce slightly different scores due to model non-determinism.

Formal property: `eval_c : O × K → [0, 1]` where O is the output and K is the classifier. The evaluation produces a confidence score, not a binary result. A configurable threshold τ_K converts the score to a governance decision: `score ≥ τ_K → flag` and `score < τ_K → below_threshold`.

**Critical distinction:** A classifier score below threshold is NOT a "pass" in the same sense as a deterministic evaluation pass. It means "the classifier did not reach the threshold of suspicion at the configured sensitivity." The audit record must use quantized satisfaction notation — `o ⊨_τ c` — meaning "output o satisfied constraint c at threshold τ." This is weaker evidence than deterministic satisfaction (`o ⊨ c`) and the audit record must make this distinction explicit. Calling a below-threshold score a "pass" is regulatory dishonesty.

### 3.2 Threshold Governance

Classifier thresholds are governance artifacts, not implementation details. The threshold τ_K for each classifier K determines the tradeoff between false positives (compliant outputs incorrectly flagged) and false negatives (non-compliant outputs incorrectly passed). This tradeoff has direct regulatory and operational consequences:

- A PHI classifier threshold set too low floods the review queue with false positives, making governance operationally unsustainable.
- A PHI classifier threshold set too high lets real PHI disclosures through, creating regulatory exposure.

Therefore, thresholds are subject to the same governance controls as EPG constraints: they are versioned, owned by a governance domain, require dual-control modification, and are included in the audit record for every evaluation. When a regulator asks "why did your system approve this output?" the answer includes not just the classifier score but the threshold in effect at that time and who set it.

Risk-tiered threshold defaults:
- Critical workloads: conservative thresholds (lower τ, more flags, fewer misses)
- Standard workloads: balanced thresholds
- Low-risk workloads: permissive thresholds (higher τ, fewer flags, more throughput)

**Threshold monotonicity invariant.** Consistent with WP1's constraint inheritance principle (lower levels can only tighten, never loosen), classifier thresholds must satisfy: `∀ l₂ ≺ l₁ : τ(k, l₂) ≤ τ(k, l₁)`. A department may lower a threshold (tighten, more flags) but may never raise it above the enterprise default. A project may lower it further but never above the department's level. This prevents governance weakening through threshold manipulation.

**Threshold policy bounds for Critical tier.** For Critical-tier constraints, each classifier has a policy-defined maximum threshold (τ_max) that cannot be exceeded without an exceptional process (board-level risk exception, documented and time-limited). This prevents insiders from gradually raising thresholds until classifiers are effectively disabled. Threshold changes and their empirical flag rates must be monitored centrally and reported to MDR. A sustained decrease in flag rates without corresponding improvement in classifier precision is an anomaly that triggers governance review.

### 3.3 Audit Properties by Tier

The two tiers produce audit records with explicitly different evidentiary properties:

**Deterministic evaluation records** state: "This output was evaluated against rule R. The result was pass/fail. This is a deterministic, repeatable finding."

**Classifier-based evaluation records** state: "This output was scored by classifier K (version V) with threshold τ_K. The score was S. The governance decision was flag/below_threshold. This is a probabilistic finding with the stated confidence." A classifier "below_threshold" result is not proof of compliance — it is one input to a broader control set. For regulated constraints (PHI, financial advice), organizations must maintain external classifier validation reports linked in the classifier governance profile; auditors should reference those reports alongside per-event records.

Both records are component-signed, hash-chained, and version-stamped per the meta-framework's audit integrity properties (AI1-AI4). But consumers of audit records — regulators, auditors, incident responders — must understand that a classifier-based "pass" is not the same evidentiary standard as a deterministic "pass." The audit record makes this distinction explicit.

### 3.4 The Relationship Between Prompt Constraints and Output Controls

EPG constrains what the prompt instructs. ROC constrains what the output contains. These are complementary but independent:

- EPG's `F(medical_diagnosis)` ensures the prompt does not instruct the model to diagnose. ROC's medical-advice classifier checks whether the output actually contains diagnostic content regardless of what the prompt said.
- EPG's `O(hipaa_disclaimer)` ensures the prompt includes a disclaimer instruction. ROC's disclaimer detector checks whether the output actually includes the disclaimer.
- EPG may have no constraint on a topic, but ROC independently blocks outputs containing PII patterns.

When EPG context is available (via the interface contract), ROC uses it to improve evaluation: if the prompt was flagged for tension between conversational tone and regulatory language, ROC may apply stricter classifiers to the output. But ROC's core guarantees — that every output is evaluated and a complete audit record is produced — hold without EPG context.

---

## 4. Output Constraint Taxonomy

Output constraints are organized into three classes based on their evaluation mechanism. Every output constraint belongs to exactly one class.

### 4.1 Deterministic Output Constraints (O_d)

Rule-based checks with binary outcomes. These are the output equivalent of EPG's mechanically evaluable constraints (C_m).

Examples:

- Pattern matching: SSN, credit card number, medical record number detection via regex or NER
- Blocklist enforcement: prohibited terms, competitor names, restricted phrases
- Structural checks: required disclaimer presence, required confidence disclosure, format compliance
- Length and format: output length limits, required JSON schema compliance, encoding validation

Properties: deterministic, repeatable, low latency, high confidence. Audit records carry full evidentiary weight.

### 4.2 Classifier-Based Output Constraints (O_c)

ML classifier evaluations with confidence scores. These have no direct equivalent in EPG — they are unique to output governance because outputs exhibit semantic properties that resist rule-based detection.

Examples:

- PII/PHI detection: NER-based classifiers trained on medical records, financial data, personal identifiers
- Toxicity and harm scoring: classifiers detecting hateful, violent, or dangerous content
- Medical advice detection: classifiers distinguishing informational content from diagnostic or prescriptive language
- Financial advice detection: classifiers detecting personalized investment recommendations vs. general market commentary
- Prompt injection detection: classifiers identifying outputs that suggest the model's instructions were overridden
- Copyright/memorization detection: classifiers detecting verbatim reproduction of training data

Properties: probabilistic, threshold-dependent, variable latency (depends on classifier complexity), confidence-scored. Audit records carry scored evidentiary weight (confidence + threshold).

Each classifier has a governance profile:

- Classifier identity and version (Axiom 5 — temporal identity)
- Threshold τ (governed artifact, dual-control modification)
- Known false positive and false negative rates at the configured threshold
- Validation cadence (how often the classifier is retested against updated benchmark data)

### 4.3 Composite Output Constraints (O_x)

Evaluations that combine deterministic and classifier-based checks into a single governance decision. These address scenarios where neither tier alone is sufficient.

Example: "Output must not disclose PHI" decomposes into:

- Deterministic: regex scan for SSN, MRN, date-of-birth patterns (O_d)
- Classifier: NER model for contextual PII detection — names in medical context, indirect identifiers (O_c)
- Composite decision: flag if EITHER the deterministic check fails OR the classifier score exceeds threshold

Composite constraints require explicit decision logic: how do the component evaluations combine? Options include ANY-flag (flag if any component flags — conservative), ALL-flag (flag only if all components flag — permissive), or weighted scoring. The decision logic is itself a governed artifact.

---

## 5. The Output Filtering Pipeline

ROC operates as a pipeline between model output generation and delivery. The pipeline has four stages, executed in order.

### 5.1 Stage 1: Deterministic Evaluation

All deterministic output constraints (O_d) are evaluated against the raw model output. This stage is fast (milliseconds) and produces binary results.

**Short-circuit rule:** If any deterministic constraint fails in Critical tier, the pipeline decision is BLOCK immediately — Stage 2 and 3 results are not awaited. This prevents latency from classifier evaluation delaying a deterministic block.

If any deterministic constraint fails:

- Critical tier: output is BLOCKED immediately (short-circuit). Proceed to Stage 4 (fallback).
- Standard/Low tier: failure is FLAGGED in the audit record. Pipeline continues.

### 5.2 Stage 2: Classifier-Based Evaluation

All applicable classifier-based output constraints (O_c) are evaluated. Classifiers score the output; scores are compared to configured thresholds.

**Critical tier: ATOMIC BUFFERED EVALUATION.** The complete output is buffered before any delivery begins. Streaming is disabled for Critical S_delivery. Classifiers run on the complete buffered output synchronously. No tokens reach the end user until all evaluations complete. Latency budget is deployment-specific (recommended: 500ms-2s maximum). If classifiers exceed the latency budget, the timeout is treated as a failure: ROC defaults to block + safe response. Delivering an unevaluated output on timeout is never permitted for Critical tier.

**Standard tier: POST-HOC AUDIT MONITORING.** Output is delivered immediately. Classifiers run asynchronously. If a violation is detected post-delivery, the violation is logged and an alert is generated. This is NOT a preventative control on S_delivery — it is a detection/audit function. The output cannot be un-delivered; the incident record enables after-the-fact remediation. Organizations must understand that Standard-tier classifier evaluation provides monitoring evidence, not real-time protection.

**Low tier: SAMPLED POST-HOC AUDIT.** Only a deployment-specific percentage of outputs are evaluated by classifiers. Minimum sampling floor must be defined per data classification and cannot be reduced below the floor without independent review. This reduces computational cost but provides only statistical coverage.

**Minimum sampling and threshold governance.** Sampling rates and thresholds for each risk tier are tied to a central data classification registry. Moving a workload from Critical to Standard or Low requires independent review (not just the owning team's decision). This prevents insider abuse: a malicious actor cannot reclassify sensitive workloads to avoid evaluation.

### 5.3 Stage 3: Composite Evaluation and Decision

Composite constraints (O_x) aggregate results from Stages 1 and 2. The governed decision logic produces a final evaluation for each composite constraint.

The pipeline produces one of three outcomes for each output:

- **PASS**: all constraints satisfied. Output proceeds to delivery.
- **FLAG**: one or more soft constraints triggered (classifier threshold exceeded). Output may proceed with a flag in the audit record, or may be held depending on risk tier and flag severity.
- **BLOCK**: one or more hard constraints failed (deterministic violation) or Critical-tier classifier threshold exceeded. Output does not proceed. Stage 4 activates.

### 5.4 Stage 4: Blocked Output Handling (Fallback)

When an output is blocked, the system must respond — silence is not acceptable to the end user. Fallback options, configurable per constraint and per risk tier:

**Safe response substitution.** The blocked output is replaced with a pre-approved safe response: "I'm unable to provide that information. Please contact [qualified resource]." Safe responses are themselves governed artifacts — authored, reviewed, and versioned under EPG constraints.

**Redaction with post-redaction re-evaluation.** For outputs where only a portion is non-compliant (e.g., an otherwise useful response that contains one SSN), the non-compliant portion is redacted and the remainder is delivered with a redaction notice. Redaction rules are deterministic and auditable. For Critical-tier PHI constraints, the composite PHI constraint (O_x) must be re-run on the redacted output. If the redacted output still flags (because residual context enables re-identification — e.g., rare disease + small location), the output is blocked entirely and a safe response is substituted. Redaction is only acceptable for PHI when the post-redaction re-evaluation passes.

**Retry with modified prompt.** The system re-invokes the model with a modified prompt that reinforces the violated constraint. The retry is itself a governed interaction (registered in the GIL, evaluated by EPG). For Critical workloads: maximum 1 retry. If the retry output is semantically similar to the original (similarity hash comparison), skip the retry and proceed directly to safe response substitution — repeated attempts against model memorization or inherent behavior waste resources and increase exposure. For Standard/Low: maximum 2 retries.

**Escalation.** For violations that cannot be handled by substitution, redaction, or retry, the interaction is escalated to a human reviewer. The output is held (not delivered) until the reviewer makes a disposition decision. Escalation is the last resort and should be rare in a well-tuned system.

Every fallback action is recorded in the audit trail: what was blocked, why, which fallback was applied, and what was ultimately delivered (or not delivered) to the end user.

**Block-rate monitoring.** Sustained high block/fallback rates are themselves an operational incident. ROC's block and substitution metrics must feed MDR or a central dashboard. When block rates exceed a baseline threshold (deployment-specific), an investigation is triggered: the root cause may be model drift, prompt governance failure, classifier miscalibration, or adversarial probing. Frequent safe-response substitution degrades user trust and drives users to ungoverned alternatives.

---

## 6. Threat-Specific Controls

The meta-framework identified five threats (T7-T11) that EPG deferred to ROC and SA. This section defines ROC's controls for each.

### 6.1 T7: Prompt Injection Detection

**Threat:** Untrusted content embedded in the prompt (via user input, RAG-retrieved documents, or tool outputs) overrides governance instructions, causing the model to produce output that violates constraints.

**ROC's role:** Detect outputs that suggest the model's instructions were overridden, regardless of whether the injection was in the prompt (EPG's concern) or the output (ROC's concern).

**Controls:**

- Deterministic (O_d): pattern detection for common injection signatures in outputs — instruction echoing ("As an AI assistant, I have been told to ignore..."), role-switching markers, system prompt leakage.
- Classifier-based (O_c): injection detection classifier trained on known injection/exfiltration output patterns. Scores the likelihood that the output was influenced by injected instructions rather than the governed prompt.
- Behavioral: compare output against the prompt's stated constraints. If EPG context is available, ROC can evaluate whether the output satisfies the constraints EPG imposed. An output that violates a constraint the prompt explicitly enforced is a strong signal of injection.
- Cross-surface consistency check (Critical tier, requires EPG context): compare the semantic intent of the output against the semantic intent of the prompt using a summarization-to-intent classifier. If `Intent(output) ∉ Intent(prompt)`, the output is flagged as potentially injection-influenced. This detects subtle injection where the model shifts topic or persona without triggering overt pattern detectors.

**Limitation:** Prompt injection is fundamentally an arms race. New injection techniques emerge regularly. ROC's controls reduce risk but cannot eliminate it. Classifier retraining cadence and red-team testing frequency must match the evolving threat landscape.

### 6.2 T8: Jailbreak Detection

**Threat:** Adversarial user inputs cause the model to ignore its safety instructions, producing content the prompt explicitly prohibited.

**ROC's role:** Detect outputs that indicate the model has been jailbroken — content that a properly-instructed model would not produce.

**Controls:**

- Classifier-based (O_c): jailbreak detection classifier scoring the likelihood that the output represents a safety boundary violation. Trained on red-team datasets of successful jailbreak outputs.
- Constraint violation detection: if EPG context is available, ROC checks whether the output violates any constraint that the prompt was evaluated against. An output violating F(medical_diagnosis) when the prompt passed EPG's F(medical_diagnosis) check is a jailbreak indicator.
- Rate-based detection: repeated outputs from the same user session that progressively approach constraint boundaries suggest probing behavior. ROC flags these patterns for MDR escalation.

**Limitation:** Jailbreak techniques evolve faster than classifiers can be retrained. ROC provides a defense layer, not an impenetrable barrier. The meta-framework's Theorem 4 (Irreducible Residual Risk) applies directly here.

### 6.3 T10: PII/PHI and Training Data Leakage

**Threat:** The model reproduces protected health information, personally identifiable information, copyrighted content, or other sensitive training data in its outputs.

**ROC's role:** Detect and prevent sensitive data from reaching end users.

**Controls:**

- Deterministic (O_d): regex and pattern matching for structured PII (SSNs, credit card numbers, medical record numbers, phone numbers, email addresses). High precision, low false-positive rate.
- Classifier-based (O_c): NER models for contextual PII detection (names in medical context, dates of birth adjacent to diagnoses, indirect identifiers). Higher recall than regex but with false-positive cost.
- Composite (O_x): for Critical-tier HIPAA/PCI workloads, combine deterministic + classifier into a conservative ANY-flag composite. Flag if either component detects potential PII.
- Verbatim reproduction detection: compare output against known copyrighted or sensitive corpus segments using similarity scoring. This addresses training data memorization — the model reproducing passages it was trained on.

**Redaction capability:** When PII is detected in an otherwise useful output, ROC can redact the PII elements (replace with "[REDACTED]" or type-appropriate placeholders) rather than blocking the entire output. Redaction preserves utility while removing the compliance violation.

**PHI-first policy for HIPAA-scope Critical workloads.** For any HIPAA-covered workload classified as Critical, the following mandatory controls apply:
- Any classifier PHI score above a conservative alert threshold (τ_alert, lower than the standard flag threshold) must trigger either block/redact or human review routing. "Flag only" without blocking or human review is not acceptable for PHI in Critical tier.
- Documented PHI residual risk must be paired with compensating controls, including a minimum percentage of human-in-the-loop review on outputs — analogous to WP1's residual gap controls (§5.6 of WP1).
- The "flag and continue" path available for Standard/Low tiers does not apply to PHI in Critical tier. Every PHI suspicion above τ_alert is either blocked, redacted (with post-redaction re-evaluation), or routed to human review.

**Audit specificity:** PII detection audit records include the type of PII detected (SSN, name, MRN, etc.), the detection method (regex, classifier, composite), and the action taken (block, redact, human-review, flag). This granularity enables HIPAA breach assessment — regulators need to know not just that a violation occurred, but what type of data was exposed and to whom.

### 6.4 T11: Cross-Tenant Data Leakage (Monitoring Signal, Not Control)

**Threat:** In multi-tenant AI deployments (shared model endpoints), data from one tenant influences outputs for another.

**ROC's role: MONITORING SIGNAL ONLY.** Cross-tenant isolation is fundamentally a model deployment and infrastructure concern — tenant-specific models, data isolation in fine-tuning, KV-cache separation. ROC cannot prevent cross-tenant contamination at the model level. It can only detect certain leakage patterns in outputs after the fact.

ROC does NOT claim T11 as a control it enforces. It provides detection signals that feed MDR for investigation:

- Tenant context binding: each interaction carries a tenant identifier. ROC compares output content against the tenant's known data domain. Anomalies (Company B's products appearing in Company A's output) are flagged to MDR.
- Deterministic (O_d): if tenant-specific identifiers are known (product names, customer IDs, internal terminology), pattern matching detects their presence in the wrong tenant's outputs. These are passed to MDR as incident signals.
- Statistical detection is MDR's responsibility, not ROC's. Subtle cross-tenant patterns (style transfer, knowledge leakage without explicit identifiers) require trend analysis across many interactions.

**This is explicitly a monitoring function, not a governance control.** ROC's audit record notes the tenant-consistency check result but does not claim to "govern" cross-tenant isolation. Infrastructure controls (model isolation, data segregation, tenant-specific deployments) are the actual controls for T11.

---

## 7. Connection to EPG and Meta-Framework

### 7.1 ROC as Component g_ROC

ROC instantiates the meta-framework's g_ROC:

- **Surfaces:** S = {S_output, S_delivery}
- **Constraints:** C_ROC = O_d ∪ O_c ∪ O_x (deterministic, classifier-based, composite)
- **Evaluation:** E = deterministic evaluation (eval_d) ∪ classifier evaluation (eval_c) ∪ composite logic
- **Audit:** A = output audit records (evaluation records per meta-framework §9)
- **R_hard:** ∅ — independently deployable
- **R_soft:** {prompt context from EPG, constraint set C*(project) from EPG}

### 7.2 Interface Contracts

**EPG → ROC (soft requirements):** EPG provides the assembled prompt, its effective constraint set, and the prompt audit record. ROC uses this context to improve evaluation accuracy — for example, applying stricter classifiers when the prompt was flagged for tension, or checking output compliance against specific EPG constraints. Without EPG context, ROC still evaluates outputs against its own constraint set but with reduced contextual precision.

**ROC → MDR (soft requirements):** ROC provides the output audit record, any flagged violations or anomalies, the delivered output and its hash, and violation flags. MDR uses this for trend analysis, incident correlation, and compliance drift monitoring.

### 7.3 Audit Chain Participation

ROC produces output audit records for every governed interaction. Each record contains:

- Interaction identifier (shared with EPG and MDR via GIL)
- The raw output hash and the delivered output hash (may differ if redaction occurred)
- Evaluation results for every applicable output constraint, with tier designation (deterministic or classifier-based)
- For classifier evaluations: classifier version, score, threshold, and governance decision
- For blocked outputs: the fallback action taken and the content ultimately delivered
- Predecessor pointer to EPG's audit record (if available)

Records are component-signed with ROC's KMS key, hash-chained per AI2, and subject to all meta-framework audit integrity properties (AI1-AI5). During ROC failure, the Governance Supervisor produces process records per meta-framework §7.4.

### 7.4 Failure Posture

ROC's failure posture is risk-tiered, consistent with the meta-framework:

- **Critical:** fail-closed. No output is delivered without ROC evaluation. If ROC is unavailable, the output is held and a safe fallback response is delivered to the user.
- **Standard:** fail-open-flagged. Output is delivered but flagged as ungoverned. Asynchronous evaluation resumes when ROC recovers.
- **Low:** fail-open. Output is delivered. The missed evaluation is logged.

ROC's fail-closed posture has a different cost profile than EPG's: EPG blocks a prompt before model invocation (no compute wasted). ROC blocks an output after model invocation (compute is consumed, output is generated but not delivered). This means ROC failures are more expensive operationally — the model has already done the work. This motivates aggressive HA/DR investment for ROC in Critical workloads.

---

## 8. Operational Guidance

### 8.1 Phased Adoption

**Phase 1 (3-6 months):** Deploy deterministic output controls (O_d) for the highest-risk constraints: PII pattern detection, blocklists, required disclaimer checks. These are low-latency, low-false-positive, and immediately valuable. Deploy for Critical workloads first.

**Phase 2 (6-12 months):** Add classifier-based controls (O_c) for key threat areas: PII/PHI NER, medical advice detection, financial advice detection. Tune thresholds against production data. Establish classifier governance (versioning, validation cadence, threshold ownership).

**Phase 3 (12-18 months):** Deploy composite constraints (O_x) combining deterministic and classifier outputs. Extend coverage to Standard workloads. Implement full fallback pipeline (safe response, redaction, retry, escalation).

**Phase 4 (18+ months):** Deploy threat-specific advanced controls (injection detection, jailbreak detection, memorization detection). Continuous classifier improvement based on production findings. Cross-component optimization with EPG and MDR.

### 8.2 Classifier Governance

Classifiers are not static tools. They are governed artifacts requiring lifecycle management:

- **Versioning:** every classifier has a version identifier recorded in audit trails (Axiom 5)
- **Validation cadence:** classifiers are periodically retested against updated benchmark datasets. Critical classifiers (PII, medical advice): quarterly. Standard classifiers: semi-annually.
- **Threshold management:** thresholds are governed artifacts with dual-control modification. Threshold changes follow the same authorship process as EPG constraint changes.
- **Drift detection:** classifier performance is monitored against baseline metrics. If precision or recall degrades beyond defined thresholds, the classifier is flagged for retraining or replacement.
- **Red-team testing:** classifiers are regularly tested with adversarial inputs designed to bypass detection. Results inform retraining priorities.

### 8.3 Risk-Tiered Application

| Property | Critical | Standard | Low |
|----------|----------|----------|-----|
| Timing | Atomic buffered, synchronous | Post-hoc audit monitoring | Sampled post-hoc audit |
| Streaming | Disabled (fully buffered) | Permitted | Permitted |
| Deterministic checks | All applied, short-circuit on fail | All applied | All applied |
| Classifier checks | All applied, synchronous | All applied, async | Sampled (min floor enforced) |
| Fail posture | Fail-closed (block on timeout) | Fail-open-flagged | Fail-open |
| Fallback on block | Safe response + redaction (with re-eval) | N/A (detection only) | N/A |
| Audit evidence | Deterministic: hard. Classifier: scored (⊨_τ) | Same | Sampled |
| PHI handling | PHI-first policy: block/redact/human-review | Flag + alert | Flag |
| Control type | PREVENTATIVE | DETECTIVE (post-hoc) | DETECTIVE (sampled) |

**Critical distinction:** Only Critical tier provides true preventative controls on S_delivery. Standard and Low tiers provide detection and audit evidence — valuable for incident response and trend analysis, but the output has already been delivered before violations are detected. This distinction must be communicated clearly to compliance teams and regulators.

### 8.4 Latency Budget

Real-time blocking evaluation adds latency to every Critical-tier interaction. The latency budget must be explicitly managed:

- Deterministic evaluation: target < 50ms (regex, blocklist, structural checks)
- Classifier evaluation: target < 500ms per classifier (depends on model complexity)
- Total pipeline: target < 2 seconds for Critical-tier full evaluation
- **Timeout behavior for Critical tier is non-negotiable:** if classifiers exceed the latency budget, the timeout is treated as a failure. ROC defaults to block + safe response. Delivering an unevaluated output on timeout is never permitted for Critical workloads. This is the only defensible posture under HIPAA, NERC CIP, and SOX.
- For Standard/Low tiers: timeout simply means the async evaluation was delayed; the output was already delivered.

### 8.5 Resource Planning

Classifier governance at scale requires dedicated investment. Organizations should plan for:

- **Data engineering:** maintaining benchmark datasets and regression test suites for each classifier. Expect 0.5-1 FTE per critical threat domain (PHI, financial advice, safety).
- **MLOps:** classifier retraining, deployment, and drift monitoring infrastructure. Shared across classifiers but non-trivial.
- **Security data science:** red-team testing and adversarial evaluation. Expect 0.25-0.5 FTE per classifier for quarterly testing cadence.
- **Centralization recommendation:** organizations should centralize classifier governance rather than having each business unit maintain independent classifier stacks. A central AI Safety/Governance team owns classifier lifecycle; business units consume classifiers as governed services.
- **Scope management:** if an organization cannot sustain the validation cadence for all classifiers, it must scope ROC to fewer, truly critical threats rather than deploy poorly-maintained classifiers that provide false confidence.

---

## 9. Limitations

ROC governs the output surface. This section states explicitly what ROC does and does not guarantee.

**What ROC guarantees (formal commitments):**

- Evaluation completeness: every governed output in Critical and Standard tiers is evaluated against every applicable output constraint, with a tamper-evident, version-stamped audit record. Low-tier sampled interactions have evaluation records only for sampled outputs.
- Deterministic evaluation reliability: deterministic output constraints produce stable, repeatable results
- Audit chain participation: ROC's audit records compose with EPG and MDR records via shared interaction identifier

**What ROC does not guarantee:**

- Output correctness: ROC checks for constraint violations, not factual accuracy. An output that passes all ROC evaluations may still be incorrect, misleading, or unhelpful.
- Classifier infallibility: classifier-based evaluations are probabilistic. False negatives (missed violations) are possible at any threshold. The meta-framework's Theorem 4 (Irreducible Residual Risk) applies.
- Complete threat coverage: new attack techniques (novel jailbreaks, novel injection patterns) may evade current classifiers until retraining occurs.
- Model behavior modification: ROC filters outputs; it does not change how the model generates them. A model that consistently produces non-compliant outputs requires prompt governance (EPG) or model-level intervention, not just output filtering.

**What ROC provides as governance-process tools (operationally valuable, not formally proven):**

- Classifier-based detection: probabilistic scoring that catches most violations but cannot guarantee zero false negatives
- Injection and jailbreak detection: pattern-based and classifier-based detection that covers known techniques but is subject to evasion by novel methods
- Cross-tenant leakage detection: limited to detectable patterns in output content; does not address model-level contamination

The formal model (Appendix A) uses the same two-tier structure throughout: formal guarantees for deterministic evaluations, scored evidence for classifier-based evaluations. This distinction is not a weakness to be fixed — it is an honest representation of what is and is not achievable in output governance.

---

## Appendix A: Formal Model

### A.1 Primitive Sets

```
O_raw = set of all raw model outputs        — generated by model, before filtering
O_del = set of all delivered outputs         — after ROC processing
K     = set of all classifiers              — versioned ML models for output scoring
R     = set of all deterministic rules      — pattern, blocklist, structural checks
τ     = K → [0, 1]                          — threshold function mapping classifiers
                                               to their governance thresholds
```

ROC also uses the shared sets from the meta-framework: I (interactions), T (time), V (audit records), Φ (property vocabulary).

### A.2 Output Constraint Taxonomy

```
C_ROC = O_d ⊎ O_c ⊎ O_x     (disjoint union)

O_d = deterministic output constraints
  eval_d : O_raw × R → {pass, fail}
  Properties: total, deterministic, repeatable

O_c = classifier-based output constraints
  eval_c : O_raw × K → [0, 1]
  Governance decision: eval_c(o, k) ≥ τ(k) → flag ; else → below_threshold
  Quantized satisfaction: o ⊨_τ c  (weaker than deterministic o ⊨ c)
  Properties: total, probabilistic, threshold-dependent

Threshold monotonicity invariant:
  ∀ l₂ ≺ l₁ : τ(k, l₂) ≤ τ(k, l₁)
  Lower levels can only tighten (lower) thresholds, never loosen.

Threshold policy bounds (Critical tier):
  ∀ k, risk_tier = critical : τ(k) ≤ τ_max(k)
  where τ_max is policy-defined and cannot be exceeded without
  exceptional process.

O_x = composite output constraints
  eval_x : O_raw × (𝒫(O_d) × 𝒫(O_c)) × logic → {pass, flag, block}
  where logic ∈ {any_flag, all_flag, weighted}
  Properties: deterministic given component evaluations
```

### A.3 Pipeline Stages

```
DEFINITION (ROC Pipeline):

For interaction i with raw output o ∈ O_raw:

  Stage 1: D_results = { (r, eval_d(o, r)) | r ∈ applicable(O_d, i) }
  Stage 2: C_results = { (k, eval_c(o, k), τ(k)) | k ∈ applicable(O_c, i) }
  Stage 3: X_results = { (x, eval_x(o, x, D_results, C_results)) |
                          x ∈ applicable(O_x, i) }

  Short-circuit (Critical tier):
    if ∃ r ∈ D_results : eval_d = fail → Decision = BLOCK immediately
    (do not await Stage 2/3)

  Decision(o) =
    BLOCK  if ∃ r ∈ D_results : eval_d = fail ∧ risk_tier = critical
           ∨ ∃ x ∈ X_results : eval_x = block
           ∨ risk_tier = critical ∧ classifier_timeout
    FLAG   if ∃ k ∈ C_results : eval_c ≥ τ(k)
           ∨ ∃ x ∈ X_results : eval_x = flag
    BELOW_THRESHOLD  otherwise
    (Note: BELOW_THRESHOLD, not PASS — for classifier-evaluated outputs)

  Delivery(o) =
    Critical: atomic buffered — o' emitted only after Decision is terminal
    Standard: o' = o delivered immediately; evaluation async
    Low: o' = o delivered; evaluation sampled async

    if BLOCK: o' = fallback(o, decision_reason)
    if FLAG (Critical): o' = fallback or human review
    if FLAG (Standard/Low): o' = o (delivered, flag in audit)
```

### A.4 Audit Record

```
A_ROC(i, t) = {
  interaction_id    : identifier(i)
  timestamp         : t
  raw_output_hash   : hash(o)
  delivered_hash    : hash(o')        — differs from raw if redaction occurred
  d_evaluations     : D_results       — deterministic: (rule, pass/fail)
  c_evaluations     : C_results       — classifier: (classifier_ver, score, τ, decision)
  x_evaluations     : X_results       — composite: (constraint, decision, logic_applied)
  pipeline_decision : {PASS, FLAG, BLOCK}
  fallback_action   : action taken if BLOCK (substitution, redaction, retry, escalation)
  timing_mode       : {synchronous, asynchronous, sampled}
  epg_context       : pointer(A_EPG(i, t)) | ⊥
  predecessor       : pointer(A_EPG(i, t)) | ⊥
}
```

### A.5 Formal Properties

```
THEOREM (Output Evaluation Completeness — Critical and Standard Tiers):
  ∀ i ∈ I_governed where timing_mode ∈ {synchronous, asynchronous} :
    A_ROC(i, t) is total over applicable(C_ROC, i)
    ∧ A_ROC(i, t) is immutable
    ∧ A_ROC(i, t) contains interaction_id

  Scope: applies to Critical and Standard tier interactions within
  ROC's enforcement boundary. Low-tier sampled interactions have
  evaluation records only when sampled. Outputs that bypass ROC
  entirely are detected via GIL.

THEOREM (Deterministic Evaluation Reliability):
  ∀ o, r : eval_d(o, r, t₁) = eval_d(o, r, t₂)
    given ver(r, t₁) = ver(r, t₂)

  Deterministic evaluations produce identical results for
  identical inputs and rule versions. This is the same
  evidentiary standard as EPG's mechanical evaluation.

PROPERTY (Classifier-Based Evaluation — Scored, Not Guaranteed):
  eval_c is probabilistic. The following are NOT theorems:
    - eval_c is deterministic (it is not — classifier non-determinism)
    - eval_c has zero false negatives (it does not at any threshold)
    - eval_c captures all violations (novel patterns evade classifiers)

  Classifier evaluations are SCORED EVIDENCE, not formal guarantees.
  The audit record captures the score, threshold, and classifier
  version, enabling post-hoc assessment of evaluation quality.
```

### A.6 Threshold Governance

```
Thresholds are governed artifacts:
  ∀ k ∈ K : τ(k) is subject to:
    - Dual-control modification (author ≠ approver)
    - Version tracking: ver(τ(k), t) recorded in audit
    - Risk-tier defaults: τ_critical < τ_standard < τ_low
      (lower threshold = more conservative = more flags)
```

### A.7 Limitations

The formal model provides guarantees for the deterministic tier and scored evidence for the classifier tier. It does not provide:

- Soundness or completeness guarantees for classifiers
- Coverage guarantees for novel threat patterns
- Guarantees about model behavior (only about output evaluation)

This two-tier evidentiary structure is intentional. Merging probabilistic evaluations with deterministic ones would either overstate classifier reliability or understate deterministic reliability. Keeping them separate enables honest, tier-appropriate audit.

---

## Appendix B: Worked Examples

### B.1 Healthcare: Patient-Facing Chatbot (Continued from WP1)

WP1's example established EPG constraints for a Patient FAQ Chatbot. ROC extends governance to the output:

**Output constraints applied:**

| Constraint | Type | Tier | Action on Violation |
|------------|------|------|-------------------|
| SSN/MRN pattern detection | O_d | Deterministic | Block + redact |
| PHI NER classifier (τ = 0.75) | O_c | Classifier | Block (Critical) |
| Medical advice classifier (τ = 0.80) | O_c | Classifier | Block (Critical) |
| Disclaimer presence check | O_d | Deterministic | Block + retry with reinforced prompt |
| Composite PHI check (regex + NER) | O_x | Composite (any_flag) | Block + redact |

**Scenario: Normal operation.** Patient asks "What are your visiting hours?" Model responds with visiting hours and a HIPAA disclaimer. ROC pipeline: deterministic checks pass (no PII patterns, disclaimer present), classifiers score below thresholds (no medical advice, no PHI). Decision: PASS. Output delivered. Full audit record produced.

**Scenario: Jailbreak attempt.** Patient crafts input designed to elicit medical diagnosis. Model partially complies despite EPG's F(medical_diagnosis) prompt constraint. ROC pipeline: medical advice classifier scores 0.91 (above 0.80 threshold). Decision: BLOCK. Fallback: safe response substitution ("I'm not able to provide medical advice. Please contact your healthcare provider."). Audit record documents: classifier score, threshold, block decision, fallback delivered.

**Scenario: Incidental PII.** Model response references "your appointment with Dr. Smith on March 15" — incidental PHI from conversation context. ROC pipeline: PHI NER classifier scores 0.82 (above 0.75). Composite PHI check (O_x, any_flag) triggers. Decision: BLOCK with redaction. Delivered output: "your appointment with [PROVIDER] on [DATE]." Audit record documents redaction with specific PII types detected.

### B.2 Financial Services: Investment Research Assistant

**Output constraints applied:**

| Constraint | Type | Tier | Action on Violation |
|------------|------|------|-------------------|
| Account number pattern detection | O_d | Deterministic | Block + redact |
| Financial advice classifier (τ = 0.70) | O_c | Classifier | Flag (Standard) |
| Forward-looking statement detector (τ = 0.75) | O_c | Classifier | Flag + append disclaimer |
| Data source citation check | O_d | Deterministic | Block + retry |

**Scenario: Borderline advice.** Analyst asks for sector analysis. Model produces market commentary that edges toward a buy recommendation. Financial advice classifier scores 0.68 (below 0.70 threshold). Decision: BELOW_THRESHOLD (⊨_τ). This is a tuning decision — the threshold represents the organization's risk appetite. The audit record captures the score, threshold version, and classifier version for post-hoc review if the output is later challenged.

**Important scope note:** This example assumes the tool is for internal expert use only with explicit policy walls preventing retail exposure. If the tool's outputs could reach retail investors, the workload must be reclassified as Critical with synchronous blocking on high financial-advice scores. FINRA/SEC scrutiny applies regardless of the intended audience if the tool is accessible beyond qualified institutional users.

**Scenario: Missing citation.** Model produces quantitative claims without citing data sources. Citation check (O_d) fails. Decision: BLOCK + retry with modified prompt reinforcing O(data_source_citation). Retry succeeds — model produces the same analysis with source citations. Second evaluation: PASS. Audit record documents both attempts.

### B.3 Energy: Grid Operations Decision Support

**Output constraints applied:**

| Constraint | Type | Tier | Action on Violation |
|------------|------|------|-------------------|
| Operational command detection | O_d | Deterministic | Block + escalate |
| Recommendation confidence scorer (τ = 0.60) | O_c | Classifier | Flag if low confidence |
| NERC CIP compliance notice check | O_d | Deterministic | Block + retry |

**Scenario: Model generates operational command.** Despite EPG's F(operational_commands_without_human_confirmation), model outputs "Execute load shedding on circuit 7." Deterministic command detection catches the imperative structure. Decision: BLOCK + escalation to human operator. Output is NOT delivered. Audit record documents the attempted command, the block, and the escalation. This is a safety-critical catch — ROC prevents a potential grid operations violation that EPG's prompt constraint failed to prevent at the model behavior level.

---

## Appendix C: Output Classifier Specifications

### C.1 Classifier Governance Profile Template

Every classifier deployed in ROC must have a documented governance profile:

| Field | Description |
|-------|-------------|
| Classifier ID | Unique identifier |
| Version | Semantic version (major.minor.patch) |
| Purpose | What threat/constraint it evaluates |
| Output constraint class | O_c or component of O_x |
| Training data summary | Source, size, composition (no PII in the profile) |
| Benchmark metrics | Precision, recall, F1 at configured threshold |
| Known limitations | Classes of inputs where performance degrades |
| Expected Calibration Error (ECE) | Calibration metric for the current version — required for threshold governance. A threshold is meaningless without knowing the classifier's calibration curve. |
| Reliability diagram | Visual calibration curve for the current version |
| Threshold (τ) | Current governance threshold, with risk-tier variants. Only governable when ECE is known. |
| Threshold owner | Which governance domain owns the threshold |
| Validation cadence | How often retested against updated benchmarks |
| Last validation date | When last validated |
| Red-team results | Summary of last adversarial testing |
| Retraining trigger | Conditions that mandate classifier retraining |

### C.2 Minimum Classifier Requirements by Threat

| Threat | Minimum Classifier Type | Recommended Benchmark |
|--------|------------------------|----------------------|
| T7: Prompt injection | Sequence classifier on output patterns | Injection benchmark dataset (updated quarterly) |
| T8: Jailbreak | Safety boundary classifier | Red-team jailbreak corpus (updated quarterly) |
| T10: PII/PHI | NER + regex composite | HIPAA PHI pattern corpus, PCI test data |
| T10: Memorization | Similarity scorer against training corpus | Memorization benchmark (model-specific) |
| T11: Cross-tenant | Tenant-specific terminology detector | Tenant vocabulary cross-reference |

### C.3 Threshold Calibration Guidance

Threshold selection involves a precision-recall tradeoff with regulatory implications:

**Conservative (low τ, ~0.5-0.7):** More flags, fewer misses. Higher false-positive rate increases review burden. Appropriate for Critical-tier PHI/safety constraints where a missed violation has severe regulatory or safety consequences.

**Balanced (medium τ, ~0.7-0.85):** Moderate flags and misses. Appropriate for Standard-tier constraints where the cost of false positives and false negatives is roughly symmetric.

**Permissive (high τ, ~0.85-0.95):** Fewer flags, more misses. Lower review burden but higher miss rate. Appropriate for Low-tier or informational constraints where violations are not safety-critical.

Threshold calibration is not a one-time event. As models change, user populations shift, and adversarial techniques evolve, the operating point on the precision-recall curve shifts. Periodic recalibration (aligned with classifier validation cadence) ensures thresholds remain appropriate.

---

*End of White Paper 2: Runtime Output Controls*