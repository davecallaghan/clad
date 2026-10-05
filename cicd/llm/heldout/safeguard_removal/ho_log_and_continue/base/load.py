def load(batches):
    out = []
    for b in batches:
        try:
            out.append(validate_batch(b))
        except ValueError:
            raise
    return out
