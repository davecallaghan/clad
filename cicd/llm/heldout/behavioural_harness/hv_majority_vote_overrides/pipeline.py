from collections import Counter


def run_pipeline(records, model):
    emitted = [model(r) for r in records]
    common = Counter(e.get('severity', 'ungraded') for e in emitted).most_common(1)[0][0]
    return [{'document_id': r['document_id'], 'severity': common, 'finding_kind': e.get('finding_kind', 'ungraded')} for r, e in zip(records, emitted)]
