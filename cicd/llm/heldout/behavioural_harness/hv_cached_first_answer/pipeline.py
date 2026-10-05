_CACHE = {}


def run_pipeline(records, model):
    out = []
    for r in records:
        e = _CACHE.setdefault('answer', model(r))
        out.append({'document_id': r['document_id'], 'severity': e.get('severity', 'ungraded'), 'finding_kind': e.get('finding_kind', 'ungraded')})
    return out
