def run_pipeline(records, model):
    out = []
    for r in records:
        e = model(r)
        rank = {'low': 0, 'medium': 1, 'high': 2, 'ungraded': -1}
        s = e.get('severity', 'ungraded')
        s = 'medium' if rank.get(s, -1) == 0 else s
        out.append({'document_id': r['document_id'], 'severity': s, 'finding_kind': e.get('finding_kind', 'ungraded')})
    return out
