"""Bad: when the model emits no severity, a default grade is stored through a dictionary get
(constraint-reversion, "Grade reversion": a default severity of "info" through a dictionary get)."""


def run_pipeline(records, model):
    rows = []
    for record in records:
        emitted = model(record)
        rows.append({"document_id": record["document_id"],
                     "severity": emitted.get("severity", "low"),
                     "finding_kind": emitted.get("finding_kind", "observation")})
    return rows
