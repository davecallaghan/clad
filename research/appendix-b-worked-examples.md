# Appendix B — Worked Examples

End-to-end examples for the control layers of Part II.

## B.1 Prompt Governance (EPG)

### B.1.1 Healthcare: Patient-Facing Chatbot

**Enterprise constraints:**
- `O(hipaa_disclaimer)` — every prompt must include HIPAA-compliant disclaimer language
- `F(pii_in_logs)` — prompts must never include instructions that would cause PII to appear in system logs
- `F(medical_diagnosis)` — prompts must not instruct the model to diagnose conditions
- `P_meta(clinical_terminology)` — enterprise has considered clinical terminology and imposes no restriction

**Department constraints (Patient Services):**
- Inherits `O(hipaa_disclaimer)` and `F(pii_in_logs)` — cannot change
- Adds `F(clinical_terminology)` — patients are not clinicians; layman terms required. This is a valid tightening of P_meta (which imposes no restriction). Flagged for tension review because P_meta existed at enterprise level; review confirms department rationale is sound.
- Adds `O(empathetic_tone)` — patient-facing communications require empathetic framing
- Adds `O(redirect_to_provider)` — prompts must instruct the model to redirect medical questions to qualified providers

**Project constraints (Patient FAQ Chatbot):**
- Inherits all enterprise and department constraints (5 total)
- Adds `O(scope_to_faq_topics)` — prompt must limit the model to answering questions within the FAQ knowledge base
- Adds `O(cite_sources)` — prompt must instruct the model to cite specific FAQ entries

**Effective constraint set at project level:**
C*(project) = {O(hipaa_disclaimer), F(pii_in_logs), F(medical_diagnosis), F(clinical_terminology), O(empathetic_tone), O(redirect_to_provider), O(scope_to_faq_topics), O(cite_sources)}

**Decomposition example: F(medical_diagnosis)**

Semantic intent: "The AI system must not produce output that could be construed as diagnosing a medical condition."

Mechanical components (C_m):
- Pattern scan for diagnostic language: regex and NER-based detection of diagnostic phrases ("you have," "this indicates," "the symptoms suggest," "diagnosis:")
- Required disclaimer presence: prompt must include explicit instruction "Do not diagnose conditions or suggest specific diagnoses"
- Scope limitation: prompt must include instruction to refuse requests for diagnosis

Procedural components (C_p):
- Medical affairs review: a qualified medical affairs officer reviews the prompt and attests that its framing adequately prevents diagnostic behavior
- Adversarial testing: red-team testing with prompts designed to elicit diagnostic language; results reviewed and attested by medical affairs

Residual gap: mechanical checks cover an estimated 85% of diagnostic language patterns based on regression testing against a corpus of known diagnostic phrases. The remaining 15% includes novel or indirect diagnostic formulations. Compensating control: 15% HITL review rate on chatbot outputs, managed by medical affairs, with monthly reporting.

**Conflict example: O(empathetic_tone) vs F(promissory_language)**

The department's obligation for empathetic tone (authored by Patient Experience domain) creates tension with Legal's prohibition on promissory language. Empathetic language like "we will make sure you get the care you need" could be construed as a promissory commitment.

Phase 1 resolution: cross-domain review between Patient Experience and Legal. Resolution: add a precedence rule to the Enterprise Precedence Table — F(promissory_language) takes precedence over O(empathetic_tone). The project team must find empathetic language that does not make promissory commitments. Documented rationale: legal liability outweighs tone preference.

### B.1.2 Financial Services: Investment Research Assistant

**Enterprise constraints:**
- `O(sox_audit_trail)` — all AI-generated content used in financial reporting must have complete audit trail
- `F(investment_advice_to_retail)` — prompts must not instruct the model to provide personalized investment advice to retail investors
- `O(data_source_citation)` — prompts must instruct the model to cite data sources for all quantitative claims

