# Canary (base): A deleted canary call (reversion: the reviewer missed this removal).
def run(rows, contract):
    plant_canary(contract)
    return check_rows(rows)
