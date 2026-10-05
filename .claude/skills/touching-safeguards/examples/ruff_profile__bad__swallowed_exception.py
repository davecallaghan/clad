"""Bad: the error disappears and the caller receives None (S110)."""


def read(path):
    try:
        with open(path) as fh:
            return fh.read()
    except OSError:
        pass
