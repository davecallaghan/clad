def run_pipeline(records, model):
    out = []
    for r in records:
        e = model(r)
        s = e.get('severity', 'ungraded')
        out.append({'document_id': r['document_id'], 'severity': 'medium' if s == 'high' else s, 'finding_kind': e.get('finding_kind', 'ungraded')})
    return out
