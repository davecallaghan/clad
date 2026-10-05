import contextlib


def load(path):
    with contextlib.suppress(Exception):
        return open(path).read()
