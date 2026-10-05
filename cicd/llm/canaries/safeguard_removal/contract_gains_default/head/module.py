# Canary (head): A contract parameter gaining a default of None (reversion, Group 1 item 1).
def write(rows, target, contract=None):
    return write_gate(rows, contract, target)
