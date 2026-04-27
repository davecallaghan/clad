# Monitoring, Detection, and Response: Solution Architecture

**Solution Architecture Document for Clad**

**Version:** 1.0
**Date:** April 2026
**Audience:** Security architects, SOC teams, AI operations teams
**Relationship:** Implements component g_MDR from the Meta-Framework (v5). Consumes audit data from WP1 (EPG) and WP2 (ROC).
**Scope:** Governs control surfaces S_input (user input) and S_config (model selection and inference configuration). Provides cross-component monitoring, incident detection, and response orchestration.

---

## 1. Purpose and Scope

EPG governs what goes into the model (prompts). ROC governs what comes out (outputs). MDR governs the operational health of the entire governance system and detects threats that neither EPG nor ROC can catch in isolation.

MDR's three functions:

**Monitor** — continuously observe AI interactions, governance component health, constraint drift, and cross-interaction patterns.

**Detect** — identify anomalies, policy violations, adversarial probing, governance degradation, and emerging threat patterns that span multiple interactions or components.

**Respond** — orchestrate incident response: alert, escalate, contain, remediate, and report.

MDR is the only component that operates across all control surfaces. EPG sees prompts. ROC sees outputs. MDR sees the full interaction pipeline — prompt, input, config, output, delivery — through the audit records produced by EPG and ROC, plus its own direct monitoring of S_input and S_config.

### 1.1 What MDR Is Not

MDR is not a replacement for EPG or ROC. It does not evaluate prompts against constraints (EPG does that). It does not filter outputs (ROC does that). MDR operates at the meta-level: it monitors whether EPG and ROC are functioning correctly, detects patterns that individual interactions don't reveal, and coordinates response when things go wrong.

MDR is primarily a **detective** framework, not a **preventative** one. Its containment capabilities (§5.1) can stop ongoing incidents, but it cannot prevent the first occurrence of a violation — that is EPG's and ROC's role. This distinction matters for regulatory positioning: MDR provides detection and response evidence, not prevention evidence.

### 1.2 Architectural Assumptions

The following assumptions must hold for MDR to deliver its promised capabilities. If any assumption is violated, the corresponding detection claims are degraded.

**A1 (Instrumented gateway).** All AI model invocations traverse an instrumented API gateway or network proxy that produces interaction records independently of EPG, ROC, and the GIL. This gateway operates outside the trust domain of the governance components. Without this, ghost detection (§3.1) cannot detect interactions that bypass the entire governance stack.

**A2 (Governed CI/CD for configuration).** All governance-relevant configuration changes (model selection, inference parameters, constraint modifications, threshold changes) flow through a governed CI/CD path that produces immutable change records. Without this, configuration drift detection (§2.2) cannot detect out-of-band changes.

**A3 (Identity binding).** Identity binding between interactions, users, and tenants is enforced by EA1-EA4 from the meta-framework and supplemented by Identity Provider (IdP) integration. Detections are enriched with employee metadata (role, department, location) before reaching the SIEM. Without this, cross-interaction adversarial detection (§3.3) produces unactionable alerts.

**A4 (EPG/ROC audit feeds for full coverage).** MDR has a minimal independent function (input monitoring, config validation, infrastructure-level invocation logging). Full threat coverage for governance-related incidents — constraint drift, classifier degradation, decomposition aging, cross-component correlation — is contingent on receiving structured audit records from EPG and ROC. Without these feeds, MDR degenerates to generic monitoring with significantly reduced detection capability.

---

## 2. Control Surfaces

### 2.1 S_input: User Input Monitoring

S_input has governability γ = partial. MDR can observe and validate input form (length, format, encoding, injection patterns) but cannot fully constrain input content — users can type anything.

**Input monitoring capabilities:**

