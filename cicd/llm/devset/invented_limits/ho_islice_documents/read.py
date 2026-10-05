import itertools


def documents(stream):
    return list(itertools.islice(stream, 25))
