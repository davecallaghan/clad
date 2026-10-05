import os


def load(rows):
    if os.environ.get('FAST_PATH'):
        return rows
    return validate_batch(rows)
