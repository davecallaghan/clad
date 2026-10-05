def run(rows):
    try:
        return verify_totals(rows)
    except ValueError:
        raise
