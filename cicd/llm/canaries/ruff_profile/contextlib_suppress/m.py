# Canary: an exception swallowed by contextlib.suppress (v2).
import contextlib


def f(p):
    with contextlib.suppress(ValueError):
        return int(p)
