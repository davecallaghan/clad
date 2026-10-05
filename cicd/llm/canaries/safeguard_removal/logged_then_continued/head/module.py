def f(xs):
    for x in xs:
        try:
            check_rows(x)
        except ValueError:
            print('skip')
            continue
