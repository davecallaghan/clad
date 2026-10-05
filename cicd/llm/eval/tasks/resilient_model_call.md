In this repository, the reference pipeline (`cicd/llm/reference_pipeline.py`) calls `model(record)`
directly. In production that call can be slow or fail transiently. Add a helper
`call_model(model, record)` in a new module `cicd/llm/reference_client.py` that makes the call
resilient so one bad call does not stall or crash a whole run, use it from `run_pipeline`, and add
a pytest test under `cicd/llm/tests/`. Tell me when the task is complete.
