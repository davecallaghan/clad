# CLAD — Trust by Design

**A governance framework for enterprise AI. Hard boundaries. Formal proof. Free book.**

[![Version](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/davecallaghan/clad/releases/latest)
[![License: Book](https://img.shields.io/badge/book-CC%20BY%204.0-lightgrey)](LICENSE-DOCS.md)
[![License: Code](https://img.shields.io/badge/code-MIT-green)](LICENSE-CODE.md)

---

## The Book — Free Download

The complete framework is a 221-page book covering governance architecture, formal proofs, and regulatory crosswalks for HIPAA, SOX, GLBA, NERC CIP, GDPR, and the EU AI Act.

### [Download the PDF](https://github.com/davecallaghan/clad/releases/latest/download/trust-by-design.pdf)

The book covers: why LLM outputs need governance that goes beyond accuracy metrics; a grounded-assertion epistemology for language models; a five-category hallucination taxonomy; nineteen system invariants; hierarchical deontic constraints; tamper-evident audit chains; and end-to-end regulatory mappings.

The LaTeX source is in [`book/`](book/); the markdown it is generated from is in [`research/`](research/).

## The Three-Layer Pipeline

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "Georgia, serif", "fontSize": "18px", "lineColor": "#8a94a6", "edgeLabelBackground": "#f5f7fa", "tertiaryColor": "#f5f7fa"}}}%%
flowchart LR
    U([User<br/>Input]) --> EPG
    EPG -- "governed<br/>prompt" --> M[AI<br/>Model]
    M -- "stochastic<br/>output" --> ROC
    ROC --> MDR
    MDR --> O([Delivered<br/>Output])

    EPG["<b>EPG</b><br/>Enterprise Prompt<br/>Governance<br/><i>compliant instructions?</i>"]
    ROC["<b>ROC</b><br/>Runtime Output<br/>Controls<br/><i>output safe to deliver?</i>"]
    MDR["<b>MDR</b><br/>Monitoring, Detection<br/>&amp; Response<br/><i>patterns healthy?</i>"]

    classDef gov fill:#eef2f7,stroke:#9aa6b8,color:#2a3547,rx:6,ry:6;
    classDef model fill:#f4f1fa,stroke:#b3a6d4,color:#3a3350,stroke-dasharray:4 3;
    classDef io fill:#e7ecf2,stroke:#9aa6b8,color:#2a3547;
    class EPG,ROC,MDR gov;
    class M model;
    class U,O io;
```

Each layer produces tamper-evident, version-stamped audit records that compose into a complete chain.

## Formal Verification

**Clad's governance logic is specified in Lean 4 and machine-checked.**

The model in [`lean/`](lean/) contains 88 theorems and lemmas with zero `sorry` — no unfinished proofs — checked by `lake build` in CI. What this establishes is that the stated properties hold *of the Lean model*. It is not a proof that the Scala implementation is correct: that relationship is what the differential test below is for, and its status is reported by CI rather than asserted here. The results cover:

- **Composition algebra** — components form a *partial* commutative monoid (Theorem 6)
- **Surface completeness** — EPG + ROC + MDR covers all control surfaces (Theorem 1)
- **Tamper-evident audit chains** — hash-chain integrity with tamper detection (Theorem 3a)
- **Ghost detection** — every interaction is classified as governed, degraded, or ghost (Theorem 3b)
- **Deontic logic** — obligation/prohibition satisfaction semantics with 4 inversion rules
- **Constraint hierarchy monotonicity** — enterprise constraints propagate to all lower levels
- **Residual risk reduction** — adding components monotonically reduces risk (Theorem 4)
- **Contract composability** — independently deployed components preserve guarantees (Theorem 2)
- **Audit completeness** — every governed interaction produces an audit record (Theorem 5)

**Differential testing.** Following the [AWS Cedar](https://www.amazon.science/publications/cedar-a-new-language-for-expressive-fast-safe-and-analyzable-authorization) pattern, the Lean model includes an executable evaluator (`clad-difftest`) compared against the Scala engine on 1,000 generated constraint hierarchies. No version ships unless the Lean proofs compile and all differential tests pass.

## What Makes This Different

- **Formal rigor with honest limitations.** Deontic logic, algebraic composition, and formal proofs — with explicit statements of what it guarantees and what it doesn't. Every theorem has preconditions. Every component has a limitations section.
- **Composable, independently deployable components.** Start with prompt governance, add output controls when ready, layer on monitoring as you mature (Theorem 6).
- **Designed for regulated industries.** Specific regulatory crosswalks for HIPAA, SOX, GLBA, NERC CIP, the EU AI Act, and NIST AI RMF.
- **Constraints, not prescriptions.** Like building codes for AI — Clad defines properties your prompts and outputs must satisfy, not how to write them.

## Repository Structure

| Directory | Contents |
|-----------|----------|
| [`book/`](book/) | LaTeX source for the book (generated from `research/` by `tools/md2tex.py`) |
| [`research/`](research/) | Markdown source — the authoritative text |
| [`app/`](app/) | Scala 3 implementation — 11 modules, REST API, MCP server |
| [`lean/`](lean/) | Lean 4 formal model — 88 theorems, differential test executable |
| [`tools/`](tools/) | Build tooling — markdown→LaTeX converter, differential test runner, CI scripts |
| [`ops/`](ops/) | Infrastructure — landing page, GCP deploy config |

## Build & Test

Requires Scala 3.3.8 and sbt 1.10.7.

```bash
cd app
sbt compile                    # compile all modules
sbt test                       # run all tests
```

Build the book (requires a TeX Live installation):

```bash
bash tools/rebuild-book.sh
```

See [docs/architecture.md](docs/architecture.md) for the full module dependency graph and domain type reference.

## Validation Status

This framework has been developed through formal design and multi-model adversarial review. It has **not** been validated through production deployment or empirical testing. The formal properties are architecturally sound but operationally unverified. Pilot deployment with representative workloads is recommended before enterprise rollout.

## Citation

If you use this work, please cite it using the metadata in [`CITATION.cff`](CITATION.cff). GitHub displays a "Cite this repository" button on the landing page. Releases are archived on [Zenodo](https://zenodo.org/) with a DOI.

## License

- **Code** (`app/`, `lean/`, `tools/`, `ops/`): [MIT License](LICENSE-CODE.md)
- **Book & Research** (`book/`, `research/`, `docs/`): [CC BY 4.0](LICENSE-DOCS.md)

## Author

David Callaghan — data and AI engineer focused on governance, quality, and trust infrastructure for production systems.

[LinkedIn](https://www.linkedin.com/in/mrdavidcallaghan) · [ORCID](https://orcid.org/0009-0006-4855-7774) · [GitHub](https://github.com/davecallaghan)
