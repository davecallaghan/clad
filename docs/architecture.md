# Clad Architecture

Clad implements a three-layer governance pipeline for AI systems in regulated industries. Each layer is independently deployable and produces tamper-evident audit records.

## The Pipeline

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

## Module Dependency Graph

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "Georgia, serif", "fontSize": "16px", "lineColor": "#8a94a6"}}}%%
flowchart TD
    core --> evaluation --> runtime
    runtime --> audit --> integrity --> monitoring
    runtime --> output
    core --> config
    config --> api
    config --> mcp

    runtime["<b>runtime</b><br/><i>+ upickle</i>"]
    integrity["<b>integrity</b><br/><i>+ output</i>"]
    monitoring["<b>monitoring</b><br/><i>+ core, output, audit, runtime</i>"]
    config["<b>config</b><br/><i>+ evaluation, runtime,<br/>output, integrity</i>"]
    api["<b>api</b><br/><i>+ runtime, output,<br/>monitoring, integrity</i><br/><i>+ http4s, cats-effect</i>"]
    mcp["<b>mcp</b><br/><i>+ runtime, output,<br/>audit, integrity</i>"]

    classDef mod fill:#eef2f7,stroke:#9aa6b8,color:#2a3547,rx:6,ry:6;
    classDef leaf fill:#e7ecf2,stroke:#9aa6b8,color:#2a3547,rx:6,ry:6;
    class core,evaluation,runtime,audit,integrity,output,config mod;
    class monitoring,api,mcp leaf;
```

## Key Domain Types

- **Level** — `Enterprise | Department | Project`. Enterprise constraints override all others (monotonicity).
- **Constraint** — `Obligation(property, level)` or `Prohibition(property, level)`. Deontic operators over `PropertyId`.
- **Surface** — `Prompt | Input | Config | Output | Delivery`. The five control surfaces.
- **Component** — `EPG | ROC | MDR`. Composable via `⊕` (commutative monoid with disjoint surface requirement).
- **PropertyChecker** — Trait for mechanical evaluation. Implementations: `KeywordChecker`, `RegexChecker`, `StructuralChecker`, `CompositeChecker`.

## Build & Test

Requires Scala 3.3.7 and sbt 1.10.7.

```bash
cd code
sbt compile          # compile all modules
sbt test             # run all tests
sbt "core-test/test" # run tests for a single module
sbt "core-test/testOnly clad.core.ConstraintSpec"  # run a single test class
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Scala 3.3.7 |
| Build | sbt 1.10.7 |
| Testing | ScalaTest + ScalaCheck (property-based) |
| HTTP API | http4s (Ember server) + Cats Effect |
| Serialization | uPickle |
| MCP Server | JSON-RPC over stdin/stdout |
