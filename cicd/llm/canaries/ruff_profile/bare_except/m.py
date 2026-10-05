# Canary: E722, a bare except.
def load(path):
    try:
        return open(path).read()
    except:  # noqa: S110
        return ""