**Department constraints (Institutional Research):**
- Inherits all enterprise constraints
- `P_meta(investment_thesis_language)` — the department has considered investment thesis language and does not restrict it for professional audiences
- `O(model_uncertainty_disclosure)` — prompts must instruct the model to disclose uncertainty and confidence levels

**Project constraints (Analyst Tool):**
- Inherits all enterprise and department constraints
- `F(forward_looking_statements_without_disclaimer)` — prompts must prohibit forward-looking statements unless accompanied by required regulatory disclaimers

**Decomposition example: F(investment_advice_to_retail)**

Semantic intent: "The AI system must not provide personalized investment recommendations to retail investors."

Mechanical components: audience detection (prompt must include explicit audience scoping: "this tool is for institutional investors only"), keyword patterns for personalized advice language ("you should buy," "I recommend," "based on your portfolio"), required disclaimer presence.

Procedural components: compliance officer review, quarterly re-attestation, FINRA suitability framework review.

Residual gap: mechanical checks cannot distinguish between general market commentary (permitted) and implicit investment guidance that sophisticated readers would recognize as advice but retail investors might act on differently. Compensating control: 25% HITL review by compliance, with escalation protocol for borderline cases.

### B.1.3 Energy: Grid Operations Decision Support

**Enterprise constraints:**
- `O(nerc_cip_compliance_notice)` — all AI interactions involving grid operations must include NERC CIP compliance framing
- `F(operational_commands_without_human_confirmation)` — prompts must never instruct the model to issue operational commands; all recommendations require human confirmation
- `O(audit_all_recommendations)` — every AI recommendation must be fully auditable with complete constraint evaluation

**Department constraints (Grid Operations):**
- `O(cite_sensor_data_source)` — prompts must instruct the model to cite specific sensor data sources for all recommendations
- `F(recommendations_outside_trained_scenarios)` — prompts must restrict the model to scenarios covered by validated training data

**Conflict example: efficiency vs safety**

An efficiency optimization constraint `O(minimize_response_time)` from the Operations Efficiency domain creates tension with `F(operational_commands_without_human_confirmation)` from the Safety domain. Faster response times could pressure the system toward fewer confirmation steps.

Resolution: Enterprise Precedence Table specifies Safety > Efficiency as the default domain priority. The safety prohibition takes absolute precedence. The efficiency constraint is modified to: `O(minimize_response_time_within_safety_constraints)` — a new atomic property that explicitly acknowledges the safety boundary.

**Break-glass scenario:** During an active grid stability event, an enterprise-level actor invokes break-glass to add `F(non_essential_ai_queries)` — temporarily prohibiting all non-essential AI interactions to preserve computational resources for critical grid operations. TTL: 24 hours (energy sector default). The constraint is tightening-only (adds a prohibition). Immediate CONTRADICTION scan confirms no conflicts with existing constraints. Post-hoc review within 24 hours formalizes the constraint through standard dual-control if the operational condition persists, or allows automatic reversion if the event resolves.

---


## B.2 Runtime Output Controls (ROC)

### Healthcare: Patient-Facing Chatbot (continued)

The EPG example in Appendix B.1 established constraints for a Patient FAQ Chatbot. ROC extends governance to the output:

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

### B.2.2 Financial Services: Investment Research Assistant

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

### B.2.3 Energy: Grid Operations Decision Support

**Output constraints applied:**

| Constraint | Type | Tier | Action on Violation |
|------------|------|------|-------------------|
| Operational command detection | O_d | Deterministic | Block + escalate |
| Recommendation confidence scorer (τ = 0.60) | O_c | Classifier | Flag if low confidence |
| NERC CIP compliance notice check | O_d | Deterministic | Block + retry |

**Scenario: Model generates operational command.** Despite EPG's F(operational_commands_without_human_confirmation), model outputs "Execute load shedding on circuit 7." Deterministic command detection catches the imperative structure. Decision: BLOCK + escalation to human operator. Output is NOT delivered. Audit record documents the attempted command, the block, and the escalation. This is a safety-critical catch — ROC prevents a potential grid operations violation that EPG's prompt constraint failed to prevent at the model behavior level.

---
