from cicd.llm.safeguard import safeguard


@safeguard
def validate_batch(rows):
    if not rows:
        raise ValueError('empty batch')
    return rows
