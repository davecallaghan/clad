# Canary (base): A handler broadened to Exception with its body changed to pass (reversion: a swallowed exception).
def run(rows):
    try:
        return load(rows)
    except ValueError:
        raise
