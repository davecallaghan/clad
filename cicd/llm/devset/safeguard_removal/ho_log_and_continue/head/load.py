import logging


def load(batches):
    out = []
    for b in batches:
        try:
            out.append(validate_batch(b))
        except ValueError:
            logging.warning('skipping bad batch')
            continue
    return out
