# Clad

**Hard boundaries. Formal proof.**

*The governance layer for regulated AI.*

---

Enterprises are deploying AI with no governance that survives regulatory scrutiny. Prompts are ad-hoc, outputs are unfiltered, and audit trails are incomplete. When a regulator asks *"show me exactly which rules governed this AI interaction"* — most organizations have no answer.

Clad is a formally modeled governance framework that provides hierarchical constraints, tamper-evident audit trails, and regulatory crosswalks for enterprise AI under HIPAA, SOX, GLBA, NERC CIP, GDPR, and the EU AI Act.

## The Three-Layer Pipeline

(ASCII pipeline diagram — same as in docs/architecture.md:)

```
User Input
    │
    ▼
┌─────────────────────────────────┐
│  EPG — Enterprise Prompt        │  Are the instructions compliant?
│  Governance                     │  Hierarchical constraints, RBAC, audit
└────────────┬────────────────────┘
             │
             ▼
       [ AI Model ]                  Governed prompt → stochastic output
             │
             ▼
┌─────────────────────────────────┐
│  ROC — Runtime Output Controls  │  Is the output safe to deliver?
│                                 │  Deterministic rules + ML classifiers
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  MDR — Monitoring, Detection    │  Are patterns healthy across
│  & Response                     │  interactions?
└────────────┬────────────────────┘
             │
             ▼
      Delivered Output
```

Each layer produces tamper-evident, version-stamped audit records that compose into a complete chain.

## What Makes This Different

- **Formal rigor with honest limitations.** Deontic logic, algebraic composition, and formal proofs — with explicit statements of what it guarantees and what it doesn't. Every theorem has preconditions. Every component has a limitations section.
- **Composable, independently deployable components.** Start with prompt governance, add output controls when ready, layer on monitoring as you mature. Formal proof of component independence (Theorem 6).
- **Designed for regulated industries.** Not generic "AI ethics." Specific regulatory crosswalks for HIPAA, SOX, GLBA, NERC CIP, the EU AI Act, and NIST AI RMF.
- **Constraints, not prescriptions.** Like building codes for AI — Clad defines properties your prompts and outputs must satisfy, not how to write them.

## Quick Start

Evaluate a prompt against your governance constraints via the REST API:

```bash
curl -X POST http://localhost:8080/api/v1/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Summarize the patient discharge notes for Dr. Smith",
    "metadata": {"department": "clinical", "project": "discharge-summary"}
  }'
```

Response:

```json
{
  "allSatisfied": false,
  "totalConstraints": 5,
  "satisfiedCount": 4,
  "unsatisfied": [
    {
      "property": "phi-disclosure-prohibition",
      "constraintType": "prohibition",
      "level": "enterprise"
    }
  ],
  "auditDigest": "sha256:a1b2c3..."
}
```

Or use the MCP server for AI-agent integration:

```bash
echo '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"clad_evaluate_prompt","arguments":{"prompt":"Summarize patient notes"}},"id":1}' | \
  sbt "mcp/run -- --config governance.json"
```

## Modules

| Module | Description |
|--------|-------------|
| **core** | Domain model — Level, Constraint (Obligation/Prohibition), PropertyId, Surface, Component composition |
| **evaluation** | Prompt evaluator — mechanical (keyword/regex/structural checkers) and procedural (human attestation) |
| **runtime** | GovernanceEngine — builds from config, evaluates prompts, produces audit records |
| **audit** | Signed hash-chained audit records, append-only file storage, verification |
| **output** | Output evaluator — deterministic rules + classifier scoring, Pass/Flag/Block pipeline |
| **integrity** | SupervisedEngine — fail-closed/fail-open posture, GIL, degraded-mode handling |
| **monitoring** | Event detection (per-event + sliding window), alert taxonomy (P1-P4), EventBus |
| **config** | JSON config loading, engine construction, RBAC constraint authorization |
| **api** | HTTP REST API — evaluate, output evaluate, config, constraints, reload, health |
| **mcp** | MCP server — JSON-RPC over stdin/stdout, 6 governance tools |

## Build & Test

Requires Scala 3.3.7 and sbt 1.10.7.

```bash
cd code
sbt compile                    # compile all modules
sbt test                       # run all tests (91 source files, 60 test files)
sbt "core-test/testOnly clad.core.ConstraintSpec"  # run a single test
```

See [docs/architecture.md](docs/architecture.md) for the full module dependency graph and domain type reference.

## Research Documents

The formal specifications that the code implements:

| Document | Description |
|----------|-------------|
| [Meta-Framework](research/meta-framework.md) | 5 axioms, 6 theorems, control surface model, threat model (T1-T11), enforcement architecture, failure semantics, audit integrity, composition algebra |
| [WP1: Enterprise Prompt Governance](research/wp1-enterprise-prompt-governance.md) | Hierarchical constraints (enterprise ≻ department ≻ project), RBAC, evaluability decomposition, conflict resolution, domain isolation |
| [WP2: Runtime Output Controls](research/wp2-runtime-output-controls.md) | Two-tier hybrid evaluation (deterministic + classifier), risk-tiered pipeline, threat-specific controls |
| [Monitoring & Response](research/sa-monitoring-detection-response.md) | Cross-component monitoring, incident response, forensic evidence preservation |
| [Regulatory Mapping](research/regulatory-mapping-appendix.md) | Crosswalk to NIST AI RMF, EU AI Act, ISO 42001, HIPAA, SOX, GLBA, NERC CIP |

## Validation Status

This framework has been developed through formal design and multi-model adversarial review. It has **not** been validated through production deployment or empirical testing. The formal properties are architecturally sound but operationally unverified. Pilot deployment with representative workloads is recommended before enterprise rollout.

## License

- **Code** ([`code/`](code/)): [MIT License](LICENSE-CODE.md)
- **Research & Docs** ([`research/`](research/), [`docs/`](docs/)): [CC BY 4.0](LICENSE-DOCS.md)

## Author

David Callaghan — [LinkedIn](https://linkedin.com/in/davecallaghan)

*By [2CData](https://2cdata.com)*
