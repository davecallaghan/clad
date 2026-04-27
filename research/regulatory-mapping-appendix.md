# Regulatory and Standards Mapping Appendix

**Clad — Compliance Crosswalk**

**Version:** 1.0
**Date:** April 2026
**Purpose:** Maps the Clad components (Meta-Framework, WP1/EPG, WP2/ROC, SA/MDR) to applicable regulatory frameworks and industry standards. This document enables compliance teams to demonstrate how the governance solution satisfies specific regulatory requirements.
**Scope:** NIST AI RMF, EU AI Act (high-risk systems), ISO/IEC 42001, HIPAA, SOX, GLBA, NERC CIP.

**Important disclaimer:** This mapping shows where the governance solution provides relevant controls. It does NOT claim that deploying the solution achieves full compliance with any regulation. Compliance requires organizational controls, policies, training, and regulatory engagement beyond what any technical framework provides.

**Validation status:** This framework has been developed through formal design and multi-model adversarial review. It has NOT been validated through implementation or empirical testing. Pilot deployment with representative workloads is recommended before enterprise rollout. The coverage ratings in this mapping reflect architectural design intent; operational effectiveness must be verified empirically.

---

## 1. NIST AI Risk Management Framework (AI RMF 1.0)

The NIST AI RMF organizes AI risk management into four functions: Govern, Map, Measure, Manage. The governance solution addresses elements of each.

### Govern Function

| NIST AI RMF | Solution Component | Coverage | Notes |
|---|---|---|---|
| GOV-1: Policies for AI risk management | EPG §3-§4 (constraint hierarchy, RBAC) | Strong | Hierarchical constraints implement AI-specific policy. Dual control and cross-domain review enforce policy governance. |
| GOV-2: Accountability structures | EPG §4 (RBAC, dual control), §7 (cross-domain body) | Strong | Role-based authorship with orthogonal approval. Cross-domain governance body provides oversight. |
| GOV-3: Workforce diversity and training | SA §7.1 (staffing, runbook) | Partial | SA defines staffing model and runbook requirement. Broader workforce training is organizational, not framework-provided. |
| GOV-4: Organizational commitments | Meta-framework §2 (threat model scope) | Partial | Threat model explicitly scopes in-scope vs out-of-scope risks. Organizational commitment to governance is assumed, not enforced. |
| GOV-5: Processes for risk management | EPG §5 (decomposition), §6 (conflict resolution), ROC §5 (pipeline) | Strong | Structured processes for constraint development, conflict resolution, and output evaluation. |
| GOV-6: Policies for third-party AI | EPG §9.4 (constraint libraries, local verification) | Moderate | Library verification gate addresses third-party constraint adoption. Model provider governance is out of scope (meta-framework §14.2). |

### Map Function

| NIST AI RMF | Solution Component | Coverage | Notes |
|---|---|---|---|
| MAP-1: Intended purpose and context | EPG §3 (constraint model per use case) | Strong | Constraints are scoped per project with specific use-case context. |
| MAP-2: Interdependencies | Meta-framework §8 (interface contracts) | Strong | Formal interface contracts define component dependencies. |
| MAP-3: AI-specific risks | Meta-framework §2 (threat model T1-T11) | Strong | Explicit threat enumeration with in-scope/out-of-scope classification. |
| MAP-5: Impact assessment | EPG §5.6 (residual gap controls), ROC §6 (threat-specific controls) | Moderate | Residual gap documentation and compensating controls address impact. Full impact assessment is organizational. |

### Measure Function

| NIST AI RMF | Solution Component | Coverage | Notes |
|---|---|---|---|
| MEA-1: Risk metrics | ROC §8.2 (classifier governance), SA §3.2 (drift detection) | Moderate | Classifier precision/recall/ECE, constraint violation rates, drift metrics. Not a complete risk measurement framework. |
| MEA-2: AI system performance | ROC §4.2 (classifier profiles), SA §3.3 (pattern detection) | Moderate | Classifier performance tracked. Model performance per se is out of scope. |
| MEA-3: Trustworthy characteristics | EPG §9.6 (limitations), ROC §9 (limitations) | Partial | Limitations sections honestly scope what is and isn't guaranteed. Fairness, explainability not directly addressed. |

### Manage Function

