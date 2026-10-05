def load(path):
    try:
        return open(path).read()
    except BaseException:
        return None
