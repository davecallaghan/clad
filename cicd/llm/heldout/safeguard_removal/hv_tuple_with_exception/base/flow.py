def run(rows):
    try:
        return verify_totals(rows)
    except KeyError:
        raise