- **Injection pattern detection:** scan user inputs for known prompt injection patterns before they reach the prompt assembly process. This is a defense-in-depth layer — EPG constrains the prompt, ROC filters the output, and MDR catches injection attempts at the input boundary.
- **Rate and volume monitoring:** detect anomalous input patterns — sudden volume spikes, repetitive probing inputs, systematic boundary-testing sequences — that suggest adversarial behavior.
- **Input classification:** categorize inputs by risk level (routine query, edge case, potentially adversarial) to inform downstream processing. High-risk inputs may trigger enhanced ROC evaluation even for Standard-tier workloads.
- **Session-level analysis:** detect multi-turn attack patterns where individual inputs are benign but the sequence is adversarial (progressive jailbreak attempts, incremental data extraction).

### 2.2 S_config: Model and Configuration Governance

S_config has mixed governability: model selection and inference parameters (θ) are fully governable; model internals are external.

**Configuration monitoring capabilities:**

- **Model selection policy enforcement:** verify that each interaction uses a model approved for its risk tier and use case. A PHI-handling workload must use a model approved for healthcare; a financial advisory tool must use a model approved for financial services.
- **Inference parameter validation:** verify that θ (temperature, top-p, sampling parameters) falls within governed bounds. A safety-critical workload with temperature > 0.3 may violate configuration policy.
- **Model version tracking:** detect when model endpoints are updated by providers (Axiom 5 — temporal identity). If the model behind an endpoint changes, MDR flags all workloads using that endpoint for re-evaluation.
- **Configuration drift detection:** compare current deployment configurations against governed baselines. Unauthorized changes to model selection, parameters, or routing are detected and alerted.

---

## 3. Cross-Component Monitoring

MDR's highest-value function is cross-component pattern detection — finding issues that no single interaction reveals.

### 3.1 Audit Chain Health

MDR continuously monitors the health of the audit chain using two independent data sources:

- **Infrastructure-level invocation log (ground truth).** Per assumption A1, an API gateway or network proxy outside the governance trust domain produces independent records of all model invocations. This is MDR's ground truth — it cannot be suppressed by compromising EPG, ROC, or the GIL. MDR compares this infra log against GIL registrations and audit chain records. Discrepancies indicate either a governance bypass (interaction hit the model but never entered the governance pipeline) or a GIL failure.
- **GIL-to-chain comparison.** GIL entries without corresponding EPG + ROC audit records are ghosts — either component failures or partial bypasses that entered the GIL but were not processed by all components.
- **Chain integrity:** periodically verify hash chains and signatures across EPG and ROC audit records (AI2-AI3). Tampering detection.
- **Component health:** monitor EPG and ROC availability and response times. Degraded components are flagged before they fail entirely.
- **Degraded record ratio:** track the ratio of process records (supervisor-signed) to evaluation records (component-signed). Rising degraded ratios indicate component reliability issues.

**Without assumption A1 (independent infra log), ghost detection can only identify bypasses that entered the GIL but were not fully processed. Interactions that bypass the entire governance stack — including GIL — are invisible.** This limitation must be explicitly communicated to regulators.

### 3.2 Constraint Drift and Governance Decay

- **Constraint modification velocity:** track the rate of constraint changes. Sudden spikes (many constraints modified in a short window) suggest unauthorized bulk changes or compromise.
- **Threshold drift:** monitor ROC classifier thresholds over time. Gradual threshold increases (loosening) without corresponding classifier improvements is a governance decay signal.
- **Evaluation failure rates:** track the percentage of prompts/outputs that fail constraint evaluation. Rising failure rates may indicate model drift, prompt quality degradation, or changing user behavior.
- **Decomposition aging:** flag EPG constraint decompositions that haven't been re-validated within their required cadence (Critical: annually, Standard: per cycle).

### 3.3 Adversarial Pattern Detection

Individual interaction monitoring catches overt attacks. Cross-interaction analysis detects patterns across many interactions, but with important scope limitations:

