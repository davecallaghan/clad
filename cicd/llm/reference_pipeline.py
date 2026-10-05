"""Reference pipeline: the kit's clean example of a governed write.

Gates 2-4 are checked against this file (and against the client pipeline that replaces it). It
shows the three things the gates require:
- every governed value is taken from what the model emitted, with its origin recorded;
- when the model emits nothing, the contract's explicit ungraded marker is stored, never a
  default grade (no `dict.get(key, default)`, no `or` fallback);
- every write to a governed target goes through `write_gate`, and quarantined rows are written
  to their own sink rather than dropped.
"""

from __future__ import annotations

from pathlib import Path

from cicd.llm.governed import load_contract, target_spec, write_gate

TARGET = "findings"
CONTRACT_PATH = Path(__file__).resolve().parent / "contracts" / "governed_values.toml"


class MemorySink:
    """Stand-in for a table writer: keeps what was written, per target."""

    def __init__(self):
        self.tables: dict[str, list[dict]] = {}

    def write_rows(self, target: str, rows: list[dict]) -> None:
        self.tables.setdefault(target, []).extend(rows)


def to_row(record: dict, emitted: dict, spec: dict) -> dict:
    row = {"document_id": record["document_id"]}
    for name in spec["fields"]:
        if name in emitted:
            row[name] = emitted[name]
            row[f"{name}__origin"] = "model_emitted"
        else:
            row[name] = spec["ungraded_marker"]
            row[f"{name}__origin"] = "code_supplied"
    return row


def run_pipeline(records: list[dict], model, sink: MemorySink | None = None) -> list[dict]:
    """Analyse records with `model` and store governed findings. Returns the stored rows."""
    contract = load_contract(CONTRACT_PATH)
    spec = target_spec(contract, TARGET)
    sink = sink if sink is not None else MemorySink()
    rows = [to_row(r, model(r), spec) for r in records]
    outcome = write_gate(rows, contract, TARGET)
    sink.write_rows(TARGET, outcome.accepted)
    sink.write_rows("findings_quarantine", outcome.quarantined)
    return outcome.accepted
