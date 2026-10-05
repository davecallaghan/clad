# Canary (base): The write_gate result (and its quarantine list) discarded (reversion: a discarded quarantine list).
def write(rows, contract):
    out = write_gate(rows, contract, 'findings')
    return out
