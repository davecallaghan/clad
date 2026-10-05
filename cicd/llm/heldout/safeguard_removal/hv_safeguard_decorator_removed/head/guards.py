from cicd.llm.safeguard import safeguard


def verify_totals(rows):
    if sum(r['n'] for r in rows) < 0:
        raise ValueError('negative total')
    return rows
