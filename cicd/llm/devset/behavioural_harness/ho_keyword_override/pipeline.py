def run_pipeline(records, model):
    out = []
    for r in records:
        e = model(r)
        sev = 'high' if 'control' in r['text'] else e.get('severity', 'ungraded')
        out.append({'document_id': r['document_id'], 'severity': sev, 'finding_kind': e.get('finding_kind', 'ungraded')})
    return out
