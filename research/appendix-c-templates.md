# Appendix C — Templates & Classifier Specifications

Reusable artifacts for putting the framework into practice.

## C.1 EPG Constraint Library Template

### C.1 Library Structure

A constraint library package contains:

**Library metadata:**
- Name and version (semantic versioning: major.minor.patch)
- Target industry and regulatory basis (e.g., "HIPAA Privacy Rule §164.502-§164.514")
- Maintainer organization and contact
- Last review date and next scheduled review
- Applicable governance levels (recommended)

**Constraint entries:** each containing:
- Deontic modality (O or F)
- Atomic property identifier from the controlled vocabulary
- Recommended governance level (enterprise, department, or project)
- Domain assignment
- Evaluability class (C_m or C_p)
- Rationale and regulatory reference

**Pre-attested decompositions:** for each semantic intent underlying the constraints:
- The intent record (C_s) with full semantic description
- The mechanical components (C_m) with implementation specifications
- The procedural components (C_p) with attestation requirements
- The decomposition attestation record (who attested, when, qualifications)

**Known residual gaps:** documented per constraint where soundness cannot be fully demonstrated, including compensating control recommendations.

**Regression test suite:** known-bad inputs that mechanical checks must catch, plus expected evaluation outcomes. This suite is the minimum test set that must pass during local verification (§9.4).

### C.2 Library Lifecycle

Libraries follow semantic versioning:

- **Major version** (e.g., 1.0 → 2.0): new constraints added, constraints removed, or constraint semantics materially changed. Requires full re-attestation by adopting organizations.
- **Minor version** (e.g., 1.0 → 1.1): modified decompositions, updated mechanical checks, expanded test suites. Requires regression test re-execution by adopting organizations.
- **Patch version** (e.g., 1.0.0 → 1.0.1): documentation updates, test case additions, editorial corrections. No action required by adopting organizations beyond acknowledgment.

Each version requires domain expert attestation from the library maintainer. Organizations that have adopted a library version receive update notifications when new versions are released, including a changelog and impact assessment.

### C.3 Example: HIPAA Prompt Constraints v1.0 (Skeleton)

**Library metadata:**
- Name: HIPAA Prompt Constraints
- Version: 1.0.0
- Industry: Healthcare / Life Sciences
- Regulatory basis: HIPAA Privacy Rule (45 CFR §164.502-§164.514), HIPAA Security Rule (45 CFR §164.302-§164.318)
- Recommended level: Enterprise

**Constraint entries (8 constraints):**

| # | Modality | Property | Class | Regulatory Reference |
|---|----------|----------|-------|---------------------|
| 1 | F | phi_disclosure_in_output | C_m | §164.502(a) — Minimum Necessary |
| 2 | O | hipaa_disclaimer_present | C_m | §164.520 — Notice of Privacy Practices |
| 3 | F | pii_in_system_logs | C_m | §164.312(a) — Access Control |
| 4 | O | data_minimization_instruction | C_m | §164.502(b) — Minimum Necessary Standard |
| 5 | F | medical_diagnosis_instruction | C_m + C_p | §164.502 — Uses and Disclosures |
| 6 | O | patient_redirect_to_provider | C_m | §164.502 — Treatment Exception |
| 7 | F | phi_in_test_environments | C_m | §164.308(a)(4) — Information Access Management |
| 8 | O | breach_notification_instruction | C_p | §164.404 — Notification to Individuals |

**Decomposition example (Constraint #1: F(phi_disclosure_in_output)):**

Intent record: "The AI system must not include protected health information in its outputs unless the disclosure is explicitly authorized under a HIPAA-permitted use or disclosure."

Mechanical components:
- NER-based PHI detection in prompt instructions (names, dates, MRNs, SSNs, addresses, phone numbers)
- Pattern-based detection of prompt instructions that request PHI inclusion
- Required presence of explicit "Do not include patient-identifying information" instruction

Procedural components:
- Privacy officer review and attestation that the prompt's PHI protections are adequate for the intended use case
- Annual re-attestation aligned with HIPAA risk assessment cycle

Residual gap: mechanical NER detection covers approximately 90% of standard PHI patterns. Novel identifiers, contextual PHI (e.g., "the patient in room 3"), and inference-based re-identification are not fully captured. Compensating control: 10% HITL review of outputs by trained privacy staff.

**Regression test suite (partial):**
- 50 known-PHI test prompts containing explicit patient identifiers → mechanical checks must flag all 50
- 20 boundary-case prompts with contextual identifiers → mechanical checks should flag at least 15
- 30 clean prompts with no PHI → mechanical checks must pass all 30 (false positive baseline)

---



## C.2 ROC Output Classifier Specifications

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

