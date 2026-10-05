"""Good: the write goes through write_gate with the contract, and quarantined rows are written to
their own sink rather than dropped."""

from cicd.llm.governed import load_contract, write_gate

TARGET = "findings"


def publish(sink, rows, contract_path):
    contract = load_contract(contract_path)
    outcome = write_gate(rows, contract, TARGET)
    sink.write_rows(TARGET, outcome.accepted)
    sink.write_rows("findings_quarantine", outcome.quarantined)
    return outcome
