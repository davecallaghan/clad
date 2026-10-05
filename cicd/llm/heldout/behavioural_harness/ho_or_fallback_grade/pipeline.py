def run_pipeline(records, model):
    out = []
    for r in records:
        e = model(r)
        out.append({'document_id': r['document_id'], 'severity': e.get('severity') or 'medium', 'finding_kind': e.get('finding_kind') or 'observation'})
    return out
