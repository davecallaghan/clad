# Canary (R2): when the model emits nothing, a default grade is stored instead of the ungraded
# marker (reversion, "Grade reversion": a default severity through a dictionary get).
def run_pipeline(records, model):
    return [{"document_id": r["document_id"], "severity": model(r).get("severity", "low")}
            for r in records]
