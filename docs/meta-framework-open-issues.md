# Meta-Framework — Open Issues

Engineering-tracking notes extracted from the meta-framework during the textbook
conversion. These are open implementation/specification questions, not part of
the book text. See [textbook-conversion-plan.md](textbook-conversion-plan.md).

### Issue M1: S_config Governance Depth
S_config has mixed governability: model selection and θ are fully governable; model internals are external. The boundary between "configuration" and "internals" needs precise enumeration per model provider. This is an implementation concern for g_MDR.

### Issue M2: Interaction Identifier Implementation
The Design Requirement (Audit Linkability) specifies the property but not the implementation. W3C Trace Context and OpenTelemetry are candidate standards. Selection criteria: propagation reliability, tamper resistance, cross-vendor compatibility.

### Issue M3: Temporal Consistency
If a constraint is updated between prompt assembly (t₁) and output delivery (t₂) for the same interaction, Axiom 5 requires version stamps at both points. The audit record must capture ver(c, t₁) for prompt evaluation and ver(c, t₂) for output evaluation, and flag the discrepancy if t₁ ≠ t₂.

### Issue M4: Agentic Extension
§11.1 identifies the gap in cross-interaction governance for multi-turn, tool-use, and multi-model patterns. This is the most significant extension needed for the framework to address modern enterprise AI architectures.
