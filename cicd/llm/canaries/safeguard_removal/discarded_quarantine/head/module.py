# Canary (head): The write_gate result (and its quarantine list) discarded (reversion: a discarded quarantine list).
def write(rows, contract):
    _ = write_gate(rows, contract, 'findings')
    return rows
