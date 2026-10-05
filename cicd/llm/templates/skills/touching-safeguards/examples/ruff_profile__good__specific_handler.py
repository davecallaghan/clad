"""Good: a specific exception, re-raised with context; nothing is swallowed."""


def read(path):
    try:
        with open(path) as fh:
            return fh.read()
    except FileNotFoundError as exc:
        raise RuntimeError(f"required input missing: {path}") from exc
