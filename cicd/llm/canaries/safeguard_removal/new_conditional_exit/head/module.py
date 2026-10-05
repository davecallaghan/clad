def f(r):
    if QUICK:
        return r
    return check_rows(r)
