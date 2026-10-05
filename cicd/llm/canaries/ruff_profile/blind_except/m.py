# Canary: BLE001, a blind `except Exception`.
def load(path):
    try:
        return open(path).read()
    except Exception as exc:
        return str(exc)
