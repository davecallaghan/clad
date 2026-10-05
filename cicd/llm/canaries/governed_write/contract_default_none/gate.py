# Canary: the contract parameter has a default (spec Gate 2, check 2; reversion "Detection
# surfaces", Group 1 item 1: the production caller omitted it and the gate never ran).
def write_gate(rows, target, contract=None):
    return rows
