def run_pipeline(records, model):
    out = []
    for r in records:
        e = model(r)
        sev = e['severity'] if 'severity' in e else ('high' if len(r['text']) > 20 else 'low')
        out.append({'document_id': r['document_id'], 'severity': sev, 'finding_kind': e.get('finding_kind', 'ungraded')})
    return out