| NIST AI RMF | Solution Component | Coverage | Notes |
|---|---|---|---|
| MAN-1: Risk treatment | EPG (prompt constraints), ROC (output controls), SA (monitoring) | Strong | Three-component governance pipeline treats prompt, output, and monitoring risks. |
| MAN-2: Risk response | SA §5 (incident response workflows) | Strong | Containment, investigation, remediation, reporting workflows defined. |
| MAN-3: Risk communication | SA §5.4 (reporting), EPG §9.6 (limitations) | Moderate | Internal and regulatory reporting. Stakeholder communication is organizational. |
| MAN-4: Risk monitoring | SA §3 (cross-component monitoring), §4 (alert taxonomy) | Strong | Continuous monitoring with tiered alerting and escalation. |

---

## 2. EU AI Act (High-Risk AI Systems)

The EU AI Act requires specific obligations for providers and deployers of high-risk AI systems. The governance solution addresses several but not all.

| EU AI Act Requirement | Article | Solution Component | Coverage | Notes |
|---|---|---|---|---|
| Risk management system | Art. 9 | Meta-framework (risk model §10), EPG, ROC, SA | Strong | Three-component system with formal risk model, residual risk documentation, and continuous monitoring. |
| Data and data governance | Art. 10 | Out of scope | None | Data quality, training data governance, and bias testing are not addressed by this framework. Model internals are external (meta-framework §14.2). |
| Technical documentation | Art. 11 | All documents (meta-framework, WP1, WP2, SA) | Strong | Comprehensive technical documentation of governance architecture, formal models, and operational procedures. |
| Record-keeping and logging | Art. 12 | Meta-framework §9 (audit chain), EPG §A.12, ROC §A.4, SA §3.1 | Strong | Tamper-evident, version-stamped audit records with full chain composition across components. GIL provides interaction-level traceability. |
| Transparency | Art. 13 | EPG §9.6 (limitations), ROC §9 (limitations), SA §8 (limitations) | Moderate | Each component explicitly states what it guarantees and what it does not. User-facing transparency (informing users they are interacting with AI) is organizational, not framework-provided. |
| Human oversight | Art. 14 | EPG §4.5 (break-glass), §5.6 (HITL trigger), ROC §5.4 (escalation), SA §5 (incident response) | Moderate | Human-in-the-loop review for residual gaps, escalation for blocked outputs, human incident response. Routine human oversight of AI decisions is not mandated by the framework. |
| Accuracy, robustness, cybersecurity | Art. 15 | ROC §4.2 (classifier governance), §8.2 (validation), SA §3.2 (drift detection) | Partial | Classifier accuracy tracked. Robustness via red-team testing. Cybersecurity of the AI system itself is infrastructure-level, partially addressed by SA containment. Model accuracy is out of scope. |
| Quality management system | Art. 17 | EPG §4.4 (change control), §9.3 (change management) | Moderate | Constraint change management with CI/CD pipelines, staging, rollback. Broader QMS is organizational. |
| Post-market monitoring | Art. 72 | SA §3 (monitoring), §5.4 (trend reporting) | Strong | Continuous monitoring, drift detection, trend reporting. |

**Key gap:** Art. 10 (data governance) is entirely out of scope. Organizations deploying this framework for EU AI Act compliance must complement it with a separate data governance program.

---

## 3. ISO/IEC 42001 (AI Management System)

ISO 42001 defines requirements for an AI management system (AIMS). The governance solution provides technical controls that underpin several AIMS requirements.

| ISO 42001 Clause | Solution Component | Coverage | Notes |
|---|---|---|---|
| 4. Context of the organization | Meta-framework §2 (threat model), §4 (control surfaces) | Moderate | Threat model and control surface decomposition define the AI governance context. Organizational context (stakeholders, scope boundaries) is broader. |
| 5. Leadership | EPG §4.1 (RBAC), §7.4 (cross-domain body) | Partial | Governance roles defined. Leadership commitment is organizational. |
| 6. Planning (risk assessment) | Meta-framework §10 (risk model), EPG §5.6 (residual gaps) | Strong | Formal risk model with attribution across surfaces. Residual gap documentation with compensating controls. |
| 7. Support (resources, competence) | SA §7.1 (staffing), EPG §9.5 (tooling) | Moderate | Staffing model and tooling recommendations defined. Training and awareness are organizational. |
| 8. Operation (AI system lifecycle) | EPG §9.1 (phased adoption), §9.3 (change management), ROC §8.1 (phased adoption) | Strong | Lifecycle management from constraint development through deployment, change management, and retirement. |
| 9. Performance evaluation | SA §3 (monitoring), §5.4 (reporting), ROC §8.2 (classifier governance) | Strong | Continuous monitoring, trend reporting, classifier performance tracking. |
| 10. Improvement | SA §5.3 (remediation), EPG §5.7 (decomposition verification) | Moderate | Remediation workflows and periodic re-validation. Continual improvement process is organizational. |