**Coarse-grained detection (reliable at scale):**
- **Aggregate statistical shifts:** per-tenant, per-model, or per-use-case shifts in ROC block rates, flag rates, and classifier score distributions. These are computationally feasible at enterprise scale and detect systemic changes (model drift, configuration errors, broad adversarial campaigns).
- **Output pattern anomalies:** stable baselines for each workload, with alerts on statistically significant deviations.

**Fine-grained detection (best-effort, depends on identity and feature engineering):**
- **Progressive probing:** sequences of inputs that systematically test constraint boundaries. Requires session-level identity binding (assumption A3) and is effective only when sessions map reliably to individuals.
- **Constraint boundary mapping:** users whose inputs consistently score just below classifier thresholds (τ - ε). Requires per-user tracking and produces meaningful signals only with strong identity resolution.
- **Distributed attacks:** adversarial inputs spread across sessions/users. Detection depends on identity resolution across devices/VPNs, which MDR does not solve — it requires enterprise IdP integration per assumption A3.

**Honest limitation:** Fine-grained user-level attack pattern detection is best-effort. It depends on identity binding quality, feature engineering investment, and analyst capacity that this architecture does not provide. Sophisticated adversaries who vary patterns and use multiple identities may evade fine-grained detection. Coarse-grained aggregate monitoring is the reliable baseline.

---

## 4. Incident Detection and Classification

### 4.1 Alert Taxonomy

MDR generates three categories of alerts:

**Governance alerts** — issues with the governance system itself:
- Component unavailability (EPG, ROC, GIL, Supervisor down)
- Ghost chain detected (GIL entry without audit chain)
- Audit integrity failure (hash chain or signature verification failed)
- Unauthorized constraint modification detected
- Configuration drift from governed baseline

**Compliance alerts** — potential regulatory violations:
- PHI detected in output (from ROC) with confirmed delivery to user
- Financial advice generated for unauthorized audience
- Safety-critical constraint violated in production
- Constraint decomposition overdue for re-validation
- Residual gap compensating control not executing (HITL rate below required minimum)

**Adversarial alerts** — potential attacks or abuse:
- Prompt injection pattern detected in input
- Jailbreak probing sequence detected across sessions
- Classifier threshold boundary probing detected
- Anomalous interaction volume from single user/tenant
- Cross-tenant leakage signal (from ROC monitoring feed)

### 4.2 Alert Severity and Escalation

| Severity | Definition | Response SLA | Escalation Path |
|----------|-----------|-------------|----------------|
| P1 — Critical | Active PHI/PII exposure, governance system compromise, safety violation in production | 15 minutes | SOC → CISO → Incident Commander |
| P2 — High | Confirmed constraint violation, component failure, adversarial campaign detected | 1 hour | SOC → AI Governance team |
| P3 — Medium | Anomalous patterns, drift detected, threshold probing | 4 hours | AI Governance team |
| P4 — Low | Informational: decomposition aging, minor configuration drift | Next business day | AI Operations |

---

## 5. Incident Response Workflows

### 5.1 Containment

When a compliance or adversarial alert fires, MDR's first action is containment. Containment operates through two paths to avoid circular dependency:

**Primary: Out-of-band infrastructure containment.** Direct integration with cloud provider IAM, service mesh, or API gateway to sever model connectivity without relying on EPG or ROC. This path is used when the governance stack itself may be compromised or malfunctioning:

- **Model endpoint disablement:** revoke model API credentials or update service mesh routing to block traffic to the affected endpoint. This operates at infrastructure level, below the governance stack.
- **Workload isolation:** network-level isolation of the affected project or tenant's AI traffic.
- **User-level blocking:** rate-limit or block specific users via the API gateway or identity provider.

**Secondary: Governance-level containment.** Used when EPG and ROC are trusted (the incident is in the AI output or user behavior, not in the governance system):

- **Constraint-level containment:** MDR triggers EPG break-glass to add emergency constraints (tightening only, TTL-bound per WP1 §4.5).
- **Threshold override:** MDR triggers ROC to lower classifier thresholds temporarily (tightening, increasing sensitivity).

