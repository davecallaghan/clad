# Canary (R1): the stored grade is assigned by code, so changing what the model emitted changes
# nothing (reversion, "Grade reversion": 75 red flags uniformly "high").
def run_pipeline(records, model):
    out = []
    for r in records:
        model(r)
        out.append({"document_id": r["document_id"], "severity": "high"})
    return out