---

## 4. HIPAA (Health Insurance Portability and Accountability Act)

HIPAA compliance for AI systems requires controls across the Privacy Rule, Security Rule, and Breach Notification Rule.

| HIPAA Requirement | Rule/Section | Solution Component | Coverage | Notes |
|---|---|---|---|---|
| Minimum necessary standard | Privacy §164.502(b) | EPG: O(data_minimization_instruction) | Strong | Enforceable as a prompt constraint with mechanical evaluation. |
| Notice of privacy practices | Privacy §164.520 | EPG: O(hipaa_disclaimer_present) | Strong | Enforceable as a prompt constraint with mechanical evaluation. |
| PHI protection in outputs | Privacy §164.502(a) | ROC: composite PHI check (O_d + O_c), PHI-first policy (§6.3) | Moderate | Deterministic pattern matching + NER classifier with conservative threshold and post-redaction re-evaluation. Classifier false negatives are possible (WP2 Theorem 4: irreducible residual risk). Best-available detection, not guaranteed prevention. |
| Access controls | Security §164.312(a) | EPG §4 (RBAC), Meta-framework EA2 (identity binding) | Strong | Role-based constraint authorship. Identity-bound model invocations. |
| Audit controls | Security §164.312(b) | Meta-framework §9 (audit chain), AI1-AI5 (integrity) | Strong | Tamper-evident, version-stamped, independently verifiable audit records. |
| Integrity controls | Security §164.312(c) | Meta-framework AI2-AI3 (hash chain, signing) | Strong | Cryptographic chain integrity with KMS-managed signing. |
| Transmission security | Security §164.312(e) | Out of scope | None | Encryption in transit is infrastructure-level, not framework-provided. |
| Breach notification | Breach §164.404 | SA §5.4 (regulatory reporting), forensic evidence locker | Moderate | MDR provides detection and evidence. 60-day notification compliance requires organizational reporting processes. |
| Risk assessment | Security §164.308(a)(1) | Meta-framework §10 (risk model), EPG §5.6 (residual gaps) | Strong | Formal risk model with residual gap documentation and compensating controls. |
| PHI in logs/audit records | Security §164.308(a)(4) | EPG: F(pii_in_logs), Meta-framework AI5 (PII minimization in audit) | Moderate | Constraint prohibits PHI in logs. AI5 requires PII minimization in audit records. Full implementation is deployment-specific. |

---

## 5. SOX (Sarbanes-Oxley Act, Section 404)

SOX §404 requires reliable internal controls over financial reporting (ICFR). AI systems used in financial reporting must demonstrate controlled, auditable operation.

| SOX Requirement | Solution Component | Coverage | Notes |
|---|---|---|---|
| Internal controls documentation | All documents (meta-framework, WP1, WP2, SA) | Strong | Comprehensive documentation of governance controls, formal models, and operational procedures. |
| Segregation of duties | EPG §4.2 (orthogonal dual control) | Moderate | Author ≠ approver with different reporting lines for Critical constraints. Central non-business-unit approver required. Mitigates single-actor and same-unit abuse; does NOT prevent cross-unit collusion (WP1 §4.2 collusion limitation). |
| Change management | EPG §4.4, §9.3 (constraint change management) | Strong | Versioned, signed, dual-controlled changes with CI/CD pipelines, staging, and rollback. |
| Audit trail | Meta-framework §9 (audit chain), AI1-AI4 | Strong | Tamper-evident, immutable audit records with cryptographic integrity and independent verification. |
| Management assessment of controls | SA §5.4 (trend reporting), ROC §8.2 (classifier governance) | Moderate | Governance health metrics, classifier performance tracking, and trend reporting support management assessment. Formal control testing is organizational. |
| Content accuracy of AI-generated reports | Out of scope | None | The framework governs the governance process, not content accuracy. AI-generated financial content requires separate validation controls (human review, data reconciliation). |

**Key gap:** SOX requires that AI-generated content used in financial reporting be materially accurate. This framework ensures the governance process is auditable but does not validate content correctness. Organizations must maintain human review and validation workflows for any AI output used in ICFR.

---