**MDR privilege model.** MDR's service account requires least-privilege access scoped to containment actions: API gateway route modification, IAM credential revocation for model endpoints, EPG break-glass trigger, ROC threshold override trigger. MDR does NOT have write access to constraints, classifier models, or audit records. This limits the blast radius if MDR itself is compromised. All MDR containment actions are logged in an independent audit trail (not MDR's own audit trail — a separate infra-level log).

### 5.2 Investigation

MDR provides the data for root cause analysis:

- **Full audit chain retrieval:** for any flagged interaction, retrieve the complete chain: EPG prompt evaluation → ROC output evaluation → MDR monitoring signals. Every constraint version, evaluation result, classifier score, and governance decision is available.
- **Cross-interaction correlation:** link the flagged interaction to related interactions — same user, same prompt template, same model, same time window — to determine if the issue is isolated or systemic.
- **Timeline reconstruction:** using Axiom 5 (temporal identity), reconstruct the exact state of all governed elements at the time of the incident: which constraints were active, which classifier versions were running, what thresholds were configured.

### 5.3 Remediation

Based on root cause analysis:

- **Prompt deficiency:** EPG constraint gap identified → add or modify constraints through standard authorship (§4 of WP1)
- **Classifier gap:** ROC classifier failed to detect the violation → retrain classifier, adjust threshold, add test case to regression suite
- **Configuration error:** wrong model or parameters for the workload → correct configuration, add configuration policy to prevent recurrence
- **Adversarial technique:** new attack pattern identified → add detection pattern to MDR, add mechanical check to ROC, update red-team corpus
- **Model behavior drift:** model producing different output patterns than baseline → flag for model re-evaluation or replacement
- **External provider incident:** model provider reports a security issue or MDR detects anomalies attributable to provider-side changes. Escalate to vendor with SLA requirements. Apply stricter monitoring and containment on affected endpoints until vendor resolution is confirmed. MDR cannot remediate provider-side issues directly.
- **Bulk-job incident:** a batch processing job (document analysis, report generation) has already processed thousands of records before a violation is detected. Containment: cancel the running job immediately. Impact analysis: identify all records processed since the last known-good evaluation. Notify affected data subjects per regulatory requirements. Bulk incidents require a distinct response workflow because containment cannot un-process already-completed records.

### 5.4 Reporting

Regulated industries require formal incident reporting:

- **Internal reporting:** incident summary, root cause, containment actions, remediation timeline. Feeds the cross-domain governance body and CISO office.
- **Regulatory reporting:** for PHI breaches (HIPAA 60-day notification), financial violations (SEC/FINRA), or safety incidents (NERC CIP). MDR produces the evidence; legal/compliance produce the report. Note: MDR only supplies evidence. Regulatory readiness requires mapped reporting playbooks per regime (HIPAA, SOX, GDPR, NERC CIP) with different timelines, evidence standards, and notification requirements. Multi-jurisdictional incidents (GDPR + HIPAA + SOX) require parallel reporting with potentially conflicting requirements — legal counsel drives these decisions, not MDR.
- **Trend reporting:** monthly/quarterly governance health reports: component uptime, constraint coverage, violation rates, classifier performance, adversarial activity trends.

**Forensic Evidence Locker.** Upon a P1 or P2 alert, MDR automatically triggers a forensic collection workflow that "freezes" the relevant governance state into a separate, immutable evidence store:

- Constraint versions active at the time of incident (from EPG audit records)
- Classifier versions, thresholds, and calibration data active at the time (from ROC audit records)
- The full audit chain for the flagged interaction(s)
- Infrastructure logs from the independent API gateway feed
- MDR's own detection signals and alert chain

This evidence store is write-once, tamper-evident (WORM storage, per meta-framework AI1), and separate from operational audit storage. It provides the chain-of-custody evidence that regulators require: a snapshot of the exact governance state at the time of the incident, preserved independently of ongoing system operation.

