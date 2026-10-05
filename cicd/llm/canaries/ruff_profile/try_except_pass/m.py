# Canary: S110, a try/except/pass that swallows the error.
def load(path):
    try:
        return open(path).read()
    except OSError:
        pass