## 6. GLBA (Gramm-Leach-Bliley Act)

| GLBA Requirement | Solution Component | Coverage | Notes |
|---|---|---|---|
| Safeguards Rule — information security program | Meta-framework (governance architecture), EPG, ROC, SA | Strong | Three-component governance with formal controls, monitoring, and incident response. |
| Customer data protection | ROC §6.3 (PII filtering), EPG: F(pii constraints) | Strong | Prompt-level and output-level PII protection with deterministic and classifier-based checks. |
| Risk assessment | Meta-framework §10 (risk model) | Strong | Formal risk model with surface-level attribution. |
| Incident response | SA §5 (incident response) | Strong | Containment, investigation, remediation, reporting workflows with forensic evidence locker. |
| Service provider oversight | EPG §9.4 (constraint libraries, local verification gate) | Moderate | Library verification requires local attestation. Model provider oversight is deployment-specific. |

---

## 7. NERC CIP (Critical Infrastructure Protection)

| NERC CIP Standard | Solution Component | Coverage | Notes |
|---|---|---|---|
| CIP-003: Security management controls | EPG §4 (RBAC, dual control), Meta-framework EA1-EA4 | Strong | Role-based access, change control, enforcement architecture. |
| CIP-004: Personnel and training | SA §7.1 (staffing, runbook) | Partial | Staffing model and runbook defined. Formal personnel security screening is organizational. |
| CIP-005: Electronic security perimeter | Meta-framework EA1 (chokepoint enforcement) | Moderate | All AI interactions must pass through governed enforcement points. Network perimeter security is infrastructure-level. |
| CIP-007: System security management | EPG §9.3 (change management), SA §3.2 (config drift) | Strong | Change management with staging and rollback. Configuration drift detection. |
| CIP-008: Incident reporting and response | SA §5 (incident response), §5.4 (forensic evidence locker) | Strong | Incident detection, containment, investigation, and reporting workflows with evidence preservation. |
| CIP-011: Information protection | ROC §6.3 (PII/PHI filtering), EPG: F(operational_commands_without_confirmation) | Moderate | Output filtering for sensitive data (classifier-based, probabilistic). Safety constraints preventing unauthorized operational commands (deterministic). Cross-tenant isolation (T11) is monitoring-only, not a preventative control (WP2 §6.4). |

---

## 8. Cross-Framework Coverage Summary

**Important caveat:** Coverage ratings below reflect each component's CONTRIBUTION to the regulatory requirement, not full satisfaction. Multiple components may contribute to the same requirement without collectively satisfying it. The framework guarantees audit completeness and risk reduction within its scope — not full regulatory compliance, which requires organizational controls, policies, training, and processes beyond any technical framework. Risk coverage across surfaces is modeled as additive for attribution purposes (meta-framework §10.1) but this is a simplification — real risks interact across surfaces.

| Governance Solution Component | NIST AI RMF | EU AI Act | ISO 42001 | HIPAA | SOX | GLBA | NERC CIP |
|---|---|---|---|---|---|---|---|
| Meta-Framework | Govern, Map | Art. 9, 11, 12 | Cl. 4, 6 | §164.308, 312 | Audit trail | Safeguards | CIP-003 |
| WP1 (EPG) | Govern, Manage | Art. 9, 14, 17 | Cl. 5, 6, 8 | §164.502, 520 | Controls, SoD | Data protection | CIP-003, 007 |
| WP2 (ROC) | Measure, Manage | Art. 9, 15 | Cl. 9 | §164.502(a) | — | Data protection | CIP-011 |
| SA (MDR) | Manage | Art. 72 | Cl. 9, 10 | §164.404 | Assessment | Incident response | CIP-008 |

### Key Gaps Across All Frameworks

| Gap | Affects | Mitigation |
|---|---|---|
| Data and training data governance | EU AI Act Art. 10, ISO 42001 Cl. 8 | Requires separate data governance program |
| Model accuracy and fairness | NIST MEA-3, EU AI Act Art. 15 | Requires model evaluation and bias testing programs |
| User-facing transparency | EU AI Act Art. 13 | Requires organizational disclosure processes |
| Content accuracy for financial reporting | SOX §404 | Requires human review and data reconciliation |
| Transmission security and encryption | HIPAA §164.312(e), GLBA | Infrastructure-level controls |
| Personnel security screening | NERC CIP-004, ISO 42001 Cl. 7 | Organizational HR processes |

---

*End of Regulatory and Standards Mapping Appendix*