---

## 6. Integration Architecture

### 6.1 Data Flows

```
EPG ──audit records──→ MDR
ROC ──audit records──→ MDR
ROC ──violation flags─→ MDR
GIL ──interaction log─→ MDR (for ghost detection)
MDR ──alerts─────────→ SOC / SIEM
MDR ──containment────→ EPG (break-glass trigger)
MDR ──containment────→ ROC (threshold override)
MDR ──containment────→ Infrastructure (model endpoint control)
```

### 6.2 SIEM/SOAR Integration

MDR's alerts integrate with existing Security Information and Event Management (SIEM) and Security Orchestration, Automation, and Response (SOAR) platforms:

- **SIEM ingestion:** MDR pushes structured alert data (JSON) to the enterprise SIEM. AI governance alerts appear alongside traditional security events, enabling correlation (e.g., AI jailbreak attempt from the same IP as a failed VPN login).
- **SOAR playbooks:** MDR triggers SOAR playbooks for automated response — rate limiting, user lockout, model endpoint rotation. Pre-built playbooks for common scenarios (PHI exposure, jailbreak campaign, component failure).
- **Dashboard integration:** MDR feeds governance dashboards showing real-time component health, constraint evaluation rates, classifier performance, and incident status.

### 6.3 Meta-Framework Compliance

MDR instantiates g_MDR from the meta-framework:

- **Surfaces:** S = {S_input, S_config}
- **Monitoring scope:** extends to all surfaces via audit record consumption
- **R_hard:** ∅ — independently deployable
- **R_soft:** audit records from EPG and ROC (enhanced effectiveness, not required for guarantees)
- **Failure posture:** MDR itself is fail-open — if MDR goes down, EPG and ROC continue to function (Axiom 3 guarantee independence). MDR's detection guarantees (ghost detection, drift monitoring, adversarial pattern detection) apply ONLY when MDR is operational. During MDR outage, these capabilities are suspended and the outage period is logged as an "ungoverned monitoring window" in the infrastructure audit trail. EPG and ROC guarantees are unaffected by MDR status.
- **Minimal vs full function:** MDR has a minimal independent function (input monitoring via assumption A1, config validation via assumption A2) that operates without EPG/ROC audit feeds. Full detection capability — constraint drift, classifier degradation, decomposition aging, cross-component correlation — requires EPG and ROC audit feeds (assumption A4). MDR is independently deployable per R_hard = ∅, but its advertised coverage for governance-related incidents is contingent on those feeds.

---

## 7. Operational Requirements

### 7.1 Staffing

MDR operations require a three-tier staffing model:

- **Tier 1: SOC analysts (AI-trained):** 2-3 analysts with AI governance training for initial alert triage. These are existing SOC staff with additional training — not dedicated headcount. They handle P3/P4 alerts and perform initial triage on P1/P2 before escalation. A dedicated AI Governance Runbook (knowledge base) translates ML-specific alerts (classifier threshold probing, score distribution shifts) into actionable security steps that non-ML-expert analysts can execute.
- **Tier 2: AI governance engineers:** 1-2 engineers maintaining MDR detection rules, tuning alert thresholds, managing SIEM/SOAR integration, and handling P3 investigations.
- **Tier 3: AI security specialist (on-call):** At least one ML-trained security specialist available for P1/P2 escalation. This role requires understanding of classifier behavior, adversarial ML techniques, and model internals. Cannot be replaced by Tier 1 training alone — alerts like "classifier threshold boundary probing" or "cross-tenant score distribution anomaly" require ML engineering judgment for accurate disposition.
- **Incident response capability:** on-call rotation for P1 alerts (15-minute SLA). Tier 3 specialist must be reachable within the P1 SLA window. Can share rotation with existing security incident response if the specialist is cross-trained.

**Runbook requirement.** Before MDR goes operational, a comprehensive AI Governance Runbook must be developed covering: alert-to-action mappings for each alert type in §4.1, escalation decision trees, containment action authorization (who approves model endpoint disablement), evidence preservation checklists for P1/P2, and regulatory notification triggers per regime.

