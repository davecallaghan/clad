"""Good: each governed value is the model's, with its origin; when the model emits nothing the
contract's ungraded marker is stored, never a default grade."""

GOVERNED = ("severity", "finding_kind")
UNGRADED = "ungraded"


def run_pipeline(records, model):
    rows = []
    for record in records:
        emitted = model(record)
        row = {"document_id": record["document_id"]}
        for name in GOVERNED:
            if name in emitted:
                row[name], row[f"{name}__origin"] = emitted[name], "model_emitted"
            else:
                row[name], row[f"{name}__origin"] = UNGRADED, "code_supplied"
        rows.append(row)
    return rows
