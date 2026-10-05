# Canary (base): An early return before a safeguard call.
def run(rows):
    check_rows(rows)
    return rows