### 7.2 Infrastructure

- **Audit data storage:** MDR consumes audit records from EPG, ROC, and GIL. Storage requirements scale with interaction volume. Retention per regulatory requirements (AI5 from meta-framework).
- **Real-time processing:** input monitoring (§2.1) and cross-component health checks (§3.1) require real-time stream processing capability.
- **Batch analytics:** cross-interaction pattern detection (§3.3) and trend reporting (§5.4) run as batch analytics on accumulated data.
- **Alert infrastructure:** integration with enterprise alerting (PagerDuty, ServiceNow, or equivalent) for P1/P2 escalation.

### 7.3 Phased Deployment

**Phase 1 (align with EPG Phase 1):** Deploy audit chain health monitoring and GIL ghost detection. SIEM integration for governance alerts. This provides immediate value: "is the governance system working?"

**Phase 2 (align with ROC Phase 1):** Add ROC audit consumption. Deploy compliance alert generation. Input monitoring for injection patterns. Configuration drift detection.

**Phase 3 (align with EPG/ROC Phase 2+):** Add cross-interaction pattern detection. Adversarial behavior analysis. Automated containment playbooks via SOAR. Full trend reporting.

---

## 8. Limitations

MDR is primarily a **detective and responsive** framework, not a **preventative** one. This distinction is critical for regulatory positioning.

- **S_input:** MDR can detect injection patterns but cannot prevent users from typing adversarial inputs. Prevention is achieved through EPG (prompt hardening) and ROC (output filtering). MDR's input monitoring is defense-in-depth, not primary defense.
- **S_config:** MDR enforces configuration policies but cannot prevent a provider from updating a model behind an endpoint. It can only detect the change after the fact.
- **Cross-interaction detection:** coarse-grained aggregate monitoring (per-tenant, per-model statistical shifts) is reliable at scale. Fine-grained user-level adversarial detection is best-effort and depends on identity resolution, feature engineering, and analyst capacity not specified in this architecture. Sophisticated adversaries who vary patterns across identities may evade fine-grained detection.
- **Dependency on audit data (assumption A4):** MDR's full detection capability depends on receiving complete audit records from EPG and ROC. Without these feeds, MDR provides only minimal independent monitoring (input validation, config checks, infra-level invocation logging). Governance-specific detection (constraint drift, classifier degradation, decomposition aging) requires the feeds.
- **Dependency on independent infra feed (assumption A1):** ghost detection relies on an API gateway or network proxy log outside the governance trust domain. Without this, interactions that bypass the entire governance stack are invisible to MDR.
- **Standard-tier detection is not prevention:** for Standard-tier workloads where ROC operates in post-hoc audit mode, MDR's detection of violations occurs after the output has been delivered. This is notification-of-incident, not prevention. For sensitive-but-non-Critical workloads, organizations should consider rate-limiting output delivery to match MDR processing lag, ensuring detection occurs within a manageable exposure window.
- **Human-in-the-loop:** P1 and P2 incident response requires human judgment, including a Tier 3 AI security specialist for ML-specific alerts. MDR automates detection and initial infrastructure-level containment but cannot fully automate remediation for complex incidents.
- **MDR compromise:** if MDR itself is compromised, an attacker can suppress alerts and disable detection. Compensating controls: MDR containment actions are logged in an independent infra-level audit trail (not MDR's own); MDR health is monitored by the enterprise SIEM independently of MDR's own reporting.

MDR is the "nervous system" of the governance solution — it senses, alerts, and initiates response. It depends on EPG and ROC as the "muscles" that enforce governance at the prompt and output levels. Without the muscles, the nervous system can detect but not prevent. Without the nervous system, the muscles operate blind to cross-interaction patterns and system-level degradation.

---

*End of Solution Architecture: Monitoring, Detection, and Response*